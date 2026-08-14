package com.geekup.ticketbooking.shared.filter;

import com.geekup.ticketbooking.shared.common.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that extracts the {@code X-User-Id} header from each request and
 * stores it in {@link UserContext} for the duration of the request.
 *
 * <p>Must run first in the filter chain (order 1) so that subsequent filters
 * and handlers can call {@link UserContext#get()} to obtain the user ID.</p>
 *
 * <p>The context is always cleared in a finally block to prevent thread-pool leaks.</p>
 */
@Slf4j
public class UserIdHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader(HEADER_USER_ID);
        try {
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    long userId = Long.parseLong(userIdHeader.trim());
                    UserContext.set(userId);
                    log.debug("[UserIdHeaderFilter] X-User-Id={}", userId);
                } catch (NumberFormatException e) {
                    log.warn("[UserIdHeaderFilter] Invalid X-User-Id header value: '{}'", userIdHeader);
                    // Invalid format — don't set context; downstream code handles absent user
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
