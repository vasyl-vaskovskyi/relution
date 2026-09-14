package com.example.appstore.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The generated OpenAPI document is available locally and disabled in the {@code prod} profile (ADR-0032). */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Nested
    class DefaultProfile {

        @Autowired
        MockMvcTester mvc;

        @Test
        void documentsBothEndpointsBehindTheBearerScheme() {
            var document =
                    assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson();

            document.extractingPath("$.info.version").isEqualTo("v1");
            document.extractingPath("$.components.securitySchemes['bearer-jwt'].scheme")
                    .isEqualTo("bearer");
            document.extractingPath("$.components.securitySchemes['bearer-jwt'].bearerFormat")
                    .isEqualTo("JWT");
            for (String path : new String[] {"/api/v1/apps", "/api/v1/apps/{id}"}) {
                String operation = "$.paths['" + path + "'].get";
                document.extractingPath(operation + ".security[0]").asMap().containsKey("bearer-jwt");
                document.extractingPath(operation + ".responses['200'].content")
                        .asMap()
                        .containsOnlyKeys("application/json");
                document.extractingPath(operation + ".responses['400'].content")
                        .asMap()
                        .containsKey("application/problem+json");
                document.extractingPath(operation + ".responses")
                        .asMap()
                        .containsKeys("200", "400", "401", "403", "502", "503", "504");
            }
            document.extractingPath("$.paths['/api/v1/apps/{id}'].get.responses")
                    .asMap()
                    .containsKey("404");
        }

        @Test
        void documentsTheTokenEndpointWithItsRateLimit() {
            var document =
                    assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson();

            String responses = "$.paths['/auth/token'].post.responses";
            document.extractingPath(responses).asMap().containsKeys("200", "401", "429");
            document.extractingPath(responses + "['429'].content").asMap().containsKey("application/problem+json");
            document.extractingPath(responses + "['429'].headers").asMap().containsKey("Retry-After");
        }
    }

    @Nested
    @ActiveProfiles("prod")
    class ProdProfile {

        @Autowired
        MockMvcTester mvc;

        @Test
        void disablesTheApiDocsAndSwaggerUi() {
            assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus(HttpStatus.NOT_FOUND);
        }
    }
}
