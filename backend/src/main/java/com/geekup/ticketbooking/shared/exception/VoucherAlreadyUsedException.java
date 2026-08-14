package com.geekup.ticketbooking.shared.exception;

/** Thrown when a customer tries to use a voucher they have already applied. → 409 VOUCHER_ALREADY_USED */
public class VoucherAlreadyUsedException extends ConflictException {

    public VoucherAlreadyUsedException() {
        super("VOUCHER_ALREADY_USED", "This voucher has already been used by your account.");
    }
}
