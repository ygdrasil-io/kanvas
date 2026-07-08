# GPU Phase 6 Image Family Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 6 `IMAGE` GM family migration wave with filterable image renders, row-level classification, stable evidence reports, and one concrete texture/sampler cache proof without broad image support claims.

**Architecture:** Keep `:integration-tests:skia` as the owner of GM render/dashboard generation. Add a dedicated Kotlin/JVM library module, `:integration-tests:skia-evidence`, that reads the Skia GM dashboard JSON with `kotlinx.serialization-json`, classifies `RenderFamily.IMAGE` rows, consumes optional resource/cache evidence, and writes JSON/CSV/Markdown reports. Keep renderer-owned texture/sampler evidence in `:gpu-renderer` so the evidence module aggregates facts without depending on renderer internals beyond files.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, `kotlinx.serialization-json`, `kotlin.test`/JUnit, existing Kanvas GPU renderer resource/provider contracts, existing Skia GM dashboard.

## Global Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild SkSL, its IR, or its VM.
- Keep WebGPU as the GPU backend.
- Keep WGSL as the shader target.
- Do not use a hidden CPU-rendered compatibility texture.
- Do not add codec, animation, mipmap, YUV, perspective, image-filter, or broad color-management support in this wave.
- Do not lower global similarity thresholds.
- Do not count `cpu-oracle` rows as Skia-comparable fidelity.
- Keep `no-score` separate from true fails.
- Keep stable reason codes for refusals.
- Prefix shell commands with `rtk`.
- No broad support claim may be made from classification alone.
- Do not add Python evidence generation for this Phase 6 IMAGE wave.

---

## File Structure

- Modify `settings.gradle.kts`
  Includes the new `:integration-tests:skia-evidence` module.
- Create `integration-tests/skia-evidence/build.gradle.kts`
  Defines the Kotlin/JVM library and the `generateGpuPhase6ImageFamilyEvidence` `JavaExec` task.
- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt`
  Contains dashboard row parsing, classification policy, evidence model, resource evidence loading, and report writing.
- Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt`
  Command-line entrypoint used by Gradle to read dashboard/resource JSON and write final artifacts.
- Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt`
  Unit tests for parsing, classification, no-score separation, resource evidence attachment, and output generation.
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGenerator.kt`
  Adds tested `--family` and `--name` filtering for `generateSkiaRendersFor`.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGeneratorFilterTest.kt`
  Unit tests the render-selection parser and GM filtering without invoking WebGPU.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidence.kt`
  Produces texture/sampler create/reuse telemetry evidence through `GPUConcreteResourceProvider`.
- Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidenceTest.kt`
  Proves the helper emits create then reuse telemetry and explicit non-claims.
- Modify `gpu-renderer/build.gradle.kts`
  Adds module-owned `generateGpuPhase6ImageResourceEvidence`.
- Modify root `build.gradle.kts`
  Adds an aggregate `generateGpuPhase6ImageFamilyEvidence` alias that delegates to `:integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence`.
- Generated outputs:
  `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md`,
  `reports/gpu-renderer/phase-6-image-family/evidence.json`,
  `reports/gpu-renderer/phase-6-image-family/classification.csv`,
  `reports/gpu-renderer/phase-6-image-family/resource-evidence.json`.

---

### Task 1: Make `generateSkiaRendersFor` Actually Filter IMAGE Rows

**Files:**
- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGenerator.kt`
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGeneratorFilterTest.kt`

**Interfaces:**
- Consumes: existing `SkiaGm`, `RenderFamily`, `RenderCost`, and `generateSkiaRenders(args: Array<String>)`.
- Produces:
  - `internal data class SkiaRenderGeneratorOptions(val outputDir: File, val family: RenderFamily?, val name: String?, val includeBlocking: Boolean)`
  - `internal fun parseSkiaRenderGeneratorOptions(args: Array<String>): SkiaRenderGeneratorOptions`
  - `internal fun selectSkiaGmsForRender(gms: List<SkiaGm>, options: SkiaRenderGeneratorOptions): List<SkiaGm>`

- [ ] **Step 1: Write the failing filter tests**

Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGeneratorFilterTest.kt`:

```kotlin
package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

class SkiaRenderGeneratorFilterTest {
    @Test
    fun `parser accepts family name and gm name filters`() {
        val options = parseSkiaRenderGeneratorOptions(
            arrayOf("/tmp/renders", "--family", "IMAGE", "--name", "DrawBitmapRect3"),
        )

        assertEquals(File("/tmp/renders"), options.outputDir)
        assertEquals(RenderFamily.IMAGE, options.family)
        assertEquals("DrawBitmapRect3", options.name)
        assertEquals(false, options.includeBlocking)
    }

    @Test
    fun `parser accepts include blocking independently of filters`() {
        val options = parseSkiaRenderGeneratorOptions(
            arrayOf("/tmp/renders", "--include-blocking", "--family", "PATH"),
        )

        assertEquals(RenderFamily.PATH, options.family)
        assertEquals(null, options.name)
        assertEquals(true, options.includeBlocking)
    }

    @Test
    fun `parser rejects unknown family`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseSkiaRenderGeneratorOptions(arrayOf("/tmp/renders", "--family", "NOT_A_FAMILY"))
        }

        assertEquals("Unknown GM family: NOT_A_FAMILY", error.message)
    }

    @Test
    fun `selection filters by family and name before rendering`() {
        val selected = selectSkiaGmsForRender(
            listOf(
                StubGm("DrawBitmapRect3", RenderFamily.IMAGE),
                StubGm("aaclip", RenderFamily.CLIP),
                StubGm("ImageShader", RenderFamily.IMAGE),
            ),
            SkiaRenderGeneratorOptions(
                outputDir = File("/tmp/renders"),
                family = RenderFamily.IMAGE,
                name = "ImageShader",
                includeBlocking = false,
            ),
        )

        assertEquals(listOf("ImageShader"), selected.map { it.name })
    }

    @Test
    fun `selection excludes blocking rows unless explicitly included`() {
        val selected = selectSkiaGmsForRender(
            listOf(
                StubGm("fast-image", RenderFamily.IMAGE, RenderCost.FAST),
                StubGm("blocking-image", RenderFamily.IMAGE, RenderCost.BLOCKING),
            ),
            SkiaRenderGeneratorOptions(
                outputDir = File("/tmp/renders"),
                family = RenderFamily.IMAGE,
                name = null,
                includeBlocking = false,
            ),
        )

        assertEquals(listOf("fast-image"), selected.map { it.name })
    }
}

private class StubGm(
    override val name: String,
    override val renderFamily: RenderFamily,
    override val renderCost: RenderCost = RenderCost.FAST,
) : SkiaGm {
    override val minSimilarity: Double = 99.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
rtk ./gradlew :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaRenderGeneratorFilterTest"
```

