package com.example.appstore.api;

import com.example.appstore.catalog.AppSearchService;
import com.example.appstore.catalog.SearchQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/apps}. Parameter constraints use Spring MVC's built-in method validation, so violations become 400
 * responses ({@code docs/api/README.md}).
 */
@RestController
@RequestMapping("/api/v1/apps")
class AppSearchController {

    private final AppSearchService searchService;

    AppSearchController(AppSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    AppSearchResponse search(
            @RequestParam
                    @NotBlank(message = "must not be blank")
                    @Size(max = SearchQuery.MAX_TERM_LENGTH, message = "must be at most 100 characters")
                    String term,
            @RequestParam @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter country code") String cc,
            @RequestParam(defaultValue = "25")
                    @Min(value = 1, message = "must be between 1 and 50")
                    @Max(value = SearchQuery.MAX_LIMIT, message = "must be between 1 and 50")
                    int limit) {
        return AppSearchResponse.from(searchService.search(term, cc, limit));
    }
}
