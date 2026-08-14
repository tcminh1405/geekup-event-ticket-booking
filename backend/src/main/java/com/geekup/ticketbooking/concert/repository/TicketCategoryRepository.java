package com.geekup.ticketbooking.concert.repository;

import com.geekup.ticketbooking.concert.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findAllByConcertId(Long concertId);

    /**
     * Atomically decrements availableQuantity only if current stock is sufficient.
     * Returns 1 if the decrement succeeded, 0 if not enough stock (available_quantity < qty).
     */
    @Modifying
    @Query("UPDATE TicketCategory t SET t.availableQuantity = t.availableQuantity - :qty WHERE t.id = :id AND t.availableQuantity >= :qty")
    int decrementAvailableQuantity(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Restores (increments) availableQuantity after a booking is cancelled or expired.
     */
    @Modifying
    @Query("UPDATE TicketCategory t SET t.availableQuantity = t.availableQuantity + :qty WHERE t.id = :id")
    void incrementAvailableQuantity(@Param("id") Long id, @Param("qty") int qty);
}
