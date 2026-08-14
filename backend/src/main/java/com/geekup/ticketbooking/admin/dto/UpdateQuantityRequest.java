package com.geekup.ticketbooking.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for updating a ticket category's total quantity.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request to update the total quantity of a ticket category")
public class UpdateQuantityRequest {

    @Min(value = 1, message = "New quantity must be at least 1")
    @Schema(description = "New total quantity for the ticket category", example = "300")
    private int newQuantity;
}
