# Implementation Plan: Concert Ticket Booking Platform

## Overview

Implement a **modular monolith** Spring Boot 3 backend for a concert ticket booking platform. Code is organized by domain module (`concert`, `booking`, `voucher`, `admin`) with cross-cutting concerns in `shared`. PostgreSQL is the authoritative source for inventory; Redis serves as a read cache updated asynchronously after DB commits.

Base package: `com.geekup.ticketbooking`

Module layout:
```
com.geekup.ticketbooking/
├── concert/       # entities, service, controller, repositories
├── booking/       # entities, service, controllers, scheduler, state enum
├── voucher/       # entities, service, repositories
├── admin/         # operator controllers + AdminService
└── shared/
    ├── common/    # ApiResponse
    ├── exception/ # ApplicationException, GlobalExceptionHandler, all specific exceptions
    ├── cache/     # InventoryCache, IdempotencyService
    ├── filter/    # RateLimitFilter, IdempotencyFilter, UserIdHeaderFilter
    └── infra/     # MockPaymentGateway, DataSeeder
```

Implementation order: Foundation → Core Domain → Booking Correctness → Customer APIs → Operation APIs → Redis Layer → Tests → Documentation & DevEx.

## Tasks

- [x] 1. Phase 1 — Foundation: Project setup and infrastructure
  - [-] 1.1 Initialize Spring Boot project and configure `pom.xml`
    - Initialize Spring Boot 3 project with Maven in `backend/`
    - Add all required dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `redisson-spring-boot-starter`, `postgresql`, `flyway-core`, `springdoc-openapi-starter-webmvc-ui 2.x`, `jqwik 1.8.4` (test scope), `testcontainers` (test scope)
    - _Requirements: 11.1, 11.2_

  - [-] 1.2 Create `application.yml` and `docker-compose.yml`
    - Create `application.yml` with datasource, Redis, Redisson, JPA, scheduler, and springdoc configuration
    - Create `docker-compose.yml` at project root with services: `postgres` (PostgreSQL 15), `redis` (Redis 7), and `app` (Spring Boot jar); configure environment variables; expose ports 8080, 5432, 6379
    - Set up Flyway migration directory `src/main/resources/db/migration`
    - _Requirements: 11.1, 11.2_

  - [-] 1.3 Implement `shared/common/ApiResponse<T>` and `shared/exception/GlobalExceptionHandler`
    - `ApiResponse<T>` is at `shared.common.ApiResponse` — already created; no changes needed
    - All exception classes are at `shared.exception.*` — already created; no changes needed
    - `GlobalExceptionHandler` is at `shared.exception.GlobalExceptionHandler` — already created; no changes needed
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [ ] 2. Phase 2 — Core Domain: JPA entities and repositories
  - [ ] 2.1 Write Flyway migration V1 — create all tables
    - File: `src/main/resources/db/migration/V1__create_tables.sql`
    - Create tables: `concerts`, `ticket_categories` (with `available_quantity` column), `bookings`, `booking_items`, `voucher_campaigns`, `vouchers` per the ERD in the design
    - Add all foreign key constraints
    - Add `UNIQUE(idempotency_key)` constraint on `bookings`
    - _Requirements: 2.1, 7.1_

  - [ ] 2.2 Implement JPA entity classes
    - `concert.Concert` — entity for `concerts` table
    - `concert.TicketCategory` — entity for `ticket_categories` table
    - `booking.Booking` — entity for `bookings` table; includes `BookingState` enum field
    - `booking.BookingItem` — entity for `booking_items` table
    - `voucher.VoucherCampaign` — entity for `voucher_campaigns` table
    - `voucher.Voucher` — entity for `vouchers` table
    - `booking.BookingState` — enum with `VALID_TRANSITIONS` map and `canTransitionTo()` method
    - Use Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@Builder`) and `@CreationTimestamp`/`@UpdateTimestamp` for audit fields
    - _Requirements: 2.1, 3.4, 5.4_

  - [ ] 2.3 Implement Spring Data JPA repositories
    - `concert.ConcertRepository` — `findAllByPublishedTrue(Pageable)`
    - `concert.TicketCategoryRepository` — `findAllByConcertId(Long)`
    - `booking.BookingRepository` — `findByUserIdOrderByCreatedAtDesc(Long, Pageable)`, `findAllByStateAndPaymentDeadlineBefore(BookingState, LocalDateTime)`, admin filter query with `state`/`concertId`/`createdFrom`/`createdTo`
    - `booking.BookingItemRepository`
    - `voucher.VoucherRepository` — `findByCode(String)`, `findByCodeAndUsedFalse(String)`
    - `voucher.VoucherCampaignRepository`
    - _Requirements: 1.1, 1.6, 5.3, 6.1, 6.2_

- [ ] 3. Phase 3 — Booking Correctness: Reservation flow, idempotency, and state machine
  - [ ] 3.1 Implement `booking.BookingService` — atomic reservation flow
    - Implement `reserve(userId, request)` with the DB-authoritative flow:
      1. Validate ticket category exists and concert is published
      2. `BEGIN TRANSACTION`
      3. `UPDATE ticket_categories SET available_quantity = available_quantity - :qty WHERE id = :id AND available_quantity >= :qty`; if 0 rows affected → `ROLLBACK` and throw `TicketSoldOutException`
      4. `INSERT INTO bookings` (state=PENDING, payment_deadline=now+15min, idempotency_key)
      5. `INSERT INTO booking_items`
      6. If `voucherCode` present → delegate to `voucher.VoucherService` (which acquires Redisson `lock:voucher:{userId}:{voucherId}`)
      7. `COMMIT`
      8. Post-commit: async update `inventory:{ticketCategoryId}` via `shared.cache.InventoryCache`
      9. Post-commit: store idempotency key → serialized response via `shared.cache.IdempotencyService` (TTL 24h)
    - `paymentDeadline = createdAt + 15 minutes`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.7, 2.8, 9.1, 9.2_

  - [ ] 3.2 Implement `shared/cache/IdempotencyService` and `shared/filter/IdempotencyFilter`
    - `shared.cache.IdempotencyService`: uses `RedisTemplate<String, String>`, key pattern `idempotency:{key}`, 24h TTL; `getIfPresent(key)` → `Optional<String>`, `store(key, responseJson)`; fail open on Redis unavailability
    - `shared.filter.IdempotencyFilter`: extends `OncePerRequestFilter`; intercepts `POST /api/v1/bookings/reserve`; missing header → 400 `MISSING_IDEMPOTENCY_KEY`; key found → return cached JSON immediately; otherwise cache on 2xx using `ContentCachingResponseWrapper`
    - _Requirements: 2.5, 2.6, 9.3_

  - [ ] 3.3 Implement `booking.BookingService` — payment flow and `shared/infra/MockPaymentGateway`
    - Implement `pay(userId, bookingId, paymentMethod)`:
      1. Load booking; 404 if not found; 403 if `userId` mismatch
      2. Validate state is PENDING; 409 `INVALID_BOOKING_STATE` otherwise
      3. Transition to AWAITING_PAYMENT, call `shared.infra.MockPaymentGateway`
      4. SUCCESS → CONFIRMED, record `paymentTimestamp` and `totalAmount`
      5. FAILED → CANCELLED, restore DB `available_quantity`, update `shared.cache.InventoryCache`, restore voucher usage via `voucher.VoucherService`, return 402 `PAYMENT_FAILED`
      6. Timeout (>10s) → leave PENDING, return 504 `PAYMENT_GATEWAY_TIMEOUT`
    - `shared.infra.MockPaymentGateway`: returns configurable SUCCESS/FAILED/TIMEOUT via `@Value("${payment.gateway.behavior}")` property
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [ ] 3.4 Implement `booking.BookingExpiryScheduler`
    - `@Scheduled(fixedDelay = 30000)` — runs every 30s
    - Query `booking.BookingRepository` for all PENDING bookings where `paymentDeadline < now()`
    - For each: transition to EXPIRED, restore DB `available_quantity`, update `shared.cache.InventoryCache`, restore voucher usage via `voucher.VoucherService`
    - _Requirements: 2.9, 9.8_

- [ ] 4. Checkpoint — Phase 3 complete: Ensure all tests pass
  - Ensure reservation, payment, expiry, and idempotency flows compile and unit tests pass. Ask the user if questions arise.

- [ ] 5. Phase 4 — Customer APIs
  - [ ] 5.1 Implement `concert.ConcertService` and `concert.ConcertController`
    - `concert.ConcertService`:
      - `listPublishedConcerts(Pageable)` → paginated DTO via `ConcertRepository.findAllByPublishedTrue`
      - `getConcertDetail(id)` → full detail with TicketCategories; reads remaining quantity from `shared.cache.InventoryCache` (falls back to DB `available_quantity` if key absent)
      - `createConcert(request)` → persists Concert + TicketCategories atomically
      - `publishConcert(id)` → sets `published=true`, loads all TicketCategory quantities into Redis via `shared.cache.InventoryCache`
    - `concert.ConcertController`: `GET /api/v1/concerts`, `GET /api/v1/concerts/{id}`; `@Valid` on DTOs; return `ApiResponse<T>`; annotate with springdoc `@Operation`, `@ApiResponse`, `@Schema`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [ ] 5.2 Implement `booking.BookingController` and `booking.PaymentController`
    - `booking.BookingController`: `POST /api/v1/bookings/reserve`, `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`; `Idempotency-Key` and `X-User-Id` as documented headers
    - `booking.PaymentController`: `POST /api/v1/bookings/{id}/pay`
    - `@Valid` on DTOs; return `ApiResponse<T>`; annotate with springdoc `@Operation`, `@ApiResponse`, `@Schema`
    - _Requirements: 2.1, 3.1, 5.1, 5.2, 5.3, 5.5_

- [ ] 6. Phase 5 — Operation APIs
  - [ ] 6.1 Implement `admin.AdminConcertController` and concert admin logic in `admin.AdminService`
    - `admin.AdminConcertController`: `POST /api/v1/admin/concerts`, `POST /api/v1/admin/concerts/{id}/publish`, `GET /api/v1/admin/concerts/{id}/inventory`, `PATCH /api/v1/admin/ticket-categories/{id}/quantity`
    - `getInventoryStats(concertId)`: for each TicketCategory return `totalQuantity`, `soldCount` (CONFIRMED + AWAITING_PAYMENT bookings), `remaining = total − sold`
    - `updateTicketCategoryQuantity(id, newQuantity)`: reject if `newQuantity < soldCount` (throw `QuantityBelowSoldException` 422); update DB and set `shared.cache.InventoryCache` to `newQuantity − soldCount`
    - `@Valid` on DTOs; return `ApiResponse<T>`; springdoc annotations
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [ ] 6.2 Implement `admin.AdminBookingController` and booking admin logic in `admin.AdminService`
    - `listBookings(filters, pageable)` with filtering by `state`, `concertId`, `createdFrom`/`createdTo`
    - `transitionBookingState(bookingId, targetState)`: validate via `booking.BookingState.canTransitionTo()`; on cancellation of CONFIRMED/AWAITING_PAYMENT restore DB `available_quantity`, update `shared.cache.InventoryCache`, restore voucher usage via `voucher.VoucherService`
    - `admin.AdminBookingController`: `GET /api/v1/admin/bookings`, `PATCH /api/v1/admin/bookings/{id}/state`
    - `@Valid` on DTOs; return `ApiResponse<T>`; springdoc annotations
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ] 6.3 Implement `admin.AdminVoucherController` and `voucher.VoucherService`
    - `voucher.VoucherService`:
      - `validateAndApplyVoucher(userId, voucherId, bookingAmount)`: acquires Redisson lock via `shared.cache.VoucherLockService`, checks campaign dates, usage limits, minimum amount, marks voucher used; discount: percentage → `floor(amount × (1 − rate/100))` min 0.01; fixed → `max(amount − fixed, 0.01)`
      - `restoreVoucherUsage(voucherId)`: decrements `current_usage_count`, marks voucher unused
    - `shared.cache.VoucherLockService`: Redisson `RLock`, key `lock:voucher:{userId}:{voucherId}`; `tryLock(waitSeconds=3, leaseSeconds=10)` → throws `ServiceBusyException` on timeout
    - `admin.AdminVoucherController`: `POST /api/v1/admin/voucher-campaigns`, `POST /api/v1/admin/voucher-campaigns/{id}/vouchers` (batch generate unique alphanumeric codes 8–16 chars, one transaction), `GET /api/v1/admin/voucher-campaigns/{id}/stats`
    - `@Valid` on DTOs; return `ApiResponse<T>`; springdoc annotations
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 7. Checkpoint — Phase 5 complete: Ensure all tests pass
  - Ensure all controllers compile, Swagger UI loads at `/swagger-ui.html`, and unit tests pass. Ask the user if questions arise.

- [ ] 8. Phase 6 — Redis layer: `InventoryCache`, `RateLimitFilter`, and `UserIdHeaderFilter`
  - [ ] 8.1 Implement `shared.cache.InventoryCache`
    - `RedisTemplate<String, Long>`, key pattern `inventory:{ticketCategoryId}`
    - `initInventory(ticketCategoryId, quantity)` — called on concert publish
    - `getInventory(ticketCategoryId)` → `Optional<Long>` (empty if key absent → caller falls back to DB)
    - `updateInventory(ticketCategoryId, quantity)` — set absolute value post-commit
    - `incrementInventory(ticketCategoryId, qty)` — atomic INCRBY for restoration after cancel/expiry
    - Redis unavailable: catch exception, log ERROR, persist `ReconciliationTask` in PostgreSQL, schedule background re-sync
    - _Requirements: 1.4, 9.6, 9.7, 9.8, 9.9_

  - [ ] 8.2 Implement `shared.filter.RateLimitFilter` and `shared.filter.UserIdHeaderFilter`
    - `shared.filter.RateLimitFilter`: extends `OncePerRequestFilter`; applies to `/api/v1/**`; Redis sliding window counter `rate:{userId}` with 60s TTL; exceeds 200 → 429 `Retry-After: 60`; fail open on Redis unavailability
    - `shared.filter.UserIdHeaderFilter`: extracts `X-User-Id` header, stores in `shared.common.UserContext` thread-local
    - `shared.common.UserContext`: `ThreadLocal<Long>` with `set(userId)`, `get()`, `clear()`
    - _Requirements: 11.5, 2.1, 3.5, 5.2_

- [ ] 9. Phase 7 — Tests
  - [ ] 9.1 Write property test P1 — `property.BookingStateMachinePropertyTest`
    - **Property 1: Booking State Machine Validity** — for any state S and target T, transition accepted iff `T ∈ VALID_TRANSITIONS[S]`; invalid → `INVALID_STATE_TRANSITION`
    - Tests `booking.BookingState.canTransitionTo()` directly; no Spring context needed
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 1: booking-state-machine-validity")`
    - **Validates: Requirements 3.4, 6.3, 6.4**

  - [ ]* 9.2 Write property test P2 — `property.IdempotencyPropertyTest`
    - **Property 2: Idempotency — No Duplicate Bookings** — re-submitting with same key K within 24h returns same response, no duplicate Booking record
    - Uses `shared.cache.IdempotencyService` with mocked `RedisTemplate`
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")`
    - **Validates: Requirements 2.5, 9.3**

  - [ ]* 9.3 Write property test P3 — `property.VoucherDiscountPropertyTest`
    - **Property 3: Voucher Discount Calculation** — percentage: `max(floor(A × (1 − R/100)), 0.01)`; fixed: `max(A − F, 0.01)`
    - Tests `voucher.VoucherService` discount logic directly
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")`
    - **Validates: Requirements 4.2**

  - [ ]* 9.4 Write property test P4 — `property.InventoryPropertyTest`
    - **Property 4: Inventory Non-Negative Invariant** — `available_quantity` never < 0 across any sequence of reserve/cancel/expire
    - Uses `booking.BookingService` with Testcontainers PostgreSQL to verify the atomic `UPDATE … WHERE available_quantity >= :qty`
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 4: inventory-non-negative-invariant")`
    - **Validates: Requirements 9.1, 9.6**

  - [ ]* 9.5 Write property test P5 — `property.ConcurrencyPropertyTest` (oversell)
    - **Property 5: Oversell Prevention Under Concurrency** — initial inventory N, concurrent reservations, non-CANCELLED/non-EXPIRED BookingItems never exceed N
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 5: oversell-prevention-under-concurrency")`
    - **Validates: Requirements 9.1, 9.2**

  - [ ]* 9.6 Write property test P6 — `property.ConcurrencyPropertyTest` (voucher limit)
    - **Property 6: Voucher Usage Limit Under Concurrency** — `maxUsage = M`, concurrent applications, successful count never exceeds M
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 6: voucher-usage-limit-under-concurrency")`
    - **Validates: Requirements 4.4, 4.5**

  - [ ]* 9.7 Write property test P7 — `property.ConcurrencyPropertyTest` (restoration)
    - **Property 7: Cancellation/Expiry Restore Inventory** — CANCELLED/EXPIRED booking → DB `available_quantity` +N, Redis cache updated, voucher `current_usage_count` -1
    - `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 7: cancellation-expiry-restoration")`
    - **Validates: Requirements 2.9, 3.3, 4.6, 6.5, 9.8**

  - [ ] 9.8 Write `integration.FlashSaleConcurrencyTest` — 3 concurrency scenarios (Testcontainers)
    - Testcontainers (real PostgreSQL + Redis) + `CountDownLatch` for coordinated concurrency
    - **Case A — Ticket Overselling Prevention**: inventory=10, 100 concurrent users → exactly 10 succeed (201 PENDING), 90 receive 409 `TICKET_SOLD_OUT`, DB `available_quantity=0`, Redis `inventory:{id}=0`
    - **Case B — Duplicate Request Idempotency**: same `Idempotency-Key` sent 3× concurrently → exactly 1 Booking record, all 3 responses identical (same `bookingId`, `state`, `totalAmount`), `available_quantity` decrements exactly once
    - **Case C — Voucher Race Condition**: `maxUsage=10`, 100 concurrent users → ≤10 succeed, rest 409 `VOUCHER_EXHAUSTED`, DB `current_usage_count=10`
    - _Requirements: 9.1, 9.2, 9.3, 4.4, 4.5_

  - [ ]* 9.9 Write unit tests for `booking.BookingExpirySchedulerTest`, payment timeout, and `shared.infra.DataSeederTest`
    - `booking.BookingExpirySchedulerTest`: verify PENDING → EXPIRED with DB quantity restore AND `shared.cache.InventoryCache` update
    - Payment timeout: `shared.infra.MockPaymentGateway` timeout → booking stays PENDING, response 504
    - `shared.infra.DataSeederTest`: idempotent seeding on empty DB and with `FLASH_SALE_MODE=true`
    - _Requirements: 2.9, 3.8, 10.1, 10.2, 10.4_

  - [ ] 9.10 Write `integration.ReservationIntegrationTest` using Testcontainers
    - Testcontainers PostgreSQL + Redis
    - reserve → pay (SUCCESS) → CONFIRMED; verify BookingItems, amounts, `paymentTimestamp`
    - reserve → pay (FAILED) → CANCELLED; verify DB inventory restored and `shared.cache.InventoryCache` updated
    - reserve with and without valid voucher
    - _Requirements: 2.1, 3.2, 3.3, 4.2_

- [ ] 10. Checkpoint — Phase 7 complete: Ensure all tests pass
  - Run full test suite (unit + property + integration). Ensure all pass. Ask the user if questions arise.

- [ ] 11. Phase 8 — Documentation and DevEx
  - [ ] 11.1 Implement `shared.infra.DataSeeder`
    - `CommandLineRunner`; check-before-insert by unique name for idempotency
    - No Concert records → insert ≥ 2 published Concerts each with VIP (price > 500,000, qty ≥ 50) and Standard (price < 500,000, qty ≥ 100); load inventory via `shared.cache.InventoryCache`
    - No VoucherCampaign records → insert ≥ 1 active campaign with ≥ 5 Voucher codes and `maxUsage ≥ 5`
    - `FLASH_SALE_MODE=true` → insert 1 additional Concert with exactly 100 tickets; load into `shared.cache.InventoryCache`
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [ ]* 11.2 Write `README.md` and Postman collection
    - `README.md`: modular monolith architecture diagram, trade-offs (PostgreSQL source-of-truth, Redis read-cache, idempotency design), run instructions (`docker-compose up`)
    - `postman/concert-ticket-booking.postman_collection.json`: all 18 endpoints grouped by module
    - `postman/local.postman_environment.json`: `baseUrl`, `userId`, `adminUserId`, `idempotencyKey`
    - Pre-request script: auto-generate UUID v4 `Idempotency-Key` on reservation requests
    - Test scripts: assert HTTP status and `success` field on each request
    - _Requirements: 11.1, 11.2_

- [ ] 12. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass and app starts cleanly with `docker-compose up`. Ask the user if any questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- **Module boundaries**: code within a module can reference `shared.*` freely; cross-module references (e.g., `booking` → `voucher`) go through service interfaces, not repositories directly
- **No `InventoryLockService` for tickets** — inventory concurrency is handled by the atomic `UPDATE … WHERE available_quantity >= :qty`; only vouchers use Redisson locks via `shared.cache.VoucherLockService`
- **PostgreSQL is the write source of truth**; `shared.cache.InventoryCache` is a read cache updated post-commit, never before the DB transaction
- Property tests: jqwik 1.8.4, `@Property(tries = 100)`, tag format `@Tag("Feature: concert-ticket-booking, Property N: <property_text>")`
- Unit tests: JUnit 5 + Mockito; integration and concurrency tests: Testcontainers (PostgreSQL + Redis)
- `shared.infra.MockPaymentGateway` behavior (SUCCESS/FAILED/TIMEOUT): `@Value("${payment.gateway.behavior}")`
- `booking.BookingExpiryScheduler` (`@Scheduled` every 30s): restores BOTH DB `available_quantity` AND `shared.cache.InventoryCache`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2"] },
    { "id": 3, "tasks": ["2.3", "3.1"] },
    { "id": 4, "tasks": ["3.2", "3.3", "3.4"] },
    { "id": 5, "tasks": ["5.1", "5.2"] },
    { "id": 6, "tasks": ["6.1", "6.2", "6.3"] },
    { "id": 7, "tasks": ["8.1", "8.2"] },
    { "id": 8, "tasks": ["9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7"] },
    { "id": 9, "tasks": ["9.8", "9.9", "9.10"] },
    { "id": 10, "tasks": ["11.1"] },
    { "id": 11, "tasks": ["11.2"] }
  ]
}
```
