# Requirements Document

## Introduction

This document defines requirements for a **Concert Ticket Booking Platform** backend system. The system supports two classes of users: **Customers** who browse concerts and purchase tickets, and **Operators** who manage concert inventory and monitor bookings.

The platform must handle high-concurrency flash-sale scenarios (~50,000 concurrent users, 300–500 booking requests/minute) while preventing overselling, duplicate bookings, and voucher abuse. The scope is divided into three delivery phases to prioritize highest-value features first.

### Scope Boundaries

**In scope:**
- Concert browsing and ticket reservation flow (customer-facing API)
- Internal operation dashboard API (admin-facing)
- Flash sale concurrency controls (distributed locking, idempotency)
- Voucher/discount application and abuse prevention
- Booking lifecycle state management
- Database seeding for demo/test purposes

**Out of scope (explicit assumptions):**
- Authentication is mocked via `X-User-Id` request header; no JWT or OAuth implementation
- Payment gateway is mocked; the Payment API returns a configurable SUCCESS/FAILED response
- Real-time push notifications (email, SMS, WebSocket) are not implemented
- Ticket barcode/QR generation is not implemented
- Multi-currency support is not implemented

---

## Glossary

- **System**: The Concert Ticket Booking Platform backend application
- **Customer**: An end user who browses concerts and purchases tickets
- **Operator**: An internal staff member who manages concerts, tickets, and bookings via the dashboard
- **Concert**: An event with a defined date, venue, and inventory of ticket categories
- **TicketCategory**: A named tier of ticket for a concert (e.g., VIP, Standard) with a defined price and quantity
- **Booking**: A customer's reservation request; transitions through a defined state machine
- **BookingItem**: A line item within a Booking representing one or more tickets of a given TicketCategory
- **Voucher**: A promotional code that grants a percentage or fixed discount on a booking, subject to usage limits
- **VoucherCampaign**: A set of Vouchers associated with a promotional event, with defined start/end dates and usage caps
- **FlashSale**: A time-boxed, high-demand sale period during which ticket inventory is contended by many concurrent users
- **DistributedLock**: A Redis-based mutex that serializes access to a shared resource across application instances
- **IdempotencyKey**: A client-supplied unique token (non-empty string, max 128 characters) that ensures a booking request is processed at most once
- **BookingState**: The current lifecycle status of a Booking. Valid transitions: `PENDING` → `AWAITING_PAYMENT` → `CONFIRMED` | `CANCELLED`; `PENDING` → `EXPIRED`
- **Overselling**: The condition where total confirmed tickets for a TicketCategory exceed its defined quantity limit
- **Seeder**: An application component that populates the database with representative demo data on startup
- **ActiveVoucherCampaign**: A VoucherCampaign where the current date falls within [start_date, end_date] inclusive

---

## Requirements

### Requirement 1: Browse Concerts

**User Story:** As a Customer, I want to browse available concerts with their ticket categories and pricing, so that I can decide which concert to attend.

#### Acceptance Criteria

1. THE System SHALL expose a paginated `GET /api/v1/concerts` endpoint that returns a list of published Concert summaries, defaulting to 20 items per page with a maximum of 100 per page.
2. WHEN a Customer requests a concert list, THE System SHALL return each Concert's name, venue, date, and available TicketCategory names and prices.
3. WHEN a Customer requests details for a specific concert via `GET /api/v1/concerts/{id}`, THE System SHALL return the full Concert detail including all TicketCategories with their name, price, and remaining quantity.
4. WHILE a FlashSale is active, THE System SHALL return up-to-date remaining quantities sourced from Redis cache rather than polling the database on every request.
5. IF a concert with the requested `id` does not exist or is not published, THEN THE System SHALL return HTTP 404 with a structured error response containing a machine-readable error code and a human-readable message.
6. WHEN a Customer requests the concert list, THE System SHALL only return Concerts that have been published by an Operator.

---

### Requirement 2: Ticket Reservation (Booking Creation)

**User Story:** As a Customer, I want to reserve tickets for a concert, so that I can secure my spot before completing payment.

