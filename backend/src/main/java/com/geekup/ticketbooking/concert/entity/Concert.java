package com.geekup.ticketbooking.concert.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "concerts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private LocalDateTime concertDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TicketCategory> ticketCategories = new ArrayList<>();
}
