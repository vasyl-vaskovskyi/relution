package com.example.appstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The generated OpenAPI document must equal the committed contract snapshot {@code docs/api/openapi.json}, so every
 * change to the public API shows up in review. Update it with the command in {@code docs/development/testing.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractSnapshotTest {

    static final String UPDATE_COMMAND =
            "(cd backend && ./gradlew test --tests '*OpenApiContractSnapshotTest' -PupdateOpenApiSnapshot)";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int DIFF_CONTEXT_LINES = 3;

    @Autowired
    MockMvcTester mvc;

    @Test
    void generatedDocumentMatchesTheCommittedSnapshot() throws IOException {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        String actual = normalize(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        Path snapshot = snapshotPath();
        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(snapshot.toAbsolutePath().getParent());
            Files.writeString(snapshot, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(snapshot)) {
            fail("OpenAPI snapshot %s is missing. Create it with:%n  %s", snapshot, UPDATE_COMMAND);
        }
        String expected = Files.readString(snapshot, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            fail(
                    "The generated OpenAPI document differs from %s.%n%s%nIf the API change is intended, update the"
                            + " snapshot and commit it with the change:%n  %s",
                    snapshot, firstDifference(expected, actual), UPDATE_COMMAND);
        }
    }

    /** Sorted keys, no generated server URLs (they depend on the host and port), two-space indent, final newline. */
    static String normalize(String document) {
        JsonNode root = JSON.readTree(document);
        if (root instanceof ObjectNode object) {
            object.remove("servers");
        }
        return JSON.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(sortKeys(root))
                        .replace("\r\n", "\n")
                + "\n";
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node instanceof ObjectNode object) {
            ObjectNode sorted = JSON.createObjectNode();
            object.propertyNames().stream().sorted().forEach(name -> sorted.set(name, sortKeys(object.get(name))));
            return sorted;
        }
        if (node instanceof ArrayNode array) {
            ArrayNode copy = JSON.createArrayNode();
            array.forEach(element -> copy.add(sortKeys(element)));
            return copy;
        }
        return node;
    }

    private static String firstDifference(String expected, String actual) {
        List<String> expectedLines = expected.lines().toList();
        List<String> actualLines = actual.lines().toList();
        int line = 0;
        while (line < expectedLines.size()
                && line < actualLines.size()
                && expectedLines.get(line).equals(actualLines.get(line))) {
            line++;
        }
        int from = Math.max(0, line - DIFF_CONTEXT_LINES);
        List<String> hint = new ArrayList<>();
        hint.add("First difference at line %d (- snapshot, + generated):".formatted(line + 1));
        hint.addAll(excerpt("- ", expectedLines, from, line));
        hint.addAll(excerpt("+ ", actualLines, from, line));
        hint.add("Run the update command and inspect the change with: git diff docs/api/openapi.json");
        return String.join(System.lineSeparator(), hint);
    }

    private static List<String> excerpt(String prefix, List<String> lines, int from, int line) {
        int to = Math.min(lines.size(), line + DIFF_CONTEXT_LINES + 1);
        List<String> excerpt = new ArrayList<>();
        for (int i = from; i < to; i++) {
            excerpt.add(prefix + "%5d  %s".formatted(i + 1, lines.get(i)));
        }
        if (line >= lines.size()) {
            excerpt.add(prefix + "       <end of document>");
        }
        return excerpt;
    }

    private static Path snapshotPath() {
        // Gradle passes the absolute path; the fallback resolves from the backend directory (e.g. in an IDE)
        return Path.of(System.getProperty("openapi.snapshot.path", "../docs/api/openapi.json"));
    }
}
