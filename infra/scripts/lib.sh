# shellcheck shell=bash
# Shared helpers for the release scripts. Sourced, not executed.

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

require_commands() {
    local command
    for command in "$@"; do
        command -v "$command" >/dev/null 2>&1 || fail "required command not found: $command"
    done
}

# Reads all stack outputs once; read single values with stack_output.
load_stack_outputs() {
    local infra_dir=$1 stack=$2
    stack_outputs=$(cd "$infra_dir" && pulumi stack output --json --stack "$stack")
}

# Prints the output as a string; an unset output or JSON null prints nothing.
stack_output() {
    jq -r --arg name "$1" '.[$name] // empty' <<<"$stack_outputs"
}

stack_region() {
    local infra_dir=$1 stack=$2 region
    region=$(cd "$infra_dir" && pulumi config get aws:region --stack "$stack")
    [[ "$region" =~ ^[a-z0-9-]+$ ]] || fail "unexpected AWS region from stack config"
    printf '%s\n' "$region"
}

validate_repository_url() {
    [[ "$1" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9._/-]+$ ]] || fail "unexpected ECR repository URL from stack output"
}
