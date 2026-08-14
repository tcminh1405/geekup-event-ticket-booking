package com.geekup.ticketbooking.shared.exception;

/**
 * Thrown when business-level validation fails (distinct from bean-validation).
 * Maps to HTTP 422 in the {@link GlobalExceptionHandler}.
 */
public class ValidationException extends ApplicationException {

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
