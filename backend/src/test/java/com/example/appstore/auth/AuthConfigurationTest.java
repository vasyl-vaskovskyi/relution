package com.example.appstore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Fail-fast: the application must not start without valid auth settings ({@code docs/operations/configuration.md}). */
class AuthConfigurationTest {

    private static final String VALID_SECRET = Base64.getEncoder()
            .encodeToString("a-32-byte-or-longer-signing-key-for-tests".getBytes(StandardCharsets.UTF_8));
    private static final String SHORT_SECRET =
            Base64.getEncoder().encodeToString("only-16-bytes!!!".getBytes(StandardCharsets.UTF_8));

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "appstore.auth.jwt.issuer=appstore",
                    "appstore.auth.jwt.audience=appstore-api",
                    "appstore.auth.jwt.ttl=PT15M",
                    "appstore.auth.client.id=client",
                    "appstore.auth.client.secret=client-secret");

    @Test
    void validSettingsCreateTheEncoderAndDecoder() {
        runner.withPropertyValues("appstore.auth.jwt.secret=" + VALID_SECRET).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
            assertThat(context.getBean(AuthProperties.class).toString())
                    .doesNotContain(VALID_SECRET)
                    .doesNotContain("client-secret");
        });
    }

    @Test
    void aMissingSecretStopsTheContext() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("appstore.auth.jwt.secret=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("appstore.auth.jwt.secret=" + VALID_SECRET, "appstore.auth.client.secret=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aSecretThatIsNotBase64OrTooShortStopsTheContextWithoutRevealingIt() {
        runner.withPropertyValues("appstore.auth.jwt.secret=%%not-base64%%").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure()))
                    .contains("must be Base64")
                    .doesNotContain("not-base64");
        });
        runner.withPropertyValues("appstore.auth.jwt.secret=" + SHORT_SECRET).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure()))
                    .contains("at least 32 bytes")
                    .doesNotContain(SHORT_SECRET);
        });
    }

    @Test
    void aTokenLifetimeAboveOneHourStopsTheContext() {
        runner.withPropertyValues("appstore.auth.jwt.secret=" + VALID_SECRET, "appstore.auth.jwt.ttl=PT2H")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    @Import(JwtConfiguration.class)
    static class Config {}
}
