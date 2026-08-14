package com.geekup.ticketbooking.shared.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Redis-backed inventory cache for ticket categories.
 *
 * <p>Key pattern: {@code inventory:{ticketCategoryId}}</p>
 *
 * <p>PostgreSQL is the authoritative write source; this cache serves fast
 * availability reads during browsing and flash sales. All write operations
 * are asynchronous post-commit updates.</p>
 *
 * <p>On Redis failure: logs at ERROR level and persists a {@link ReconciliationTask}
 * in PostgreSQL for later re-sync.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCache {

    private static final String KEY_PREFIX = "inventory:";

    private final RedisTemplate<String, Long> longRedisTemplate;
    private final ReconciliationTaskRepository reconciliationTaskRepository;

    // ─── Key helper ──────────────────────────────────────────────────────────────

    private String key(Long ticketCategoryId) {
        return KEY_PREFIX + ticketCategoryId;
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Initialise the cached inventory for a ticket category.
     * Called when a concert is published.
     *
     * @param ticketCategoryId the ticket category ID
     * @param quantity         the initial available quantity
     */
    public void initInventory(Long ticketCategoryId, int quantity) {
        try {
            longRedisTemplate.opsForValue().set(key(ticketCategoryId), (long) quantity);
            log.debug("[InventoryCache] initInventory: categoryId={}, qty={}", ticketCategoryId, quantity);
        } catch (Exception ex) {
            log.error("[InventoryCache] Redis failure on initInventory: categoryId={}, qty={} — scheduling reconciliation",
                    ticketCategoryId, quantity, ex);
            persistReconciliationTask(ticketCategoryId, (long) quantity);
        }
    }

    /**
     * Get the cached remaining inventory for a ticket category.
     *
     * @param ticketCategoryId the ticket category ID
     * @return an {@link Optional} with the cached quantity, or empty on cache miss / Redis unavailability
     */
    public Optional<Long> getInventory(Long ticketCategoryId) {
        try {
            Long value = longRedisTemplate.opsForValue().get(key(ticketCategoryId));
            if (value == null) {
                log.debug("[InventoryCache] Cache miss: categoryId={}", ticketCategoryId);
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (Exception ex) {
            log.warn("[InventoryCache] Redis failure on getInventory: categoryId={} — returning empty (fail open)",
                    ticketCategoryId, ex);
            return Optional.empty();
        }
    }

    /**
     * Overwrite the cached inventory count for a ticket category.
     *
     * @param ticketCategoryId the ticket category ID
     * @param quantity         the new quantity to set
     */
    public void updateInventory(Long ticketCategoryId, long quantity) {
        try {
            longRedisTemplate.opsForValue().set(key(ticketCategoryId), quantity);
            log.debug("[InventoryCache] updateInventory: categoryId={}, qty={}", ticketCategoryId, quantity);
        } catch (Exception ex) {
            log.error("[InventoryCache] Redis failure on updateInventory: categoryId={}, qty={} — scheduling reconciliation",
                    ticketCategoryId, quantity, ex);
            persistReconciliationTask(ticketCategoryId, quantity);
        }
    }

    /**
     * Atomically increment (or decrement with a negative delta) the cached inventory.
     *
     * @param ticketCategoryId the ticket category ID
     * @param delta            the amount to add (use negative value to decrement)
     */
    public void incrementInventory(Long ticketCategoryId, long delta) {
        try {
            longRedisTemplate.opsForValue().increment(key(ticketCategoryId), delta);
            log.debug("[InventoryCache] incrementInventory: categoryId={}, delta={}", ticketCategoryId, delta);
        } catch (Exception ex) {
            log.error("[InventoryCache] Redis failure on incrementInventory: categoryId={}, delta={} — scheduling reconciliation",
                    ticketCategoryId, delta, ex);
            // For increment failures we cannot know the exact expected quantity without a DB lookup;
            // persist a task with the delta as the expected quantity so a reconciler can re-sync.
            persistReconciliationTask(ticketCategoryId, delta);
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    private void persistReconciliationTask(Long ticketCategoryId, Long expectedQuantity) {
        try {
            reconciliationTaskRepository.save(new ReconciliationTask(ticketCategoryId, expectedQuantity));
            log.info("[InventoryCache] ReconciliationTask saved: categoryId={}, expectedQty={}",
                    ticketCategoryId, expectedQuantity);
        } catch (Exception dbEx) {
            log.error("[InventoryCache] Failed to persist ReconciliationTask for categoryId={}: {}",
                    ticketCategoryId, dbEx.getMessage(), dbEx);
        }
    }
}
