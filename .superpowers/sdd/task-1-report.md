# Task 1 — Material Classifier And Report Model

## What I implemented
- Added `Phase6MaterialFamilyEvidence.kt` in `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence`.
- Added `Phase6MaterialFamilyEvidenceTest.kt` in `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence`.
- Implemented the Task 1 model layer:
  - `Phase6MaterialFamiliesEvidence`
  - `Phase6MaterialSummary`
  - `Phase6MaterialFamilyDelta`
  - `Phase6MaterialRowEvidence`
  - `Phase6MaterialFamilyClassifier` with `classify(...)` and `buildEvidence(...)`
- Implemented material family filtering for `GRADIENT`, `RUNTIME_EFFECT`, `COLOR` only.
- Implemented subfamily classification for gradient/runtimе-effect/color rows, fallback reason mapping, no-score handling, and stable row IDs for duplicates.
- Implemented family deltas vs baseline and summary aggregation.

## What I tested and results
- Ran focused test target:
  - `./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6MaterialFamilyEvidenceTest" --rerun-tasks`
- Iteration 1: failed on `classifies material gates with stable reasons()` due to conflicting keyword ordering for
  `gradients_color_space_many_stops`; fixed by prioritizing `many stops` detection before `color space` detection.
- Iteration 2: all focused tests passed.

## TDD Evidence
- **RED**
  - Added tests first from task brief.
  - First run of focused test command failed on one assertion in `classifies material gates with stable reasons()`.
  - Failure showed `many-stops` row was classified as `gradient-color-space-gated` instead of `gradient-many-stops-gated`.
- **GREEN**
  - Updated `gradientSubfamily` ordering and re-ran the same focused test command.
  - Result: `BUILD SUCCESSFUL` for `Phase6MaterialFamilyEvidenceTest`.

## Files changed
- `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidence.kt`
- `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidenceTest.kt`

## Self-review findings
- Implemented exactly the requested classifier/evidence model shape and behavior requested by the Task 1 brief.
- Kept implementation confined to the two assigned files and aligned with existing IMAGE/COVERAGE classifier patterns.
- No unrelated files changed.
- No hidden fallbacks added.
- No broad support claims introduced in `nonClaims`.
- No dependency/plugin or backend changes touched.

## Issues or concerns
- Aucun point bloquant.
- Seule divergence mineure de l’extrait fourni : priorité de la règle `manystops/manycolors` dans `gradientSubfamily` a été ajustée pour respecter le comportement attendu par le test (`gradients_color_space_many_stops` -> `gradient-many-stops-gated`).

## Fix pass (post-review)
- Updated `Phase6MaterialFamilyEvidence.kt` to remove the excluded family name from serialized `nonClaims` output by replacing:
  - `COMPOSITE blend, ...` → `Blend composition, ...`.
- Kept `GRADIENT`, `RUNTIME_EFFECT`, and `COLOR` behavior unchanged.
- Added targeted coverage in `Phase6MaterialFamilyEvidenceTest.kt` for:
  - `familyDeltas` baseline comparison (`build evidence computes family deltas from baseline`)
  - stable duplicate row IDs (`build evidence assigns stable row ids for duplicate names`)
  - non-claims excluding excluded family identifiers (`non-claims do not mention excluded families`).
- Focused test run (after fix):
  - Command: `rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6MaterialFamilyEvidenceTest" --rerun-tasks`
  - Result: `BUILD SUCCESSFUL` (all `Phase6MaterialFamilyEvidenceTest` cases passed).
