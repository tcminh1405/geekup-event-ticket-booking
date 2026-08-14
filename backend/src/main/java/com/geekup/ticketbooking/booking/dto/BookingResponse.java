package com.geekup.ticketbooking.booking.dto;

import com.geekup.ticketbooking.booking.state.BookingState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Summary response for a booking (used in listing and reservation responses).
 * For full detail (including items), see {@link BookingDetailResponse}.
 */
@Getter
@Builder
@Schema(description = "Summary response for a booking")
public class BookingResponse {

    @Schema(description = "Booking ID", example = "42")
    private Long bookingId;

    @Schema(description = "Concert ID", example = "1")
    private Long concertId;

    @Schema(description = "Concert name", example = "Rock Night 2024")
    private String concertName;

    @Schema(description = "Current booking state", example = "PENDING")
    private BookingState state;

    @Schema(description = "Total booking amount after any discounts", example = "1350000.00")
    private BigDecimal totalAmount;

    @Schema(description = "Discount amount applied (null if no voucher used)", example = "150000.00")
    private BigDecimal discountAmount;

    @Schema(description = "Deadline by which payment must be made", example = "2024-01-15T10:45:00")
    private LocalDateTime paymentDeadline;

    @Schema(description = "Timestamp when payment was confirmed (null if not yet paid)", example = "2024-01-15T10:31:00")
    private LocalDateTime paymentTimestamp;

    @Schema(description = "Booking creation timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;
}
