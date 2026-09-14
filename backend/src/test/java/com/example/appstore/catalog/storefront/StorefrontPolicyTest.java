package com.example.appstore.catalog.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.appstore.observability.MetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class StorefrontPolicyTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StorefrontPolicy policy = new StorefrontPolicy(registry);

    @Test
    void allowlistHasTheDocumented175Codes() {
        assertThat(SupportedStorefronts.CODES).hasSize(175).contains("de", "us", "gb", "jp", "xk", "nr");
        assertThat(SupportedStorefronts.CODES).doesNotContain("cu", "kp", "ir", "sy", "aq", "cw", "gu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "DE", "De", " us ", "xk"})
    void allowlistedCodesPassNormalizedToLowerCase(String input) {
        assertThat(policy.requireSupported(input)).isEqualTo(input.strip().toLowerCase());
        assertThat(mismatches("missing")).isZero();
    }

    @Test
    void isoCountryWithoutStorefrontIsUnsupportedAndReportedAsMissing(CapturedOutput output) {
        assertThatThrownBy(() -> policy.requireSupported("CU"))
                .isInstanceOf(UnsupportedStorefrontException.class)
                .extracting(e -> ((UnsupportedStorefrontException) e).countryCode())
                .isEqualTo("cu");

        assertThat(mismatches("missing")).isEqualTo(1);
        assertThat(output).contains("storefront missing from allowlist, verify the list: cu");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"zz", "d", "deu", "1a", "d€"})
    void valuesThatAreNotCountryCodesAreInvalid(String input) {
        assertThatThrownBy(() -> policy.requireSupported(input)).isInstanceOf(InvalidCountryCodeException.class);
        assertThat(mismatches("missing")).isZero();
    }

    @Test
    void invalidInputIsNeverEchoed() {
        assertThatThrownBy(() -> policy.requireSupported("<script>"))
                .isInstanceOf(InvalidCountryCodeException.class)
                .hasMessageNotContaining("<script>");
    }

    @Test
    void appleRejectingAnAllowlistedStorefrontIsReportedAsOutdated(CapturedOutput output) {
        policy.reportRejectedByApple("de");

        assertThat(mismatches("outdated")).isEqualTo(1);
        assertThat(output).contains("storefront allowlist outdated: de");
    }

    private double mismatches(String direction) {
        return registry.get(MetricNames.STOREFRONT_ALLOWLIST_MISMATCH)
                .tag(MetricNames.TAG_DIRECTION, direction)
                .counter()
                .count();
    }
}
