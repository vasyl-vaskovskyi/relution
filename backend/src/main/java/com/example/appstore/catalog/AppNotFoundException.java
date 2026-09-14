package com.example.appstore.catalog;

/**
 * The lookup found no app for the id in the requested storefront. Not an upstream failure; maps to 404
 * {@code app-not-found}.
 */
public final class AppNotFoundException extends RuntimeException {

    private final String id;

    public AppNotFoundException(String id) {
        super("App " + id + " not found, not available in this storefront, or not accessible");
        this.id = id;
    }

    public String id() {
        return id;
    }
}
