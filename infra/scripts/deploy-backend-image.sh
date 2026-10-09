#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

[[ $# -eq 1 ]] || fail "usage: $0 <repository-uri@sha256:digest>"
for command in aws python3 pulumi; do
    command -v "$command" >/dev/null 2>&1 || fail "required command not found: $command"
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
infra_dir=$(cd -- "$script_dir/.." && pwd)
stack=${PULUMI_STACK:-prod}
image_ref=$1

repository_url=$(cd "$infra_dir" && pulumi stack output backendRepositoryUrl --stack "$stack")
instance_id=$(cd "$infra_dir" && pulumi stack output backendInstanceId --stack "$stack")
region=$(cd "$infra_dir" && pulumi config get aws:region --stack "$stack")

[[ "$repository_url" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9._/-]+$ ]] \
    || fail "unexpected ECR repository URL from stack output"
[[ "$image_ref" == "$repository_url"@sha256:* ]] || fail "image must belong to the stack's backend repository"
digest=${image_ref#*@}
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || fail "image must use a valid immutable SHA-256 digest"
[[ "$instance_id" =~ ^i-[a-f0-9]+$ ]] || fail "unexpected EC2 instance ID from stack output"
[[ "$region" =~ ^[a-z0-9-]+$ ]] || fail "unexpected AWS region from stack config"

aws sts get-caller-identity --output text >/dev/null
ping_status=$(aws ssm describe-instance-information \
    --region "$region" \
    --filters "Key=InstanceIds,Values=$instance_id" \
    --query 'InstanceInformationList[0].PingStatus' \
    --output text)
[[ "$ping_status" == Online ]] || fail "backend instance is not online in Systems Manager (status: $ping_status)"

parameters_file=$(mktemp "${TMPDIR:-/tmp}/finanssi2-ssm-parameters.XXXXXX")
trap 'rm -f "$parameters_file"' EXIT
chmod 0600 "$parameters_file"
IMAGE_REF="$image_ref" python3 - "$parameters_file" <<'PY'
import json
import os
import sys

image_ref = os.environ["IMAGE_REF"]
remote_script = r'''#!/usr/bin/env bash
set -eu
IMAGE_REF='@@IMAGE_REF@@'
REF_FILE=/etc/finanssi2/image-ref
TEMP_FILE="${REF_FILE}.new"
OLD_REF=$(cat "$REF_FILE" 2>/dev/null || true)

rollback() {
    if [ -n "$OLD_REF" ]; then
        printf '%s\n' "$OLD_REF" > "$TEMP_FILE"
        chmod 0644 "$TEMP_FILE"
        mv -f "$TEMP_FILE" "$REF_FILE"
        systemctl restart finanssi2 || true
    else
        rm -f "$TEMP_FILE" "$REF_FILE"
        systemctl stop finanssi2 || true
    fi
}

docker pull "$IMAGE_REF"
printf '%s\n' "$IMAGE_REF" > "$TEMP_FILE"
chmod 0644 "$TEMP_FILE"
mv -f "$TEMP_FILE" "$REF_FILE"

if ! systemctl restart finanssi2; then
    echo 'Backend service restart failed; restoring the previous image' >&2
    rollback
    exit 1
fi

for attempt in $(seq 1 120); do
    if curl --fail --silent http://127.0.0.1:8080/api/health/live >/dev/null; then
        echo "Backend is healthy on $IMAGE_REF"
        exit 0
    fi
    sleep 5
done

echo 'Backend did not become healthy; restoring the previous image' >&2
rollback
exit 1
'''.replace('@@IMAGE_REF@@', image_ref)

with open(sys.argv[1], "w", encoding="utf-8") as output:
    json.dump({"commands": [remote_script], "executionTimeout": ["1800"]}, output)
PY

command_id=$(aws ssm send-command \
    --region "$region" \
    --instance-ids "$instance_id" \
    --document-name AWS-RunShellScript \
    --comment "Deploy Finanssi backend ${digest#sha256:}" \
    --timeout-seconds 1800 \
    --parameters "file://$parameters_file" \
    --query 'Command.CommandId' \
    --output text)
printf 'SSM command: %s\n' "$command_id" >&2

deadline=$((SECONDS + 1860))
while (( SECONDS < deadline )); do
    status=$(aws ssm get-command-invocation \
        --region "$region" \
        --command-id "$command_id" \
        --instance-id "$instance_id" \
        --query Status \
        --output text 2>/dev/null || true)
    case "$status" in
        Success)
            aws ssm get-command-invocation \
                --region "$region" \
                --command-id "$command_id" \
                --instance-id "$instance_id" \
                --query StandardOutputContent \
                --output text
            exit 0
            ;;
        Failed|Cancelled|TimedOut|DeliveryTimedOut|ExecutionTimedOut|Undeliverable|Terminated)
            details=$(aws ssm get-command-invocation --region "$region" --command-id "$command_id" \
                --instance-id "$instance_id" --query StatusDetails --output text 2>/dev/null || true)
            stderr=$(aws ssm get-command-invocation --region "$region" --command-id "$command_id" \
                --instance-id "$instance_id" --query StandardErrorContent --output text 2>/dev/null || true)
            stdout=$(aws ssm get-command-invocation --region "$region" --command-id "$command_id" \
                --instance-id "$instance_id" --query StandardOutputContent --output text 2>/dev/null || true)
            printf '%s\n%s\n%s\n' "$details" "$stderr" "$stdout" >&2
            fail "backend deployment command ended with status $status"
            ;;
        *) sleep 5 ;;
    esac
done

fail "timed out waiting for SSM command $command_id"
