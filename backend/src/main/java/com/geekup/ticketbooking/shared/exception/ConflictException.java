package com.geekup.ticketbooking.shared.exception;

/**
 * Thrown when an operation conflicts with the current state of a resource.
 * Maps to HTTP 409 in the {@link GlobalExceptionHandler}.
 */
public class ConflictException extends ApplicationException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
