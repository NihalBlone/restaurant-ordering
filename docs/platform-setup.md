# Platform Setup And Deployment

For the two-repository Render deployment, domain purchase, and release commands, use
[DEPLOYMENT.md](DEPLOYMENT.md). The instructions below also cover local development and generic hosts.

## Entry Points

| Audience | Frontend route | Access |
| --- | --- | --- |
| Product/platform owner | `/platform/login` then `/platform` | Environment-bootstrapped account |
| Restaurant owner and staff | `/admin/login` then `/dashboard` | Invited restaurant account |
| Customers | `/menu?tableId=<UUID>` | No login; table QR is a bearer link |

The same HttpOnly cookie is used for the signed-in administrative account. Sign out to switch between platform and restaurant accounts in the same browser. Different browsers/profiles can be used simultaneously. Customer ordering does not depend on that administrative cookie.

## Local Setup

The default `local` profile creates the existing sample restaurant login (`admin` / `Admin@12345`) and uses **file-backed H2**, not an ephemeral database. Data lives under `data/`; do not delete this directory. Sample rows are created only when necessary.

For the simplest first-time platform setup, run:

```sh
cd /Users/nihaltamang/Documents/personal/qr-restaurant-ordering-system
bash scripts/start-local-platform.sh
```

The script prompts for a username (default `owner`), email, and a unique password with confirmation. Password input is hidden and not written to a file or shell history. `Admin@12345` is only the restaurant demo password and is too short for a new platform owner. Stop any other backend process first, preserving old in-memory data if needed. Existing platform accounts keep their original password; the script does not reset them.

For manual configuration instead:

Set the platform owner's real email and a unique 14-72 character password in your shell or secret manager:

```sh
cd /Users/nihaltamang/Documents/personal/qr-restaurant-ordering-system
export APP_PLATFORM_USERNAME=owner
export APP_PLATFORM_EMAIL=you@example.com
# Set APP_PLATFORM_PASSWORD securely; do not commit it or reuse the sample password.
mvn -s .mvn/settings-public.xml spring-boot:run
```

The bootstrap creates an account only if its username is absent. It never changes an existing password. After the first creation, remove `APP_PLATFORM_PASSWORD` from the runtime environment. Use password reset for later changes. `APP_PLATFORM_USERNAME` must identify that same platform owner on subsequent bootstrap runs.

Spring Boot does not automatically load `.env`; export values explicitly. `.env.example` documents local variables and Docker Compose reads `.env` for its database password.

Start the UI from `../qr-restaurant-ordering-UI/restaurant_ordering_UI` with `npm run dev`, then open `http://localhost:5173/platform/login`.

**Upgrade note:** do not stop an older in-memory backend if its current data matters until you have exported it. This change does not recover data from a previous ephemeral H2 process, migrate it to PostgreSQL, or preserve IDs that were already lost. New databases receive both Flyway migrations. For an existing nonempty database without Flyway history, first take a backup and compare its schema with V1; baseline at version 1 only after verification, then apply V2. Automatic baselining is intentionally disabled.

## PostgreSQL

Use a managed PostgreSQL instance for a hosted deployment, with private networking, encryption, monitored backups, point-in-time recovery, and restore drills. MySQL is not configured by this change; PostgreSQL is the selected production database.

For a local PostgreSQL instance, install/enable Docker Compose, start Docker/Colima, set `DATABASE_PASSWORD`, then:

```sh
docker compose up -d --wait database
export DATABASE_USERNAME=restaurant_app
export DATABASE_URL=jdbc:postgresql://localhost:5432/restaurant_ordering
mvn -s .mvn/settings-public.xml spring-boot:run -Dspring-boot.run.profiles=postgres
```

The `postgres` profile does not seed demo restaurant data. It is a local development profile, not a production security preset. Use the platform console to onboard a restaurant. The Docker volume persists database files across restarts; `docker compose down -v` deletes that volume, so do not use it on data you need.