#### Acceptance Criteria

1. WHEN a Customer submits a reservation request to `POST /api/v1/bookings/reserve` with an `Idempotency-Key` header containing a non-empty string of at most 128 characters, THE System SHALL create a Booking in `PENDING` state and return the Booking ID and a payment deadline timestamp.
2. WHEN a reservation request is received, THE System SHALL acquire a DistributedLock on the requested TicketCategory before checking or decrementing inventory.
3. WHEN a reservation request is received for a TicketCategory with remaining quantity greater than or equal to the requested ticket count, THE System SHALL atomically decrement the ticket count and persist the Booking within the same database transaction, then release the DistributedLock after the transaction commits.
4. IF the requested TicketCategory's remaining quantity is less than the requested ticket count, THEN THE System SHALL release the DistributedLock and return HTTP 409 with error code `TICKET_SOLD_OUT`.
5. IF a Customer submits a reservation request with an `Idempotency-Key` that was already successfully processed within the last 24 hours, THEN THE System SHALL return the original response for that key without creating a duplicate Booking; if the original attempt failed, the key may be reused for a new attempt.
6. IF a Customer submits a reservation request without an `Idempotency-Key` header, THEN THE System SHALL return HTTP 400 with error code `MISSING_IDEMPOTENCY_KEY`.
7. WHEN a reservation request includes a `voucherCode`, THE System SHALL validate and apply the Voucher according to Requirement 4 before computing the final booking amount.
8. WHEN a Booking is created in `PENDING` state, THE System SHALL set a payment deadline of 15 minutes from creation time and store it with the Booking record.
9. WHEN a background expiry process detects that a `PENDING` Booking has not transitioned out of `PENDING` state before its payment deadline, THE System SHALL transition the Booking to `EXPIRED` state and restore the reserved ticket quantity to inventory.
10. IF a reservation request specifies a quantity less than 1 or greater than 10 per TicketCategory, THEN THE System SHALL return HTTP 422 with error code `INVALID_QUANTITY`.

---

### Requirement 3: Booking Payment

**User Story:** As a Customer, I want to confirm payment for my pending booking, so that my tickets are officially reserved.

#### Acceptance Criteria

1. WHEN a Customer submits a payment request to `POST /api/v1/bookings/{id}/pay` for a Booking in `PENDING` state, THE System SHALL invoke the mocked Payment Gateway and transition the Booking to `AWAITING_PAYMENT` state, returning the Booking ID and current state.
2. WHEN the Payment Gateway returns a SUCCESS response, THE System SHALL transition the Booking to `CONFIRMED` state and return the Booking with its state, all BookingItems with quantities and prices, applied voucher code (if any), total amount, and payment timestamp.
3. WHEN the Payment Gateway returns a FAILED response, THE System SHALL transition the Booking to `CANCELLED` state, restore the ticket quantity to the Redis-backed inventory, and return HTTP 402 with error code `PAYMENT_FAILED`.
4. IF a Customer submits a payment request for a Booking not in `PENDING` state, THEN THE System SHALL return HTTP 409 with error code `INVALID_BOOKING_STATE`, including the current state in the response.
5. IF a Customer submits a payment request for a Booking that belongs to a different user, THEN THE System SHALL return HTTP 403 with error code `FORBIDDEN`.
6. WHEN a Booking is transitioned to `CONFIRMED`, THE System SHALL record the payment timestamp and final confirmed amount.
7. IF a Customer submits a payment request for a Booking ID that does not exist, THEN THE System SHALL return HTTP 404 with error code `BOOKING_NOT_FOUND`.
8. IF the mocked Payment Gateway does not respond within 10 seconds, THEN THE System SHALL leave the Booking in `PENDING` state, release any held resources, and return HTTP 504 with error code `PAYMENT_GATEWAY_TIMEOUT`.

---

### Requirement 4: Voucher Application

**User Story:** As a Customer, I want to apply a promotional voucher to my booking, so that I receive a discount on the ticket price.

#### Acceptance Criteria

