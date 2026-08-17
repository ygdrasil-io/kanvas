# Skia Fidelity Wave 2 UNPREMUL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept valid decoded CPU `UNPREMUL` RGBA/BGRA images without double-unpremultiplying them, preserve all existing premultiplied and refusal contracts, and produce a reconciled Wave 2 alpha-cohort evidence manifest.

**Architecture:** Change only `GPUPreparedImageArtifactFactory.prepare` for the rendering fix. The existing artifact contract remains straight encoded sRGB upload followed by linear-premultiplied shader sampling. A separate thin Python Wave 2 adapter will select the exact 58 historical alpha rows, filter fresh evidence in memory, validate residual refusals, and reuse Wave 1 parsing and provenance helpers without modifying the Wave 1 scanner.

**Tech Stack:** Kotlin, Kotlin test, Gradle, WebGPU/WGSL prepared-image pipeline, Python 3, JUnit XML, Skia GM dashboard artifacts, SHA-256 evidence manifests.

---

## File Map

Production and focused tests:

- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt`: accept `UNPREMUL` and condition the RGB recovery loop on `PREMUL`.
- Modify `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`: add the failing byte-preservation test and retain premul/refusal invariants.
- Modify `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceTest.kt`: assert the public prepared-image source accepts decoded `UNPREMUL` pixels.
- Modify `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalCases.kt`: remove only the now-valid decoded CPU `UNPREMUL` refusal row.
- Modify `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalMatrixTest.kt`: remove the constructible `UNPREMUL` refusal expectation and add an explicit acceptance assertion while preserving all refusal-route assertions.
- Verify without modification `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceRefusalMatrixTest.kt`: its generated refusal loop must continue to cover every remaining matrix row.

Wave 2 reconciliation:

- Create `scripts/gm/reconcile_skia_fidelity_wave2.py`: thin cohort selector, in-memory input filter, Wave 2 manifest builder/checker, and CLI adapter around Wave 1 parsing/provenance helpers.
- Create `scripts/gm/test_reconcile_skia_fidelity_wave2.py`: fixtures and tests for cohort selection, identity mapping, residual-refusal validation, source immutability, and manifest policy.

Generated evidence, never hand-edited:

- Create `reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/` through the runner/evidence commands.
- Write the final JSON/Markdown manifest only through `reconcile_skia_fidelity_wave2.py`.
- Leave `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/` and `reports/wgsl-pipeline/m86-fidelity-burndown/` unchanged.

No changes are planned for `GPUPreparedImageShader.kt`, `GPUPreparedImageSource.kt`, the prepared-image payload, or the generic artifact schema.

### Task 1: Add The Failing UNPREMUL Contract Test

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`
- Test: the `PreparedImageContractsTest` class

- [ ] **Step 1: Record the clean baseline.**

Run:

```bash
git rev-parse HEAD
git status --short --branch
./gradlew -F off :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest --no-daemon --no-parallel --console=plain
```

Expected: the worktree is clean, `0e98d3198` is an ancestor of `HEAD`, the production tree still matches the Wave 1 implementation, and the existing focused test class passes. The approved spec and plan commits may already be present on `HEAD`.

- [ ] **Step 2: Add the red test before production changes.**

Insert this test after the existing premultiplied recovery test:

```kotlin
@Test
fun `factory accepts unpremultiplied color and preserves straight encoded upload`() {
    val artifact = ready(
        input(
            alpha = AlphaType.UNPREMUL,
            bytes = byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
        ),
    )

    assertContentEquals(
        byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
        artifact.tightRgba8BytesForUpload(),
    )
    assertEquals(
        ArtifactColorUploadEncoding.StraightEncodedSrgb,
        artifact.colorUploadEncoding,
    )
    assertEquals(
        GPUColorInterpretation.StraightEncodedSrgb.value,
        artifact.colorUploadInterpretation,
    )
}
```

- [ ] **Step 3: Run the red test.**

Run:

```bash
./gradlew -F off :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest --no-daemon --no-parallel --console=plain
```

Expected: FAIL in the new test because `GPUPreparedImageArtifactFactory.prepare` currently returns `ALPHA_INTERPRETATION` for `AlphaType.UNPREMUL`. Do not change production code until this failure is recorded.

