package com.geekup.ticketbooking.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Statistics for a voucher campaign.
 */
@Getter
@Builder
@Schema(description = "Usage statistics for a voucher campaign")
public class CampaignStatsResponse {

    @Schema(description = "Campaign ID", example = "1")
    private Long campaignId;

    @Schema(description = "Campaign name", example = "Summer Sale 2024")
    private String campaignName;

    @Schema(description = "Total voucher codes issued for this campaign", example = "500")
    private long totalIssued;

    @Schema(description = "Total vouchers that have been used", example = "120")
    private long totalUsed;

    @Schema(description = "Remaining unused vouchers", example = "380")
    private long remaining;
}
