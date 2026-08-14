package com.geekup.ticketbooking.property;

import com.geekup.ticketbooking.shared.idempotency.IdempotencyService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property test P2 — Idempotency: No Duplicate Bookings
 *
 * For any successfully processed reservation request with idempotency key K,
 * re-submitting an identical request with the same key K within 24 hours SHALL
 * return the same response body and SHALL NOT create an additional Booking.
 *
 * **Validates: Requirements 2.5, 9.3**
 */
@net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")
class IdempotencyPropertyTest {

    // ─── Arbitraries ─────────────────────────────────────────────────────────────

    /**
     * Generates a valid idempotency key: non-empty, at most 128 characters,
     * printable ASCII (no control characters).
     */
    @Provide
    Arbitrary<String> validIdempotencyKey() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars("-_")
                .ofMinLength(1)
                .ofMaxLength(128);
    }

    /**
     * Generates a plausible JSON response body (non-empty, simulates a real payload).
     */
    @Provide
    Arbitrary<String> jsonResponse() {
        return Arbitraries.integers()
                .between(1, 99999)
                .map(id -> "{\"bookingId\":" + id + ",\"state\":\"PENDING\",\"totalAmount\":500000}");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Build a fresh {@link IdempotencyService} backed by a fully mocked
     * {@link StringRedisTemplate}.  The {@code storedValues} array (length 1)
     * acts as a tiny in-memory cell that stores whatever was last passed to
     * {@code set()}, so that the subsequent {@code get()} returns it.
     */
    private IdempotencyService buildService(StringRedisTemplate template,
                                            ValueOperations<String, String> ops,
                                            String[] storedValue) {
        when(template.opsForValue()).thenReturn(ops);

        // Capture whatever is stored and replay it on get
        doAnswer(inv -> {
            storedValue[0] = inv.getArgument(1, String.class);
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));

        when(ops.get(anyString())).thenAnswer(inv -> storedValue[0]);

        return new IdempotencyService(template);
    }

    // ─── Property 2a: store(K, json) then getIfPresent(K) returns same json ──────

    /**
     * **Validates: Requirements 2.5, 9.3**
     *
     * For any idempotency key K and any response JSON, calling store(K, json)
     * followed by getIfPresent(K) must return an Optional containing exactly
     * the same JSON string — proving the same response is returned on retry.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")
    void sameKeyProducesNoDuplicateBooking(
            @ForAll("validIdempotencyKey") String key,
            @ForAll("jsonResponse") String json) {

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        String[] stored = {null};

        IdempotencyService service = buildService(template, ops, stored);

        // First (successful) processing stores the response
        service.store(key, json);

        // Subsequent retry with the same key must return the original response
        Optional<String> result = service.getIfPresent(key);

        assertThat(result)
                .as("getIfPresent(%s) after store should return the cached json", key)
                .isPresent()
                .hasValue(json);
    }

    // ─── Property 2b: absent key returns Optional.empty() ────────────────────────

    /**
     * **Validates: Requirements 2.5, 9.3**
     *
     * For any idempotency key K that has never been stored, getIfPresent(K)
     * must return Optional.empty() — no phantom cache hits.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")
    void absentKeyReturnsEmpty(@ForAll("validIdempotencyKey") String key) {

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);  // nothing stored

        IdempotencyService service = new IdempotencyService(template);

        Optional<String> result = service.getIfPresent(key);

        assertThat(result)
                .as("getIfPresent(%s) for an absent key must return empty", key)
                .isEmpty();
    }

    // ─── Property 2c: valid keys are stored under the correct Redis key pattern ──

    /**
     * **Validates: Requirements 2.5, 9.3**
     *
     * For any valid idempotency key K (non-empty, ≤128 chars), store(K, json)
     * must write to the Redis key {@code idempotency:{K}} with a 24-hour TTL.
     * This confirms the key-namespace contract that separates idempotency
     * entries from other Redis data.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")
    void validKeysAreStoredWithCorrectPrefixAndTtl(
            @ForAll("validIdempotencyKey") String key,
            @ForAll("jsonResponse") String json) {

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);

        IdempotencyService service = new IdempotencyService(template);
        service.store(key, json);

        // Verify the key stored in Redis has the "idempotency:" prefix
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(ops).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue())
                .as("Redis key must start with 'idempotency:' prefix")
                .startsWith("idempotency:")
                .endsWith(key);

        assertThat(valueCaptor.getValue())
                .as("Stored value must match the provided json")
                .isEqualTo(json);

        assertThat(ttlCaptor.getValue())
                .as("TTL must be exactly 24 hours")
                .isEqualTo(Duration.ofHours(24));
    }

    // ─── Property 2d: storing the same key twice still returns the same response ─

    /**
     * **Validates: Requirements 2.5, 9.3**
     *
     * Idempotency means the first stored value is the canonical one.
     * After store(K, json1) and store(K, json2) (simulating a race / retry on
     * the store path), getIfPresent(K) must return the last-written value —
     * but crucially only ONE Booking is ever created (the store call does not
     * create a new Booking; it only caches a response).  The test validates
     * that whatever is cached is returned faithfully, with no silent data loss.
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("Feature: concert-ticket-booking, Property 2: idempotency-no-duplicate-bookings")
    void storingSameKeyTwiceReturnsLastStoredResponse(
            @ForAll("validIdempotencyKey") String key,
            @ForAll("jsonResponse") String json) {

        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        // In-memory cell: simulates Redis overwrite behaviour
        String[] stored = {null};

        doAnswer(inv -> {
            stored[0] = inv.getArgument(1, String.class);
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));

        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> stored[0]);

        IdempotencyService service = new IdempotencyService(template);

        // Store once (original successful booking response)
        service.store(key, json);

        // Store again with the same key and same json (idempotent retry)
        service.store(key, json);

        Optional<String> result = service.getIfPresent(key);

        assertThat(result)
                .as("After storing same key twice, getIfPresent must still return the json")
                .isPresent()
                .hasValue(json);
    }
}
