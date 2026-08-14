package com.geekup.ticketbooking.shared.exception;

/** Thrown when a concert with the given ID does not exist or is not published. → 404 CONCERT_NOT_FOUND */
public class ConcertNotFoundException extends ResourceNotFoundException {

    public ConcertNotFoundException(Long concertId) {
        super("CONCERT_NOT_FOUND", "Concert with ID " + concertId + " was not found.");
    }
}
