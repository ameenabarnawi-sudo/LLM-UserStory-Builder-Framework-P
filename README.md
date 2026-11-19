# A Builder-Based Framework for Structured Generation and Validation of Agile User Stories Using Large Language Models

**Aminah A. Barnawi** and **Abdulmajeed Al-Juhani**
Department of Computer Science, College of Computer Science and Engineering, Taibah University, Madinah, Saudi Arabia

This repository contains the Java implementation, datasets, and recorded results for the study.
The framework turns natural-language descriptions into JSON user stories using an LLM, validates them against a schema, and builds Java `UserStory` objects only from the outputs that pass validation, using the Builder pattern.

## Framework stages

1. **Input preparation**: natural-language descriptions are assigned the IDs `M001`–`M050` (`dataset/PrepareMainDataset`).
2. **LLM generation**: a zero-shot structured prompt produces one JSON user story per input (`llm/MainUserStoryGenerator`).
   - Required fields: `role`, `goal`, `benefit`
   - Optional fields: `acceptanceCriteria`, `priority`, `dependencies`, `storyPoints`
3. **Schema validation**: checks JSON syntax, the required fields, data types, and field constraints. `storyPoints`, when present, must be a positive integer. Rejected outputs are logged with their errors.
4. **Builder construction**: `UserStory.Builder` creates objects from the accepted outputs only (`MainEvaluation`).

Additional tools: `evaluation/MainErrorAnalysis` (field-level error analysis) and `MainResultsVisualization` (figures).

## Experimental configuration

| Setting | Value |
|---|---|
| Model | `openai/gpt-oss-20b` (GPT-OSS) |
| API provider | Groq |
| Prompting | Zero-shot structured prompting |
| Temperature | 0 |
| Max output tokens | 1200 |
| Fine-tuning | None |
| Inputs | 50 (from a 101-record UStAI subset, 31 domains) |
| Sampling seed | 20261110 (domain-balanced) |

Implementation stack: Java 17, Maven, Jackson (JSON), Apache Commons CSV, JUnit 5.

## Requirements

- Java 17+
- Maven 3.8+
- A Groq API key (needed only to regenerate outputs)

## Running

```bash
# Reproduce the evaluation from the saved outputs (no API key needed)
mvn compile exec:java -Dexec.mainClass="edu.research.userstory.MainEvaluation"

# Error analysis
mvn compile exec:java -Dexec.mainClass="edu.research.userstory.evaluation.MainErrorAnalysis"

# Regenerate the user stories (requires an API key)
export GROQ_API_KEY=your_key_here
mvn compile exec:java -Dexec.mainClass="edu.research.userstory.llm.MainUserStoryGenerator"
```

The API key is read only from the environment variable. It is never stored in the code.

## Results

### Structural validation and construction (E1–E3)

| Stage | Metric | Count | Rate |
|---|---|---|---|
| E1 | JSON Validity (JVR) | 50/50 | 100% |
| E1 | Required-Field Completeness (RFCR) | 50/50 | 100% |
| E2 | Schema Compliance (SCR) | 42/50 | 84% |
| E2 | Schema Rejection | 8/50 | 16% |
| E3 | Overall Construction Success (OCSR) | 42/50 | 84% |
| E3 | Attempted Construction Success (ACSR) | 42/42 | 100% |

### Rejected outputs (all caused by `storyPoints`)

| Input IDs | Recorded value | Error |
|---|---|---|
| M011, M027, M048, M050 | `"5"` | Numeric string |
| M016, M034 | `"8"` | Numeric string |
| M013, M014 | `""` | Empty string |

### Rubric quality assessment (42 accepted stories, scores 0–2 per criterion)

| Criterion | Initial AI-assisted | Claude | Author (human) |
|---|---|---|---|
| Alignment | 91.67% | 94.05% | 96.43% |
| Clarity | 75.00% | 97.62% | 98.81% |
| Testability | 67.86% | 61.90% | 67.86% |
| Consistency | 89.29% | 83.33% | 89.29% |
| **Overall** | **80.95%** | **84.23%** | **88.10%** |

These are descriptive ordinal scores, not accuracy measures.

### Builder vs. direct-constructor baseline

Both paths constructed 42/42 objects, and all seven fields matched in every paired case. This shows functional equivalence on the validated inputs, not a Builder advantage in construction success.

## Limitations

- 50 inputs and a single LLM. The inputs are research abstracts, not stakeholder-authored requirements.
- The human ratings come from one author and were not blinded.
- `run_storypoints_correction.py` is an archived offline correction experiment. It is not part of the main pipeline and is not evidence of automatic repair.

## Repository structure

```
src/main/java/edu/research/userstory/   source code
datasets/                               inputs, LLM outputs, validation and evaluation CSVs
results/                                generated results
pom.xml                                 Maven configuration
```

## Data source

The input descriptions come from the UStAI dataset:

> A. Yamani, M. Baslyman, M. Ahmed, "Leveraging LLMs for User Stories in AI Systems: UStAI Dataset," *PROMISE 2025*, pp. 21–30. DOI: [10.1145/3727582.3728689](https://doi.org/10.1145/3727582.3728689)

## Citation

```
A. A. Barnawi and A. Al-Juhani, "A Builder-Based Framework for Structured Generation
and Validation of Agile User Stories Using Large Language Models," Taibah University, 2026.
```

## Contact

Corresponding author: Abdulmajeed Al-Juhani (ABALJUHANI@taibahu.edu.sa)
