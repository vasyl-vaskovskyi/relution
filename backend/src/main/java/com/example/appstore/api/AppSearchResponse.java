package com.example.appstore.api;

import com.example.appstore.catalog.AppSearchResult;
import com.example.appstore.catalog.AppSummary;
import com.example.appstore.catalog.Price;
import com.example.appstore.catalog.Rating;
import java.util.List;

/** Response of {@code GET /api/v1/apps} ({@code docs/api/README.md}). */
public record AppSearchResponse(List<Item> items, int count, Storefront storefront) {

    public record Item(
            String id, String name, String developer, String iconUrl, String kind, PriceDto price, RatingDto rating) {}

    /** {@code amount} is a decimal string, so prices are never distorted by floating point. */
    public record PriceDto(String amount, String currency, String formatted) {}

    public record RatingDto(Double average, Long count) {}

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
                price(app.price()),
                rating(app.rating()));
    }

    private static PriceDto price(Price price) {
        if (price == null) {
            return null;
        }
        String amount = price.amount() == null ? null : price.amount().toPlainString();
        return new PriceDto(amount, price.currency(), price.formatted());
    }

    private static RatingDto rating(Rating rating) {
        return rating == null ? null : new RatingDto(rating.average(), rating.count());
    }
}
