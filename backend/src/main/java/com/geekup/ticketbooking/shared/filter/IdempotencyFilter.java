package com.geekup.ticketbooking.shared.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import com.geekup.ticketbooking.shared.common.UserContext;
import com.geekup.ticketbooking.shared.idempotency.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Idempotency filter for {@code POST /api/v1/bookings/reserve}.
 *
 * <ol>
 *   <li>Reads the {@code Idempotency-Key} header.</li>
 *   <li>Missing/blank key → 400 {@code MISSING_IDEMPOTENCY_KEY}.</li>
 *   <li>Key already cached in Redis → replay stored response directly.</li>
 *   <li>New key → wrap response in {@link ContentCachingResponseWrapper},
 *       proceed with chain, then store the response body in Redis if the
 *       status is 2xx.</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER  = "Idempotency-Key";
    private static final String RESERVE_PATH        = "/api/v1/bookings/reserve";
    private static final String POST_METHOD         = "POST";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only intercept POST /api/v1/bookings/reserve
        return !(POST_METHOD.equalsIgnoreCase(request.getMethod())
                && RESERVE_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        // 1. Missing or blank header → 400
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("[IdempotencyFilter] Missing Idempotency-Key header");
            writeMissingKeyResponse(response);
            return;
        }

        // A key belongs to a customer, not to the whole platform. This avoids
        // one customer replaying another customer's cached booking response.
        String scopedKey = UserContext.get() + ":" + idempotencyKey;

        // 2. Check Redis cache for an existing response
        Optional<String> cached = idempotencyService.getIfPresent(scopedKey);
        if (cached.isPresent()) {
            log.debug("[IdempotencyFilter] Returning cached response for key={}", idempotencyKey);
            replayResponse(response, cached.get());
            return;
        }

        // Claim before running the controller. A cache check followed by a
        // later store is otherwise vulnerable to simultaneous retries.
        if (!idempotencyService.tryStartProcessing(scopedKey)) {
            writeInProgressResponse(response);
            return;
        }

        // 3. New key — wrap response to capture the body
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int status = wrappedResponse.getStatus();

            // 4. Store body only on 2xx responses
            if (status >= 200 && status < 300) {
                String responseBody = new String(
                        wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                if (!responseBody.isBlank()) {
                    idempotencyService.store(scopedKey, responseBody);
                    log.debug("[IdempotencyFilter] Stored idempotency response: key={}, status={}",
                            idempotencyKey, status);
                }
            } else {
                idempotencyService.clearInFlight(scopedKey);
            }

            // Always copy the wrapped response body back to the real response
            wrappedResponse.copyBodyToResponse();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void writeMissingKeyResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<?> body = ApiResponse.error(
                "MISSING_IDEMPOTENCY_KEY",
                "The Idempotency-Key header is required for booking reservation requests.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void replayResponse(HttpServletResponse response, String json) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json);
    }

    private void writeInProgressResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<?> body = ApiResponse.error("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "A request with this Idempotency-Key is already being processed. Retry shortly.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
