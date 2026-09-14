package com.example.appstore.catalog.storefront;

import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides whether a country code may reach Apple, and records allowlist maintenance signals
 * ({@code docs/architecture/error-handling.md}, ADR-0008, ADR-0043). Clients never learn how the allowlist is
 * maintained.
 */
@Component
public class StorefrontPolicy {

    private static final Logger log = LoggerFactory.getLogger(StorefrontPolicy.class);
    private static final Pattern TWO_LETTERS = Pattern.compile("[a-z]{2}");
    private static final Set<String> ISO_COUNTRIES = Arrays.stream(Locale.getISOCountries())
            .map(code -> code.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    private final Counter missingFromAllowlist;
    private final Counter allowlistOutdated;

    public StorefrontPolicy(MeterRegistry registry) {
        this.missingFromAllowlist = Counter.builder(MetricNames.STOREFRONT_ALLOWLIST_MISMATCH)
                .tag(MetricNames.TAG_DIRECTION, "missing")
                .description("Valid ISO country codes rejected because they are not on the storefront allowlist")
                .register(registry);
        this.allowlistOutdated = Counter.builder(MetricNames.STOREFRONT_ALLOWLIST_MISMATCH)
                .tag(MetricNames.TAG_DIRECTION, "outdated")
                .description("Allowlisted storefronts that Apple rejected or replaced")
                .register(registry);
    }

    /**
     * Returns the lower-case country code if Apple may be called with it.
     *
     * @throws InvalidCountryCodeException if the value is not a country code
     * @throws UnsupportedStorefrontException if it is a country without an App Store storefront
     */
    public String requireSupported(String countryCode) {
        String code = countryCode == null ? "" : countryCode.strip().toLowerCase(Locale.ROOT);
        if (!TWO_LETTERS.matcher(code).matches()) {
            throw new InvalidCountryCodeException();
        }
        if (SupportedStorefronts.CODES.contains(code)) {
            return code;
        }
        if (ISO_COUNTRIES.contains(code)) {
            log.warn("storefront missing from allowlist, verify the list: {}", code);
            missingFromAllowlist.increment();
            throw new UnsupportedStorefrontException(code);
        }
        throw new InvalidCountryCodeException();
    }

    /**
     * Called when Apple rejects or replaces a storefront that passed {@link #requireSupported}: the allowlist is out of
     * date. Only the maintenance signal is recorded here; the caller still fails the request.
     */
    public void reportRejectedByApple(String countryCode) {
        log.error("storefront allowlist outdated: {}", countryCode);
        allowlistOutdated.increment();
    }
}
