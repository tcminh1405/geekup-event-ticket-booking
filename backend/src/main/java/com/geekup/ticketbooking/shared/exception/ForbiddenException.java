package com.geekup.ticketbooking.shared.exception;

/**
 * Thrown when the authenticated user does not have permission to perform an action.
 * Maps to HTTP 403 in the {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
