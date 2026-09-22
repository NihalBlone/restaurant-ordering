#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destination="${1:-$root/.frontend}"
ref="$(tr -d '\r\n' < "$root/deploy/frontend.ref")"
if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
    printf 'deploy/frontend.ref must contain one full lowercase Git commit SHA.\n' >&2
    exit 1
fi
if [ -e "$destination" ]; then
    printf 'Destination already exists: %s. Choose a new empty path; no files were changed.\n' "$destination" >&2
    exit 1
fi
export GIT_TERMINAL_PROMPT=0
git init --quiet "$destination"
git -C "$destination" fetch --depth 1 https://github.com/NihalBlone/restaurant_ordering_UI.git "$ref"
git -C "$destination" checkout --quiet --detach FETCH_HEAD
test "$(git -C "$destination" rev-parse HEAD)" = "$ref"
printf 'Fetched UI commit %s into %s\n' "$ref" "$destination"
