# Task 6 Report

## Scope

Implemented the mechanical promoted-root migration from GPU evidence v1 to v2 without changing rendering, oracle, threshold, refusal, PNG-in-Git, or headless/offscreen semantics:

- added `MigratePromotedEvidenceCliRunner.run(args: Array<String>): Int` with `--repository-root`, `--reviewer`, and `--reason`;
- added Gradle task `migratePromotedGpuEvidenceV1ToV2`;
- kept `VerifyEvidenceCliRunner` dual-read for promoted roots by allowing `--allow-historical-commit` to remain harmless on v2 roots;
- added focused migration tests for success, missing/invalid/mixed roots, malformed metadata, and swap/cleanup failures.

## Implementation Notes

Added `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/MigratePromotedEvidenceCli.kt` with the following behavior:

- resolves only `reports/gpu-renderer/evidence/correctness/promoted`;
- preflights the existing root with `VerifyEvidenceCliRunner --allow-historical-commit --all` before creating any staged destination, which enforces a complete v1 catalogue, valid scene ids, pass/refusal semantics, coherent environment identity, and valid per-scene promotion metadata;
- reads each v1 manifest/environment/promotion record, preserves the scene id and manifest `sourceCommit`, and rewrites only the scene manifest to `gpu-evidence-scene-v2`;
- copies each scene directory byte-for-byte into a temporary sibling root, compares SHA-256 values for every present `cpu.png`, `skia.png`, `gpu.png`, and `diff.png`, then removes scene-level `environment.json` and `promotion.json`;
- writes one root `environment.json`, one root `promotion.json` with `rebaseline: true`, the requested reviewer/reason, and all migrated scene ids sorted, then writes one root `catalog.json`;
- validates the staged v2 root with `VerifyEvidenceCliRunner --all` and swaps it atomically with rollback/backup behavior patterned after Task 5 promotion.

Updated `integration-tests/gpu-evidence/build.gradle.kts` to register `migratePromotedGpuEvidenceV1ToV2`, depend on `classes`, reuse `promotionReviewer` / `promotionReason`, and declare the promoted-root input plus correctness-root output area used by sibling staging.

Updated `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/VerifyEvidenceCli.kt` so promoted v2 verification still succeeds when the existing `verifyPromotedGpuEvidence` task supplies `--allow-historical-commit`.

## Tests

Red phase:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest' --tests '*VerifyEvidenceCliTest'
```

Observed before implementation: Kotlin compilation failed with unresolved `MigratePromotedEvidenceCliRunner`, matching the missing CLI/task seam from the brief.

Green phase:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest' --tests '*VerifyEvidenceCliTest'
```

Observed after implementation: `BUILD SUCCESSFUL`.

Directly affected regression:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest'
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:help --task migratePromotedGpuEvidenceV1ToV2
```

Observed after implementation: both commands succeeded; `help` reported the new `migratePromotedGpuEvidenceV1ToV2` `JavaExec` task in group `verification`.

## Self-review

Checked the final work with:

```bash
rtk git diff --check
rtk git status --short
rtk git diff --stat
```

`git diff --check` was clean. `git status --short` confirmed the expected five code/test/build changes plus this report. `git diff --stat` showed the tracked-file delta; the two new migration files were additionally confirmed through status and the passing targeted test run.

## Concerns

No functional concerns in the implemented scope. The Gradle runs still emit the pre-existing Gradle 10 deprecation warning and restricted native-access warning, but all requested verification commands passed.

## Fix Round 1

Addressed the review follow-ups in the migration rollback and validation path:

- strengthened `swapPromotedRoot` so rollback attempts restoration whenever the backup root exists, even if the failed staged install already left a destination path behind;
- delete any partial destination before restore, then move the backup back with the same atomic-to-non-atomic fallback helper used for swap;
- preserved diagnostics when restoration itself fails by suppressing the restore error onto the primary failure and keeping the backup-path stderr message;
- added the explicitly requested mixed-v1-environments test and asserted the migration rejects before `beforeStagedVerification` runs;
- removed validation drift by routing migration-side v1 `promotion.json` checks through `EvidenceBundleVerifier.verifyHistoricalPromotionRecord`, which also enforces the same `rebaseline` and nonblank comparison-summary relationships as historical verification.

Fix-round red phase:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest.migration rejects mixed v1 environments before staging' --tests '*MigratePromotedEvidenceCliTest.migration rejects invalid v1 rebaseline promotion metadata without mutating v1' --tests '*MigratePromotedEvidenceCliTest.partial non-atomic swap failure restores the original v1 root byte for byte'
```

Observed before fixes: `3 tests completed, 1 failed`; the new partial/non-atomic swap test failed, demonstrating that rollback skipped restoration when a destination already existed after the failed staged install.

Fix-round green phase:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest.migration rejects mixed v1 environments before staging' --tests '*MigratePromotedEvidenceCliTest.migration rejects invalid v1 rebaseline promotion metadata without mutating v1' --tests '*MigratePromotedEvidenceCliTest.partial non-atomic swap failure restores the original v1 root byte for byte'
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*MigratePromotedEvidenceCliTest' --tests '*VerifyEvidenceCliTest'
```

Observed after fixes: both commands returned `BUILD SUCCESSFUL`.

Fix-round self-review:

```bash
rtk git diff --check
rtk git diff --stat
```

`git diff --check` remained clean. The fix-round diff touched only `EvidenceBundleVerifier.kt`, `MigratePromotedEvidenceCli.kt`, `MigratePromotedEvidenceCliTest.kt`, and this report append.