For a generic production host, use the `prod` profile and configure the variables below.
The included Render Blueprint instead uses `prod,render` and private database fields; see [DEPLOYMENT.md](DEPLOYMENT.md).

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>?sslmode=verify-full
DATABASE_USERNAME=<application-user>
DATABASE_PASSWORD=<secret>
APP_CUSTOMER_BASE_URL=https://<public-domain>
APP_WEBSOCKET_ALLOWED_ORIGINS=https://<public-domain>
APP_AUTH_JWT_SECRET=<random-secret-of-at-least-48-characters>
APP_PLATFORM_USERNAME=<owner-username>
APP_PLATFORM_EMAIL=<owner-email>
APP_PLATFORM_PASSWORD=<first-bootstrap-only>
APP_PLATFORM_TOTP_SECRET=<random-Base32-secret-of-at-least-160-bits>
SMTP_HOST=<smtp-host>
SMTP_PORT=587
SMTP_USERNAME=<smtp-user>
SMTP_PASSWORD=<smtp-secret>
APP_MAIL_FROM=<verified-sender-email>
APP_MENU_IMAGE_DIRECTORY=<durable-volume-path>
```

Install the managed database's CA as required for `verify-full`. Restrict database permissions, and preferably execute migrations with a separate deployment principal. Hibernate validates schemas instead of changing them automatically. Deploy the frontend and reverse-proxy `/api`, `/ws-orders` (including websocket upgrades), and `/uploads` under the same HTTPS origin. Direct cross-origin API hosting is not configured. Set SPA history fallback for frontend routes; never fallback API errors to `index.html`.

The production startup guard rejects insecure cookies, development JWT secrets, missing platform MFA, exposed reset tokens, sample data, and a non-HTTPS customer URL. SMTP and database connectivity still need operational verification; do not infer working email delivery from configuration alone.

## Platform MFA

The current deployment supports one bootstrapped platform owner. Provision its random Base32 secret out of band into a standard authenticator (TOTP, SHA-1, 6 digits, 30 seconds). Store it in a secret manager, not in the database, repository, logs, or frontend. Production requires it; local development may omit it.

The verifier allows one time step of clock drift and persists the last accepted step under a row lock to reject replay. Keep hosts time-synchronized. There is no self-service MFA enrollment/recovery or backup-code flow yet. Secret rotation and recovery require an operator procedure. Do not provision multiple platform owners against this shared deployment secret; move to per-account secrets or an identity provider first.

## Permissions

| Action | Platform owner | Restaurant owner | Manager | Kitchen | Waiter |
| --- | --- | --- | --- | --- | --- |
| Onboard/suspend/archive restaurants | Yes | No | No | No | No |
| Manage restaurant staff | Selected restaurant | Own restaurant | No | No | No |
| View sales reports | Read-only, selected restaurant | Own | Own | No | No |
| Edit menu and create tables | No | Own | Own | No | No |
| View/update order queue | No | Own | Own | Own | Own |
| Record paid and close session | No | Own | Own | No | Own |

All checks are enforced server-side, not just by hidden navigation. Invitations cannot grant platform privileges. Each restaurant must keep at least one active owner. Table/staff limits are checked while holding the restaurant row lock; disabled accounts still count toward the staff limit. Plan names and trial dates are informational; no automatic billing, subscription lifecycle, or scheduled suspension is implemented.

JWT verification checks the persisted account role, active state, token version, and restaurant status. Disabling an account, changing its role, revoking sessions, resetting its password, signing out, or changing restaurant status invalidates existing tokens. **Sign out revokes all sessions for that account**, not only the current browser. Restaurant websocket subscriptions also check current access before outbound delivery. Suspension blocks public menu/order requests; reactivation requires staff to sign in again.

## Invitations, Reporting And Audit

Onboarding creates the restaurant and initial owner atomically. A random unusable initial password is replaced via the single-use invitation/reset link. Production links are emailed; development can return a link in the UI. SMTP failure rolls back invitation creation. Email dispatch is synchronous and not a durable outbox yet; configure monitoring/retry support before high-volume onboarding.

Platform support access is explicit and read-only for selected restaurant sales reports; it is not silent impersonation. Report views and platform/account mutations are recorded with actor, tenant, action, subject, and time. The audit screen is paginated and filterable by restaurant. Existing restaurant operational logs are not a complete immutable compliance ledger. Database-level administrator access can still alter audit records; use a separately protected audit sink if required.

Sales reports retain date/table/food filters, bar charts, bill history, and CSV aggregate export across all matching bills. Exports are summaries, not a full raw-data backup. Formula-like spreadsheet cells are escaped. Platform sales totals represent settled **restaurant sales**, not SaaS subscription revenue. The current application uses INR; do not mix restaurants with different currencies until per-tenant currency and currency-separated aggregates are implemented.

## Go-Live Work Still Required

- Provision cloud database, DNS/TLS, SMTP, monitoring/alerts, and tested automated backups. No cloud resources are created by this implementation.
- Run the application against your actual PostgreSQL version and rehearse migration/restore paths before release. CI includes real PostgreSQL migration, concurrent-order, session, and report tests; local runs need `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, and `TEST_DATABASE_PASSWORD` to enable that suite.
- Use a shared STOMP broker and distributed rate limiter before adding backend replicas. Current messaging and authentication throttling are in-process.
- Store images on a durable volume for one instance or add object storage/CDN for multiple instances. Image uploads are not cloud-backed yet.
- Review QR/session access: table IDs are bearer links, not proof of physical presence. Time-limited signed QR/session capabilities and abuse prevention are needed for stronger protection against copied links.
- Add durable email/event outboxes, complete security/operational auditing, tested retention/anonymization/deletion workflows, and operational MFA recovery as appropriate. Soft archive is not data erasure.
- Review dependency updates and run security/load testing. No payment gateway or automatic SaaS billing is included.

## Verification

```sh
mvn -s .mvn/settings-public.xml test
cd ../qr-restaurant-ordering-UI/restaurant_ordering_UI
npm test
npm run build
```

Backend tests cover migration validation, portal/tenant isolation, CSRF, role restrictions, suspension, revocation, one-time resets, invitations, optimistic settings updates, table-limit concurrency, and RFC TOTP vectors/replay. Frontend tests cover QR generation, sales aggregation, and CSV safety; interactive platform flows additionally require browser smoke testing.
