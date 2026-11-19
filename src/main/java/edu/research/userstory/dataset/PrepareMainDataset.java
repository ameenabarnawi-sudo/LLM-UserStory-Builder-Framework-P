
package edu.research.userstory.dataset;

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
import java.util.*;

public final class PrepareMainDataset {

    private static final Path SOURCE =
            Paths.get("datasets/UStAI/summery.csv");

    private static final Path OUTPUT =
            Paths.get("datasets/main_inputs.csv");

    private static final Path LOG =
            Paths.get("datasets/main_selection_log.csv");

    private static final Path SUMMARY =
            Paths.get("datasets/main_dataset_summary.csv");

    private static final Path DISTRIBUTION =
            Paths.get("datasets/main_domain_distribution.csv");

    private static final int SAMPLE_SIZE = 50;
    private static final long RANDOM_SEED = 20261110L;
    private static final int MIN_ABSTRACT_WORDS = 20;

    private PrepareMainDataset() {}

    private static final class Entry {
        String sourceId;
        String domain;
        String title;
        String abstractText;
        String status = "";
        String reason = "";
        String inputId = "";

        Entry(String sourceId, String domain,
              String title, String abstractText) {
            this.sourceId = sourceId;
            this.domain = domain;
            this.title = title;
            this.abstractText = abstractText;
        }
    }

    public static void main(String[] args) throws Exception {

        if (!Files.isRegularFile(SOURCE)) {
            System.err.println(
                    "Dataset not found: " + SOURCE);
            return;
        }

        Files.createDirectories(OUTPUT.getParent());

        List<Entry> all = new ArrayList<>();
        Map<String, Entry> unique = new LinkedHashMap<>();

        CSVFormat inputFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (Reader reader = Files.newBufferedReader(
                    SOURCE, StandardCharsets.UTF_8);
             CSVParser parser = inputFormat.parse(reader)) {

            for (CSVRecord record : parser) {

                String id = value(record, "ID");
                String domain = value(record, "domain");
                String title = value(record, "title");
                String abstractText = value(record, "abstract");

                Entry entry = new Entry(
                        id, domain, title, abstractText);

                all.add(entry);

                if (id.isBlank()) {
                    exclude(entry, "MISSING_SOURCE_ID");
                    continue;
                }

                if (abstractText.isBlank()) {
                    exclude(entry, "MISSING_ABSTRACT");
                    continue;
                }

                if (wordCount(abstractText)
                        < MIN_ABSTRACT_WORDS) {
                    exclude(entry, "ABSTRACT_TOO_SHORT");
                    continue;
                }

                if (domain.isBlank()) {
                    exclude(entry, "MISSING_DOMAIN");
                    continue;
                }

                String normalized =
                        abstractText.toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", " ")
                                .trim();

                if (unique.containsKey(normalized)) {
                    exclude(entry, "DUPLICATE_ABSTRACT");
                    continue;
                }

                unique.put(normalized, entry);
                entry.status = "ELIGIBLE";
                entry.reason = "PASSED_SCREENING";
            }
        }

        List<Entry> eligible =
                new ArrayList<>(unique.values());

        if (eligible.size() < SAMPLE_SIZE) {
            writeLog(all);
            writeSummary(all, eligible.size(), 0);

            throw new IllegalStateException(
                    "Only " + eligible.size()
                    + " eligible abstracts; required "
                    + SAMPLE_SIZE);
        }

        Map<String, List<Entry>> groups =
                new TreeMap<>();

        for (Entry entry : eligible) {
            groups.computeIfAbsent(
                    entry.domain, k -> new ArrayList<>()
            ).add(entry);
        }

        Random random = new Random(RANDOM_SEED);

        for (List<Entry> group : groups.values()) {
            Collections.shuffle(group, random);
        }

        List<Entry> selected = new ArrayList<>();
        Set<Entry> selectedSet = new HashSet<>();

        // First pass: one record per domain,
        // as long as sample capacity allows.
        List<String> domains =
                new ArrayList<>(groups.keySet());

        Collections.shuffle(domains, random);

        for (String domain : domains) {
            if (selected.size() >= SAMPLE_SIZE) {
                break;
            }

            List<Entry> group = groups.get(domain);

            if (!group.isEmpty()) {
                Entry entry = group.remove(0);
                selected.add(entry);
                selectedSet.add(entry);
            }
        }

        // Second pass: fill remaining positions
        // using round-robin sampling across domains.
        boolean progress = true;

        while (selected.size() < SAMPLE_SIZE && progress) {
            progress = false;

            for (String domain : domains) {
                if (selected.size() >= SAMPLE_SIZE) {
                    break;
                }

                List<Entry> group = groups.get(domain);

                if (!group.isEmpty()) {
                    Entry entry = group.remove(0);
                    selected.add(entry);
                    selectedSet.add(entry);
                    progress = true;
                }
            }
        }

        // Assign reproducible IDs.
        for (int i = 0; i < selected.size(); i++) {
            Entry entry = selected.get(i);
            entry.inputId =
                    String.format(Locale.US, "M%03d", i + 1);
            entry.status = "SELECTED";
            entry.reason = "STRATIFIED_SAMPLE";
        }

        for (Entry entry : eligible) {
            if (!selectedSet.contains(entry)) {
                entry.status = "NOT_SELECTED";
                entry.reason = "SAMPLE_LIMIT";
            }
        }

        writeInputs(selected);
        writeLog(all);
        writeSummary(all, eligible.size(), selected.size());
        writeDistribution(selected);

        System.out.println(
                "\n=== MAIN DATASET PREPARATION ===");
        System.out.println("Source records: " + all.size());
        System.out.println(
                "Eligible records: " + eligible.size());
        System.out.println(
                "Selected records: " + selected.size());
        System.out.println(
                "Selected domains: "
                + selected.stream()
                    .map(e -> e.domain)
                    .distinct()
                    .count());
        System.out.println(
                "Random seed: " + RANDOM_SEED);

        System.out.println("\nGenerated files:");
        System.out.println(OUTPUT);
        System.out.println(LOG);
        System.out.println(SUMMARY);
        System.out.println(DISTRIBUTION);
    }

