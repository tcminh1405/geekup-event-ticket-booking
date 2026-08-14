package com.geekup.ticketbooking.booking.repository;

import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.state.BookingState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    java.util.Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<Booking> findAllByStateAndPaymentDeadlineBefore(BookingState state, LocalDateTime deadline);

    List<Booking> findAllByStateInAndPaymentDeadlineBefore(List<BookingState> states, LocalDateTime deadline);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.state = :target WHERE b.id = :id AND b.state = :expected")
    int transitionStateIfCurrent(@Param("id") Long id,
                                 @Param("expected") BookingState expected,
                                 @Param("target") BookingState target);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.state = :target WHERE b.id = :id AND b.state IN :expectedStates")
    int transitionStateIfCurrentIn(@Param("id") Long id,
                                   @Param("expectedStates") List<BookingState> expectedStates,
                                   @Param("target") BookingState target);

    // Count inventory that is no longer available: pending reservations as well
    // as bookings being paid or confirmed.
    @Query("""
            SELECT COALESCE(SUM(bi.quantity), 0)
            FROM BookingItem bi
            WHERE bi.ticketCategory.id = :ticketCategoryId
              AND bi.booking.state IN ('PENDING', 'AWAITING_PAYMENT', 'CONFIRMED')
            """)
    int countSoldQuantityByTicketCategoryId(@Param("ticketCategoryId") Long ticketCategoryId);
}
