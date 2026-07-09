# GPU Phase 6 Material Families Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 6 evidence generation for `GRADIENT`, `RUNTIME_EFFECT`, and `COLOR` GM families.

**Architecture:** Add a focused Material evidence classifier/writer in `:integration-tests:skia-evidence`, mirroring the existing IMAGE and PATH+CLIP evidence flow. The implementation reads the regenerated Skia GM dashboard JSON, classifies only Material rows, and writes JSON/CSV/Markdown artifacts under `reports/gpu-renderer`.

**Tech Stack:** Kotlin/JVM, Gradle `JavaExec`, `kotlinx.serialization-json`, JUnit 5, existing `GmDashboardEvidence` dashboard reader.

## Global Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild SkSL, its IR, or its VM.
- Keep WebGPU as the GPU backend.
- Keep WGSL as the shader implementation target.
- Keep `SkRuntimeEffect` as a compatibility facade backed only by registered Kanvas descriptors.
- Do not include `COMPOSITE`, `BLUR`, `IMAGE_FILTERS`, `MESH`, or `TEXT` in this evidence wave.
- Do not claim broad shader, color-space, color-filter, arbitrary runtime-effect, compose-shader, or blend-chain support.
- Do not lower global similarity thresholds.
- Do not hide `no-score` rows.
- Prefix shell commands with `rtk`.

---

## File Structure

- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidence.kt`
  - Owns Material evidence data classes, classifier, JSON/CSV/Markdown serialization, and writer.
- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamiliesEvidenceCli.kt`
  - CLI entry point for the Gradle `JavaExec` task.
- Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidenceTest.kt`
  - Unit tests for family filtering, subfamilies, stable refusals, `no-score`, duplicate IDs, report content, and writer output.
- Modify `integration-tests/skia-evidence/build.gradle.kts`
  - Register `generateGpuPhase6MaterialFamiliesEvidence`.
- Create generated artifacts:
  - `reports/gpu-renderer/phase-6-material-families/evidence.json`
  - `reports/gpu-renderer/phase-6-material-families/classification.csv`
  - `reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md`

---

### Task 1: Material Classifier And Report Model

**Files:**
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidence.kt`
- Test: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes: `GmDashboard`, `GmDashboardRow`, `GmRgbaInt`, `GmRgbaDouble` from `GmDashboardEvidence.kt`.
- Produces:
  - `data class Phase6MaterialFamiliesEvidence`
  - `data class Phase6MaterialSummary`
  - `data class Phase6MaterialFamilyDelta`
  - `data class Phase6MaterialRowEvidence`
  - `object Phase6MaterialFamilyClassifier`
  - `fun Phase6MaterialFamilyClassifier.classify(row: GmDashboardRow, rowId: String = row.name): Phase6MaterialRowEvidence`
  - `fun Phase6MaterialFamilyClassifier.buildEvidence(dashboard: GmDashboard): Phase6MaterialFamiliesEvidence`

- [ ] **Step 1: Write failing classifier tests**

Add this initial test file:

