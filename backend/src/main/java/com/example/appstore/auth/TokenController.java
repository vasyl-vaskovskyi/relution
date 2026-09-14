package com.example.appstore.auth;

import com.example.appstore.api.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /auth/token}: HTTP Basic client credentials in, a short-lived bearer token out
 * ({@code docs/api/README.md}). Failed attempts are logged without any credential.
 */
@RestController
class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private final TokenService tokenService;

    TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/auth/token")
    ResponseEntity<Object> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request) {
        Optional<Credentials> credentials = basicCredentials(authorization);
        if (credentials.isEmpty()
                || !tokenService.credentialsMatch(
                        credentials.get().clientId(), credentials.get().clientSecret())) {
            log.info("token request rejected: missing or invalid client credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"appstore\"")
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(ProblemDetails.unauthorized(request.getRequestURI()));
        }
        TokenService.IssuedToken token = tokenService.issue(credentials.get().clientId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TokenResponse(token.value(), "Bearer", token.expiresInSeconds()));
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
