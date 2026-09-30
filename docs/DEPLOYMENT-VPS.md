# Deploy On A 2 GB Ubuntu VPS

This guide is for the existing backend repository, a single amd64 Ubuntu 24.04 server, and
`orders.servemytable.com`. Keep the domain at GoDaddy. Do not create another VM per restaurant.

```text
HTTPS / WebSocket :443 -> Caddy -> Java 17 + compiled React :8080 -> PostgreSQL 16 :5432
                       public         private Docker             isolated Docker
```

Only Caddy publishes ports 80/443. The app has outbound access for email, but the database is on an
internal network with no public port. `sslmode=disable` is ONLY for this same-host Docker database link;
use verified TLS if moving the database off the host. PostgreSQL, photos, and certificates use separate
persistent volumes. The application DB role is not a superuser. This is a single point of failure,
not a highly available architecture.

## 1. Host Prerequisites

- Ubuntu 24.04 amd64, Docker Engine and Compose plugin installed and tested with `hello-world`.
- A non-root `deploy` user whose SSH login and `sudo` have been tested in a second terminal.
- A cloud firewall attached to the Droplet: TCP 22 from your current public IP only, 80/443 from all
  clients, default outbound traffic allowed. Never publish 5432/8080/5173. Update the SSH source if your
  home/VPN IP changes. Keep recovery access and a working session during access-rule changes.
- Resolve any stopped `adduser` job before proceeding; an existing account must not be created again.
- Replace any password exposed in screenshots or shell history. Verify key-only login before disabling
  password/root SSH access. Do not disable your only working authentication method.
- Enable provider MFA, monitoring/alerts and security updates. Review provider backup costs separately.

Memory limits: app 1024 MiB (512 MiB Java heap, 40 request threads, 5 DB connections), PostgreSQL 384 MiB,
Caddy 128 MiB. This leaves roughly 400-500 MiB for the host, depending on its actual usable RAM. There is
no on-server build. These are pilot limits, not a guarantee for a number of restaurants. Load-test with
realistic simultaneous orders, reports, photo uploads and WebSocket connections; monitor OOM restarts.
Swap is not a replacement for adequate RAM.

## 2. Push And Publish The Tested Image (Mac)

The existing `Verify Deployment` workflow now validates the VPS config, runs existing backend/UI tests,
builds the combined Docker image, runs both local-demo and production-profile smoke checks, and only
then publishes that SAME image to GHCR. PR jobs cannot publish. Production secrets and SSH keys are not
needed in GitHub Actions. A push publishes a candidate; it does not connect to or modify the live server.

Review and commit the deployment files from the backend directory. Do not stage `.DS_Store`, local
databases, populated env files or uploads. Push to `main`, then open the backend repository's Actions tab.
All jobs, including `publish`, must pass. Do not bypass an audit or test failure to deploy.

The `publish` job's summary contains:

```text
APP_IMAGE=ghcr.io/nihalblone/restaurant-ordering@sha256:<64-character-digest>
CONFIG_REF=<40-character-backend-commit>
```

Copy the actual values, not the placeholders. Use the digest, not `latest` or a moving branch tag.
On first publication, GHCR packages are normally private even for a public repository. In your GitHub
profile's Packages section, open `restaurant-ordering`, then Package settings and change visibility to
Public if you want unauthenticated pulls. This exposes the compiled application image; it must contain
no secrets (the build has no production configuration). If you need private images instead, configure
a read-only `read:packages` token on the server using `docker login --password-stdin`; do not put a PAT
in Git, a Dockerfile, a build argument, or a command-line argument. Keep all image pulls under the same
`sudo docker` identity. Review GitHub Actions/package usage limits and retention separately.

The workflow uses the automatically supplied `GITHUB_TOKEN` with `packages: write` in its publish job.
A permission error requires checking GitHub Actions/package access, not embedding a personal token in YAML.
The tested image artifact expires after one day; rerun the workflow if retrying only publication after expiry.

## 3. Configure Transactional Email

The application currently sends restaurant invitations and password resets using authenticated SMTP.
DigitalOcean blocks outbound ports 25, 465 and 587. This setup uses Resend's documented alternate
**2587 with required STARTTLS and certificate verification**, with IPv4 connections.

1. In Resend, verify a dedicated sending subdomain such as `mail.servemytable.com`.
2. Add the exact DKIM/SPF/verification records it supplies to GoDaddy. Preserve existing mailbox records;
   never create conflicting SPF records or replace unrelated MX records. Configure DMARC appropriately.
3. Wait for verification and create a sending API key. The sender can then be `orders@mail.servemytable.com`.
4. The platform owner's email must be a real inbox you can receive email in; Resend is not an inbox service.

