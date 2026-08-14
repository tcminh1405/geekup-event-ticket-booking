package com.geekup.ticketbooking.voucher.entity;

import com.geekup.ticketbooking.booking.entity.Booking;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private VoucherCampaign campaign;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    private Long usedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_in_booking_id")
    private Booking usedInBooking;

    private LocalDateTime usedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
