# GPU Phase 6 Effect + Composition Families Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 6 evidence generation for `COMPOSITE + BLUR` with row-level classification, stable refusals, JSON/CSV/Markdown artifacts, and no broad renderer support claims.

**Architecture:** Add a focused Effect + Composition evidence classifier/writer in `:integration-tests:skia-evidence`, following the existing Phase 6 `IMAGE`, `PATH + CLIP`, and Material waves. The generator reads the Skia GM dashboard, filters only `COMPOSITE` and `BLUR`, classifies rows into subfamilies, emits stable fallback reasons, and writes evidence artifacts under `reports/gpu-renderer/phase-6-effect-composition-families/`.

**Tech Stack:** Kotlin/JVM, Gradle `JavaExec`, kotlinx.serialization JSON, `kotlin.test`, Skia GM dashboard JSON.

## Global Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild SkSL, its IR, or its VM.
- Keep WebGPU as the GPU backend.
- Keep WGSL as the shader target.
- Do not lower global similarity thresholds.
- Do not hide `no-score`.
- Do not promote a row without reference, generated render, diff/stat, route/effect/composition diagnostics, explicit fallback policy, and non-claim.
- Do not claim broad `saveLayer`, destination-read, backdrop filter, image-filter DAG, matrix convolution, advanced blend chain, or arbitrary blur support.
- Do not count a visually passing render as support if the real route is an approximation, omission, or out-of-scope dependency.
- Keep heavy renderer fixes in separate PRs unless strictly required for evidence.

---

## File Structure

- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidence.kt`
  - Owns data classes, classifier, writer, JSON/CSV/Markdown serialization, row id stability, subfamily policy, fallback reasons, and non-claims for `COMPOSITE + BLUR`.
- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamiliesEvidenceCli.kt`
  - Reads `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`, builds evidence, writes artifacts.
- Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt`
  - Unit tests for filtering, subfamily classification, `no-score`, duplicate ids, writer output, preserved validation section, JSON/CSV/Markdown shape.
- Modify `integration-tests/skia-evidence/build.gradle.kts`
  - Add `generateGpuPhase6EffectCompositionFamiliesEvidence`.
- Generate:
  - `reports/gpu-renderer/phase-6-effect-composition-families/evidence.json`
  - `reports/gpu-renderer/phase-6-effect-composition-families/classification.csv`
  - `reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md`

---

### Task 1: Effect + Composition Classifier Tests

**Files:**
- Create: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes: `GmDashboard`, `GmDashboardRow`, `GmRgbaInt`, `GmRgbaDouble` from `GmDashboardEvidence.kt`.
- Produces expected API for Task 2:
  - `object Phase6EffectCompositionFamilyClassifier`
  - `fun classify(row: GmDashboardRow, rowId: String = row.name): Phase6EffectCompositionRowEvidence`
  - `fun buildEvidence(dashboard: GmDashboard): Phase6EffectCompositionFamiliesEvidence`
  - `data class Phase6EffectCompositionRowEvidence`
  - `object Phase6EffectCompositionFamilyEvidenceWriter`
  - `fun Phase6EffectCompositionFamiliesEvidence.toJsonObject(): JsonObject`

- [ ] **Step 1: Create the failing test file**

Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt` with:

