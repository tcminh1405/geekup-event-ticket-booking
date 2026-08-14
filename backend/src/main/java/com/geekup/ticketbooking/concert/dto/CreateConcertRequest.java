package com.geekup.ticketbooking.concert.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request body for creating a new concert (customer-facing module).
 * Validation is enforced via {@code @Valid} at the controller layer.
 *
 * <p>Note: The admin module has its own {@code CreateConcertRequest} in the admin.dto package.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateConcertRequest {

    @NotBlank(message = "Concert name is required")
    @Size(max = 255, message = "Concert name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Venue is required")
    @Size(max = 255, message = "Venue must not exceed 255 characters")
    private String venue;

    @NotNull(message = "Concert date is required")
    @Future(message = "Concert date must be in the future")
    private LocalDateTime concertDate;

    @NotEmpty(message = "At least one ticket category is required")
    @Valid
    private List<TicketCategoryRequest> ticketCategories;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TicketCategoryRequest {

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        private String name;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "Price format is invalid")
        private BigDecimal price;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
    }
}
