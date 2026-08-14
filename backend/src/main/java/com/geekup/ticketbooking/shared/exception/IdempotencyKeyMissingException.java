package com.geekup.ticketbooking.shared.exception;

/** Thrown when the Idempotency-Key header is absent on a reservation request. → 400 MISSING_IDEMPOTENCY_KEY */
public class IdempotencyKeyMissingException extends ApplicationException {

    public IdempotencyKeyMissingException() {
        super("MISSING_IDEMPOTENCY_KEY", "The Idempotency-Key header is required for this request.");
    }
}
