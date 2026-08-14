package com.geekup.ticketbooking.shared.exception;

/** Thrown when a TicketCategory has no remaining inventory. → 409 TICKET_SOLD_OUT */
public class TicketSoldOutException extends ConflictException {

    public TicketSoldOutException() {
        super("TICKET_SOLD_OUT", "The requested ticket category has no remaining inventory.");
    }
}
