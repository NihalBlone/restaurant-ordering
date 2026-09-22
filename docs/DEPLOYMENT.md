# Deploy Your Separate Backend And UI Repositories

Keep your existing folders and repositories:

| Purpose | Local directory | GitHub repository |
| --- | --- | --- |
| Backend and deployment | `/Users/nihaltamang/Documents/personal/qr-restaurant-ordering-system` | [NihalBlone/restaurant-ordering](https://github.com/NihalBlone/restaurant-ordering) |
| UI source | `/Users/nihaltamang/Documents/personal/qr-restaurant-ordering-UI/restaurant_ordering_UI` | [NihalBlone/restaurant_ordering_UI](https://github.com/NihalBlone/restaurant_ordering_UI) |

Do not use the old combined `restaurant-ordering` directory or the `redefinednt-ship-it` repository for this setup.
No folder move, collaborator invitation, third repository, or separate frontend hosting is required.

Recommended initial architecture:

```text
Phones / restaurant staff / platform owner
                    |
             HTTPS + secure WebSocket
                    |
        orders.yourdomain.com (Render)
        Java 17 + compiled React, one instance
             |                    |
      PostgreSQL 16          Persistent photo disk
             |
      managed DB backups

The Java service also sends invitations/password resets through SMTP.
```

The browser, `/api`, `/uploads`, and `/ws-orders` share one origin. Do not deploy the frontend on a separate
hostname without redesigning cookie, CSRF, CORS, and WebSocket-origin handling.

## 1. Accounts And Purchases

| Item | Where | What to do |
| --- | --- | --- |
| Git repository | [GitHub repository](https://github.com/NihalBlone/restaurant-ordering) | Ensure you and the Render integration have access; enable MFA and branch protection. |
| Domain | [Cloudflare Registrar](https://domains.cloudflare.com/) | Search for your preferred name, review registration AND renewal costs, purchase in your own account, enable auto-renew and MFA. |
| Hosting + database + photo disk | [Render](https://dashboard.render.com/) | Connect GitHub and approve the Blueprint cost estimate. These are recurring paid resources. |
| Transactional email | [Resend](https://resend.com/) | Verify a sending domain and create a restricted sending API key for SMTP. Review its sending limits and costs. |
| Secret storage | Your password manager | Store the owner password, JWT key, TOTP recovery secret, and provider recovery codes. |

The domain is not included in the hosting purchase. You can initially test the app on Render's generated
HTTPS hostname before connecting your domain. A sending domain must be verified for real invitation email.
There is no requirement to buy an SSL certificate or a separate frontend hosting plan for this setup.
Cloudflare-registered domains use Cloudflare nameservers; see [registration instructions](https://developers.cloudflare.com/registrar/get-started/register-domain/).

The Blueprint selects a 1-CPU/2-GB web service, a 0.5-CPU/1-GB PostgreSQL instance, 5 GB of database storage,
and a 5-GB photo disk in Singapore. This is an initial sizing choice, not a capacity guarantee. Review
[current Render pricing](https://render.com/pricing), email charges, bandwidth, and taxes before purchase.
Do not use sleeping/free services for restaurant service.

## 2. Push Both Repositories In Order

First review the local changes. The commands below stage the deployment work explicitly, avoiding your
already-tracked `.DS_Store` change. Never stage populated `.env` files, database files, uploads, or credentials.

Run these commands in the **same terminal** so `UI_SHA` carries across the directory change:

```sh
cd /Users/nihaltamang/Documents/personal/qr-restaurant-ordering-UI/restaurant_ordering_UI
git status
git add .gitignore .github README.md public/robots.txt
git diff --cached --stat
git commit -m "Add frontend verification and deployment guidance"
git push origin main
UI_SHA=$(git rev-parse HEAD)

cd /Users/nihaltamang/Documents/personal/qr-restaurant-ordering-system
node scripts/pin-frontend.mjs "$UI_SHA"
git add .gitignore .dockerignore .gitattributes .env.production.example .github
git add Dockerfile render.yaml compose.smoke.yml deploy docs scripts pom.xml src README.md
git diff --cached --stat
git commit -m "Prepare two-repository production deployment"
git push origin main
```

Stop if a command fails; fix it before proceeding. In particular, push the UI successfully before pinning
its commit. If you have already committed the listed files, skip that commit rather than inventing changes.
Check `git remote -v` in each folder if Git reports an unexpected repository. Do not force-push.

Wait for **Verify UI** in the UI repository and **Verify Deployment** in the backend repository to pass.
Backend CI runs H2 regression tests, real PostgreSQL migration/order/concurrency/session/report tests,
an authenticated WebSocket test, pinned frontend tests, a production-dependency audit, and a full-container
smoke test. A skipped local PostgreSQL test is not a passing PostgreSQL test.
Require the CI jobs in branch protection. Enable Dependabot and GitHub secret scanning where available.

### How The Two-Repo Release Works

The backend's `deploy/frontend.ref` contains one full 40-character UI Git commit SHA. Its Docker build
fetches exactly that commit from the **public** UI repository, runs `npm ci`, tests/builds the UI, and
packages `dist/` inside the Java JAR. It never builds a moving UI branch or copies your uncommitted files.

For each later UI release: commit and push UI changes, capture `git rev-parse HEAD`, run
`node scripts/pin-frontend.mjs <FULL_UI_SHA>` in the backend, and commit/push `deploy/frontend.ref`.
A UI push alone does not update the live app. Backend-only updates retain the selected UI version.
Keep older UI commits available so releases remain rebuildable. Coordinate API-contract changes and test
the selected backend/UI pair together.

The build intentionally needs no GitHub credentials. If the UI repository becomes private, stop and add
a secret-safe build/artifact strategy; do not put a PAT in source, a Docker build argument, or a Git URL.

### Optional Local Container Check

After Docker and Docker Compose are available, from the backend folder:

```sh
docker compose -f compose.smoke.yml up --build --wait --wait-timeout 180
bash scripts/smoke-test.sh http://localhost:18083
docker compose -f compose.smoke.yml down
```

This isolated smoke project uses demo credentials, loopback port 18083, and separate volumes. It does not
replace the existing `compose.yml` development database. It is **not** the production security configuration.
Do not expose it publicly. Its volumes persist until explicitly removed; do not confuse them with live data.
CI additionally removes its own disposable smoke volumes after each run.

## 3. Configure Email

In Resend, add your domain or a dedicated sending subdomain. Add the exact verification/DKIM/SPF DNS
records supplied by Resend in Cloudflare, then wait for the domain to show verified. Do not overwrite
existing mailbox MX/SPF records blindly. Configure DMARC with your mail administrator.

The Blueprint uses these [SMTP settings](https://resend.com/docs/send-with-smtp):

```text
SMTP_HOST=smtp.resend.com
SMTP_PORT=587
SMTP_USERNAME=resend
SMTP_PASSWORD=<your sending API key>
APP_MAIL_FROM=noreply@<your verified domain>
```

The app requires STARTTLS and certificate verification. Do not disable TLS checks to resolve connection
errors. Resend is a sending service, not a personal email inbox; the platform owner's email must be an
inbox you can receive mail in. A different SMTP provider works if it supports authenticated STARTTLS.

## 4. Generate And Store Secrets

Run locally, not in GitHub Actions or a shared/logged terminal:

```sh
python3 scripts/generate-secrets.py
```

The helper prints fresh values; it does not write a secret file. Store them in your password manager.
Never commit the output. `.env.production.example` is a reference with placeholders, not deployable secrets.

| Variable | Required value |
| --- | --- |
| `APP_AUTH_JWT_SECRET` | Generated 64-character random hex value. Keep it stable across normal deploys. |
| `APP_PLATFORM_USERNAME` | A unique owner login, e.g. `owner`; 3-80 letters/numbers/dots/underscores/hyphens. |
| `APP_PLATFORM_EMAIL` | Your real owner email address. |
| `APP_PLATFORM_PASSWORD` | Unique 14-72-character password, at most 72 UTF-8 bytes; the helper generates one. |
| `APP_PLATFORM_TOTP_SECRET` | Generated 32-character Base32 key; keep it stable and confidential. |
| `SMTP_PASSWORD` | SMTP key from your email provider. |
| `APP_MAIL_FROM` | Verified sender address. |

Add the TOTP secret manually to your authenticator as a **time-based** account using SHA-1, 6 digits,
and a 30-second period. Label it with your app and owner username. Keep device time automatic.
The app currently supports one deployment-managed platform-owner TOTP secret, not independent enrollment
for multiple platform owners. Store an offline recovery copy securely; password reset does not remove MFA.

## 5. Deploy The Blueprint

1. In Render, choose **New > Blueprint**, select **NihalBlone/restaurant-ordering** (the backend), and use branch `main`. Do not select the UI repository.
2. Keep the repository root as the Docker build context. The root `render.yaml` is the blueprint. No `backend/` or `frontend/` root-directory setting is needed.
3. Review the paid web service, PostgreSQL, disk, region, and cost estimate before approving.
4. Fill in every prompted secret from the table above. Database credentials are connected automatically.
5. Create the resources and wait for the Docker build, Flyway migrations, and readiness check.
6. Open the generated HTTPS service URL. Run `bash scripts/smoke-test.sh https://YOUR_RENDER_HOST` locally.
7. Open `/platform/login` and use your owner username, password, and current authenticator code.

The active profiles must be **`prod,render`** in that order. The Render profile builds a JDBC URL from
private database fields and defaults the public URL to `https://${RENDER_EXTERNAL_HOSTNAME}`. Do not copy
a raw `postgres://` connection string into `DATABASE_URL`; Java expects `jdbc:postgresql://...`.
Flyway manages schema changes and Hibernate validates them. Start with a new empty database; do not enable
automatic Flyway baselining against an unknown schema.

Keep `APP_AUTH_JWT_SECRET` and `APP_PLATFORM_TOTP_SECRET` configured permanently. After verifying the first
owner login, remove **`APP_PLATFORM_PASSWORD`**, **`APP_PLATFORM_EMAIL`**, and **`APP_PLATFORM_USERNAME`**
from the service environment. Existing accounts persist in PostgreSQL; bootstrap variables are not a
password-reset mechanism. Removing these secrets does not remove the account. Leave their Blueprint
entries as `sync: false`, which does not recreate values during subsequent syncs.

The photo directory is `/var/data/menu-images`. Only `/var/data` is durable. The entrypoint initializes
volume ownership, then runs Java as UID 10001. Do not change its location without updating the volume mount.

The readiness check includes the database but not SMTP; **a green deploy does not prove email delivery**.
Test invitations and resets explicitly. Forwarded HTTPS headers are enabled for Render's trusted edge;
do not expose the container directly to arbitrary clients without a trusted reverse proxy.

## 6. Connect Your Domain

Use a stable app subdomain such as `orders.yourdomain.com`, leaving the root domain available for a future website.

1. Add the exact hostname under the Render web service's **Settings > Custom Domains**.
2. In Cloudflare DNS, create the record Render specifies. For an `orders` subdomain this is normally a
   CNAME whose target is your actual Render service hostname, without `https://` or a path.
3. Keep the record **DNS only** initially. Do not proxy or cache authenticated APIs/WebSockets.
4. Return to Render, verify the domain, and wait for the HTTPS certificate to become active.
5. Set `APP_CUSTOMER_BASE_URL=https://orders.yourdomain.com` in the service environment.
6. Set `APP_WEBSOCKET_ALLOWED_ORIGINS=https://orders.yourdomain.com` as the exact matching origin.
7. Redeploy and run the smoke test using the custom domain. Sign in again on that domain.

Follow the exact current [Render domain/DNS instructions](https://render.com/docs/custom-domains) rather
than guessing IP addresses. Cookies do not migrate from the temporary hostname. Existing QR cards printed
with localhost or the temporary hostname should be regenerated after the final domain is configured.
No rebuild-time `VITE_API_BASE_URL` or backend URL is needed: production uses same-origin relative URLs.

## 7. Onboard The First Restaurant

1. At `/platform/login`, sign in as the platform owner.
2. Open **Restaurants**, create the restaurant and its owner invitation, and use a real recipient address.
3. Have the restaurant owner follow the email link to choose a password, then use `/admin/login`.
4. Add menu tabs/categories, items, correct prices, dietary labels, photos, and availability.
5. Add tables in **Tables & QR**. Check the public hostname before downloading/printing the cards.
6. Invite waiter/kitchen/manager accounts with the minimum necessary role; do not share owner credentials.
7. Scan a table QR from several phones on mobile data, place orders, and check shared history/live updates.
8. Accept and serve all orders. After collecting payment externally, use **Record paid & close session**.
9. Confirm all phones see the session close and the next order starts a new session without old orders.
10. Check the settled sales statement, date/table/item filters, charts, and historical price totals.

Use a separate browser profile for platform and restaurant accounts: one browser cookie holds one signed-in account.
There are **no production demo logins**. The local `admin / Admin@12345` account is not seeded in production.

## 8. Launch Gate

- [ ] CI is green, including PostgreSQL, audit, and the container test.
- [ ] HTTPS, mobile QR links, direct-page refreshes, and WebSocket reconnection work.
- [ ] Platform MFA, invitations, password resets, CSRF, staff roles, and cross-tenant denial are verified.
- [ ] No local/sample credentials, reset tokens, database dumps, or `.env` files are tracked.
- [ ] A photo remains accessible after a restart/redeploy.
- [ ] Database recovery and photo restore have been tested in a separate environment.
- [ ] Monitor readiness, error rate, latency, memory, database connections, and disk usage; configure alerts.
- [ ] Run a realistic lunch-rush load test and independent security review before real restaurant reliance.
- [ ] Agree on support coverage, outage/manual-order procedure, privacy/retention policy, and backup ownership.
- [ ] Review the limitations and maintenance procedures in [OPERATIONS.md](OPERATIONS.md).

Keep the app restricted to a pilot until this gate is complete. Creating files or passing unit tests alone
does not guarantee production safety.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Missing platform email/password startup error | Supply all initial owner variables; use 14-72-byte password, not the restaurant sample password. |
| No menu | Use a real table URL from **Tables & QR** and ensure the restaurant/table are active. |
| Login loops or CSRF errors | Use one HTTPS origin, keep secure cookies enabled, and clear old cookies for that host. |
| TOTP rejected | Check device time, Base32 key, and a fresh code; successfully used time steps cannot be replayed. |
| Invitation/reset fails | Check verified sender, SMTP key, TLS port, provider quota, and delivery logs; readiness excludes SMTP. |
| WebSocket does not connect | Match the HTTPS origin exactly; permit `/ws-orders/**` through the proxy, including upgrade requests. |
| Database connection/migration fails | Check profiles, internal host, JDBC format, credentials, and the actual Flyway error; never switch to H2 to hide it. |
| Photos disappear | Verify `/var/data` is the attached persistent disk and image directory is inside it. |
| npm TLS certificate error locally | Install/configure your organization's trusted CA or use a trusted network; do not set `strict-ssl=false`. |

The database connection uses TLS `require` on Render's private network. Render's internal certificate
does not support hostname/CA verification modes; see [Render PostgreSQL connection guidance](https://render.com/docs/postgresql-creating-connecting).
For other hosts, use their documented TLS verification/CA settings, typically `verify-full`.
