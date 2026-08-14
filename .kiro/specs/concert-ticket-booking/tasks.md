# Implementation Plan: Concert Ticket Booking Platform

## Overview

Implement a **modular monolith** Spring Boot 3 backend. Each task completes one full domain module end-to-end (entity + repository + service + controller + dto). PostgreSQL is the authoritative source for inventory; Redis serves as a read cache updated asynchronously after DB commits.

Base package: `com.geekup.ticketbooking`

Module layout:
```
com.geekup.ticketbooking/
├── concert/
│   ├── controller/
│   ├── service/
│   ├── entity/
│   ├── repository/
│   └── dto/
├── booking/
│   ├── controller/
│   ├── service/
│   ├── scheduler/
│   ├── entity/
│   ├── state/
│   ├── repository/
│   └── dto/
├── voucher/
│   ├── service/
│   ├── entity/
│   ├── repository/
│   └── dto/
├── admin/
│   ├── controller/
│   ├── service/
│   └── dto/
└── shared/
    ├── common/        # ApiResponse, UserContext
    ├── exception/     # ApplicationException, GlobalExceptionHandler, generic exceptions
    ├── cache/         # InventoryCache, VoucherLockService
    ├── idempotency/   # IdempotencyService
    ├── filter/        # RateLimitFilter, IdempotencyFilter, UserIdHeaderFilter
    └── infrastructure/
        ├── payment/   # MockPaymentGateway
        └── seeder/    # DataSeeder
```

## Tasks

- [x] 1. Foundation — Shared infrastructure and project setup
  - [x] 1.1 Initialize Spring Boot project and configure `pom.xml`
    - Spring Boot 3.2.x, Java 17, all required dependencies
    - _Requirements: 11.1, 11.2_

  - [x] 1.2 Create `application.yml` and `docker-compose.yml`
    - `application.yml` with datasource, Redis, Redisson, JPA, scheduler, springdoc config
    - `docker-compose.yml` with postgres, redis, app services; `Dockerfile` for app
    - _Requirements: 11.1, 11.2_

  - [x] 1.3 Shared layer — `ApiResponse<T>`, exceptions, `GlobalExceptionHandler`
    - `shared.common.ApiResponse<T>`: `{ success, data, error, timestamp }` envelope
    - Generic exception classes: `ResourceNotFoundException` (404), `ConflictException` (409), `ValidationException` (422), `ForbiddenException` (403), `ServiceBusyException` (503), `PaymentFailedException` (402), `PaymentGatewayTimeoutException` (504)
    - `GlobalExceptionHandler`: maps all exceptions to `ApiResponse` error envelopes
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [x] 2. Database — Flyway migration V1
  - Write `src/main/resources/db/migration/V1__create_tables.sql`
  - Tables: `concerts`, `ticket_categories`, `voucher_campaigns`, `vouchers`, `bookings`, `booking_items`
  - All FK constraints; `UNIQUE(idempotency_key)` on bookings; indexes on queried columns
  - _Requirements: 2.1, 7.1_

- [x] 3. Concert module — Full end-to-end
  - **Entity**: `concert/entity/Concert.java`, `concert/entity/TicketCategory.java`
  - **Repository**: `concert/repository/ConcertRepository.java` (`findAllByPublishedTrue(Pageable)`), `concert/repository/TicketCategoryRepository.java` (`findAllByConcertId(Long)`)
  - **Service**: `concert/service/ConcertService.java`
    - `listPublishedConcerts(Pageable)` → paginated list
    - `getConcertDetail(id)` → full detail; reads remaining qty from `InventoryCache`, falls back to DB
    - `createConcert(request)` → atomically persist Concert + TicketCategories
    - `publishConcert(id)` → set `published=true`, load inventory into `InventoryCache`
  - **Controller**: `concert/controller/ConcertController.java`
    - `GET /api/v1/concerts` (paginated, published only)
    - `GET /api/v1/concerts/{id}` (404 if not found or unpublished)
  - **DTO**: `concert/dto/` — `ConcertSummaryResponse`, `ConcertDetailResponse`, `TicketCategoryResponse`, `CreateConcertRequest`
  - `@Valid` on request DTOs; `ApiResponse<T>` envelope; springdoc annotations
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [x] 4. Voucher module — Full end-to-end
  - **Entity**: `voucher/entity/VoucherCampaign.java`, `voucher/entity/Voucher.java`
  - **Repository**: `voucher/repository/VoucherRepository.java` (`findByCode`, `findByCodeAndUsedFalse`), `voucher/repository/VoucherCampaignRepository.java`
  - **Service**: `voucher/service/VoucherService.java`
    - `validateAndApplyVoucher(userId, voucher, bookingAmount)`: acquires `VoucherLockService` Redisson lock, validates campaign dates/usage limits/min amount, marks used; discount: percentage → `floor(A × (1 − R/100))` min 0.01; fixed → `max(A − F, 0.01)`
    - `restoreVoucherUsage(voucher)`: decrements `current_usage_count`, marks unused
  - **Shared**: `shared/cache/VoucherLockService.java` — Redisson `RLock` key `lock:voucher:{userId}:{voucherId}`; `tryLock(waitSeconds=3, leaseSeconds=10)` → throws `ServiceBusyException` on timeout
  - **DTO**: `voucher/dto/` — used internally by booking module (no public customer endpoint)
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10_