- [ ] **Step 4: Commit the red test.**

```bash
git add gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt
git commit -m "test: cover unpremultiplied prepared image uploads"
```

### Task 2: Implement Conditional Alpha Normalization

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt:91-103,200-214`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`

- [ ] **Step 1: Narrow the general alpha refusal.**

Replace:

```kotlin
if (input.alphaType == AlphaType.UNPREMUL || input.alphaType == AlphaType.UNKNOWN) {
```

with:

```kotlin
if (input.alphaType == AlphaType.UNKNOWN) {
```

Keep the existing refusal code and facts unchanged.

- [ ] **Step 2: Condition premultiplied RGB recovery.**

Replace:

```kotlin
if (!alphaOnly) {
```

with:

```kotlin
if (!alphaOnly && input.alphaType == AlphaType.PREMUL) {
```

Do not change the loop body. This preserves the existing `PREMUL` conversion and leaves normalized `UNPREMUL` bytes straight and unchanged.

- [ ] **Step 3: Run the focused production/test boundary.**

Run:

```bash
./gradlew -F off :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest --no-daemon --no-parallel --console=plain
```

Expected: PASS, including the existing `25,75,132,160 -> 40,120,210,160` premul recovery assertion, the new `UNPREMUL` byte-preservation assertion, A8 behavior, and opaque validation.

