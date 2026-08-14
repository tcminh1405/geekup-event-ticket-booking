package com.geekup.ticketbooking.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.ticketbooking.shared.filter.IdempotencyFilter;
import com.geekup.ticketbooking.shared.filter.RateLimitFilter;
import com.geekup.ticketbooking.shared.filter.UserIdHeaderFilter;
import com.geekup.ticketbooking.shared.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Registers the three request-processing filters in the correct order:
 *
 * <ol>
 *   <li>Order 1 — {@link UserIdHeaderFilter}: extracts {@code X-User-Id} → {@code UserContext}</li>
 *   <li>Order 2 — {@link RateLimitFilter}: sliding-window 200 req/min per user</li>
 *   <li>Order 3 — {@link IdempotencyFilter}: deduplicates reservation requests</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final IdempotencyService idempotencyService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<UserIdHeaderFilter> userIdHeaderFilter() {
        FilterRegistrationBean<UserIdHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserIdHeaderFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("userIdHeaderFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(stringRedisTemplate, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        registration.setName("rateLimitFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter() {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IdempotencyFilter(idempotencyService, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(3);
        registration.setName("idempotencyFilter");
        return registration;
    }
}
