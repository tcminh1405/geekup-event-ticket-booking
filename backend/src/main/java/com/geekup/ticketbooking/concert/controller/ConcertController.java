package com.geekup.ticketbooking.concert.controller;

import com.geekup.ticketbooking.concert.dto.ConcertDetailResponse;
import com.geekup.ticketbooking.concert.dto.ConcertSummaryResponse;
import com.geekup.ticketbooking.concert.service.ConcertService;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Customer-facing REST controller for browsing published concerts.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/concerts}       — paginated list of published concerts</li>
 *   <li>{@code GET /api/v1/concerts/{id}}  — full concert detail (404 if not found or unpublished)</li>
 * </ul>
 */
@Tag(name = "Concerts", description = "Browse published concerts and view ticket category details")
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConcertService concertService;

    /**
     * Returns a paginated list of published concerts.
     * Defaults to page 0, 20 items per page; maximum 100 per page.
     *
     * <p>Requirements: 1.1, 1.2, 1.6</p>
     */
    @Operation(
            summary = "List published concerts",
            description = "Returns a paginated list of concerts that have been published by an operator. "
                    + "Default page size is 20; maximum is 100."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated list of published concerts")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConcertSummaryResponse>>> listConcerts(
            @Parameter(description = "Zero-based page index (default: 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by("concertDate").ascending());

        Page<ConcertSummaryResponse> result = concertService.listPublishedConcerts(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Returns full detail for a single published concert.
     * Returns HTTP 404 if the concert does not exist or is not published.
     *
     * <p>Requirements: 1.3, 1.4, 1.5</p>
     */
    @Operation(
            summary = "Get concert detail",
            description = "Returns the full detail of a published concert including all ticket categories "
                    + "with their remaining quantities (sourced from Redis cache with DB fallback). "
                    + "Returns 404 if the concert is not found or is not yet published."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Concert detail"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Concert not found or not published")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> getConcertDetail(
            @Parameter(description = "Concert ID", required = true)
            @PathVariable Long id) {

        ConcertDetailResponse response = concertService.getConcertDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
