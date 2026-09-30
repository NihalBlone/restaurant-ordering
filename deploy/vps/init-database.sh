#!/usr/bin/env bash
set -euo pipefail
: "${APP_DATABASE_PASSWORD:?Set APP_DATABASE_PASSWORD}"

# Runs only on a NEW PostgreSQL volume. The application is never a DB superuser.
psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 \
  --set app_password="$APP_DATABASE_PASSWORD" <<'SQL'
CREATE ROLE restaurant_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
CREATE DATABASE restaurant_ordering OWNER restaurant_app;
REVOKE ALL ON DATABASE restaurant_ordering FROM PUBLIC;
SQL
