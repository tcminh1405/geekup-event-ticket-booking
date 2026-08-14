package com.geekup.ticketbooking.shared.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.ticketbooking.shared.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Minimal role gate for the test's mocked identity model. */
@RequiredArgsConstructor
public class AdminRoleFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH = "/api/v1/admin/";
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String role = request.getHeader("X-Role");
        if (!"ADMIN".equalsIgnoreCase(role) && !"OPERATOR".equalsIgnoreCase(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(
                    "ADMIN_ROLE_REQUIRED", "X-Role must be ADMIN or OPERATOR for this endpoint.")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
