package com.geekup.ticketbooking.shared.exception;

/**
 * Base exception for all application-specific exceptions.
 * Carries a machine-readable {@code errorCode} that the {@link GlobalExceptionHandler}
 * uses to build the error response envelope.
 */
public class ApplicationException extends RuntimeException {

    private final String errorCode;

    public ApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApplicationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
