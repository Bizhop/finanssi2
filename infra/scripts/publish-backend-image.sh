#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

for command in aws docker git pulumi; do
    command -v "$command" >/dev/null 2>&1 || fail "required command not found: $command"
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)
backend_dir="$repo_root/finanssi2-backend"
stack=${PULUMI_STACK:-prod}
deploy_backend=${DEPLOY_BACKEND:-true}

case "$deploy_backend" in
    true|false) ;;
    *) fail "DEPLOY_BACKEND must be true or false" ;;
esac

if [[ -n "$(git -C "$repo_root" status --porcelain -- finanssi2-backend)" ]]; then
    fail "commit or stash backend changes before publishing an image"
fi

repository_url=$(cd "$script_dir/.." && pulumi stack output backendRepositoryUrl --stack "$stack")
region=$(cd "$script_dir/.." && pulumi config get aws:region --stack "$stack")
architecture=$(cd "$script_dir/.." && pulumi stack output selectedArchitecture --stack "$stack")

[[ "$repository_url" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9._/-]+$ ]] \
    || fail "unexpected ECR repository URL from stack output"
[[ "$region" =~ ^[a-z0-9-]+$ ]] || fail "unexpected AWS region from stack config"

case "$architecture" in
    x86_64) platform=linux/amd64 ;;
    arm64) platform=linux/arm64 ;;
    *) fail "unsupported backend architecture: $architecture" ;;
esac

aws sts get-caller-identity --output text >/dev/null
registry=${repository_url%%/*}
aws ecr get-login-password --region "$region" \
    | docker login --username AWS --password-stdin "$registry" >/dev/null

revision=$(git -C "$repo_root" rev-parse --short=12 HEAD)
release_tag="${revision}-$(date -u +%Y%m%dT%H%M%SZ)-$$-${RANDOM}"
docker buildx build \
    --platform "$platform" \
    --tag "$repository_url:$release_tag" \
    --push \
    "$backend_dir" >&2

repository_name=${repository_url##*/}
digest=$(aws ecr describe-images \
    --region "$region" \
    --repository-name "$repository_name" \
    --image-ids "imageTag=$release_tag" \
    --query 'imageDetails[0].imageDigest' \
    --output text)
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || fail "could not resolve the pushed image digest"

printf '%s@%s\n' "$repository_url" "$digest"
image_ref="$repository_url@$digest"

case "$deploy_backend" in
    true) "$script_dir/deploy-backend-image.sh" "$image_ref" >&2 ;;
    false) ;;
esac
