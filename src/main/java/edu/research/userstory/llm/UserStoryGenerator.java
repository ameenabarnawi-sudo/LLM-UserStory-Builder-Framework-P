
package edu.research.userstory.llm;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import org.apache.commons.csv.*;

public class UserStoryGenerator {

    private static final String MODEL = "openai/gpt-oss-20b";

    private static final Path INPUT =
        Paths.get("datasets/pilot_inputs_resolved.csv");

    private static final Path OUTPUT =
        Paths.get("datasets/pilot_llm_retry.csv");
   

    public static void main(String[] args) throws Exception {

        String apiKey = System.getenv("GROQ_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("GROQ_API_KEY is missing.");
            return;
        }

        if (!Files.exists(INPUT)) {
            System.err.println("Input file not found: " + INPUT);
            return;
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

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

        try (
            Reader reader = Files.newBufferedReader(INPUT);
            CSVParser parser = inputFormat.parse(reader);
            Writer writer = Files.newBufferedWriter(
                OUTPUT,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            CSVPrinter printer = new CSVPrinter(writer, outputFormat)
        ) {

            int count = 0;

            for (CSVRecord record : parser) {

                String inputId = record.get("input_id");

                    Set<String> retryIds = Set.of(
                      "P001", "P003", "P005", "P010"
                             );

                           if (!retryIds.contains(inputId)) {
                             continue;
                  }
                String requirement = record.get("abstract_text");

                System.out.println(
                    "\nProcessing: " + inputId
                );

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

                String requestBody = """
                    {
                      "model": "%s",
                      "messages": [
                        {
                          "role": "user",
                          "content": %s
                        }
                      ],
                      "temperature": 0,
                      "max_tokens": 1200
                    }
                    """.formatted(
                        MODEL,
                        jsonString(prompt)
                    );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                        "https://api.groq.com/openai/v1/chat/completions"
                    ))
                    .timeout(Duration.ofSeconds(90))
                    .header(
                        "Authorization",
                        "Bearer " + apiKey
                    )
                    .header(
                        "Content-Type",
                        "application/json"
                    )
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            requestBody
                        )
                    )
                    .build();

                int status;
                String response;

                try {
                    HttpResponse<String> result = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                    );

                    status = result.statusCode();
                    response = result.body();

                } catch (Exception e) {
                    status = -1;
                    response = e.toString();
                }

                printer.printRecord(
                    inputId,
                    MODEL,
                    status,
                    response
                );

                printer.flush();

                System.out.println(
                    "HTTP Status: " + status
                );

                count++;

                if (status == 401 || status == 403 ||
                    status == 429) {
                    System.err.println(
                        "Stopping due to API error: " + status
                    );
                    break;
                }
            }

            System.out.println(
                "\nProcessed inputs: " + count
            );

            System.out.println(
                "Results saved to: " + OUTPUT
            );
        }
    }

    private static String jsonString(String text) {

        StringBuilder result = new StringBuilder("\"");

        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(
                            String.format("\\u%04x", (int) c)
                        );
                    } else {
                        result.append(c);
                    }
                }
            }
        }

        return result.append("\"").toString();
    }
}
