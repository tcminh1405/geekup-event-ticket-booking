package com.geekup.ticketbooking.shared.exception;

/** Thrown when a booking with the given ID does not exist. → 404 BOOKING_NOT_FOUND */
public class BookingNotFoundException extends ResourceNotFoundException {

    public BookingNotFoundException(Long bookingId) {
        super("BOOKING_NOT_FOUND", "Booking with ID " + bookingId + " was not found.");
    }
}