```kotlin
package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertEquals

class Phase6MaterialFamilyEvidenceTest {
    @Test
    fun `classifies gradient subfamilies`() {
        assertEquals("gradient-linear", classify("linear_gradient", "GRADIENT").subfamily)
        assertEquals("gradient-radial", classify("radial_gradient3", "GRADIENT").subfamily)
        assertEquals("gradient-sweep", classify("rgbw_sweep_gradient", "GRADIENT").subfamily)
        assertEquals("gradient-conical", classify("conical_gradients", "GRADIENT").subfamily)
        assertEquals("gradient-hard-stops", classify("hardstop_gradients_many", "GRADIENT").subfamily)
        assertEquals("gradient-local-matrix", classify("gradient_matrix", "GRADIENT").subfamily)
    }

    @Test
    fun `classifies material gates with stable reasons`() {
        val perspective = classify("gradients_view_perspective", "GRADIENT", similarity = 20.0, isPassing = false)
        val manyStops = classify("gradients_color_space_many_stops", "GRADIENT", similarity = 20.0, isPassing = false)
        val colorSpace = classify("p3ovals", "COLOR", similarity = 20.0, isPassing = false)

        assertEquals("gradient-perspective-gated", perspective.subfamily)
        assertEquals("unsupported.material.perspective_shader", perspective.fallbackReason)
        assertEquals("expected-unsupported", perspective.classification)
        assertEquals("gradient-many-stops-gated", manyStops.subfamily)
        assertEquals("unsupported.material.gradient_many_stops", manyStops.fallbackReason)
        assertEquals("color-space-gated", colorSpace.subfamily)
        assertEquals("unsupported.material.color_space", colorSpace.fallbackReason)
    }

    @Test
    fun `classifies runtime effects and refusals`() {
        val registered = classify("linear_gradient_rt", "RUNTIME_EFFECT")
        val missing = classify("spiral_rt", "RUNTIME_EFFECT", similarity = 10.0, isPassing = false)
        val child = classify("runtime_shader_child_shader", "RUNTIME_EFFECT", similarity = 10.0, isPassing = false)
        val imageInput = classify("runtime_shader_image_surface", "RUNTIME_EFFECT", similarity = 10.0, isPassing = false)

        assertEquals("runtime-effect-registered", registered.subfamily)
        assertEquals("instrumented-existing", registered.classification)
        assertEquals("runtime-effect-unregistered-gated", missing.subfamily)
        assertEquals("unsupported.runtime_effect.unregistered_descriptor", missing.fallbackReason)
        assertEquals("runtime-effect-child-shader-gated", child.subfamily)
        assertEquals("unsupported.runtime_effect.child_shader", child.fallbackReason)
        assertEquals("runtime-effect-image-or-surface-gated", imageInput.subfamily)
        assertEquals("unsupported.runtime_effect.image_or_surface_input", imageInput.fallbackReason)
    }

    @Test
    fun `classifies color rows`() {
        assertEquals("color-solid", classify("color", "COLOR").subfamily)
        assertEquals("color-alpha", classify("paint_alpha_normals_rt", "COLOR").subfamily)
        assertEquals("color-filter-gated", classify("filter", "COLOR", similarity = 20.0, isPassing = false).subfamily)
        assertEquals("color-processor-gated", classify("const_color_processor", "COLOR", similarity = 20.0, isPassing = false).subfamily)
    }

    @Test
    fun `separates no score from unexpected fail`() {
        val noScore = classify("missing_gradient_reference", "GRADIENT", similarity = null, isPassing = null, noReference = true)
        val fail = classify("plain_gradient_fail", "GRADIENT", similarity = 20.0, isPassing = false)

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `build evidence filters material families only`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T09:00:00",
                rows = listOf(
                    row("linear_gradient", family = "GRADIENT"),
                    row("linear_gradient_rt", family = "RUNTIME_EFFECT"),
                    row("color", family = "COLOR"),
                    row("cubicpath", family = "PATH"),
                ),
            ),
        )

        assertEquals(3, evidence.summary.totalRows)
        assertEquals(mapOf("COLOR" to 1, "GRADIENT" to 1, "RUNTIME_EFFECT" to 1), evidence.summary.families)
        assertEquals(listOf("linear_gradient", "linear_gradient_rt", "color"), evidence.rows.map { it.name })
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
    ): Phase6MaterialRowEvidence =
        Phase6MaterialFamilyClassifier.classify(
            row(name = name, family = family, similarity = similarity, isPassing = isPassing, noReference = noReference),
        )
}

private fun row(
    name: String,
    family: String,
    similarity: Double? = 100.0,
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
        minSimilarity = 99.0,
        isPassing = isPassing,
        width = width,
        height = height,
        maxDiff = maxDiff,
        meanDiff = meanDiff,
        matchingPixels = matchingPixels,
        totalPixels = totalPixels,
        noReference = noReference,
        renderFailed = renderFailed,
        sizeMismatch = sizeMismatch,
        hasDiff = hasDiff,
    )
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6MaterialFamilyEvidenceTest" --rerun-tasks
```

Expected: compilation fails because `Phase6MaterialFamilyClassifier` and `Phase6MaterialRowEvidence` do not exist.

