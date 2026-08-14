package com.geekup.ticketbooking.shared.exception;

/**
 * Thrown when a requested resource cannot be found.
 * Maps to HTTP 404 in the {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
