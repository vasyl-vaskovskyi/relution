package com.example.appstore.observability;

/**
 * Metric names and tag keys, defined once. Tag values must come from small fixed sets: never terms, country codes, ids
 * or client ids ({@code docs/operations/observability.md}).
 */
public final class MetricNames {

    /** Timer, one sample per logical Apple call or local short-circuit; tags {@code api}, {@code outcome}. */
    public static final String APPLE_REQUESTS = "appstore.apple.requests";

    /** Counter for drift signals; tags {@code api}, {@code field}. */
    public static final String APPLE_MAPPING_MISSING_FIELD = "appstore.apple.mapping.missing_field";

    /** Counter for allowlist maintenance signals; tag {@code direction=missing|outdated}. */
    public static final String STOREFRONT_ALLOWLIST_MISMATCH = "appstore.storefront.allowlist.mismatch";

    public static final String TAG_API = "api";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_FIELD = "field";

    private MetricNames() {}
}
