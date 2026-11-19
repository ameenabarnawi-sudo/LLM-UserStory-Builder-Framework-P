package edu.research.userstory.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.research.userstory.llm.UserStoryValidator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserStoryValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String VALID_STORY = """
        {
          "role": "customer",
          "goal": "track my order",
          "benefit": "know the delivery status"
        }
        """;

    private Object validate(String status, String raw)
            throws Exception {

        Method method = UserStoryValidator.class
                .getDeclaredMethod(
                        "validate", String.class, String.class);

        method.setAccessible(true);
        return method.invoke(null, status, raw);
    }

    private String apiResponse(String content, String finish)
            throws Exception {

        var response = JSON.createObjectNode();
        var choice = JSON.createObjectNode();
        var message = JSON.createObjectNode();

        message.put("content", content);
        choice.put("finish_reason", finish);
        choice.set("message", message);
        response.putArray("choices").add(choice);

        return JSON.writeValueAsString(response);
    }

    private Object checkStory(String content) throws Exception {
        return validate("200", apiResponse(content, "stop"));
    }

    private boolean flag(Object result, String field)
            throws Exception {

        Field f = result.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getBoolean(result);
    }

    @SuppressWarnings("unchecked")
    private List<String> errors(Object result) throws Exception {

        Field f = result.getClass().getDeclaredField("errors");
        f.setAccessible(true);
        return (List<String>) f.get(result);
    }

    @Test
    void validStoryPasses() throws Exception {
        Object r = checkStory(VALID_STORY);
        assertTrue(flag(r, "jsonValid"));
        assertTrue(flag(r, "requiredComplete"));
        assertTrue(flag(r, "schemaValid"));
    }

    @Test
    void apiErrorIsRejected() throws Exception {
        Object r = validate("500", "{}");
        assertFalse(flag(r, "schemaValid"));
        assertTrue(errors(r).contains("API_ERROR_500"));
    }

    @Test
    void invalidApiJsonIsRejected() throws Exception {
        Object r = validate("200", "{invalid");
        assertTrue(errors(r).contains("INVALID_API_RESPONSE_JSON"));
    }

    @Test
    void missingChoicesIsRejected() throws Exception {
        Object r = validate("200", "{\"choices\":[]}");
        assertTrue(errors(r).contains("MISSING_CHOICES"));
    }

    @Test
    void invalidStoryJsonIsRejected() throws Exception {
        Object r = checkStory("{invalid");
        assertFalse(flag(r, "jsonValid"));
        assertTrue(errors(r).contains("INVALID_STORY_JSON"));
    }

    @Test
    void jsonArrayIsRejected() throws Exception {
        Object r = checkStory("[]");
        assertFalse(flag(r, "schemaValid"));
        assertTrue(errors(r).contains("JSON_NOT_OBJECT"));
    }

    @Test
    void missingRoleIsRejected() throws Exception {
        Object r = checkStory("""
            {"goal":"track orders","benefit":"save time"}
            """);
        assertFalse(flag(r, "requiredComplete"));
        assertTrue(errors(r).contains("MISSING_OR_INVALID_role"));
    }

    @Test
    void missingGoalIsRejected() throws Exception {
        Object r = checkStory("""
            {"role":"customer","benefit":"save time"}
            """);
        assertTrue(errors(r).contains("MISSING_OR_INVALID_goal"));
    }

    @Test
    void missingBenefitIsRejected() throws Exception {
        Object r = checkStory("""
            {"role":"customer","goal":"track orders"}
            """);
        assertTrue(errors(r).contains("MISSING_OR_INVALID_benefit"));
    }

    @Test
    void blankRequiredFieldIsRejected() throws Exception {
        Object r = checkStory("""
            {"role":" ","goal":"track orders","benefit":"save time"}
            """);
        assertFalse(flag(r, "requiredComplete"));
    }

    @Test
    void invalidStoryPointsTypeIsRejected() throws Exception {
        Object r = checkStory("""
            {
              "role":"customer",
              "goal":"track orders",
              "benefit":"save time",
              "storyPoints":"five"
            }
            """);
        assertFalse(flag(r, "schemaValid"));
        assertTrue(errors(r).contains("INVALID_TYPE_storyPoints"));
    }

    @Test
    void negativeStoryPointsAreRejected() throws Exception {
        Object r = checkStory("""
            {
              "role":"customer",
              "goal":"track orders",
              "benefit":"save time",
              "storyPoints":-3
            }
            """);
        assertFalse(flag(r, "schemaValid"));
    }

    @Test
    void invalidAcceptanceCriteriaAreRejected() throws Exception {
        Object r = checkStory("""
            {
              "role":"customer",
              "goal":"track orders",
              "benefit":"save time",
              "acceptanceCriteria":[true, 5]
            }
            """);
        assertTrue(errors(r).contains(
                "INVALID_TYPE_acceptanceCriteria"));
    }

    @Test
    void validOptionalFieldsPass() throws Exception {
        Object r = checkStory("""
            {
              "role":"customer",
              "goal":"track orders",
              "benefit":"save time",
              "acceptanceCriteria":["Order status is visible"],
              "priority":"High",
              "dependencies":["Order service"],
              "storyPoints":5
            }
            """);
        assertTrue(flag(r, "schemaValid"));
    }

    @Test
    void incompleteGenerationIsRejected() throws Exception {
        Object r = validate(
                "200", apiResponse(VALID_STORY, "length"));

        assertTrue(flag(r, "jsonValid"));
        assertFalse(flag(r, "schemaValid"));
        assertTrue(errors(r).contains("FINISH_REASON_length"));
    }
}
