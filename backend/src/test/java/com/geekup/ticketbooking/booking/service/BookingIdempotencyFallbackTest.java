package com.geekup.ticketbooking.booking.service;

import com.geekup.ticketbooking.booking.dto.BookingResponse;
import com.geekup.ticketbooking.booking.dto.ReserveBookingRequest;
import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.repository.BookingRepository;
import com.geekup.ticketbooking.booking.state.BookingState;
import com.geekup.ticketbooking.concert.entity.Concert;
import com.geekup.ticketbooking.concert.repository.ConcertRepository;
import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import com.geekup.ticketbooking.shared.cache.InventoryCache;
import com.geekup.ticketbooking.shared.infrastructure.payment.MockPaymentGateway;
import com.geekup.ticketbooking.voucher.service.VoucherService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookingIdempotencyFallbackTest {

    @Test
    void returnsExistingBookingBeforeTouchingInventoryWhenRedisHasNoCachedResponse() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        ConcertRepository concertRepository = mock(ConcertRepository.class);
        Concert concert = Concert.builder().id(9L).name("Concert").build();
        Booking existing = Booking.builder().id(7L).userId(100L).concert(concert)
                .state(BookingState.PENDING).totalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO).idempotencyKey("retry-key").build();
        when(bookingRepository.findByUserIdAndIdempotencyKey(100L, "retry-key"))
                .thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookingRepository, concertRepository,
                mock(TicketCategoryRepository.class), mock(VoucherService.class),
                mock(MockPaymentGateway.class), mock(InventoryCache.class));

        BookingResponse response = service.reserve(100L,
                ReserveBookingRequest.builder().concertId(9L).build(), "retry-key");

        assertThat(response.getBookingId()).isEqualTo(7L);
        verifyNoInteractions(concertRepository);
    }
}
