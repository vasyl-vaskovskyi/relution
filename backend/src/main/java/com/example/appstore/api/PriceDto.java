package com.example.appstore.api;

import com.example.appstore.catalog.Price;

/** A price in responses. {@code amount} is a decimal string, so prices are never distorted by floating point. */
public record PriceDto(String amount, String currency, String formatted) {

    static PriceDto from(Price price) {
        if (price == null) {
            return null;
        }
        String amount = price.amount() == null ? null : price.amount().toPlainString();
        return new PriceDto(amount, price.currency(), price.formatted());
    }
}
