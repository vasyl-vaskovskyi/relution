package com.example.appstore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.observability.CorrelationId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authentication and authorization through the real filter chains on both ports ({@code docs/architecture/security.md},
 * ADR-0034, ADR-0044). No test here reaches Apple.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class SecurityIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Value("${local.management.port}")
    int managementPort;

    @Autowired
    AuthProperties properties;

    private final RestClient http = RestClient.create();

    record Result(int status, HttpHeaders headers, String body) {

        JsonNode json() {
            return JSON.readTree(body);
        }
    }

    @Test
    void validClientCredentialsGetAShortLivedToken() {
        Result result = post(
                "/auth/token",
                basic(properties.client().id(), properties.client().secret()));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.headers().getCacheControl()).contains("no-store");
        assertThat(result.json().path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(result.json().path("expiresIn").asLong()).isEqualTo(900);
        assertThat(result.json().path("accessToken").asString()).isNotBlank();
    }

    @Test
    void invalidOrMissingClientCredentialsAreUnauthorized() {
        for (String authorization : new String[] {
            basic(properties.client().id(), "wrong"),
            basic("other", properties.client().secret()),
            null,
            "Basic not-base64!"
        }) {
            Result result = post("/auth/token", authorization);

            assertThat(result.status()).as("Authorization: %s", authorization).isEqualTo(401);
            assertProblem(result, "unauthorized");
            assertThat(result.headers().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Basic");
            assertThat(result.body()).doesNotContain(properties.client().secret());
        }
    }

    @Test
    void anInvalidBearerTokenOnTheTokenEndpointIsRejectedByTheResourceServer() {
        // the bearer filter runs on every path of the public chain, before the token controller
        Result result = post("/auth/token", "Bearer something");

        assertThat(result.status()).isEqualTo(401);
        assertProblem(result, "unauthorized");
        assertThat(result.headers().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void apiWithoutTokenIsUnauthorizedWithAProblemBody() {
        Result result = get(port, "/api/v1/apps?term=pages&cc=de", null);

        assertThat(result.status()).isEqualTo(401);
        assertProblem(result, "unauthorized");
        assertThat(result.headers().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(result.json().path("correlationId").asString())
                .isEqualTo(result.headers().getFirst(CorrelationId.HEADER));
    }

    @Test
    void aValidTokenReachesTheController() {
        // a missing cc is rejected by the controller, so this proves authentication without calling Apple
        Result result = get(port, "/api/v1/apps?term=pages", bearer(issuedToken()));

        assertThat(result.status()).isEqualTo(400);
        assertProblem(result, "invalid-request");
    }

    @Test
    void aTokenWithoutTheScopeIsForbidden() {
        String token = token(claims().claim("scope", "other:read").build(), signingKey());

        Result result = get(port, "/api/v1/apps?term=pages&cc=de", bearer(token));

        assertThat(result.status()).isEqualTo(403);
        assertProblem(result, "forbidden");
    }

    @Test
    void expiredWrongIssuerWrongAudienceAndForeignKeyTokensAreUnauthorized() {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        List<String> tokens = List.of(
                token(
                        claims().issuedAt(past)
                                .expiresAt(past.plus(Duration.ofMinutes(15)))
                                .build(),
                        signingKey()),
                token(claims().issuer("someone-else").build(), signingKey()),
                token(claims().audience(List.of("another-api")).build(), signingKey()),
                token(
                        claims().build(),
                        JwtConfiguration.signingKey(Base64.getEncoder()
                                .encodeToString(
                                        "a-different-key-with-more-than-32-bytes!".getBytes(StandardCharsets.UTF_8)))),
                "not-a-jwt");

        for (String token : tokens) {
            Result result = get(port, "/api/v1/apps?term=pages&cc=de", bearer(token));

            assertThat(result.status()).isEqualTo(401);
            assertProblem(result, "unauthorized");
        }
    }

    @Test
    void everythingElseOnThePublicPortIsDenied() {
        assertThat(get(port, "/actuator/env", null).status()).isEqualTo(401);
        assertThat(get(port, "/anything", null).status()).isEqualTo(401);
        Result withToken = get(port, "/actuator/env", bearer(issuedToken()));
        assertThat(withToken.status()).isEqualTo(403);
        assertThat(withToken.body()).doesNotContain("appstore.auth");
    }

    @Test
    void probesAndApiDocsNeedNoToken() {
        assertThat(get(port, "/livez", null).status()).isEqualTo(200);
        assertThat(get(port, "/readyz", null).status()).isEqualTo(200);
        assertThat(get(port, "/v3/api-docs", null).status()).isEqualTo(200);
    }

    @Test
    void theManagementPortIsOpenButExposesOnlyTheListedEndpoints() {
        assertThat(get(managementPort, "/actuator/health", null).status()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/info", null).status()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/env", null).status()).isEqualTo(404);
        assertThat(get(managementPort, "/anything", null).status()).isEqualTo(404);
    }

    private void assertProblem(Result result, String type) {
        assertThat(MediaType.parseMediaType(result.headers().getFirst(HttpHeaders.CONTENT_TYPE)))
                .matches(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith);
        assertThat(result.json().path("type").asString()).isEqualTo("urn:appstore:problem:" + type);
        assertThat(result.body()).doesNotContain("Exception", "at org.");
    }

    private String issuedToken() {
        return post(
                        "/auth/token",
                        basic(properties.client().id(), properties.client().secret()))
                .json()
                .path("accessToken")
                .asString();
    }

    private JwtClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .audience(List.of(properties.jwt().audience()))
                .subject(properties.client().id())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claim("scope", TokenService.SCOPE);
    }

    private SecretKey signingKey() {
        return JwtConfiguration.signingKey(properties.jwt().secret());
    }

    private static String token(JwtClaimsSet claims, SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String basic(String id, String secret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(id, secret, StandardCharsets.UTF_8);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private Result get(int targetPort, String path, String authorization) {
        return http.get()
                .uri("http://127.0.0.1:" + targetPort + path)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .exchange((request, response) -> toResult(response));
    }

    private Result post(String path, String authorization) {
        return http.post()
                .uri("http://127.0.0.1:" + port + path)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .exchange((request, response) -> toResult(response));
    }

    private static Result toResult(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        return new Result(
                response.getStatusCode().value(),
                response.getHeaders(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }
}
