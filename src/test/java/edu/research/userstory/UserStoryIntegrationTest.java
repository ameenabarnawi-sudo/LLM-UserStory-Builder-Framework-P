
package edu.research.userstory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.research.userstory.builder.UserStory;
import edu.research.userstory.llm.UserStoryValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserStoryIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String VALID_STORY = """
        {
          "role": "customer",
          "goal": "track my order",
          "benefit": "know the delivery status",
          "acceptanceCriteria": ["Order status is visible"],
          "priority": "High",
          "dependencies": ["Order service"],
          "storyPoints": 5
        }
        """;

    // Simulates the API response expected by UserStoryValidator.
    private String apiResponse(String content) throws Exception {
        return JSON.writeValueAsString(
            java.util.Map.of(
                "choices", List.of(
                    java.util.Map.of(
                        "finish_reason", "stop",
                        "message", java.util.Map.of(
                            "content", content
                        )
                    )
                )
            )
        );
    }

    // Calls the existing Validator without changing its code.
    private Object validate(String status, String content)
            throws Exception {

        Method method = UserStoryValidator.class
                .getDeclaredMethod(
                    "validate", String.class, String.class
                );

        method.setAccessible(true);

        return method.invoke(
            null, status, apiResponse(content)
        );
    }

    private boolean flag(Object result, String fieldName)
            throws Exception {

        Field field = result.getClass()
                .getDeclaredField(fieldName);

        field.setAccessible(true);
        return field.getBoolean(result);
    }

    // Mirrors the JSON-to-Builder mapping in the existing runner.
    private UserStory construct(String content) throws Exception {

        JsonNode story = JSON.readTree(content);

        UserStory.Builder builder = new UserStory.Builder(
            story.path("role").asText(),
            story.path("goal").asText(),
            story.path("benefit").asText()
        );

        if (story.hasNonNull("acceptanceCriteria")) {
            builder.acceptanceCriteria(
                readStringList(story.get("acceptanceCriteria"))
            );
        }

        if (story.hasNonNull("priority")) {
            builder.priority(
                story.get("priority").asText()
            );
        }

        if (story.hasNonNull("dependencies")) {
            builder.dependencies(
                readStringList(story.get("dependencies"))
            );
        }

        if (story.hasNonNull("storyPoints")) {
            builder.storyPoints(
                story.get("storyPoints").intValue()
            );
        }

        return builder.build();
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.asText());
            }
        } else if (node.isTextual()) {
            values.add(node.asText());
        }

        return values;
    }

    // IT01: Valid JSON passes validation and construction.
    @Test
    void validStoryPassesBothStages() throws Exception {

        Object result = validate("200", VALID_STORY);

        assertTrue(flag(result, "jsonValid"));
        assertTrue(flag(result, "requiredComplete"));
        assertTrue(flag(result, "schemaValid"));

        UserStory story = construct(VALID_STORY);

        assertNotNull(story);
        assertEquals("customer", story.getRole());
        assertEquals("track my order", story.getGoal());
        assertEquals(5, story.getStoryPoints());
    }

    // IT02: Missing mandatory field prevents construction.
    @Test
    void missingRoleIsRejected() throws Exception {

        String content = """
            {
              "goal": "track my order",
              "benefit": "know the delivery status"
            }
            """;

        Object result = validate("200", content);

        assertTrue(flag(result, "jsonValid"));
        assertFalse(flag(result, "requiredComplete"));
        assertFalse(flag(result, "schemaValid"));

        UserStory story = flag(result, "schemaValid")
                ? construct(content) : null;

        assertNull(story);
    }

    // IT03: Invalid storyPoints type is rejected.
    @Test
    void invalidStoryPointsIsRejected() throws Exception {

        String content = """
            {
              "role": "customer",
              "goal": "track my order",
              "benefit": "know the delivery status",
              "storyPoints": "five"
            }
            """;

        Object result = validate("200", content);

        assertTrue(flag(result, "requiredComplete"));
        assertFalse(flag(result, "schemaValid"));

        UserStory story = flag(result, "schemaValid")
                ? construct(content) : null;

        assertNull(story);
    }

    // IT04: Malformed JSON is rejected.
    @Test
    void malformedJsonIsRejected() throws Exception {

        String content = """
            {"role": "customer", "goal":
            """;

        Object result = validate("200", content);

        assertFalse(flag(result, "jsonValid"));
        assertFalse(flag(result, "schemaValid"));

        UserStory story = flag(result, "schemaValid")
                ? construct(content) : null;

        assertNull(story);
    }

    // IT05: Optional attributes survive object construction.
    @Test
    void optionalFieldsArePreserved() throws Exception {

        Object result = validate("200", VALID_STORY);

        assertTrue(flag(result, "schemaValid"));

        UserStory story = construct(VALID_STORY);

        assertEquals("High", story.getPriority());
        assertEquals(5, story.getStoryPoints());

        assertEquals(
            List.of("Order status is visible"),
            story.getAcceptanceCriteria()
        );

        assertEquals(
            List.of("Order service"),
            story.getDependencies()
        );
    }

    // IT06: Non-successful API response blocks construction.
    @Test
    void apiFailurePreventsConstruction() throws Exception {

        Object result = validate("500", VALID_STORY);

        assertFalse(flag(result, "schemaValid"));

        UserStory story = flag(result, "schemaValid")
                ? construct(VALID_STORY) : null;

        assertNull(story);
    }
}
