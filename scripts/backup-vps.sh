#!/usr/bin/env bash
set -euo pipefail
umask 077
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:?Usage: sudo bash scripts/backup-vps.sh /opt/servemytable/backups/UNIQUE_NAME}"
vps() { bash "$root/scripts/vps.sh" "$@"; }
if [ -e "$output" ]; then
  printf 'Refusing to overwrite backup directory: %s\n' "$output" >&2
  exit 1
fi
mkdir -p "$(dirname "$output")"
mkdir -m 700 "$output"
restart_needed=false
cleanup() {
  result=$?
  trap - EXIT
  if [ "$restart_needed" = true ]; then
    # Older Compose runners support --wait on up, but not on start.
    if ! vps up -d --no-deps --no-recreate --no-build --pull never --wait --wait-timeout 300 app; then
      printf 'App restart failed: inspect readiness immediately.\n' >&2
      result=1
    fi
  fi
  if [ "$result" -ne 0 ]; then
    printf 'Backup/restart failed. Keep the previous backup; inspect %s before use.\n' "$output" >&2
  else
    printf 'Backup created at %s. Encrypt and copy off this server; test restoration separately.\n' "$output"
  fi
  exit "$result"
}
trap cleanup EXIT

# Pause writes so the DB and photo archive describe the same point in time.
if [ -n "$(vps ps --status running -q app)" ]; then
  restart_needed=true
  vps stop app
fi
vps exec -T database pg_dump -U postgres -d restaurant_ordering -Fc --no-owner --no-acl \
  > "$output/database.dump.partial"
vps run -T --rm --no-deps --entrypoint tar app -C /var/data -czf - menu-images \
  > "$output/photos.tar.gz.partial"
vps exec -T database pg_restore --list < "$output/database.dump.partial" > /dev/null
tar -tzf "$output/photos.tar.gz.partial" > /dev/null
mv "$output/database.dump.partial" "$output/database.dump"
mv "$output/photos.tar.gz.partial" "$output/photos.tar.gz"