- [ ] **Step 4: Commit the semantic fix.**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt
git commit -m "fix: preserve unpremultiplied image pixels"
```

### Task 3: Update Public Source And Refusal-Matrix Tests

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalCases.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalMatrixTest.kt`
- Verify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceRefusalMatrixTest.kt`

- [ ] **Step 1: Add an UNPREMUL BGRA and transparent-pixel invariant.**

Add this test to `PreparedImageContractsTest`:

```kotlin
@Test
fun `factory preserves unpremultiplied BGRA channel order and transparent RGB`() {
    val bgra = ready(
        input(
            format = GPUPreparedImageSourceFormat.Bgra8,
            alpha = AlphaType.UNPREMUL,
            bytes = byteArrayOf(210.toByte(), 120, 40, 160.toByte()),
        ),
    )
    val transparent = ready(
        input(
            alpha = AlphaType.UNPREMUL,
            bytes = byteArrayOf(200.toByte(), 100, 50, 0),
        ),
    )

    assertContentEquals(
        byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
        bgra.tightRgba8BytesForUpload(),
    )
    assertContentEquals(
        byteArrayOf(200.toByte(), 100, 50, 0),
        transparent.tightRgba8BytesForUpload(),
    )
}
```

- [ ] **Step 2: Replace the public source refusal test.**

Replace the body of `prepared surface source refuses unpremultiplied caller pixels` with:

```kotlin
@Test
fun `prepared surface source accepts unpremultiplied caller pixels`() {
    val result = GPUPreparedSurfaceImageSource.prepare(
        Image(
            1,
            1,
            ColorType.RGBA_8888,
            "caller",
            byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
            alphaType = AlphaType.UNPREMUL,
        ),
    )

    assertIs<GPUPreparedImageArtifactResult.Ready>(result)
}
```

Remove the now-unused `GPUPreparedImageRefusalCodes` import from that test.

- [ ] **Step 3: Remove only the valid matrix refusal.**

Delete the `ImageRefusalCase` named `UNPREMUL alpha` from `GPUPreparedImageRefusalCases.sourceRefusalCases`. Keep `UNKNOWN alpha`, A8 with `OPAQUE`, and `OPAQUE with non-255 alpha` unchanged.

- [ ] **Step 4: Update the constructible route matrix.**

Delete the `ConstructibleCase` named `unpremultiplied-alpha` from the refusal list in `GPUPreparedImageRefusalMatrixTest`. Add a separate assertion before the refusal loop:

```kotlin
val acceptedUnpremul = GPUPreparedSurfaceImageSource.prepare(
    Image(
        width = 1,
        height = 1,
        colorType = ColorType.RGBA_8888,
        sourceId = "unpremultiplied-accepted",
        pixels = byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
        alphaType = AlphaType.UNPREMUL,
    ),
)
assertIs<GPUPreparedImageArtifactResult.Ready>(acceptedUnpremul)
```

Leave all route propagation assertions for remaining refusals unchanged.

- [ ] **Step 5: Run the focused Kanvas and GPU suites.**

Run:

```bash
./gradlew -F off :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest --no-daemon --no-parallel --console=plain
./gradlew -F off :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUPreparedImageSourceTest --tests org.graphiks.kanvas.surface.gpu.GPUPreparedImageRefusalMatrixTest --tests org.graphiks.kanvas.surface.gpu.GPUPreparedImageSourceRefusalMatrixTest --no-daemon --no-parallel --console=plain
```

Expected: PASS; the refusal matrix has one fewer row, and every remaining refusal still reports its original code and boundary. The obsolete `UNPREMUL` refusal assertion in `PreparedImageContractsTest` was removed during Task 1 and must not be reintroduced.

- [ ] **Step 6: Commit the test-contract update.**

```bash
git add gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalCases.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalMatrixTest.kt
git commit -m "test: update prepared image alpha refusal matrix"
```

### Task 4: Add The Wave 2 Cohort Reconciliation Adapter

**Files:**
- Create: `scripts/gm/reconcile_skia_fidelity_wave2.py`
- Create: `scripts/gm/test_reconcile_skia_fidelity_wave2.py`
- Reuse read-only: `scripts/gm/reconcile_skia_fidelity_wave1.py`

- [ ] **Step 1: Define the adapter's immutable cohort selector.**

Load the Wave 1 JSON and select only rows whose `failureCode` equals the exact CLI value. Use the stable pair `(name, referenceKind)` as the identity. Reject the input with `ValueError` unless it contains exactly 58 selected rows and these family counts:

```python
{
    "IMAGE": 38,
    "COMPOSITE": 8,
    "CLIP": 6,
    "BLUR": 3,
    "GRADIENT": 2,
    "RUNTIME_EFFECT": 1,
}
```

The selector must also require `referenceKind == "skia-upstream"` for every selected row and return a frozen set of `(name, referenceKind)` identities.

- [ ] **Step 2: Add selector tests before adapter implementation.**

The test module must load the real Wave 1 manifest when present and use a temporary fixture for failure cases. Cover these exact assertions:

```python
selected = reconcile.load_wave2_cohort(manifest, "unsupported.image.alpha_interpretation")
assert len(selected.rows) == 58
assert selected.family_counts == {
    "IMAGE": 38,
    "COMPOSITE": 8,
    "CLIP": 6,
    "BLUR": 3,
    "GRADIENT": 2,
    "RUNTIME_EFFECT": 1,
}
assert all(row["referenceKind"] == "skia-upstream" for row in selected.rows)
```

Also assert that a fixture with a missing row, a duplicate identity, or a different failure code raises `ValueError` and never mutates the source JSON.

- [ ] **Step 3: Reuse Wave 1 parsing and path helpers.**

Load `reconcile_skia_fidelity_wave1.py` with `importlib.util`. Reuse its `parse_junit`, `parse_dashboard`, `_dashboard_entries`, `load_scores`, `_merge_junit_fields`, `_junit_identity`, `_junit_dashboard_matches`, `_lane_key`, `_identity_matches`, `_artifact_records`, `_entry_dimensions`, `_has_complete_pixel_evidence`, `_check_execution_contract`, `_check_policy`, `_check_source_commit`, `_check_pixel_score_range`, and hashing/path helpers. Deep-copy dashboard rows before calling `_merge_junit_fields`, because that helper mutates its input list. Use strict `(name, referenceKind)` identity matching for cohort filtering rather than the helper's fuzzy fallback. Do not copy the 3,535-line scanner and do not change the Wave 1 module.

- [ ] **Step 4: Implement fresh-input filtering in memory.**

After parsing fresh dashboard and runner inputs, merge JUnit fields as Wave 1 does, then retain only rows whose stable identity is in the selected cohort. Filter the evidence entries by the same identity. Never rewrite `skia-dashboard-gms.json`, `test-similarity-scores.properties`, JUnit XML, or generated images.

The adapter must preserve the complete original paths and hashes in provenance while reporting the filtered cohort counters in `rows.skia`, `rows.skiaJunit`, and `current`.

- [ ] **Step 5: Implement separate supported-after and residual-refusal validation.**

For `supportedAfter=true`, require all seven artifact roles and the before/after similarity fields:

```text
reference, render, cpu, gpu, diff, stat, route,
similarityBefore, similarityAfter, threshold or minSimilarity,
pixelImproved=true, causal evidence, route diagnostics
```

For `status == "residual-refusal"`, require `failureCode`, `fallbackReason`, `expectedRoute`, `rootCause`, `followUpFamily`, and whatever reference/CPU/route/stat artifacts exist. Do not require or fabricate a render artifact for a terminal refusal. Apply this residual-refusal branch before Wave 1's generic `_check_evidence_index`, whose `skia-upstream` rule requires render/reference artifacts. Reject missing hashes, duplicate artifact paths, orphan entries, input aliasing, and unknown cohort identities.

- [ ] **Step 6: Build the Wave 2 manifest and Markdown output.**

The output must set:

```python
manifest["kind"] = "skia-fidelity-wave-2-unpremul"
manifest["generatedBy"] = "reconcile_skia_fidelity_wave2.py"
manifest["cohort"] = {
    "failureCode": "unsupported.image.alpha_interpretation",
    "rows": 58,
    "familyCounts": {
        "IMAGE": 38,
        "COMPOSITE": 8,
        "CLIP": 6,
        "BLUR": 3,
        "GRADIENT": 2,
        "RUNTIME_EFFECT": 1,
    },
}
manifest["policy"]["assertionsWeakened"] = False
manifest["policy"]["globalThresholdWeakened"] = False
manifest["policy"]["memoryBudgetChanged"] = False
manifest["policy"]["readinessDelta"] = 0.0
manifest["policy"]["referencesModified"] = False
manifest["policy"]["scoresDirectlyEdited"] = False
```

The Markdown title must be `# Skia Fidelity Wave 2 UNPREMUL Reconciliation`. It must include before/after counts, residual failure codes, provenance hashes, non-claims, and the generated artifact paths.

