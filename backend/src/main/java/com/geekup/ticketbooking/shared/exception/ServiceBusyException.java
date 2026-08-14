package com.geekup.ticketbooking.shared.exception;

/**
 * Thrown when a distributed lock cannot be acquired within the maximum wait time.
 * Maps to HTTP 503 in the {@link GlobalExceptionHandler}.
 */
public class ServiceBusyException extends ApplicationException {

    public ServiceBusyException(String message) {
        super("SERVICE_BUSY", message);
    }
}
