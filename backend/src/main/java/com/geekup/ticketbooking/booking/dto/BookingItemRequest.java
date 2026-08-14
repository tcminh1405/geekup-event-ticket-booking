package com.geekup.ticketbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a single line item within a booking reservation request.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single line item specifying a ticket category and quantity to reserve")
public class BookingItemRequest {

    @NotNull(message = "ticketCategoryId is required")
    @Schema(description = "ID of the ticket category to reserve", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ticketCategoryId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 10, message = "quantity must not exceed 10")
    @Schema(description = "Number of tickets to reserve (1–10)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;
}
