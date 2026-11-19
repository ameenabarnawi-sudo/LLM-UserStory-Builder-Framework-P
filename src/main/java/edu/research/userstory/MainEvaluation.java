package edu.research.userstory;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainEvaluation {

    private static final Path VALIDATION = Paths.get(
            "datasets/main_validation_results.csv");

    private static final Path BUILDER = Paths.get(
            "datasets/main_builder_results.csv");

    private static final Path OUTPUT = Paths.get(
            "datasets/main_evaluation_summary.csv");

    private MainEvaluation() {
    }

    public static void main(String[] args) throws Exception {

        if (!Files.isRegularFile(VALIDATION)
                || !Files.isRegularFile(BUILDER)) {

            System.err.println(
                    "Required evaluation files are missing.");
            return;
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        Map<String, CSVRecord> validation =
                new LinkedHashMap<>();

        // Select the latest successful API response
        // for each unique input.
        try (
                Reader reader = Files.newBufferedReader(
                        VALIDATION, StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)
        ) {
            for (CSVRecord record : parser) {

                String id = record.get("input_id");
                String status = record.get("http_status");

                CSVRecord previous = validation.get(id);

                if ("200".equals(status)
                        || previous == null) {
                    validation.put(id, record);
                }
            }
        }

        Map<String, CSVRecord> builder =
                new LinkedHashMap<>();

        try (
                Reader reader = Files.newBufferedReader(
                        BUILDER, StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)
        ) {
            for (CSVRecord record : parser) {
                builder.put(record.get("input_id"), record);
            }
        }

        int total = validation.size();
        int jsonValid = 0;
        int requiredComplete = 0;
        int schemaValid = 0;
        int builderAttempted = 0;
        int constructionSuccess = 0;

        for (Map.Entry<String, CSVRecord> entry
                : validation.entrySet()) {

            CSVRecord record = entry.getValue();

            if (Boolean.parseBoolean(
                    record.get("json_valid"))) {
                jsonValid++;
            }

            if (Boolean.parseBoolean(
                    record.get("required_complete"))) {
                requiredComplete++;
            }

            if (Boolean.parseBoolean(
                    record.get("schema_valid"))) {
                schemaValid++;
            }

            CSVRecord builderRecord =
                    builder.get(entry.getKey());

            if (builderRecord != null) {

                if (Boolean.parseBoolean(
                        builderRecord.get("builder_attempted"))) {
                    builderAttempted++;
                }

                if (Boolean.parseBoolean(
                        builderRecord.get("construction_success"))) {
                    constructionSuccess++;
                }
            }
        }

        Files.createDirectories(OUTPUT.getParent());

        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "metric",
                        "count",
                        "denominator",
                        "rate_percent"
                )
                .get();

        try (
                Writer writer = Files.newBufferedWriter(
                        OUTPUT, StandardCharsets.UTF_8);
                CSVPrinter printer =
                        new CSVPrinter(writer, outputFormat)
        ) {

            writeMetric(printer,
                    "JSON Validity",
                    jsonValid, total);

            writeMetric(printer,
                    "Required-Field Completeness",
                    requiredComplete, total);

            writeMetric(printer,
                    "Schema Validity",
                    schemaValid, total);

            writeMetric(printer,
                    "Overall Construction Success",
                    constructionSuccess, total);

            writeMetric(printer,
                    "Builder Success Among Attempts",
                    constructionSuccess, builderAttempted);
        }

        System.out.println();
        System.out.println("=== MAIN EVALUATION SUMMARY ===");
        System.out.println("Total unique inputs: " + total);
        System.out.println("JSON valid: " + jsonValid);
        System.out.println(
                "Required fields complete: " + requiredComplete);
        System.out.println("Schema valid: " + schemaValid);
        System.out.println(
                "Builder attempted: " + builderAttempted);
        System.out.println(
                "Construction successful: " + constructionSuccess);
        System.out.println(
                "Rejected before Builder: "
                        + (total - builderAttempted));
        System.out.println(
                "Failed Builder attempts: "
                        + (builderAttempted - constructionSuccess));
        System.out.println("Results saved to: " + OUTPUT);
    }

    private static void writeMetric(
            CSVPrinter printer,
            String metric,
            int count,
            int denominator
    ) throws Exception {

        double rate = denominator == 0
                ? 0.0
                : (100.0 * count / denominator);

        printer.printRecord(
                metric,
                count,
                denominator,
                String.format(
                        java.util.Locale.US, "%.2f", rate)
        );
    }
}
