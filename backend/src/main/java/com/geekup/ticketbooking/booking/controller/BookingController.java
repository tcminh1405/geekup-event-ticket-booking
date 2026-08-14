package com.geekup.ticketbooking.booking.controller;

import com.geekup.ticketbooking.booking.dto.BookingDetailResponse;
import com.geekup.ticketbooking.booking.dto.BookingResponse;
import com.geekup.ticketbooking.booking.dto.ReserveBookingRequest;
import com.geekup.ticketbooking.booking.service.BookingService;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Customer-facing controller for booking management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/bookings/reserve}  — reserve tickets</li>
 *   <li>{@code GET  /api/v1/bookings}           — list own bookings</li>
 *   <li>{@code GET  /api/v1/bookings/{id}}      — get booking detail</li>
 * </ul>
 * </p>
 */
@Tag(name = "Bookings", description = "Reserve tickets and manage bookings")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final int    MAX_PAGE_SIZE     = 100;
    private static final String USER_ID_HEADER    = "X-User-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final BookingService bookingService;

    /**
     * Reserve tickets for a concert.
     *
     * <p>Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 9.1, 9.2</p>
     */
    @Operation(
            summary = "Reserve concert tickets",
            description = "Atomically reserves tickets for a published concert. "
                    + "Requires 'X-User-Id' header. "
                    + "Optionally accepts an 'Idempotency-Key' header to prevent duplicate bookings. "
                    + "Returns 409 if tickets are sold out."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Booking created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Concert or ticket category not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Ticket sold out"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Concert not published")
    })
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<BookingResponse>> reserve(
            @Parameter(description = "Authenticated user ID", required = true)
            @RequestHeader(USER_ID_HEADER) Long userId,

            @Parameter(description = "Idempotency key to prevent duplicate bookings (recommended: UUID)")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,

            @Valid @RequestBody ReserveBookingRequest request) {

        BookingResponse response = bookingService.reserve(userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * List all bookings for the authenticated user, ordered by creation date descending.
     *
     * <p>Requirements: 2.6</p>
     */
    @Operation(
            summary = "List my bookings",
            description = "Returns a paginated list of bookings for the authenticated user, "
                    + "ordered by creation date (newest first)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated list of bookings")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> listBookings(
            @Parameter(description = "Authenticated user ID", required = true)
            @RequestHeader(USER_ID_HEADER) Long userId,

            @Parameter(description = "Zero-based page index (default: 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Items per page (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by("createdAt").descending());

        Page<BookingResponse> result = bookingService.listBookings(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Get the full detail for a specific booking.
     *
     * <p>Requirements: 2.6</p>
     */
    @Operation(
            summary = "Get booking detail",
            description = "Returns full detail of a booking including all line items. "
                    + "Returns 403 if the booking belongs to another user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking detail"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDetailResponse>> getBookingDetail(
            @Parameter(description = "Authenticated user ID", required = true)
            @RequestHeader(USER_ID_HEADER) Long userId,

            @Parameter(description = "Booking ID", required = true)
            @PathVariable Long id) {

        BookingDetailResponse response = bookingService.getBookingDetail(userId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
