package com.geekup.ticketbooking.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for generating a batch of vouchers for a campaign.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request to generate a batch of voucher codes for a campaign")
public class GenerateVouchersRequest {

    @Min(value = 1, message = "Count must be at least 1")
    @Max(value = 10000, message = "Count must not exceed 10,000")
    @Schema(description = "Number of voucher codes to generate", example = "100")
    private int count;
}
