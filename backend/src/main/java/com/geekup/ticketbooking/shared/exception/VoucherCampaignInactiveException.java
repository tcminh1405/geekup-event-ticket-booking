package com.geekup.ticketbooking.shared.exception;

/** Thrown when the current date is outside the voucher campaign's [start_date, end_date] window. → 422 VOUCHER_CAMPAIGN_INACTIVE */
public class VoucherCampaignInactiveException extends ValidationException {

    public VoucherCampaignInactiveException() {
        super("VOUCHER_CAMPAIGN_INACTIVE", "This voucher campaign is not currently active.");
    }
}
