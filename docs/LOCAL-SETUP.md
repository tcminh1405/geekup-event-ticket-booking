# Local Setup and Test Execution

## Prerequisites

- Docker Desktop running
- Java 21 for local backend execution

Maven Wrapper is included; no global Maven installation is needed.

## Start the system

Run the complete stack:

```powershell
docker compose up --build
```

Or run only PostgreSQL and Redis in Docker, then start Spring Boot locally:

```powershell
docker compose up -d postgres redis
cd backend
.\mvnw.cmd spring-boot:run
```

The local URLs are:

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/api-docs`
- PostgreSQL: `localhost:5432` (`ticketbooking` / `postgres` / `postgres`)
- Redis: `localhost:6379`

On an empty database, `DataSeeder` inserts two published concerts, ticket categories, an active voucher campaign, and voucher codes. Set `$env:FLASH_SALE_MODE = 'true'` before starting Spring Boot to create the dedicated 100-ticket concert.

## Run tests

From `backend`:

```powershell
.\mvnw.cmd test
```

The inventory property test starts PostgreSQL through Testcontainers. Its first run can be slower because Docker may need to pull `postgres:16-alpine`.

## Run API tests in Postman

1. Import `postman/concert-ticket-booking.postman_collection.json`.
2. Import and select `postman/local.postman_environment.json`.
3. Start the API, then run the requests in the collection in their displayed order.
4. The `Get concert detail` request saves `concertId` and `ticketCategoryId`; `Reserve tickets` saves `bookingId`. Requests whose IDs do not yet exist use those variables.

Customer requests use `X-User-Id`. Every admin request includes `X-Role: OPERATOR` from the environment.
