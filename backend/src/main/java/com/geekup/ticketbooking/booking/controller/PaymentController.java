package com.geekup.ticketbooking.booking.controller;

import com.geekup.ticketbooking.booking.dto.BookingDetailResponse;
import com.geekup.ticketbooking.booking.dto.PaymentRequest;
import com.geekup.ticketbooking.booking.service.BookingService;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Payment controller — processes payment for a PENDING booking.
 *
 * <p>Endpoint: {@code POST /api/v1/bookings/{id}/pay}</p>
 */
@Tag(name = "Payments", description = "Submit payment for a pending booking")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class PaymentController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final BookingService bookingService;

    /**
     * Submit payment for a PENDING booking.
     *
     * <p>Behavior depends on {@code payment.gateway.behavior} configuration:
     * <ul>
     *   <li>SUCCESS  → booking transitions to CONFIRMED (HTTP 200)</li>
     *   <li>FAILED   → booking transitions to CANCELLED; inventory + voucher restored (HTTP 402)</li>
     *   <li>TIMEOUT  → booking remains PENDING (HTTP 504)</li>
     * </ul>
     * </p>
     *
     * <p>Requirements: 3.1–3.8</p>
     */
    @Operation(
            summary = "Pay for a booking",
            description = "Submits payment for a PENDING booking. "
                    + "On SUCCESS, the booking is CONFIRMED. "
                    + "On FAILED, the booking is CANCELLED and inventory/voucher are restored. "
                    + "On TIMEOUT, the booking remains PENDING and can be retried."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment successful — booking confirmed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "402", description = "Payment declined"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not in PENDING state"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "Payment gateway timeout — booking remains PENDING")
    })
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<BookingDetailResponse>> pay(
            @Parameter(description = "Authenticated user ID", required = true)
            @RequestHeader(USER_ID_HEADER) Long userId,

            @Parameter(description = "Booking ID to pay for", required = true)
            @PathVariable Long id,

            @Valid @RequestBody PaymentRequest request) {

        BookingDetailResponse response = bookingService.pay(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
