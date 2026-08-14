package com.geekup.ticketbooking.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for creating a new voucher campaign.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request to create a new voucher campaign")
public class CreateVoucherCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    @Schema(description = "Campaign name", example = "Summer Sale 2024")
    private String name;

    @NotBlank(message = "Discount type is required")
    @Pattern(regexp = "PERCENTAGE|FIXED", message = "Discount type must be PERCENTAGE or FIXED")
    @Schema(description = "Discount type: PERCENTAGE or FIXED", example = "PERCENTAGE")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be at least 0.01")
    @Schema(description = "Discount value (percentage 1–100 or fixed amount > 0)", example = "10.00")
    private BigDecimal discountValue;

    @DecimalMin(value = "0", message = "Minimum booking amount cannot be negative")
    @Schema(description = "Minimum booking amount required to use this voucher (default 0)", example = "500000.00")
    private BigDecimal minBookingAmount;

    @NotNull(message = "Max usage count is required")
    @Min(value = 1, message = "Max usage count must be at least 1")
    @Max(value = 1000000, message = "Max usage count must not exceed 1,000,000")
    @Schema(description = "Maximum total number of times this campaign's vouchers can be used", example = "100")
    private Integer maxUsageCount;

    @NotNull(message = "Start date is required")
    @Schema(description = "Campaign start date (inclusive)", example = "2024-06-01")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Schema(description = "Campaign end date (inclusive)", example = "2024-06-30")
    private LocalDate endDate;
}
