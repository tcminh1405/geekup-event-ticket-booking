package com.geekup.ticketbooking.booking.repository;

import com.geekup.ticketbooking.booking.entity.Booking;
import com.geekup.ticketbooking.booking.state.BookingState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Booking> findAllByStateAndPaymentDeadlineBefore(BookingState state, LocalDateTime deadline);

    // Count active (CONFIRMED + AWAITING_PAYMENT) bookings for inventory stats
    @Query("""
            SELECT COALESCE(SUM(bi.quantity), 0)
            FROM BookingItem bi
            WHERE bi.ticketCategory.id = :ticketCategoryId
              AND bi.booking.state IN ('CONFIRMED', 'AWAITING_PAYMENT')
            """)
    int countSoldQuantityByTicketCategoryId(@Param("ticketCategoryId") Long ticketCategoryId);
}
