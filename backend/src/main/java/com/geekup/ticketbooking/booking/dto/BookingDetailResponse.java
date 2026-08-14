package com.geekup.ticketbooking.booking.dto;

import com.geekup.ticketbooking.booking.state.BookingState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed response for a booking including all line items.
 */
@Getter
@Builder
@Schema(description = "Detailed view of a booking including all items")
public class BookingDetailResponse {

    @Schema(description = "Booking ID", example = "42")
    private Long bookingId;

    @Schema(description = "Concert ID", example = "1")
    private Long concertId;

    @Schema(description = "Concert name", example = "Rock Night 2024")
    private String concertName;

    @Schema(description = "Current booking state", example = "CONFIRMED")
    private BookingState state;

    @Schema(description = "List of ticket items in this booking")
    private List<BookingItemResponse> items;

    @Schema(description = "Total booking amount after any discounts", example = "1350000.00")
    private BigDecimal totalAmount;

    @Schema(description = "Discount amount applied (null if no voucher used)", example = "150000.00")
    private BigDecimal discountAmount;

    @Schema(description = "Voucher code used (null if no voucher applied)", example = "PROMO2024")
    private String voucherCode;

    @Schema(description = "Idempotency key used during reservation", example = "a1b2c3d4-e5f6-...")
    private String idempotencyKey;

    @Schema(description = "Deadline by which payment must be made", example = "2024-01-15T10:45:00")
    private LocalDateTime paymentDeadline;

    @Schema(description = "Timestamp when payment was confirmed (null if not yet paid)", example = "2024-01-15T10:31:00")
    private LocalDateTime paymentTimestamp;

    @Schema(description = "Booking creation timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Booking last updated timestamp", example = "2024-01-15T10:31:00")
    private LocalDateTime updatedAt;
}
