package com.example.appstore.catalog;

import java.util.Objects;

/**
 * One search result. {@code id}, {@code name} and {@code kind} are always present; every other component may be
 * {@code null}.
 */
public record AppSummary(
        String id, String name, String developer, String iconUrl, AppKind kind, Price price, Rating rating) {

    public AppSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
    }
}
