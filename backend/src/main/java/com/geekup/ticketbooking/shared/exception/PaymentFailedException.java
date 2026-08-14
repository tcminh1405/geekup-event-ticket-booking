package com.geekup.ticketbooking.shared.exception;

/** Thrown when the payment gateway returns a FAILED response. → 402 PAYMENT_FAILED */
public class PaymentFailedException extends ApplicationException {

    public PaymentFailedException(String message) {
        super("PAYMENT_FAILED", message);
    }
}
