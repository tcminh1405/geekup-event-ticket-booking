package com.geekup.ticketbooking.voucher.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_campaigns")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class VoucherCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String discountType; // "PERCENTAGE" or "FIXED"

    @Column(nullable = false)
    private BigDecimal discountValue;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal minBookingAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private int maxUsageCount;

    @Column(nullable = false)
    @Builder.Default
    private int currentUsageCount = 0;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
