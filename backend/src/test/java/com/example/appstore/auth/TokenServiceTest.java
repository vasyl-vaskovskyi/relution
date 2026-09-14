package com.example.appstore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenServiceTest {

    private static final AuthProperties PROPERTIES = new AuthProperties(
            new AuthProperties.Jwt(
                    Base64.getEncoder()
                            .encodeToString(
                                    "a-32-byte-or-longer-signing-key-for-tests".getBytes(StandardCharsets.UTF_8)),
                    "appstore",
                    "appstore-api",
                    Duration.ofMinutes(15)),
            new AuthProperties.Client("client", "client-secret"));

    private final JwtConfiguration configuration = new JwtConfiguration();
    private final TokenService service = new TokenService(configuration.jwtEncoder(PROPERTIES), PROPERTIES);

    @Test
    void onlyTheExactCredentialsMatch() {
        assertThat(service.credentialsMatch("client", "client-secret")).isTrue();
        assertThat(service.credentialsMatch("client", "client-secret ")).isFalse();
        assertThat(service.credentialsMatch("client", "wrong")).isFalse();
        assertThat(service.credentialsMatch("other", "client-secret")).isFalse();
        assertThat(service.credentialsMatch(null, null)).isFalse();
    }

    @Test
    void issuedTokensCarryTheDocumentedClaimsAndValidate() {
        TokenService.IssuedToken token = service.issue("client");

        Jwt jwt = configuration.jwtDecoder(PROPERTIES).decode(token.value());
        // getIssuer() expects a URL; our issuer is a plain name
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("appstore");
        assertThat(jwt.getAudience()).containsExactly("appstore-api");
        assertThat(jwt.getSubject()).isEqualTo("client");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("apps:read");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    void basicCredentialsAreParsedStrictly() {
        assertThat(TokenController.basicCredentials(basic("client:se:cret"))).hasValueSatisfying(credentials -> {
            assertThat(credentials.clientId()).isEqualTo("client");
            assertThat(credentials.clientSecret()).isEqualTo("se:cret");
        });
        assertThat(TokenController.basicCredentials("basic " + encode("client:secret")))
                .isPresent();
        assertThat(TokenController.basicCredentials(null)).isEmpty();
        assertThat(TokenController.basicCredentials("Bearer abc")).isEmpty();
        assertThat(TokenController.basicCredentials("Basic !!!")).isEmpty();
        assertThat(TokenController.basicCredentials(basic("no-colon"))).isEmpty();
    }

    private static String basic(String raw) {
        return "Basic " + encode(raw);
    }

    private static String encode(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
