# GPU Evidence Incremental Promotion

**Status:** Proposed
**Date:** 2026-08-26
**Scope:** `integration-tests:gpu-evidence` correctness evidence only

## Context

Kanvas keeps GPU correctness evidence as checked-in, headless/offscreen
artifacts. A promoted scene currently contains its images and several JSON
files. The generated runner can already select one scene with `--scene`, but
verification and promotion are catalogue-wide operations.

The current v1 bundle also repeats run-level metadata in every scene:

- `environment.json` repeats the host and adapter identity;
- `manifest.json` repeats the source commit and generation timestamp;
- `promotion.json` repeats the same review metadata for every scene.

The current promoted snapshot is approximately 2.8 MiB and contains 714
tracked files. The latest translated-RRect promotion changed 244 paths, of
which 232 were JSON files and only 12 were PNG files. This demonstrates that
the primary cost is metadata churn and whole-catalogue replacement, not the
pixel evidence itself.

The active renderer target requires reference/CPU/GPU or refusal evidence,
diff/stat artifacts, route diagnostics, stable fallback policy, and
reproducible headless validation. The design must preserve those guarantees.

## Goals

1. Generate, verify, and promote one or more selected scenes without running
   or rewriting the rest of the catalogue.
2. Keep promoted PNG evidence in Git for reviewability and auditability.
3. Ensure an unchanged scene remains byte-for-byte unchanged during an
   incremental promotion.
4. Remove duplicated run-level metadata from scene directories.
5. Preserve complete-catalogue validation for CI and release checks.
6. Keep promotion transactional: a failed validation must not partially update
   the checked-in catalogue.
7. Keep v1 evidence readable during the migration window and make the
   one-time format transition explicit.

## Non-goals

- Moving PNGs to an external artifact store or introducing Git LFS.
- Changing GPU rendering, CPU oracle, comparison thresholds, route IDs, or
  refusal semantics.
- Making performance evidence part of this correctness-bundle migration.
- Inferring affected scenes from source-code dependency analysis. Selection is
  explicit and reviewable.
- Reintroducing native windowing or any non-headless GPU dependency.

## Alternatives considered

### A. Keep v1 and only add partial commands

This has the smallest implementation cost, but v1 requires a common source
commit and repeats environment/promotion metadata per scene. It cannot safely
represent an incremental catalogue whose scenes were captured at different
commits without continuing the same metadata churn.

### B. Incremental v2 catalogue with root metadata — recommended

Keep scene evidence in Git, introduce a root catalogue and root run metadata,
and make promotion merge selected scene directories into an atomically staged
copy of the existing catalogue. This removes the source of the current churn
while preserving local review and independent verification.

### C. External or content-addressed artifact storage

This could reduce Git object growth further, but adds storage availability,
retention, authentication, and review indirection. The current snapshot is
small enough that those operational costs are not justified. It remains a
future option if the promoted pixel corpus grows materially.

## Proposed v2 layout

The promoted root becomes:

```text
reports/gpu-renderer/evidence/correctness/promoted/
  catalog.json
  environment.json
  promotion.json
  <scene-id>/
    manifest.json
    cpu.png | skia.png
    gpu.png
    diff.png
    stats.json
    route.json
    diagnostics.json
```

The scene keeps the evidence that belongs to the scene. `manifest.json`
remains the integrity boundary for its files, but v2 removes run-level
`sourceCommit` and `generatedAtUtc` fields from that manifest. The root
catalogue owns the source commit for each scene, so unchanged scenes may keep
their original evidence commit.

### `catalog.json`

`catalog.json` is deterministic: keys and scene entries are sorted, and it
contains no wall-clock timestamp. Its conceptual shape is:

```json
{
  "schemaVersion": "gpu-evidence-catalog-v2",
  "environment": "environment.json",
  "promotion": "promotion.json",
  "scenes": [
    {
      "sceneId": "solid-triangle-path",
      "sourceCommit": "0123456789abcdef0123456789abcdef01234567",
      "manifest": "solid-triangle-path/manifest.json",
      "manifestSha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    }
  ]
}
```