- [ ] **Step 3: Implement classifier and model**

Create `Phase6MaterialFamilyEvidence.kt` with these public shapes and logic:

```kotlin
package org.graphiks.kanvas.skia.evidence

data class Phase6MaterialFamiliesEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6MaterialSummary,
    val nonClaims: List<String>,
    val rows: List<Phase6MaterialRowEvidence>,
)

data class Phase6MaterialSummary(
    val totalRows: Int,
    val families: Map<String, Int>,
    val familyDeltas: Map<String, Phase6MaterialFamilyDelta>,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6MaterialFamilyDelta(
    val baselineSource: String,
    val currentSource: String,
    val currentGeneratedAt: String?,
    val baselineCount: Int,
    val currentCount: Int,
    val delta: Int,
)

data class Phase6MaterialRowEvidence(
    val rowId: String,
    val name: String,
    val family: String,
    val subfamily: String,
    val classification: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val width: Int?,
    val height: Int?,
    val matchingPixels: Long?,
    val totalPixels: Long?,
    val maxDiff: GmRgbaInt?,
    val meanDiff: GmRgbaDouble?,
    val fallbackReason: String,
    val referencePath: String?,
    val generatedPath: String?,
    val diffPath: String?,
    val noScoreCause: String?,
    val nonClaim: String,
)

object Phase6MaterialFamilyClassifier {
    private const val baselineSource = "2026-07-09 local dashboard before material-family wave"
    private val materialFamilies = setOf("GRADIENT", "RUNTIME_EFFECT", "COLOR")
    private val baselineFamilyCounts = linkedMapOf("COLOR" to 20, "GRADIENT" to 56, "RUNTIME_EFFECT" to 25)

    fun classify(row: GmDashboardRow, rowId: String = row.name): Phase6MaterialRowEvidence {
        require(row.family in materialFamilies) { "Expected GRADIENT, RUNTIME_EFFECT, or COLOR row, got ${row.family}" }
        val subfamily = materialSubfamily(row)
        val gatedReason = gatedReason(subfamily)
        val noScore = row.similarity == null || row.noReference || row.renderFailed || row.sizeMismatch

        val classification: String
        val fallback: String
        val noScoreCause: String?
        val nonClaim: String
        when {
            noScore -> {
                classification = "no-score"
                fallback = gatedReason ?: "none"
                noScoreCause = noScoreCause(row)
                nonClaim = "No score is not counted as fail or support; material evidence is incomplete."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "${row.family} row remains instrumented until route and material diagnostics are attached."
            }
            gatedReason != null -> {
                classification = "expected-unsupported"
                fallback = gatedReason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 material-family migration scope."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable material refusal."
            }
        }

        return Phase6MaterialRowEvidence(
            rowId = rowId,
            name = row.name,
            family = row.family,
            subfamily = subfamily,
            classification = classification,
            similarity = row.similarity,
            minSimilarity = row.minSimilarity,
            isPassing = row.isPassing,
            width = row.width,
            height = row.height,
            matchingPixels = row.matchingPixels,
            totalPixels = row.totalPixels,
            maxDiff = row.maxDiff,
            meanDiff = row.meanDiff,
            fallbackReason = fallback,
            referencePath = if (row.noReference) null else "images/reference/${row.name}.png",
            generatedPath = if (row.renderFailed) null else "images/generated/${row.name}.png",
            diffPath = if (row.hasDiff) "images/diff/${row.name}.png" else null,
            noScoreCause = noScoreCause,
            nonClaim = nonClaim,
        )
    }

    fun buildEvidence(dashboard: GmDashboard): Phase6MaterialFamiliesEvidence {
        val rows = dashboard.rows
            .filter { row -> row.family in materialFamilies }
            .withStableMaterialRowIds()
            .map { (rowId, row) -> classify(row, rowId) }
        val classifications = rows.groupingBy { it.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { it.subfamily }.eachCount().toSortedMap()
        val families = rows.groupingBy { it.family }.eachCount().toSortedMap()
        val familyDeltas = baselineFamilyCounts.mapValues { (family, baselineCount) ->
            val currentCount = families[family] ?: 0
            Phase6MaterialFamilyDelta(
                baselineSource = baselineSource,
                currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                currentGeneratedAt = dashboard.generatedAt,
                baselineCount = baselineCount,
                currentCount = currentCount,
                delta = currentCount - baselineCount,
            )
        }.toSortedMap()

        return Phase6MaterialFamiliesEvidence(
            schemaVersion = "phase6-material-families-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6MaterialFamiliesEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6MaterialSummary(
                totalRows = rows.size,
                families = families,
                familyDeltas = familyDeltas,
                classifications = classifications,
                subfamilies = subfamilies,
                promotedRows = classifications["promoted-support"] ?: 0,
                unexpectedFails = classifications["unexpected-fail"] ?: 0,
                noScore = classifications["no-score"] ?: 0,
            ),
            nonClaims = listOf(
                "No broad shader support is claimed from classification alone.",
                "No dynamic SkSL compiler or arbitrary SkRuntimeEffect support is added.",
                "COMPOSITE blend, compose shader, filter DAG, saveLayer, and destination-read rows remain outside this material-family wave.",
                "Rows without route and material diagnostics remain instrumented rather than promoted.",
            ),
            rows = rows,
        )
    }

    private fun materialSubfamily(row: GmDashboardRow): String {
        val name = row.name.materialKey()
        return when (row.family) {
            "GRADIENT" -> gradientSubfamily(name)
            "RUNTIME_EFFECT" -> runtimeEffectSubfamily(name)
            "COLOR" -> colorSubfamily(name)
            else -> error("Unexpected material family ${row.family}")
        }
    }

    private fun gradientSubfamily(name: String): String =
        when {
            name.containsAnyMaterial("persp", "perspective") -> "gradient-perspective-gated"
            name.containsAnyMaterial("colorspace", "p3", "hue") -> "gradient-color-space-gated"
            name.containsAnyMaterial("manystops", "manycolors") -> "gradient-many-stops-gated"
            name.containsAnyMaterial("tiling", "tilemode", "clamped", "scaledtiling") -> "gradient-tile-mode"
            name.containsAnyMaterial("matrix", "localmatrix") -> "gradient-local-matrix"
            name.containsAnyMaterial("hardstop", "hardstops") -> "gradient-hard-stops"
            name.contains("sweep") -> "gradient-sweep"
            name.contains("conical") -> "gradient-conical"
            name.contains("radial") -> "gradient-radial"
            else -> "gradient-linear"
        }

    private fun runtimeEffectSubfamily(name: String): String =
        when {
            name.containsAnyMaterial("childshader", "child") -> "runtime-effect-child-shader-gated"
            name.containsAnyMaterial("image", "surface") -> "runtime-effect-image-or-surface-gated"
            name.containsAnyMaterial("colorfilter", "colorfilterrt") -> "runtime-effect-color-filter"
            name.containsAnyMaterial("spiral", "runtimefunctions", "runtimeshader") -> "runtime-effect-unregistered-gated"
            else -> "runtime-effect-registered"
        }

    private fun colorSubfamily(name: String): String =
        when {
            name.containsAnyMaterial("colorspace", "p3", "srgb", "encode") -> "color-space-gated"
            name.containsAnyMaterial("filter", "colorfilter") -> "color-filter-gated"
            name.containsAnyMaterial("processor", "cube") -> "color-processor-gated"
            name.contains("alpha") -> "color-alpha"
            else -> "color-solid"
        }

    private fun gatedReason(subfamily: String): String? =
        when (subfamily) {
            "gradient-perspective-gated" -> "unsupported.material.perspective_shader"
            "gradient-color-space-gated" -> "unsupported.material.color_space"
            "gradient-many-stops-gated" -> "unsupported.material.gradient_many_stops"
            "gradient-tile-mode" -> "unsupported.material.gradient_tile_mode"
            "runtime-effect-unregistered-gated" -> "unsupported.runtime_effect.unregistered_descriptor"
            "runtime-effect-child-shader-gated" -> "unsupported.runtime_effect.child_shader"
            "runtime-effect-image-or-surface-gated" -> "unsupported.runtime_effect.image_or_surface_input"
            "color-space-gated" -> "unsupported.material.color_space"
            "color-filter-gated" -> "unsupported.material.color_filter"
            "color-processor-gated" -> "unsupported.material.color_processor"
            else -> null
        }

    private fun noScoreCause(row: GmDashboardRow): String =
        when {
            row.noReference -> "reference-missing"
            row.renderFailed -> "generated-render-missing"
            row.sizeMismatch -> "size-mismatch"
            row.similarity == null -> "comparison-unavailable"
            else -> "none"
        }

    private fun List<GmDashboardRow>.withStableMaterialRowIds(): List<Pair<String, GmDashboardRow>> {
        val seenCounts = linkedMapOf<String, Int>()
        return map { row ->
            val count = seenCounts.getOrDefault(row.name, 0) + 1
            seenCounts[row.name] = count
            val rowId = if (count == 1) row.name else "${row.name}#$count"
            rowId to row
        }
    }
}

private fun String.materialKey(): String =
    lowercase().filter { char -> char.isLetterOrDigit() }

private fun String.containsAnyMaterial(vararg tokens: String): Boolean =
    tokens.any(this::contains)
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6MaterialFamilyEvidenceTest" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk proxy git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidence.kt integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidenceTest.kt
rtk proxy git commit -m "Add Phase 6 material family classifier"
```

