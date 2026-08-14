package com.geekup.ticketbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/bookings/{id}/pay}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment request for a pending booking")
public class PaymentRequest {

    @NotBlank(message = "paymentMethod is required")
    @Schema(description = "Payment method identifier (currently only 'MOCK' is supported)", example = "MOCK",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String paymentMethod;
}
