package com.geekup.ticketbooking.concert.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * DTO representing a single ticket category within a concert response.
 * Includes the remaining quantity sourced from Redis cache (falls back to DB).
 */
@Getter
@Builder
public class TicketCategoryResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private int totalQuantity;
    private long remainingQuantity;
}
