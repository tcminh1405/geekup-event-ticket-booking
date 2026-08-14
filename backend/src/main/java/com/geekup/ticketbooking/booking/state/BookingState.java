package com.geekup.ticketbooking.booking.state;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public enum BookingState {
    PENDING, AWAITING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED;

    private static final Map<BookingState, Set<BookingState>> VALID_TRANSITIONS = Map.of(
            PENDING,          Set.of(AWAITING_PAYMENT, EXPIRED, CANCELLED),
            AWAITING_PAYMENT, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED,        Set.of(CANCELLED),
            CANCELLED,        Collections.emptySet(),
            EXPIRED,          Collections.emptySet()
    );

    public boolean canTransitionTo(BookingState target) {
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public Set<BookingState> validNextStates() {
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }
}
