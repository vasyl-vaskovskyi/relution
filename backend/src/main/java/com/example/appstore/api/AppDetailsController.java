package com.example.appstore.api;

import com.example.appstore.catalog.AppDetailsService;
import com.example.appstore.catalog.Platform;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
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
class AppDetailsController {

    private final AppDetailsService detailsService;

    AppDetailsController(AppDetailsService detailsService) {
        this.detailsService = detailsService;
    }

    @GetMapping("/{id}")
    AppDetailsResponse details(
            @PathVariable @Pattern(regexp = "\\d{1,15}") String id,
            @RequestParam @Pattern(regexp = "[A-Za-z]{2}") String cc,
            @RequestParam @Pattern(regexp = "[a-zA-Z]{2}([-_][a-zA-Z]{2})?") String l,
            @RequestParam(defaultValue = "ios") @Pattern(regexp = "ios|mac") String platform) {
        Platform requested = Platform.valueOf(platform.toUpperCase(Locale.ROOT));
        return AppDetailsResponse.from(detailsService.details(id, cc, l, requested));
    }
}
