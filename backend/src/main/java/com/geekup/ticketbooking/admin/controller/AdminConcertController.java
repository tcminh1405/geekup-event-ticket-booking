package com.geekup.ticketbooking.admin.controller;

import com.geekup.ticketbooking.admin.dto.CreateConcertRequest;
import com.geekup.ticketbooking.admin.dto.InventoryStatsResponse;
import com.geekup.ticketbooking.admin.dto.UpdateQuantityRequest;
import com.geekup.ticketbooking.admin.service.AdminService;
import com.geekup.ticketbooking.concert.dto.ConcertDetailResponse;
import com.geekup.ticketbooking.concert.dto.TicketCategoryResponse;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Admin endpoints for concert and ticket-category management.
 * Requirements: 7.1–7.7
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Concert", description = "Operator endpoints for concert and inventory management")
@RequiredArgsConstructor
public class AdminConcertController {

    private final AdminService adminService;

    /**
     * Create a new concert with ticket categories.
     * POST /api/v1/admin/concerts → 201
     */
    @Operation(summary = "Create a new concert", description = "Atomically persists a Concert and its TicketCategories. Requirement 7.1")
    @PostMapping("/concerts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConcertDetailResponse> createConcert(
            @Valid @RequestBody CreateConcertRequest request) {
        return ApiResponse.success(adminService.createConcert(request));
    }

    /**
     * Publish a concert and load its inventory into Redis.
     * POST /api/v1/admin/concerts/{id}/publish → 200
     */
    @Operation(summary = "Publish a concert", description = "Marks the concert as published and initialises Redis inventory. Requirements 7.3–7.5")
    @PostMapping("/concerts/{id}/publish")
    public ApiResponse<ConcertDetailResponse> publishConcert(@PathVariable Long id) {
        return ApiResponse.success(adminService.publishConcert(id));
    }

    /**
     * Get inventory statistics for a concert.
     * GET /api/v1/admin/concerts/{id}/inventory → 200
     */
    @Operation(summary = "Get inventory stats", description = "Returns total, sold, and available quantity per ticket category. Requirement 7.6")
    @GetMapping("/concerts/{id}/inventory")
    public ApiResponse<InventoryStatsResponse> getInventoryStats(@PathVariable Long id) {
        return ApiResponse.success(adminService.getInventoryStats(id));
    }

    /**
     * Update the quantity of a ticket category.
     * PATCH /api/v1/admin/ticket-categories/{id}/quantity → 200
     */
    @Operation(summary = "Update ticket category quantity", description = "Updates total and available quantity; rejects if new qty < sold count. Requirement 7.7")
    @PatchMapping("/ticket-categories/{id}/quantity")
    public ApiResponse<TicketCategoryResponse> updateQuantity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuantityRequest request) {
        return ApiResponse.success(adminService.updateTicketCategoryQuantity(id, request));
    }
}
