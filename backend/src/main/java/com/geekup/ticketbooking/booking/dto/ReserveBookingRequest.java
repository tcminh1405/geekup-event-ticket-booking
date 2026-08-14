package com.geekup.ticketbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/bookings/reserve}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reserve tickets for a concert")
public class ReserveBookingRequest {

    @NotNull(message = "concertId is required")
    @Schema(description = "ID of the concert to book tickets for", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long concertId;

    @NotEmpty(message = "items must not be empty")
    @Valid
    @Schema(description = "List of ticket categories and quantities to reserve", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<BookingItemRequest> items;

    @Schema(description = "Optional voucher code for a discount", example = "PROMO2024")
    private String voucherCode;
}
