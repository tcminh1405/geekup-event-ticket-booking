package com.geekup.ticketbooking.concert.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full concert detail returned for {@code GET /api/v1/concerts/{id}}.
 * Includes all ticket categories with their remaining quantities (Redis-first, DB fallback).
 */
@Getter
@Builder
public class ConcertDetailResponse {

    private Long id;
    private String name;
    private String venue;
    private LocalDateTime concertDate;
    private boolean published;
    private List<TicketCategoryResponse> ticketCategories;
}
