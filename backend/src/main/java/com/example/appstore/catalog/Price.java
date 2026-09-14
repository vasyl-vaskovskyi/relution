package com.example.appstore.catalog;

import java.math.BigDecimal;

/**
 * A price as Apple reports it. Every component may be {@code null}: the lookup API has no currency, and {@code
 * formatted} is localized by Apple.
 */
public record Price(BigDecimal amount, String currency, String formatted) {}
