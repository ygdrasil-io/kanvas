# Task 5 — source retagging workflow status

Date: 2026-07-10

## Commit scope completed

- The scanner accepts explicit registry indices, preserves original indices in
  selections, and can list the currently `BLOCKING` registry entries.
- Gradle exposes the scanner index and blocking-list flags.
- Measurement orchestration selects and aggregates GMs by registry index,
  rather than display name, including duplicate-name fallback coverage.
- The generated reclassification report and raw measurement evidence are kept
  under `reports/skia-gm-render-cost/`.

## Source retagging status

The source retagging portion is deliberately not included in this partial
commit. The report contains 511 rows: 475 targets to retag (`FAST`: 460,
`MEDIUM`: 14, `SLOW`: 1) and 36 `BLOCKING` rows to leave unchanged. No Kotlin
`renderCost` declaration was edited in this commit, and the required
registry-to-source patcher plus exhaustive source validation were not run.

This state was committed at the user's interruption request before the
retagger implementation started. Therefore this commit must not be treated as
evidence that non-blocking rows were retagged or that blocking rows remained
unchanged after retagging.

## Validation performed

- `python3 -m unittest scripts/gm/test_gm_measure_blocking.py` — 10 tests
  passed.
- `bash scripts/gm/gm-measure-blocking.sh --self-test` — passed; its timeout
  fixture retained one scanner record and fell back to four registry indices.
- `./gradlew --no-daemon :integration-tests:skia:test --tests
  org.graphiks.kanvas.skia.SkiaGmScannerTest --tests
  org.graphiks.kanvas.skia.SkiaGmRunnerFilterTest --tests
  org.graphiks.kanvas.skia.SkiaRenderGeneratorFilterTest` — build successful;
  17 focused JVM tests passed.
- `git diff --check` — passed before staging.

## Remaining work

Implement and test a source retagger that resolves each report
`registryIndex` through the service registry to the exact Kotlin class,
including multiple classes per file; apply only `FAST`, `MEDIUM`, and `SLOW`;
then validate all 511 source declarations and review the resulting Git diff.