The production verifier still derives expected scene descriptors from
`GpuEvidenceCatalog`; `catalog.json` is not allowed to add, remove, or rename
catalogue cases in a complete promoted root. A generated staging root may be a
strict subset, but every entry must still be a known case.

### `environment.json`

The root environment records the common execution identity once. It contains
host, OS, JDK, device-generation, capability implementation, and adapter
fields. It no longer uses the code source commit as its identity because a v2
catalogue can legitimately contain scene evidence from multiple commits.

An incremental promotion is accepted only when its generated environment is
identical to the existing promoted environment. A mismatch produces the stable
diagnostic `gpu.evidence.environment-mismatch.requires-rebaseline` and
requires an explicit full rebaseline. This preserves the current requirement
that one promoted catalogue represents one coherent GPU execution environment.

### `promotion.json`

Promotion metadata is recorded once at the root:

```json
{
  "schemaVersion": "gpu-evidence-promotion-v2",
  "promotedAtUtc": "2026-08-26T15:55:32Z",
  "reviewer": "example-reviewer",
  "reason": "translated-rrect-route-proof",
  "rebaseline": false,
  "sceneIds": ["solid-triangle-path"],
  "priorComparison": null,
  "newComparison": null
}
```

The timestamp is therefore changed once per promotion rather than once per
scene. Git history remains the audit trail for previous promotion events.

## Selection and command behavior

The CLI receives an explicit selection model:

- `--scene <id>` selects one scene;
- repeated `--scene <id>` or `--scenes-file <path>` selects a batch;
- `--all` is required for a complete catalogue operation.

The Gradle tasks expose the same model through the existing `-Pscene=<id>`
property and a new `-PscenesFile=<path>` property. Existing full-gate tasks
invoke `--all` internally, so CI behavior remains catalogue-wide and explicit.

Examples:

```text
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit="$COMMIT_SHA" -Pscene=clip-path-inverse-axis-x-translated-solid-rrect

rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence \
  -PsourceCommit="$COMMIT_SHA" -Pscene=clip-path-inverse-axis-x-translated-solid-rrect

rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence \
  -PsourceCommit="$COMMIT_SHA" -Pscene=clip-path-inverse-axis-x-translated-solid-rrect \
  -PpromotionReviewer="$REVIEWER" -PpromotionReason="$PROMOTION_REASON"
```

Selected promotion is the daily workflow. When a complete promoted catalogue
already exists, full promotion is an explicit rebaseline and must include
`--all`, `--rebaseline`, and both comparison summaries; a bare full promotion
is rejected. The Gradle equivalent is:

```text
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence \
  -PsourceCommit="$COMMIT_SHA" -Pall=true \
  -PpromotionReviewer="$REVIEWER" -PpromotionReason="$PROMOTION_REASON" \
  -PpromotionRebaseline=true \
  -PpromotionPriorComparison="$PRIOR_COMPARISON" \
  -PpromotionNewComparison="$NEW_COMPARISON"
```

Generation writes only selected bundles below the ignored generated root.
Verification has two modes:

- selected mode verifies exactly the selected generated bundles and their
  v2 staging metadata;
- `--all` verifies the complete generated or promoted catalogue.

Promotion in selected mode requires every selected generated bundle to be
present and rejects unknown, duplicate, or unselected generated scene data.
It copies the existing promoted catalogue into a staging directory, replaces
only selected scene directories, updates the root catalogue and promotion
metadata, verifies the complete staged catalogue, then atomically swaps the
root. The existing promoted scene directories are not regenerated or copied
back through the scene writer.

If the promoted root does not exist, selected promotion is rejected unless
the selection is `--all`; the initial catalogue must be complete.

## Verification contracts

The v2 verifier must enforce:

1. Root and scene paths contain no symlinks and remain below the repository
   root.
2. A complete promoted root contains exactly the code-derived catalogue IDs.
3. A selected generated root contains exactly its declared selected IDs.
4. Every scene manifest agrees with its code-derived descriptor, oracle,
   expected route, dimensions, comparison policy, and observed outcome.
