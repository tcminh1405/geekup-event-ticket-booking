# Test Strategy and Current Coverage

## Run tests

```powershell
cd backend
.\mvnw.cmd test
```

The suite uses JUnit 5 and jqwik. `InventoryPropertyTest` starts a PostgreSQL Testcontainer, so the first run may take longer while Docker pulls the image.

## Tests currently implemented

| Test | What it proves |
| --- | --- |
| `BookingStateMachinePropertyTest` | Only defined booking-state transitions are allowed. |
| `IdempotencyPropertyTest` | Redis idempotency storage uses the expected key namespace and 24-hour TTL. |
| `VoucherDiscountPropertyTest` | Fixed/percentage discount calculations stay within valid monetary bounds. |
| `InventoryPropertyTest` | The PostgreSQL conditional inventory decrement never makes availability negative, including reserve/restore cycles. |
| `BookingExpiryServiceTest` | An overdue `AWAITING_PAYMENT` booking is atomically expired and its reservation is released. |
| `BookingIdempotencyFallbackTest` | A durable `(user_id, idempotency_key)` match is returned before any new inventory operation. |

## Manual API verification

Import the two files under `postman/` and select the local environment. Run the collection in order; it discovers seeded IDs, reserves and pays for a booking, and exercises operator inventory, booking, and voucher workflows.

Swagger UI is at `http://localhost:8080/swagger-ui.html`.

## Explicit limitations

This submission does **not** claim a full HTTP-level load test at 300–500 requests/minute, a Spring Boot end-to-end integration suite, or a benchmark report. The implementation protects critical persistence operations with conditional SQL updates and Redis coordination; these mechanisms should be the first targets for production load testing.
