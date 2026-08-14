# Implementation Plan: Concert Ticket Booking Platform

## Overview

Implement a monolithic Spring Boot 3 backend for a concert ticket booking platform supporting two user roles (Customer and Operator). Implementation follows a phase-based order: Foundation → Core Domain → Booking Correctness → Customer APIs → Operation APIs → Redis Layer → Tests → Documentation & DevEx. PostgreSQL is the authoritative source for inventory; Redis serves as a read cache updated asynchronously after DB commits.

## Tasks

- [ ] 1. Phase 1 — Foundation: Project setup and infrastructure
  - [ ] 1.1 Initialize Spring Boot project and configure `pom.xml`
    - Initialize Spring Boot 3 project with Maven
    - Add all required dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `redisson-spring-boot-starter`, `postgresql`, `flyway-core`, `springdoc-openapi-starter-webmvc-ui 2.x`, `jqwik 1.8.4` (test scope), `testcontainers` (test scope)
    - _Requirements: 11.1, 11.2_

  - [ ] 1.2 Create `application.yml` and `docker-compose.yml`
    - Create `application.yml` with datasource, Redis, Redisson, JPA, scheduler, and springdoc configuration
    - Create `docker-compose.yml` at project root with services: `postgres` (PostgreSQL 15), `redis` (Redis 7), and `app` (Spring Boot jar); configure environment variables for datasource and Redis connection; expose ports 8080, 5432, 6379
    - Set up Flyway migration directory structure under `src/main/resources/db/migration`
    - _Requirements: 11.1, 11.2_

  - [ ] 1.3 Implement `ApiResponse<T>` and `GlobalExceptionHandler`
    - Define `ApiResponse<T>` generic response envelope with `success`, `data`/`error`, and `timestamp` fields
    - Implement the 6 base exception types: `ApplicationException` (base, carries `errorCode` field), `ResourceNotFoundException` (→ 404), `ConflictException` (→ 409), `ValidationException` (→ 422), `ForbiddenException` (→ 403), `ServiceBusyException` (→ 503), `PaymentGatewayTimeoutException` (→ 504); each carries a `code` field
    - Implement `GlobalExceptionHandler` annotated with `@ControllerAdvice`; map all exceptions from the design's handler table to the correct HTTP status and error code; handle `MethodArgumentNotValidException` → HTTP 400 with `fields` array; handle unhandled `Exception` → HTTP 500 without stack trace
    - Wrap all responses in `ApiResponse<T>` with UTC ISO-8601 timestamp
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [ ] 2. Phase 2 — Core Domain: JPA entities and repositories
  - [ ] 2.1 Write Flyway migration V1 — create all tables
    - Create tables: `concerts`, `ticket_categories` (with `available_quantity` column), `bookings`, `booking_items`, `voucher_campaigns`, `vouchers` per the ERD in the design
    - Add all foreign key constraints
    - Add `UNIQUE(idempotency_key)` and `UNIQUE(user_id, idempotency_key)` constraints on `bookings`
    - _Requirements: 2.1, 7.1_

  - [ ] 2.2 Implement JPA entity classes
    - Implement `Concert`, `TicketCategory`, `Booking`, `BookingItem`, `VoucherCampaign`, `Voucher` JPA entities with all fields, relationships, and Lombok annotations
    - Implement `BookingState` enum with `VALID_TRANSITIONS` map and `canTransitionTo()` method as specified in the design
    - Add `@CreationTimestamp`/`@UpdateTimestamp` for audit fields
    - _Requirements: 2.1, 3.4, 5.4_

  - [ ] 2.3 Implement Spring Data JPA repositories
    - Implement `ConcertRepository`, `TicketCategoryRepository`, `BookingRepository`, `BookingItemRepository`, `VoucherRepository`, `VoucherCampaignRepository`
    - Add custom query methods: `findAllByPublishedTrue(Pageable)`, `findByUserIdOrderByCreatedAtDesc(Long, Pageable)`, `findAllByStateAndPaymentDeadlineBefore(BookingState, LocalDateTime)`, admin filter query with `state`/`concertId`/`createdFrom`/`createdTo`
    - _Requirements: 1.1, 1.6, 5.3, 6.1, 6.2_

