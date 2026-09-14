package com.example.appstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appstore.catalog.AppKind;
import com.example.appstore.catalog.AppSearchResult;
import com.example.appstore.catalog.AppSearchService;
import com.example.appstore.catalog.AppSummary;
import com.example.appstore.catalog.Price;
import com.example.appstore.catalog.Rating;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AppSearchController.class)
class AppSearchControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AppSearchService searchService;

    @Test
    void returnsTheContractShape() {
        AppSummary pages = new AppSummary(
                "361309726",
                "Pages",
                "Apple",
                "https://is1-ssl.mzstatic.com/512x512bb.jpg",
                AppKind.IOS_APP,
                new Price(new BigDecimal("0.00"), "EUR", "Gratis"),
                new Rating(4.1, 12345L));
        when(searchService.search("pages", "DE", 25)).thenReturn(new AppSearchResult(List.of(pages), "de"));

        assertThat(mvc.get().uri("/api/v1/apps").param("term", "pages").param("cc", "DE"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "items": [ { "id": "361309726", "name": "Pages", "developer": "Apple",
                            "iconUrl": "https://is1-ssl.mzstatic.com/512x512bb.jpg", "kind": "IOS_APP",
                            "price": { "amount": "0.00", "currency": "EUR", "formatted": "Gratis" },
                            "rating": { "average": 4.1, "count": 12345 } } ],
                          "count": 1, "storefront": { "cc": "de" } }
                        """);
    }

    @Test
    void passesAnExplicitLimit() {
        when(searchService.search("pages", "de", 5)).thenReturn(new AppSearchResult(List.of(), "de"));

        assertThat(mvc.get()
                        .uri("/api/v1/apps")
                        .param("term", "pages")
                        .param("cc", "de")
                        .param("limit", "5"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{ \"items\": [], \"count\": 0 }");
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "null, de, 25", // missing term
                "'  ', de, 25", // blank term
                "pages, null, 25", // missing cc
                "pages, d1, 25", // cc not two letters
                "pages, deu, 25",
                "pages, de, 0", // limit below 1
                "pages, de, 51", // limit above 50
                "pages, de, many" // limit not a number
            })
    void rejectsInvalidParametersWithoutCallingTheService(String term, String cc, String limit) {
        var request = mvc.get().uri("/api/v1/apps").param("limit", limit);
        if (term != null) {
            request = request.param("term", term);
        }
        if (cc != null) {
            request = request.param("cc", cc);
        }

        assertThat(request).hasStatus(HttpStatus.BAD_REQUEST);
        verify(searchService, never()).search(anyString(), anyString(), anyInt());
    }

    @Test
    void rejectsATermLongerThan100Characters() {
        assertThat(mvc.get().uri("/api/v1/apps").param("term", "x".repeat(101)).param("cc", "de"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
