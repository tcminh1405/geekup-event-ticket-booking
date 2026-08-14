package com.geekup.ticketbooking.property;

import com.geekup.ticketbooking.booking.state.BookingState;
import net.jqwik.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test P1 — Booking State Machine Validity
 *
 * For any state S and target T:
 *   - canTransitionTo(T) returns true  iff T ∈ VALID_TRANSITIONS[S]
 *   - canTransitionTo(T) returns false iff T ∉ VALID_TRANSITIONS[S]
 *
 * Validates: Requirements 3.4, 6.3, 6.4
 */
@net.jqwik.api.Tag("booking-state-machine")
class BookingStateMachinePropertyTest {

    // -----------------------------------------------------------------
    // Arbitraries
    // -----------------------------------------------------------------

    /** Generates any BookingState value. */
    @Provide
    Arbitrary<BookingState> anyState() {
        return Arbitraries.of(BookingState.values());
    }

    // -----------------------------------------------------------------
    // Property 1a: valid transitions are accepted
    //
    // For every (S, T) pair where T ∈ validNextStates(S),
    // canTransitionTo(T) must return true.
    // -----------------------------------------------------------------

    /**
     * **Validates: Requirements 3.4, 6.3, 6.4**
     *
     * For every source state S and every T that is a valid next state,
     * BookingState.canTransitionTo(T) must return true.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("booking-state-machine")
    void validTransitionsAreAccepted(@ForAll("anyState") BookingState source) {
        Set<BookingState> validTargets = source.validNextStates();

        for (BookingState target : validTargets) {
            assertThat(source.canTransitionTo(target))
                    .as("Expected %s → %s to be a valid transition", source, target)
                    .isTrue();
        }
    }

    // -----------------------------------------------------------------
    // Property 1b: invalid transitions are rejected
    //
    // For every (S, T) pair where T ∉ validNextStates(S),
    // canTransitionTo(T) must return false.
    // -----------------------------------------------------------------

    /**
     * **Validates: Requirements 3.4, 6.3, 6.4**
     *
     * For every source state S and every T that is NOT in the valid
     * next-state set, BookingState.canTransitionTo(T) must return false,
     * signalling that the transition would be rejected with
     * INVALID_STATE_TRANSITION.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("booking-state-machine")
    void invalidTransitionsAreRejected(
            @ForAll("anyState") BookingState source,
            @ForAll("anyState") BookingState target) {

        Set<BookingState> validTargets = source.validNextStates();
        boolean expectedAccepted = validTargets.contains(target);

        assertThat(source.canTransitionTo(target))
                .as("canTransitionTo(%s → %s): expected %b (validNextStates=%s)",
                        source, target, expectedAccepted, validTargets)
                .isEqualTo(expectedAccepted);
    }

    // -----------------------------------------------------------------
    // Property 1c: terminal states have no valid transitions
    //
    // CANCELLED and EXPIRED are terminal — their valid-next-state sets
    // must be empty, so every attempted transition is rejected.
    // -----------------------------------------------------------------

    /**
     * **Validates: Requirements 6.3, 6.4**
     *
     * CANCELLED and EXPIRED are terminal states: no further state
     * transition is permitted from them.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("booking-state-machine")
    void terminalStatesHaveNoValidTransitions(@ForAll("anyState") BookingState target) {
        assertThat(BookingState.CANCELLED.canTransitionTo(target))
                .as("CANCELLED should not be able to transition to %s", target)
                .isFalse();

        assertThat(BookingState.EXPIRED.canTransitionTo(target))
                .as("EXPIRED should not be able to transition to %s", target)
                .isFalse();
    }

    // -----------------------------------------------------------------
    // Property 1d: validNextStates is consistent with canTransitionTo
    //
    // For every (S, T): T ∈ validNextStates(S) ↔ canTransitionTo(T)
    // The two methods must never disagree.
    // -----------------------------------------------------------------

    /**
     * **Validates: Requirements 6.3, 6.4**
     *
     * The validNextStates() set and canTransitionTo() method must always
     * agree: membership in the set is equivalent to canTransitionTo()
     * returning true.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("booking-state-machine")
    void validNextStatesIsConsistentWithCanTransitionTo(
            @ForAll("anyState") BookingState source,
            @ForAll("anyState") BookingState target) {

        boolean inSet = source.validNextStates().contains(target);
        boolean canTransition = source.canTransitionTo(target);

        assertThat(canTransition)
                .as("validNextStates() and canTransitionTo() disagree for %s → %s", source, target)
                .isEqualTo(inSet);
    }
}