Expected: compilation fails with unresolved references to `parseSkiaRenderGeneratorOptions`, `selectSkiaGmsForRender`, and `SkiaRenderGeneratorOptions`.

- [ ] **Step 3: Implement the parser and selection helpers**

Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGenerator.kt` so the generator uses these helpers:

```kotlin
internal data class SkiaRenderGeneratorOptions(
    val outputDir: File,
    val family: RenderFamily?,
    val name: String?,
    val includeBlocking: Boolean,
)

internal fun parseSkiaRenderGeneratorOptions(args: Array<String>): SkiaRenderGeneratorOptions {
    require(args.isNotEmpty()) { "Usage: SkiaRenderGenerator <outputDir> [--family FAMILY] [--name NAME] [--include-blocking]" }
    val outputDir = File(args[0])
    var family: RenderFamily? = null
    var name: String? = null
    var includeBlocking = false
    var index = 1
    while (index < args.size) {
        when (val arg = args[index]) {
            "--family" -> {
                require(index + 1 < args.size) { "--family requires a value" }
                val rawFamily = args[index + 1]
                family = RenderFamily.entries.firstOrNull { it.name == rawFamily.uppercase() }
                    ?: throw IllegalArgumentException("Unknown GM family: $rawFamily")
                index += 2
            }
            "--name" -> {
                require(index + 1 < args.size) { "--name requires a value" }
                name = args[index + 1]
                index += 2
            }
            "--include-blocking" -> {
                includeBlocking = true
                index += 1
            }
            else -> throw IllegalArgumentException("Unknown SkiaRenderGenerator argument: $arg")
        }
    }
    return SkiaRenderGeneratorOptions(outputDir, family, name, includeBlocking)
}

internal fun selectSkiaGmsForRender(
    gms: List<SkiaGm>,
    options: SkiaRenderGeneratorOptions,
): List<SkiaGm> =
    gms.filter { gm ->
        (options.includeBlocking || gm.renderCost != RenderCost.BLOCKING) &&
            (options.family == null || gm.renderFamily == options.family) &&
            (options.name == null || gm.name == options.name)
    }
