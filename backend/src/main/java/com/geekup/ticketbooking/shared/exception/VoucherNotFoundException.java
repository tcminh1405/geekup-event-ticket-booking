package com.geekup.ticketbooking.shared.exception;

/** Thrown when a voucher code does not exist in the database. → 404 VOUCHER_NOT_FOUND */
public class VoucherNotFoundException extends ResourceNotFoundException {

    public VoucherNotFoundException(String code) {
        super("VOUCHER_NOT_FOUND", "Voucher with code '" + code + "' was not found.");
    }
}
