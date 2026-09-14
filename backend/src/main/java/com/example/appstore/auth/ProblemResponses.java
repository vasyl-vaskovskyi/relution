package com.example.appstore.auth;

import com.example.appstore.api.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes security failures in the same RFC 9457 format as the API ({@code docs/architecture/security.md}): 401
 * {@code unauthorized} without a valid token, 403 {@code forbidden} without the scope. No exception details are sent.
 */
final class ProblemResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    ProblemResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, ProblemDetails.unauthorized(request.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
            throws IOException {
        write(response, ProblemDetails.forbidden(request.getRequestURI()));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetails.asMap(problem));
    }
}
