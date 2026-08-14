package com.geekup.ticketbooking.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Operator request to flag or clear a booking that needs review. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateBookingSuspicionRequest {
    private boolean suspicious;

    @Size(max = 500, message = "suspicionReason must not exceed 500 characters")
    private String suspicionReason;
}
