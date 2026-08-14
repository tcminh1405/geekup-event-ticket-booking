package com.geekup.ticketbooking.booking.entity;

import com.geekup.ticketbooking.concert.entity.TicketCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_category_id", nullable = false)
    private TicketCategory ticketCategory;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal subtotal;
}
