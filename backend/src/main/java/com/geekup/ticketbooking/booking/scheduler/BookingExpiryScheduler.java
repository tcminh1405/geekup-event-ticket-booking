package com.geekup.ticketbooking.booking.scheduler;

import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.repository.BookingRepository;
import com.geekup.ticketbooking.booking.service.BookingService;
import com.geekup.ticketbooking.booking.state.BookingState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that expires PENDING bookings whose payment deadline has passed.
 *
 * <p>Runs every 30 seconds ({@code fixedDelay = 30000 ms}).
 * For each expired booking:
 * <ol>
 *   <li>Transition state from PENDING to EXPIRED.</li>
 *   <li>Restore DB inventory ({@code available_quantity + qty} for each item).</li>
 *   <li>Update Redis inventory cache.</li>
 *   <li>Restore voucher usage (if a voucher was applied).</li>
 * </ol>
 * </p>
 *
 * <p>Requirements: 2.7, 2.8, 2.9, 5.1–5.5</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService    bookingService;

    /**
     * Find all PENDING bookings whose {@code paymentDeadline} is in the past,
     * expire them, and restore their inventory and vouchers.
     *
     * <p>Each booking is processed in its own transaction so that a failure in
     * one does not prevent others from being expired.</p>
     */
    @Scheduled(fixedDelay = 30_000)
    public void expireOverdueBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> overdueBookings =
                bookingRepository.findAllByStateAndPaymentDeadlineBefore(BookingState.PENDING, now);

        if (overdueBookings.isEmpty()) {
            log.debug("[BookingExpiryScheduler] No overdue PENDING bookings found.");
            return;
        }

        log.info("[BookingExpiryScheduler] Found {} overdue PENDING booking(s) to expire.", overdueBookings.size());

        for (Booking booking : overdueBookings) {
            try {
                expireBooking(booking);
            } catch (Exception ex) {
                log.error("[BookingExpiryScheduler] Failed to expire bookingId={}: {}",
                        booking.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Expire a single booking and restore its resources. Wrapped in its own
     * {@code @Transactional} so each booking is handled independently.
     *
     * @param booking the PENDING booking to expire
     */
    @Transactional
    public void expireBooking(Booking booking) {
        // Re-fetch to ensure we have the latest state and avoid stale data
        Booking fresh = bookingRepository.findById(booking.getId()).orElse(null);
        if (fresh == null) {
            log.warn("[BookingExpiryScheduler] Booking {} no longer exists, skipping.", booking.getId());
            return;
        }

        // Guard: another thread/instance may have already transitioned this booking
        if (fresh.getState() != BookingState.PENDING) {
            log.debug("[BookingExpiryScheduler] Booking {} is now in state {}; skipping expiry.",
                    fresh.getId(), fresh.getState());
            return;
        }

        fresh.setState(BookingState.EXPIRED);
        bookingRepository.save(fresh);

        // Restore inventory (DB + cache) and voucher
        bookingService.restoreInventory(fresh);

        log.info("[BookingExpiryScheduler] Booking {} expired and inventory restored.", fresh.getId());
    }
}
