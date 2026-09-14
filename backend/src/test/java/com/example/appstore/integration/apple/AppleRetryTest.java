package com.example.appstore.integration.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.catalog.Platform;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamRateLimitedException.Reason;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retry through the real Spring proxy ({@code @EnableResilientMethods}): attempts are counted by a request interceptor,
 * so the JDK client's own hidden retry is not included.
 */
class AppleRetryTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void searchRetriesConnectionFailuresTwice() throws IOException {
        String refused = "http://127.0.0.1:" + closedPort();

        runner(refused, 20).run(context -> {
            ItunesSearchClient client = context.getBean(ItunesSearchClient.class);

            assertThatThrownBy(() -> client.search("pages", "de", 5)).isInstanceOf(UpstreamConnectException.class);
            assertThat(attempts).hasValue(3);
        });
    }

    @Test
    void lookupRetriesConnectionFailuresTwice() throws IOException {
        String refused = "http://127.0.0.1:" + closedPort();

        runner(refused, 20).run(context -> {
            MzLookupClient client = context.getBean(MzLookupClient.class);

            assertThatThrownBy(() -> client.lookup("1", "de", "de", Platform.IOS))
                    .isInstanceOf(UpstreamConnectException.class);
            assertThat(attempts).hasValue(3);
        });
    }

    @Test
    void readTimeoutsAreNotRetried() throws IOException {
        List<Socket> held = new CopyOnWriteArrayList<>();
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread.ofVirtual().start(() -> {
                while (!silent.isClosed()) {
                    try {
                        held.add(silent.accept()); // accept and never answer
                    } catch (IOException ignored) {
                        // server closed at the end of the test
                    }
                }
            });

            runner("http://127.0.0.1:" + silent.getLocalPort(), 20).run(context -> {
                ItunesSearchClient client = context.getBean(ItunesSearchClient.class);

                assertThatThrownBy(() -> client.search("pages", "de", 5))
                        .isInstanceOf(UpstreamReadTimeoutException.class);
                assertThat(attempts).hasValue(1);
            });
        } finally {
            for (Socket socket : held) {
                socket.close();
            }
        }
    }

    @Test
    void everyAttemptTakesABudgetPermit() throws IOException {
        String refused = "http://127.0.0.1:" + closedPort();

        runner(refused, 2).run(context -> {
            ItunesSearchClient client = context.getBean(ItunesSearchClient.class);

            // two attempts use both permits; the third attempt finds the budget empty and ends the retries
            assertThatThrownBy(() -> client.search("pages", "de", 5))
                    .isInstanceOfSatisfying(
                            UpstreamRateLimitedException.class,
                            e -> assertThat(e.reason()).isEqualTo(Reason.BUDGET));
            assertThat(attempts).hasValue(2);
        });
    }

    private ApplicationContextRunner runner(String baseUrl, int budgetPerMinute) {
        AppleProperties properties = new AppleProperties(
                new AppleProperties.Search(URI.create(baseUrl), budgetPerMinute),
                new AppleProperties.Lookup(URI.create(baseUrl)),
                new AppleProperties.Timeout(Duration.ofMillis(500), Duration.ofMillis(300)),
                new AppleProperties.Retry(2, Duration.ofSeconds(8)),
                new AppleProperties.RetryAfter(Duration.ofMinutes(5)));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(AppleClientConfiguration.requestFactory(properties))
                .requestInterceptor((request, body, execution) -> {
                    attempts.incrementAndGet();
                    return execution.execute(request, body);
                })
                .build();
        SearchBudget budget = new SearchBudget(budgetPerMinute, Clock.systemUTC());
        return new ApplicationContextRunner()
                .withPropertyValues("appstore.apple.retry.max=2", "appstore.apple.retry.timeout=PT8S")
                .withUserConfiguration(AppleResilienceConfiguration.class)
                .withBean(ItunesSearchClient.class, () -> new ItunesSearchClient(restClient, JSON, budget))
                .withBean(MzLookupClient.class, () -> new MzLookupClient(restClient, JSON));
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
