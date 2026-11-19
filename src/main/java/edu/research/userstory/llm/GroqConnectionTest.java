
package edu.research.userstory.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class GroqConnectionTest {

    public static void main(String[] args) throws Exception {

        String apiKey = System.getenv("GROQ_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("GROQ_API_KEY is missing");
            return;
        }

        String requestBody = """
            {
              "model": "openai/gpt-oss-20b",
              "messages": [
                {
                  "role": "user",
                  "content": "Reply with: API connection successful"
                }
              ],
              "temperature": 0,
              "max_tokens": 50
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://api.groq.com/openai/v1/chat/completions"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("HTTP Status: " + response.statusCode());
        System.out.println("Response: " + response.body());
    }
}
