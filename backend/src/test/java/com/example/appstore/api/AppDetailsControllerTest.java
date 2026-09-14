package com.example.appstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appstore.catalog.AppDetails;
import com.example.appstore.catalog.AppDetailsService;
import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.DeviceFamily;
import com.example.appstore.catalog.Links;
import com.example.appstore.catalog.Platform;
import com.example.appstore.catalog.Price;
import com.example.appstore.catalog.Rating;
import com.example.appstore.catalog.Storefront;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Web layer only; authentication and authorization are covered by the auth package's SecurityIntegrationTest. */
@WebMvcTest(AppDetailsController.class)
@WithMockUser(authorities = "SCOPE_apps:read")
class AppDetailsControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AppDetailsService detailsService;

    @Test
    void returnsTheContractShape() {
        when(detailsService.details("361309726", "de", "de", Platform.IOS)).thenReturn(pages(Platform.IOS));

        assertThat(mvc.get().uri("/api/v1/apps/361309726").param("cc", "de").param("l", "de"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        { "id": "361309726", "name": "Pages", "kind": "IOS_APP", "subtitle": "Documents",
                          "developer": "Apple", "seller": "Apple Distribution International",
                          "bundleId": "com.apple.Pages", "watchBundleId": null, "version": "15.3",
                          "minimumOsVersion": "18.0", "firstReleaseDate": "2010-05-26",
                          "price": { "amount": "0.00", "currency": null, "formatted": "0,00 €" },
                          "platforms": ["IPHONE", "IPAD", "MAC"], "universal": true,
                          "description": "Write.", "whatsNew": null,
                          "iconUrl": "https://is1-ssl.mzstatic.com/512x512bb.png", "genres": ["Produktivität"],
                          "rating": { "average": 4.5, "count": 146780 },
                          "links": { "store": "https://apps.apple.com/de/app/id361309726", "support": null,
                                     "privacyPolicy": null },
                          "storefront": { "cc": "de", "language": "de-de", "platform": "ios" } }
                        """);
    }

    @Test
    void passesTheMacPlatformAndTheLanguageAsGiven() {
        when(detailsService.details("361309726", "DE", "de_DE", Platform.MAC)).thenReturn(pages(Platform.MAC));

        assertThat(mvc.get()
                        .uri("/api/v1/apps/361309726")
                        .param("cc", "DE")
                        .param("l", "de_DE")
                        .param("platform", "mac"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.storefront.platform")
                .isEqualTo("mac");
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "abc, de, de, ios", // id not numeric
                "1234567890123456, de, de, ios", // id longer than 15 digits
                "1, null, de, ios", // missing cc
                "1, d1, de, ios",
                "1, de, null, ios", // missing l
                "1, de, deu, ios",
                "1, de, de-DEU, ios",
                "1, de, de, tv", // unknown platform
                "1, de, de, IOS"
            })
    void rejectsInvalidParametersWithoutCallingTheService(String id, String cc, String l, String platform) {
        var request = mvc.get().uri("/api/v1/apps/" + id).param("platform", platform);
        if (cc != null) {
            request = request.param("cc", cc);
        }
        if (l != null) {
            request = request.param("l", l);
        }

        assertThat(request).hasStatus(HttpStatus.BAD_REQUEST);
        verify(detailsService, never()).details(anyString(), anyString(), anyString(), any());
    }

    private static AppDetails pages(Platform platform) {
        return new AppDetails(
                "361309726",
                "Pages",
                AppKind.IOS_APP,
                "Documents",
                "Apple",
                "Apple Distribution International",
                "com.apple.Pages",
                null,
                "15.3",
                "18.0",
                LocalDate.of(2010, 5, 26),
                new Price(new BigDecimal("0.00"), null, "0,00 €"),
                List.of(DeviceFamily.IPHONE, DeviceFamily.IPAD, DeviceFamily.MAC),
                true,
                "Write.",
                null,
                "https://is1-ssl.mzstatic.com/512x512bb.png",
                List.of("Produktivität"),
                new Rating(4.5, 146780L),
                new Links("https://apps.apple.com/de/app/id361309726", null, null),
                new Storefront("de", "de-de", platform));
    }
}