- [ ] 3. Phase 3 — Booking Correctness: Reservation flow, idempotency, and state machine
  - [ ] 3.1 Implement `BookingService` — atomic reservation flow
    - Implement `reserve(userId, request)` with the following DB-authoritative flow:
      1. Validate ticket category exists and concert is published
      2. `BEGIN TRANSACTION`
      3. `UPDATE ticket_categories SET available_quantity = available_quantity - :qty WHERE id = :id AND available_quantity >= :qty`; if 0 rows affected → `ROLLBACK` and throw `TicketSoldOutException` (409 `TICKET_SOLD_OUT`)
      4. `INSERT INTO bookings` (state=PENDING, payment_deadline=now+15min, idempotency_key)
      5. `INSERT INTO booking_items`
      6. If `voucherCode` present → acquire Redisson `lock:voucher:{userId}:{voucherId}` and delegate to `VoucherService`
      7. `COMMIT`
      8. Post-commit: async update `inventory:{ticketCategoryId}` Redis cache
      9. Post-commit: store idempotency key → serialized response in Redis (TTL 24h)
    - Set `paymentDeadline = createdAt + 15 minutes`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.7, 2.8, 9.1, 9.2_

  - [ ] 3.2 Implement `IdempotencyFilter` and `IdempotencyService`
    - Implement `IdempotencyService` using `RedisTemplate<String, String>` with key pattern `idempotency:{key}` and 24-hour TTL; implement `getIfPresent(key)` → `Optional<String>`, `store(key, responseJson)`; fail open (allow request) when Redis is unavailable; log a warning
    - Implement `IdempotencyFilter` extending `OncePerRequestFilter`; intercept `POST /api/v1/bookings/reserve`; if `Idempotency-Key` header is missing → return 400 `MISSING_IDEMPOTENCY_KEY`; if key is found in Redis → return cached JSON response immediately (short-circuit); otherwise wrap response in `ContentCachingResponseWrapper` and store on 2xx
    - _Requirements: 2.5, 2.6, 9.3_

  - [ ] 3.3 Implement `BookingService` — payment flow and state transitions
    - Implement `pay(userId, bookingId, paymentMethod)`:
      1. Load booking; throw `BookingNotFoundException` (404) if not found; throw `ForbiddenException` (403) if `userId` mismatch
      2. Validate state is PENDING; throw `InvalidBookingStateException` (409) otherwise
      3. Transition to AWAITING_PAYMENT, delegate to `PaymentService`/`MockPaymentGateway`
      4. On SUCCESS → transition to CONFIRMED, record `paymentTimestamp` and `totalAmount`
      5. On FAILED → transition to CANCELLED, restore DB `available_quantity` (`UPDATE available_quantity + qty`), update Redis cache, restore voucher usage, return 402 `PAYMENT_FAILED`
      6. On timeout (>10s) → leave PENDING, return 504 `PAYMENT_GATEWAY_TIMEOUT`
    - Implement `MockPaymentGateway` returning configurable SUCCESS/FAILED/TIMEOUT behavior via a Spring `@Value` property
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [ ] 3.4 Implement `BookingExpiryScheduler`
    - Annotate with `@Scheduled(fixedDelay = 30000)`
    - Query `BookingRepository` for all PENDING bookings where `paymentDeadline < now()`
    - For each expired booking: transition state to EXPIRED, restore DB `available_quantity` (`UPDATE available_quantity + qty`) AND update Redis `inventory:{ticketCategoryId}` cache, restore voucher usage count via `VoucherService`
    - _Requirements: 2.9, 9.8_

- [ ] 4. Checkpoint — Phase 3 complete: Ensure all tests pass
  - Ensure reservation, payment, expiry, and idempotency flows compile and unit tests pass. Ask the user if questions arise.

- [ ] 5. Phase 4 — Customer APIs
  - [ ] 5.1 Implement `ConcertService` and `ConcertController`
    - Implement `ConcertService`: `listPublishedConcerts(Pageable)` → paginated DTO; `getConcertDetail(id)` → full detail with TicketCategories; reads remaining quantity from Redis cache (falls back to DB if key absent); `createConcert(request)` → persists Concert + TicketCategories atomically; `publishConcert(id)` → marks published, loads all TicketCategory quantities into Redis via `InventoryCache`
    - Implement `ConcertController`: `GET /api/v1/concerts` (paginated, published only), `GET /api/v1/concerts/{id}` (detail); annotate with `@Operation`, `@ApiResponse`, `@Schema` (springdoc); `@Valid` on request DTOs; return `ApiResponse<T>`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [ ] 5.2 Implement `BookingController` and `PaymentController`
    - `BookingController`: `POST /api/v1/bookings/reserve`, `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`; include `Idempotency-Key` and `X-User-Id` as documented header parameters
    - `PaymentController`: `POST /api/v1/bookings/{id}/pay`
    - Annotate all endpoints and DTOs with `@Operation`, `@ApiResponse`, `@Schema` (springdoc) for Swagger UI; `@Valid` on request DTOs; return `ApiResponse<T>`
    - _Requirements: 2.1, 3.1, 5.1, 5.2, 5.3, 5.5_