---

### Task 2: Material Serialization, Writer, CLI, And Gradle Task

**Files:**
- Modify: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidence.kt`
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamiliesEvidenceCli.kt`
- Modify: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidenceTest.kt`
- Modify: `integration-tests/skia-evidence/build.gradle.kts`

**Interfaces:**
- Consumes: Task 1 `Phase6MaterialFamiliesEvidence` and `Phase6MaterialFamilyClassifier.buildEvidence`.
- Produces:
  - `object Phase6MaterialFamilyEvidenceWriter`
  - `fun Phase6MaterialFamilyEvidenceWriter.writeOutputs(root: Path, evidence: Phase6MaterialFamiliesEvidence)`
  - `fun Phase6MaterialFamiliesEvidence.toJsonObject(): JsonObject`
  - `fun Phase6MaterialFamiliesEvidence.toCsv(): String`
  - `fun Phase6MaterialFamiliesEvidence.toMarkdown(): String`
  - CLI main class `org.graphiks.kanvas.skia.evidence.Phase6MaterialFamiliesEvidenceCliKt`
  - Gradle task `generateGpuPhase6MaterialFamiliesEvidence`

- [ ] **Step 1: Add failing writer/report tests**

Append these tests to `Phase6MaterialFamilyEvidenceTest`:

```kotlin
@Test
fun `duplicate material rows receive stable row ids and surface them in csv and markdown`() {
    val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
        GmDashboard(
            generatedAt = "2026-07-09T09:00:00",
            rows = listOf(
                row("linear_gradient", family = "GRADIENT"),
                row("linear_gradient", family = "GRADIENT", similarity = 20.0, isPassing = false),
            ),
        ),
    )

    assertEquals(listOf("linear_gradient", "linear_gradient#2"), evidence.rows.map { it.rowId })
    assertContains(evidence.toCsv(), "linear_gradient,linear_gradient,GRADIENT,gradient-linear,instrumented-existing")
    assertContains(evidence.toCsv(), "linear_gradient#2,linear_gradient,GRADIENT,gradient-linear,unexpected-fail")
    assertContains(evidence.toMarkdown(), "| `linear_gradient` | `linear_gradient` | `GRADIENT` | `gradient-linear` | `instrumented-existing` |")
    assertContains(evidence.toMarkdown(), "| `linear_gradient#2` | `linear_gradient` | `GRADIENT` | `gradient-linear` | `unexpected-fail` |")
}