```

Update `generateSkiaRenders(args)` so it calls `parseSkiaRenderGeneratorOptions(args)` and iterates over `selectSkiaGmsForRender(SkiaGmRegistry.all(), options)`.

- [ ] **Step 4: Run the targeted test**

Run:

```bash
rtk ./gradlew :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaRenderGeneratorFilterTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGenerator.kt integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaRenderGeneratorFilterTest.kt
rtk git commit -m "Wire Skia IMAGE render filtering"
```

---

### Task 2: Add the `:integration-tests:skia-evidence` Library and IMAGE Classifier

**Files:**
- Modify: `settings.gradle.kts`
- Create: `integration-tests/skia-evidence/build.gradle.kts`
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt`
- Create: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt`

**Interfaces:**
- Consumes: dashboard JSON rows with fields `name`, `family`, `similarity`, `minSimilarity`, `isPassing`, `renderFailed`, `noReference`, `sizeMismatch`, and `hasDiff`.
- Produces:
  - `data class GmDashboard(val generatedAt: String?, val rows: List<GmDashboardRow>)`
  - `data class GmDashboardRow(val name: String, val family: String, val similarity: Double?, val minSimilarity: Double?, val isPassing: Boolean?, val noReference: Boolean, val renderFailed: Boolean, val sizeMismatch: Boolean, val hasDiff: Boolean)`
  - `data class Phase6ImageRowEvidence(val name: String, val family: String, val subfamily: String, val classification: String, val similarity: Double?, val minSimilarity: Double?, val isPassing: Boolean?, val fallbackReason: String, val referencePath: String?, val generatedPath: String?, val diffPath: String?, val noScoreCause: String?, val nonClaim: String)`
  - `object Phase6ImageFamilyClassifier`
  - `fun Phase6ImageFamilyClassifier.classify(row: GmDashboardRow): Phase6ImageRowEvidence`
  - `fun Phase6ImageFamilyClassifier.buildEvidence(dashboard: GmDashboard, resourceEvidence: ResourceEvidence? = null): Phase6ImageFamilyEvidence`

- [ ] **Step 1: Add the module include and build file**

Add this line to `settings.gradle.kts` after `include(":integration-tests:skia")`:

```kotlin
include(":integration-tests:skia-evidence")
```

Create `integration-tests/skia-evidence/build.gradle.kts`:

```kotlin
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinxSerialization)

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
```

- [ ] **Step 2: Write the failing classifier tests**

Create `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class Phase6ImageFamilyEvidenceTest {
    @Test
    fun `classifies passed image rect as instrumented until route evidence exists`() {
        val classified = Phase6ImageFamilyClassifier.classify(row("DrawBitmapRect3"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("simple-image-rect", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
        assertContains(classified.nonClaim, "route/cache/batching evidence missing")
    }

    @Test
    fun `classifies codec rows as expected unsupported`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("AnimatedGif", similarity = 37.5, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("animation-gated", classified.subfamily)
        assertEquals("dependency.image.codec.unregistered", classified.fallbackReason)
    }

    @Test
    fun `classifies no score separately from unexpected fail`() {
        val noScore = Phase6ImageFamilyClassifier.classify(
            row("MissingReferenceImage", similarity = null, isPassing = null, noReference = true),
        )
        val fail = Phase6ImageFamilyClassifier.classify(
            row("PlainImageFail", similarity = 40.0, isPassing = false),
        )

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
    }

    @Test
    fun `build evidence filters only image family`() {
        val dashboard = GmDashboard(
            generatedAt = "2026-07-08T21:00:00",
            rows = listOf(
                row("DrawBitmapRect3"),
                row("aaclip", family = "CLIP"),
            ),
        )

        val evidence = Phase6ImageFamilyClassifier.buildEvidence(dashboard)

        assertEquals(1, evidence.summary.totalImageRows)
        assertEquals(1, evidence.summary.classifications["instrumented-existing"])
        assertEquals("DrawBitmapRect3", evidence.rows.single().name)
    }
}

private fun row(
    name: String,
    family: String = "IMAGE",
    similarity: Double? = 100.0,
    isPassing: Boolean? = true,
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
        noReference = noReference,
        renderFailed = renderFailed,
        sizeMismatch = sizeMismatch,
        hasDiff = hasDiff,
    )
```

- [ ] **Step 3: Run the test and verify it fails**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
```

Expected: compilation fails because the evidence model and classifier are not defined.

- [ ] **Step 4: Implement the evidence model and classifier**

Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class GmDashboard(
    val generatedAt: String?,
    val rows: List<GmDashboardRow>,
)

data class GmDashboardRow(
    val name: String,
    val family: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val noReference: Boolean,
    val renderFailed: Boolean,
    val sizeMismatch: Boolean,
    val hasDiff: Boolean,
)

data class ResourceEvidence(
    val rowId: String,
    val dumpLines: List<String>,
    val nonClaims: List<String>,
)

data class Phase6ImageFamilyEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6ImageSummary,
    val resourceEvidence: ResourceEvidence?,
    val nonClaims: List<String>,
    val rows: List<Phase6ImageRowEvidence>,
)

data class Phase6ImageSummary(
    val totalImageRows: Int,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6ImageRowEvidence(
    val name: String,
    val family: String,
    val subfamily: String,
    val classification: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val fallbackReason: String,
    val referencePath: String?,
    val generatedPath: String?,
    val diffPath: String?,
    val noScoreCause: String?,
    val nonClaim: String,
)

object Phase6ImageFamilyClassifier {
    private val unsupportedRules = listOf(
        UnsupportedRule(setOf("animated", "gif", "video", "codec", "encode"), "animation-gated", "dependency.image.codec.unregistered"),
        UnsupportedRule(setOf("yuv"), "yuv-gated", "unsupported.color.yuv_conversion"),
        UnsupportedRule(setOf("mip", "mipmap"), "mipmap-gated", "unsupported.image.mipmap_budget_exceeded"),
        UnsupportedRule(setOf("persp", "perspective"), "perspective-gated", "unsupported.transform.perspective_route_rejected"),
        UnsupportedRule(setOf("filter", "blur", "morphology", "magnifier"), "image-filter-gated", "unsupported.filter.node_unimplemented"),
        UnsupportedRule(setOf("colorspace", "p3", "outofgamut", "working"), "color-management-gated", "unsupported.color.image_profile_conversion"),
        UnsupportedRule(setOf("readpixels", "snap", "surface"), "readpixels-or-snapshot-gated", "unsupported.destination_read.strategy_unaccepted"),
    )

    fun classify(row: GmDashboardRow): Phase6ImageRowEvidence {
        require(row.family == "IMAGE") { "Expected IMAGE row, got ${row.family}" }
        val subfamily = imageSubfamily(row)
        val reason = expectedUnsupportedReason(row)
        val noScore = row.similarity == null || row.noReference || row.renderFailed || row.sizeMismatch

        val classification: String
        val fallback: String
        val nonClaim: String
        val noScoreCause: String?
        when {
            noScore -> {
                classification = "no-score"
                fallback = reason ?: "none"
                noScoreCause = noScoreCause(row)
                nonClaim = "No score is not counted as fail or support; reference/render/size/comparison evidence is incomplete."
            }
            reason != null -> {
                classification = "expected-unsupported"
                fallback = reason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 IMAGE migration scope."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Rendered row remains instrumented until route/cache/batching evidence missing from dashboard is attached."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable refusal."
            }
        }

        return Phase6ImageRowEvidence(
            name = row.name,
            family = "IMAGE",
            subfamily = subfamily,
            classification = classification,
            similarity = row.similarity,
            minSimilarity = row.minSimilarity,
            isPassing = row.isPassing,
            fallbackReason = fallback,
            referencePath = if (row.noReference) null else "images/reference/${row.name}.png",
            generatedPath = if (row.renderFailed) null else "images/generated/${row.name}.png",
            diffPath = if (row.hasDiff) "images/diff/${row.name}.png" else null,
            noScoreCause = noScoreCause,
            nonClaim = nonClaim,
        )
    }

    fun buildEvidence(
        dashboard: GmDashboard,
        resourceEvidence: ResourceEvidence? = null,
    ): Phase6ImageFamilyEvidence {
        val rows = dashboard.rows
            .filter { row -> row.family == "IMAGE" }
            .map(::classify)
        val classifications = rows.groupingBy { row -> row.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { row -> row.subfamily }.eachCount().toSortedMap()
        return Phase6ImageFamilyEvidence(
            schemaVersion = "phase6-image-family-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6ImageSummary(
                totalImageRows = rows.size,
                classifications = classifications,
                subfamilies = subfamilies,
                promotedRows = classifications["promoted-support"] ?: 0,
                unexpectedFails = classifications["unexpected-fail"] ?: 0,
                noScore = classifications["no-score"] ?: 0,
            ),
            resourceEvidence = resourceEvidence,
            nonClaims = listOf(
                "No broad IMAGE support is claimed from classification alone.",
                "No codec, animation, mipmap, YUV, perspective, image-filter, picture-shader, or broad color-management support is added.",
                "CPU-oracle rows do not count as Skia-comparable fidelity.",
            ),
            rows = rows,
        )
    }

    private fun imageSubfamily(row: GmDashboardRow): String {
        val name = row.name.lowercase()
        unsupportedRules.firstOrNull { rule -> rule.tokens.any(name::contains) }?.let { return it.subfamily }
        return when {
            "drawbitmaprect" in name || "bitmaprect" in name || "drawmini" in name -> "simple-image-rect"
            "localmatrix" in name -> "local-matrix-affine"
            "shader" in name || "bitmapshader" in name -> "bitmap-shader-affine"
            "sampling" in name || "nearest" in name || "linear" in name -> "strict-nearest-linear"
            "tile" in name -> "sampler-policy-candidate"
            else -> "texture-cache-candidate"
        }
    }

    private fun expectedUnsupportedReason(row: GmDashboardRow): String? {
        val name = row.name.lowercase()
        return unsupportedRules.firstOrNull { rule -> rule.tokens.any(name::contains) }?.reason
    }

    private fun noScoreCause(row: GmDashboardRow): String =
        when {
            row.noReference -> "reference-missing"
            row.renderFailed -> "generated-render-missing"
            row.sizeMismatch -> "size-mismatch"
            row.similarity == null -> "comparison-unavailable"
            else -> "none"
        }
}

private data class UnsupportedRule(
    val tokens: Set<String>,
    val subfamily: String,
    val reason: String,
)

object GmDashboardJsonReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(path: Path): GmDashboard {
        val root = json.parseToJsonElement(Files.readString(path)).jsonObject
        val rows = root["gms"]?.jsonArray.orEmpty().map { element -> parseRow(element.jsonObject) }
        return GmDashboard(
            generatedAt = root["generatedAt"]?.jsonPrimitive?.contentOrNull,
            rows = rows,
        )
    }

    private fun parseRow(row: JsonObject): GmDashboardRow =
        GmDashboardRow(
            name = row.string("name") ?: error("GM dashboard row missing name"),
            family = row.string("family") ?: error("GM dashboard row missing family"),
            similarity = row.double("similarity"),
            minSimilarity = row.double("minSimilarity"),
            isPassing = row.boolean("isPassing"),
            noReference = row.boolean("noReference") ?: false,
            renderFailed = row.boolean("renderFailed") ?: false,
            sizeMismatch = row.boolean("sizeMismatch") ?: false,
            hasDiff = row.boolean("hasDiff") ?: false,
        )
}

internal fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull
```

- [ ] **Step 5: Run the classifier tests**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add settings.gradle.kts integration-tests/skia-evidence/build.gradle.kts integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt
rtk git commit -m "Add Skia IMAGE evidence module"
```

---

### Task 3: Add Kotlin Evidence Writers and Gradle Task

**Files:**
- Modify: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt`
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt`
- Modify: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt`
- Modify: `integration-tests/skia-evidence/build.gradle.kts`
- Modify: `build.gradle.kts`

**Interfaces:**
- Consumes: `Phase6ImageFamilyClassifier.buildEvidence(dashboard: GmDashboard, resourceEvidence: ResourceEvidence? = null): Phase6ImageFamilyEvidence`.
- Produces:
  - `object Phase6ImageFamilyEvidenceWriter`
  - `fun Phase6ImageFamilyEvidenceWriter.writeOutputs(root: Path, evidence: Phase6ImageFamilyEvidence)`
  - `fun main(args: Array<String>)` in `Phase6ImageFamilyEvidenceCli.kt`
  - Gradle task `:integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence`
  - root alias `generateGpuPhase6ImageFamilyEvidence`

- [ ] **Step 1: Add failing writer tests**

Append these tests to `Phase6ImageFamilyEvidenceTest.kt`:

```kotlin
    @Test
    fun `writer creates json markdown and csv outputs`() {
        val evidence = Phase6ImageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-08T21:00:00",
                rows = listOf(row("DrawBitmapRect3")),
            ),
        )

        val root = kotlin.io.path.createTempDirectory("phase6-image-evidence")
        Phase6ImageFamilyEvidenceWriter.writeOutputs(root, evidence)

        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-image-family/evidence.json")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-image-family/classification.csv")

        assertEquals(true, java.nio.file.Files.isRegularFile(evidencePath))
        assertEquals(true, java.nio.file.Files.isRegularFile(markdownPath))
        assertEquals(true, java.nio.file.Files.isRegularFile(csvPath))
        assertContains(java.nio.file.Files.readString(evidencePath), "\"schemaVersion\": \"phase6-image-family-v1\"")
        assertContains(java.nio.file.Files.readString(markdownPath), "No broad IMAGE support is claimed")
        assertContains(java.nio.file.Files.readString(csvPath), "DrawBitmapRect3,simple-image-rect,instrumented-existing")
    }
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
```

Expected: compilation fails because `Phase6ImageFamilyEvidenceWriter` is not defined.

- [ ] **Step 3: Implement JSON, CSV, and Markdown writers**

Append these declarations to `Phase6ImageFamilyEvidence.kt`:

```kotlin
object Phase6ImageFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6ImageFamilyEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-image-family/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-image-family/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md")
        Files.createDirectories(evidencePath.parent)
        Files.createDirectories(markdownPath.parent)
        Files.writeString(evidencePath, prettyJson.encodeToString(JsonObject.serializer(), evidence.toJsonObject()) + "\n")
        Files.writeString(csvPath, evidence.toCsv())
        Files.writeString(markdownPath, evidence.toMarkdown())
    }
}

