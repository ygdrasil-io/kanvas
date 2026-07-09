# GPU Phase 6 Text + Mesh Families Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 6 evidence generation for `TEXT + MESH` with row-level classification, stable refusals, JSON/CSV/Markdown artifacts, and no broad text/mesh support claims.

**Architecture:** Add a focused Text + Mesh evidence classifier/writer in `:integration-tests:skia-evidence`, following the existing Phase 6 image, coverage, material, and effect/composition waves. The generator reads the Skia GM dashboard, filters only `TEXT` and `MESH`, classifies rows into subfamilies, emits stable fallback reasons, and writes evidence artifacts under `reports/gpu-renderer/phase-6-text-mesh-families/`.

**Tech Stack:** Kotlin/JVM, Gradle `JavaExec`, kotlinx.serialization JSON, `kotlin.test`, Skia GM dashboard JSON.

## Global Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild SkSL, its IR, or its VM.
- Keep WebGPU as the GPU backend.
- Keep WGSL as the shader target.
- Do not lower global similarity thresholds.
- Do not hide `no-score`.
- Do not promote a row without reference, generated render, diff/stat, route text/mesh diagnostics, explicit fallback policy, and non-claim.
- Do not claim broad shaping, font fallback, color emoji, color font, glyph atlas, custom mesh, vertices texture, perspective mesh, or mesh effects support.
- Keep heavy renderer fixes in separate PRs unless strictly required for evidence.
- Do not commit generated PNG changes or `.superpowers/sdd` artifacts.

---

## File Structure

- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidence.kt`
  - Owns data classes, classifier, writer, JSON/CSV/Markdown serialization, row id stability, subfamily policy, fallback reasons, follow-up candidates, and non-claims for `TEXT + MESH`.
- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamiliesEvidenceCli.kt`
  - Reads `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`, builds evidence, writes artifacts.
- Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt`
  - Unit tests for filtering, subfamily classification, `no-score`, duplicate ids, writer output, preserved validation section, JSON/CSV/Markdown shape, and non-claims.
- Modify `integration-tests/skia-evidence/build.gradle.kts`
  - Add `generateGpuPhase6TextMeshFamiliesEvidence`.
- Generate:
  - `reports/gpu-renderer/phase-6-text-mesh-families/evidence.json`
  - `reports/gpu-renderer/phase-6-text-mesh-families/classification.csv`
  - `reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md`

---

### Task 0: Baseline Verification

**Files:**
- Read: `docs/superpowers/specs/2026-07-09-gpu-phase-6-text-mesh-families-design.md`
- Read: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/GmDashboardEvidence.kt`
- Read: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6EffectCompositionFamilyEvidence.kt`
- Read: `integration-tests/skia-evidence/build.gradle.kts`

**Interfaces:**
- Consumes: current `GmDashboard`, `GmDashboardRow`, and dashboard reader APIs.
- Produces: clean baseline evidence for the next tasks.

- [ ] **Step 1: Verify branch and worktree**

Run:

```bash
rtk proxy git status --short --branch
```

Expected: branch is `codex/phase-6-text-mesh-families`, no modified files except intentional plan/spec work already committed.

- [ ] **Step 2: Verify baseline tests**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify current dashboard family counts**

Run:

```bash
rtk jq -r '.gms | group_by(.family)[] | "\(length) \(.[0].family)"' integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json | sort -k2
```

Expected output includes:

```text
16 MESH
77 TEXT
```

- [ ] **Step 4: Commit**

No commit is required for a pure verification task. If the task creates local build output only, leave it untracked.

---

### Task 1: Text + Mesh Classifier Tests

**Files:**
- Create: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes: `GmDashboard`, `GmDashboardRow`, `GmRgbaInt`, `GmRgbaDouble` from `GmDashboardEvidence.kt`.
- Produces expected API for Task 2:
  - `object Phase6TextMeshFamilyClassifier`
  - `fun classify(row: GmDashboardRow, rowId: String = row.name): Phase6TextMeshRowEvidence`
  - `fun buildEvidence(dashboard: GmDashboard): Phase6TextMeshFamiliesEvidence`
  - `data class Phase6TextMeshRowEvidence`
  - `object Phase6TextMeshFamilyEvidenceWriter`
  - `fun Phase6TextMeshFamiliesEvidence.toJsonObject(): JsonObject`
  - `fun Phase6TextMeshFamiliesEvidence.toCsv(): String`
  - `fun Phase6TextMeshFamiliesEvidence.toMarkdown(): String`

- [ ] **Step 1: Create the failing test file**

Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt` with these tests and helpers:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Phase6TextMeshFamilyEvidenceTest {
    @Test
    fun `build evidence filters text and mesh only`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("bigtext", family = "TEXT"),
                    row("vertices", family = "MESH"),
                    row("srcmode", family = "COMPOSITE"),
                    row("linear_gradient", family = "GRADIENT"),
                ),
            ),
        )

        assertEquals(2, evidence.summary.totalRows)
        assertEquals(mapOf("MESH" to 1, "TEXT" to 1), evidence.summary.families)
        assertEquals(listOf("bigtext", "vertices"), evidence.rows.map { it.name })
        assertEquals("phase6-text-mesh-families-v1", evidence.schemaVersion)
    }

    @Test
    fun `classifies text subfamilies with stable reasons`() {
        val basic = classify("bigtext", "TEXT")
        val rsxform = classify("drawTextRSXform", "TEXT", similarity = null, isPassing = null, renderFailed = true)
        val perspective = classify("dftext_blob_persp", "TEXT", similarity = null, isPassing = null, renderFailed = true)
        val fontManager = classify("fontmgr_match", "TEXT")
        val emoji = classify("coloremoji", "TEXT", similarity = null, isPassing = null, noReference = true)
        val colorFont = classify("colrv1_gradient_stops_repeat", "TEXT")
        val shader = classify("chrome_gradtext2", "TEXT")
        val filter = classify("textfilter_image", "TEXT")
        val clip = classify("cliperror", "TEXT", similarity = null, isPassing = null, renderFailed = true)

        assertEquals("text-basic-latin", basic.subfamily)
        assertEquals("instrumented-existing", basic.classification)
        assertEquals("text-rsxform-gated", rsxform.subfamily)
        assertEquals("unsupported.text.rsxform", rsxform.fallbackReason)
        assertEquals("text-perspective-or-transform-gated", perspective.subfamily)
        assertEquals("unsupported.text.perspective", perspective.fallbackReason)
        assertEquals("text-font-manager-gated", fontManager.subfamily)
        assertEquals("unsupported.text.font_manager", fontManager.fallbackReason)
        assertEquals("expected-unsupported", fontManager.classification)
        assertEquals("text-emoji-gated", emoji.subfamily)
        assertEquals("unsupported.text.emoji", emoji.fallbackReason)
        assertEquals("text-color-font-gated", colorFont.subfamily)
        assertEquals("unsupported.text.color_font", colorFont.fallbackReason)
        assertEquals("text-shader-or-gradient-gated", shader.subfamily)
        assertEquals("unsupported.text.shader_or_gradient", shader.fallbackReason)
        assertEquals("text-filter-or-blur-gated", filter.subfamily)
        assertEquals("unsupported.text.filter_or_blur", filter.fallbackReason)
        assertEquals("text-clip-interaction-gated", clip.subfamily)
        assertEquals("unsupported.text.clip_interaction", clip.fallbackReason)
    }

    @Test
    fun `classifies mesh subfamilies with stable reasons`() {
        val vertices = classify("vertices", "MESH")
        val custom = classify("custommesh", "MESH")
        val customUniforms = classify("custommesh_cs_uniforms", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val effects = classify("mesh_with_effects", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val image = classify("mesh_with_image", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val paintColor = classify("mesh_with_paint_color", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val paintImage = classify("mesh_with_paint_image", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val perspective = classify("vertices_perspective", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val updates = classify("mesh_updates", "MESH")
        val zeroInit = classify("mesh_zero_init", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val picture = classify("picture_mesh", "MESH")

        assertEquals("mesh-basic-vertices", vertices.subfamily)
        assertEquals("instrumented-existing", vertices.classification)
        assertEquals("mesh-custom-basic", custom.subfamily)
        assertEquals("instrumented-existing", custom.classification)
        assertEquals("mesh-custom-uniforms-gated", customUniforms.subfamily)
        assertEquals("unsupported.mesh.custom_uniforms", customUniforms.fallbackReason)
        assertEquals("mesh-effect-dependency-gated", effects.subfamily)
        assertEquals("unsupported.mesh.effect_dependency", effects.fallbackReason)
        assertEquals("mesh-image-dependency-gated", image.subfamily)
        assertEquals("unsupported.mesh.image_dependency", image.fallbackReason)
        assertEquals("mesh-paint-color-dependency-gated", paintColor.subfamily)
        assertEquals("unsupported.mesh.paint_color_dependency", paintColor.fallbackReason)
        assertEquals("mesh-paint-image-dependency-gated", paintImage.subfamily)
        assertEquals("unsupported.mesh.paint_image_dependency", paintImage.fallbackReason)
        assertEquals("mesh-perspective-gated", perspective.subfamily)
        assertEquals("unsupported.mesh.perspective", perspective.fallbackReason)
        assertEquals("mesh-update-or-dynamic-gated", updates.subfamily)
        assertEquals("unsupported.mesh.dynamic_updates", updates.fallbackReason)
        assertEquals("mesh-zero-init-gated", zeroInit.subfamily)
        assertEquals("unsupported.mesh.zero_init", zeroInit.fallbackReason)
        assertEquals("mesh-picture-dependency-gated", picture.subfamily)
        assertEquals("unsupported.mesh.picture_dependency", picture.fallbackReason)
    }

    @Test
    fun `separates no score from unexpected fail`() {
        val noScore = classify("coloremoji", "TEXT", similarity = null, isPassing = null, noReference = true)
        val fail = classify("plain_text_fail", "TEXT", similarity = 20.0, isPassing = false)

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unsupported.text.emoji", noScore.fallbackReason)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `passing gated rows remain expected unsupported`() {
        val text = classify("fontmgr_match", "TEXT", similarity = 100.0, isPassing = true)
        val mesh = classify("mesh_updates", "MESH", similarity = 100.0, isPassing = true)

        assertEquals("expected-unsupported", text.classification)
        assertEquals("unsupported.text.font_manager", text.fallbackReason)
        assertEquals("expected-unsupported", mesh.classification)
        assertEquals("unsupported.mesh.dynamic_updates", mesh.fallbackReason)
    }

    @Test
    fun `build evidence computes family deltas from baseline`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("bigtext", family = "TEXT"),
                    row("fontmgr_match", family = "TEXT"),
                    row("vertices", family = "MESH"),
                ),
            ),
        )

        assertEquals(77, evidence.summary.familyDeltas.getValue("TEXT").baselineCount)
        assertEquals(2, evidence.summary.familyDeltas.getValue("TEXT").currentCount)
        assertEquals(-75, evidence.summary.familyDeltas.getValue("TEXT").delta)
        assertEquals(16, evidence.summary.familyDeltas.getValue("MESH").baselineCount)
        assertEquals(1, evidence.summary.familyDeltas.getValue("MESH").currentCount)
        assertEquals(-15, evidence.summary.familyDeltas.getValue("MESH").delta)
    }

    @Test
    fun `duplicate rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("vertices", family = "MESH"),
                    row("vertices", family = "MESH"),
                ),
            ),
        )

        assertEquals(listOf("vertices", "vertices#2"), evidence.rows.map { it.rowId })
        assertContains(evidence.toCsv(), "vertices#2,vertices,MESH")
        assertContains(evidence.toMarkdown(), "`vertices#2`")
    }

    @Test
    fun `evidence json includes diff stats no score cause and follow ups`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("coloremoji", family = "TEXT", similarity = null, isPassing = null, noReference = true),
                    row("mesh_updates", family = "MESH", maxDiff = GmRgbaInt(1, 2, 3, 4), meanDiff = GmRgbaDouble(1.5, 2.5, 3.5, 4.5)),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()
        assertContains(json, "\"schemaVersion\":\"phase6-text-mesh-families-v1\"")
        assertContains(json, "\"noScoreCause\":\"reference-missing\"")
        assertContains(json, "\"maxDiff\":{\"r\":1,\"g\":2,\"b\":3,\"a\":4}")
        assertContains(json, "\"meanDiff\":{\"r\":1.5,\"g\":2.5,\"b\":3.5,\"a\":4.5}")
        assertContains(json, "\"followUpCandidates\"")
        assertContains(json, "\"unsupported.mesh.dynamic_updates\"")
    }

    @Test
    fun `writer creates json markdown and csv outputs and preserves validation section`() {
        val root = Files.createTempDirectory("phase6-text-mesh-evidence-test")
        val markdown = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md")
        Files.createDirectories(markdown.parent)
        Files.writeString(markdown, "# Existing\n\n## Validation\n\n- keep this validation note\n")
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(row("bigtext", family = "TEXT")),
            ),
        )

        Phase6TextMeshFamilyEvidenceWriter.writeOutputs(root, evidence)

        assertContains(Files.readString(root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/evidence.json")), "phase6-text-mesh-families-v1")
        assertContains(Files.readString(root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/classification.csv")), "rowId,name,family,subfamily,classification")
        assertContains(Files.readString(markdown), "## Validation")
        assertContains(Files.readString(markdown), "keep this validation note")
    }

    @Test
    fun `non claims do not mention excluded support as complete`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(row("bigtext", family = "TEXT")),
            ),
        )

        val nonClaims = evidence.nonClaims.joinToString("\n")
        assertContains(nonClaims, "No broad TEXT or MESH support is claimed from classification alone.")
        assertContains(nonClaims, "shaping, font fallback, glyph cache, color fonts, emoji, palettes, transformed text, text filters, and clip/text interactions remain outside this evidence wave unless row diagnostics prove a bounded route.")
        assertContains(nonClaims, "custom mesh, dynamic mesh updates, perspective mesh, picture mesh, image dependencies, paint-image dependencies, mesh effects, and arbitrary vertices remain outside this evidence wave unless row diagnostics prove a bounded route.")
        assertFalse(nonClaims.contains("complete support", ignoreCase = true))
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 99.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
    ): Phase6TextMeshRowEvidence =
        Phase6TextMeshFamilyClassifier.classify(
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
        minSimilarity: Double? = 99.0,
        isPassing: Boolean? = true,
        width: Int? = null,
        height: Int? = null,
        matchingPixels: Long? = null,
        totalPixels: Long? = null,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        hasDiff: Boolean = false,
    ): GmDashboardRow =
        GmDashboardRow(
            name = name,
            family = family,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = width,
            height = height,
            matchingPixels = matchingPixels,
            totalPixels = totalPixels,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            noReference = noReference,
            renderFailed = renderFailed,
            sizeMismatch = sizeMismatch,
            hasDiff = hasDiff,
        )
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests org.graphiks.kanvas.skia.evidence.Phase6TextMeshFamilyEvidenceTest --rerun-tasks
```

Expected: compile failure mentioning unresolved references such as `Phase6TextMeshFamilyClassifier` and `Phase6TextMeshRowEvidence`.

- [ ] **Step 3: Commit the RED tests**

Run:

```bash
rtk proxy git add integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt
rtk proxy git commit -m "Add Phase 6 text mesh evidence tests"
```

Expected: commit succeeds even though the branch is intentionally red. This branch may carry unstable intermediate commits; do not merge while red.

---

### Task 2: Text + Mesh Classifier And Writer

**Files:**
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidence.kt`
- Modify: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt` only if the test reveals a naming mismatch.

**Interfaces:**
- Consumes tests from Task 1.
- Produces:
  - `Phase6TextMeshFamiliesEvidence`
  - `Phase6TextMeshSummary`
  - `Phase6TextMeshFamilyDelta`
  - `Phase6TextMeshFollowUpCandidate`
  - `Phase6TextMeshRowEvidence`
  - `Phase6TextMeshFamilyClassifier`
  - `Phase6TextMeshFamilyEvidenceWriter`
  - `toJsonObject()`, `toCsv()`, `toMarkdown()`

- [ ] **Step 1: Implement the data classes and classifier skeleton**

Create `Phase6TextMeshFamilyEvidence.kt` with data classes mirroring the Task 1 interfaces. Use:

```kotlin
private val textMeshFamilies = setOf("TEXT", "MESH")
private val baselineFamilyCounts = linkedMapOf("MESH" to 16, "TEXT" to 77)
```

Classification order must be:

1. compute `subfamily`;
2. compute `gatedReason`;
3. compute `noScore`;
4. if `noScore`, classification is `no-score` and fallback is `gatedReason ?: "none"`;
5. else if `gatedReason != null`, classification is `expected-unsupported`;
6. else if `row.isPassing == true`, classification is `instrumented-existing`;
7. else classification is `unexpected-fail`.

- [ ] **Step 2: Implement text subfamilies**

Add `private fun textSubfamily(name: String): String` with this precedence:

```kotlin
when {
    name.containsAnyTextMesh("clip") -> "text-clip-interaction-gated"
    name.containsAnyTextMesh("textfilter", "blur") -> "text-filter-or-blur-gated"
    name.containsAnyTextMesh("gradtext", "shader") -> "text-shader-or-gradient-gated"
    name.containsAnyTextMesh("rsxform") -> "text-rsxform-gated"
    name.containsAnyTextMesh("persp", "perspective", "dftextblobpersp") -> "text-perspective-or-transform-gated"
    name.containsAnyTextMesh("fontmgr", "fontmanager") -> "text-font-manager-gated"
    name.containsAnyTextMesh("fontcache", "glyphcache") -> "text-large-or-cache"
    name.containsAnyTextMesh("fontregen", "fallback") -> "text-font-fallback-gated"
    name.containsAnyTextMesh("coloremoji", "emoji") -> "text-emoji-gated"
    name.containsAnyTextMesh("colrv1", "colrv0", "colorfont") -> "text-color-font-gated"
    name.containsAnyTextMesh("palette") -> "text-color-palette-gated"
    name.containsAnyTextMesh("textblob", "blob") -> "text-blob-gated"
    name.containsAnyTextMesh("annotated", "annotation") -> "text-annotation-gated"
    name.containsAnyTextMesh("bigtext", "largeglyph") -> "text-large-or-cache"
    else -> "text-basic-latin"
}
```

- [ ] **Step 3: Implement mesh subfamilies**

Add `private fun meshSubfamily(name: String): String` with this precedence:

```kotlin
when {
    name.containsAnyTextMesh("uniform") -> "mesh-custom-uniforms-gated"
    name.containsAnyTextMesh("effect") -> "mesh-effect-dependency-gated"
    name.containsAnyTextMesh("paintimage") -> "mesh-paint-image-dependency-gated"
    name.containsAnyTextMesh("paintcolor") -> "mesh-paint-color-dependency-gated"
    name.containsAnyTextMesh("image") -> "mesh-image-dependency-gated"
    name.containsAnyTextMesh("perspective", "persp") -> "mesh-perspective-gated"
    name.containsAnyTextMesh("updates", "dynamic") -> "mesh-update-or-dynamic-gated"
    name.containsAnyTextMesh("zeroinit", "zero") -> "mesh-zero-init-gated"
    name.containsAnyTextMesh("picture") -> "mesh-picture-dependency-gated"
    name.containsAnyTextMesh("colorspace", "cs") -> "mesh-color-space-gated"
    name.containsAnyTextMesh("custommesh") -> "mesh-custom-basic"
    else -> "mesh-basic-vertices"
}
```

- [ ] **Step 4: Implement stable reason codes**

Map gated subfamilies exactly:

```kotlin
"text-rsxform-gated" -> "unsupported.text.rsxform"
"text-perspective-or-transform-gated" -> "unsupported.text.perspective"
"text-font-manager-gated" -> "unsupported.text.font_manager"
"text-font-fallback-gated" -> "unsupported.text.font_fallback"
"text-color-font-gated" -> "unsupported.text.color_font"
"text-emoji-gated" -> "unsupported.text.emoji"
"text-color-palette-gated" -> "unsupported.text.palette"
"text-blob-gated" -> "unsupported.text.glyph_cache"
"text-shader-or-gradient-gated" -> "unsupported.text.shader_or_gradient"
"text-filter-or-blur-gated" -> "unsupported.text.filter_or_blur"
"text-clip-interaction-gated" -> "unsupported.text.clip_interaction"
"mesh-custom-uniforms-gated" -> "unsupported.mesh.custom_uniforms"
"mesh-color-space-gated" -> "unsupported.mesh.color_space"
"mesh-effect-dependency-gated" -> "unsupported.mesh.effect_dependency"
"mesh-image-dependency-gated" -> "unsupported.mesh.image_dependency"
"mesh-paint-color-dependency-gated" -> "unsupported.mesh.paint_color_dependency"
"mesh-paint-image-dependency-gated" -> "unsupported.mesh.paint_image_dependency"
"mesh-perspective-gated" -> "unsupported.mesh.perspective"
"mesh-update-or-dynamic-gated" -> "unsupported.mesh.dynamic_updates"
"mesh-zero-init-gated" -> "unsupported.mesh.zero_init"
"mesh-picture-dependency-gated" -> "unsupported.mesh.picture_dependency"
```

- [ ] **Step 5: Implement JSON/CSV/Markdown writers**

Use the same field names as Task 1 expects:

```text
schemaVersion, generatedBy, dashboardSource, sourceGeneratedAt, summary,
nonClaims, followUpCandidates, rows
```

CSV header must be:

```text
rowId,name,family,subfamily,classification,similarity,minSimilarity,isPassing,width,height,matchingPixels,totalPixels,maxDiff,meanDiff,fallbackReason,referencePath,generatedPath,diffPath,noScoreCause
```

Markdown heading must be:

```markdown
# GPU Phase 6 Text Mesh Families Evidence
```

Preserve an existing `## Validation` section by reading the current Markdown before overwriting it and appending that section back to the regenerated content.

