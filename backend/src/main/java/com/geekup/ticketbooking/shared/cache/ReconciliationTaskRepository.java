package com.geekup.ticketbooking.shared.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisting {@link ReconciliationTask} records when Redis
 * inventory updates fail.
 */
@Repository
public interface ReconciliationTaskRepository extends JpaRepository<ReconciliationTask, Long> {
}
