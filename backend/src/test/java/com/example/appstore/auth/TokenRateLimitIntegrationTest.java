package com.example.appstore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The failed-attempt limit on {@code POST /auth/token} through the real filter chain (ADR-0049). A small limit and a
 * long window keep the refill out of the test; every request comes from 127.0.0.1, so the whole scenario is one
 * ordered test in its own context.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "appstore.auth.limit.failures=3", "appstore.auth.limit.window=PT1H"})
class TokenRateLimitIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    AuthProperties properties;

    @Autowired
    MeterRegistry registry;

    // the JDK client never retries; Apache HttpClient would wait out Retry-After on a 429
    private final RestClient http = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

    record Result(int status, HttpHeaders headers, String body) {

        JsonNode json() {
            return JSON.readTree(body);
        }
    }

    @Test
    void failedAttemptsAreLimitedWhileValidTokensKeepWorkingUnderTheLimit() {
        String valid = basic(properties.client().id(), properties.client().secret());
        String wrong = basic(properties.client().id(), "wrong-secret");

        // successes don't use up the budget, even between failures
        for (int i = 0; i < 10; i++) {
            assertThat(post(valid).status()).as("valid request %d", i + 1).isEqualTo(200);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(post(wrong).status()).as("failure %d", i + 1).isEqualTo(401);
            assertThat(post(valid).status()).as("valid after failure %d", i + 1).isEqualTo(200);
        }
        assertThat(post(wrong).status()).as("failure 3").isEqualTo(401);

        // the budget of three failures is used up: checked before the credentials, so valid ones are refused too
        for (String authorization : new String[] {wrong, valid, null}) {
            Result result = post(authorization);

            assertThat(result.status()).isEqualTo(429);
            assertThat(MediaType.parseMediaType(result.headers().getFirst(HttpHeaders.CONTENT_TYPE)))
                    .matches(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith);
            assertThat(Long.parseLong(result.headers().getFirst(HttpHeaders.RETRY_AFTER)))
                    .isBetween(1L, 1200L);
            assertThat(result.headers().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
            assertThat(result.json().path("type").asString()).isEqualTo("urn:appstore:problem:too-many-requests");
            assertThat(result.json().path("status").asInt()).isEqualTo(429);
            assertThat(result.json().path("instance").asString()).isEqualTo("/auth/token");
            assertThat(result.json().path("correlationId").asString()).isNotBlank();
            assertThat(result.body())
                    .doesNotContain(
                            properties.client().secret(), properties.client().id(), "127.0.0.1", "Exception");
        }

        assertThat(count("issued")).isEqualTo(12);
        assertThat(count("rejected")).isEqualTo(3);
        assertThat(count("rate_limited")).isEqualTo(3);
    }

    private double count(String outcome) {
        return registry.get("appstore.auth.token.requests")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private static String basic(String id, String secret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(id, secret, StandardCharsets.UTF_8);
    }

    private Result post(String authorization) {
        return http.post()
                .uri("http://127.0.0.1:" + port + "/auth/token")
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
