package com.geekup.ticketbooking.shared.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed idempotency service that prevents duplicate booking processing.
 *
 * <p>Key pattern: {@code idempotency:{key}}, TTL: 24 hours</p>
 *
 * <p>Fail-open strategy: on Redis unavailability, return empty (allow request through)
 * or silently swallow store errors — booking correctness degrades gracefully to
 * the DB-level idempotency key constraint.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    // ─── Key helper ──────────────────────────────────────────────────────────────

    private String key(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Return the previously cached response JSON for the given key, if present.
     *
     * @param key the client-supplied idempotency key
     * @return an {@link Optional} with the cached JSON string, or empty on miss or Redis failure
     */
    public Optional<String> getIfPresent(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key(key));
            if (value == null) {
                return Optional.empty();
            }
            log.debug("[IdempotencyService] Cache hit: key={}", key);
            return Optional.of(value);
        } catch (Exception ex) {
            log.warn("[IdempotencyService] Redis failure on getIfPresent: key={} — failing open", key, ex);
            return Optional.empty();
        }
    }

    /**
     * Store a response JSON string for the given key with a 24-hour TTL.
     * Silently fails open if Redis is unavailable.
     *
     * @param key  the client-supplied idempotency key
     * @param json the serialised response body to cache
     */
    public void store(String key, String json) {
        try {
            redisTemplate.opsForValue().set(key(key), json, TTL);
            log.debug("[IdempotencyService] Stored idempotency response: key={}", key);
        } catch (Exception ex) {
            log.warn("[IdempotencyService] Redis failure on store: key={} — failing open (response not cached)", key, ex);
        }
    }
}