Optional server-side connectivity/TLS check (this does not test authentication or delivery):

```sh
openssl s_client -starttls smtp -connect smtp.resend.com:2587 \
  -servername smtp.resend.com -verify_hostname smtp.resend.com -verify_return_error -brief </dev/null
```

If the connection is blocked or fails TLS verification, stop and resolve it with the provider. Never
disable TLS verification. Actual invitation and password-reset delivery are required before launch;
the health endpoint intentionally does not test SMTP.

## 4. Fetch The Deployment Configuration (Droplet)

Use the `deploy` SSH session. The folder outside the checkout holds persistent private configuration:

```sh
sudo install -d -m 700 -o deploy -g deploy /opt/servemytable
cd /opt/servemytable
git clone https://github.com/NihalBlone/restaurant-ordering.git source
cd source
git checkout --detach YOUR_ACTUAL_CONFIG_REF
```

Replace `YOUR_ACTUAL_CONFIG_REF` with the full commit from the successful CI summary. If `source` already
exists, fetch it and inspect its status instead of cloning or deleting it again. Do not clone the UI here:
the release image already includes the pinned React build.

Create the initial private environment file:

```sh
python3 scripts/configure-vps.py
```

The helper prompts for the hostname, release digest, real owner inbox, verified sender, and hidden
Resend API key. It generates independent DB passwords, JWT signing key, owner password and TOTP secret.
It writes `/opt/servemytable/.env` with mode 600, prints no secrets, and refuses to overwrite an existing
file or symlink. Never regenerate this file for an update: DB, JWT and TOTP secrets must remain stable.

Open that file privately with `nano /opt/servemytable/.env` to store the generated owner password and
TOTP recovery secret in your password manager. Do not paste, screenshot, log, or commit its contents.
Set up your authenticator with `APP_PLATFORM_TOTP_SECRET`: time-based, SHA-1, six digits, 30-second period.
The username is `owner`. This is NOT your Linux `deploy` password or the local restaurant demo account.

## 5. Start The Private Application First

From `/opt/servemytable/source`:

```sh
sudo bash scripts/vps.sh config --quiet
sudo bash scripts/vps.sh pull
sudo bash scripts/vps.sh up -d --wait --wait-timeout 300 database app
sudo bash scripts/vps.sh ps
sudo bash scripts/vps.sh exec -T app curl --fail --silent \
  http://127.0.0.1:8080/actuator/health/readiness
```

The last response must contain `"status":"UP"`. No app/DB ports are open on the host at this point.
If startup fails, inspect `sudo bash scripts/vps.sh logs --tail=100 app database`. Redact sensitive values
before sharing logs. Do NOT run `config` without `--quiet`: it expands and prints secrets.

The init script creates the application DB/user only on an EMPTY PostgreSQL volume. Changing env passwords
does not change an existing DB user's password. Flyway then migrates and Hibernate validates the schema.
If initialization fails, investigate the existing volume; never delete it as a generic troubleshooting step.
Never run `compose.smoke.yml`, `scripts/smoke-vps.sh` or `down --volumes` on this live deployment.

## 6. GoDaddy DNS And HTTPS

After private readiness passes, in GoDaddy's DNS manager for `servemytable.com`, add/update:

| Type | Name | Value | TTL |
| --- | --- | --- | --- |
| A | orders | Your Droplet's current public IPv4 | Default |

Use only the IP, not `https://` or a path. Remove conflicting records for the SAME `orders` name only.
Do not leave an `orders` AAAA record unless it actually reaches this server. Preserve apex, www, MX,
DKIM and other mail records. These instructions assume GoDaddy hosts the authoritative nameservers;
otherwise edit DNS at the actual DNS provider. There is no need to transfer or buy the domain again.

From your Mac, check the result (allow for DNS caches to expire):

```sh
dig +short orders.servemytable.com A
dig +short orders.servemytable.com AAAA
```

Once A points to the Droplet and inbound ports 80/443 are permitted, run on the server:

```sh
cd /opt/servemytable/source
sudo bash scripts/vps.sh up -d proxy
sudo bash scripts/vps.sh logs --tail=80 proxy
bash scripts/smoke-test.sh https://orders.servemytable.com
```

Caddy automatically obtains and renews HTTPS certificates and proxies WebSockets. Its `/data` volume
must remain persistent. It is the only internet-facing container, so the app can trust its forwarded
HTTPS headers. No purchased certificate or extra load balancer is required. A healthy proxy process
alone does not prove issuance; the HTTPS smoke check and real browser login must pass.

## 7. First Login And Launch Checks

Open `https://orders.servemytable.com/platform/login`. Sign in with `owner`, the generated password,
and the authenticator code. After successful login, remove these three lines from the private `.env`:

