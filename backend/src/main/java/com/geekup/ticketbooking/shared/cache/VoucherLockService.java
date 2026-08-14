package com.geekup.ticketbooking.shared.cache;

import com.geekup.ticketbooking.shared.exception.ServiceBusyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Distributed lock service for voucher application.
 *
 * <p>Key pattern: {@code lock:voucher:{userId}:{voucherId}}</p>
 * <p>Uses Redisson {@link RLock} with a 3-second wait and 10-second lease time
 * to prevent the same user from concurrently applying the same voucher.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherLockService {

    private static final int WAIT_SECONDS  = 3;
    private static final int LEASE_SECONDS = 10;
    private static final String KEY_PREFIX = "lock:voucher:";

    private final RedissonClient redissonClient;

    /**
     * Acquire the distributed lock for the given userId + voucherId combination.
     *
     * @param userId    the customer's user ID
     * @param voucherId the voucher ID being applied
     * @return the acquired {@link RLock} (caller must release it in a finally block)
     * @throws ServiceBusyException if the lock cannot be acquired within {@value #WAIT_SECONDS} seconds
     */
    public RLock acquireLock(Long userId, Long voucherId) {
        String lockKey = KEY_PREFIX + userId + ":" + voucherId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[VoucherLockService] Failed to acquire lock within {}s: key={}", WAIT_SECONDS, lockKey);
                throw new ServiceBusyException(
                        "Voucher service is currently busy. Please retry in a moment.");
            }
            log.debug("[VoucherLockService] Lock acquired: key={}", lockKey);
            return lock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException(
                    "Voucher lock acquisition was interrupted. Please retry in a moment.");
        }
    }

    /**
     * Release the lock if it is held by the current thread.
     *
     * @param lock the lock to release (may be null; no-op in that case)
     */
    public void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("[VoucherLockService] Lock released: name={}", lock.getName());
        }
    }
}