- [ ] 6. Phase 5 — Operation APIs
  - [ ] 6.1 Implement `AdminConcertController` and admin concert logic in `AdminService`
    - `AdminConcertController`: `POST /api/v1/admin/concerts`, `POST /api/v1/admin/concerts/{id}/publish`, `GET /api/v1/admin/concerts/{id}/inventory`, `PATCH /api/v1/admin/ticket-categories/{id}/quantity`
    - Implement `getInventoryStats(concertId)`: for each TicketCategory return `totalQuantity`, `soldCount` (CONFIRMED + AWAITING_PAYMENT), `remaining = total − sold`
    - Implement `updateTicketCategoryQuantity(id, newQuantity)`: reject if `newQuantity < soldCount` (throw `QuantityBelowSoldException` 422); if accepted, update DB and set Redis inventory to `newQuantity − soldCount`
    - Annotate all endpoints and DTOs with `@Operation`, `@ApiResponse`, `@Schema` (springdoc); `@Valid` on request DTOs; return `ApiResponse<T>`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [ ] 6.2 Implement `AdminBookingController` and admin booking logic in `AdminService`
    - Implement `listBookings(filters, pageable)` with filtering by `state`, `concertId`, `createdFrom`/`createdTo`
    - Implement `transitionBookingState(bookingId, targetState)`: validate via `BookingState.canTransitionTo()`; on cancellation of CONFIRMED/AWAITING_PAYMENT restore DB `available_quantity`, update Redis cache, restore voucher usage
    - `AdminBookingController`: `GET /api/v1/admin/bookings`, `PATCH /api/v1/admin/bookings/{id}/state`
    - Annotate all endpoints and DTOs with `@Operation`, `@ApiResponse`, `@Schema` (springdoc); `@Valid` on request DTOs; return `ApiResponse<T>`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ] 6.3 Implement `AdminVoucherController` and `VoucherService`
    - Implement `VoucherService`: `validateAndApplyVoucher(userId, voucherId, bookingAmount)` — acquires Redisson `lock:voucher:{userId}:{voucherId}`, checks campaign active dates, usage limits, minimum amount, marks voucher used; discount calculation: percentage → `floor(amount × (1 − rate/100))` min 0.01; fixed → `max(amount − fixed, 0.01)`; `restoreVoucherUsage(voucherId)` — decrements `current_usage_count` and marks voucher unused
    - Implement `VoucherLockService` using Redisson `RLock` with key pattern `lock:voucher:{userId}:{voucherId}` (no `lock:ticket:{ticketCategoryId}` keys); `tryLock(key, waitSeconds=3, leaseSeconds=10)` → throws `ServiceBusyException` on timeout; `unlock(key)`
    - `AdminVoucherController`: `POST /api/v1/admin/voucher-campaigns`, `POST /api/v1/admin/voucher-campaigns/{id}/vouchers` (batch generate unique alphanumeric codes 8–16 chars in one transaction), `GET /api/v1/admin/voucher-campaigns/{id}/stats`
    - Annotate all endpoints and DTOs with `@Operation`, `@ApiResponse`, `@Schema` (springdoc); `@Valid` on request DTOs; return `ApiResponse<T>`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 7. Checkpoint — Phase 5 complete: Ensure all tests pass
  - Ensure all controllers compile, Swagger UI loads, and unit tests pass. Ask the user if questions arise.

- [ ] 8. Phase 6 — Redis layer: `InventoryCache`, `RateLimitFilter`, and `UserIdHeaderFilter`
  - [ ] 8.1 Implement `InventoryCache`
    - Use `RedisTemplate<String, Long>` with key pattern `inventory:{ticketCategoryId}`
    - Implement `initInventory(ticketCategoryId, quantity)`, `getInventory(ticketCategoryId)`, `updateInventory(ticketCategoryId, quantity)` (set after DB commit), `incrementInventory(ticketCategoryId, qty)` (atomic INCRBY for restoration after cancel/expiry)
    - Handle Redis unavailability: catch exceptions, log at ERROR, persist a `ReconciliationTask` record in PostgreSQL and schedule background reconciliation
    - _Requirements: 1.4, 9.6, 9.7, 9.8, 9.9_

  - [ ] 8.2 Implement `RateLimitFilter` and `UserIdHeaderFilter`
    - `RateLimitFilter`: extend `OncePerRequestFilter`; apply to all `/api/v1/**`; use Redis sliding window counter with key `rate:{userId}` and 60-second TTL; if counter exceeds 200 → return 429 with `Retry-After: 60` header; fail open when Redis is unavailable
    - `UserIdHeaderFilter`: extract `X-User-Id` header and store in a `UserContext` thread-local for downstream use
    - _Requirements: 11.5, 2.1, 3.5, 5.2_

