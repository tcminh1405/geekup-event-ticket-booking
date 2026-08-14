package com.geekup.ticketbooking.shared.cache;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity that records a failed Redis inventory update for later reconciliation.
 *
 * <p>When Redis is unavailable and an inventory write fails, a {@code ReconciliationTask}
 * is persisted in PostgreSQL. A background process can then re-sync the Redis cache
 * from the database once connectivity is restored.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reconciliation_tasks")
public class ReconciliationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_category_id", nullable = false)
    private Long ticketCategoryId;

    @Column(name = "expected_quantity", nullable = false)
    private Long expectedQuantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public ReconciliationTask(Long ticketCategoryId, Long expectedQuantity) {
        this.ticketCategoryId = ticketCategoryId;
        this.expectedQuantity = expectedQuantity;
    }
}