- [x] 5. Booking module — Reservation, payment, and expiry
  - **Entity**: `booking/entity/Booking.java`, `booking/entity/BookingItem.java`
  - **State**: `booking/state/BookingState.java` — enum with `VALID_TRANSITIONS` map and `canTransitionTo()` / `validNextStates()`
  - **Repository**: `booking/repository/BookingRepository.java` (`findByUserIdOrderByCreatedAtDesc`, `findAllByStateAndPaymentDeadlineBefore`, `JpaSpecificationExecutor`), `booking/repository/BookingItemRepository.java`
  - **Service**: `booking/service/BookingService.java`
    - `reserve(userId, request)`:
      1. Validate category exists and concert is published
      2. `@Transactional`: `UPDATE ticket_categories SET available_quantity = available_quantity - :qty WHERE id = :id AND available_quantity >= :qty`; 0 rows → throw `ConflictException("TICKET_SOLD_OUT", ...)`
      3. Insert Booking (PENDING, paymentDeadline=now+15min) + BookingItems
      4. If voucherCode → call `VoucherService.validateAndApplyVoucher()`
      5. Post-commit: async update `InventoryCache`, store idempotency key → response
    - `pay(userId, bookingId, method)`:
      1. Load booking; 404/403 checks; validate state is PENDING
      2. Transition to AWAITING_PAYMENT, call `MockPaymentGateway`
      3. SUCCESS → CONFIRMED, record paymentTimestamp
      4. FAILED → CANCELLED, restore DB qty + `InventoryCache` + voucher; throw `PaymentFailedException`
      5. Timeout → leave PENDING; throw `PaymentGatewayTimeoutException`
  - **Scheduler**: `booking/scheduler/BookingExpiryScheduler.java` — `@Scheduled(fixedDelay=30000)`; PENDING past deadline → EXPIRED, restore DB qty + `InventoryCache` + voucher
  - **Controller**:
    - `booking/controller/BookingController.java`: `POST /api/v1/bookings/reserve`, `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`
    - `booking/controller/PaymentController.java`: `POST /api/v1/bookings/{id}/pay`
  - **DTO**: `booking/dto/` — `ReserveBookingRequest`, `BookingItemRequest`, `BookingResponse`, `BookingDetailResponse`, `PaymentRequest`
  - `@Valid` on request DTOs; `ApiResponse<T>` envelope; springdoc annotations
  - _Requirements: 2.1–2.9, 3.1–3.8, 5.1–5.5, 9.1, 9.2_

- [x] 6. Shared Redis layer — `InventoryCache`, `IdempotencyService`, filters
  - **`shared/cache/InventoryCache.java`**:
    - `RedisTemplate<String, Long>`, key `inventory:{ticketCategoryId}`
    - `initInventory(id, qty)`, `getInventory(id)` → `Optional<Long>`, `updateInventory(id, qty)`, `incrementInventory(id, qty)` (atomic INCRBY)
    - On Redis failure: log ERROR, persist `ReconciliationTask` in PostgreSQL
  - **`shared/idempotency/IdempotencyService.java`**:
    - `RedisTemplate<String, String>`, key `idempotency:{key}`, TTL 24h
    - `getIfPresent(key)` → `Optional<String>`, `store(key, json)`; fail open on Redis unavailability
  - **`shared/filter/IdempotencyFilter.java`**:
    - `OncePerRequestFilter`; intercepts `POST /api/v1/bookings/reserve`
    - Missing header → 400 `MISSING_IDEMPOTENCY_KEY`; cached key → return stored response; else capture 2xx response and store
  - **`shared/filter/RateLimitFilter.java`**:
    - Redis sliding window `rate:{userId}`, TTL 60s; >200 req/min → 429 `Retry-After: 60`; fail open
  - **`shared/filter/UserIdHeaderFilter.java`**:
    - Extracts `X-User-Id` header → `UserContext.set(userId)`, clears after request
  - **`shared/common/UserContext.java`**: `ThreadLocal<Long>` with `set`, `get`, `clear`
  - **`shared/infrastructure/payment/MockPaymentGateway.java`**:
    - Returns configurable SUCCESS/FAILED/TIMEOUT via `@Value("${payment.gateway.behavior}")`
    - Timeout simulated with `Thread.sleep(11000)` for TIMEOUT behavior
  - _Requirements: 1.4, 2.5, 2.6, 9.3–9.9, 11.5_