1. WHEN a reservation request includes a `voucherCode`, THE System SHALL validate that the Voucher exists, belongs to a VoucherCampaign whose start_date ≤ current date ≤ end_date, and has not exceeded its usage limit.
2. WHEN a valid Voucher is applied, THE System SHALL compute the discounted amount — for percentage vouchers: `floor(original_amount × (1 − rate/100))` with a minimum of 0.01; for fixed vouchers: `max(original_amount − fixed_amount, 0.01)` — and record the Voucher usage against the Booking.
3. IF a Customer applies a Voucher that has already been used by that same Customer, THEN THE System SHALL return HTTP 409 with error code `VOUCHER_ALREADY_USED`.
4. IF a Voucher's total usage count has reached its defined maximum, THEN THE System SHALL return HTTP 409 with error code `VOUCHER_EXHAUSTED`.
5. WHEN processing a reservation that includes a Voucher, THE System SHALL acquire a DistributedLock scoped to the combination of `userId` and `voucherId` before validating and incrementing usage, to prevent concurrent duplicate applications by the same user.
6. IF a Booking is cancelled or expired after a Voucher was applied, THEN THE System SHALL decrement the Voucher's usage count to restore availability.
7. WHERE a Voucher specifies a minimum booking amount, THE System SHALL apply the discount only when the pre-discount booking total meets or exceeds that minimum amount.
8. IF a `voucherCode` is supplied but no Voucher with that code exists in the database, THEN THE System SHALL return HTTP 404 with error code `VOUCHER_NOT_FOUND`.
9. IF a `voucherCode` is supplied but the VoucherCampaign's current date is outside [start_date, end_date], THEN THE System SHALL return HTTP 422 with error code `VOUCHER_CAMPAIGN_INACTIVE`.
10. IF the pre-discount booking total does not meet the Voucher's minimum booking amount, THEN THE System SHALL return HTTP 422 with error code `VOUCHER_MINIMUM_NOT_MET`.

---

### Requirement 5: Booking Status Tracking

**User Story:** As a Customer, I want to view the current status and details of my bookings, so that I can track whether my reservation is confirmed or pending action.

#### Acceptance Criteria

