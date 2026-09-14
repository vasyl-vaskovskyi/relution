package com.example.appstore.catalog.storefront;

import java.util.Set;

/**
 * The App Store storefronts both Apple APIs serve, as lower-case alpha-2 codes (ADR-0008).
 *
 * <p>Source: App Store Connect Help, "App Store localizations"
 * (https://developer.apple.com/help/app-store-connect/reference/app-information/app-store-localizations/), seen
 * 2026-09-13: 175 ISO alpha-3 codes converted to alpha-2; Kosovo {@code XKS} (not ISO) becomes {@code xk}. Refresh
 * procedure: {@code docs/operations/runbook.md}.
 */
public final class SupportedStorefronts {

    public static final Set<String> CODES = Set.of(
            "ae", "af", "ag", "ai", "al", "am", "ao", "ar", "at", "au", "az", "ba", "bb", "be", "bf", "bg", "bh", "bj",
            "bm", "bn", "bo", "br", "bs", "bt", "bw", "by", "bz", "ca", "cd", "cg", "ch", "ci", "cl", "cm", "cn", "co",
            "cr", "cv", "cy", "cz", "de", "dk", "dm", "do", "dz", "ec", "ee", "eg", "es", "fi", "fj", "fm", "fr", "ga",
            "gb", "gd", "ge", "gh", "gm", "gr", "gt", "gw", "gy", "hk", "hn", "hr", "hu", "id", "ie", "il", "in", "iq",
            "is", "it", "jm", "jo", "jp", "ke", "kg", "kh", "kn", "kr", "kw", "ky", "kz", "la", "lb", "lc", "lk", "lr",
            "lt", "lu", "lv", "ly", "ma", "md", "me", "mg", "mk", "ml", "mm", "mn", "mo", "mr", "ms", "mt", "mu", "mv",
            "mw", "mx", "my", "mz", "na", "ne", "ng", "ni", "nl", "no", "np", "nr", "nz", "om", "pa", "pe", "pg", "ph",
            "pk", "pl", "pt", "pw", "py", "qa", "ro", "rs", "ru", "rw", "sa", "sb", "sc", "se", "sg", "si", "sk", "sl",
            "sn", "sr", "st", "sv", "sz", "tc", "td", "th", "tj", "tm", "tn", "to", "tr", "tt", "tw", "tz", "ua", "ug",
            "us", "uy", "uz", "vc", "ve", "vg", "vn", "vu", "xk", "ye", "za", "zm", "zw");

    private SupportedStorefronts() {}
}