@Test
fun `markdown report includes separate material family deltas`() {
    val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
        GmDashboard(
            generatedAt = "2026-07-09T09:00:00",
            rows = buildList {
                repeat(56) { add(row("gradient_$it", family = "GRADIENT")) }
                repeat(25) { add(row("runtime_$it", family = "RUNTIME_EFFECT")) }
                repeat(20) { add(row("color_$it", family = "COLOR")) }
            },
        ),
    )

    val markdown = evidence.toMarkdown()

    assertContains(markdown, "## Family Deltas")
    assertContains(markdown, "2026-07-09 local dashboard before material-family wave")
    assertContains(markdown, "| `COLOR` | 20 | 20 | +0 |")
    assertContains(markdown, "| `GRADIENT` | 56 | 56 | +0 |")
    assertContains(markdown, "| `RUNTIME_EFFECT` | 25 | 25 | +0 |")
}

@Test
fun `material evidence json includes diff stats and no score cause`() {
    val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
        GmDashboard(
            generatedAt = "2026-07-09T09:00:00",
            rows = listOf(
                row(
                    "linear_gradient",
                    family = "GRADIENT",
                    hasDiff = true,
                    width = 320,
                    height = 240,
                    matchingPixels = 70000,
                    totalPixels = 76800,
                    maxDiff = GmRgbaInt(r = 1, g = 2, b = 3, a = 4),
                    meanDiff = GmRgbaDouble(r = 1.25, g = 2.5, b = 3.75, a = 4.0),
                ),
                row("missing_runtime", family = "RUNTIME_EFFECT", similarity = null, isPassing = null, noReference = true),
            ),
        ),
    )

    val json = evidence.toJsonObject().toString()

    assertContains(json, "\"schemaVersion\":\"phase6-material-families-v1\"")
    assertContains(json, "\"width\":320")
    assertContains(json, "\"height\":240")
    assertContains(json, "\"matchingPixels\":70000")
    assertContains(json, "\"totalPixels\":76800")
    assertContains(json, "\"maxDiff\":{\"r\":1,\"g\":2,\"b\":3,\"a\":4}")
    assertContains(json, "\"meanDiff\":{\"r\":1.25,\"g\":2.5,\"b\":3.75,\"a\":4.0}")
    assertContains(json, "\"noScoreCause\":\"reference-missing\"")
}