fun Phase6ImageFamilyEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("schemaVersion", schemaVersion)
        put("generatedBy", generatedBy)
        put("dashboardSource", dashboardSource)
        if (sourceGeneratedAt == null) put("sourceGeneratedAt", JsonNull) else put("sourceGeneratedAt", sourceGeneratedAt)
        put("summary", summary.toJsonObject())
        if (resourceEvidence == null) put("resourceEvidence", JsonNull) else put("resourceEvidence", resourceEvidence.toJsonObject())
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
        put("rows", buildJsonArray { rows.forEach { add(it.toJsonObject()) } })
    }

private fun Phase6ImageSummary.toJsonObject(): JsonObject =
    buildJsonObject {
        put("totalImageRows", totalImageRows)
        put("classifications", classifications.toCountJsonObject())
        put("subfamilies", subfamilies.toCountJsonObject())
        put("promotedRows", promotedRows)
        put("unexpectedFails", unexpectedFails)
        put("noScore", noScore)
    }

private fun ResourceEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("dumpLines", buildJsonArray { dumpLines.forEach { add(it) } })
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
    }

private fun Phase6ImageRowEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("name", name)
        put("family", family)
        put("subfamily", subfamily)
        put("classification", classification)
        putNullableDouble("similarity", similarity)
        putNullableDouble("minSimilarity", minSimilarity)
        if (isPassing == null) put("isPassing", JsonNull) else put("isPassing", isPassing)
        put("fallbackReason", fallbackReason)
        putNullableString("referencePath", referencePath)
        putNullableString("generatedPath", generatedPath)
        putNullableString("diffPath", diffPath)
        putNullableString("noScoreCause", noScoreCause)
        put("nonClaim", nonClaim)
    }

