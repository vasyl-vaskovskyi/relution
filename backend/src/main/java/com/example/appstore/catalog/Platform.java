package com.example.appstore.catalog;

/**
 * The platform whose metadata the details endpoint returns. For universal apps it selects iOS or Mac metadata
 * (ADR-0029). The Apple adapter translates it to Apple's channel names.
 */
public enum Platform {
    IOS,
    MAC
}
