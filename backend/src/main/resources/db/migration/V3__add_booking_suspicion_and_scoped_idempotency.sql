-- Operator review metadata and user-scoped idempotency keys.
ALTER TABLE bookings
    ADD COLUMN suspicious BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN suspicion_reason VARCHAR(500);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_idempotency_key_key;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS uk_bookings_idempotency_key;
ALTER TABLE bookings
    ADD CONSTRAINT uq_bookings_user_idempotency_key UNIQUE (user_id, idempotency_key);

CREATE INDEX idx_bookings_suspicious ON bookings(suspicious) WHERE suspicious = TRUE;
