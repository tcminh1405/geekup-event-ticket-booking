package com.geekup.ticketbooking.booking.repository;

import com.geekup.ticketbooking.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
}
