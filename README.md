# Restaurant Ordering System

Spring Boot 3 / Java 17 backend for a multi-tenant QR-based restaurant ordering system with real-time order tracking over WebSocket.

## Features

- Multi-tenant data model with `restaurantId` scoped entities and repository queries
- Customer menu access via `tableId` QR lookup
- Order placement with optional `X-Idempotency-Key`
- Table-level order history with pagination
- Real-time notifications:
  - `/topic/restaurant/{restaurantId}` for newly placed orders
  - `/topic/table/{tableId}` for customer order updates
- H2 in-memory database with sample restaurant, tables, categories, and menu items

## Run

```bash
mvn spring-boot:run
```

## API Summary

- `GET /api/menu?tableId={tableId}`
- `POST /api/orders`
- `GET /api/orders?tableId={tableId}&page=0&size=20`
- `PUT /api/orders/{orderId}/status`
  - Requires `X-Restaurant-Id` header to scope restaurant updates

## WebSocket

- STOMP endpoint: `/ws-orders`
- Restaurant topic: `/topic/restaurant/{restaurantId}`
- Table topic: `/topic/table/{tableId}`

## Sample Data

On startup the app creates:

- 1 restaurant
- 5 active tables
- 3 menu categories
- 7 menu items