- [ ] 9. Phase 7 — Tests: Property tests, concurrency tests, and unit tests
  - [ ] 9.1 Write property test P1 — Booking State Machine Validity (`BookingStateMachinePropertyTest`)
    - **Property 1: Booking State Machine Validity** — for any booking state S and target T, transition is accepted iff `T ∈ VALID_TRANSITIONS[S]`; invalid transitions rejected with `INVALID_STATE_TRANSITION`
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 1: booking-state-machine-validity")`
    - **Validates: Requirements 3.4, 6.3, 6.4**

  - [ ]* 9.2 Write property test P2 — Idempotency: No Duplicate Bookings (`IdempotencyPropertyTest`)
    - **Property 2: Idempotency — No Duplicate Bookings** — re-submitting any successfully processed reservation with the same key K within 24h returns the same response and creates no additional Booking record
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")`
    - **Validates: Requirements 2.5, 9.3**

  - [ ]* 9.3 Write property test P3 — Voucher Discount Calculation Correctness (`VoucherDiscountPropertyTest`)
    - **Property 3: Voucher Discount Calculation Correctness** — for percentage voucher with rate R (1–100): discounted = `max(floor(A × (1 − R/100)), 0.01)`; for fixed voucher with amount F: discounted = `max(A − F, 0.01)`
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 3: voucher-discount-calculation-correctness")`
    - **Validates: Requirements 4.2**

  - [ ]* 9.4 Write property test P4 — Inventory Non-Negative Invariant (`InventoryPropertyTest`)
    - **Property 4: Inventory Non-Negative Invariant** — for any sequence of reservation, cancellation, and expiry operations, `available_quantity` in PostgreSQL never falls below 0; the atomic `UPDATE … WHERE available_quantity >= :qty` enforces this at the DB level
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 4: inventory-non-negative-invariant")`
    - **Validates: Requirements 9.1, 9.6**

  - [ ]* 9.5 Write property test P5 — Oversell Prevention Under Concurrency (`ConcurrencyPropertyTest`)
    - **Property 5: Oversell Prevention Under Concurrency** — for any TicketCategory with initial inventory N, after any concurrent reservation requests, count of non-CANCELLED/non-EXPIRED BookingItems never exceeds N
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 5: oversell-prevention-under-concurrency")`
    - **Validates: Requirements 9.1, 9.2**

  - [ ]* 9.6 Write property test P6 — Voucher Usage Limit Under Concurrency (`ConcurrencyPropertyTest`)
    - **Property 6: Voucher Usage Limit Under Concurrency** — for any voucher with `maxUsage = M`, total successful concurrent applications never exceeds M; Redisson lock scoped to `lock:voucher:{userId}:{voucherId}` serializes per-user and DB `current_usage_count` enforces the global cap
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 6: voucher-usage-limit-under-concurrency")`
    - **Validates: Requirements 4.4, 4.5**

  - [ ]* 9.7 Write property test P7 — Cancellation/Expiry Restoration (`ConcurrencyPropertyTest`)
    - **Property 7: Cancellation and Expiry Restore Inventory** — for any booking with N tickets in a TicketCategory and an optional voucher, when booking transitions to CANCELLED or EXPIRED (via any path), PostgreSQL `available_quantity` increases by exactly N, Redis `inventory:{ticketCategoryId}` is updated, and if a voucher was applied `current_usage_count` decreases by exactly 1
    - Use jqwik `@Property(tries = 100)`, `@Tag("Feature: concert-ticket-booking, Property 7: cancellation-expiry-restoration")`
    - **Validates: Requirements 2.9, 3.3, 4.6, 6.5, 9.8**

  - [ ] 9.8 Write `FlashSaleConcurrencyTest` — 3 concurrency scenarios (Testcontainers)
    - Use Testcontainers (real PostgreSQL + Redis) and `CountDownLatch` for coordinated concurrent execution
    - **Case A — Ticket Overselling Prevention**: inventory=10, 100 concurrent users each reserve 1 ticket → exactly 10 succeed (HTTP 201, PENDING), exactly 90 receive 409 `TICKET_SOLD_OUT`, DB `available_quantity=0`, Redis cache `inventory:{id}=0`
    - **Case B — Duplicate Request Idempotency**: same `Idempotency-Key` sent 3 times concurrently → exactly 1 Booking record created, all 3 responses return identical body (same `bookingId`, `state`, `totalAmount`), `available_quantity` decrements by exactly the requested quantity
    - **Case C — Voucher Race Condition**: VoucherCampaign `maxUsage=10`, 100 concurrent users each apply a voucher → at most 10 succeed, rest receive 409 `VOUCHER_EXHAUSTED`, DB `current_usage_count` equals exactly 10
    - _Requirements: 9.1, 9.2, 9.3, 4.4, 4.5_

  - [ ]* 9.9 Write unit tests for `BookingExpiryScheduler`, payment timeout, and `DataSeeder`
    - `BookingExpirySchedulerTest`: verify PENDING → EXPIRED transition with DB quantity restoration AND Redis cache update for each expired booking
    - Payment timeout test: verify gateway timeout leaves booking in PENDING state and returns 504
    - `DataSeederTest`: verify idempotent seeding on empty DB and with `FLASH_SALE_MODE=true`
    - _Requirements: 2.9, 3.8, 10.1, 10.2, 10.4_

  - [ ] 9.10 Write `ReservationIntegrationTest` using Testcontainers
    - Start PostgreSQL and Redis via Testcontainers
    - Test full flow: reserve → pay (SUCCESS) → CONFIRMED; verify BookingItems, amounts, payment timestamp
    - Test reserve → pay (FAILED) → CANCELLED; verify DB inventory restored and Redis cache updated
    - Test reservation with and without valid voucher
    - _Requirements: 2.1, 3.2, 3.3, 4.2_

