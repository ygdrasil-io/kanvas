# Task 5 Report

## Scope

Implemented atomic incremental GPU evidence promotion in `PromoteEvidenceCliRunner` while preserving the task 1-4 interfaces:

- `PromoteEvidenceCliRequest` still parses shared `EvidenceSelection`, now rejects `--rebaseline` without `--all`.
- `PromoteEvidenceCliRunner.run` now dispatches through `promoteSelected(request)`.
- Promotion writes one root-level v2 `catalog.json`, `environment.json`, and `promotion.json`.
- Selected promotion requires an existing valid promoted v2 catalogue and keeps unselected scene bytes unchanged.
- `--all --rebaseline` accepts changed root environments plus paired comparison summaries.
- Staged promotion verifies the complete v2 catalogue before the atomic swap, and rollback/cleanup behavior remains covered by tests.

## Implementation Notes

Updated `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt` to:

- verify the generated root first with `VerifyEvidenceCliRunner`;
- re-parse validated v2 catalogues to merge selected scene entries with the existing promoted catalogue;
- compare canonical serialized root environments on incremental promotion and fail with `gpu.evidence.environment-mismatch.requires-rebaseline` when they differ;
- copy the existing promoted root into a temporary sibling staging root, replace only selected scene directories, rewrite only the root metadata files, verify the full staged root, then atomically swap it into place.

## Tests

Red phase:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest' --tests '*EvidenceBundleWriterContractTest' --tests '*EvidenceCatalogVerifierTest'
```

Observed before implementation: `35 tests completed, 7 failed`, all in `PromoteEvidenceCliTest`, matching the missing promotion semantics from the brief.

Green phase:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest' --tests '*EvidenceBundleWriterContractTest' --tests '*EvidenceCatalogVerifierTest'
```

Observed after implementation: `BUILD SUCCESSFUL`.

Added/updated coverage in:

- `PromoteEvidenceCliTest`
- `EvidenceCatalogVerifierTest`
- `EvidenceBundleWriterContractTest`

Key assertions now covered:

- selected promotion leaves unselected scene bytes untouched;
- selected promotion rejects absent destinations, absent generated bundles, unknown generated scenes, environment drift, and staged verification failures without mutating the promoted root;
- `--all --rebaseline` accepts changed environments with required comparison summaries;
- root-level promotion metadata names only the promoted scene ids and no scene-local promotion metadata is written;
- late swap failure restores the old root byte-for-byte and failed rollback retains the backup for recovery.

## Self-review

Checked the final diff with:

```bash
rtk git diff --check
rtk git diff --stat -- integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCli.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/PromoteEvidenceCliTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceBundleWriterContractTest.kt integration-tests/gpu-evidence/src/test/kotlin/org/graphiks/kanvas/gpu/evidence/artifacts/EvidenceCatalogVerifierTest.kt
```

No whitespace/errors from `git diff --check`.

## Concerns

No functional concerns found in the implemented scope. The Gradle run still reports pre-existing deprecation warnings about Gradle 10 compatibility, but the targeted suite passed.

## Review Fixes

Follow-up fixes applied after review:

- `PromoteEvidenceCliRequest.parse` now resolves explicit selections against `GpuEvidenceCatalog.cases`, so unknown scene ids fail during argument parsing while `--all` remains unchanged.
- Staged root validation now checks `promotion.json` against the active request, including `rebaseline`, `sceneIds`, `reviewer`, `reason`, and paired comparison summaries; corrupted `--all --rebaseline` metadata now fails before swap.
- An existing empty `reports/gpu-renderer/evidence/correctness/promoted` directory is now treated as an empty destination for initial `--all` promotion, while selected promotion and rebaseline still require an existing non-empty valid catalogue.

Review-specific red/green:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest.promotion request parser rejects unknown explicit scene ids' --tests '*PromoteEvidenceCliTest.initial all promotion accepts an empty promoted directory' --tests '*PromoteEvidenceCliTest.all rebaseline rejects corrupted root promotion metadata before swap'
```

Observed before fixes: `3 tests completed, 3 failed`.

Observed after fixes: `BUILD SUCCESSFUL`.

Post-fix regression coverage:

```bash
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --tests '*PromoteEvidenceCliTest' --tests '*EvidenceBundleWriterContractTest' --tests '*EvidenceCatalogVerifierTest'
```

Observed after fixes: `BUILD SUCCESSFUL`.
