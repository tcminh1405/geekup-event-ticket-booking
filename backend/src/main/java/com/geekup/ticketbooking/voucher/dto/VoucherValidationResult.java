package com.geekup.ticketbooking.voucher.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Internal DTO returned by {@link com.geekup.ticketbooking.voucher.service.VoucherService}
 * after a successful voucher validation and application.
 *
 * <p>Used by the Booking module to obtain the discounted booking amount and the
 * applied voucher ID for persisting against the Booking record.</p>
 */
@Getter
@Builder
public class VoucherValidationResult {

    /** The voucher ID that was successfully applied. */
    private final Long voucherId;

    /** The discounted total amount after applying the voucher. */
    private final BigDecimal discountedAmount;

    /** The raw discount value deducted from the original amount. */
    private final BigDecimal discountAmount;
}
