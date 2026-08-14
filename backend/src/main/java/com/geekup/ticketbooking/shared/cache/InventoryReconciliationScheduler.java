package com.geekup.ticketbooking.shared.cache;

import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Replays failed Redis updates from PostgreSQL after Redis becomes available. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReconciliationScheduler {

    private final ReconciliationTaskRepository taskRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final InventoryCache inventoryCache;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void reconcile() {
        for (ReconciliationTask task : taskRepository.findAll()) {
            ticketCategoryRepository.findById(task.getTicketCategoryId()).ifPresentOrElse(category -> {
                if (inventoryCache.writeInventory(task.getTicketCategoryId(), category.getAvailableQuantity())) {
                    taskRepository.delete(task);
                    log.info("Reconciled inventory cache for categoryId={}", task.getTicketCategoryId());
                }
            }, () -> taskRepository.delete(task));
        }
    }
}