```kotlin
package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.nio.file.Files

class Phase6EffectCompositionFamilyEvidenceTest {
    @Test
    fun `build evidence filters composite and blur only`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("srcmode", family = "COMPOSITE"),
                    row("BlurSmallSigma", family = "BLUR"),
                    row("linear_gradient", family = "GRADIENT"),
                    row("cubicpath", family = "PATH"),
                ),
            ),
        )

        assertEquals(2, evidence.summary.totalRows)
        assertEquals(mapOf("BLUR" to 1, "COMPOSITE" to 1), evidence.summary.families)
        assertEquals(listOf("srcmode", "BlurSmallSigma"), evidence.rows.map { it.name })
        assertEquals("phase6-effect-composition-families-v1", evidence.schemaVersion)
    }

    @Test
    fun `classifies composite subfamilies with stable reasons`() {
        val srcOver = classify("srcmode", "COMPOSITE")
        val pd = classify("xfermodes", "COMPOSITE")
        val advanced = classify("advanced_blend_modes", "COMPOSITE")
        val saveLayer = classify("savelayer_f16", "COMPOSITE")
        val backdrop = classify("backdrop_imagefilter_croprect", "COMPOSITE")
        val dstRead = classify("dstreadshuffle", "COMPOSITE")
        val imageFilter = classify("imagefilters_xfermodes", "COMPOSITE")
        val atlas = classify("draw-atlas-colors", "COMPOSITE")

        assertEquals("composite-src-over-basic", srcOver.subfamily)
        assertEquals("instrumented-existing", srcOver.classification)
        assertEquals("none", srcOver.fallbackReason)
        assertEquals("composite-porter-duff", pd.subfamily)
        assertEquals("instrumented-existing", pd.classification)
        assertEquals("composite-advanced-blend-gated", advanced.subfamily)
        assertEquals("unsupported.composition.advanced_blend", advanced.fallbackReason)
        assertEquals("expected-unsupported", advanced.classification)
        assertEquals("composite-save-layer-gated", saveLayer.subfamily)
        assertEquals("unsupported.composition.save_layer", saveLayer.fallbackReason)
        assertEquals("composite-backdrop-gated", backdrop.subfamily)
        assertEquals("unsupported.composition.backdrop_filter", backdrop.fallbackReason)
        assertEquals("composite-destination-read-gated", dstRead.subfamily)
        assertEquals("unsupported.composition.destination_read", dstRead.fallbackReason)
        assertEquals("composite-image-filter-gated", imageFilter.subfamily)
        assertEquals("unsupported.composition.image_filter_dag", imageFilter.fallbackReason)
        assertEquals("composite-atlas-or-vertices-gated", atlas.subfamily)
        assertEquals("unsupported.composition.atlas_or_vertices", atlas.fallbackReason)
    }

    @Test
    fun `classifies blur subfamilies with stable reasons`() {
        val smallSigma = classify("BlurSmallSigma", "BLUR")
        val rects = classify("blur2rects", "BLUR")
        val image = classify("blur_image", "BLUR")
        val big = classify("BlurBigSigma", "BLUR")
        val transform = classify("blur_matrix_rect", "BLUR")
        val clip = classify("blurredclippedcircle", "BLUR")
        val graph = classify("fast_slow_blurimagefilter", "BLUR")
        val convolution = classify("matrixconvolution", "BLUR")
        val text = classify("imagefilterstext_if", "BLUR")

        assertEquals("blur-small-sigma", smallSigma.subfamily)
        assertEquals("instrumented-existing", smallSigma.classification)
        assertEquals("blur-rect-rrect-circle", rects.subfamily)
        assertEquals("instrumented-existing", rects.classification)
        assertEquals("blur-image-basic", image.subfamily)
        assertEquals("instrumented-existing", image.classification)
        assertEquals("blur-large-sigma-gated", big.subfamily)
        assertEquals("unsupported.blur.large_sigma", big.fallbackReason)
        assertEquals("expected-unsupported", big.classification)
        assertEquals("blur-transform-or-perspective-gated", transform.subfamily)
        assertEquals("unsupported.blur.transform_or_perspective", transform.fallbackReason)
        assertEquals("blur-clip-interaction-gated", clip.subfamily)
        assertEquals("unsupported.blur.clip_interaction", clip.fallbackReason)
        assertEquals("blur-filter-graph-gated", graph.subfamily)
        assertEquals("unsupported.blur.image_filter_graph", graph.fallbackReason)
        assertEquals("blur-matrix-convolution-gated", convolution.subfamily)
        assertEquals("unsupported.blur.matrix_convolution", convolution.fallbackReason)
        assertEquals("blur-text-dependent-gated", text.subfamily)
        assertEquals("unsupported.blur.text_dependency", text.fallbackReason)
    }

    @Test
    fun `separates no score from unexpected fail`() {
        val noScore = classify("animatedbackdropblur", "BLUR", similarity = null, isPassing = null, noReference = true)
        val fail = classify("plain_composite_fail", "COMPOSITE", similarity = 20.0, isPassing = false)

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `passing gated effect rows remain expected unsupported`() {
        val advanced = classify("advanced_blend_modes", "COMPOSITE", similarity = 100.0, isPassing = true)
        val bigBlur = classify("BlurBigSigma", "BLUR", similarity = 100.0, isPassing = true)

        assertEquals("expected-unsupported", advanced.classification)
        assertEquals("unsupported.composition.advanced_blend", advanced.fallbackReason)
        assertEquals("expected-unsupported", bigBlur.classification)
        assertEquals("unsupported.blur.large_sigma", bigBlur.fallbackReason)
    }

    @Test
    fun `build evidence computes family deltas from baseline`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("srcmode", family = "COMPOSITE"),
                    row("xfermodes", family = "COMPOSITE"),
                    row("BlurSmallSigma", family = "BLUR"),
                ),
            ),
        )

        assertEquals(113, evidence.summary.familyDeltas.getValue("COMPOSITE").baselineCount)
        assertEquals(2, evidence.summary.familyDeltas.getValue("COMPOSITE").currentCount)
        assertEquals(-111, evidence.summary.familyDeltas.getValue("COMPOSITE").delta)
        assertEquals(45, evidence.summary.familyDeltas.getValue("BLUR").baselineCount)
        assertEquals(1, evidence.summary.familyDeltas.getValue("BLUR").currentCount)
        assertEquals(-44, evidence.summary.familyDeltas.getValue("BLUR").delta)
    }

    @Test
    fun `duplicate rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("modecolorfilters", family = "COMPOSITE"),
                    row("modecolorfilters", family = "COMPOSITE"),
                ),
            ),
        )

        assertEquals(listOf("modecolorfilters", "modecolorfilters#2"), evidence.rows.map { it.rowId })
        assertContains(evidence.toCsv(), "modecolorfilters#2,modecolorfilters,COMPOSITE")
        assertContains(evidence.toMarkdown(), "`modecolorfilters#2`")
    }

    @Test
    fun `evidence json includes diff stats and no score cause`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("animatedbackdropblur", family = "BLUR", similarity = null, isPassing = null, noReference = true),
                    row("srcmode", family = "COMPOSITE", maxDiff = GmRgbaInt(1, 2, 3, 4), meanDiff = GmRgbaDouble(1.5, 2.5, 3.5, 4.5)),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()
        assertContains(json, "\"schemaVersion\":\"phase6-effect-composition-families-v1\"")
        assertContains(json, "\"noScoreCause\":\"reference-missing\"")
        assertContains(json, "\"maxDiff\":{\"r\":1,\"g\":2,\"b\":3,\"a\":4}")
        assertContains(json, "\"meanDiff\":{\"r\":1.5,\"g\":2.5,\"b\":3.5,\"a\":4.5}")
    }

    @Test
    fun `writer creates json markdown and csv outputs and preserves validation section`() {
        val root = Files.createTempDirectory("phase6-effect-composition-evidence-test")
        val markdown = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")
        Files.createDirectories(markdown.parent)
        Files.writeString(markdown, "# Existing\n\n## Validation\n\n- keep this validation note\n")
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(row("srcmode", family = "COMPOSITE")),
            ),
        )

        Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)

        assertContains(Files.readString(root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")), "phase6-effect-composition-families-v1")
        assertContains(Files.readString(root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")), "rowId,name,family,subfamily,classification")
        assertContains(Files.readString(markdown), "## Validation")
        assertContains(Files.readString(markdown), "keep this validation note")
    }

    @Test
    fun `non claims do not mention excluded support as complete`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(row("srcmode", family = "COMPOSITE")),
            ),
        )

        val nonClaims = evidence.nonClaims.joinToString("\n")
        assertContains(nonClaims, "No broad COMPOSITE or BLUR support is claimed from classification alone.")
        assertContains(nonClaims, "saveLayer, destination-read, backdrop filters, image-filter DAGs, matrix convolution, and advanced blend chains remain outside this evidence wave unless row diagnostics prove a bounded route.")
        assertFalse(nonClaims.contains("complete support", ignoreCase = true))
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 0.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
    ): Phase6EffectCompositionRowEvidence =
        Phase6EffectCompositionFamilyClassifier.classify(
            row(
                name = name,
                family = family,
                similarity = similarity,
                minSimilarity = minSimilarity,
                isPassing = isPassing,
                noReference = noReference,
                renderFailed = renderFailed,
                sizeMismatch = sizeMismatch,
                maxDiff = maxDiff,
                meanDiff = meanDiff,
            ),
        )

    private fun row(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 0.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
    ): GmDashboardRow =
        GmDashboardRow(
            name = name,
            family = family,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = 256,
            height = 256,
            matchingPixels = if (similarity == null) null else 65536,
            totalPixels = if (similarity == null) null else 65536,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            noReference = noReference,
            renderFailed = renderFailed,
            sizeMismatch = sizeMismatch,
            hasDiff = similarity != null && !renderFailed && !noReference && !sizeMismatch,
        )
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6EffectCompositionFamilyEvidenceTest" --rerun-tasks
```

Expected: FAIL during test compilation with unresolved references to `Phase6EffectCompositionFamilyClassifier`, `Phase6EffectCompositionRowEvidence`, `Phase6EffectCompositionFamilyEvidenceWriter`, `toCsv`, `toMarkdown`, and `toJsonObject`.

- [ ] **Step 3: Commit failing tests only**

```bash
rtk proxy git add integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt
rtk proxy git commit -m "Add Phase 6 effect composition evidence tests"
```

---

### Task 2: Effect + Composition Classifier and Writer

**Files:**
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidence.kt`
- Test: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes test expectations from Task 1.
- Produces:
  - `Phase6EffectCompositionFamiliesEvidence`
  - `Phase6EffectCompositionSummary`
  - `Phase6EffectCompositionFamilyDelta`
  - `Phase6EffectCompositionRowEvidence`
  - `Phase6EffectCompositionFamilyClassifier`
  - `Phase6EffectCompositionFamilyEvidenceWriter`
  - `Phase6EffectCompositionFamiliesEvidence.toJsonObject()`
  - `Phase6EffectCompositionFamiliesEvidence.toCsv()`
  - `Phase6EffectCompositionFamiliesEvidence.toMarkdown()`

