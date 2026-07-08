# Task 5 Report: Attach Resource Evidence to the Kotlin Evidence Module

## Scope completed

Implemented exactly Task 5 from `/Users/chaos/.codex/worktrees/eda6/kanvas/.superpowers/sdd/task-5-brief.md`:

- added failing tests first for resource evidence attachment and optional JSON loading;
- implemented `ResourceEvidenceReader.readIfPresent(root: Path): ResourceEvidence?`;
- reused shared top-level JSON helpers in `Phase6ImageFamilyEvidence.kt`;
- updated the CLI to attach optional resource evidence when present;
- updated `generateGpuPhase6ImageFamilyEvidence` to depend on `:gpu-renderer:generateGpuPhase6ImageResourceEvidence`;
- added the resource evidence JSON file as a task input;
- did not generate final artifacts from the task pipeline.

## TDD record

### Red

Added these tests to `Phase6ImageFamilyEvidenceTest.kt`:

- `resource evidence is attached when present`
- `resource evidence reader loads optional json file`

Ran:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
```

Observed expected failure:

- `Unresolved reference 'ResourceEvidenceReader'`

This confirmed the new tests were exercising the missing interface from the brief.

### Green

Implemented:

- `ResourceEvidenceReader` in `Phase6ImageFamilyEvidence.kt`
- `JsonObject.stringArray(key: String): List<String>`
- CLI wiring in `Phase6ImageFamilyEvidenceCli.kt`
- Gradle dependency/input wiring in `integration-tests/skia-evidence/build.gradle.kts`

Re-ran the same focused test target and it passed.

## Files changed

- `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt`
- `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt`
- `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt`
- `integration-tests/skia-evidence/build.gradle.kts`

## Validation evidence

### 1. Focused tests

Command:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
```

Result:

- build succeeded
- 8 tests passed
- includes both new resource evidence tests

### 2. Task visibility

Command:

```bash
rtk ./gradlew tasks --all | rg "generateGpuPhase6ImageResourceEvidence|generateGpuPhase6ImageFamilyEvidence"
```

Result:

- `generateGpuPhase6ImageFamilyEvidence`
- `integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence`
- `gpu-renderer:generateGpuPhase6ImageResourceEvidence`

This confirms the consumer task and producer task are both exposed as expected.

## Notes / concerns

- The resource evidence JSON remains optional by design; missing file returns `null` and does not block the CLI.
- No Task 6 artifact generation was performed.
