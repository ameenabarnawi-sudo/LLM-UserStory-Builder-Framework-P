
package edu.research.userstory.dataset;

import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;

public class DatasetInspector {

    public static void main(String[] args) throws Exception {

        Path folder = Paths.get("datasets/UStAI");

        String[] files = {
            "complete UStAI dataset.csv",
            "UStAI-annotated_updated.csv",
            "UStAI-annotated.csv",
            "summery.csv"
        };

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setAllowMissingColumnNames(true)
            .get();

        // Part 1: Inspect all dataset files
        for (String filename : files) {

            Path path = folder.resolve(filename);

            System.out.println("\n========================");
            System.out.println("FILE: " + filename);

            try (Reader reader = Files.newBufferedReader(path);
                 CSVParser parser = format.parse(reader)) {

                List<String> headers = parser.getHeaderNames();

                System.out.println("COLUMNS:");
                headers.forEach(System.out::println);

                long records = 0;
                long missingStories = 0;
                long missingAbstracts = 0;

                Set<String> uniqueAbstracts = new HashSet<>();
                Set<String> uniqueStories = new HashSet<>();

                for (CSVRecord record : parser) {

                    records++;

                    if (record.isMapped("User story")) {

                        String story =
                            record.get("User story").trim();

                        if (story.isEmpty()) {
                            missingStories++;
                        } else {
                            uniqueStories.add(story);
                        }
                    }

                    if (record.isMapped("Abstract")) {

                        String abstractText =
                            record.get("Abstract").trim();

                        if (abstractText.isEmpty()) {
                            missingAbstracts++;
                        } else {
                            uniqueAbstracts.add(abstractText);
                        }
                    }
                }

                System.out.println("Total records: " + records);
                System.out.println("Missing stories: " + missingStories);
                System.out.println("Unique stories: " + uniqueStories.size());

                if (headers.contains("Abstract")) {
                    System.out.println("Missing abstracts: " + missingAbstracts);
                    System.out.println("Unique abstracts: " + uniqueAbstracts.size());
                }
            }
        }

        // Part 2: Analyze user story distribution by abstract
        Path annotatedFile = folder.resolve(
            "UStAI-annotated_updated.csv"
        );

        Map<String, Integer> abstractCounts =
            new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(annotatedFile);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {

                String abstractText =
                    record.get("Abstract").trim();

                if (!abstractText.isEmpty()) {
                    abstractCounts.merge(
                        abstractText, 1, Integer::sum
                    );
                }
            }
        }

        System.out.println("\nABSTRACT DISTRIBUTION");
        System.out.println(
            "Unique abstracts: " + abstractCounts.size()
        );

        int index = 1;

        for (Map.Entry<String, Integer> entry :
                abstractCounts.entrySet()) {

            System.out.printf(
                "Abstract %02d: %d user stories%n",
                index++,
                entry.getValue()
            );
        }
    }
}
