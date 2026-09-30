#!/usr/bin/env bash
set -euo pipefail
if [ "${GITHUB_ACTIONS:-}" != true ]; then
  printf 'This disposable-volume test may run only in GitHub Actions, never on the live server.\n' >&2
  exit 1
fi
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export APP_IMAGE="${1:?Pass the locally built release image}"
temp="$(mktemp -d)"
export VPS_ENV_FILE="$temp/.env"
export COMPOSE_PROJECT_NAME=servemytable-vps-ci

vps() { bash "$root/scripts/vps.sh" -p servemytable-vps-ci "$@"; }
cleanup() {
  result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then vps logs --no-color --tail=100 || true; fi
  vps down --volumes --remove-orphans || true
  rm -rf "$temp"
  exit "$result"
}
trap cleanup EXIT

python3 - "$root" "$VPS_ENV_FILE" <<'PY'
import importlib.util
import sys
from pathlib import Path
spec = importlib.util.spec_from_file_location("config", Path(sys.argv[1]) / "scripts/configure-vps.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
values = module.configuration("orders.example.com", "ghcr.io/example/app@sha256:" + "a" * 64,
                              "owner@example.com", "orders@mail.example.com", "re_ci_test_only")
module.write_configuration(sys.argv[2], values)
PY

vps up -d --wait --wait-timeout 300 database app
vps exec -T app bash -s -- http://127.0.0.1:8080 < "$root/scripts/smoke-test.sh"
sql() { vps exec -T database psql -U postgres -d restaurant_ordering -Atc "$1"; }
test "$(sql "SELECT count(*) FROM pg_roles WHERE rolname='restaurant_app' AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole")" = 1
test "$(sql 'SELECT count(*) FROM restaurants')" = 0
test "$(sql "SELECT count(*) FROM restaurant_admins WHERE role='PLATFORM_ADMIN'")" = 1
vps exec -T --user 10001:10001 app sh -c 'printf vps-ci > /var/data/menu-images/.vps-ci-marker'

# The owner and photos must survive a container replacement with bootstrap disabled.
export APP_PLATFORM_USERNAME= APP_PLATFORM_EMAIL= APP_PLATFORM_PASSWORD=
vps up -d --no-deps --force-recreate --wait --wait-timeout 300 app
test "$(sql "SELECT count(*) FROM restaurant_admins WHERE role='PLATFORM_ADMIN'")" = 1
test "$(vps exec -T app cat /var/data/menu-images/.vps-ci-marker)" = vps-ci
vps exec -T app bash -s -- http://127.0.0.1:8080 < "$root/scripts/smoke-test.sh"
bash "$root/scripts/backup-vps.sh" "$temp/backup"
vps exec -T database createdb -U postgres -O restaurant_app restore_check
vps exec -T database pg_restore -U restaurant_app -d restore_check --no-owner --no-acl --exit-on-error \
  < "$temp/backup/database.dump"
test "$(vps exec -T database psql -U restaurant_app -d restore_check -Atc "SELECT count(*) FROM restaurant_admins WHERE role='PLATFORM_ADMIN'")" = 1
test "$(tar -xOzf "$temp/backup/photos.tar.gz" menu-images/.vps-ci-marker)" = vps-ci
vps run -T --rm --no-deps proxy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
printf 'Production-profile VPS smoke checks passed. External DNS, HTTPS, SMTP, and load tests remain.\n'