- [ ] **Step 7: Add adapter CLI and tests.**

The adapter must accept every Wave 1 input flag plus:

```text
--cohort-manifest PATH
--cohort-failure-code CODE
```

It must accept `--status classification|approved|blocked`, require `--check` for `approved`, require `--source-commit`, and write only the two requested output paths. Test the CLI with temporary XML/JSON fixtures for:

1. exact 58-row selection;
2. current `HEAD` source-commit validation;
3. no mutation of dashboard/score/evidence input bytes;
4. residual refusal entries without fabricated render artifacts;
5. zero unclassified failures and both non-weakening flags false;
6. rejection of incomplete supported-after evidence.

- [ ] **Step 8: Run Python tests and commit the adapter.**

Run:

```bash
python3 -m unittest scripts/gm/test_reconcile_skia_fidelity_wave2.py
python3 -m unittest scripts/gm/test_reconcile_skia_fidelity_wave1.py
```

Expected: both test modules pass and the Wave 1 scanner behavior remains unchanged.

```bash
git add scripts/gm/reconcile_skia_fidelity_wave2.py scripts/gm/test_reconcile_skia_fidelity_wave2.py
git commit -m "feat: add Wave 2 alpha cohort reconciliation"
```

### Task 5: Capture Fresh Before Evidence

**Files:**
- Create through commands: `reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/`
- Do not modify: `reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/`

- [ ] **Step 1: Create the evidence directory and provenance skeleton.**

Create the directory before writing artifacts:

```bash
EVIDENCE=reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs
mkdir -p "$EVIDENCE/provenance" "$EVIDENCE/runner-chunks"
```

Then record `git rev-parse HEAD`, OS/JDK, GPU adapter/driver, `env -u DISPLAY`, repeat count, Gradle version, and every command in `provenance/environment.json` and `provenance/commands.json`. The source commit must be the actual current branch commit, never the historical `dd045a...` value in Wave 1.

- [ ] **Step 2: Snapshot the score file without editing it.**

Copy the current generated similarity properties file to the evidence directory:

