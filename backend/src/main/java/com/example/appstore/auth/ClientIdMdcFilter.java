package com.example.appstore.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the authenticated client id (JWT {@code sub}) into the MDC as {@code clientId}, so logs can be grouped by API
 * client. Runs inside the security chain after bearer authentication; the value comes from a token we signed.
 */
final class ClientIdMdcFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "clientId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getSubject() != null) {
            MDC.put(MDC_KEY, jwt.getToken().getSubject());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
