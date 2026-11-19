
package edu.research.userstory.dataset;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;

public class PreparePilotDataset {

    public static void main(String[] args) throws Exception {

        Path folder = Paths.get("datasets");
        Path summary = folder.resolve("UStAI/summery.csv");
        Path pilot = folder.resolve("pilot_inputs.csv");
        Path output = folder.resolve("pilot_inputs_resolved.csv");

        CSVFormat readFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        Map<String, String> abstracts = new HashMap<>();

        try (Reader reader = Files.newBufferedReader(summary);
             CSVParser parser = readFormat.parse(reader)) {

            for (CSVRecord record : parser) {
                String id = record.get("ID").trim();
                String text = record.get("abstract").trim();

                if (!id.isEmpty() && !text.isEmpty()) {
                    abstracts.put(id, text);
                }
            }
        }

        int matched = 0;
        int unmatched = 0;

        try (Reader reader = Files.newBufferedReader(pilot);
             CSVParser parser = readFormat.parse(reader);
             Writer writer = Files.newBufferedWriter(output);
             CSVPrinter printer = new CSVPrinter(
                     writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(
                                     "input_id",
                                     "abstract_id",
                                     "abstract_text")
                             .get())) {

            for (CSVRecord record : parser) {
                String inputId = record.get("input_id").trim();
                String abstractId = record.get("abstract").trim();

                String abstractText = abstracts.get(abstractId);

                if (abstractText == null) {
                    unmatched++;
                    System.out.println(
                            "Unmatched abstract: " + abstractId);
                    continue;
                }

                printer.printRecord(
                        inputId,
                        abstractId,
                        abstractText);

                matched++;
            }
        }

        System.out.println("Available abstracts: "
                + abstracts.size());
        System.out.println("Matched pilot inputs: " + matched);
        System.out.println("Unmatched pilot inputs: " + unmatched);
        System.out.println("Saved to: " + output);
    }
}
