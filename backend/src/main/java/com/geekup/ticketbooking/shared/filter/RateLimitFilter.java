package com.geekup.ticketbooking.shared.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import com.geekup.ticketbooking.shared.common.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Sliding-window rate limit filter: max 200 requests per 60 seconds per user.
 *
 * <p>Key pattern: {@code rate:{userId}}, TTL 60 seconds.</p>
 * <p>Uses {@link UserContext} (populated by {@code UserIdHeaderFilter}) for the user ID.
 * Requests without a user ID bypass rate limiting.</p>
 * <p>Fails open on Redis errors — logs a warning and allows the request through.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX    = "rate:";
    private static final long   RATE_LIMIT    = 200L;
    private static final long   WINDOW_SECS   = 60L;
    private static final int    RETRY_AFTER   = 60;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Long userId = UserContext.get();

        // No user ID → skip rate limiting
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String redisKey = KEY_PREFIX + userId;

        try {
            // Atomic increment
            Long count = redisTemplate.opsForValue().increment(redisKey);

            // Set TTL only on newly-created key (count == 1)
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(WINDOW_SECS));
            }

            if (count != null && count > RATE_LIMIT) {
                log.warn("[RateLimitFilter] Rate limit exceeded: userId={}, count={}", userId, count);
                writeRateLimitResponse(response);
                return;
            }

        } catch (Exception ex) {
            log.warn("[RateLimitFilter] Redis failure for userId={} — failing open", userId, ex);
            // Fail open: allow the request through
        }

        filterChain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(RETRY_AFTER));
        ApiResponse<?> body = ApiResponse.error(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry after " + RETRY_AFTER + " seconds.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
