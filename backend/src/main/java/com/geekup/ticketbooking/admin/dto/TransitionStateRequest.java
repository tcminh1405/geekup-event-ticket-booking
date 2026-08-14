package com.geekup.ticketbooking.admin.dto;

import com.geekup.ticketbooking.booking.state.BookingState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for manually transitioning a booking's state (operator action).
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request to transition a booking to a new state")
public class TransitionStateRequest {

    @NotNull(message = "Target state is required")
    @Schema(description = "The target state to transition the booking to", example = "CANCELLED")
    private BookingState targetState;
}