- [ ] **Step 6: Run tests and verify GREEN**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests org.graphiks.kanvas.skia.evidence.Phase6TextMeshFamilyEvidenceTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

Run:

```bash
rtk proxy git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidence.kt integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt
rtk proxy git commit -m "Add Phase 6 text mesh evidence classifier"
```

Expected: commit succeeds.

---

### Task 3: CLI And Gradle Task

**Files:**
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamiliesEvidenceCli.kt`
- Modify: `integration-tests/skia-evidence/build.gradle.kts`
- Test: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes: `Phase6TextMeshFamilyClassifier.buildEvidence`.
- Produces: Gradle task `generateGpuPhase6TextMeshFamiliesEvidence`.

- [ ] **Step 1: Add CLI**

Create `Phase6TextMeshFamiliesEvidenceCli.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.Path

fun main(args: Array<String>) {
    val root = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val dashboardPath = args.getOrNull(1)?.let(::Path)
        ?: root.resolve("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")
    val dashboard = GmDashboardReader.read(dashboardPath)
    val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(dashboard)
    Phase6TextMeshFamilyEvidenceWriter.writeOutputs(root, evidence)
}
```

- [ ] **Step 2: Add Gradle task**

In `integration-tests/skia-evidence/build.gradle.kts`, register:

```kotlin
tasks.register<JavaExec>("generateGpuPhase6TextMeshFamiliesEvidence") {
    group = "verification"
    description = "Generate GPU Phase 6 TEXT + MESH family evidence reports."
    dependsOn(":integration-tests:skia:generateSkiaDashboard", "classes")
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6TextMeshFamiliesEvidenceCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args(rootProject.projectDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-text-mesh-families/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-text-mesh-families/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md"))
}
```

If this project already uses a helper pattern for evidence tasks in the same file, use that local pattern but keep the task name, main class, inputs, outputs, group, and description above.

- [ ] **Step 3: Verify task is visible**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:tasks --group verification
```

Expected output includes `generateGpuPhase6TextMeshFamiliesEvidence`.

- [ ] **Step 4: Run tests**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
rtk proxy git add integration-tests/skia-evidence/build.gradle.kts integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6TextMeshFamiliesEvidenceCli.kt
rtk proxy git commit -m "Add Phase 6 text mesh evidence task"
```

Expected: commit succeeds.

---

### Task 4: Generate Evidence Reports

**Files:**
- Generate: `reports/gpu-renderer/phase-6-text-mesh-families/evidence.json`
- Generate: `reports/gpu-renderer/phase-6-text-mesh-families/classification.csv`
- Generate: `reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md`
- Do not commit: `integration-tests/skia/src/test/resources/generated-renders/**/*.png`

**Interfaces:**
- Consumes: Gradle task from Task 3.
- Produces: checked-in evidence artifacts and final counters.

- [ ] **Step 1: Generate evidence**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6TextMeshFamiliesEvidence --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` and the three text/mesh report files are written. The full dashboard may still report unrelated failures outside `TEXT + MESH`; record them in PR validation if present.

- [ ] **Step 2: Inspect summary counters**

Run:

```bash
rtk jq '.summary | {totalRows, families, classifications, promotedRows, unexpectedFails, noScore}' reports/gpu-renderer/phase-6-text-mesh-families/evidence.json
```

Expected invariant fields:

```json
{
  "totalRows": 93,
  "families": {
    "MESH": 16,
    "TEXT": 77
  }
}
```

Also verify:

```bash
rtk jq -e '.summary.promotedRows == 0 and (.summary.unexpectedFails | type == "number") and (.summary.noScore | type == "number") and (.summary.classifications | type == "object")' reports/gpu-renderer/phase-6-text-mesh-families/evidence.json
```

Expected: command exits `0`. If `unexpectedFails` is non-zero, inspect and either add a stable reason or document a follow-up before PR.

- [ ] **Step 3: Restore generated PNG noise**

Run:

```bash
rtk proxy git restore -- integration-tests/skia/src/test/resources/generated-renders
```

Expected: generated PNGs are removed from `git status`.

- [ ] **Step 4: Verify no generated PNGs or SDD artifacts are in the diff**

Run:

```bash
rtk proxy git status --short
rtk proxy git diff --name-only HEAD -- integration-tests/skia/src/test/resources/generated-renders .superpowers/sdd
```

Expected: the second command prints nothing.

- [ ] **Step 5: Verify report content**

Run:

```bash
rtk rg -n "Promoted rows|Unexpected fails|No score|Follow-Up Candidates|No broad TEXT or MESH" reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md
```

Expected: all terms are present.

- [ ] **Step 6: Commit reports**

Run:

```bash
rtk proxy git add reports/gpu-renderer/phase-6-text-mesh-families/evidence.json reports/gpu-renderer/phase-6-text-mesh-families/classification.csv reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md
rtk proxy git commit -m "Generate Phase 6 text mesh evidence"
```

Expected: commit succeeds and includes only the report files.

---

### Task 5: Final Verification, Review, And PR

**Files:**
- Read: all files changed by Tasks 1-4.
- Do not create: `.superpowers/sdd/task-*.md` tracked artifacts.

**Interfaces:**
- Consumes: completed evidence implementation and generated reports.
- Produces: reviewed branch ready for PR.

- [ ] **Step 1: Run final tests**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run final generation**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6TextMeshFamiliesEvidence --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`. Restore PNG noise afterward:

```bash
rtk proxy git restore -- integration-tests/skia/src/test/resources/generated-renders
```

- [ ] **Step 3: Run whitespace and scope checks**

Run:

```bash
rtk proxy git diff --check
rtk proxy git diff --name-only origin/master..HEAD -- integration-tests/skia/src/test/resources/generated-renders .superpowers/sdd
```

Expected: both commands print nothing.

- [ ] **Step 4: Request independent review**

Create a review package from `origin/master..HEAD` and dispatch a code reviewer subagent. The review prompt must require checks for:

```text
TEXT + MESH only.
No broad support claims.
Stable refusal taxonomy.
No generated PNG changes.
No .superpowers/sdd artifacts in cumulative diff.
JSON/CSV/Markdown counters consistent.
promotedRows remains 0 unless complete route diagnostics exist.
unexpectedFails is 0 or explicitly justified.
```

- [ ] **Step 5: Fix Critical/Important findings**

For each Critical or Important review finding:

1. verify it against the code and reports;
2. fix it in code/tests/reports;
3. run the targeted test or generation command proving the fix;
4. commit the fix;
5. request a follow-up review.

- [ ] **Step 6: Push and open PR**

Run:

```bash
rtk proxy git push -u origin codex/phase-6-text-mesh-families
SUMMARY=$(rtk jq -c '.summary | {totalRows, families, classifications, promotedRows, unexpectedFails, noScore}' reports/gpu-renderer/phase-6-text-mesh-families/evidence.json)
REVIEW_RESULT="no Critical/Important findings after final independent review"
rtk gh pr create --draft --base master --head codex/phase-6-text-mesh-families --title "GPU Phase 6 text mesh family evidence" --body "## Summary
- add Phase 6 evidence docs for the TEXT + MESH wave
- add classifier, evidence writer, Gradle generation task, and tests for text/mesh families
- generate JSON/CSV/Markdown evidence with stable refusal taxonomy and follow-up candidates

## Evidence
- Scope: TEXT + MESH only
- Summary: \`$SUMMARY\`
- Broad text/glyph/font/emoji/mesh support is not claimed

## Validation
- rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
- rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6TextMeshFamiliesEvidence --rerun-tasks
- rtk proxy git diff --check
- Independent review result: $REVIEW_RESULT"
```

The PR body must include:

```text
Scope: TEXT + MESH only.
Rows: total, TEXT, MESH.
Classifications: exact evidence counts.
promotedRows and unexpectedFails.
Validation commands run.
Independent review result.
Explicit non-claims for broad text/glyph/font/emoji/mesh support.
```
