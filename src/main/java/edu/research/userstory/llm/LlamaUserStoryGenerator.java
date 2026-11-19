
package edu.research.userstory.llm;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LlamaUserStoryGenerator {

    private static final String MODEL =  "qwen/qwen3.8-27b";

    private static final Path INPUT =
            Paths.get("datasets/main_inputs.csv");

    private static final Path OUTPUT =
            Paths.get("datasets/qwen_llm_results.csv");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static void main(String[] args) throws Exception {

        String apiKey = System.getenv("GROQ_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("GROQ_API_KEY is missing.");
            return;
        }

        if (!Files.isRegularFile(INPUT)) {
            System.err.println("Input file not found: " + INPUT);
            return;
        }

        CSVFormat inputFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        CSVFormat outputFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "input_id",
                        "model",
                        "http_status",
                        "raw_response"
                )
                .get();

        // Preserve completed results if execution is interrupted.
        Set<String> completed = new HashSet<>();

        if (Files.isRegularFile(OUTPUT)) {
            try (Reader reader = Files.newBufferedReader(
                    OUTPUT, StandardCharsets.UTF_8);
                 CSVParser parser = inputFormat.parse(reader)) {

                for (CSVRecord record : parser) {
                    String id = record.get("input_id");
                    String status = record.get("http_status");

                    if ("200".equals(status)) {
                        completed.add(id);
                    }
                }
            }
        }

        boolean append = Files.isRegularFile(OUTPUT)
                && Files.size(OUTPUT) > 0;

        Files.createDirectories(OUTPUT.getParent());

        try (
                Reader reader = Files.newBufferedReader(
                        INPUT, StandardCharsets.UTF_8);

                CSVParser parser = inputFormat.parse(reader);

                Writer writer = Files.newBufferedWriter(
                        OUTPUT,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);

                CSVPrinter printer = new CSVPrinter(
                        writer,
                        append ? CSVFormat.DEFAULT : outputFormat)
        ) {

            int processed = 0;
            int skipped = 0;
            int failed = 0;

            for (CSVRecord record : parser) {

                String inputId = record.get("input_id");

                if (completed.contains(inputId)) {
                    System.out.println("Skipping completed: " + inputId);
                    skipped++;
                    continue;
                }

                String requirement = record.get("requirement_text");

                if (requirement == null || requirement.isBlank()) {
                    System.out.println("Skipping empty input: " + inputId);
                    skipped++;
                    continue;
                }

                System.out.println("\nProcessing: " + inputId);

                String prompt = """
                        Convert the following source description
                        into exactly one Agile user story.

                        Required fields:
                        - role
                        - goal
                        - benefit

                        Optional fields:
                        - acceptanceCriteria
                        - priority
                        - dependencies
                        - storyPoints

                        Return only a valid JSON object.

                        Do not invent unsupported technical details.
                        If a required component cannot be determined,
                        use an empty string.

                        Source description:
                        """ + requirement;

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", MODEL);

                body.put("messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ));

                body.put("temperature", 0);
                body.put("max_tokens", 1200);

                String requestBody = JSON.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://api.groq.com/openai/v1/chat/completions"))
                        .timeout(Duration.ofSeconds(90))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                int status;
                String response;

                int maxRetries = 5;
          int attempt = 0;

          while (true) {
          try {
           HttpResponse<String> result = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
           );

          status = result.statusCode();
          response = result.body();

           if (status != 429 || attempt >= maxRetries) {
            break;
         }

          attempt++;

          long waitSeconds = Math.min(15L * attempt, 60L);

           System.out.println(
                "Rate limit reached. Waiting "
                + waitSeconds + " seconds..."
          );

          Thread.sleep(waitSeconds * 1000);

       } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw e;

        } catch (Exception e) {
          status = -1;
          response = e.toString();
          break;
        }
      }

                printer.printRecord(
                        inputId,
                        MODEL,
                        status,
                        response
                );

                printer.flush();

                processed++;

                System.out.println("HTTP Status: " + status);

                if (status != 200) {
                    failed++;
                }

                if (status == 401 || status == 403 || status == 429) {
                    System.err.println(
                            "Stopping due to API error: " + status);
                    break;
                }

                // Small delay to reduce API request pressure.
                Thread.sleep(6000);
            }

            System.out.println("\n=== MAIN GENERATION SUMMARY ===");
            System.out.println("Newly processed: " + processed);
            System.out.println("Skipped: " + skipped);
            System.out.println("API failures: " + failed);
            System.out.println("Results saved to: " + OUTPUT);
        }
    }
}