@Test
fun `material writer creates json markdown and csv outputs and preserves validation section`() {
    val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
        GmDashboard(
            generatedAt = "2026-07-09T09:00:00",
            rows = listOf(row("linear_gradient", family = "GRADIENT"), row("color", family = "COLOR")),
        ),
    )

    val root = kotlin.io.path.createTempDirectory("phase6-material-evidence")
    Phase6MaterialFamilyEvidenceWriter.writeOutputs(root, evidence)

    val evidencePath = root.resolve("reports/gpu-renderer/phase-6-material-families/evidence.json")
    val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md")
    val csvPath = root.resolve("reports/gpu-renderer/phase-6-material-families/classification.csv")

    assertEquals(true, java.nio.file.Files.isRegularFile(evidencePath))
    assertEquals(true, java.nio.file.Files.isRegularFile(markdownPath))
    assertEquals(true, java.nio.file.Files.isRegularFile(csvPath))
    assertContains(java.nio.file.Files.readString(evidencePath), "\"schemaVersion\": \"phase6-material-families-v1\"")
    assertContains(java.nio.file.Files.readString(markdownPath), "No broad shader support is claimed")
    assertContains(java.nio.file.Files.readString(csvPath), "linear_gradient,linear_gradient,GRADIENT,gradient-linear,instrumented-existing")

    java.nio.file.Files.writeString(
        markdownPath,
        java.nio.file.Files.readString(markdownPath) +
            """

            ## Validation

            - `:integration-tests:skia-evidence:test` passed.
            - `generateGpuPhase6MaterialFamiliesEvidence` regenerated evidence.
            """.trimIndent() +
            "\n",
    )

    Phase6MaterialFamilyEvidenceWriter.writeOutputs(root, evidence)
    val regenerated = java.nio.file.Files.readString(markdownPath)
    assertContains(regenerated, "## Validation")
    assertContains(regenerated, "- `:integration-tests:skia-evidence:test` passed.")
}
```

Also add this import near the top:

```kotlin
import kotlin.test.assertContains
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6MaterialFamilyEvidenceTest" --rerun-tasks
```

Expected: compilation fails because `toCsv`, `toMarkdown`, `toJsonObject`, and `Phase6MaterialFamilyEvidenceWriter` do not exist.

- [ ] **Step 3: Implement serialization and writer**

In `Phase6MaterialFamilyEvidence.kt`, add imports matching coverage evidence:

```kotlin
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