- [ ] **Step 1: Add the implementation file**

Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidence.kt` by adapting the existing Material evidence file with these exact policy constants and functions:

```kotlin
private const val baselineSource = "2026-07-09 local dashboard before effect-composition-family wave"
private val effectCompositionFamilies = setOf("COMPOSITE", "BLUR")
private val baselineFamilyCounts = linkedMapOf("BLUR" to 45, "COMPOSITE" to 113)
```

The generated task name and paths must be:

```kotlin
generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence"
dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"
schemaVersion = "phase6-effect-composition-families-v1"
```

Writer paths must be:

```kotlin
val evidencePath = root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")
val csvPath = root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")
val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")
```

Use these classifier functions:

```kotlin
private fun effectCompositionSubfamily(row: GmDashboardRow): String {
    val name = row.name.effectCompositionKey()
    return when (row.family) {
        "COMPOSITE" -> compositeSubfamily(name)
        "BLUR" -> blurSubfamily(name)
        else -> error("Unexpected effect/composition family ${row.family}")
    }
}

private fun compositeSubfamily(name: String): String =
    when {
        name.containsAnyEffectComposition("advancedblend", "advanced", "hsl", "luminosity", "overlay", "hardlight", "softlight", "colordodge", "colorburn") -> "composite-advanced-blend-gated"
        name.containsAnyEffectComposition("savelayer", "layer") -> "composite-save-layer-gated"
        name.containsAnyEffectComposition("backdrop") -> "composite-backdrop-gated"
        name.containsAnyEffectComposition("dstread", "readshuffle", "destinationread") -> "composite-destination-read-gated"
        name.containsAnyEffectComposition("imagefilter", "imagefilters", "filtergraph", "offsetimagefilter", "matriximagefilter", "localmatriximagefilter") -> "composite-image-filter-gated"
        name.containsAnyEffectComposition("atlas", "vertices", "patch") -> "composite-atlas-or-vertices-gated"
        name.containsAnyEffectComposition("colorfilter", "color4blendcf", "modecolorfilters", "tablecolorfilter") -> "composite-color-filter-gated"
        name.containsAnyEffectComposition("xfer", "mode", "porterduff", "srcmode", "aaxfer", "hairmodes", "lcdblend") -> "composite-porter-duff"
        name.containsAnyEffectComposition("overdraw") -> "composite-overdraw-diagnostic"
        name.containsAnyEffectComposition("bounds", "croprect") -> "composite-layer-bounds-gated"
        else -> "composite-src-over-basic"
    }

