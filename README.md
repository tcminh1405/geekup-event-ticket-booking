# Concert Ticket Booking Platform

Backend submission for the GEEK Up Product Backend Engineer Test. The project models a concert-ticket platform with a customer booking flow and an internal operator flow, with particular attention to a flash-sale workload.

## What is included

- Browse published concerts and ticket categories.
- Reserve tickets, optionally apply a voucher, pay, and track booking status.
- Operator APIs to create/publish concerts, inspect and adjust inventory, manage bookings, and create voucher campaigns/codes.
- PostgreSQL migrations, Redis, deterministic seed data, Swagger/OpenAPI, and Docker Compose.
- Protection for the main flash-sale risks:
  - Atomic PostgreSQL inventory decrement prevents overselling.
  - Redis in-flight claim plus a user-scoped database key prevents duplicate reservation processing; the database is also used as the durable retry fallback.
  - Voucher-level distributed lock and atomic campaign quota update prevent concurrent reuse/over-consumption.
  - Atomic booking state transitions protect payment retries and booking expiry.

## Architecture

The service is a modular monolith. This is intentional for the scope of the assignment: booking, concert, voucher, and operator modules can evolve independently while a single deployment and relational transaction keep the critical reservation flow straightforward.

| Component | Responsibility |
| --- | --- |
| Spring Boot / Java 21 | REST API, domain services, scheduled expiry job |
| PostgreSQL 15 | System of record: concerts, inventory, bookings, vouchers |
| Redis 7 / Redisson | Availability cache, rate limiting, idempotency response/in-flight claim, voucher locks |
| Flyway | Versioned database schema migrations |

The database is authoritative for availability. Ticket reservation uses a conditional update (`available_quantity >= requested_quantity`), so concurrent requests cannot make inventory negative. Redis is an optimisation and is updated only after the write transaction commits.

Further rationale and data modelling are in [docs/SYSTEM-DESIGN.md](docs/SYSTEM-DESIGN.md), [docs/DATABASE-DESIGN.md](docs/DATABASE-DESIGN.md), [docs/TRADE-OFFS.md](docs/TRADE-OFFS.md), and [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md).

Developer conventions and the new-API workflow are in [docs/CODING-GUIDELINES.md](docs/CODING-GUIDELINES.md). Local setup and test instructions are in [docs/LOCAL-SETUP.md](docs/LOCAL-SETUP.md).

## Prerequisites

- Docker Desktop (for PostgreSQL and Redis, or the full application stack)
- Java 21, if running the backend directly

No globally installed Maven is required; the repository includes Maven Wrapper.

## Run locally

### Option 1: run all services with Docker

```powershell
docker compose up --build
```

The API becomes available at `http://localhost:8080`. Stop it with `Ctrl+C`; use `docker compose down` to stop containers while retaining volumes.

### Option 2: run infrastructure in Docker and backend locally

```powershell
docker compose up -d postgres redis
cd backend
.\mvnw.cmd spring-boot:run
```

To start with a dedicated 100-ticket flash-sale concert:

```powershell
$env:FLASH_SALE_MODE = 'true'
.\mvnw.cmd spring-boot:run
```

On a clean database, the application seeds two published concerts, ticket categories, an active voucher campaign, and voucher codes. First list the concerts to obtain real IDs rather than assuming seeded IDs.

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

All responses use the envelope `{ success, data, error, timestamp }`.

### Identity used for this assignment

Authentication is intentionally mocked so the assessment can focus on booking correctness:

- Customer endpoints requiring an identity: `X-User-Id: <number>`
- Operator endpoints under `/api/v1/admin/**`: `X-Role: ADMIN` or `X-Role: OPERATOR`

This is a development-only role gate, not a substitute for production authentication/authorization.

## Main endpoints