5. Every manifest hash matches the actual scene files.
6. `stats.json`, `route.json`, and `diagnostics.json` preserve the current
   submission, route, refusal, and pixel-comparison invariants.
7. Every scene entry has a valid 40-character source commit.
8. The root environment is valid and all incremental input environments are
   identical to it.
9. Root promotion metadata lists only the scenes changed by that promotion.
10. The complete promoted verifier does not require all scenes to share one
    source commit; it uses the commit recorded by each catalogue entry.

The v1 verifier remains available for generated/historical bundles during the
migration window. It must not silently reinterpret v1 as v2.

## Migration plan

### Phase 1 — dual-read and selection contracts

- Introduce a typed `EvidenceSelection` model shared by generation,
  verification, and promotion.
- Add selected verification and explicit `--all` handling.
- Add tests for one-scene and multi-scene selection, unknown IDs, duplicates,
  missing generated scenes, and complete-gate behavior.
- Keep the existing v1 promoted root unchanged.

### Phase 2 — v2 writers and verifier

- Add v2 scene, catalogue, environment, and promotion models.
- Make generation produce v2 staging roots.
- Make the verifier support both v1 and v2, with separate schema paths.
- Add full-catalogue and incremental-catalogue verification tests.

### Phase 3 — one-time checked-in migration

- Verify the existing v1 promoted root completely.
- Convert its scene bundles to v2 without rerendering pixels.
- Extract the common environment and create one root promotion record.
- Record the migration as a dedicated commit because it necessarily touches
  existing metadata files once.
- Run the full promoted verifier and compare all PNG SHA-256 values before and
  after migration.

### Phase 4 — incremental promotion by default

- Change normal promotion to selected mode.
- Keep full rebaseline behind explicit `--all --rebaseline` and comparison
  metadata.
- Add a no-unrelated-changes policy test that snapshots all unselected scene
  files and asserts byte equality after promotion.
- Keep `gpuEvidenceCorrectness` and `gpuEvidenceVerification` as full
  catalogue gates.

## Failure handling and rollback

Generation may leave ignored failed-attempt diagnostics as it does today.
Promoted state must never be updated before independent verification succeeds.

Selected promotion uses the existing staged-root and atomic-swap strategy:

1. validate request and selected generated bundles;
2. validate environment compatibility;
3. copy the existing promoted root to a temporary sibling;
4. replace only selected scenes and update root metadata;
5. verify the full temporary root;
6. atomically replace the promoted root and clean the backup.

Any failure before the swap leaves the checked-in promoted root unchanged.
Environment changes, catalogue descriptor changes, and missing selected
scenes are refusal conditions, not implicit fallbacks.

## Testing strategy

Unit and contract tests must cover:

- CLI parsing for `--scene`, repeated selections, `--scenes-file`, and
  `--all`;
- deterministic ordering and serialization of all v2 root files;
- v1 compatibility and v2 schema rejection of malformed layouts;
- selected generation writing only selected scene directories;
- selected verification accepting a valid strict subset;
- incremental promotion preserving unselected scene bytes and hashes;
- environment mismatch refusal and explicit rebaseline acceptance;
- root metadata tampering, unknown scene IDs, duplicate IDs, path traversal,
  symlink rejection, and manifest-hash mismatch;
- complete promoted verification with heterogeneous per-scene source commits;
- atomic rollback when staged verification or filesystem replacement fails;
- a regression test asserting that a one-scene promotion does not modify any
  unselected scene file.

The existing pixel, route, refusal, oracle, and GPU adapter tests remain
unchanged except where their expected v1 path is made schema-aware.

## Acceptance criteria

The design is implemented when:

- a one-scene change produces only that scene's changed evidence plus the root
  `catalog.json`, `promotion.json`, and any explicitly changed environment
  metadata;
- unchanged scene directories are byte-for-byte identical before and after
  promotion;
- a full headless verification still validates every promoted scene;
- a full rebaseline remains possible and explicitly review-gated;
- v1-to-v2 migration proves identical PNG hashes and preserves all current
  support/refusal results;
- no native windowing artifact or external artifact service is introduced.
