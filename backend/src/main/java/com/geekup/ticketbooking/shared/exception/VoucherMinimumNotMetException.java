package com.geekup.ticketbooking.shared.exception;

/** Thrown when a booking total does not meet the voucher's minimum booking amount. → 422 VOUCHER_MINIMUM_NOT_MET */
public class VoucherMinimumNotMetException extends ValidationException {

    public VoucherMinimumNotMetException() {
        super("VOUCHER_MINIMUM_NOT_MET", "The booking total does not meet the minimum amount required for this voucher.");
    }
}
