package edu.research.userstory.builder;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public final class UserStory {

    private final String role;
    private final String goal;
    private final String benefit;

    private final List<String> acceptanceCriteria;
    private final String priority;
    private final List<String> dependencies;
    private final Integer storyPoints;

    private UserStory(Builder builder) {
        this.role = builder.role;
        this.goal = builder.goal;
        this.benefit = builder.benefit;

        this.acceptanceCriteria = Collections.unmodifiableList(
                new ArrayList<>(builder.acceptanceCriteria));

        this.priority = builder.priority;

        this.dependencies = Collections.unmodifiableList(
                new ArrayList<>(builder.dependencies));

        this.storyPoints = builder.storyPoints;
    }

    public String getRole() {
        return role;
    }

    public String getGoal() {
        return goal;
    }

    public String getBenefit() {
        return benefit;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public String getPriority() {
        return priority;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public Integer getStoryPoints() {
        return storyPoints;
    }

    public static class Builder {

        private final String role;
        private final String goal;
        private final String benefit;

        private List<String> acceptanceCriteria =
                new ArrayList<>();

        private String priority = "";

        private List<String> dependencies =
                new ArrayList<>();

        private Integer storyPoints = null;

        public Builder(
                String role,
                String goal,
                String benefit
        ) {
            this.role = role;
            this.goal = goal;
            this.benefit = benefit;
        }

        public Builder acceptanceCriteria(
                List<String> criteria
        ) {
            this.acceptanceCriteria =
                    new ArrayList<>(criteria);
            return this;
        }

        public Builder priority(String priority) {
            this.priority = priority;
            return this;
        }

        public Builder dependencies(
                List<String> dependencies
        ) {
            this.dependencies =
                    new ArrayList<>(dependencies);
            return this;
        }

        public Builder storyPoints(Integer points) {
            this.storyPoints = points;
            return this;
        }

        public UserStory build() {

            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException(
                        "Role is required");
            }

            if (goal == null || goal.isBlank()) {
                throw new IllegalArgumentException(
                        "Goal is required");
            }

            if (benefit == null || benefit.isBlank()) {
                throw new IllegalArgumentException(
                        "Benefit is required");
            }

            if (storyPoints != null &&
                    storyPoints <= 0) {
                throw new IllegalArgumentException(
                        "Story points must be positive");
            }

            return new UserStory(this);
        }
    }

    // Format the story without repeating phrases already supplied by the LLM.
    // This affects presentation only; the stored fields remain unchanged.
    private static String removeLeadingPhrase(String value, String regex) {
        return value.trim().replaceFirst(regex, "").trim();
    }

    @Override
    public String toString() {
        String trimmedRole = role.trim();
        String rolePhrase;
        if (trimmedRole.matches("(?is)^as\\s+an?\\s+.+")) {
            rolePhrase = trimmedRole;
        } else {
            rolePhrase = "As a " + trimmedRole;
        }

        String cleanGoal = removeLeadingPhrase(goal, "(?i)^I\\s+want\\s+");
        String cleanBenefit = removeLeadingPhrase(benefit, "(?i)^so\\s+that\\s+");
        cleanBenefit = cleanBenefit.replaceFirst("[.!?]+$", "").trim();

        return rolePhrase + ", I want " + cleanGoal
                + ", so that " + cleanBenefit + ".";
    }
}
