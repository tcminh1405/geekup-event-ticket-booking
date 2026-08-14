package com.geekup.ticketbooking.admin.dto;

import com.geekup.ticketbooking.booking.state.BookingState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Filter parameters for the admin booking list endpoint.
 * All fields are optional; omitted fields mean "no filter applied".
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Optional filters for listing bookings in the admin dashboard")
public class AdminBookingFilter {

    @Schema(description = "Filter by booking state", example = "CONFIRMED")
    private BookingState state;

    @Schema(description = "Filter by concert ID", example = "1")
    private Long concertId;

    @Schema(description = "Filter bookings flagged for operator review", example = "true")
    private Boolean suspicious;

    @Schema(description = "Filter bookings created on or after this timestamp", example = "2024-01-01T00:00:00")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;

    @Schema(description = "Filter bookings created on or before this timestamp", example = "2024-12-31T23:59:59")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;
}