Then add writer/serialization functions equivalent to coverage evidence, using Material names and paths:

```kotlin
object Phase6MaterialFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6MaterialFamiliesEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-material-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-material-families/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md")
        Files.createDirectories(evidencePath.parent)
        Files.createDirectories(markdownPath.parent)
        Files.writeString(evidencePath, prettyJson.encodeToString(JsonObject.serializer(), evidence.toJsonObject()) + "\n")
        Files.writeString(csvPath, evidence.toCsv())
        val preservedValidation = readValidationSection(markdownPath)
        val markdown = buildString {
            append(evidence.toMarkdown().trimEnd())
            if (preservedValidation != null) {
                appendLine()
                appendLine()
                append(preservedValidation.trim())
            }
            appendLine()
        }
        Files.writeString(markdownPath, markdown)
    }

    private fun readValidationSection(markdownPath: Path): String? {
        if (!Files.isRegularFile(markdownPath)) return null
        val existing = Files.readString(markdownPath)
        val heading = "## Validation"
        val start = existing.indexOf(heading)
        if (start < 0) return null
        return existing.substring(start).trim()
    }
}
```

Use the coverage file as the exact template for `toJsonObject`, nullable JSON helpers, `toCsv`, CSV escaping, and signed delta formatting, with these Material-specific Markdown strings:

```kotlin
fun Phase6MaterialFamiliesEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 Material Families Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total GRADIENT + RUNTIME_EFFECT + COLOR rows: ${summary.totalRows}")
        appendLine("- Families: ${summary.families}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine()
        appendLine("## Family Deltas")
        appendLine()
        appendLine("- Baseline source: `${summary.familyDeltas.values.firstOrNull()?.baselineSource ?: "2026-07-09 local dashboard before material-family wave"}`")
        appendLine("- Current dashboard: `${dashboardSource}` (${sourceGeneratedAt ?: "generatedAt unavailable"})")
        appendLine()
        appendLine("| Family | Baseline | Current | Delta |")
        appendLine("|---|---:|---:|---:|")
        summary.familyDeltas.entries.sortedBy { it.key }.forEach { (family, delta) ->
            appendLine("| `$family` | ${delta.baselineCount} | ${delta.currentCount} | ${delta.delta.toSignedMaterialDelta()} |")
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Reason Code Taxonomy")
        appendLine()
        appendLine("- Material `unsupported.material.*` and `unsupported.runtime_effect.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.")
        appendLine()
        appendLine("## Rows")
        appendLine()
        appendLine("| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback | No Score Cause |")
        appendLine("|---|---|---|---|---|---:|---|---|")
        rows.forEach { row ->
            val similarity = row.similarity?.let { "%.2f".format(java.util.Locale.US, it) } ?: "n/a"
            appendLine("| `${row.rowId}` | `${row.name}` | `${row.family}` | `${row.subfamily}` | `${row.classification}` | $similarity | `${row.fallbackReason}` | `${row.noScoreCause ?: ""}` |")
        }
        appendLine()
        appendLine("## Regeneration Notes")
        appendLine()
        appendLine("- Run `:integration-tests:skia:generateSkiaDashboard` before generating material-family evidence.")
        appendLine("- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.")
        appendLine("- `COMPOSITE` rows are intentionally excluded from this material-family wave.")
    }
