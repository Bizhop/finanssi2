# Production infrastructure

This TypeScript Pulumi project defines the first production stack modules: a two-AZ VPC, private Aurora PostgreSQL, ECR, a
single x86-64 EC2 backend, Secrets Manager inputs, a private S3 frontend bucket, and a staged CloudFront certificate/distribution.
The current default host is `t3.small` (`x86_64`). The stack also defines bounded CloudWatch log retention, host CPU/memory/disk/credit
alarms, backend restart alarms and a monthly cost budget with email notifications. No AWS resources are created by the local
TypeScript build.

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

`pulumi preview` and `pulumi up` have not been run from this environment. The code is not an authorization to create AWS resources;
review the resource and cost preview before deployment. The first stack stage creates billable AWS resources even while the frontend
distribution is disabled. Do not run `pulumi up` until the deployment plan's DNS, account, cost-alert and secret configuration is ready.

The EC2 image reference is intentionally absent at bootstrap. It installs host services and waits for a later release script to set
an immutable ECR digest through SSM. The host exposes only 80/443; SSH and PostgreSQL are not public, and Aurora accepts traffic only
from the backend security group. S3 is private and accepts reads only through CloudFront when the distribution stage is enabled.

## Current gaps

- ECR release/publish and SSM forward-deployment helpers are not implemented yet.
- Account-specific budget amount/email still require operator values; service restart monitoring relies on host syslog forwarding.
- The frontend upload/cache publication workflow and Domainhotelli DNS handoff remain manual work.
- AWS-side IAM, engine-version availability, security-group behavior, certificate validation, and real Aurora pause/resume have not
  been validated with an AWS account.