```bash
cp integration-tests/skia/test-similarity-scores.properties reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/scores-before.properties
```

Do not alter the generated source file. The after snapshot will be captured only after the post-fix runner.

- [ ] **Step 3: Run the baseline module tests.**

Run:

```bash
EVIDENCE=reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs
set -o pipefail
env -u DISPLAY ./gradlew -F off :kanvas:test --no-daemon --no-parallel --console=plain 2>&1 | tee "$EVIDENCE/cpu-test.log"
env -u DISPLAY ./gradlew -F off :gpu-renderer:test --no-daemon --no-parallel --console=plain 2>&1 | tee "$EVIDENCE/gpu-test.log"
env -u DISPLAY ./gradlew -F off :integration-tests:svg:test --no-daemon --no-parallel --console=plain 2>&1 | tee "$EVIDENCE/svg-test.log"
```

Store the resulting CPU/GPU/SVG test outputs under the Wave 2 evidence directory with hashes.

- [ ] **Step 4: Run the baseline Skia runner in the three Wave 1 chunks.**

Run each command with `-Dkanvas.gm.includeBlocking=true`, `-F off`, no daemon, no parallelism, and plain console:

```bash
EVIDENCE=reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs
set -o pipefail
env -u DISPLAY ./gradlew -F off :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmRunner -Dkanvas.gm.includeBlocking=true -Dkanvas.gm.from=0 -Dkanvas.gm.to=350 -Dkanvas.render.debugLevel=TRACE --no-daemon --no-parallel --console=plain 2>&1 | tee "$EVIDENCE/runner-0-350.log"
cp integration-tests/skia/build/test-results/test/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml "$EVIDENCE/runner-chunks/TEST-SkiaGmRunner-0-350.xml"
env -u DISPLAY ./gradlew -F off :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmRunner -Dkanvas.gm.includeBlocking=true -Dkanvas.gm.from=351 -Dkanvas.gm.to=451 -Dkanvas.render.debugLevel=TRACE --no-daemon --no-parallel --console=plain 2>&1 | tee "$EVIDENCE/runner-351-451.log"
cp integration-tests/skia/build/test-results/test/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml "$EVIDENCE/runner-chunks/TEST-SkiaGmRunner-351-451.xml"
env -u DISPLAY ./gradlew -F off :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmRunner -Dkanvas.gm.includeBlocking=true -Dkanvas.gm.from=452 -Dkanvas.gm.to=610 -Dkanvas.render.debugLevel=TRACE --no-daemon --no-parallel --console=plain 2>&1 | tee "$EVIDENCE/runner-452-610.log"
cp integration-tests/skia/build/test-results/test/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml "$EVIDENCE/runner-chunks/TEST-SkiaGmRunner-452-610.xml"
PYTHONDONTWRITEBYTECODE=1 python3 scripts/gm/merge_skia_junit.py --output "$EVIDENCE/skia-gm-runner.xml" "$EVIDENCE/runner-chunks/TEST-SkiaGmRunner-0-350.xml" "$EVIDENCE/runner-chunks/TEST-SkiaGmRunner-351-451.xml" "$EVIDENCE/runner-chunks/TEST-SkiaGmRunner-452-610.xml"
```

Merge the JUnit chunks with `python3 scripts/gm/merge_skia_junit.py`. Preserve all 58 selected alpha rows and their current terminal diagnostics as before evidence.

- [ ] **Step 5: Generate baseline dashboard, renders, scan, and evidence index.**

Run the dashboard, render, and scan tasks with the same three ranges. `generateSkiaRenders` consumes `-Pgm.includeBlocking`, `-Pgm.from`, `-Pgm.to`, and `-Pgm.outputDir`; `generateSkiaDashboard` consumes `-Pgm.includeBlocking`, `-Pgm.outputDir`, and `-Pgm.dashboardOutputDir`. Use the exact selected GM-name list for `-Pkanvas.scan.names`. Capture render-route trace through the runner test's `-Dkanvas.render.debugLevel=TRACE`; the JavaExec render task does not forward that system property.

