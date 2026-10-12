# Production infrastructure

This TypeScript Pulumi project defines the production stack: a two-AZ VPC, private Aurora PostgreSQL, ECR, a
single x86-64 EC2 backend, Secrets Manager inputs, a private S3 frontend bucket, and a staged CloudFront certificate/distribution.
The current default host is `t3.small` (`x86_64`). The stack also defines bounded CloudWatch log retention, host CPU/memory/disk/credit
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
npm run build
```

Create a local production stack config from `Pulumi.prod.yaml.example`, replace the public Firebase web placeholders, then initialize
the stack and store the Firebase Admin service-account JSON with Pulumi's `--secret` option. The example is ignored by Git along
with real `Pulumi.*.yaml` files. Never place Firebase Admin JSON in the stack YAML as plaintext or in `firebaseWeb`.

The stack uses `aws:region` with a default of `eu-north-1`. Set `monthlyBudgetLimitUsd` to the alert amount you choose and
`budgetAlertEmail` to the recipient address. Confirm the SNS subscription email after deployment. Check that the configured Aurora PostgreSQL engine version is currently
available in that region and supports Serverless v2 auto-pause before previewing. `createFrontendDistribution` defaults to `false`:
the first stage requests the ACM certificate and exports its validation CNAME without waiting for Domainhotelli DNS. After that CNAME
resolves publicly, enable the distribution in the local stack config and preview again.

The `prod` stack is deployed. The backend image has been pushed to ECR and deployed through SSM; the backend service reported that
the app started. `frontendDistributionHostname` currently returns `null` because the CloudFront distribution is still staged/disabled.
The first stack stage creates billable AWS resources even while the frontend distribution is disabled. Review the resource and cost
preview before future infrastructure changes.

The EC2 image reference is intentionally absent at bootstrap. It installs host services and waits for the release helpers below to
set an immutable ECR digest through SSM. The host exposes only 80/443; SSH and PostgreSQL are not public, and Aurora accepts traffic
only from the backend security group. S3 is private and accepts reads only through CloudFront when the distribution stage is enabled.

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
exists. It needs AWS credentials, Deno and Pulumi access to the stack.

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

The production stack is live (2026-10-12): `https://finanssi.bizhop.fi` serves the frontend through CloudFront, and
`https://api.finanssi.bizhop.fi` answers `/api/health/live` through Caddy. Sign-in works; the first request after an idle period is
slow while Aurora resumes.

- Service restart monitoring relies on host syslog forwarding.
- Not yet measured: Aurora pause/resume timing and backend memory under real gameplay on the 2 GiB host, EC2 reboot behavior.
- Live multiplayer acceptance with friends is in progress; see the backend and frontend STATUS files.
