#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

if [[ ! -f finanssi2-firebase-adminsdk.json ]]; then
  printf '%s\n' 'Missing finanssi2-firebase-adminsdk.json. Add the local Firebase service account key first.' >&2
  exit 1
fi

docker compose build backend
docker compose up -d
