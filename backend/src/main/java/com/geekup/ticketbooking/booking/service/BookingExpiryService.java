package com.geekup.ticketbooking.booking.service;

import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.repository.BookingRepository;
import com.geekup.ticketbooking.booking.state.BookingState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes a single expiry in its own proxied transaction. */
@Service
@RequiredArgsConstructor
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Transactional
    public boolean expire(Long bookingId) {
        if (bookingRepository.transitionStateIfCurrentIn(bookingId,
                java.util.List.of(BookingState.PENDING, BookingState.AWAITING_PAYMENT), BookingState.EXPIRED) == 0) {
            return false;
        }
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        bookingService.restoreInventory(booking);
        return true;
    }
}
