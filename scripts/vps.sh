#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${VPS_ENV_FILE:-/opt/servemytable/.env}"
if [ ! -f "$env_file" ]; then
  printf 'Missing private configuration: %s\nRun scripts/configure-vps.py first.\n' "$env_file" >&2
  exit 1
fi
exec docker compose --env-file "$env_file" -f "$root/deploy/vps/compose.yml" "$@"
