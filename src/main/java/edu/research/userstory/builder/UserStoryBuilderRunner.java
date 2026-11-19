package edu.research.userstory.builder;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class UserStoryBuilderRunner {

    private static final Path INPUT = Paths.get(
            "datasets/main_validation_results.csv");

    private static final Path OUTPUT = Paths.get(
            "datasets/main_builder_results.csv");

    private static final ObjectMapper JSON =
            new ObjectMapper();

    private UserStoryBuilderRunner() {
    }

    public static void main(String[] args)
            throws Exception {

        Path input = args.length > 0 ? Paths.get(args[0]) : INPUT;
        Path output = args.length > 1 ? Paths.get(args[1]) : OUTPUT;
        if (!Files.isRegularFile(input)) {
            System.err.println(
                    "Validation file not found: " + input);
            return;
        }

        CSVFormat inputFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "input_id",
                        "attempt",
                        "schema_valid",
                        "builder_attempted",
                        "construction_success",
                        "construction_error",
                        "constructed_story"
                )
                .get();

        Map<String, CSVRecord> selected =
                new LinkedHashMap<>();

        try (
                Reader reader = Files.newBufferedReader(
                        input, StandardCharsets.UTF_8);
                CSVParser parser = inputFormat.parse(reader)
        ) {
            for (CSVRecord record : parser) {

                String id = record.get("input_id");
                String status = record.get("http_status");

                // Keep the latest successful API response.
                // Retain an unsuccessful record only when
                // no successful response exists for that ID.
                CSVRecord previous = selected.get(id);

                if ("200".equals(status)
                        || previous == null) {
                    selected.put(id, record);
                }
            }
        }

        Files.createDirectories(output.getParent());

        int successful = 0;
        int attempted = 0;
        int total = 0;

        try (
                Writer writer = Files.newBufferedWriter(
                        output, StandardCharsets.UTF_8);
                CSVPrinter printer =
                        new CSVPrinter(writer, outputFormat)
        ) {

            for (CSVRecord record : selected.values()) {

                total++;

                String id = record.get("input_id");
                String attempt = record.get("attempt");

                boolean schemaValid = Boolean.parseBoolean(
                        record.get("schema_valid"));

                boolean builderAttempted = false;
                boolean constructionSuccess = false;

                String error = "";
                String constructedStory = "";

                if (!schemaValid) {

                    error = "SCHEMA_VALIDATION_FAILED";

                } else {

                    builderAttempted = true;
                    attempted++;

                    try {

                        String content =
                                record.get("generated_content")
                                        .trim();

                        // Normalize Markdown fences for parsing.
                        // Original validation data is unchanged.
                        if (content.startsWith("```")) {
                            content = content
                                    .replaceFirst(
                                            "^```(?:json)?\\s*", "")
                                    .replaceFirst(
                                            "\\s*```$", "")
                                    .trim();
                        }

                        JsonNode story = JSON.readTree(content);

                        UserStory.Builder builder =
                                new UserStory.Builder(
                                        story.path("role").asText(),
                                        story.path("goal").asText(),
                                        story.path("benefit").asText()
                                );

                        if (story.hasNonNull(
                                "acceptanceCriteria")) {

                            builder.acceptanceCriteria(
                                    readStringList(
                                            story.get(
                                                    "acceptanceCriteria")));
                        }

                        if (story.hasNonNull("priority")) {

                            builder.priority(
                                    story.get("priority").asText());
                        }

                        if (story.hasNonNull("dependencies")) {

                            builder.dependencies(
                                    readStringList(
                                            story.get("dependencies")));
                        }

                        if (story.hasNonNull("storyPoints")) {

                            builder.storyPoints(
                                    story.get("storyPoints").intValue());
                        }

                        UserStory userStory = builder.build();

                        constructionSuccess = true;
                        constructedStory = userStory.toString();
                        successful++;

                    } catch (Exception e) {

                        error = e.getClass().getSimpleName()
                                + ": " + e.getMessage();
                    }
                }

                printer.printRecord(
                        id,
                        attempt,
                        schemaValid,
                        builderAttempted,
                        constructionSuccess,
                        error,
                        constructedStory
                );

                System.out.printf(
                        "%s | %s | Schema: %s | Builder: %s%n",
                        id,
                        attempt,
                        schemaValid,
                        constructionSuccess
                );
            }
        }

        System.out.println();
        System.out.println("=== MAIN BUILDER SUMMARY ===");
        System.out.println("Total inputs: " + total);
        System.out.println("Builder attempted: " + attempted);
        System.out.println("Successful objects: " + successful);
        System.out.println("Failed objects: " +
                (total - successful));
        System.out.println("Results saved to: " + output);
    }

    private static List<String> readStringList(
            JsonNode value) {

        List<String> result = new ArrayList<>();

        if (value.isArray()) {

            for (JsonNode item : value) {
                result.add(item.asText());
            }

        } else if (value.isTextual()) {

            result.add(value.asText());
        }

        return result;
    }
}
