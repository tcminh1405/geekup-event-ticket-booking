package com.geekup.ticketbooking.shared.exception;

/** Thrown when a voucher campaign's maximum usage count has been reached. → 409 VOUCHER_EXHAUSTED */
public class VoucherExhaustedException extends ConflictException {

    public VoucherExhaustedException() {
        super("VOUCHER_EXHAUSTED", "The maximum usage limit for this voucher campaign has been reached.");
    }
}
