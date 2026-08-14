package com.geekup.ticketbooking.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for a voucher campaign (admin view).
 * Avoids exposing the JPA entity directly over the wire.
 * Requirements: 8.1, 8.6
 */
@Getter
@Builder
@Schema(description = "Admin view of a voucher campaign")
public class VoucherCampaignResponse {

    @Schema(description = "Campaign ID", example = "1")
    private Long id;

    @Schema(description = "Campaign name", example = "Summer Sale 2024")
    private String name;

    @Schema(description = "Discount type: PERCENTAGE or FIXED", example = "PERCENTAGE")
    private String discountType;

    @Schema(description = "Discount value (percentage 1–100 or fixed amount)", example = "10.00")
    private BigDecimal discountValue;

    @Schema(description = "Minimum booking amount required to use this voucher", example = "500000.00")
    private BigDecimal minBookingAmount;

    @Schema(description = "Maximum total times vouchers in this campaign can be used", example = "100")
    private int maxUsageCount;

    @Schema(description = "Campaign start date (inclusive)", example = "2024-06-01")
    private LocalDate startDate;

    @Schema(description = "Campaign end date (inclusive)", example = "2024-06-30")
    private LocalDate endDate;

    @Schema(description = "Timestamp when this campaign was created")
    private LocalDateTime createdAt;
}
