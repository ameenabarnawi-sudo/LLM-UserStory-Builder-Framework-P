package edu.research.userstory;

import org.apache.commons.csv.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MainResultsVisualization {

    private static final Path COMPARATIVE = Paths.get(
            "datasets/main_comparative_evaluation.csv");

    private static final Path ERRORS = Paths.get(
            "datasets/main_error_analysis.csv");

    private static final Path OUTPUT = Paths.get("results");

    private static final Color BLUE = new Color(65, 115, 170);
    private static final Color TEAL = new Color(75, 155, 150);
    private static final Color RED = new Color(195, 105, 105);
    private static final Color DARK = new Color(45, 55, 65);

    private static final CSVFormat FORMAT =
            CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get();

    private record Metric(int count, int denominator) {
        double percentage() {
            return denominator == 0 ? 0 :
                    count * 100.0 / denominator;
        }
    }

    public static void main(String[] args) throws Exception {

        if (!Files.isRegularFile(COMPARATIVE)
                || !Files.isRegularFile(ERRORS)) {
            throw new IllegalStateException(
                    "Required CSV input file is missing.");
        }

        Files.createDirectories(OUTPUT);

        Map<String, Metric> metrics = readMetrics();

        drawStageResults(metrics);
        drawErrorDistribution();

        System.out.println("Visualization completed.");
        System.out.println(
                "Figures saved to: " + OUTPUT.toAbsolutePath());
    }

    private static Map<String, Metric> readMetrics()
            throws Exception {

        Map<String, Metric> metrics = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(
                COMPARATIVE, StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {

            for (CSVRecord record : parser) {

                String key = record.get("configuration")
                        + "|" + record.get("metric");

                int count = Integer.parseInt(
                        record.get("count"));

                int denominator = Integer.parseInt(
                        record.get("denominator"));

                metrics.put(key, new Metric(count, denominator));
            }
        }

        return metrics;
    }

    private static Metric get(
            Map<String, Metric> metrics,
            String configuration,
            String metric) {

        String key = configuration + "|" + metric;

        Metric value = metrics.get(key);

        if (value == null) {
            throw new IllegalStateException(
                    "Missing evaluation metric: " + key);
        }

        return value;
    }

    private static void drawStageResults(
            Map<String, Metric> metrics) throws Exception {

        String[] labels = {
                "JSON Validity",
                "Required Fields",
                "Schema Validity",
                "Object Construction"
        };

        Metric[] values = {
                get(metrics, "E1_LLM_ONLY", "JSON_VALIDITY"),
                get(metrics, "E1_LLM_ONLY",
                        "REQUIRED_COMPLETENESS"),
                get(metrics, "E2_LLM_VALIDATION",
                        "SCHEMA_VALIDITY"),
                get(metrics, "E3_LLM_VALIDATION_BUILDER",
                        "CONSTRUCTION_SUCCESS_ALL_INPUTS")
        };

        BufferedImage image = new BufferedImage(
                1400, 850, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = createGraphics(image);

        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        g.drawString("Stage-wise Evaluation Results", 90, 85);

        int baseline = 680;
        int maxHeight = 450;
        int barWidth = 180;

        g.setColor(new Color(205, 210, 215));
        g.drawLine(90, baseline, 1310, baseline);

        for (int i = 0; i < values.length; i++) {

            double percent = values[i].percentage();

            int height = (int) Math.round(
                    maxHeight * percent / 100.0);

            int x = 125 + i * 310;

            g.setColor(i < 2 ? BLUE : TEAL);
            g.fillRoundRect(
                    x, baseline - height,
                    barWidth, height, 12, 12);

            g.setColor(DARK);
            g.setFont(new Font("SansSerif", Font.BOLD, 25));

            String percentage = String.format(
                    Locale.US, "%.1f%%", percent);

            g.drawString(
                    percentage, x + 45, baseline - height - 25);

            g.setFont(new Font("SansSerif", Font.PLAIN, 19));
            g.drawString(labels[i], x - 10, baseline + 40);

            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            g.drawString(
                    values[i].count() + "/" +
                    values[i].denominator(),
                    x + 65, baseline + 75);
        }

        g.dispose();

        ImageIO.write(image, "png",
                OUTPUT.resolve(
                        "stage_wise_results.png").toFile());
    }

    private static void drawErrorDistribution()
            throws Exception {

        Map<String, Integer> counts = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(
                ERRORS, StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {

            for (CSVRecord record : parser) {

                String codes = record.get("error_codes");

                for (String code : codes.split(";")) {

                    String error = code.trim();

                    if (!error.isEmpty()) {
                        counts.merge(error, 1, Integer::sum);
                    }
                }
            }
        }

        if (counts.isEmpty()) {
            throw new IllegalStateException(
                    "No error codes found.");
        }

        int height = Math.max(
                500, 230 + counts.size() * 115);

        BufferedImage image = new BufferedImage(
                1400, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = createGraphics(image);

        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        g.drawString(
                "Schema Validation Error Distribution",
                90, 85);

        int total = counts.values().stream()
                .mapToInt(Integer::intValue).sum();

        int maxCount = counts.values().stream()
                .mapToInt(Integer::intValue).max().orElse(1);

        int y = 170;

        for (Map.Entry<String, Integer> entry :
                counts.entrySet()) {

            int count = entry.getValue();

            int width = (int) Math.round(
                    count * 900.0 / maxCount);

            double percent = count * 100.0 / total;

            g.setColor(DARK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 22));
            g.drawString(entry.getKey(), 100, y);

            g.setColor(RED);
            g.fillRoundRect(
                    100, y + 18, width, 42, 10, 10);

            g.setColor(DARK);
            g.setFont(new Font("SansSerif", Font.BOLD, 21));
            g.drawString(
                    count + " (" +
                    String.format(Locale.US, "%.1f%%", percent)
                    + ")",
                    1050, y + 49);

            y += 115;
        }

        g.dispose();

        ImageIO.write(image, "png",
                OUTPUT.resolve(
                        "schema_error_distribution.png").toFile());
    }

    private static Graphics2D createGraphics(
            BufferedImage image) {

        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(
                0, 0, image.getWidth(), image.getHeight());

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        return g;
    }
}