private fun Map<String, Int>.toCountJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObjectBuilder.putNullableString(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableDouble(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun Phase6ImageFamilyEvidence.toCsv(): String =
    buildString {
        appendLine("name,subfamily,classification,similarity,minSimilarity,fallbackReason,noScoreCause")
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.name,
                    row.subfamily,
                    row.classification,
                    row.similarity?.toString().orEmpty(),
                    row.minSimilarity?.toString().orEmpty(),
                    row.fallbackReason,
                    row.noScoreCause.orEmpty(),
                ).joinToString(",") { it.csvCell() },
            )
        }
    }

fun Phase6ImageFamilyEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 IMAGE Family Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total IMAGE rows: ${summary.totalImageRows}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        resourceEvidence?.let { resource ->
            appendLine()
            appendLine("## Resource And Cache Evidence")
            appendLine()
            appendLine("- Row id: `${resource.rowId}`")
            resource.dumpLines.forEach { appendLine("- `$it`") }
            resource.nonClaims.forEach { appendLine("- `$it`") }
        }
        appendLine()
        appendLine("## Rows")
        appendLine()
        appendLine("| Row | Subfamily | Classification | Similarity | Fallback |")
        appendLine("|---|---|---|---:|---|")
        rows.forEach { row ->
            val similarity = row.similarity?.let { "%.2f".format(java.util.Locale.US, it) } ?: "n/a"
            appendLine("| `${row.name}` | `${row.subfamily}` | `${row.classification}` | $similarity | `${row.fallbackReason}` |")
        }
    }

private fun String.csvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
```

Add missing imports at the top of `Phase6ImageFamilyEvidence.kt`:

```kotlin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
```

- [ ] **Step 4: Add the CLI entrypoint**

Create `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val dashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = root.resolve(dashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = Phase6ImageFamilyClassifier.buildEvidence(dashboard)
    Phase6ImageFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-image-family/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-image-family/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md")}")
}
```

- [ ] **Step 5: Register Gradle tasks**

Append this task to `integration-tests/skia-evidence/build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("generateGpuPhase6ImageFamilyEvidence") {
    group = "verification"
    description = "Generates the GPU Phase 6 IMAGE family classification and evidence report."
    dependsOn(":integration-tests:skia:generateSkiaDashboard", tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceCliKt")
    args(rootDir.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/evidence.json"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/classification.csv"))
    outputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md"))
}
```

Append this root alias to `build.gradle.kts` near other verification tasks:

```kotlin
tasks.register("generateGpuPhase6ImageFamilyEvidence") {
    group = "verification"
    description = "Delegates to :integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence."
    dependsOn(":integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence")
}
```

- [ ] **Step 6: Run the writer tests and task listing**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
rtk ./gradlew tasks --all | rg "generateGpuPhase6ImageFamilyEvidence"
```