- [ ] 10. Checkpoint — Phase 7 complete: Ensure all tests pass
  - Run the full test suite (unit + property + integration). Ensure all tests pass. Ask the user if questions arise.

- [ ] 11. Phase 8 — Documentation and DevEx
  - [ ] 11.1 Implement `DataSeeder`
    - Implement as `CommandLineRunner`; check-before-insert by unique name/identifier for idempotency
    - If no Concert records: insert ≥ 2 published Concerts each with VIP (price > 500,000, qty ≥ 50) and Standard (price < 500,000, qty ≥ 100) categories; load inventory into Redis
    - If no VoucherCampaign records: insert ≥ 1 active campaign with ≥ 5 pre-generated Voucher codes and `maxUsage ≥ 5`
    - If `FLASH_SALE_MODE=true`: insert 1 additional published Concert with exactly 100 tickets and load into Redis
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [ ]* 11.2 Write `README.md` and Postman collection
    - Write `README.md` with architecture diagram, trade-offs section (PostgreSQL-as-source-of-truth rationale, Redis read-cache strategy, idempotency design), and run instructions (`docker-compose up`)
    - Create `postman/concert-ticket-booking.postman_collection.json` with all 18 endpoints grouped by feature: Concert browsing, Booking reservation, Payment, Admin bookings, Admin concerts, Admin vouchers
    - Create `postman/local.postman_environment.json` with variables: `baseUrl`, `userId`, `adminUserId`, `idempotencyKey`
    - Add pre-request script for `Idempotency-Key` auto-generation (UUID v4) on reservation requests
    - Add test scripts asserting HTTP status codes and `success` field on each request
    - _Requirements: 11.1, 11.2_

- [ ] 12. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass and the application starts cleanly with `docker-compose up`. Ask the user if any questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints at end of Phase 3, Phase 5, Phase 7, and final ensure incremental validation
- **No `InventoryLockService` for ticket categories** — inventory concurrency is handled entirely by the atomic `UPDATE … WHERE available_quantity >= :qty` DB operation; `VoucherLockService` handles only voucher operations via `lock:voucher:{userId}:{voucherId}`
- **PostgreSQL is the write source of truth** for inventory; Redis `inventory:{ticketCategoryId}` is a read cache populated post-commit and after restoration, never written before the DB transaction
- Property tests use jqwik 1.8.4 with `@Property(tries = 100)` and tag format `@Tag("Feature: concert-ticket-booking, Property N: <property_text>")`
- Unit tests use JUnit 5 + Mockito; integration and concurrency tests use Testcontainers (PostgreSQL + Redis)
- `MockPaymentGateway` behavior (SUCCESS/FAILED/TIMEOUT) is configurable via a Spring `@Value` property
- `BookingExpiryScheduler` (`@Scheduled` every 30s) restores BOTH DB `available_quantity` AND Redis cache after expiry

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
