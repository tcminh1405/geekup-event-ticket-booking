package com.geekup.ticketbooking.shared.exception;

/** Thrown when an action is attempted on a booking that is not in the required state. → 409 INVALID_BOOKING_STATE */
public class InvalidBookingStateException extends ConflictException {

    public InvalidBookingStateException(String currentState) {
        super("INVALID_BOOKING_STATE",
                "The booking is in state '" + currentState + "' which does not allow this operation.");
    }
}
