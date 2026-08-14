package com.geekup.ticketbooking.shared.exception;

/** Thrown when a requested ticket quantity is outside the allowed range [1, 10]. → 422 INVALID_QUANTITY */
public class InvalidQuantityException extends ValidationException {

    public InvalidQuantityException() {
        super("INVALID_QUANTITY", "Quantity must be between 1 and 10 per ticket category.");
    }
}
