package edu.research.userstory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.research.userstory.builder.UserStory;
import edu.research.userstory.llm.UserStoryValidator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the eight corrected outputs without modifying original experiment CSV files. */
public class StoryPointsCorrectionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path INPUT = Path.of("datasets", "storypoints_corrected_outputs.json");
    private static final List<String> IDS = List.of(
            "M011", "M013", "M014", "M016", "M027", "M034", "M048", "M050");

    @TestFactory
    Stream<DynamicTest> correctedStoriesPassActualValidatorAndBuilder() throws Exception {
        assertTrue(Files.isRegularFile(INPUT), "Missing corrected JSON: " + INPUT.toAbsolutePath());
        JsonNode outputs = JSON.readTree(INPUT.toFile());
        assertTrue(outputs.isObject(), "Expected an object keyed by input ID");
        assertEquals(IDS.size(), outputs.size(), "Expected exactly eight corrected stories");

        // Use the existing validator's real private validation implementation.
        // Reflection avoids changing production code or writing to original CSV files.
        Method validate = UserStoryValidator.class.getDeclaredMethod(
                "validate", String.class, String.class);
        validate.setAccessible(true);

        return IDS.stream().map(id -> DynamicTest.dynamicTest(id, () -> {
            JsonNode story = outputs.get(id);
            assertNotNull(story, "Missing story for " + id);
            assertTrue(story.isObject(), "Story must be a JSON object: " + id);

            String content = JSON.writeValueAsString(story);
            String rawApiResponse = JSON.writeValueAsString(Map.of(
                    "choices", List.of(Map.of(
                            "finish_reason", "stop",
                            "message", Map.of("content", content)))));

            Object result = validate.invoke(null, "200", rawApiResponse);
            Field schemaValidField = result.getClass().getDeclaredField("schemaValid");
            schemaValidField.setAccessible(true);
            assertTrue(schemaValidField.getBoolean(result), "Validator rejected corrected story " + id);

            UserStory.Builder builder = new UserStory.Builder(
                    story.path("role").asText(),
                    story.path("goal").asText(),
                    story.path("benefit").asText());
            if (story.hasNonNull("acceptanceCriteria")) {
                builder.acceptanceCriteria(readStringList(story.get("acceptanceCriteria")));
            }
            if (story.hasNonNull("priority")) {
                builder.priority(story.get("priority").asText());
            }
            if (story.hasNonNull("dependencies")) {
                builder.dependencies(readStringList(story.get("dependencies")));
            }
            if (story.hasNonNull("storyPoints")) {
                builder.storyPoints(story.get("storyPoints").intValue());
            }
            UserStory built = assertDoesNotThrow(builder::build);
            assertNotNull(built);
            assertFalse(built.toString().isBlank());
            if (story.hasNonNull("storyPoints")) {
                assertEquals(story.get("storyPoints").intValue(), built.getStoryPoints());
            } else {
                assertNull(built.getStoryPoints());
            }
        }));
    }

    private static List<String> readStringList(JsonNode value) {
        List<String> result = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode item : value) result.add(item.asText());
        } else if (value.isTextual()) {
            result.add(value.asText());
        }
        return result;
    }
}