1. WHEN a Customer requests `GET /api/v1/bookings/{id}`, THE System SHALL return the Booking's `state`, all BookingItems with quantities and prices, applied Voucher code (if any), total amount, and — only when state is `PENDING` or `AWAITING_PAYMENT` — the payment deadline.
2. IF a Customer requests a Booking that belongs to a different user, THEN THE System SHALL return HTTP 403 with error code `FORBIDDEN`.
3. WHEN a Customer requests `GET /api/v1/bookings`, THE System SHALL return a paginated list (default 20, max 100 per page) of all Bookings belonging to the authenticated Customer, ordered by creation date descending, with each item containing: Booking ID, state, concert name, total amount, and creation timestamp.
4. THE System SHALL represent BookingState using the canonical values: `PENDING`, `AWAITING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.
5. IF a Customer requests a Booking ID that does not exist, THEN THE System SHALL return HTTP 404 with error code `BOOKING_NOT_FOUND`.

---

### Requirement 6: Operation Dashboard — Booking Management

**User Story:** As an Operator, I want to view and manage all customer bookings, so that I can monitor platform activity and intervene when necessary.

#### Acceptance Criteria

1. WHEN an Operator requests `GET /api/v1/admin/bookings`, THE System SHALL return a paginated list (default 20 items per page, maximum 100 per page) of all Bookings with customer ID, concert name, total amount, current state, and creation timestamp.
2. WHEN an Operator requests the booking list, THE System SHALL support filtering by `state`, `concertId`, and `createdFrom`/`createdTo` date range parameters (applied to creation timestamp).
3. WHEN an Operator submits `PATCH /api/v1/admin/bookings/{id}/state` with a valid target state, THE System SHALL validate that the transition is permitted by the BookingState machine, apply the update, and return the updated Booking state.
4. IF an Operator requests an invalid state transition (e.g., `CONFIRMED` → `PENDING`), THEN THE System SHALL return HTTP 422 with error code `INVALID_STATE_TRANSITION` and list the valid transitions from the current state.
5. WHEN an Operator manually cancels a `CONFIRMED` or `AWAITING_PAYMENT` Booking, THE System SHALL restore the ticket quantity to the Redis-backed inventory and, if a Voucher was applied, decrement the Voucher's usage count.
6. IF an Operator submits a state-change request for a Booking ID that does not exist, THEN THE System SHALL return HTTP 404 with error code `BOOKING_NOT_FOUND`.

---

### Requirement 7: Operation Dashboard — Concert and Ticket Management

**User Story:** As an Operator, I want to create and publish concerts with ticket categories, so that customers can browse and book them.

#### Acceptance Criteria

1. WHEN an Operator submits `POST /api/v1/admin/concerts` with valid concert data (name, venue, date, and at least one TicketCategory with name, price, and quantity), THE System SHALL persist the Concert and all provided TicketCategories in a single atomic operation and return the created Concert with its ID.
2. IF an Operator submits concert creation data with missing required fields or invalid values (e.g., negative price, zero quantity), THEN THE System SHALL return HTTP 400 with error code `INVALID_CONCERT_DATA` and a list of field-level validation errors.
3. WHEN an Operator submits `POST /api/v1/admin/concerts/{id}/publish` for an unpublished Concert, THE System SHALL mark the Concert as published and load its TicketCategory inventory counts into Redis cache.
4. IF an Operator attempts to publish a Concert that is already published, THEN THE System SHALL return HTTP 422 with error code `CONCERT_ALREADY_PUBLISHED`.
5. IF an Operator attempts to publish a Concert that has no TicketCategories defined, THEN THE System SHALL return HTTP 422 with error code `NO_TICKET_CATEGORIES`.
6. WHEN an Operator requests `GET /api/v1/admin/concerts/{id}/inventory`, THE System SHALL return the total quantity, sold count (Bookings in `CONFIRMED` or `AWAITING_PAYMENT` state), and remaining quantity for each TicketCategory.
7. WHEN an Operator submits `PATCH /api/v1/admin/ticket-categories/{id}/quantity` with an updated quantity, THE System SHALL reject the update if the new quantity is less than the count of Bookings in `CONFIRMED` or `AWAITING_PAYMENT` state for that category, returning HTTP 422 with error code `QUANTITY_BELOW_SOLD`; if accepted, THE System SHALL update the Redis inventory count to reflect the new quantity.

---

### Requirement 8: Voucher Campaign Management

**User Story:** As an Operator, I want to create and manage voucher campaigns, so that I can run promotional discounts for concerts.

#### Acceptance Criteria

1. WHEN an Operator submits `POST /api/v1/admin/voucher-campaigns` with campaign data, THE System SHALL persist the VoucherCampaign with its start date, end date, discount value (percentage 1–100 or fixed amount > 0), discount type (percentage or fixed), and maximum usage count (1–1,000,000).
2. WHEN an Operator submits `POST /api/v1/admin/voucher-campaigns/{id}/vouchers` with a batch size between 1 and 10,000 inclusive, THE System SHALL generate that number of globally unique alphanumeric Voucher codes (8–16 characters) and associate them with the campaign.
3. IF an Operator submits a voucher generation request with a batch size less than 1 or greater than 10,000, THEN THE System SHALL return HTTP 400 with error code `INVALID_BATCH_SIZE`.
4. IF an Operator submits a voucher generation or stats request for a campaign ID that does not exist, THEN THE System SHALL return HTTP 404 with error code `CAMPAIGN_NOT_FOUND`.
5. WHEN an Operator requests `GET /api/v1/admin/voucher-campaigns/{id}/stats`, THE System SHALL return total vouchers issued, total used, and remaining available count.
6. IF an Operator attempts to create a VoucherCampaign with an end date before its start date, THEN THE System SHALL return HTTP 400 with error code `INVALID_CAMPAIGN_DATES`.

---

### Requirement 9: Flash Sale Concurrency and Integrity

**User Story:** As a System architect, I want the booking system to remain correct and stable under high concurrency, so that no tickets are oversold and no resource is double-allocated.

#### Acceptance Criteria

1. THE System SHALL ensure that the total count of non-cancelled, non-expired BookingItems for any TicketCategory never exceeds that TicketCategory's defined quantity at any point in time.
2. WHEN two or more concurrent reservation requests target the same TicketCategory and only one unit remains, THE System SHALL successfully reserve (create a Booking in `PENDING` state) for exactly one request and reject all others with HTTP 409 `TICKET_SOLD_OUT`.
3. THE System SHALL process each unique `Idempotency-Key` at most once for successful requests; repeated requests with the same key after a successful processing SHALL receive the cached response. Keys from failed attempts may be retried.
4. WHEN acquiring a DistributedLock, THE System SHALL apply a maximum wait time of 3 seconds and a lock lease time of 10 seconds to prevent indefinite blocking.
5. IF a DistributedLock cannot be acquired within the maximum wait time, THEN THE System SHALL return HTTP 503 with error code `SERVICE_BUSY` and include a `Retry-After` header with a value of 2 seconds.
6. WHEN a Concert is published, THE System SHALL initialize each TicketCategory's inventory count in Redis as the authoritative source for availability checks during the sale.
7. WHILE a Concert's sale is active, THE System SHALL use Redis inventory counts as the authoritative source for all ticket availability checks, bypassing direct database queries for this purpose.
8. WHEN a Booking expires or is cancelled, THE System SHALL restore the ticket count in Redis atomically to prevent race conditions during the restoration.
9. IF Redis is unavailable when a Booking expiry or cancellation restoration is attempted, THE System SHALL log the failure and schedule a reconciliation task to re-sync Redis inventory from the database.

---

### Requirement 10: Data Seeding

**User Story:** As a developer or reviewer, I want representative demo data to be loaded automatically on startup, so that I can test the full booking flow without manual setup.

#### Acceptance Criteria

1. WHEN the System starts and the database contains no Concert records, THE Seeder SHALL insert at least 2 published Concerts, each with at least 2 TicketCategories (including one VIP tier priced above 500,000 VND with quantity ≥ 50 and one Standard tier priced below 500,000 VND with quantity ≥ 100), and load their inventory into Redis cache.
2. WHEN the System starts and the database contains no VoucherCampaign records, THE Seeder SHALL insert at least 1 VoucherCampaign whose start_date ≤ current date ≤ end_date, with at least 5 pre-generated Voucher codes and a maximum usage count of at least 5.
3. THE Seeder SHALL be idempotent: the Seeder SHALL use a unique name or identifier per seed record to check existence before inserting, producing no duplicate records and leaving existing data unchanged on re-run.
4. WHEN the System starts with `FLASH_SALE_MODE=true` environment variable set, THE Seeder SHALL insert one additional published Concert with a TicketCategory of quantity exactly 100 and load its inventory into Redis cache, intended for concurrency testing.

---

### Requirement 11: API Response Standards

**User Story:** As an API consumer, I want all API responses to follow a consistent structure, so that I can handle success and error cases uniformly.

#### Acceptance Criteria

1. THE System SHALL wrap all successful responses in a standard envelope: `{ "success": true, "data": <payload>, "timestamp": "<UTC ISO-8601>" }`.
2. THE System SHALL wrap all error responses in a standard envelope: `{ "success": false, "error": { "code": "<string>", "message": "<string>" }, "timestamp": "<UTC ISO-8601>" }`.
3. THE System SHALL return HTTP 400 for malformed request payloads or missing required fields, with the error envelope's `error` object containing a `fields` array of objects each with `field` (field name) and `reason` (human-readable explanation).
4. THE System SHALL return HTTP 500 for unhandled internal errors using the standard error envelope, without exposing stack traces or internal implementation details in the response body.
5. WHEN the System receives a request exceeding the rate limit threshold of 200 requests per minute per user, THE System SHALL return HTTP 429 with a `Retry-After` header set to 60 seconds.
