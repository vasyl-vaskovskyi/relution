package com.example.appstore.auth;

import com.example.appstore.api.ProblemDetails;
import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /auth/token}: HTTP Basic client credentials in, a short-lived bearer token out
 * ({@code docs/api/README.md}). Failed attempts are limited per client address before the credentials are compared
 * (ADR-0049) and logged without any credential or address.
 */
@RestController
@Tag(name = "Auth")
class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private static final String PROBLEM_JSON = "application/problem+json";

    private final TokenService tokenService;
    private final TokenRequestLimiter limiter;
    private final Counter issued;
    private final Counter rejected;
    private final Counter rateLimited;

    TokenController(TokenService tokenService, TokenRequestLimiter limiter, MeterRegistry registry) {
        this.tokenService = tokenService;
        this.limiter = limiter;
        this.issued = outcomeCounter(registry, "issued");
        this.rejected = outcomeCounter(registry, "rejected");
        this.rateLimited = outcomeCounter(registry, "rate_limited");
    }

    @PostMapping("/auth/token")
    @Operation(
            summary = "Get an access token",
            description = "HTTP Basic authentication with the client id and secret. Failed attempts are limited per"
                    + " client address; over the limit every request gets 429, valid credentials included.")
    @ApiResponse(
            responseCode = "200",
            description = "A bearer token with scope `apps:read`",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid client credentials (`unauthorized`)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many failed attempts from this client address (`too-many-requests`)",
            headers =
                    @Header(
                            name = HttpHeaders.RETRY_AFTER,
                            description = "Seconds to wait before retrying",
                            schema = @Schema(type = "integer")),
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Object> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request) {
        String key = TokenRequestLimiter.key(request.getRemoteAddr());
        Optional<Duration> wait = limiter.tryAcquire(key);
        if (wait.isPresent()) {
            rateLimited.increment();
            log.debug("token request rejected: too many failed attempts from this client address");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(wait.get().toSeconds()))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(ProblemDetails.tooManyRequests(request.getRequestURI()));
        }
        Optional<Credentials> credentials = basicCredentials(authorization);
        if (credentials.isEmpty()
                || !tokenService.credentialsMatch(
                        credentials.get().clientId(), credentials.get().clientSecret())) {
            rejected.increment();
            log.info("token request rejected: missing or invalid client credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"appstore\"")
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(ProblemDetails.unauthorized(request.getRequestURI()));
        }
        limiter.release(key);
        TokenService.IssuedToken token = tokenService.issue(credentials.get().clientId());
        issued.increment();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TokenResponse(token.value(), "Bearer", token.expiresInSeconds()));
    }

    private static Counter outcomeCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(MetricNames.AUTH_TOKEN_REQUESTS)
                .description("POST /auth/token requests by outcome")
                .tag(MetricNames.TAG_OUTCOME, outcome)
                .register(registry);
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {

        @Override
        public String toString() {
            return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", accessToken=***]";
        }
    }

    record Credentials(String clientId, String clientSecret) {

        @Override
        public String toString() {
            return "Credentials[clientId=***, clientSecret=***]";
        }
    }

    static Optional<Credentials> basicCredentials(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(6).strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(decoded.substring(0, colon), decoded.substring(colon + 1)));
    }
}
