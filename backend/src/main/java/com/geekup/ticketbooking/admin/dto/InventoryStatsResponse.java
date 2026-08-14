package com.geekup.ticketbooking.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response containing per-category inventory statistics for a concert.
 */
@Getter
@Builder
@Schema(description = "Inventory statistics for all ticket categories of a concert")
public class InventoryStatsResponse {

    @Schema(description = "Concert ID", example = "1")
    private Long concertId;

    @Schema(description = "Per-category inventory breakdown")
    private List<TicketCategoryInventory> categories;

    @Getter
    @Builder
    @Schema(description = "Inventory stats for a single ticket category")
    public static class TicketCategoryInventory {

        @Schema(description = "Ticket category ID", example = "2")
        private Long ticketCategoryId;

        @Schema(description = "Ticket category name", example = "VIP")
        private String name;

        @Schema(description = "Total quantity originally allocated", example = "200")
        private int totalQuantity;

        @Schema(description = "Number of tickets in confirmed bookings", example = "120")
        private int soldCount;

        @Schema(description = "Number of tickets temporarily reserved but not yet confirmed", example = "30")
        private int reservedCount;

        @Schema(description = "Remaining available quantity", example = "50")
        private int availableQuantity;
    }
}
