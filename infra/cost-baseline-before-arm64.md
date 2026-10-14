# AWS cost baseline before ARM64

Recorded 2026-10-14 from `plan/costs-before-arm64-transfer.png`, before the EC2 move from `t3.small` to `t4g.small`.
The screenshot does not show the billing period. These are pre-tax charges for the displayed period, not monthly estimates.

| Service | Usage details | Amount (USD) |
| --- | --- | ---: |
| Elastic Compute Cloud | Linux/UNIX `t3.small`: 103.998 instance-hours at $0.0216/hour ($2.25); gp3 EBS: 1.687 GB-month at $0.0836/GB-month ($0.14) | 2.39 |
| Virtual Private Cloud | In-use public IPv4: 105.945 hours at $0.005/hour ($0.53); idle public IPv4: 0.537 hours at $0.005/hour ($0.00) | 0.53 |
| Relational Database Service | Aurora Serverless v2: 2.152 ACU-hours at $0.14/ACU-hour ($0.30); 92,692 I/O requests at $0.21/million ($0.02); consumed storage 0.007 GB-month at $0.11/GB-month ($0.00); backup storage 0.001 GB-month at $0.02/GB-month ($0.00) | 0.32 |
| Secrets Manager | 0.43 Secrets at $0.40/Secret ($0.17); 79 API requests at $0.05/10,000 requests ($0.00) | 0.17 |
| **Total** | **13 active services; displayed line items may round independently** | **3.41** |

Use this as the observed pre-migration comparison point. Compare equivalent elapsed periods after ARM64 migration and account for workload,
Aurora activity, public IPv4 hours, and billing rounding before attributing any difference to the EC2 instance type.

## Cost Explorer month-to-date snapshot

The account-wide Cost Explorer query for `2026-10-01` through `2026-10-14` (end date exclusive) returned `Estimated: true` and these
service totals. The grouped response did not include `Total.UnblendedCost`, so totals below are sums of the service amounts.

| Service | Unblended cost (USD) |
| --- | ---: |
| AWS Secrets Manager | 0.1725481858 |
| Amazon EC2 Container Registry (ECR) | 0.0029998610 |
| EC2 - Other | 0.1410618157 |
| Amazon EC2 - Compute | 2.2463604823 |
| Amazon Relational Database Service | 0.3215670114 |
| Amazon Simple Storage Service | 0.0003553830 |
| Amazon Virtual Private Cloud | 0.5324111150 |
| AWS Certificate Manager | 0.0000000000 |
| AWS Glue | 0.0000000000 |
| AWS Key Management Service | 0.0000000000 |
| Amazon CloudFront | 0.0000000000 |
| Amazon Simple Notification Service | 0.0000000000 |
| AmazonCloudWatch | 0.0000000000 |
| **Pre-tax sum** | **3.4173038542** |
| Tax | 0.8700000000 |
| **Sum including tax** | **4.2873038542** |

Cost Explorer's pre-tax sum rounds to $3.42, close to the screenshot's $3.41; the small difference is consistent with estimate updates
and displayed rounding. Use the pre-tax sum when comparing with the screenshot. The report currently spans pre-migration usage through
October 13; query a period ending after the migration to measure ARM64 costs.
