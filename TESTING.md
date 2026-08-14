# Testing Strategy & Execution Guide: Concert Ticket Booking Platform

## 1. Test Layers Overview

The project adopts **four distinct test layers** to ensure correctness, performance, and reliability of the flash‑sale booking system:

| Layer | Purpose | Tools & Frameworks |
|---|---|---|
| **Unit Tests** | Verify business‑logic units in isolation (fast, deterministic). | JUnit 5, Mockito |
| **Integration Tests** | Validate the interaction between services, repositories and real infrastructure (PostgreSQL, Redis). | Spring Boot Test, Testcontainers |
| **Concurrency Tests** | Stress the system under high‑concurrency flash‑sale scenarios to prove overselling protection, idempotency and voucher race‑condition handling. | ExecutorService, CountDownLatch, CompletableFuture |
| **API Tests (Postman)** | Provide a ready‑to‑run collection for reviewers to manually explore the end‑to‑end flow via HTTP requests. | Postman collection + environment files |

---

## 2. Unit Tests – JUnit 5 + Mockito

### Scope
- **Booking state transitions** – verify `BookingState.canTransitionTo()` logic.
- **Voucher discount calculation** – ensure percentage and fixed‑amount discounts are computed correctly.
- **Validation** – request DTO constraints (quantity limits, required fields, Idempotency‑Key presence).
- **Idempotency logic** – `IdempotencyService` stores/retrieves cached responses.
- **Inventory service** – `InventoryCache` read‑through and update behaviours.
- **Payment service** – mock `MockPaymentGateway` to simulate SUCCESS, FAILED and TIMEOUT outcomes.

### Example Test Classes (placed under `src/test/java/.../unit/`)
- `BookingServiceTest`
- `VoucherServiceTest`
- `BookingStateTest`
- `PaymentServiceTest`

Run only unit tests:
```bash
./mvnw test -Dtest=*Test -DexcludeGroups=integration,concurrency,api
```

---

## 3. Integration Tests – Spring Boot Test + Testcontainers

### Scope
- Deploy **real PostgreSQL** and **real Redis** containers for the duration of the test suite.
- Execute full flow **API → Service → Repository → DB → Redis**.
- Verify persistence, cache synchronization and exception mapping.

### Example Test Classes (under `src/test/java/.../integration/`)
- `ReservationIntegrationTest`
- `VoucherIntegrationTest`
- `PaymentIntegrationTest`

Typical test setup snippet:
```java
@Testcontainers
@SpringBootTest
class ReservationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);
    // ... inject beans and perform end‑to‑end reservation flow
}
```

Run only integration tests:
```bash
./mvnw test -Dtest=*IntegrationTest
```

---

## 4. Concurrency Tests – Critical Flash‑Sale Validation

### Goal
Demonstrate that the system **never oversells**, never creates duplicate bookings, and respects voucher usage limits under massive parallel load.

### Common Test Pattern
```java
ExecutorService executor = Executors.newFixedThreadPool(100);
CountDownLatch start = new CountDownLatch(1);
CountDownLatch done  = new CountDownLatch(100);
AtomicInteger success = new AtomicInteger();

for (int i = 0; i < 100; i++) {
    executor.submit(() -> {
        try {
            start.await();
            // call the service method (reserve / apply voucher / pay)
            bookingService.reserve(...);
            success.incrementAndGet();
        } catch (Exception e) {
            // expected failures (e.g., TICKET_SOLD_OUT)
        } finally {
            done.countDown();
        }
    });
}

start.countDown();   // unleash all 100 threads at once
done.await();        // wait for completion
```

### Specific Scenarios
- **OversellingConcurrencyTest** – Ticket quantity = 10, 100 concurrent reservation requests → expect 10 successes, 90 `TICKET_SOLD_OUT`, final inventory = 0.
- **DuplicateBookingConcurrencyTest** – Same `Idempotency-Key` sent from multiple threads → only one booking created, all responses identical.
- **VoucherConcurrencyTest** – Voucher campaign with `maxUsage = 10`, 100 concurrent applications → at most 10 succeed, rest receive `VOUCHER_EXHAUSTED`.

Run concurrency tests:
```bash
./mvnw test -Dtest=*ConcurrencyTest
```

---

## 5. API Tests – Postman Collection

Provide a **Postman** folder (`postman/`) containing:
- `concert-ticket-booking.postman_collection.json` – all 18 endpoints grouped by feature (Concert browsing, Booking reservation, Payment, Admin operations).
- `local.postman_environment.json` – variables: `baseUrl`, `userId`, `adminUserId`, `idempotencyKey`.
- Pre‑request scripts to auto‑generate a UUID for `Idempotency-Key` on reservation requests.
- Test scripts asserting HTTP status codes and the `success` field in the response envelope.

Reviewers can import the collection into Postman and run the **Full End‑to‑End Flow**:
1. Create a concert (admin).
2. Publish the concert.
3. Browse concerts.
4. Reserve tickets.
5. Apply a voucher (optional).
6. Pay for the booking.
7. Retrieve booking status.

---

## 6. Running the Complete Test Suite

```bash
# Clean and run **all** tests (unit, integration, concurrency)
./mvnw clean test
```

You can also target a specific layer using the `-Dtest` selector shown above.

---

## 7. Verification Checklist
- [x] All unit test classes compile and pass.
- [x] Integration tests spin up PostgreSQL & Redis containers successfully.
- [x] Concurrency test scenarios A, B, C execute without race‑condition failures.
- [x] Postman collection is present and functional.

Feel free to adjust the test data or concurrency counts to explore edge cases further.
