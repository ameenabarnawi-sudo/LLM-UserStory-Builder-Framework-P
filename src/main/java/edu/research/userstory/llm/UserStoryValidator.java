
package edu.research.userstory.llm;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates LLM-generated Agile user stories.
 * Preserves initial and retry attempts separately.
 * Does not modify the original generated content.
 */
public final class UserStoryValidator {

    private static final Path INITIAL =
            Paths.get("datasets/main_llm_results.csv");

    private static final Path RETRY =
            Paths.get("datasets/main_llm_retry.csv");

    private static final Path OUTPUT =
            Paths.get("datasets/main_validation_results.csv");

    private static final ObjectMapper JSON =
            new ObjectMapper();

    private static final List<String> REQUIRED =
            List.of("role", "goal", "benefit");

    private UserStoryValidator() {
    }

    public static void main(String[] args) throws Exception {

        Path initial = args.length > 0 ? Paths.get(args[0]) : INITIAL;
        Path output = args.length > 1 ? Paths.get(args[1]) : OUTPUT;
        Path retry = args.length > 2 ? Paths.get(args[2]) : null;
        if (!Files.isRegularFile(initial)) {
            throw new IllegalStateException("Missing input file: " + initial);
        }

        CSVFormat inputFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "input_id",
                        "attempt",
                        "model",
                        "http_status",
                        "finish_reason",
                        "json_valid",
                        "required_complete",
                        "schema_valid",
                        "markdown_fences",
                        "errors",
                        "generated_content"
                )
                .get();

        if (output.getParent() != null) Files.createDirectories(output.getParent());

        try (
                Writer writer = Files.newBufferedWriter(
                        output,
                        StandardCharsets.UTF_8
                );

                CSVPrinter printer =
                        new CSVPrinter(writer, outputFormat)
        ) {

            process(initial, "initial", inputFormat, printer);
            if (retry != null) process(retry, "retry", inputFormat, printer);
        }

        System.out.println();
        System.out.println(
                "Validation results saved to: " + output
        );
    }

    private static void process(
            Path path,
            String attempt,
            CSVFormat format,
            CSVPrinter printer
    ) throws Exception {

        if (!Files.isRegularFile(path)) {
            System.out.println("Skipping missing file: " + path);
            return;
        }

        try (
                Reader reader = Files.newBufferedReader(
                        path,
                        StandardCharsets.UTF_8
                );

                CSVParser parser = format.parse(reader)
        ) {

            for (CSVRecord record : parser) {

                String id = record.get("input_id");
                String model = record.get("model");
                String status = record.get("http_status");
                String raw = record.get("raw_response");

                Result result = validate(status, raw);

                printer.printRecord(
                        id,
                        attempt,
                        model,
                        status,
                        result.finishReason,
                        result.jsonValid,
                        result.requiredComplete,
                        result.schemaValid,
                        result.markdownFences,
                        String.join("; ", result.errors),
                        result.content
                );

                System.out.printf(
                        "%s %s | HTTP %s | JSON %s | Required %s | Schema %s%n",
                        attempt,
                        id,
                        status,
                        result.jsonValid,
                        result.requiredComplete,
                        result.schemaValid
                );
            }
        }
    }

    private static Result validate(String status, String raw) {

        Result r = new Result();

        // Step 1: Check API response status.
        if (!"200".equals(status)) {
            r.errors.add("API_ERROR_" + status);
            return r;
        }

        // Step 2: Extract generated content.
        try {

            JsonNode response = JSON.readTree(raw);
            JsonNode choices = response.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                r.errors.add("MISSING_CHOICES");
                return r;
            }

            JsonNode choice = choices.get(0);

            r.finishReason = choice.path("finish_reason")
                    .asText("");

            r.content = choice.path("message")
                    .path("content")
                    .asText("");

        } catch (Exception e) {
            r.errors.add("INVALID_API_RESPONSE_JSON");
            return r;
        }

        // Step 3: Detect Markdown formatting.
        String cleaned = r.content.trim();

        if (cleaned.startsWith("```")) {

            r.markdownFences = true;

            cleaned = cleaned
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        // Record incomplete generation.
        if (!"stop".equals(r.finishReason)) {
            r.errors.add(
                    "FINISH_REASON_" + r.finishReason
            );
        }

        // Step 4: Validate JSON syntax.
        JsonNode story;

        try {

            story = JSON.readTree(cleaned);

            if (!story.isObject()) {
                r.errors.add("JSON_NOT_OBJECT");
                return r;
            }

            r.jsonValid = true;

        } catch (Exception e) {
            r.errors.add("INVALID_STORY_JSON");
            return r;
        }

        // Step 5: Validate mandatory fields.
        boolean complete = true;

        for (String field : REQUIRED) {

            JsonNode value = story.get(field);

            if (value == null
                    || !value.isTextual()
                    || value.asText().isBlank()) {

                complete = false;

                r.errors.add(
                        "MISSING_OR_INVALID_" + field
                );
            }
        }

        r.requiredComplete = complete;

        // Step 6: Validate optional field types.
        boolean typesValid = true;

        for (String field : List.of(
                "acceptanceCriteria",
                "priority",
                "dependencies"
        )) {

            JsonNode value = story.get(field);

            if (value == null || value.isNull()) {
                continue;
            }

            boolean validType;

            if (field.equals("priority")) {

                validType = value.isTextual();

            } else {

                validType = value.isTextual()
                        || value.isArray();

                if (value.isArray()) {
                    for (JsonNode item : value) {
                        if (!item.isTextual()) {
                            validType = false;
                            break;
                        }
                    }
                }
            }

            if (!validType) {

                typesValid = false;

                r.errors.add(
                        "INVALID_TYPE_" + field
                );
            }
        }

        // Step 7: Validate story points.
        JsonNode points = story.get("storyPoints");

        if (points != null && !points.isNull()) {

            if (!points.isIntegralNumber()
              || !points.canConvertToInt()
              || points.asInt() <= 0)  {

                typesValid = false;

                r.errors.add(
                        "INVALID_TYPE_storyPoints"
                );
            }
        }

        // Step 8: Calculate schema validity.
        r.schemaValid = r.jsonValid
                && r.requiredComplete
                && typesValid
                && "stop".equals(r.finishReason);;

        return r;
    }

    /**
     * Stores validation results for each attempt.
     */
    private static class Result {

        String finishReason = "";
        String content = "";

        boolean jsonValid = false;
        boolean requiredComplete = false;
        boolean schemaValid = false;
        boolean markdownFences = false;

        List<String> errors = new ArrayList<>();
    }
}
