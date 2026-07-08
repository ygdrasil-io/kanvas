# Task 4 Report - Wire Gradle Tasks

## What I implemented

- Added `tasks.register<JavaExec>("generateGpuPhase6CoverageFamiliesEvidence")` to `integration-tests/skia-evidence/build.gradle.kts`.
- Wired it to:
  - depend on `:integration-tests:skia:generateSkiaDashboard`
  - depend on `classes`
  - use `org.graphiks.kanvas.skia.evidence.Phase6CoverageFamiliesEvidenceCliKt`
  - read `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`
  - declare the expected coverage-family evidence outputs under `reports/gpu-renderer/phase-6-coverage-families/`
- Added the root alias `generateGpuPhase6CoverageFamiliesEvidence` to `build.gradle.kts`, delegating to the module task.

## Commands run and test results

- `rtk ./gradlew :integration-tests:skia-evidence:tasks --group verification`
  - Result: `BUILD SUCCESSFUL`
  - Verified `generateGpuPhase6CoverageFamiliesEvidence` is listed in the module's `verification` group.
- `rtk ./gradlew :integration-tests:skia-evidence:test`
  - Result: `BUILD SUCCESSFUL`
  - The existing Phase 6 coverage-family and image-family tests passed.

## Files changed

- `build.gradle.kts`
- `integration-tests/skia-evidence/build.gradle.kts`

## Self-review findings

- The new task matches the brief exactly in name, group, description, inputs, outputs, and CLI entry point.
- The root alias is present and delegates to the module task as requested.
- I did not modify renderer code or generate evidence artifacts.
- Existing IMAGE evidence behavior remains intact.

## Concerns

- None at this time.
