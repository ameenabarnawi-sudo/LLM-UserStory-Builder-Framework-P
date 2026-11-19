# LLM-UserStory-Builder-Framework

A Java framework that generates agile user stories with Large Language Models (LLMs)
and validates them through a Builder-pattern construction stage before evaluation.

## Pipeline

1. **Dataset preparation**: `dataset/PrepareMainDataset` builds the input set.
2. **LLM generation**: `llm/MainUserStoryGenerator` produces structured (JSON) user stories via the Groq API (Llama models).
3. **Validation and construction**: outputs are checked for JSON validity, required fields and schema, then built through the Builder.
4. **Evaluation**: `MainEvaluation` computes the summary metrics, and `evaluation/MainErrorAnalysis` analyzes the rejected outputs.
5. **Visualization**: `MainResultsVisualization`.

## Requirements

- Java 17+
- Maven 3.8+
- A Groq API key (only needed for the generation step)

## Running

```bash
# Evaluate the existing results (no API key needed)
mvn compile exec:java -Dexec.mainClass="edu.research.userstory.MainEvaluation"

# Generate new user stories (requires an API key)
export GROQ_API_KEY=your_key_here
mvn compile exec:java -Dexec.mainClass="edu.research.userstory.llm.MainUserStoryGenerator"
```

## Main evaluation results

| Metric | Value |
|---|---|
| Total unique inputs | 50 |
| JSON valid | 50 |
| Required fields complete | 50 |
| Schema valid | 42 |
| Builder construction successful | 42 / 42 |
| Rejected before Builder | 8 |
| Failed Builder attempts | 0 |

The full results are in `datasets/main_evaluation_summary.csv`.

## Repository structure
