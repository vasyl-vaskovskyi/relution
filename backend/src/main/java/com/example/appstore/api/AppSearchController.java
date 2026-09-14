package com.example.appstore.api;

import com.example.appstore.catalog.AppSearchService;
import com.example.appstore.catalog.SearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Apps")
class AppSearchController {

    private final AppSearchService searchService;

    AppSearchController(AppSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(
            summary = "Search apps in a storefront",
            description = "No pagination: Apple ignores offsets, so `limit` is the only size control.")
    @ApiResponse(responseCode = "200", description = "Matching apps; `count` equals the number of items")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid parameters (`invalid-request`, see `errors[]`) or no store for this country"
                    + " (`unsupported-storefront`)",
            content =
                    @Content(
                            mediaType = OpenApiConfiguration.PROBLEM_JSON,
                            schema = @Schema(implementation = ProblemDetail.class)))
    AppSearchResponse search(
            @Parameter(description = "Search term; trimmed, 1–100 characters, not blank")
                    @RequestParam
                    @NotBlank(message = "must not be blank")
                    @Size(max = SearchQuery.MAX_TERM_LENGTH, message = "must be at most 100 characters")
                    String term,
            @Parameter(description = "Two-letter storefront country code, case-insensitive", example = "de")
                    @RequestParam
                    @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter country code")
                    String cc,
            @Parameter(description = "Maximum number of results, 1–50")
                    @RequestParam(defaultValue = "25")
                    @Min(value = 1, message = "must be between 1 and 50")
                    @Max(value = SearchQuery.MAX_LIMIT, message = "must be between 1 and 50")
                    int limit) {
        return AppSearchResponse.from(searchService.search(term, cc, limit));
    }
}
