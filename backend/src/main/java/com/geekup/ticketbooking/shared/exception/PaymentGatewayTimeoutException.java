package com.geekup.ticketbooking.shared.exception;

/**
 * Thrown when the payment gateway does not respond within the configured timeout.
 * Maps to HTTP 504 in the {@link GlobalExceptionHandler}.
 */
public class PaymentGatewayTimeoutException extends ApplicationException {

    public PaymentGatewayTimeoutException(String message) {
        super("PAYMENT_GATEWAY_TIMEOUT", message);
    }
}
