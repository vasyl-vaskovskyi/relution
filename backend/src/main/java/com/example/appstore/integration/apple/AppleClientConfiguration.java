package com.example.appstore.integration.apple;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@code RestClient}s for the Apple APIs, built from Boot's builder (observations, message converters) with the JDK
 * request factory and the configured timeouts. The JDK factory is fixed because the timeout classification depends on
 * its exception types ({@code docs/architecture/caching-resilience.md}).
 */
@Configuration(proxyBeanMethods = false)
class AppleClientConfiguration {

    static final String SEARCH_REST_CLIENT = "itunesSearchRestClient";

    @Bean
    @Qualifier(SEARCH_REST_CLIENT)
    RestClient itunesSearchRestClient(RestClient.Builder builder, AppleProperties properties) {
        return builder.clone()
                .baseUrl(properties.search().url().toString())
                .requestFactory(requestFactory(properties))
                .build();
    }

    static ClientHttpRequestFactory requestFactory(AppleProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(
                        properties.timeout().connect(), properties.timeout().read());
        return ClientHttpRequestFactoryBuilder.jdk().build(settings);
    }
}
