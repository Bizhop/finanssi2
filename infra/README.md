# Production infrastructure

This TypeScript Pulumi project defines the production stack: a two-AZ VPC, private Aurora PostgreSQL, ECR, a
single ARM64 EC2 backend, Secrets Manager inputs, a private S3 frontend bucket, and a staged CloudFront certificate/distribution.
The production host and new-stack default are `t4g.small` (`arm64`). The stack also defines bounded CloudWatch log retention, host CPU/memory/disk/credit
alarms, backend restart alarms and a monthly cost budget with email notifications. No AWS resources are created by the local TypeScript
build.

## Local setup

- Node.js 22 or newer and npm
- Pulumi CLI
- AWS credentials for the intended account
- A Pulumi state backend selected by the operator; log into it with `pulumi login` before creating a stack. Keep secrets encrypted
  with the backend's Pulumi secrets provider. Do not commit stack files or state exports.

Install exact locked package versions and type-check the project:

```bash
npm ci
npm run typecheck
npm run lint
npm run fmt:check   # `npm run fmt` fixes formatting; needs deno
```

Create a local production stack config from `Pulumi.prod.yaml.example`, replace the public Firebase web placeholders, then initialize
the stack and store the Firebase Admin service-account JSON with Pulumi's `--secret` option. The example is tracked; real
`Pulumi.*.yaml` files are ignored by Git. Never place Firebase Admin JSON in the stack YAML as plaintext or in `firebaseWeb`.

The stack uses `aws:region` with a default of `eu-north-1`. Set `monthlyBudgetLimitUsd` to the alert amount you choose and
`budgetAlertEmail` to the recipient address. Confirm the SNS subscription email after deployment. Check that the configured Aurora PostgreSQL engine version is currently
available in that region and supports Serverless v2 auto-pause before previewing. `createFrontendDistribution` defaults to `false`:
the first stage requests the ACM certificate and exports its validation CNAME without waiting for Domainhotelli DNS. After that CNAME
resolves publicly, enable the distribution in the local stack config and preview again.

The backend release helper defaults to the platform in the applied stack output. Set `BACKEND_PLATFORMS=linux/amd64,linux/arm64`
to publish one immutable multi-platform release index, or use `DEPLOY_BACKEND=false` to publish without deploying. The deploy helper
checks the host architecture and pulls the digest for that platform before it changes the active image reference. Production uses a
multi-platform release so the same digest can run on ARM64 EC2 and amd64 developer machines.

Production moved from `t3.small`/x86-64 to `t4g.small`/ARM64 on 2026-10-14. For a future architecture change, set
`allowAmiChange: true` while replacing the instance so Pulumi can select the matching AL2023 AMI; after replacement and state refresh,
set it back to `false` to restore routine AMI protection. It defaults to `false`.

The `prod` stack is deployed. The backend image has been pushed to ECR and deployed through SSM; the backend service reported that
the app started. `frontendDistributionHostname` currently returns `null` because the CloudFront distribution is still staged/disabled.
The first stack stage creates billable AWS resources even while the frontend distribution is disabled. Review the resource and cost
preview before future infrastructure changes.

On a brand-new stack the EC2 image reference is absent at bootstrap: the host installs its services and waits for the release helpers
below to deploy an immutable ECR digest. After that the deploy script records the digest in the SSM parameter
`/finanssi2/<stack>/image-ref` (output `backendImageRefParameter`), and a replaced host reads it at bootstrap and starts the last
deployed image by itself. The host exposes only 80/443; SSH and PostgreSQL are not public, and Aurora accepts traffic only from the
backend security group. S3 is private and accepts reads only through CloudFront when the distribution stage is enabled.

## Host configuration

The host bootstrap is `userdata/bootstrap.sh`, with the host scripts in `userdata/host/` embedded by `userdata.ts` (`# @include`
lines). Run `npm run shellcheck` after editing them; EC2 allows 16 KB of user data and the build fails above that.

- **Runtime settings** (allowed origin, debug emails) live in the SSM parameter `/finanssi2/<stack>/runtime-settings`, which Pulumi
  manages. `finanssi2-run` reads it at every service start, so after a `pulumi up` that changes a setting, restart the service
  (`systemctl restart finanssi2` through SSM) instead of replacing the host. Changing other values baked into user data (API hostname,
  secret ARNs, scripts) still replaces the instance.
- **AMI**: `ignoreChanges: ["ami"]` keeps routine `pulumi up` from replacing the host when a new Amazon Linux 2023 AMI is released.
  Roll the host deliberately with `pulumi up --replace <instance urn>`; the new host starts the recorded image without a redeploy.
- **Logs**: `rsyslog` provides `/var/log/messages` for the CloudWatch agent; the `BackendStarts` metric and the restart alarm count the
  `backend container start` lines in it. The RDS CA bundle is fetched when the secrets service starts, not during bootstrap.
- **Container**: runs with all capabilities dropped, `no-new-privileges`, a read-only root filesystem and a 128 MB `/tmp` tmpfs. JVM
  options (heap percentage, `ExitOnOutOfMemoryError`) are set only in the backend `Dockerfile`.

## Monitoring

