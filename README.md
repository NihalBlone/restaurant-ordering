# QR Restaurant Ordering Backend

Spring Boot 3 / Java 17 backend for multi-tenant, QR-based table ordering. Customers order without an account; restaurant operations are protected by a tenant-scoped admin login.

## Go Live With Your Two Repositories

This is the backend and deployment repository: [NihalBlone/restaurant-ordering](https://github.com/NihalBlone/restaurant-ordering).
The UI stays in [NihalBlone/restaurant_ordering_UI](https://github.com/NihalBlone/restaurant_ordering_UI).
No folder move or third repository is required.

The root Dockerfile builds the exact public UI commit in `deploy/frontend.ref`, then packages React inside
Spring Boot. One Render web service serves UI, API, photos, and WebSocket on the same HTTPS domain, with
managed PostgreSQL and a persistent photo disk. A UI push alone does not update production: update the
backend's pin to the published UI commit and push the backend to release it.

Follow [the deployment guide](docs/DEPLOYMENT.md) for exact push commands, hosting, domain purchase,
email, secrets, and first-restaurant setup. Read [operations and recovery](docs/OPERATIONS.md) before launch.
See [verification results and remaining checks](docs/VERIFICATION.md) for what has actually been tested.
The old combined `restaurant-ordering` checkout is not used by this deployment.

## Implemented

- QR menu lookup and server-owned table sessions
- Shared table order history and running bill totals
- Atomic order placement with idempotency and per-device rate limiting
- Strict PLACED -> PREPARING -> SERVED state transitions
- STOMP updates for customer tables and authenticated restaurant topics
- Restaurant username/password login using an HttpOnly JWT cookie
- One-time, expiring password-reset tokens
- Dynamic menu categories, item editing, availability, and photo uploads
- Tenant-scoped table creation and configurable customer QR links
- External-payment settlement that closes the table session
- Settled sales statement with date and table filters
- Food-item history filters with matching-item totals and chart aggregates by item, table, and local settlement day
- Tenant validation across admin APIs and WebSocket subscriptions

This application does not process payments. Staff use **Record paid & close session** only after payment is received externally.

## Run Locally

    mvn -s .mvn/settings-public.xml spring-boot:run

The API starts at http://localhost:8080. The default `local` profile uses file-backed H2 at `./data/restaurantdb`, so table IDs and orders survive restarts. Tests use separate in-memory databases.

Sample admin:

    username: admin
    password: Admin@12345

Startup logs list the generated table IDs. The authenticated GET /api/admin/tables endpoint returns tables and their customer QR links.

## Important Endpoints

Public customer API:

- GET /api/menu?tableId={tableId}
- POST /api/orders
- GET /api/orders?tableId={tableId}
- STOMP /topic/table/{tableId}

Authenticated restaurant API:

- POST /api/auth/login
- GET /api/auth/me
- POST /api/auth/logout
- POST /api/auth/password-reset/request
- POST /api/auth/password-reset/confirm
- GET /api/admin/tables
- POST /api/admin/tables
- PUT /api/orders/{orderId}/status
- POST /api/table-sessions/{tableId}/close
- GET /api/admin/reports/sales
- /api/restaurants/{restaurantId}/menu-categories
- /api/restaurants/{restaurantId}/menu-items
- POST /api/restaurants/{restaurantId}/menu-images
- STOMP /topic/restaurant/{restaurantId}

Postman requests are in postman/restaurant-ordering.postman_collection.json. Postman retains the login cookie automatically.

## Configuration

Set these environment variables outside local development:

    SPRING_PROFILES_ACTIVE=prod
    APP_AUTH_JWT_SECRET=<at-least-48-character-random-secret>
    APP_AUTH_SECURE_COOKIE=true
    APP_AUTH_EXPOSE_RESET_TOKEN=false
    APP_WEBSOCKET_ALLOWED_ORIGINS=https://your-frontend.example.com
    APP_CUSTOMER_BASE_URL=https://your-frontend.example.com
    APP_MENU_IMAGE_DIRECTORY=/durable/menu-image/path
    APP_PLATFORM_TOTP_SECRET=<random-Base32-authenticator-secret>

APP_AUTH_EXPOSE_RESET_TOKEN=true is intended only for the local reset-password demo. The `prod` profile disables it and sends reset/invitation links through the configured SMTP server.

The local profile uses file-backed H2 and filesystem image storage. PostgreSQL profiles, Flyway migrations, and SMTP delivery are included. Before deployment, provision PostgreSQL, durable image storage (or an object-storage adapter), HTTPS, managed secrets, backups, and SMTP. See [Platform and deployment setup](docs/platform-setup.md).

## Tables And QR Codes

In the dashboard, open **Tables & QR**, enter a name such as `T6` or `PATIO 1`, then download the PNG table card. Existing tables also get QR previews and downloads. QR generation happens locally in the browser, not through a third-party QR service.

`POST /api/admin/tables` accepts `{"tableNumber":"PATIO 1"}` and returns HTTP 201 with the table ID, customer URL, active flag, and current session ID. The restaurant is taken from the authenticated session, never from the request. Names are trimmed, normalized to uppercase, and limited to 30 characters. A database unique constraint protects against concurrent duplicates within the same restaurant (HTTP 409).

`APP_CUSTOMER_BASE_URL` defaults to `http://localhost:5173`. For phone testing on the same Wi-Fi, set it to this computer's LAN address (for example `http://192.168.1.20:5173`), set `APP_WEBSOCKET_ALLOWED_ORIGINS` to the same origin, and start Vite with `npm run dev -- --host 0.0.0.0`. Restart the backend, refresh the table list, and download new QR cards. Use a stable HTTPS customer domain in production.

The URL contains only the permanent table ID, not a session ID. Settlement therefore does not require reprinting a QR code. Changing the customer domain or deleting/recreating table data does require new cards. The local H2 file persists across restarts; production uses PostgreSQL. Back up the database before printing cards for real use.

## Platform Owner

The three entry points are `/platform/login`, `/admin/login`, and `/menu?tableId=<UUID>` in the React application. Platform accounts cannot call restaurant mutation endpoints. Restaurant accounts cannot access platform APIs.

Platform features include restaurant onboarding with owner invitations, search and pagination, profile/plan/capacity settings, suspension/reactivation/soft archive, staff roles and session revocation, read-only scoped sales reports, audit history, announcements, and database health. Plan labels and trial dates are metadata only: no subscription billing or automatic expiry is implemented.

Configure the first platform account through `APP_PLATFORM_USERNAME`, `APP_PLATFORM_EMAIL`, and `APP_PLATFORM_PASSWORD` before starting Spring Boot. There is no built-in platform password. See [setup, permissions, and deployment limitations](docs/platform-setup.md).

Cookie-authenticated mutations now require CSRF. Fetch `GET /api/auth/csrf`, preserve its cookie, and send the returned token under the returned `headerName` on each POST/PUT/DELETE. The React Axios client handles this automatically. Existing Postman requests must add this step.

## Sales Filters And Charts

`GET /api/admin/reports/sales` accepts required `from` and `to` timestamps and optional `tableId`, `menuItemId`, `timeZone` (default `UTC`), `page`, and `size`. Table and food-item filters combine with the date range and are restricted to the signed-in restaurant. Unavailable menu items can still be used for historical reports.

Only settled sessions whose settlement timestamp is in the inclusive date range are included. With a food filter, amounts and quantities include **only that item's order lines**, using historical prices. Bill and order counts count distinct matching sessions/orders, not line items. `statements[].billTotalAmount` retains the full settled bill for reference while statement details contain matching items. Without a food filter, the existing complete-bill totals remain unchanged.

`items`, `tables`, and `days` contain chart aggregates across **all** matching bills, independent of statement pagination. `days` uses the requested IANA timezone, such as `Asia/Kolkata`. The UI fills gaps with zero-sales days and can switch between sales amount and quantity. Menu chart labels use current names; detailed orders retain their original item names.

## Verify

    mvn test -s .mvn/settings-public.xml
