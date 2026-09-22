# Deployment Verification

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
