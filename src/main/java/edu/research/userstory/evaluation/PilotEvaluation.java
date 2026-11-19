package edu.research.userstory.evaluation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class PilotEvaluation {

    private static final Path VALIDATION =
            Paths.get("datasets/pilot_validation_results.csv");

    private static final Path BUILDER =
            Paths.get("datasets/pilot_builder_results.csv");

    private static final Path OUTPUT =
            Paths.get("datasets/pilot_evaluation_summary.csv");

    private static final Path DETAILS =
            Paths.get("datasets/pilot_evaluation_details.csv");

    public static void main(String[] args) throws Exception {

        if (!Files.isRegularFile(VALIDATION)
                || !Files.isRegularFile(BUILDER)) {
            System.err.println("Required CSV files are missing.");
            return;
        }

        Map<String, Map<String, CSVRecord>> validation =
                new LinkedHashMap<>();

        for (CSVRecord record : readCsv(VALIDATION)) {
            String id = record.get("input_id");
            String attempt = record.get("attempt");

            validation.computeIfAbsent(
                    id, k -> new LinkedHashMap<>()
            ).put(attempt, record);
        }

        Map<String, CSVRecord> builder = new LinkedHashMap<>();

        for (CSVRecord record : readCsv(BUILDER)) {
            builder.put(record.get("input_id"), record);
        }

        int total = validation.size();

        int initialJson = 0;
        int initialRequired = 0;
        int initialSchema = 0;

        int finalJson = 0;
        int finalRequired = 0;
        int finalSchema = 0;

        int constructionSuccess = 0;
        int constructionFailed = 0;

        int retryUsed = 0;
        int missingInitial = 0;
        int missingBuilder = 0;

        CSVFormat detailFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "input_id",
                        "selected_attempt",
                        "initial_json_valid",
                        "initial_required_complete",
                        "initial_schema_valid",
                        "final_json_valid",
                        "final_required_complete",
                        "final_schema_valid",
                        "construction_success",
                        "construction_error"
                ).get();

        try (
            Writer writer = Files.newBufferedWriter(
                    DETAILS, StandardCharsets.UTF_8);
            CSVPrinter printer =
                    new CSVPrinter(writer, detailFormat)
        ) {

            for (String id : validation.keySet()) {

                Map<String, CSVRecord> attempts =
                        validation.get(id);

                CSVRecord initial = attempts.get("initial");
                CSVRecord selected = initial;

                if (initial == null) {
                    missingInitial++;
                }

                CSVRecord build = builder.get(id);

                String selectedAttempt = "initial";

                if (build != null) {
                    selectedAttempt = build.get("attempt");

                    CSVRecord matching =
                            attempts.get(selectedAttempt);

                    if (matching != null) {
                        selected = matching;
                    }
                } else {
                    missingBuilder++;
                }

                if ("retry".equals(selectedAttempt)) {
                    retryUsed++;
                }

                boolean ij = flag(initial, "json_valid");
                boolean ir = flag(initial, "required_complete");
                boolean is = flag(initial, "schema_valid");

                boolean fj = flag(selected, "json_valid");
                boolean fr = flag(selected, "required_complete");
                boolean fs = flag(selected, "schema_valid");

                boolean bs = flag(build, "construction_success");

                if (ij) initialJson++;
                if (ir) initialRequired++;
                if (is) initialSchema++;

                if (fj) finalJson++;
                if (fr) finalRequired++;
                if (fs) finalSchema++;

                if (build != null) {
                    if (bs) {
                        constructionSuccess++;
                    } else {
                        constructionFailed++;
                    }
                }

                String error = build == null
                        ? "MISSING_BUILDER_RESULT"
                        : build.get("construction_error");

                printer.printRecord(
                        id, selectedAttempt,
                        ij, ir, is,
                        fj, fr, fs,
                        bs, error
                );
            }
        }

        CSVFormat summaryFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "metric",
                        "count",
                        "total",
                        "percentage"
                ).get();

        try (
            Writer writer = Files.newBufferedWriter(
                    OUTPUT, StandardCharsets.UTF_8);
            CSVPrinter printer =
                    new CSVPrinter(writer, summaryFormat)
        ) {

            writeMetric(printer, "Initial JSON Validity",
                    initialJson, total);

            writeMetric(printer, "Initial Required Completeness",
                    initialRequired, total);

            writeMetric(printer, "Initial Schema Validity",
                    initialSchema, total);

            writeMetric(printer, "Post-Retry JSON Validity",
                    finalJson, total);

            writeMetric(printer, "Post-Retry Required Completeness",
                    finalRequired, total);

            writeMetric(printer, "Post-Retry Schema Validity",
                    finalSchema, total);

            writeMetric(printer, "Builder Construction Success",
                    constructionSuccess, total);

            writeMetric(printer, "Builder Construction Failure",
                    constructionFailed, total);

            writeMetric(printer, "Retry Used",
                    retryUsed, total);
        }

        System.out.println("\n=== PILOT EVALUATION ===");
        System.out.println("Total inputs: " + total);

        System.out.printf(
                "Initial JSON validity: %.1f%%%n",
                percentage(initialJson, total));

        System.out.printf(
                "Initial schema validity: %.1f%%%n",
                percentage(initialSchema, total));

        System.out.printf(
                "Post-retry schema validity: %.1f%%%n",
                percentage(finalSchema, total));

        System.out.printf(
                "Builder success: %.1f%%%n",
                percentage(constructionSuccess, total));

        System.out.println("Retries used: " + retryUsed);
        System.out.println("Missing initial rows: " + missingInitial);
        System.out.println("Missing builder rows: " + missingBuilder);

        System.out.println("\nSummary saved to: " + OUTPUT);
        System.out.println("Details saved to: " + DETAILS);
    }

    private static List<CSVRecord> readCsv(Path path)
            throws Exception {

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (
            Reader reader = Files.newBufferedReader(
                    path, StandardCharsets.UTF_8);
            CSVParser parser = format.parse(reader)
        ) {
            return parser.getRecords();
        }
    }

    private static boolean flag(
            CSVRecord record, String column) {

        return record != null
                && "true".equalsIgnoreCase(
                        record.get(column).trim());
    }

    private static void writeMetric(
            CSVPrinter printer,
            String name,
            int count,
            int total) throws Exception {

        printer.printRecord(
                name,
                count,
                total,
                String.format(
                        Locale.US,
                        "%.2f",
                        percentage(count, total))
        );
    }

    private static double percentage(
            int count, int total) {

        return total == 0
                ? 0.0
                : (count * 100.0 / total);
    }
}