```bash
EVIDENCE=reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs
env -u DISPLAY ./gradlew -F off :integration-tests:skia:generateSkiaRenders -Pgm.includeBlocking=true -Pgm.from=0 -Pgm.to=350 -Pgm.outputDir="$EVIDENCE/generated-renders" --no-daemon --no-parallel --console=plain
env -u DISPLAY ./gradlew -F off :integration-tests:skia:generateSkiaRenders -Pgm.includeBlocking=true -Pgm.from=351 -Pgm.to=451 -Pgm.outputDir="$EVIDENCE/generated-renders" --no-daemon --no-parallel --console=plain
env -u DISPLAY ./gradlew -F off :integration-tests:skia:generateSkiaRenders -Pgm.includeBlocking=true -Pgm.from=452 -Pgm.to=610 -Pgm.outputDir="$EVIDENCE/generated-renders" --no-daemon --no-parallel --console=plain
env -u DISPLAY ./gradlew -F off :integration-tests:skia:generateSkiaDashboard -Pgm.includeBlocking=true -Pgm.outputDir="$EVIDENCE/generated-renders" -Pgm.dashboardOutputDir="$EVIDENCE/dashboard" -Pgm.scores=integration-tests/skia/test-similarity-scores.properties -x :integration-tests:skia:generateSkiaRenders --no-daemon --no-parallel --console=plain
COHORT_NAMES="$(python3 -c 'import json; print(",".join(row["name"] for row in json.load(open("reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/wave1-classification.json", encoding="utf-8"))["rows"]["skia"] if row.get("failureCode") == "unsupported.image.alpha_interpretation"))')"
env -u DISPLAY ./gradlew -F off :integration-tests:skia:generateSkiaScan -Pkanvas.scan.names="$COHORT_NAMES" -Pkanvas.scan.timeout=30 -Pkanvas.scan.output="$EVIDENCE/skia-scan-results.txt" --no-daemon --no-parallel --console=plain
python3 scripts/gm/scan_results_to_junit.py --scan "$EVIDENCE/skia-scan-results.txt" --output "$EVIDENCE/fp13-runner.xml"
```

The selector command supplies all exact cohort names; never shorten the list manually. Store fresh reference/generated/diff/stat/route paths in `provenance/evidence-index.json` and hash every file.

- [ ] **Step 6: Commit the sealed before evidence.**

Verify the evidence index has one entry per selected `(name, referenceKind)` identity, then commit only generated Wave 2 before-evidence files:

```bash
git add reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs
git commit -m "evidence: capture Wave 2 alpha baseline"
```

### Task 6: Rerun After The Fix And Reconcile

**Files:**
- Modify through generated workflow: `reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/provenance/evidence-index.json`
- Create through scanner: `reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/wave2-unpremul.json`
- Create through scanner: `reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/wave2-unpremul.md`

- [ ] **Step 1: Run the complete post-fix validation set.**

Run the focused tests, the full `:kanvas:test`, `:gpu-renderer:test`, `:integration-tests:svg:test`, and all three Skia runner chunks with both blocking flags where applicable. Do not alter a threshold or assertion to make a row pass.

- [ ] **Step 2: Capture after scores and pixels.**

Snapshot the generated score file:

```bash
cp integration-tests/skia/test-similarity-scores.properties reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/scores-after.properties
```

Regenerate the dashboard and renders through Gradle. For each row that now has pixels, collect reference/render/CPU/GPU/diff/stat/route artifacts. For each row that remains refused, record its exact refusal route and stable follow-up cause without creating a fake render.

- [ ] **Step 3: Run the Wave 2 adapter in check mode.**

Use:

```bash
python3 scripts/gm/reconcile_skia_fidelity_wave2.py \
  --cohort-manifest reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave-1-inputs/wave1-classification.json \
  --cohort-failure-code unsupported.image.alpha_interpretation \
  --skia-runner reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/skia-gm-runner.xml \
  --dashboard-json reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/skia-dashboard-gms.json \
  --dashboard-dir reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/dashboard \
  --generated-renders reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/generated-renders \
  --svg-xml reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/svg-integration.xml \
  --cpu-results reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/cpu-results.json \
  --gpu-results reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/gpu-results.json \
  --scores-before reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/scores-before.properties \
  --scores-after reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/scores-after.properties \
  --fp13-runner reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/fp13-runner.xml \
  --commands-json reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/provenance/commands.json \
  --environment-json reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/provenance/environment.json \
  --evidence-index reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/provenance/evidence-index.json \
  --source-commit "$(git rev-parse HEAD)" \
  --status classification \
  --check \
  --output-json reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/wave2-unpremul.json \
  --output-markdown reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs/wave2-unpremul.md
```