```text
APP_PLATFORM_USERNAME
APP_PLATFORM_EMAIL
APP_PLATFORM_PASSWORD
```

Keep JWT/TOTP/DB/SMTP secrets. Then recreate just the app:

```sh
sudo bash scripts/vps.sh up -d --no-deps --force-recreate --wait --wait-timeout 300 app
```

Verify owner login still works. Create a restaurant and send its owner invitation to a real inbox.
Test the invite/reset links, restaurant login at `/admin/login`, menu/photo editing, tables and QR codes.
Use a separate browser profile for platform and restaurant accounts. There are no production demo logins.

Create NEW production tables and QR cards after choosing the final hostname. The local H2 database and
its table UUIDs have not been migrated. Test at least three phones on one table: shared session history,
running total, live updates, independent orders and retries, staff accept/serve, and external payment
settlement followed by a fresh session. This app does not process payments.

Before handling real orders: verify tenant isolation, email delivery, monitoring/alerts, realistic load,
photo persistence, encrypted off-server DB/photo backups and an isolated restore test. Neither CI nor
these files replace that operational acceptance. Close any unresolved suspended account-management job
and verify key-based administrator access before final SSH hardening.

## 8. Backups, Updates, And Recovery

`backup-vps.sh` briefly stops the application to prevent changes between the DB dump and photo archive,
then restarts it even on failure. Run outside service hours; this single-server design has downtime.

```sh
cd /opt/servemytable/source
sudo bash scripts/backup-vps.sh "/opt/servemytable/backups/$(date -u +%Y%m%dT%H%M%SZ)"
sudo bash scripts/vps.sh ps
```

The helper refuses an existing destination, produces a PostgreSQL custom-format dump and compressed
photo archive, and validates their structure. Structural validation is NOT a successful restore test.
Encrypt/copy backups to independent off-server storage and choose retention/RPO based on how many orders
you can afford to lose. A local backup dies with the Droplet. Keep a separate secure backup of the `.env`,
release digest and configuration commit. Automated schedules/offsite storage are not enabled by this code.

Rehearse recovery on an ISOLATED test host with Docker, the same configuration commit and PostgreSQL 16:
configure its own private `.env`, start ONLY `database` on a fresh volume, and restore before starting Java.
Restore with `pg_restore -U restaurant_app -d restaurant_ordering --no-owner --no-acl --exit-on-error`
via `scripts/vps.sh exec -T database`, redirecting the dump on stdin. Restore `photos.tar.gz` to `/var/data`
using a one-off app-image container with `--no-deps --entrypoint tar`; preserve numeric UID 10001 ownership.
Use the backed-up TOTP secret for restored platform accounts. Do not expose the recovery system or send
real invitation email from it. Check login, historical orders, photos, and totals before declaring recovery
tested. Never restore over live data or point a recovery test at production volumes.

For an update: push/verify/publish, take an off-server backup, record the old digest/config commit, fetch
the new CONFIG_REF in `source`, and change ONLY `APP_IMAGE` in `.env` to the new digest. Review any config
diff and migrations, run `config --quiet`, pull the app image and recreate it:

```sh
sudo bash scripts/vps.sh pull app
sudo bash scripts/vps.sh up -d --no-deps --wait --wait-timeout 300 app
bash scripts/smoke-test.sh https://orders.servemytable.com
sudo bash scripts/vps.sh ps
```

A container healthcheck detects failure but does not itself restart an unhealthy process; monitor readiness
externally and inspect failures. Container restarts cover process exits, not every application fault.
Use `sudo docker stats --no-stream` plus provider CPU/RAM/disk monitoring. Watch DB/photo growth and log
rotation. Keep one Java replica: the current in-process WebSocket broker is not multi-instance-ready.
Do not scale replicas without a shared broker and another architecture review.

Rollback to an earlier app digest is safe ONLY if database migrations remain backward-compatible. Otherwise
use a tested recovery procedure and acknowledge the data-loss window. Do not run destructive volume/prune
commands. Update PostgreSQL 16/Caddy images separately during a backed-up maintenance window, checking
release notes; never change PostgreSQL's major version against its existing data volume.

## Sources

- [DigitalOcean cloud firewalls](https://docs.digitalocean.com/products/networking/firewalls/how-to/create/)
- [DigitalOcean SMTP restrictions](https://docs.digitalocean.com/products/droplets/details/limits/)
- [Resend SMTP ports and TLS](https://resend.com/docs/send-with-smtp)
- [GitHub container publishing](https://docs.github.com/en/actions/use-cases-and-examples/publishing-packages/publishing-docker-images)
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
- [GoDaddy A records](https://www.godaddy.com/en-in/help/add-or-edit-an-a-record-42546)
