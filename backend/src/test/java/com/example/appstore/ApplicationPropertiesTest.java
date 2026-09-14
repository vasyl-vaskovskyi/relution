package com.example.appstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.appstore.catalog.CacheProperties;
import com.example.appstore.integration.apple.AppleProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ApplicationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsDefaultsFromApplicationYml() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            AppleProperties apple = context.getBean(AppleProperties.class);
            assertThat(apple.search().url()).isEqualTo(URI.create("https://itunes.apple.com"));
            assertThat(apple.search().budget()).isEqualTo(20);
            assertThat(apple.lookup().url()).isEqualTo(URI.create("https://uclient-api.itunes.apple.com"));
            assertThat(apple.timeout().connect()).isEqualTo(Duration.ofSeconds(2));
            assertThat(apple.timeout().read()).isEqualTo(Duration.ofSeconds(5));
            assertThat(apple.retry().max()).isEqualTo(2);
            assertThat(apple.retry().timeout()).isEqualTo(Duration.ofSeconds(8));
            assertThat(apple.retryAfter().max()).isEqualTo(Duration.ofMinutes(5));

            CacheProperties cache = context.getBean(CacheProperties.class);
            assertThat(cache.search().ttl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(cache.search().size()).isEqualTo(1000);
            assertThat(cache.details().ttl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(cache.details().size()).isEqualTo(5000);
            assertThat(cache.notFound().ttl()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    void dashFreePropertyNamesBindToCamelCaseComponents() {
        runner.withPropertyValues("appstore.apple.retryafter.max=PT1M", "appstore.cache.notfound.ttl=PT5S")
                .run(context -> {
                    assertThat(context.getBean(AppleProperties.class)
                                    .retryAfter()
                                    .max())
                            .isEqualTo(Duration.ofMinutes(1));
                    assertThat(context.getBean(CacheProperties.class).notFound().ttl())
                            .isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void invalidValuesStopTheContext() {
        runner.withPropertyValues("appstore.apple.search.budget=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("appstore.apple.timeout.read=PT0S")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("appstore.cache.search.size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AppleProperties.class, CacheProperties.class})
    static class PropertiesConfig {}
}