- [ ] **Step 4: Verify reconciliation invariants.**

Expected: exit code 0, `unclassifiedFailures=0`, `assertionsWeakened=false`, `globalThresholdWeakened=false`, no direct score edits, no reference mutation, no new unsupported-to-pass labels, and explicit residual refusal rows where later blockers remain. `supportedRowsAfter` may be lower than 58; only rows with complete pixel evidence and actual improvement count as supported.

- [ ] **Step 5: Commit the reconciled evidence.**

```bash
git add reports/upstream-rebaseline/2026-08-17-skia-fidelity-wave-2-unpremul-inputs
git commit -m "evidence: reconcile Wave 2 UNPREMUL cohort"
```

### Task 7: Independent Review And Final Verification

**Files:**
- Review all branch changes; no new production files are expected.

- [ ] **Step 1: Run the final test matrix.**

```bash
python3 -m unittest scripts/gm/test_reconcile_skia_fidelity_wave2.py
python3 -m unittest scripts/gm/test_reconcile_skia_fidelity_wave1.py
env -u DISPLAY ./gradlew -F off :kanvas:test --no-daemon --no-parallel --console=plain
env -u DISPLAY ./gradlew -F off :gpu-renderer:test --no-daemon --no-parallel --console=plain
env -u DISPLAY ./gradlew -F off :integration-tests:svg:test --no-daemon --no-parallel --console=plain
```

Run the Skia chunks again if any generated evidence changed after the previous reconciliation.

- [ ] **Step 2: Request independent review.**

Review specifically for: conditional alpha semantics, unchanged PREMUL output, explicit `UNKNOWN`/A8/OPAQUE refusals, no shader drift, no CPU fallback, no per-GM workaround, no threshold/assertion weakening, complete evidence roles, and adapter input immutability.

- [ ] **Step 3: Inspect the final branch state.**

```bash
git status --short --branch
git diff 0e98d3198...HEAD --check
git diff 0e98d3198...HEAD --stat
git log --oneline 0e98d3198..HEAD
```

Expected: only the approved spec/plan, focused production/tests, Wave 2 adapter/tests, and generated Wave 2 evidence are present; Wave 1 and M86 files are unchanged.

### Task 8: Push And Open The PR

**Files:**
- No source changes.

- [ ] **Step 1: Push the requested branch.**

```bash
git push -u origin codex/skia-fidelity-wave2
```

- [ ] **Step 2: Open the PR with the Wave 2 evidence summary.**

Use the `gh-pr-creator` skill and `gh` to create a PR from `codex/skia-fidelity-wave2`. The PR body must link:

- the design spec;
- the implementation plan;
- the Wave 2 JSON and Markdown manifests;
- the before/after artifact directory;
- the exact validation commands and results;
- the explicit non-claims and residual refusals.

- [ ] **Step 3: Return the PR URL and verification summary.**

Report the selected cohort count, supported-after count, residual failure codes, test commands, reconciliation invariants, branch name, and PR URL. Do not claim the full 58-row cohort passed unless the manifest proves it with complete pixel evidence.

## Self-Review Checklist

- Spec coverage: cohort selection, Graphite/Dawn comparison, one-layer fix, tests-first, evidence roles, residual refusals, escalation, non-weakening policy, independent review, and PR delivery each have a task above.
- Placeholder scan: no task relies on an unspecified implementation, unbounded workaround, threshold change, or unnamed artifact.
- Type consistency: `AlphaType.UNPREMUL`, `GPUPreparedImageArtifactFactory.prepare`, `StraightEncodedSrgb`, `GPUPreparedImageRefusalMatrix`, and the Wave 2 CLI names match the approved spec and existing source.
- Scope: no shader, WGSL parser, material, coverage, image codec, or Skia GM source modification is included in this wave.
