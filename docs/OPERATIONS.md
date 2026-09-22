# Operations And Recovery

## Deployment Boundaries

This is a single-instance deployment. Spring's simple STOMP broker and in-process rate limiters are not
distributed. The photo disk is tied to one service instance. Do not increase replicas or enable horizontal
autoscaling until a shared message broker, distributed rate limits, durable event delivery/outbox, and
object storage are implemented. A persistent disk also means deploys can cause an interruption;
plan maintenance outside service hours. See [Render disks](https://render.com/docs/disks).

Events are sent after the database commits, but not stored in a durable outbox. A disconnect/crash can
lose a live notification; REST order history is the source of truth. Test reconnect/refetch behavior
and maintain an outage/manual-order procedure. This architecture is not highly available.

Table QR UUIDs are bearer capabilities, not proof of physical presence. Anyone possessing a valid table
link may use its public menu/order endpoints. Session resets do not revoke a photographed QR. Avoid
publishing table URLs publicly; stronger guest session admission and abuse controls are needed for
hostile/high-volume environments. Test tenant isolation independently before launch.

There is no payment processing, tax invoice engine, refund flow, or automatic subscription billing.
Settled totals mean staff manually recorded external payment, not a gateway-confirmed transaction.
Platform plan/trial fields are metadata. Review the app's currency formatting and restaurant/accounting
requirements before onboarding restaurants in different countries.

## Backups

Use managed PostgreSQL backups/PITR and verify retention in your purchased plan. Also export encrypted
off-provider backups according to your recovery objectives. See [Render recovery](https://render.com/docs/postgresql-backups).
Database backups do not contain photo bytes: preserve `/var/data/menu-images` separately.

The repository includes an export helper requiring PostgreSQL 16 client tools and environment variables:

```sh
export PGHOST=YOUR_DATABASE_HOST
export PGPORT=5432
export PGDATABASE=restaurant_ordering
export PGUSER=restaurant_app
# Set PGPASSWORD through a secret manager or use a mode-0600 PGPASSFILE; do not put it in shell history.
bash scripts/backup-database.sh backups/restaurant-YYYY-MM-DD.dump
pg_restore --list backups/restaurant-YYYY-MM-DD.dump
```

The helper does not schedule uploads or rotate backups. The operator must arrange encrypted offsite
storage, access control, retention, and alerting. On Render the database blocks all external IPs by default.
Use an approved private-network job or temporarily allow only your exact public `/32` IP for an external
export, then remove it. Do not open it to `0.0.0.0/0`.

To rehearse recovery, provision an **empty isolated database**, restore with PostgreSQL 16 tooling, and run
the matching app version with test-only credentials/email destinations:

```sh
# Set PGHOST/PGDATABASE/PGUSER/PGPASSWORD to the NEW recovery database, never the live one.
pg_restore --exit-on-error --no-owner --no-acl --dbname="$PGDATABASE" backups/restaurant-YYYY-MM-DD.dump
```

Restore photo files to the recovery service's `/var/data/menu-images` with UID/GID 10001 ownership.
Check images, restaurant records, table IDs, sessions, settled totals, and authentication. Protect dumps
as sensitive customer/business data. Do not commit them or send real invitation mail during restore tests.
Record measured recovery time and data-loss window; a backup is not verified until a restore succeeds.

## Updates And Rollback

The backend repository controls releases of both apps. Push UI changes to its repository first, select
the published commit with `node scripts/pin-frontend.mjs <FULL_UI_SHA>`, then commit/push that pin in the
backend repository. UI-only pushes are not deployments. Keep the selected UI repository public and its
historical commits available for rebuilds. See [DEPLOYMENT.md](DEPLOYMENT.md) for the full release flow.

1. Make changes on a branch, review the diff and Dependabot advisories, and require green CI before merging.
2. Test the exact Docker build against a staging PostgreSQL database; staging must not share production data or secrets.
3. Take/verify backups before schema changes. Add new versioned Flyway migrations; never edit applied migrations.
4. Merge to `main`. Render is configured to deploy only after checks pass. Confirm the active commit and readiness.
5. Run the smoke script and restaurant acceptance checks after deployment.

For an application-only regression, select a known-good deployment in Render **only if** it is compatible
with the current database schema. Reverting code does not undo migrations. For incompatible data/schema
changes, use a reviewed forward migration or the documented database recovery process in a maintenance window.
Never delete the live database/disk, force Flyway repair, or use `ddl-auto=create/update` as a rollback method.

Container image tags and dependencies need regular review. Spring Boot 3.5 is used to preserve the existing
Java 17 / Spring Security architecture; check the [official support policy](https://spring.io/projects/spring-boot#support)
and security advisories before launch and budget for supported-version upgrades. A successful build is not
a security certification. Keep the CI dependency audit and automated update PRs enabled.

## Secrets And Access

- Keep all secrets in the hosting secret store/password manager, never React `VITE_*` variables or Git.
- Remove one-time platform bootstrap variables after the initial login, retaining JWT and TOTP secrets.
- Keep one platform owner until per-account MFA enrollment/recovery is implemented.
- Rotate SMTP/database credentials using provider procedures and verify service connectivity afterward.
- JWT-key rotation signs out every account; schedule and communicate it. Account/staff revocation is available separately.
- If the authenticator is lost, an authorized operator must verify ownership and rotate the deployment TOTP secret securely; there is no public bypass.
- Never log passwords, full cookies, SMTP keys, reset URLs/tokens, or database URLs containing passwords.
- Avoid permissive CORS, disabled TLS validation, public H2 console, and `APP_SAMPLE_ENABLED=true` in production.

## Monitoring

Use `/actuator/health/liveness` for process state and `/actuator/health/readiness` for readiness plus DB.
Detailed actuator endpoints are not public. Production logs use JSON; forward them to your logging provider
with access controls and retention. Avoid collecting request bodies/query strings containing reset tokens.

Alert on repeated 5xx errors, order latency, reconnect storms, failed email delivery, database pool saturation,
low free storage, process restarts, and unsuccessful backups. A green readiness check does not validate SMTP,
the photo volume, every restaurant workflow, or external DNS. Maintain a daily operator check during the pilot.

## Local Data Is Not Automatically Migrated

The packaged production deployment starts with an empty PostgreSQL database. Existing H2 files and menu
photos in the old working folders are deliberately excluded from Git and deployment. If that data matters,
back it up and perform a reviewed migration preserving UUIDs/foreign keys/password hashes before switching
restaurants. Copying an H2 file into PostgreSQL does not migrate it. Do not print production QR cards from
throwaway test data.