    private static void writeInputs(
            List<Entry> selected) throws Exception {

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "input_id",
                        "source_id",
                        "domain",
                        "title",
                        "requirement_text")
                .get();

        try (Writer writer = Files.newBufferedWriter(
                    OUTPUT, StandardCharsets.UTF_8);
             CSVPrinter printer =
                    new CSVPrinter(writer, format)) {

            for (Entry entry : selected) {
                printer.printRecord(
                        entry.inputId,
                        entry.sourceId,
                        entry.domain,
                        entry.title,
                        entry.abstractText);
            }
        }
    }

    private static void writeLog(
            List<Entry> entries) throws Exception {

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "source_id",
                        "domain",
                        "title",
                        "status",
                        "reason",
                        "input_id")
                .get();

        try (Writer writer = Files.newBufferedWriter(
                    LOG, StandardCharsets.UTF_8);
             CSVPrinter printer =
                    new CSVPrinter(writer, format)) {

            for (Entry entry : entries) {
                printer.printRecord(
                        entry.sourceId,
                        entry.domain,
                        entry.title,
                        entry.status,
                        entry.reason,
                        entry.inputId);
            }
        }
    }

    private static void writeSummary(
            List<Entry> all,
            int eligible,
            int selected) throws Exception {

        Map<String, Integer> counts =
                new TreeMap<>();

        for (Entry entry : all) {
            counts.merge(
                    entry.reason, 1, Integer::sum);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("metric", "count")
                .get();

        try (Writer writer = Files.newBufferedWriter(
                    SUMMARY, StandardCharsets.UTF_8);
             CSVPrinter printer =
                    new CSVPrinter(writer, format)) {

            printer.printRecord(
                    "TOTAL_SOURCE_RECORDS", all.size());

            printer.printRecord(
                    "ELIGIBLE_RECORDS", eligible);

            printer.printRecord(
                    "SELECTED_RECORDS", selected);

            printer.printRecord(
                    "RANDOM_SEED", RANDOM_SEED);

            printer.printRecord(
                    "MIN_ABSTRACT_WORDS",
                    MIN_ABSTRACT_WORDS);

            for (Map.Entry<String, Integer> item
                    : counts.entrySet()) {
                printer.printRecord(
                        item.getKey(), item.getValue());
            }
        }
    }

    private static void writeDistribution(
            List<Entry> selected) throws Exception {

        Map<String, Integer> distribution =
                new TreeMap<>();

        for (Entry entry : selected) {
            distribution.merge(
                    entry.domain, 1, Integer::sum);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("domain", "selected_count")
                .get();

        try (Writer writer = Files.newBufferedWriter(
                    DISTRIBUTION, StandardCharsets.UTF_8);
             CSVPrinter printer =
                    new CSVPrinter(writer, format)) {

            for (Map.Entry<String, Integer> item
                    : distribution.entrySet()) {
                printer.printRecord(
                        item.getKey(), item.getValue());
            }
        }
    }

    private static String value(
            CSVRecord record, String column) {

        if (!record.isMapped(column)
                || !record.isSet(column)) {
            return "";
        }

        return record.get(column).trim();
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        return text.trim().split("\\s+").length;
    }

    private static void exclude(
            Entry entry, String reason) {

        entry.status = "EXCLUDED";
        entry.reason = reason;
    }
}