```

- [ ] **Step 4: Add CLI**

Create `Phase6MaterialFamiliesEvidenceCli.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val materialDashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = root.resolve(materialDashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = Phase6MaterialFamilyClassifier.buildEvidence(dashboard)
    Phase6MaterialFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-material-families/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-material-families/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md")}")
}
```

- [ ] **Step 5: Add Gradle task**

Append this task to `integration-tests/skia-evidence/build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("generateGpuPhase6MaterialFamiliesEvidence") {
    group = "verification"
    description = "Generates the GPU Phase 6 GRADIENT + RUNTIME_EFFECT + COLOR material family classification and evidence report."
    dependsOn(
        ":integration-tests:skia:generateSkiaDashboard",
        tasks.named("classes"),
    )
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6MaterialFamiliesEvidenceCliKt")
    args(rootDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-material-families/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-material-families/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md"))
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6MaterialFamilyEvidenceTest" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
rtk proxy git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidence.kt integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamiliesEvidenceCli.kt integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6MaterialFamilyEvidenceTest.kt integration-tests/skia-evidence/build.gradle.kts
rtk proxy git commit -m "Generate Phase 6 material family evidence"
```

---

### Task 3: Generate Evidence Artifacts And Validate

**Files:**
- Create: `reports/gpu-renderer/phase-6-material-families/evidence.json`
- Create: `reports/gpu-renderer/phase-6-material-families/classification.csv`
- Create: `reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md`
- Modify if generated by dashboard task: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json` and report assets under `integration-tests/skia/build/reports/skia-gm-dashboard/` are build outputs and should not be committed.

**Interfaces:**
- Consumes: Task 2 Gradle task `generateGpuPhase6MaterialFamiliesEvidence`.
- Produces: checked-in Material evidence artifacts and final validation notes.

- [ ] **Step 1: Generate Material evidence**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6MaterialFamiliesEvidence --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` and stdout prints the three `reports/gpu-renderer/...material...` output paths.

- [ ] **Step 2: Inspect generated summaries**

Run:

```bash
rtk sed -n '1,180p' reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md
rtk node -e "const fs=require('fs'); const e=JSON.parse(fs.readFileSync('reports/gpu-renderer/phase-6-material-families/evidence.json','utf8')); console.log(JSON.stringify(e.summary,null,2));"
```

Expected:

- Markdown title is `# GPU Phase 6 Material Families Evidence`.
- Summary includes only `COLOR`, `GRADIENT`, and `RUNTIME_EFFECT`.
- JSON `summary.totalRows` equals the sum of those three family counts.
- `summary.familyDeltas` contains `COLOR`, `GRADIENT`, and `RUNTIME_EFFECT`.

- [ ] **Step 3: Add validation section to Markdown**

Append this section to `reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md` after generated content:

```markdown
## Validation

- `:integration-tests:skia-evidence:test --rerun-tasks` passed.
- `:integration-tests:skia-evidence:generateGpuPhase6MaterialFamiliesEvidence --rerun-tasks` regenerated `evidence.json`, `classification.csv`, and this report.
- `git diff --check` passed.
```

- [ ] **Step 4: Run focused and module verification**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
rtk proxy git diff --check
```

Expected:

- `:integration-tests:skia-evidence:test` ends with `BUILD SUCCESSFUL`.
- `git diff --check` prints no output and exits 0.

- [ ] **Step 5: Check Git status and commit generated artifacts**

Run:

```bash
rtk proxy git status --short
```

Expected untracked candidates include only the Material report directory and
the Material Markdown report:

```text
reports/gpu-renderer/phase-6-material-families/
reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md
```

Then commit:

```bash
rtk proxy git add reports/gpu-renderer/phase-6-material-families/evidence.json reports/gpu-renderer/phase-6-material-families/classification.csv reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md
rtk proxy git commit -m "Add Phase 6 material family evidence"
```

- [ ] **Step 6: Final branch verification**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --rerun-tasks
rtk proxy git diff --check
rtk proxy git status --short --branch
```

Expected:

- evidence tests pass;
- whitespace check passes;
- branch is `codex/phase-6-material-families`;
- worktree is clean.

---

## Self-Review Notes

- Spec coverage: tasks cover classifier/model, strict Material family filtering, subfamilies, stable refusal taxonomy, JSON/CSV/Markdown outputs, Gradle task, generated artifacts, tests, and non-claims.
- Scope: `COMPOSITE` is intentionally excluded and documented in the report.
- Type consistency: all produced names use the `Phase6Material*` prefix and mirror existing `Phase6Coverage*` names.
- Execution mode: use `superpowers:subagent-driven-development` for implementation unless the user explicitly asks for inline execution.