- [x] 7. Admin module — Full end-to-end
  - **Service**: `admin/service/AdminService.java`
    - Concert admin: `createConcert`, `publishConcert`, `getInventoryStats`, `updateTicketCategoryQuantity`
    - Booking admin: `listBookings(filters, pageable)`, `transitionBookingState(bookingId, targetState)`
    - Voucher admin: `createVoucherCampaign`, `generateVouchers(campaignId, count)`, `getCampaignStats`
  - **Controllers**:
    - `admin/controller/AdminConcertController.java`: `POST /api/v1/admin/concerts`, `POST /api/v1/admin/concerts/{id}/publish`, `GET /api/v1/admin/concerts/{id}/inventory`, `PATCH /api/v1/admin/ticket-categories/{id}/quantity`
    - `admin/controller/AdminBookingController.java`: `GET /api/v1/admin/bookings`, `PATCH /api/v1/admin/bookings/{id}/state`
    - `admin/controller/AdminVoucherController.java`: `POST /api/v1/admin/voucher-campaigns`, `POST /api/v1/admin/voucher-campaigns/{id}/vouchers`, `GET /api/v1/admin/voucher-campaigns/{id}/stats`
  - **DTO**: `admin/dto/` — `CreateConcertRequest`, `InventoryStatsResponse`, `UpdateQuantityRequest`, `AdminBookingFilter`, `TransitionStateRequest`, `CreateVoucherCampaignRequest`, `GenerateVouchersRequest`, `CampaignStatsResponse`
  - `publishConcert`: load inventory into `InventoryCache`; `updateTicketCategoryQuantity`: reject if `newQty < soldCount` (throw `ValidationException("QUANTITY_BELOW_SOLD", ...)`), update Redis
  - `transitionBookingState`: validate via `BookingState.canTransitionTo()`; on CANCELLED restore DB qty + `InventoryCache` + voucher
  - Generate vouchers: unique alphanumeric 8–16 chars, batch in single transaction; reject batch size < 1 or > 10,000
  - `@Valid` on request DTOs; `ApiResponse<T>` envelope; springdoc annotations
  - _Requirements: 6.1–6.6, 7.1–7.7, 8.1–8.6_

