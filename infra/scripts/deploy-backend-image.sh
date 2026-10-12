#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
# shellcheck source-path=SCRIPTDIR
source "$script_dir/lib.sh"

[[ $# -eq 1 ]] || fail "usage: $0 <repository-uri@sha256:digest>"
require_commands aws jq python3 pulumi

infra_dir=$(cd -- "$script_dir/.." && pwd)
stack=${PULUMI_STACK:-prod}
image_ref=$1

load_stack_outputs "$infra_dir" "$stack"
repository_url=$(stack_output backendRepositoryUrl)
instance_id=$(stack_output backendInstanceId)
image_ref_parameter=$(stack_output backendImageRefParameter)
region=$(stack_region "$infra_dir" "$stack")

validate_repository_url "$repository_url"
[[ "$image_ref" == "$repository_url"@sha256:* ]] || fail "image must belong to the stack's backend repository"
digest=${image_ref#*@}
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || fail "image must use a valid immutable SHA-256 digest"
[[ "$instance_id" =~ ^i-[a-f0-9]+$ ]] || fail "unexpected EC2 instance ID from stack output"
[[ "$image_ref_parameter" =~ ^/[A-Za-z0-9_./-]+$ ]] || fail "unexpected image reference parameter name from stack output"

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
set -euo pipefail
IMAGE_REF='@@IMAGE_REF@@'
REF_FILE=/etc/finanssi2/image-ref
TEMP_FILE="${REF_FILE}.new"
OLD_REF=$(cat "$REF_FILE" 2>/dev/null || true)

if command -v cloud-init >/dev/null 2>&1; then
    # Exit status 2 means a recoverable error ("degraded"); anything else non-zero is a failed bootstrap.
    cloud_init_status=0
    cloud-init status --wait || cloud_init_status=$?
    if [ "$cloud_init_status" -eq 2 ]; then
        echo 'cloud-init finished degraded; continuing (see /var/log/cloud-init-output.log)' >&2
    elif [ "$cloud_init_status" -ne 0 ]; then
        echo 'EC2 bootstrap failed; inspect /var/log/cloud-init-output.log' >&2
        if [ -r /var/log/cloud-init-output.log ]; then
            tail -n 120 /var/log/cloud-init-output.log >&2
        fi
        exit 1
    fi
fi
if ! command -v docker >/dev/null 2>&1; then
    echo 'Docker is missing after EC2 bootstrap; inspect /var/log/cloud-init-output.log' >&2
    if [ -r /var/log/cloud-init-output.log ]; then
        tail -n 120 /var/log/cloud-init-output.log >&2
    fi
    exit 127
fi

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

source /etc/finanssi2/host.env
aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REPOSITORY_URL" >/dev/null
docker pull "$IMAGE_REF"
printf '%s\n' "$IMAGE_REF" > "$TEMP_FILE"
chmod 0644 "$TEMP_FILE"
mv -f "$TEMP_FILE" "$REF_FILE"

if ! systemctl restart finanssi2; then
    echo 'Backend service restart failed; restoring the previous image' >&2
    rollback
    exit 1
fi

for _ in $(seq 1 120); do
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

invocation() {
    aws ssm get-command-invocation --region "$region" --command-id "$command_id" --instance-id "$instance_id" --output json 2>/dev/null || true
}

deadline=$((SECONDS + 1860))
while ((SECONDS < deadline)); do
    result=$(invocation)
    status=$(jq -r '.Status // empty' <<<"$result")
    case "$status" in
        Success)
            jq -r '.StandardOutputContent' <<<"$result"
            # Recorded only after the health check passed, so a replaced host starts the last good image.
            aws ssm put-parameter --region "$region" --name "$image_ref_parameter" --type String --value "$image_ref" --overwrite >/dev/null
            exit 0
            ;;
        Failed | Cancelled | TimedOut | DeliveryTimedOut | ExecutionTimedOut | Undeliverable | Terminated)
            jq -r '[.StatusDetails, .StandardErrorContent, .StandardOutputContent] | join("\n")' <<<"$result" >&2
            fail "backend deployment command ended with status $status"
            ;;
        *) sleep 5 ;;
    esac
done

fail "timed out waiting for SSM command $command_id"
