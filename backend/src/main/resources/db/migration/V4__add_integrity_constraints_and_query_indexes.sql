-- Enforce invariants documented for inventory and booking state.
ALTER TABLE ticket_categories
    ADD CONSTRAINT chk_ticket_category_quantity_bounds
        CHECK (total_quantity >= 0 AND available_quantity >= 0
            AND available_quantity <= total_quantity AND sold_quantity >= 0),
    ADD CONSTRAINT chk_ticket_category_price_positive CHECK (price > 0);

ALTER TABLE booking_items
    ADD CONSTRAINT chk_booking_item_quantity CHECK (quantity BETWEEN 1 AND 10),
    ADD CONSTRAINT chk_booking_item_amounts CHECK (unit_price > 0 AND subtotal >= 0);

ALTER TABLE bookings
    ADD CONSTRAINT chk_booking_state
        CHECK (state IN ('PENDING', 'AWAITING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));

CREATE INDEX idx_concerts_published_concert_date ON concerts(published, concert_date);
CREATE INDEX idx_bookings_user_created_at ON bookings(user_id, created_at DESC);
CREATE INDEX idx_bookings_expiry_state_deadline ON bookings(state, payment_deadline);
CREATE INDEX idx_bookings_admin_filter ON bookings(state, concert_id, created_at);
