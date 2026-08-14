package com.geekup.ticketbooking.booking.service;

import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.repository.BookingRepository;
import com.geekup.ticketbooking.booking.state.BookingState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BookingExpiryServiceTest {

    @Test
    void expiresAnInterruptedPaymentAndRestoresItsReservation() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = mock(BookingService.class);
        Booking booking = Booking.builder().id(42L).state(BookingState.AWAITING_PAYMENT).build();
        when(bookingRepository.transitionStateIfCurrentIn(eq(42L),
                eq(List.of(BookingState.PENDING, BookingState.AWAITING_PAYMENT)), eq(BookingState.EXPIRED)))
                .thenReturn(1);
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(booking));

        boolean expired = new BookingExpiryService(bookingRepository, bookingService).expire(42L);

        assertThat(expired).isTrue();
        verify(bookingService).restoreInventory(booking);
    }
}
