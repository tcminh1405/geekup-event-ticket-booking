package com.geekup.ticketbooking.concert.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight concert summary returned in the paginated list endpoint.
 * Contains the concert metadata and the names/prices of its ticket categories.
 */
@Getter
@Builder
public class ConcertSummaryResponse {

    private Long id;
    private String name;
    private String venue;
    private LocalDateTime concertDate;

    /** Ticket category names and prices (no remaining-quantity needed for list view). */
    private List<TicketCategorySummary> ticketCategories;

    @Getter
    @Builder
    public static class TicketCategorySummary {
        private Long id;
        private String name;
        private java.math.BigDecimal price;
    }
}
