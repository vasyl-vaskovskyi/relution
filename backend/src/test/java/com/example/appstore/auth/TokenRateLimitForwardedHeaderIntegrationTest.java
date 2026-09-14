package com.example.appstore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The failed-attempt limit can't be bypassed by sending a different {@code X-Forwarded-For} (ADR-0049). The context
 * pretends to run on Kubernetes, where Spring Boot would default {@code server.forward-headers-strategy} to
 * {@code native} and Tomcat would trust the header from 127.0.0.1; {@code application.yml} pins it to {@code none}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=0",
            "spring.main.cloud-platform=kubernetes",
            "appstore.auth.limit.failures=1",
            "appstore.auth.limit.window=PT1H"
        })
class TokenRateLimitForwardedHeaderIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    AuthProperties properties;

    // the JDK client never retries; Apache HttpClient would wait out Retry-After on a 429
    private final RestClient http = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

    @Test
    void aSpoofedForwardedForDoesNotChangeTheClientAddress() {
        String wrong = "Basic "
                + HttpHeaders.encodeBasicAuth(properties.client().id(), "wrong-secret", StandardCharsets.UTF_8);

        assertThat(post(wrong, "198.51.100.1")).isEqualTo(401);
        assertThat(post(wrong, "198.51.100.2")).isEqualTo(429);
        assertThat(post(wrong, "2001:db8:ffff::1")).isEqualTo(429);
    }

    private int post(String authorization, String forwardedFor) {
        return http.post()
                .uri("http://127.0.0.1:" + port + "/auth/token")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Forwarded-For", forwardedFor)
                .exchange((request, response) -> {
                    // read the problem body so the connection is released before the next request
                    response.getBody().readAllBytes();
                    return response.getStatusCode().value();
                });
    }
}
