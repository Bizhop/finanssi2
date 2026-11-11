#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    printf 'Usage: %s START_DATE END_DATE [OUTPUT_FILE]\n' "$0" >&2
    printf 'Dates use YYYY-MM-DD; END_DATE is exclusive. Example: %s 2026-10-14 2026-10-27\n' "$0" >&2
    exit 2
fi

start_date=$1
end_date=$2
for date in "$start_date" "$end_date"; do
    [[ "$date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || {
        printf 'error: dates must use YYYY-MM-DD\n' >&2
        exit 2
    }
done

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
reports_dir="$script_dir/reports"
output_file=${3:-"$reports_dir/cost-report-$start_date-to-$end_date.json"}

if [[ $# -eq 2 ]]; then
    mkdir -p "$reports_dir"
fi

aws ce get-cost-and-usage \
    --time-period "Start=$start_date,End=$end_date" \
    --granularity MONTHLY \
    --metrics UnblendedCost \
    --group-by Type=DIMENSION,Key=SERVICE \
    --region us-east-1 \
    --output json > "$output_file"

printf 'Saved cost report to %s\n' "$output_file"