Expected: tests pass and both root/module task names are visible.

- [ ] **Step 7: Commit**

```bash
rtk git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt integration-tests/skia-evidence/build.gradle.kts build.gradle.kts
rtk git commit -m "Add phase 6 image evidence writer"
```

---

### Task 4: Add Texture/Sampler Cache Evidence for Simple IMAGE Rows

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidence.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidenceTest.kt`
- Modify: `gpu-renderer/build.gradle.kts`

**Interfaces:**
- Consumes: `GPUConcreteResourceProvider.materializeTextureSamplerBinding`.
- Produces:
  - `data class ImageFamilyResourceEvidence(val rowId: String, val dumpLines: List<String>, val nonClaims: List<String>)`
  - `fun buildRepeatedImageTextureSamplerEvidence(provider: GPUConcreteResourceProvider = GPUConcreteResourceProvider()): ImageFamilyResourceEvidence`
  - `fun main(args: Array<String>)` writing `resource-evidence.json`
  - Gradle task `:gpu-renderer:generateGpuPhase6ImageResourceEvidence`

- [ ] **Step 1: Write the failing resource evidence test**

Create `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidenceTest.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ImageFamilyResourceEvidenceTest {
    @Test
    fun `repeated image texture sampler evidence records create then reuse without broad support claim`() {
        val evidence = buildRepeatedImageTextureSamplerEvidence()

        assertEquals("phase6-image-repeated-texture-sampler", evidence.rowId)
        val lines = evidence.dumpLines.joinToString("\n")
        assertContains(lines, "resource-provider.cache lane=texture-sampler result=create")
        assertContains(lines, "resource-provider.cache lane=texture-sampler result=reuse")
        assertContains(lines, "subject=sampled-texture.phase6-checker")
        assertContains(evidence.nonClaims, "no-broad-image-support")
        assertContains(evidence.nonClaims, "no-codec-support")
        assertFalse(lines.contains("handle:") || lines.contains("0x") || lines.contains("@"))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceTest"
```

Expected: compilation fails because `buildRepeatedImageTextureSamplerEvidence` and `ImageFamilyResourceEvidence` do not exist.

- [ ] **Step 3: Implement the resource evidence helper**

Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidence.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.images

import java.io.File
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUSampledTextureBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureAllocationPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureOwnershipPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureViewDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUUseToken

data class ImageFamilyResourceEvidence(
    val rowId: String,
    val dumpLines: List<String>,
    val nonClaims: List<String>,
)

fun buildRepeatedImageTextureSamplerEvidence(
    provider: GPUConcreteResourceProvider = GPUConcreteResourceProvider(),
): ImageFamilyResourceEvidence {
    val context = GPUTargetPreparationContext(
        targetId = "phase6-image-target",
        frameId = "phase6-image-frame",
        deviceGeneration = 17,
        budgetClass = "phase6-image-simple",
    )
    val request = phase6TextureSamplerRequest()
    provider.materializeTextureSamplerBinding(request, context)
    provider.materializeTextureSamplerBinding(request, context)
    return ImageFamilyResourceEvidence(
        rowId = "phase6-image-repeated-texture-sampler",
        dumpLines = provider.telemetry.dumpLines(),
        nonClaims = listOf(
            "no-broad-image-support",
            "no-codec-support",
            "no-animation-support",
            "no-mipmap-support",
            "no-yuv-support",
            "no-image-filter-support",
        ),
    )
}

private fun phase6TextureSamplerRequest(): GPUTextureSamplerMaterializationRequest {
    val textureDescriptor = GPUTextureDescriptor(
        width = 64,
        height = 64,
        format = "rgba8unorm",
        usageLabels = setOf("copy_dst", "texture_binding"),
        sampleCount = 1,
    )
    val viewDescriptor = GPUTextureViewDescriptor(
        textureDescriptorHash = "texture:phase6-checker",
        viewDimension = "2d",
        mipRange = 0..0,
        arrayLayerRange = 0..0,
    )
    val samplerDescriptor = GPUSamplerDescriptor(
        addressModeU = "clamp-to-edge",
        addressModeV = "clamp-to-edge",
        magFilter = "nearest",
        minFilter = "nearest",
        mipmapFilter = "none",
    )
    val ownership = GPUTextureOwnershipPlan(
        ownerLabel = "phase6-image-cache",
        lifetimeClass = "recording-local",
        releasePolicy = "submission-complete",
        canAliasScratch = false,
    )
    val binding = GPUSampledTextureBinding(
        bindingLabel = "sampled-texture.phase6-checker",
        view = viewDescriptor,
        sampler = samplerDescriptor,
        useToken = GPUUseToken(17L),
    )
    return GPUTextureSamplerMaterializationRequest(
        targetId = "phase6-image-target",
        packetId = "packet-phase6-image-1",
        taskIds = listOf("task-phase6-image-texture-sampler"),
        resourcePlanLabels = listOf("texture-sampler:phase6-checker"),
        allocation = GPUTextureAllocationPlan.CreateTexture(
            descriptor = textureDescriptor,
            ownership = ownership,
        ),
        ownership = ownership,
        textureDescriptor = textureDescriptor,
        viewDescriptor = viewDescriptor,
        samplerDescriptor = samplerDescriptor,
        binding = binding,
        bindingLayoutHash = "layout-image-sampler-v1",
        deviceGeneration = 17,
        expectedResourceGeneration = 3,
        actualResourceGeneration = 3,
        requiredTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        availableTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        requiredMipLevels = 1,
        uploadBytes = 16384,
        uploadBudgetBytes = 65536,
        uploadCapabilityAvailable = true,
    )
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: ImageFamilyResourceEvidenceKt <output-json>" }
    val evidence = buildRepeatedImageTextureSamplerEvidence()
    val output = File(args[0])
    output.parentFile.mkdirs()
    output.writeText(evidence.toJson() + "\n")
}

private fun ImageFamilyResourceEvidence.toJson(): String =
    buildString {
        appendLine("{")
        appendLine("  \"rowId\": \"${rowId.escapeJson()}\",")
        appendLine("  \"dumpLines\": [")
        dumpLines.forEachIndexed { index, line ->
            val comma = if (index == dumpLines.lastIndex) "" else ","
            appendLine("    \"${line.escapeJson()}\"$comma")
        }
        appendLine("  ],")
        appendLine("  \"nonClaims\": [")
        nonClaims.forEachIndexed { index, line ->
            val comma = if (index == nonClaims.lastIndex) "" else ","
            appendLine("    \"${line.escapeJson()}\"$comma")
        }
        appendLine("  ]")
        append("}")
    }

private fun String.escapeJson(): String =
    buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
```

- [ ] **Step 4: Register the resource evidence task**

Append this task to `gpu-renderer/build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("generateGpuPhase6ImageResourceEvidence") {
    group = "verification"
    description = "Writes Phase 6 IMAGE texture/sampler resource evidence."
    dependsOn("testClasses")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceKt")
    val outputFile = rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/resource-evidence.json")
    args(outputFile.asFile.absolutePath)
    outputs.file(outputFile)
}
```

- [ ] **Step 5: Run targeted validations**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceTest"
rtk ./gradlew tasks --all | rg "generateGpuPhase6ImageResourceEvidence"
```

Expected: the test passes and the task name is visible.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidence.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageFamilyResourceEvidenceTest.kt gpu-renderer/build.gradle.kts
rtk git commit -m "Add phase 6 image resource evidence"
```

---

### Task 5: Attach Resource Evidence to the Kotlin Evidence Module

**Files:**
- Modify: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt`
- Modify: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt`
- Modify: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt`
- Modify: `integration-tests/skia-evidence/build.gradle.kts`

**Interfaces:**
- Consumes: `reports/gpu-renderer/phase-6-image-family/resource-evidence.json`.
- Produces: `ResourceEvidenceReader.readIfPresent(root: Path): ResourceEvidence?` and report section `## Resource And Cache Evidence`.

- [ ] **Step 1: Add failing resource attachment tests**

Append these tests to `Phase6ImageFamilyEvidenceTest.kt`:

```kotlin
    @Test
    fun `resource evidence is attached when present`() {
        val evidence = Phase6ImageFamilyClassifier.buildEvidence(
            dashboard = GmDashboard("2026-07-08T21:00:00", listOf(row("DrawBitmapRect3"))),
            resourceEvidence = ResourceEvidence(
                rowId = "phase6-image-repeated-texture-sampler",
                dumpLines = listOf("resource-provider.cache lane=texture-sampler result=create key=k subject=s"),
                nonClaims = listOf("no-broad-image-support"),
            ),
        )

        assertEquals("phase6-image-repeated-texture-sampler", evidence.resourceEvidence?.rowId)
        assertContains(evidence.toMarkdown(), "## Resource And Cache Evidence")
        assertContains(evidence.toMarkdown(), "resource-provider.cache lane=texture-sampler result=create")
    }

    @Test
    fun `resource evidence reader loads optional json file`() {
        val root = kotlin.io.path.createTempDirectory("phase6-resource-evidence")
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-image-family/resource-evidence.json")
        java.nio.file.Files.createDirectories(evidencePath.parent)
        java.nio.file.Files.writeString(
            evidencePath,
            """
            {
              "rowId": "phase6-image-repeated-texture-sampler",
              "dumpLines": ["resource-provider.cache lane=texture-sampler result=create key=k subject=s"],
              "nonClaims": ["no-broad-image-support"]
            }
            """.trimIndent(),
        )

        val loaded = ResourceEvidenceReader.readIfPresent(root)

        assertEquals("phase6-image-repeated-texture-sampler", loaded?.rowId)
        assertEquals(listOf("no-broad-image-support"), loaded?.nonClaims)
    }
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
```

Expected: compilation fails because `ResourceEvidenceReader` is not defined.

- [ ] **Step 3: Implement the optional resource evidence reader**

Append this object to `Phase6ImageFamilyEvidence.kt`:

```kotlin
object ResourceEvidenceReader {
    private val json = Json { ignoreUnknownKeys = true }
    private val resourceEvidencePath = Path.of("reports/gpu-renderer/phase-6-image-family/resource-evidence.json")

    fun readIfPresent(root: Path): ResourceEvidence? {
        val path = root.resolve(resourceEvidencePath)
        if (!Files.isRegularFile(path)) return null
        val obj = json.parseToJsonElement(Files.readString(path)).jsonObject
        return ResourceEvidence(
            rowId = obj.string("rowId") ?: error("resource evidence missing rowId"),
            dumpLines = obj.stringArray("dumpLines"),
            nonClaims = obj.stringArray("nonClaims"),
        )
    }
}

internal fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.stringArray(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element -> element.jsonPrimitive.contentOrNull }
```

The `JsonObject.string`, `double`, and `boolean` helpers already exist from Task 2. Keep them as shared top-level helpers so both readers use the same functions.

- [ ] **Step 4: Update the CLI and task dependency**

Change `Phase6ImageFamilyEvidenceCli.kt` to pass optional resource evidence:

```kotlin
    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val resourceEvidence = ResourceEvidenceReader.readIfPresent(root)
    val evidence = Phase6ImageFamilyClassifier.buildEvidence(
        dashboard = dashboard,
        resourceEvidence = resourceEvidence,
    )
```

Change the `generateGpuPhase6ImageFamilyEvidence` task in `integration-tests/skia-evidence/build.gradle.kts`:

```kotlin
dependsOn(
    ":integration-tests:skia:generateSkiaDashboard",
    ":gpu-renderer:generateGpuPhase6ImageResourceEvidence",
    tasks.named("classes"),
)
inputs.file(rootProject.layout.projectDirectory.file("reports/gpu-renderer/phase-6-image-family/resource-evidence.json"))
```

- [ ] **Step 5: Run targeted validations**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
rtk ./gradlew tasks --all | rg "generateGpuPhase6ImageResourceEvidence|generateGpuPhase6ImageFamilyEvidence"
```

Expected: tests pass and both task names are visible.

- [ ] **Step 6: Commit**

```bash
rtk git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidence.kt integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceCli.kt integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/Phase6ImageFamilyEvidenceTest.kt integration-tests/skia-evidence/build.gradle.kts
rtk git commit -m "Attach phase 6 image resource evidence"
```

---

### Task 6: Generate Dashboard Evidence and Final Reports

**Files:**
- Generate/modify: `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md`
- Generate/modify: `reports/gpu-renderer/phase-6-image-family/evidence.json`
- Generate/modify: `reports/gpu-renderer/phase-6-image-family/classification.csv`
- Generate/modify: `reports/gpu-renderer/phase-6-image-family/resource-evidence.json`

**Interfaces:**
- Consumes: all tasks above.
- Produces: checked-in Phase 6 IMAGE evidence artifacts.

- [ ] **Step 1: Run the full evidence task**

Run:

```bash
rtk ./gradlew generateGpuPhase6ImageFamilyEvidence
```

Expected: PASS. Output mentions `evidence.json`, `classification.csv`, and `2026-07-08-gpu-phase-6-image-family.md`.

- [ ] **Step 2: Inspect key report facts with Gradle-owned tests**

Run:

```bash
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceTest"
rtk ./gradlew :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaRenderGeneratorFilterTest"
```

Expected: all commands pass.

- [ ] **Step 3: Check generated artifacts are present**

Run:

```bash
rtk proxy test -s reports/gpu-renderer/phase-6-image-family/evidence.json
rtk proxy test -s reports/gpu-renderer/phase-6-image-family/classification.csv
rtk proxy test -s reports/gpu-renderer/phase-6-image-family/resource-evidence.json
rtk proxy test -s reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md
```

Expected: every command exits 0.

- [ ] **Step 4: Run whitespace and status checks**

Run:

```bash
rtk git diff --check
rtk git status --short
```

Expected: `git diff --check` exits 0. `git status --short` lists only intentional Phase 6 IMAGE files.

- [ ] **Step 5: Commit final evidence artifacts**

```bash
rtk git add reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md reports/gpu-renderer/phase-6-image-family/evidence.json reports/gpu-renderer/phase-6-image-family/classification.csv reports/gpu-renderer/phase-6-image-family/resource-evidence.json
rtk git commit -m "Generate phase 6 image family evidence"
```

---

### Task 7: Final Smoke and Closeout

**Files:**
- Modify if needed: `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md`

**Interfaces:**
- Consumes: committed implementation and evidence.
- Produces: final validation status ready for review.

- [ ] **Step 1: Run final targeted validation bundle**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceTest"
rtk ./gradlew :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaRenderGeneratorFilterTest"
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
rtk ./gradlew generateGpuPhase6ImageFamilyEvidence
rtk git diff --check
```

Expected: all commands pass.

- [ ] **Step 2: Record final validation note**

Append this section to `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md`:

````markdown
## Final Validation

```bash
rtk ./gradlew :gpu-renderer:test --tests "org.graphiks.kanvas.gpu.renderer.images.ImageFamilyResourceEvidenceTest"
rtk ./gradlew :integration-tests:skia:test --tests "org.graphiks.kanvas.skia.SkiaRenderGeneratorFilterTest"
rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"
rtk ./gradlew generateGpuPhase6ImageFamilyEvidence
rtk git diff --check
```

All Phase 6 IMAGE targeted validations passed. The wide `IMAGE` inventory is classified by the `:integration-tests:skia-evidence` Kotlin module, no-score rows remain separate from true fails, and the only renderer migration claim is the repeated texture/sampler provider create/reuse evidence.
````

- [ ] **Step 3: Commit closeout note if it changed**

Run:

```bash
rtk git status --short
```

Commit the report note:

```bash
rtk git add reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md
rtk git commit -m "Close phase 6 image family validation"
```

Expected: the closeout commit succeeds and `rtk git status --short` is clean.
