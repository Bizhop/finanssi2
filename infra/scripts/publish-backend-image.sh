#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$script_dir/lib.sh"

require_commands aws docker git jq pulumi

infra_dir="$script_dir/.."
repo_root=$(cd -- "$script_dir/../.." && pwd)
backend_dir="$repo_root/finanssi2-backend"
stack=${PULUMI_STACK:-prod}
deploy_backend=${DEPLOY_BACKEND:-true}

case "$deploy_backend" in
    true|false) ;;
    *) fail "DEPLOY_BACKEND must be true or false" ;;
esac

backend_changes=$(git -C "$repo_root" status --porcelain -- finanssi2-backend)
if [[ -n "$backend_changes" ]]; then
    fail "commit or stash backend changes before publishing an image"
fi

load_stack_outputs "$infra_dir" "$stack"
repository_url=$(stack_output backendRepositoryUrl)
architecture=$(stack_output selectedArchitecture)
region=$(stack_region "$infra_dir" "$stack")
validate_repository_url "$repository_url"

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
    *) fail "DEPLOY_BACKEND must be true or false" ;;
esac
