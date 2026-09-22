#!/usr/bin/env bash
set -euo pipefail
base="${1:?Usage: bash scripts/smoke-test.sh https://YOUR_HOST}"
base="${base%/}"
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
curl --fail --silent --show-error "$base/actuator/health/readiness" | grep -q '"status":"UP"'
for route in menu admin/login platform/login dashboard; do
  curl --fail --silent --show-error "$base/$route" > "$temp/page"
  grep -q 'id="root"' "$temp/page"
  grep -q '/assets/' "$temp/page"
done
asset="$(grep -oE '/assets/[^" ]+\.js' "$temp/page" | head -n 1)"
curl --fail --silent --show-error "$base$asset" -o /dev/null
curl --fail --silent --show-error "$base/api/auth/csrf" | grep -q 'token'
curl --fail --silent --show-error "$base/ws-orders/info" | grep -q 'websocket'
status="$(curl --silent --show-error -o "$temp/api" -w '%{http_code}' "$base/api/platform/overview")"
test "$status" = 401
grep -q '"status":401' "$temp/api"
status="$(curl --silent --show-error -o /dev/null -w '%{http_code}' "$base/assets/does-not-exist.js")"
test "$status" = 404
printf 'Smoke checks passed for %s\n' "$base"
