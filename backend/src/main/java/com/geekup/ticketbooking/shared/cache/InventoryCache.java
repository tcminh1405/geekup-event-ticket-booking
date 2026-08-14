package com.geekup.ticketbooking.shared.cache;

import com.geekup.ticketbooking.concert.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Redis read cache; PostgreSQL remains the authoritative inventory source. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCache {

    private static final String KEY_PREFIX = "inventory:";

    private final RedisTemplate<String, Long> longRedisTemplate;
    private final ReconciliationTaskRepository reconciliationTaskRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    public boolean initInventory(Long ticketCategoryId, int quantity) {
        return updateInventory(ticketCategoryId, quantity);
    }

    public Optional<Long> getInventory(Long ticketCategoryId) {
        try {
            return Optional.ofNullable(longRedisTemplate.opsForValue().get(key(ticketCategoryId)));
        } catch (Exception ex) {
            log.warn("Redis failure reading inventory categoryId={}", ticketCategoryId, ex);
            return Optional.empty();
        }
    }

    /** Writes an exact, authoritative quantity and schedules recovery on failure. */
    public boolean updateInventory(Long ticketCategoryId, long quantity) {
        if (writeInventory(ticketCategoryId, quantity)) {
            return true;
        }
        persistReconciliationTask(ticketCategoryId, quantity);
        return false;
    }

    /** Applies a post-commit delta. Recovery always stores the DB quantity, not this delta. */
    public boolean incrementInventory(Long ticketCategoryId, long delta) {
        try {
            longRedisTemplate.opsForValue().increment(key(ticketCategoryId), delta);
            return true;
        } catch (Exception ex) {
            log.error("Redis failure incrementing inventory categoryId={}, delta={}", ticketCategoryId, delta, ex);
            ticketCategoryRepository.findById(ticketCategoryId).ifPresent(category ->
                    persistReconciliationTask(ticketCategoryId, (long) category.getAvailableQuantity()));
            return false;
        }
    }

    /** Used by the recovery worker so a failed retry does not create duplicate tasks. */
    public boolean writeInventory(Long ticketCategoryId, long quantity) {
        try {
            longRedisTemplate.opsForValue().set(key(ticketCategoryId), quantity);
            return true;
        } catch (Exception ex) {
            log.error("Redis failure writing inventory categoryId={}, quantity={}", ticketCategoryId, quantity, ex);
            return false;
        }
    }

    private String key(Long ticketCategoryId) {
        return KEY_PREFIX + ticketCategoryId;
    }

    private void persistReconciliationTask(Long ticketCategoryId, Long expectedQuantity) {
        try {
            reconciliationTaskRepository.save(new ReconciliationTask(ticketCategoryId, expectedQuantity));
        } catch (Exception dbEx) {
            log.error("Failed to save reconciliation task for categoryId={}", ticketCategoryId, dbEx);
        }
    }
}
