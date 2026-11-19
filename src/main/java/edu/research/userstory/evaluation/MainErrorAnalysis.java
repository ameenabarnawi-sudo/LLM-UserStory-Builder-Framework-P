package edu.research.userstory.evaluation;

import org.apache.commons.csv.*;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MainErrorAnalysis {

    private static final Path INPUT = Paths.get(
            "datasets/main_validation_results.csv");

    private static final Path OUTPUT = Paths.get(
            "datasets/main_error_analysis.csv");

    public static void main(String[] args) throws Exception {

        if (!Files.isRegularFile(INPUT)) {
            System.err.println("File not found: " + INPUT);
            return;
        }

        CSVFormat inputFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        Map<String, Set<String>> errorsByInput =
                new LinkedHashMap<>();

        Map<String, Integer> errorCounts =
                new TreeMap<>();

        try (Reader reader = Files.newBufferedReader(
                INPUT, StandardCharsets.UTF_8);
             CSVParser parser = inputFormat.parse(reader)) {

            // Keep the latest HTTP 200 result for each input.
            Map<String, CSVRecord> selected =
                    new LinkedHashMap<>();

            for (CSVRecord record : parser) {
                String id = record.get("input_id");
                String status = record.get("http_status");

                if ("200".equals(status)) {
                    selected.put(id, record);
                }
            }

            for (CSVRecord record : selected.values()) {

                if (Boolean.parseBoolean(
                        record.get("schema_valid"))) {
                    continue;
                }

                String id = record.get("input_id");
                String rawErrors = record.get("errors");

                Set<String> uniqueErrors =
                        new LinkedHashSet<>();

                for (String error : rawErrors.split(";")) {

                    String code = error.trim();

                    if (!code.isEmpty()) {
                        uniqueErrors.add(code);
                        errorCounts.merge(code, 1,
                                Integer::sum);
                    }
                }

                errorsByInput.put(id, uniqueErrors);
            }
        }

        Files.createDirectories(OUTPUT.getParent());

        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
                .setHeader("input_id", "error_codes")
                .get();

        try (Writer writer = Files.newBufferedWriter(
                OUTPUT, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(
                writer, outputFormat)) {

            for (Map.Entry<String, Set<String>> entry
                    : errorsByInput.entrySet()) {

                printer.printRecord(
                        entry.getKey(),
                        String.join("; ", entry.getValue())
                );
            }
        }

        System.out.println(
                "=== MAIN SCHEMA ERROR ANALYSIS ===");

        System.out.println(
                "Rejected unique inputs: "
                + errorsByInput.size());

        for (Map.Entry<String, Integer> entry
                : errorCounts.entrySet()) {

            System.out.println(
                    entry.getKey() + ": "
                    + entry.getValue());
        }

        System.out.println(
                "Results saved to: " + OUTPUT);
    }
}
