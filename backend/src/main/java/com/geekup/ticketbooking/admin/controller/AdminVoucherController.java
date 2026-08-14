package com.geekup.ticketbooking.admin.controller;

import com.geekup.ticketbooking.admin.dto.CampaignStatsResponse;
import com.geekup.ticketbooking.admin.dto.CreateVoucherCampaignRequest;
import com.geekup.ticketbooking.admin.dto.GenerateVouchersRequest;
import com.geekup.ticketbooking.admin.dto.VoucherCampaignResponse;
import com.geekup.ticketbooking.admin.service.AdminService;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints for voucher campaign management.
 * Requirements: 8.1–8.6
 */
@RestController
@RequestMapping("/api/v1/admin/voucher-campaigns")
@Tag(name = "Admin - Voucher", description = "Operator endpoints for voucher campaign management")
@RequiredArgsConstructor
public class AdminVoucherController {

    private final AdminService adminService;

    /**
     * Create a new voucher campaign.
     * POST /api/v1/admin/voucher-campaigns → 201
     */
    @Operation(summary = "Create a voucher campaign", description = "Persists a new VoucherCampaign with discount rules and date range. Requirements 8.1, 8.6")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VoucherCampaignResponse> createCampaign(
            @Valid @RequestBody CreateVoucherCampaignRequest request) {
        return ApiResponse.success(adminService.createVoucherCampaign(request));
    }

    /**
     * Generate a batch of voucher codes for a campaign.
     * POST /api/v1/admin/voucher-campaigns/{id}/vouchers → 201
     */
    @Operation(summary = "Generate voucher codes", description = "Generates a batch of unique alphanumeric voucher codes. Requirements 8.2–8.4")
    @PostMapping("/{id}/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> generateVouchers(
            @PathVariable Long id,
            @Valid @RequestBody GenerateVouchersRequest request) {
        return ApiResponse.success(adminService.generateVouchers(id, request));
    }

    /**
     * Get statistics for a voucher campaign.
     * GET /api/v1/admin/voucher-campaigns/{id}/stats → 200
     */
    @Operation(summary = "Get campaign stats", description = "Returns total issued, used, and remaining vouchers for a campaign. Requirement 8.5")
    @GetMapping("/{id}/stats")
    public ApiResponse<CampaignStatsResponse> getCampaignStats(@PathVariable Long id) {
        return ApiResponse.success(adminService.getCampaignStats(id));
    }
}
