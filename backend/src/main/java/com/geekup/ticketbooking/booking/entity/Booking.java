package com.geekup.ticketbooking.booking.entity;

import com.geekup.ticketbooking.booking.state.BookingState;
import com.geekup.ticketbooking.concert.entity.Concert;
import com.geekup.ticketbooking.voucher.entity.Voucher;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingState state;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    private LocalDateTime paymentDeadline;

    private LocalDateTime paymentTimestamp;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BookingItem> items = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
