package com.geekup.ticketbooking.concert.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_categories")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TicketCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    @Builder.Default
    private int soldQuantity = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