Alarms notify the SNS topic `<prefix>-alarms`: EC2 status checks, CPU, CPU credits, memory and root disk (a silent memory metric counts
as a breach, since it means the CloudWatch agent stopped), backend restarts (four starts in five minutes), Aurora never pausing
(capacity above zero for 24 hours) and the monthly cost budget.

## Maintenance routine

Do this monthly (there is no CI; Dependabot for npm, Gradle and Docker digests is an option that opens PRs without a pipeline):

1. **OS patches.** The host's package repositories are locked to the AMI release. Patch in place through Systems Manager:
   `aws ssm send-command --document-name AWS-RunShellScript --instance-ids <id> --parameters 'commands=["dnf -y upgrade --releasever=latest"]'`,
   then restart the services (`systemctl restart caddy finanssi2`) or reboot. Alternatively roll the AMI deliberately (see above).
2. **Disk.** Every deploy prunes unused Docker images older than a week after the health check passes; watch the disk alarm.
3. **Dependencies.** `npm outdated`; before bumping TypeScript check `@pulumi/pulumi`'s peer range (TypeScript 7 is not supported yet);
   keep `@types/node` on the Node major in `.node-version`. Refresh the Caddy version and sha512 in `userdata/bootstrap.sh`, the Temurin
   digests and the RDS CA bundle hash in the backend `Dockerfile`, and review Aurora and Node release calendars.
4. **Checks.** `npm run typecheck`, `npm run lint`, `npm run fmt:check`, `npm run shellcheck`, then `pulumi preview` before any `up`.

## Backend releases

The release scripts require Docker Buildx, AWS CLI credentials with ECR and Systems Manager access, and Pulumi access to the deployed
stack. They read the repository, architecture, region and instance from stack outputs/config. The backend directory must have no
uncommitted changes so each image maps to a source revision.

Publishing builds for the configured EC2 architecture, pushes a unique immutable ECR tag, resolves its digest and prints the
digest-pinned image reference. It then deploys that reference through Systems Manager; the host waits for EC2 bootstrap, pulls the
image, updates the image reference, restarts the service and checks `/api/health/live`. A failed health check restores the previous
image reference and restarts the previous release. Set `DEPLOY_BACKEND=false` to publish without deploying.

```bash
./infra/scripts/publish-backend-image.sh
```

## Frontend releases

`publish-frontend.sh` builds the frontend (`deno task build`, which needs the untracked `finanssi2-web/.env.production`), uploads hashed
assets to the S3 bucket first, then `index.html` with `no-cache`, and invalidates `/index.html` on CloudFront when the distribution
exists. Assets that are not part of the build are deleted once they are older than `ASSET_RETENTION_DAYS` (default 30), which keeps
bundles for visitors holding an older `index.html`; the bucket has no expiry rule, so a frontend left untouched keeps working. The
frontend directory must have no uncommitted changes. It needs AWS credentials, Deno, jq and Pulumi access to the stack.

The CloudFront response headers policy sets HSTS, `nosniff`, frame denial and a CSP that allows the API origin, `wss:` to the API,
Firebase sign-in and the Google popup. If sign-in ever breaks after a change, set `frontendCspReportOnly: true` in the stack config
and `pulumi up`: the policy is then sent as `Content-Security-Policy-Report-Only`, and the browser console lists what it would block.

```bash
./infra/scripts/publish-frontend.sh
```

## DNS handoff (Domainhotelli)

Records are added manually; Domainhotelli's form strips the trailing `.bizhop.fi` from names. Values come from stack outputs:

| Name | Type | Target |
| --- | --- | --- |
| ACM validation name (`pulumi stack output frontendCertificateDnsRecords`) | CNAME | ACM validation target; keep permanently for certificate renewal |
| `api.finanssi` | A | `pulumi stack output backendElasticIp` |
| `finanssi` | CNAME | `pulumi stack output frontendDistributionHostname` (after enabling `createFrontendDistribution`) |

## Troubleshooting

- **`curl https://api.finanssi.bizhop.fi` fails with a TLS "internal error" while port 80 redirects:** Caddy has no certificate yet.
  This happens when the instance bootstrapped before the API A record existed, so the first ACME attempt failed. Once the name
  resolves, `sudo systemctl restart caddy` on the host (via SSM) forces a new attempt; check `journalctl -u caddy`.
- **First request after idle is slow:** Aurora auto-pause resumes the database on demand (about 15 seconds or more).

## Current state and gaps

The production stack is live on ARM64 (2026-10-14): `https://finanssi.bizhop.fi` serves the frontend through CloudFront, and
`https://api.finanssi.bizhop.fi` answers `/api/health/live` through Caddy. The t4g.small host runs Amazon Linux 2023 on `aarch64` and
uses a multi-platform backend image. After replacement, backend startup took about 51 seconds; requests through Caddy returned 502
until Spring finished starting, after which public liveness and a previously started debug game loaded successfully. The first request
after an Aurora idle period can still be slow while the database resumes.

- Not yet measured: Aurora pause/resume timing, backend memory under real gameplay on the 2 GiB ARM64 host, CPU-credit behavior and reboot recovery.
- Live multiplayer acceptance with friends is in progress; see the backend and frontend STATUS files.
- The recorded pre-migration cost window totaled USD 3.41 before tax; see [the cost baseline](cost-baseline-before-arm64.md) before making comparisons.
