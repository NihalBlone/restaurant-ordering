#!/usr/bin/env bash
set -euo pipefail
umask 077
output="${1:?Usage: bash scripts/backup-database.sh backups/name.dump}"
: "${PGHOST:?Set PGHOST}"
: "${PGDATABASE:?Set PGDATABASE}"
: "${PGUSER:?Set PGUSER}"
export PGSSLMODE="${PGSSLMODE:-require}"
command -v pg_dump >/dev/null || { printf 'Install PostgreSQL 16 client tools first.\n' >&2; exit 1; }
if [ -e "$output" ]; then
    printf 'Refusing to overwrite existing backup: %s\n' "$output" >&2
    exit 1
fi
mkdir -p "$(dirname "$output")"
partial="$(mktemp "${output}.partial.XXXXXX")"
trap 'rm -f "$partial"' EXIT
pg_dump --no-password --format=custom --no-owner --no-acl --file="$partial"
mv "$partial" "$output"
printf 'Backup created: %s. Encrypt and move it to your approved offsite backup store.\n' "$output"