- [x] 8. DataSeeder — Demo data on startup
  - `shared/infrastructure/seeder/DataSeeder.java` implements `CommandLineRunner`
  - Idempotent: check-before-insert by unique name
  - Empty DB → insert ≥ 2 published Concerts (VIP > 500,000 VND qty ≥ 50; Standard < 500,000 VND qty ≥ 100); load into `InventoryCache`
  - No campaigns → insert ≥ 1 active VoucherCampaign with ≥ 5 vouchers, `maxUsage ≥ 5`
  - `FLASH_SALE_MODE=true` → insert 1 extra Concert with exactly 100 tickets; load into `InventoryCache`
  - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [ ] 9. Tests — Property-based and integration
  - [x] 9.1 Property test P1 — `BookingStateMachinePropertyTest`
    - For any state S and target T: accepted iff `T ∈ VALID_TRANSITIONS[S]`; else throws `INVALID_STATE_TRANSITION`
    - `@Property(tries = 100)`, no Spring context needed
    - _Validates: Requirements 3.4, 6.3, 6.4_

  - [x]* 9.2 Property test P2 — `IdempotencyPropertyTest`
    - Same key K within 24h → same response, no duplicate Booking
    - _Validates: Requirements 2.5, 9.3_

  - [x]* 9.3 Property test P3 — `VoucherDiscountPropertyTest`
    - Percentage: `max(floor(A × (1 − R/100)), 0.01)`; fixed: `max(A − F, 0.01)`
    - _Validates: Requirement 4.2_

  - [ ]* 9.4 Property test P4 — `InventoryPropertyTest`
    - `available_quantity` never < 0 across any reserve/cancel/expire sequence (Testcontainers PostgreSQL)
    - _Validates: Requirements 9.1, 9.6_

  - [-]* 9.5 Property test P5 — `ConcurrencyPropertyTest` (oversell prevention)
    - Initial inventory N, concurrent reservations → non-CANCELLED/non-EXPIRED BookingItems never exceed N
    - _Validates: Requirements 9.1, 9.2_

  - [-]* 9.6 Property test P6 — `ConcurrencyPropertyTest` (voucher limit)
    - `maxUsage = M`, concurrent applications → successful count never exceeds M
    - _Validates: Requirements 4.4, 4.5_

  - [-]* 9.7 Property test P7 — `ConcurrencyPropertyTest` (restoration)
    - CANCELLED/EXPIRED → DB qty +N, Redis updated, voucher `current_usage_count` -1
    - _Validates: Requirements 2.9, 3.3, 4.6, 6.5, 9.8_

  - [~] 9.8 Integration test — `FlashSaleConcurrencyTest` (Testcontainers)
    - Case A: inventory=10, 100 concurrent users → exactly 10 succeed, 90 get `TICKET_SOLD_OUT`
    - Case B: same `Idempotency-Key` sent 3× → exactly 1 Booking, all 3 responses identical
    - Case C: `maxUsage=10`, 100 concurrent → ≤10 succeed, rest `VOUCHER_EXHAUSTED`
    - _Requirements: 9.1, 9.2, 9.3, 4.4, 4.5_

  - [ ]* 9.9 Unit tests — scheduler, payment timeout, DataSeeder
    - `BookingExpirySchedulerTest`: PENDING → EXPIRED, DB qty restored, `InventoryCache` updated
    - Payment timeout: `MockPaymentGateway` TIMEOUT → booking stays PENDING, 504 response
    - `DataSeederTest`: idempotent on empty DB and with `FLASH_SALE_MODE=true`
    - _Requirements: 2.9, 3.8, 10.1, 10.2, 10.4_

  - [~] 9.10 Integration test — `ReservationIntegrationTest` (Testcontainers)
    - reserve → pay SUCCESS → CONFIRMED; verify items, amounts, paymentTimestamp
    - reserve → pay FAILED → CANCELLED; verify DB qty restored and `InventoryCache` updated
    - reserve with and without valid voucher
    - _Requirements: 2.1, 3.2, 3.3, 4.2_

- [~] 10. Final checkpoint — All tests pass, app starts cleanly
  - Run full test suite (unit + property + integration). Ensure all pass.
  - App starts cleanly with `docker-compose up` and Swagger UI loads at `/swagger-ui.html`.

- [ ]* 11. Documentation — README and Postman collection
  - `README.md`: architecture diagram, trade-offs, `docker-compose up` instructions
  - `postman/concert-ticket-booking.postman_collection.json`: all 18 endpoints grouped by module
  - `postman/local.postman_environment.json`: `baseUrl`, `userId`, `adminUserId`, `idempotencyKey`
  - Pre-request script: auto-generate UUID v4 `Idempotency-Key` on reservation requests
  - _Requirements: 11.1, 11.2_

## Notes

- Tasks marked with `*` are optional
- **Module boundaries**: cross-module calls (e.g., `booking` → `voucher`) go through service interfaces, not repositories
- **No distributed lock for tickets** — concurrency handled by atomic `UPDATE … WHERE available_quantity >= :qty`
- **PostgreSQL is write source of truth**; `InventoryCache` is a read cache updated post-commit
- Property tests: jqwik 1.8.4, `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property N: <text>")`
- Integration tests: Testcontainers (PostgreSQL + Redis) + `CountDownLatch` for concurrency coordination

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2"] },
    { "id": 2, "tasks": ["3", "4"] },
    { "id": 3, "tasks": ["5"] },
    { "id": 4, "tasks": ["6"] },
    { "id": 5, "tasks": ["7", "8"] },
    { "id": 6, "tasks": ["9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7"] },
    { "id": 7, "tasks": ["9.8", "9.9", "9.10"] },
    { "id": 8, "tasks": ["10"] },
    { "id": 9, "tasks": ["11"] }
  ]
}
```
z`