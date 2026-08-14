package com.geekup.ticketbooking.shared.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stub placeholder for Redis-backed inventory cache.
 * The real implementation (RedisTemplate-based) is delivered in Task 6.
 *
 * <p>Key pattern: {@code inventory:{ticketCategoryId}}</p>
 */
@Slf4j
@Component
public class InventoryCache {

    /**
     * Initialise the cached inventory for a ticket category.
     * Called when a concert is published.
     *
     * @param ticketCategoryId the ticket category ID
     * @param quantity         the initial available quantity
     */
    public void initInventory(Long ticketCategoryId, int quantity) {
        log.info("[InventoryCache STUB] initInventory: categoryId={}, qty={}", ticketCategoryId, quantity);
    }

    /**
     * Get the cached remaining inventory for a ticket category.
     *
     * @param ticketCategoryId the ticket category ID
     * @return an {@link Optional} with the cached quantity, or empty on cache miss / Redis unavailability
     */
    public Optional<Long> getInventory(Long ticketCategoryId) {
        log.debug("[InventoryCache STUB] getInventory: categoryId={} — returning empty (cache miss)", ticketCategoryId);
        return Optional.empty();
    }

    /**
     * Overwrite the cached inventory count for a ticket category.
     *
     * @param ticketCategoryId the ticket category ID
     * @param quantity         the new quantity to set
     */
    public void updateInventory(Long ticketCategoryId, long quantity) {
        log.info("[InventoryCache STUB] updateInventory: categoryId={}, qty={}", ticketCategoryId, quantity);
    }

    /**
     * Atomically increment (or decrement with a negative delta) the cached inventory.
     *
     * @param ticketCategoryId the ticket category ID
     * @param delta            the amount to add (use negative value to decrement)
     */
    public void incrementInventory(Long ticketCategoryId, long delta) {
        log.info("[InventoryCache STUB] incrementInventory: categoryId={}, delta={}", ticketCategoryId, delta);
    }
}
