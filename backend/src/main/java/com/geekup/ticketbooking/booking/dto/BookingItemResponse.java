package com.geekup.ticketbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Represents a single booking item in a booking response.
 */
@Getter
@Builder
@Schema(description = "A single line item within a booking")
public class BookingItemResponse {

    @Schema(description = "Booking item ID", example = "101")
    private Long id;

    @Schema(description = "Ticket category ID", example = "2")
    private Long ticketCategoryId;

    @Schema(description = "Ticket category name", example = "VIP")
    private String ticketCategoryName;

    @Schema(description = "Number of tickets", example = "3")
    private int quantity;

    @Schema(description = "Price per ticket", example = "500000.00")
    private BigDecimal unitPrice;

    @Schema(description = "Total price for this line item (quantity × unitPrice)", example = "1500000.00")
    private BigDecimal subtotal;
}