private fun blurSubfamily(name: String): String =
    when {
        name.containsAnyEffectComposition("matrixconvolution") -> "blur-matrix-convolution-gated"
        name.containsAnyEffectComposition("text") -> "blur-text-dependent-gated"
        name.containsAnyEffectComposition("backdrop") -> "blur-backdrop-gated"
        name.containsAnyEffectComposition("fastslow", "imagefilter", "imagefilters", "filtergraph", "inversefillfilters", "inversewindingmodefilters") -> "blur-filter-graph-gated"
        name.containsAnyEffectComposition("bigsigma", "large", "biggest", "bigger") -> "blur-large-sigma-gated"
        name.containsAnyEffectComposition("matrix", "xform", "persp", "perspective") -> "blur-transform-or-perspective-gated"
        name.containsAnyEffectComposition("clip", "clipped") -> "blur-clip-interaction-gated"
        name.containsAnyEffectComposition("image", "drawimage") -> "blur-image-basic"
        name.containsAnyEffectComposition("small", "sigma") -> "blur-small-sigma"
        name.containsAnyEffectComposition("rect", "rrect", "circle") -> "blur-rect-rrect-circle"
        name.containsAnyEffectComposition("resource", "texture", "hdr", "pip") -> "blur-resource-budget-gated"
        else -> "blur-mask-basic"
    }
