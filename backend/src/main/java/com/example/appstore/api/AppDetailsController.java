package com.example.appstore.api;

import com.example.appstore.catalog.AppDetailsService;
import com.example.appstore.catalog.Platform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/apps/{id}}. Parameter constraints use Spring MVC's built-in method validation, so violations become
 * 400 responses ({@code docs/api/README.md}).
 */
@RestController
@RequestMapping("/api/v1/apps")
@Tag(name = "Apps")
class AppDetailsController {

    private final AppDetailsService detailsService;

    AppDetailsController(AppDetailsService detailsService) {
        this.detailsService = detailsService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get app details",
            description = "Apple decides which language it serves; `storefront.language` tells which one it did.")
    @ApiResponse(responseCode = "200", description = "The app's details in the requested storefront")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid parameters (`invalid-request`, see `errors[]`) or no store for this country"
                    + " (`unsupported-storefront`)",
            content =
                    @Content(
                            mediaType = OpenApiConfiguration.PROBLEM_JSON,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The app doesn't exist, isn't available in this storefront, or isn't accessible"
                    + " (`app-not-found`)",
            content =
                    @Content(
                            mediaType = OpenApiConfiguration.PROBLEM_JSON,
                            schema = @Schema(implementation = ProblemDetail.class)))
    AppDetailsResponse details(
            @Parameter(description = "App id (Apple adamId), 1–15 digits", example = "361309726")
                    @PathVariable
                    @Pattern(regexp = "\\d{1,15}", message = "must be 1 to 15 digits")
                    String id,
            @Parameter(description = "Two-letter storefront country code, case-insensitive", example = "de")
                    @RequestParam
                    @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter country code")
                    String cc,
            @Parameter(description = "Requested language tag such as `de` or `de-DE`", example = "de")
                    @RequestParam
                    @Pattern(
                            regexp = "[a-zA-Z]{2}([-_][a-zA-Z]{2})?",
                            message = "must be a language tag like de or de-DE")
                    String l,
            @Parameter(
                            description = "`ios` or `mac`; for universal apps, selects iOS or Mac metadata",
                            schema = @Schema(allowableValues = {"ios", "mac"}))
                    @RequestParam(defaultValue = "ios")
                    @Pattern(regexp = "ios|mac", message = "must be ios or mac")
                    String platform) {
        Platform requested = Platform.valueOf(platform.toUpperCase(Locale.ROOT));
        return AppDetailsResponse.from(detailsService.details(id, cc, l, requested));
    }
}
