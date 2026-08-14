package com.geekup.ticketbooking.shared.exception;

/** Thrown when an operator requests a state transition not permitted by the booking state machine. → 422 INVALID_STATE_TRANSITION */
public class InvalidStateTransitionException extends ValidationException {

    public InvalidStateTransitionException(String fromState, String toState) {
        super("INVALID_STATE_TRANSITION",
                "Transition from '" + fromState + "' to '" + toState + "' is not permitted.");
    }
}
