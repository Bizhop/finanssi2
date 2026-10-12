#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

for command in aws deno jq pulumi; do
    command -v "$command" >/dev/null 2>&1 || fail "required command not found: $command"
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)
web_dir="$repo_root/finanssi2-web"
infra_dir="$script_dir/.."
stack=${PULUMI_STACK:-prod}
# Superseded assets stay this long, for clients that still hold an older index.html.
asset_retention_days=${ASSET_RETENTION_DAYS:-30}
[[ "$asset_retention_days" =~ ^[0-9]+$ ]] || fail "ASSET_RETENTION_DAYS must be a whole number"

[[ -f "$web_dir/.env.production" ]] || fail "missing $web_dir/.env.production"

bucket=$(cd "$infra_dir" && pulumi stack output frontendBucketName --stack "$stack")
region=$(cd "$infra_dir" && pulumi config get aws:region --stack "$stack")
distribution_host=$(cd "$infra_dir" && pulumi stack output frontendDistributionHostname --stack "$stack")
[[ "$bucket" =~ ^[a-z0-9.-]+$ ]] || fail "unexpected bucket name from stack output"
[[ "$region" =~ ^[a-z0-9-]+$ ]] || fail "unexpected AWS region from stack config"

aws sts get-caller-identity --output text >/dev/null
(cd "$web_dir" && deno task build)
[[ -f "$web_dir/dist/index.html" ]] || fail "build did not produce dist/index.html"

# Hashed assets first, so the new index never references a missing file. Superseded assets are pruned below.
aws s3 sync "$web_dir/dist/assets" "s3://$bucket/assets" --region "$region" \
    --cache-control "public, max-age=31536000, immutable"
aws s3 cp "$web_dir/dist/index.html" "s3://$bucket/index.html" --region "$region" \
    --cache-control "no-cache" --content-type "text/html; charset=utf-8"

# Delete assets that are not part of this build and are older than the retention period.
cutoff=$(date -u -d "$asset_retention_days days ago" +%Y-%m-%dT%H:%M:%SZ)
current_assets=$(cd "$web_dir/dist/assets" && find . -type f -printf 'assets/%P\n' | jq -R . | jq -s .)
stale_keys=$(aws s3api list-objects-v2 --bucket "$bucket" --prefix assets/ --region "$region" --output json |
    jq -c --arg cutoff "$cutoff" --argjson current "$current_assets" \
        '[(.Contents // [])[] | select(.LastModified < $cutoff and (.Key as $key | $current | index($key) | not)) | {Key}]')
if [[ "$(jq length <<<"$stale_keys")" -gt 0 ]]; then
    printf 'Deleting %s superseded assets older than %s days.\n' "$(jq length <<<"$stale_keys")" "$asset_retention_days"
    jq -c '[range(0; length; 1000) as $i | .[$i:$i + 1000]][]' <<<"$stale_keys" | while IFS= read -r batch; do
        aws s3api delete-objects --bucket "$bucket" --region "$region" \
            --delete "$(jq -c '{Objects: ., Quiet: true}' <<<"$batch")" >/dev/null
    done
fi

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
