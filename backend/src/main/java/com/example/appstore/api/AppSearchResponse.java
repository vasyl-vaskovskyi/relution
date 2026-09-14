package com.example.appstore.api;

import com.example.appstore.catalog.AppSearchResult;
import com.example.appstore.catalog.AppSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Response of {@code GET /api/v1/apps} ({@code docs/api/README.md}). */
public record AppSearchResponse(List<Item> items, int count, Storefront storefront) {

    public record Item(
            String id, String name, String developer, String iconUrl, String kind, PriceDto price, RatingDto rating) {}

    /** Named explicitly: the simple name clashes with {@link AppDetailsResponse.Storefront} in OpenAPI. */
    @Schema(name = "SearchStorefront")
    public record Storefront(String cc) {}

    static AppSearchResponse from(AppSearchResult result) {
        List<Item> items = result.items().stream().map(AppSearchResponse::item).toList();
        return new AppSearchResponse(items, items.size(), new Storefront(result.countryCode()));
    }

    private static Item item(AppSummary app) {
        return new Item(
                app.id(),
                app.name(),
                app.developer(),
                app.iconUrl(),
                app.kind().name(),
                PriceDto.from(app.price()),
                RatingDto.from(app.rating()));
    }
}
