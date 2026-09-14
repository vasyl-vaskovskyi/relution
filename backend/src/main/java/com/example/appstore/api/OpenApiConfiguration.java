package com.example.appstore.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * The generated OpenAPI document ({@code /v3/api-docs}, Swagger UI at {@code /swagger-ui.html}; both disabled in the
 * {@code prod} profile). Every operation under {@code /api/v1/} requires the bearer JWT and can fail upstream, so the
 * scheme and the shared problem responses (401, 403, 502, 503, 504) are added here once; the controllers document only
 * their own responses. Policies OpenAPI can't express stay in {@code docs/api/README.md}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static final String BEARER_SCHEME = "bearer-jwt";
    static final String PROBLEM_JSON = "application/problem+json";

    private static final String SECURED_PATH_PREFIX = "/api/v1/";
    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/ProblemDetail";

    @Bean
    OpenAPI appstoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("App Store search and details API")
                        .version("v1")
                        .description("""
                                Searches apps in the Apple App Store and returns app details as small, stable \
                                DTOs. Errors are RFC 9457 problem details (application/problem+json) with a \
                                correlationId. Get a token with POST /auth/token (HTTP Basic client credentials), \
                                then use Authorize. Policies and problem types: docs/api/README.md."""))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token from POST /auth/token with scope apps:read")));
    }

    @Bean
    OpenApiCustomizer bearerJwtForVersionedApi() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (!path.startsWith(SECURED_PATH_PREFIX)) {
                    return;
                }
                item.readOperations().forEach(operation -> {
                    operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                    ApiResponses responses = operation.getResponses();
                    responses.addApiResponse("401", problem("Token missing, invalid or expired (`unauthorized`)"));
                    responses.addApiResponse("403", problem("Token lacks the scope `apps:read` (`forbidden`)"));
                    responses.addApiResponse(
                            "502", problem("Apple failed or returned an unexpected response (`upstream-error`)"));
                    responses.addApiResponse(
                            "503",
                            problem("Apple rate limit reached (`upstream-unavailable`)")
                                    .addHeaderObject(
                                            HttpHeaders.RETRY_AFTER,
                                            new Header()
                                                    .description("Seconds to wait before retrying")
                                                    .schema(new IntegerSchema())));
                    responses.addApiResponse("504", problem("Apple didn't answer in time (`upstream-timeout`)"));
                });
            });
        };
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF))));
    }
}