```

Use these fallback reasons:

```kotlin
private fun gatedReason(subfamily: String): String? =
    when (subfamily) {
        "composite-advanced-blend-gated" -> "unsupported.composition.advanced_blend"
        "composite-xfermode-gated" -> "unsupported.composition.xfermode"
        "composite-save-layer-gated" -> "unsupported.composition.save_layer"
        "composite-backdrop-gated" -> "unsupported.composition.backdrop_filter"
        "composite-destination-read-gated" -> "unsupported.composition.destination_read"
        "composite-image-filter-gated" -> "unsupported.composition.image_filter_dag"
        "composite-atlas-or-vertices-gated" -> "unsupported.composition.atlas_or_vertices"
        "composite-layer-bounds-gated" -> "unsupported.composition.layer_bounds"
        "blur-large-sigma-gated" -> "unsupported.blur.large_sigma"
        "blur-transform-or-perspective-gated" -> "unsupported.blur.transform_or_perspective"
        "blur-clip-interaction-gated" -> "unsupported.blur.clip_interaction"
        "blur-filter-graph-gated" -> "unsupported.blur.image_filter_graph"
        "blur-image-filter-gated" -> "unsupported.blur.image_filter_graph"
        "blur-matrix-convolution-gated" -> "unsupported.blur.matrix_convolution"
        "blur-backdrop-gated" -> "unsupported.blur.backdrop"
        "blur-text-dependent-gated" -> "unsupported.blur.text_dependency"
        "blur-resource-budget-gated" -> "unsupported.blur.resource_budget"
        else -> null
    }
```

Use these non-claims in `buildEvidence`:

```kotlin
nonClaims = listOf(
    "No broad COMPOSITE or BLUR support is claimed from classification alone.",
    "saveLayer, destination-read, backdrop filters, image-filter DAGs, matrix convolution, and advanced blend chains remain outside this evidence wave unless row diagnostics prove a bounded route.",
    "Rows without route and effect/composition diagnostics remain instrumented rather than promoted.",
    "TEXT, IMAGE, PATH, CLIP, MATERIAL, and MESH dependencies are not absorbed into this wave.",
)
```

Implement JSON, CSV, and Markdown serialization with the same fields as Material evidence, replacing `Material` names with `EffectComposition`. Keep stable duplicate row ids with:

```kotlin
private fun List<GmDashboardRow>.withStableEffectCompositionRowIds(): List<Pair<String, GmDashboardRow>> {
    val seenCounts = linkedMapOf<String, Int>()
    return map { row ->
        val count = seenCounts.getOrDefault(row.name, 0) + 1
        seenCounts[row.name] = count
        val rowId = if (count == 1) row.name else "${row.name}#$count"
        rowId to row
    }
}
```

- [ ] **Step 2: Run the Task 1 tests**

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6EffectCompositionFamilyEvidenceTest" --rerun-tasks
```