| Area | Endpoint |
| --- | --- |
| Customer | `GET /api/v1/concerts`, `GET /api/v1/concerts/{id}` |
| Customer | `POST /api/v1/bookings/reserve`, `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}` |
| Customer | `POST /api/v1/bookings/{id}/pay` |
| Operator | `GET /api/v1/admin/bookings`, `PATCH /api/v1/admin/bookings/{id}/state` |
| Operator | `PATCH /api/v1/admin/bookings/{id}/suspicion`, `GET /api/v1/admin/bookings?suspicious=true` |
| Operator | `POST /api/v1/admin/concerts`, `POST /api/v1/admin/concerts/{id}/publish` |
| Operator | `GET /api/v1/admin/concerts/{id}/inventory`, `PATCH /api/v1/admin/ticket-categories/{id}/quantity` |
| Operator | `POST /api/v1/admin/voucher-campaigns`, `POST /api/v1/admin/voucher-campaigns/{id}/vouchers`, `GET /api/v1/admin/voucher-campaigns/{id}/stats` |

## Manual end-to-end test flow

The following commands use PowerShell and `curl.exe` to avoid the PowerShell `curl` alias. Replace `concertId`, `ticketCategoryId`, and `bookingId` with values from previous responses.

1. Discover seeded concerts and a ticket category.

```powershell
curl.exe http://localhost:8080/api/v1/concerts
curl.exe http://localhost:8080/api/v1/concerts/1
```

2. Reserve tickets. `Idempotency-Key` is required; retrying the same request with the same key returns the saved response. If a simultaneous request is still executing, it returns `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`; retry shortly.

```powershell
curl.exe -X POST http://localhost:8080/api/v1/bookings/reserve `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1001" `
  -H "Idempotency-Key: 8b9b4f14-087a-4f45-85cd-3e25f853a001" `
  -d '{"concertId":1,"items":[{"ticketCategoryId":1,"quantity":2}]}'
```

3. Pay for the resulting booking. The default mock gateway succeeds.

```powershell
curl.exe -X POST http://localhost:8080/api/v1/bookings/1/pay `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1001" `
  -d '{"paymentMethod":"MOCK"}'
```

4. Confirm status and inventory as an operator.

```powershell
curl.exe -H "X-User-Id: 9001" -H "X-Role: OPERATOR" http://localhost:8080/api/v1/admin/bookings
curl.exe -H "X-User-Id: 9001" -H "X-Role: OPERATOR" http://localhost:8080/api/v1/admin/concerts/1/inventory
```

5. Exercise failed payment and timeout behaviour by restarting the backend with one of the following values. A failed payment cancels the booking and releases inventory/voucher usage; a timeout returns the booking to `PENDING` for retry.

```powershell
$env:PAYMENT_BEHAVIOR = 'FAILED'   # or TIMEOUT, RANDOM, SUCCESS
.\mvnw.cmd spring-boot:run
```

## Automated tests

Run the suite from `backend`:

```powershell
cd backend
.\mvnw.cmd test
```

The current tests include property-based checks for:

- Valid booking-state transitions.
- Idempotency cache behaviour and TTL.
- Voucher discount calculation.
- PostgreSQL conditional inventory decrement/restore invariant via Testcontainers.

The inventory property test starts a PostgreSQL Testcontainer and can take longer on its first run while Docker pulls/starts the image. Import [postman/concert-ticket-booking.postman_collection.json](postman/concert-ticket-booking.postman_collection.json) and [postman/local.postman_environment.json](postman/local.postman_environment.json) for the local API collection. See [TESTING.md](TESTING.md) for the test strategy and intended additional integration/concurrency coverage.

## Booking state model

```text
PENDING → AWAITING_PAYMENT → CONFIRMED
    │             │              │
    ├────────→ EXPIRED           └→ CANCELLED
    └──────────────────────→ CANCELLED
```

- A reservation is held in `PENDING` for 15 minutes.
- The expiry scheduler scans every 30 seconds and atomically expires still-pending or interrupted in-payment bookings.
- `CANCELLED` and `EXPIRED` release reserved inventory and voucher usage once.

Operators may also flag a booking with a short review reason through `PATCH /api/v1/admin/bookings/{id}/suspicion`; this is a manual-review workflow, not an automated fraud engine.

## Scope and limitations

This is deliberately not a production-complete payment or identity system. It does not include JWT/OAuth, a real payment provider, webhooks, notifications, QR tickets, or multi-currency support. The exact assumptions, operational parameters, and trade-offs are documented in [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md) and [docs/TRADE-OFFS.md](docs/TRADE-OFFS.md).
