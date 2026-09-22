#!/usr/bin/env bash
# Run with Bash, including when the interactive shell is zsh.
set +x
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -t 0 ]]; then
  printf '%s\n' 'Run this script in an interactive terminal; do not pipe passwords into it.' >&2
  exit 1
fi
if [[ -n "${SPRING_PROFILES_ACTIVE:-}" && "${SPRING_PROFILES_ACTIVE}" != "local" ]]; then
  printf '%s\n' 'This helper is for the local profile only. Use the deployment guide for other profiles.' >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  printf '%s\n' 'Maven is not on PATH. Install/configure Maven before running this script.' >&2
  exit 1
fi

printf '%s\n' 'Local platform-owner setup' \
  'This starts the backend; it does not stop another running backend.' \
  'An existing account keeps its original password. This is not a password-reset tool.'

while true; do
  read -r -p 'Platform username [owner]: ' username
  username="${username:-owner}"
  if [[ "$username" =~ ^[A-Za-z0-9._-]{3,80}$ ]]; then break; fi
  printf '%s\n' 'Use 3-80 letters, numbers, dots, underscores, or hyphens.'
done

while true; do
  read -r -p 'Your email address: ' email
  if [[ ${#email} -le 254 && "$email" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then break; fi
  printf '%s\n' 'Enter a valid email address, for example you@example.com.'
done

while true; do
  read -r -s -p 'Choose a unique platform password (14-72 characters; input is hidden): ' password
  printf '\n'
  password_bytes=$(LC_ALL=C printf '%s' "$password" | wc -c | tr -d ' ')
  if [[ ${#password} -lt 14 || ${#password} -gt 72 || $password_bytes -gt 72 || ! "$password" =~ [^[:space:]] ]]; then
    printf '%s\n' 'Use 14-72 characters (at most 72 UTF-8 bytes). Admin@12345 is too short.'
    continue
  fi
  read -r -s -p 'Confirm password: ' confirmation
  printf '\n'
  if [[ "$password" == "$confirmation" ]]; then break; fi
  printf '%s\n' 'Passwords did not match. Please try again.'
done

export APP_PLATFORM_USERNAME="$username"
export APP_PLATFORM_EMAIL="$email"
export APP_PLATFORM_PASSWORD="$password"
unset password confirmation

printf '\n%s\n' 'Starting Spring Boot with the local profile.' \
  'Once startup completes, open http://localhost:5173/platform/login.' \
  'Use the username above and your chosen password. No password is written to a file.'
exec mvn -s .mvn/settings-public.xml spring-boot:run -Dspring-boot.run.profiles=local
