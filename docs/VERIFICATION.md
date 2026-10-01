# Deployment Verification

## Backup Restart Fix, 2026-10-01

- [Run 36734378850](https://github.com/NihalBlone/restaurant-ordering/actions/runs/36734378850)
  passed the backend, frontend and deployment-config jobs. Its container job built the image and
  passed the packaged UI/API/WebSocket smoke checks. The supplied production-test log also shows
  successful startup, container replacement, persistent-data assertions and backup creation.
- The failure was in the backup cleanup: the runner rejected `docker compose start --wait` with
  `unknown flag: --wait`. Publishing was skipped. Database restoration and Caddy validation were
  not reached in that run.
- The backup helper now uses health-checked `up` with `--no-deps --no-recreate --no-build --pull never`.
  It preserves the existing app container and only reports success after recovery succeeds. A
  previously stopped app is not started, and failed recovery still returns a nonzero exit status.
- Sixteen local checks passed, including eight backup shell regression tests using a strict Docker
  stub, Compose option support and the resolved Compose model. Four regression tests failed against
  the original restart command before the fix. The CLI checks used Docker Compose 5.5.1; shell syntax
  and `git diff --check` also passed.
- Stub tests do not prove live PostgreSQL backup/restore or container readiness. The local Docker
  daemon remains unavailable; push this fix and require a new complete CI run before deploying.
  No production server, DNS, secrets, or repository remote were changed by this fix.

## VPS Preparation, 2026-09-30

- Seven local Python/Compose checks passed, including secret generation, mode-600 creation,
  overwrite/symlink refusal, input validation, the non-CI smoke-test guard, missing-config handling,
  resolved network/port isolation, production profile, app DB role and the 1536 MiB container budget.
- The resolved model was validated with the official Docker Compose 5.5.1 CLI in a temporary directory;
  its release checksum was verified. This does not require or prove a working Docker daemon.
- The GitHub Actions workflow passed actionlint 1.7.12 (shellcheck integration disabled); shell scripts
  were checked separately with `bash -n`. `git diff --check` passed.
- No Java/React business code changed. Their previous test results below are historical, not a new run.
- Full container execution was NOT possible on this Mac because its Docker daemon is unavailable.
  The updated workflow must pass its production-profile startup, non-superuser DB, bootstrap removal,
  persistent photo, backup/DB-restore, Caddy configuration and existing regression checks before publish.
  These new CI checks have been added but have not been observed running yet.
- Nothing was pushed, published, deployed to the Droplet, or changed in GoDaddy by this preparation.
  No live secrets were generated. Real DNS/HTTPS, SMTP delivery, SSH hardening, off-server backups,
  recovery rehearsal with real data and concurrent-user load testing remain launch requirements.

Follow [the VPS guide](DEPLOYMENT-VPS.md) for DigitalOcean; the Render results below concern the earlier
alternative deployment, not the new server.

## Earlier Render Verification

Local verification on 2026-09-22, before committing these deployment changes:

- Backend: 70 tests passed, zero failures/errors/skips, using Java 17 and Spring Boot 3.5.16.
- Real PostgreSQL 16 integration tests ran, including migrations, concurrent idempotent orders,
  shared sessions, status changes, settlement, and sales summaries.
- An authenticated STOMP connection received a restaurant event over a real WebSocket.
- The frontend fetch helper retrieved the exact public UI commit in `deploy/frontend.ref`.
- Pinned UI: 11 tests passed and the Vite production build succeeded. Dependencies installed from the
  local npm cache using `npm ci --offline`; this does not verify an online clean install.
- The Java JAR containing that UI started with `prod,render` and an isolated PostgreSQL database.
  Smoke checks passed for SPA routes/assets, readiness, CSRF, SockJS transport, unauthorized API
  responses, and missing-resource handling. Only the test database URL used plaintext loopback;
  the Render configuration retains TLS.
- Render Blueprint passed the official JSON schema validation; workflow/Compose YAML parsed.
- Shell syntax, release-pin argument validation, exact checkout, and existing-directory protection passed.

## Still Required

- Full Docker image/Compose execution was not run locally because the Docker daemon was unavailable.
  The backend GitHub Actions workflow builds both repositories and runs this check after pushing.
- The online npm advisory audit failed on a local certificate-chain verification error, including a
  retry with the system CA option. It did not produce a clean audit result. TLS verification was not
  disabled. Both repositories' CI run the audit on GitHub-hosted runners.
- No cloud deployment, DNS change, domain purchase, real SMTP delivery, load test, or disaster-recovery
  rehearsal was performed. Follow [DEPLOYMENT.md](DEPLOYMENT.md) and [OPERATIONS.md](OPERATIONS.md).
- Changing the pinned frontend commit or backend code requires rerunning CI for that exact release.

The smoke run used synthetic secrets and loopback-only temporary services. It did not modify the
development H2 database or require stopping the existing development UI/backend.
