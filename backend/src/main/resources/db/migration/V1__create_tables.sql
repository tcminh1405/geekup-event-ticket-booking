-- =============================================================================
-- V1__create_tables.sql
-- Concert Ticket Booking Platform — initial schema
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. CONCERTS
-- -----------------------------------------------------------------------------
CREATE TABLE concerts (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    venue        VARCHAR(255) NOT NULL,
    concert_date TIMESTAMP    NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 2. TICKET_CATEGORIES
-- -----------------------------------------------------------------------------
CREATE TABLE ticket_categories (
    id                 BIGSERIAL       PRIMARY KEY,
    concert_id         BIGINT          NOT NULL REFERENCES concerts(id),
    name               VARCHAR(100)    NOT NULL,
    price              NUMERIC(15, 2)  NOT NULL,
    total_quantity     INT             NOT NULL,
    available_quantity INT             NOT NULL,
    sold_quantity      INT             NOT NULL DEFAULT 0,
    created_at         TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 3. VOUCHER_CAMPAIGNS
-- -----------------------------------------------------------------------------
CREATE TABLE voucher_campaigns (
    id                  BIGSERIAL      PRIMARY KEY,
    name                VARCHAR(255)   NOT NULL,
    discount_type       VARCHAR(20)    NOT NULL,  -- 'PERCENTAGE' or 'FIXED'
    discount_value      NUMERIC(15, 2) NOT NULL,
    min_booking_amount  NUMERIC(15, 2) NOT NULL DEFAULT 0,
    max_usage_count     INT            NOT NULL,
    current_usage_count INT            NOT NULL DEFAULT 0,
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 4. VOUCHERS
--    Created before BOOKINGS to satisfy the FK from bookings.voucher_id.
--    The circular FK (used_in_booking_id → bookings) is added via ALTER TABLE
--    after BOOKINGS is created.
-- -----------------------------------------------------------------------------
CREATE TABLE vouchers (
    id                 BIGSERIAL    PRIMARY KEY,
    campaign_id        BIGINT       NOT NULL REFERENCES voucher_campaigns(id),
    code               VARCHAR(16)  NOT NULL UNIQUE,
    used               BOOLEAN      NOT NULL DEFAULT FALSE,
    used_by_user_id    BIGINT,
    used_in_booking_id BIGINT,                    -- FK added below after bookings exists
    used_at            TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 5. BOOKINGS
--    voucher_id is nullable; FK to vouchers added here since vouchers already
--    exists.
-- -----------------------------------------------------------------------------
CREATE TABLE bookings (
    id                BIGSERIAL      PRIMARY KEY,
    user_id           BIGINT         NOT NULL,
    concert_id        BIGINT         NOT NULL REFERENCES concerts(id),
    state             VARCHAR(30)    NOT NULL,
    total_amount      NUMERIC(15, 2),
    discount_amount   NUMERIC(15, 2),
    voucher_id        BIGINT         REFERENCES vouchers(id),
    idempotency_key   VARCHAR(128)   NOT NULL UNIQUE,
    payment_deadline  TIMESTAMP,
    payment_timestamp TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 6. BOOKING_ITEMS
-- -----------------------------------------------------------------------------
CREATE TABLE booking_items (
    id                 BIGSERIAL      PRIMARY KEY,
    booking_id         BIGINT         NOT NULL REFERENCES bookings(id),
    ticket_category_id BIGINT         NOT NULL REFERENCES ticket_categories(id),
    quantity           INT            NOT NULL,
    unit_price         NUMERIC(15, 2) NOT NULL,
    subtotal           NUMERIC(15, 2) NOT NULL
);

-- -----------------------------------------------------------------------------
-- 7. Circular FK: vouchers.used_in_booking_id → bookings(id)
--    Added after bookings table exists to resolve the circular reference.
-- -----------------------------------------------------------------------------
ALTER TABLE vouchers
    ADD CONSTRAINT fk_vouchers_used_in_booking
    FOREIGN KEY (used_in_booking_id) REFERENCES bookings(id);

-- -----------------------------------------------------------------------------
-- 8. Indexes on frequently queried columns
-- -----------------------------------------------------------------------------
CREATE INDEX idx_bookings_user_id         ON bookings(user_id);
CREATE INDEX idx_bookings_state           ON bookings(state);
CREATE INDEX idx_bookings_concert_id      ON bookings(concert_id);
CREATE INDEX idx_bookings_payment_deadline ON bookings(payment_deadline);
CREATE INDEX idx_ticket_categories_concert_id ON ticket_categories(concert_id);
CREATE INDEX idx_vouchers_code            ON vouchers(code);
