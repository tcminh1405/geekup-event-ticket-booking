package com.geekup.ticketbooking.admin.controller;

import com.geekup.ticketbooking.admin.dto.AdminBookingFilter;
import com.geekup.ticketbooking.admin.dto.TransitionStateRequest;
import com.geekup.ticketbooking.admin.service.AdminService;
import com.geekup.ticketbooking.booking.dto.BookingDetailResponse;
import com.geekup.ticketbooking.booking.dto.BookingResponse;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * Admin endpoints for booking management.
 * Requirements: 6.1–6.6
 */
@RestController
@RequestMapping("/api/v1/admin/bookings")
@Tag(name = "Admin - Booking", description = "Operator endpoints for booking management")
@RequiredArgsConstructor
public class AdminBookingController {

    private final AdminService adminService;

    /**
     * List all bookings with optional filters.
     * GET /api/v1/admin/bookings → 200
     */
    @Operation(summary = "List all bookings", description = "Returns a paginated list of all bookings with optional state, concertId, and date-range filters. Requirements 6.1, 6.2")
    @GetMapping
    public ApiResponse<Page<BookingResponse>> listBookings(
            @ModelAttribute AdminBookingFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminService.listBookings(filter, pageable));
    }

    /**
     * Manually transition a booking to a new state.
     * PATCH /api/v1/admin/bookings/{id}/state → 200
     */
    @Operation(summary = "Transition booking state", description = "Validates and applies a state transition. On CANCEL, restores inventory and voucher usage. Requirements 6.3–6.6")
    @PatchMapping("/{id}/state")
    public ApiResponse<BookingDetailResponse> transitionState(
            @PathVariable Long id,
            @Valid @RequestBody TransitionStateRequest request) {
        return ApiResponse.success(adminService.transitionBookingState(id, request.getTargetState()));
    }
}
