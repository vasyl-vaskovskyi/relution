package com.example.appstore.integration.apple;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Enables {@code @Retryable} on the Apple clients ({@code docs/architecture/caching-resilience.md#retry}). */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
class AppleResilienceConfiguration {}