Expected: PASS. If a test fails because the classifier order assigns a row to the wrong gated category, fix the classifier order rather than weakening the assertion.

- [ ] **Step 3: Run all skia-evidence tests**

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
```

Expected: PASS for existing image, coverage, material, and new effect/composition tests.

- [ ] **Step 4: Commit classifier and writer**

```bash
rtk proxy git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidence.kt
rtk proxy git add integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt
rtk proxy git commit -m "Add Phase 6 effect composition classifier"
```

---

### Task 3: CLI and Gradle Task

**Files:**
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamiliesEvidenceCli.kt`
- Modify: `integration-tests/skia-evidence/build.gradle.kts`
- Test: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes: `Phase6EffectCompositionFamilyClassifier.buildEvidence(dashboard)` and `Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)` from Task 2.
- Produces: Gradle task `:integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence`.

- [ ] **Step 1: Add CLI file**

Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamiliesEvidenceCli.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val effectCompositionDashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = root.resolve(effectCompositionDashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(dashboard)
    Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")}")
}
```

- [ ] **Step 2: Add Gradle task**

In `integration-tests/skia-evidence/build.gradle.kts`, append after `generateGpuPhase6MaterialFamiliesEvidence`:

```kotlin
tasks.register<JavaExec>("generateGpuPhase6EffectCompositionFamiliesEvidence") {
    group = "verification"
    description = "Generates the GPU Phase 6 COMPOSITE + BLUR effect/composition family classification and evidence report."
    dependsOn(
        ":integration-tests:skia:generateSkiaDashboard",
        tasks.named("classes"),
    )
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6EffectCompositionFamiliesEvidenceCliKt")
    args(rootDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md"))
}
```

- [ ] **Step 3: Run targeted tests**

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6EffectCompositionFamilyEvidenceTest" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 4: Run generation task**

```bash
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence --rerun-tasks
```

Expected:

```text
BUILD SUCCESSFUL
Wrote /Users/chaos/.codex/worktrees/eda6/kanvas/reports/gpu-renderer/phase-6-effect-composition-families/evidence.json
Wrote /Users/chaos/.codex/worktrees/eda6/kanvas/reports/gpu-renderer/phase-6-effect-composition-families/classification.csv
Wrote /Users/chaos/.codex/worktrees/eda6/kanvas/reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md
```

- [ ] **Step 5: Commit CLI and Gradle task**

```bash
rtk proxy git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamiliesEvidenceCli.kt
rtk proxy git add integration-tests/skia-evidence/build.gradle.kts
rtk proxy git commit -m "Add Phase 6 effect composition evidence task"
```

---

### Task 4: Generated Evidence Artifacts and Validation Notes

**Files:**
- Create: `reports/gpu-renderer/phase-6-effect-composition-families/evidence.json`
- Create: `reports/gpu-renderer/phase-6-effect-composition-families/classification.csv`
- Create: `reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md`

**Interfaces:**
- Consumes: Gradle task `generateGpuPhase6EffectCompositionFamiliesEvidence` from Task 3.
- Produces: checked-in Phase 6 Effect + Composition evidence artifacts.

- [ ] **Step 1: Regenerate evidence**

```bash
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence --rerun-tasks
```

Expected: BUILD SUCCESSFUL and the three report files are written.

- [ ] **Step 2: Inspect summary counters**

Run:

```bash
rtk jq '.summary | {totalRows, families, classifications, promotedRows, unexpectedFails, noScore}' reports/gpu-renderer/phase-6-effect-composition-families/evidence.json
```

Expected: the command prints a JSON object with `totalRows` equal to `158`, `families.BLUR` equal to `45`, `families.COMPOSITE` equal to `113`, `promotedRows` equal to `0`, and non-negative `unexpectedFails` / `noScore` counters. Example shape:

```json
{
  "totalRows": 158,
  "families": {
    "BLUR": 45,
    "COMPOSITE": 113
  },
  "classifications": {},
  "promotedRows": 0,
  "unexpectedFails": 0,
  "noScore": 0
}
```

If `unexpectedFails` is non-zero, inspect rows with:

```bash
rtk jq -r '.rows[] | select(.classification == "unexpected-fail") | [.family, .name, .subfamily, .similarity, .minSimilarity] | @tsv' reports/gpu-renderer/phase-6-effect-composition-families/evidence.json
```

For each row, either add a stable gated subfamily/reason backed by the spec, or leave it as `unexpected-fail` only if the row genuinely has no stable expected refusal.

- [ ] **Step 3: Add validation section to Markdown report**

Append this validation section to `reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md` after generated content:

```markdown
## Validation

- `rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks`
- `rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence --rerun-tasks`
- `rtk proxy git diff --check`
```

If the writer later re-runs, it preserves the `## Validation` section.

- [ ] **Step 4: Run full evidence tests and whitespace check**

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
rtk proxy git diff --check
```

Expected: both commands pass.

- [ ] **Step 5: Commit generated evidence**

```bash
rtk proxy git add reports/gpu-renderer/phase-6-effect-composition-families/evidence.json
rtk proxy git add reports/gpu-renderer/phase-6-effect-composition-families/classification.csv
rtk proxy git add reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md
rtk proxy git commit -m "Generate Phase 6 effect composition evidence"
```

---

### Task 5: Independent Review and PR Preparation

**Files:**
- No new source files unless review finds required fixes.
- Review scope is the full branch diff from the merge-base with `origin/master`.

**Interfaces:**
- Consumes: commits from Tasks 1-4.
- Produces: reviewer verdict and a pushed branch ready for PR.

- [ ] **Step 1: Run final verification**

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence --rerun-tasks
rtk proxy git diff --check
```

Expected: all pass.

- [ ] **Step 2: Check worktree status**

```bash
rtk proxy git status --short --branch
```

Expected: clean branch status. If the generation task only changed `sourceGeneratedAt` timestamps after the evidence commit, either commit the regenerated artifacts if they are intended, or restore the timestamp-only churn with `apply_patch` before review.

- [ ] **Step 3: Launch independent review**

Use `superpowers:requesting-code-review` with:

```text
DESCRIPTION: Phase 6 Effect + Composition family evidence for COMPOSITE + BLUR.
PLAN_OR_REQUIREMENTS: docs/superpowers/specs/2026-07-09-gpu-phase-6-effect-composition-families-design.md and docs/superpowers/plans/2026-07-09-gpu-phase-6-effect-composition-families.md.
BASE_SHA: $(rtk proxy git merge-base origin/master HEAD)
HEAD_SHA: HEAD
```

Ask the reviewer to focus on:

- whether `COMPOSITE + BLUR` scope is respected;
- whether no `TEXT`, `IMAGE`, `PATH`, `CLIP`, `MATERIAL`, or `MESH` support is implied;
- whether advanced blend, `saveLayer`, destination-read, backdrop, image-filter DAG, matrix convolution, and large sigma blur remain gated;
- whether `promoted-support` remains zero unless route diagnostics prove support;
- whether artifacts overclaim support.

- [ ] **Step 4: Fix blocking review feedback**

If the reviewer reports Critical or Important issues, apply `superpowers:receiving-code-review`, verify each point against source, fix valid feedback, rerun:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence --rerun-tasks
rtk proxy git diff --check
```

Commit fixes with a focused message, then rerun independent review.

- [ ] **Step 5: Push and open PR**

```bash
rtk proxy git push -u origin codex/phase-6-effect-composition-families
```

Open a draft PR titled:

```text
GPU Phase 6 effect composition family evidence
```

PR body must mention:

- scope: `COMPOSITE + BLUR`;
- generated artifacts;
- validation commands;
- independent review result;
- explicit non-claims for broad `saveLayer`, destination-read, backdrop, image-filter DAG, matrix convolution, advanced blend, and arbitrary blur support.

---

## Self-Review Notes

- Spec coverage: tasks cover classifier/writer, Gradle generation, artifacts, no-score separation, stable refusals, non-claims, validation, and review.
- Completion scan: no incomplete implementation markers are required for workers.
- Type consistency: all public names use `Phase6EffectComposition...`; CLI and Gradle task use `generateGpuPhase6EffectCompositionFamiliesEvidence`.
- Scope check: renderer fixes are explicitly excluded; this plan is one evidence wave for `COMPOSITE + BLUR`.
