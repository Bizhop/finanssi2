#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

for command in aws deno pulumi; do
    command -v "$command" >/dev/null 2>&1 || fail "required command not found: $command"
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)
web_dir="$repo_root/finanssi2-web"
infra_dir="$script_dir/.."
stack=${PULUMI_STACK:-prod}

[[ -f "$web_dir/.env.production" ]] || fail "missing $web_dir/.env.production"

bucket=$(cd "$infra_dir" && pulumi stack output frontendBucketName --stack "$stack")
region=$(cd "$infra_dir" && pulumi config get aws:region --stack "$stack")
distribution_host=$(cd "$infra_dir" && pulumi stack output frontendDistributionHostname --stack "$stack")
[[ "$bucket" =~ ^[a-z0-9.-]+$ ]] || fail "unexpected bucket name from stack output"
[[ "$region" =~ ^[a-z0-9-]+$ ]] || fail "unexpected AWS region from stack config"

aws sts get-caller-identity --output text >/dev/null
(cd "$web_dir" && deno task build)
[[ -f "$web_dir/dist/index.html" ]] || fail "build did not produce dist/index.html"

# Hashed assets first, so the new index never references a missing file. Old assets are kept; the bucket lifecycle expires them.
aws s3 sync "$web_dir/dist/assets" "s3://$bucket/assets" --region "$region" \
    --cache-control "public, max-age=31536000, immutable"
aws s3 cp "$web_dir/dist/index.html" "s3://$bucket/index.html" --region "$region" \
    --cache-control "no-cache" --content-type "text/html; charset=utf-8"

# Other top-level files (favicon etc.), if the build emits any.
find "$web_dir/dist" -maxdepth 1 -type f ! -name index.html -print0 | while IFS= read -r -d '' file; do
    aws s3 cp "$file" "s3://$bucket/$(basename "$file")" --region "$region" --cache-control "public, max-age=3600"
done

if [[ "$distribution_host" == "null" || -z "$distribution_host" ]]; then
    printf 'Uploaded to s3://%s. The CloudFront distribution is not created yet; no invalidation needed.\n' "$bucket"
    exit 0
fi

distribution_id=$(aws cloudfront list-distributions \
    --query "DistributionList.Items[?DomainName=='$distribution_host'].Id | [0]" --output text)
[[ "$distribution_id" =~ ^[A-Z0-9]+$ ]] || fail "could not find the CloudFront distribution for $distribution_host"
aws cloudfront create-invalidation --distribution-id "$distribution_id" --paths "/index.html" >/dev/null
printf 'Published frontend to s3://%s and invalidated /index.html on %s.\n' "$bucket" "$distribution_id"
