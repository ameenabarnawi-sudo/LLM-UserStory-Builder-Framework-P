package edu.research.userstory;

import edu.research.userstory.builder.UserStory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserStoryBuilderTest {

    @Test
    void shouldBuildValidUserStory() {

        UserStory story = new UserStory.Builder(
                "customer",
                "track my order",
                "know its delivery status"
        ).build();

        assertNotNull(story);
        assertEquals("customer", story.getRole());
        assertEquals("track my order", story.getGoal());
        assertEquals(
                "know its delivery status",
                story.getBenefit()
        );
    }

    @Test
    void shouldRejectMissingRole() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new UserStory.Builder(
                        "",
                        "track my order",
                        "know its delivery status"
                ).build()
        );
    }

    @Test
    void shouldRejectMissingGoal() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new UserStory.Builder(
                        "customer",
                        " ",
                        "know its delivery status"
                ).build()
        );
    }

    @Test
    void shouldRejectMissingBenefit() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new UserStory.Builder(
                        "customer",
                        "track my order",
                        null
                ).build()
        );
    }

    @Test
    void shouldAcceptOptionalFields() {

        UserStory story = new UserStory.Builder(
                "administrator",
                "manage users",
                "maintain system security"
        )
                .acceptanceCriteria(
                        List.of("Admin can deactivate accounts")
                )
                .priority("High")
                .dependencies(List.of("Authentication"))
                .storyPoints(5)
                .build();

        assertEquals("High", story.getPriority());
        assertEquals(5, story.getStoryPoints());
        assertEquals(
                1, story.getAcceptanceCriteria().size()
        );
        assertEquals(
                List.of("Authentication"),
                story.getDependencies()
        );
    }

    @Test
    void shouldRejectZeroStoryPoints() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new UserStory.Builder(
                        "customer",
                        "track my order",
                        "know its delivery status"
                )
                        .storyPoints(0)
                        .build()
        );
    }

    @Test
    void shouldRejectNegativeStoryPoints() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new UserStory.Builder(
                        "customer",
                        "track my order",
                        "know its delivery status"
                )
                        .storyPoints(-3)
                        .build()
        );
    }

    @Test
    void shouldAllowMissingOptionalFields() {

        UserStory story = new UserStory.Builder(
                "customer",
                "view products",
                "compare available options"
        ).build();

        assertTrue(story.getAcceptanceCriteria().isEmpty());
        assertTrue(story.getDependencies().isEmpty());
        assertNull(story.getStoryPoints());
    }

    @Test
    void shouldPreventExternalListModification() {

        List<String> criteria = new ArrayList<>();
        criteria.add("Order status is visible");

        UserStory story = new UserStory.Builder(
                "customer",
                "track my order",
                "know its delivery status"
        )
                .acceptanceCriteria(criteria)
                .build();

        criteria.add("Unexpected change");

        assertEquals(
                1, story.getAcceptanceCriteria().size()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> story.getAcceptanceCriteria()
                        .add("New criterion")
        );
    }

    @Test
    void shouldFormatUserStoryCorrectly() {

        UserStory story = new UserStory.Builder(
                "customer",
                "track my order",
                "know its delivery status"
        ).build();

        assertEquals(
                "As a customer, I want track my order, " +
                "so that know its delivery status.",
                story.toString()
        );
    }
}
