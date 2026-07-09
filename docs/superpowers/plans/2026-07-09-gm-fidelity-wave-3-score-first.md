# GM Fidelity Wave 3 Score-First Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first score-first GM fidelity wave after PR #2027 by ranking cross-family candidates, then implementing the first high-impact image/sampling slice with tests, render evidence, and guardrails.

**Architecture:** Reuse the existing Skia dashboard evidence reader for candidate selection, then make renderer changes only behind focused tests. The first implementation slice is Work Group A from the spec: bitmap/image shader sampling and tile-mode behavior. Later groups stay planned but are not implemented until Group A either produces a measured gain or a precise stop-rule diagnostic.

**Tech Stack:** Kotlin/JVM, Gradle, `kotlin.test`, `integration-tests/skia-evidence`, `gpu-renderer`, Skia GM dashboard JSON.

## Global Constraints

- Do not modify `integration-tests/skia/src/test/resources/reference/**`.
- Do not lower `minSimilarity` thresholds to claim progress.
- Do not hide `noReference`, `renderFailed`, `sizeMismatch`, or unsupported rows.
- Do not port Ganesh or Graphite.
- Do not rebuild or embed a dynamic SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `SkRuntimeEffect` as a compatibility facade backed only by registered Kotlin/WGSL implementations.
- Do not introduce font/codec/dependency substitutes to clear historical rows.
- Do not mix unrelated renderer refactors into this wave.
- Branch is stacked on `codex/master-after-path-gm` / PR #2027.

---

## File Structure

- `docs/superpowers/specs/2026-07-09-gm-fidelity-wave-3-score-first-design.md`
  - Existing design spec. Do not edit unless implementation discovers a scope contradiction.
- `docs/superpowers/plans/2026-07-09-gm-fidelity-wave-3-score-first.md`
  - This plan.
- `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3.kt`
  - New score-first classifier and markdown report builder. It reads `GmDashboard`, ranks candidates by unmatched pixels, and assigns wave groups A-D.
- `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Cli.kt`
  - New CLI entry point that reads `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json` and writes report artifacts under `reports/gpu-renderer/gm-fidelity-wave-3-score-first/`.
- `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Test.kt`
  - Tests for ranking, group assignment, guardrail text, and report stability.
- `integration-tests/skia-evidence/build.gradle.kts`
  - Add a `generateGmFidelityWave3ScoreFirstEvidence` `JavaExec` task.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLowering.kt`
  - First Group A behavior boundary: bitmap shader tile modes and material key/evidence.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLoweringTest.kt`
  - Focused tests for supported/refused tile modes and material key uniqueness.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/BitmapShaderSnippet.kt`
  - Only touch if tests prove shader source must expose repeat/mirror/decal selection. Keep WGSL deterministic and parser-friendly.
- `integration-tests/skia/src/test/resources/generated-renders/image/*.png`
  - Generated renders after a measured Group A change.
- `integration-tests/skia/test-similarity-scores.properties`
  - Updated only after `:integration-tests:skia:test` justifies score persistence.

---

### Task 1: Score-First Candidate Evidence

**Files:**
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3.kt`
- Create: `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Cli.kt`
- Create: `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Test.kt`
- Modify: `integration-tests/skia-evidence/build.gradle.kts`

**Interfaces:**
- Consumes: `GmDashboard`, `GmDashboardRow`, and `GmDashboardJsonReader.read(path: Path)` from `GmDashboardEvidence.kt`.
- Produces: `ScoreFirstWave3Evidence`, `ScoreFirstWave3Candidate`, `ScoreFirstWave3Classifier.buildEvidence(dashboard: GmDashboard)`, and `ScoreFirstWave3ReportWriter.write(root: Path, evidence: ScoreFirstWave3Evidence): ScoreFirstWave3Output`.

- [ ] **Step 1: Write the failing classifier/report tests**

Add `integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Test.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreFirstGmFidelityWave3Test {
    @Test
    fun `ranks candidates by unmatched pixels descending`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T00:00:00Z",
                rows = listOf(
                    row("pictureshader", "IMAGE", matchingPixels = 626_000, totalPixels = 2_030_000),
                    row("tablecolorfilter", "COMPOSITE", matchingPixels = 451_704, totalPixels = 1_155_000),
                    row("dashing5_aa", "PATH", matchingPixels = 0, totalPixels = 80_000),
                ),
            ),
        )

        assertEquals(listOf("pictureshader", "tablecolorfilter", "dashing5_aa"), evidence.candidates.map { it.name })
        assertEquals(1_404_000, evidence.candidates.first().unmatchedPixels)
    }

    @Test
    fun `assigns candidates to wave groups`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = null,
                rows = listOf(
                    row("tilemode_decal", "IMAGE"),
                    row("xfermodes", "COMPOSITE"),
                    row("complexclip4_aa", "CLIP"),
                    row("rtif_unsharp", "RUNTIME_EFFECT", isPassing = false),
                ),
            ),
        )

        assertEquals(listOf("A", "B", "C", "D"), evidence.candidates.map { it.groupId })
    }

    @Test
    fun `report includes guardrails and first implementation slice`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = "now",
                rows = listOf(row("imageshader_tinyscale", "IMAGE", matchingPixels = 0, totalPixels = 1_000_000)),
            ),
        )

        val markdown = ScoreFirstWave3Markdown.render(evidence)

        assertContains(markdown, "Do not modify `integration-tests/skia/src/test/resources/reference/**`")
        assertContains(markdown, "First slice: Work Group A")
        assertContains(markdown, "imageshader_tinyscale")
        assertTrue(markdown.lines().any { it.contains("unmatchedPixels") })
    }

    private fun row(
        name: String,
        family: String,
        similarity: Double? = 50.0,
        minSimilarity: Double? = 0.0,
        isPassing: Boolean? = true,
        matchingPixels: Long? = 50,
        totalPixels: Long? = 100,
    ): GmDashboardRow =
        GmDashboardRow(
            name = name,
            family = family,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = 10,
            height = 10,
            maxDiff = null,
            meanDiff = null,
            matchingPixels = matchingPixels,
            totalPixels = totalPixels,
            noReference = false,
            renderFailed = false,
            sizeMismatch = false,
            hasDiff = true,
        )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :integration-tests:skia-evidence:test --tests org.graphiks.kanvas.skia.evidence.ScoreFirstGmFidelityWave3Test
```

Expected: compilation fails because `ScoreFirstWave3Classifier`, `ScoreFirstWave3Markdown`, and related types do not exist.

- [ ] **Step 3: Add the score-first evidence model and classifier**

Create `ScoreFirstGmFidelityWave3.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

data class ScoreFirstWave3Evidence(
    val generatedAt: String?,
    val candidates: List<ScoreFirstWave3Candidate>,
    val groupSummaries: List<ScoreFirstWave3GroupSummary>,
)

data class ScoreFirstWave3Candidate(
    val name: String,
    val family: String,
    val groupId: String,
    val groupName: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val matchingPixels: Long,
    val totalPixels: Long,
    val unmatchedPixels: Long,
    val status: String,
)

data class ScoreFirstWave3GroupSummary(
    val groupId: String,
    val groupName: String,
    val candidateCount: Int,
    val unmatchedPixels: Long,
)

data class ScoreFirstWave3Output(
    val markdownPath: Path,
    val tsvPath: Path,
)

object ScoreFirstWave3Classifier {
    fun buildEvidence(dashboard: GmDashboard): ScoreFirstWave3Evidence {
        val candidates = dashboard.rows
            .filterNot { it.noReference || it.renderFailed || it.sizeMismatch }
            .mapNotNull { row -> row.toCandidateOrNull() }
            .sortedWith(compareByDescending<ScoreFirstWave3Candidate> { it.unmatchedPixels }.thenBy { it.name })

        val summaries = candidates
            .groupBy { it.groupId to it.groupName }
            .map { (key, rows) ->
                ScoreFirstWave3GroupSummary(
                    groupId = key.first,
                    groupName = key.second,
                    candidateCount = rows.size,
                    unmatchedPixels = rows.sumOf { it.unmatchedPixels },
                )
            }
            .sortedBy { it.groupId }

        return ScoreFirstWave3Evidence(
            generatedAt = dashboard.generatedAt,
            candidates = candidates,
            groupSummaries = summaries,
        )
    }

    private fun GmDashboardRow.toCandidateOrNull(): ScoreFirstWave3Candidate? {
        val total = totalPixels ?: return null
        val matched = matchingPixels ?: return null
        val unmatched = (total - matched).coerceAtLeast(0)
        if (total <= 0 || unmatched <= 0) return null
        val group = classifyGroup()
        return ScoreFirstWave3Candidate(
            name = name,
            family = family,
            groupId = group.id,
            groupName = group.name,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            matchingPixels = matched,
            totalPixels = total,
            unmatchedPixels = unmatched,
            status = when {
                isPassing == false -> "fail"
                isPassing == true -> "pass"
                else -> "no-score"
            },
        )
    }

    private fun GmDashboardRow.classifyGroup(): Wave3Group {
        val lower = name.lowercase()
        return when {
            family == "IMAGE" ||
                lower.contains("pictureshader") ||
                lower.contains("imageshader") ||
                lower.contains("tilemode") ||
                lower.contains("bitmap") ||
                lower.contains("coordclamp") -> Wave3Group.ImageSampling
            family == "COMPOSITE" ||
                lower.contains("xfer") ||
                lower.contains("colorfilter") ||
                lower.contains("blend") ||
                lower.contains("transparency") -> Wave3Group.CompositeBlend
            family == "CLIP" || family == "PATH" -> Wave3Group.ClipPathResiduals
            family == "RUNTIME_EFFECT" -> Wave3Group.RuntimeEffect
            else -> Wave3Group.Later
        }
    }
}

private sealed class Wave3Group(val id: String, val name: String) {
    data object ImageSampling : Wave3Group("A", "Image, bitmap, and shader sampling")
    data object CompositeBlend : Wave3Group("B", "Composite, color filters, and blend")
    data object ClipPathResiduals : Wave3Group("C", "Clip and path residuals")
    data object RuntimeEffect : Wave3Group("D", "Runtime effect cleanup")
    data object Later : Wave3Group("Z", "Later score-first backlog")
}

object ScoreFirstWave3Markdown {
    fun render(evidence: ScoreFirstWave3Evidence): String = buildString {
        appendLine("# GM Fidelity Wave 3 Score-First Evidence")
        appendLine()
        appendLine("Generated at: `${evidence.generatedAt ?: "unknown"}`")
        appendLine()
        appendLine("## Guardrails")
        appendLine()
        appendLine("- Do not modify `integration-tests/skia/src/test/resources/reference/**`.")
        appendLine("- Do not lower `minSimilarity` thresholds.")
        appendLine("- Keep unsupported rows visible.")
        appendLine("- First slice: Work Group A.")
        appendLine()
        appendLine("## Group Summary")
        appendLine()
        appendLine("| Group | Name | Candidates | unmatchedPixels |")
        appendLine("|---|---|---:|---:|")
        evidence.groupSummaries.forEach { group ->
            appendLine("| ${group.groupId} | ${group.groupName} | ${group.candidateCount} | ${group.unmatchedPixels} |")
        }
        appendLine()
        appendLine("## Top Candidates")
        appendLine()
        appendLine("| Group | Family | GM | Similarity | Threshold | Status | unmatchedPixels |")
        appendLine("|---|---|---|---:|---:|---|---:|")
        evidence.candidates.take(80).forEach { row ->
            appendLine(
                "| ${row.groupId} | ${row.family} | ${row.name} | ${row.similarity ?: ""} | " +
                    "${row.minSimilarity ?: ""} | ${row.status} | ${row.unmatchedPixels} |",
            )
        }
    }
}

object ScoreFirstWave3ReportWriter {
    fun write(root: Path, evidence: ScoreFirstWave3Evidence): ScoreFirstWave3Output {
        val reportDir = root.resolve("reports/gpu-renderer/gm-fidelity-wave-3-score-first")
        reportDir.createDirectories()
        val markdownPath = reportDir.resolve("evidence.md")
        val tsvPath = reportDir.resolve("candidates.tsv")

        Files.writeString(markdownPath, ScoreFirstWave3Markdown.render(evidence))
        Files.writeString(
            tsvPath,
            buildString {
                appendLine("group\tfamily\tname\tsimilarity\tthreshold\tstatus\tmatchingPixels\ttotalPixels\tunmatchedPixels")
                evidence.candidates.forEach { row ->
                    appendLine(
                        "${row.groupId}\t${row.family}\t${row.name}\t${row.similarity ?: ""}\t" +
                            "${row.minSimilarity ?: ""}\t${row.status}\t${row.matchingPixels}\t${row.totalPixels}\t${row.unmatchedPixels}",
                    )
                }
            },
        )
        return ScoreFirstWave3Output(markdownPath = markdownPath, tsvPath = tsvPath)
    }
}
```

- [ ] **Step 4: Add the CLI**

Create `ScoreFirstGmFidelityWave3Cli.kt`:

```kotlin
package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path

private val dashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = Path.of(args.firstOrNull() ?: ".").toAbsolutePath().normalize()
    val dashboardPath = root.resolve(dashboardJson)
    require(dashboardPath.toFile().isFile) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = ScoreFirstWave3Classifier.buildEvidence(dashboard)
    val output = ScoreFirstWave3ReportWriter.write(root, evidence)

    println("Wrote ${output.markdownPath}")
    println("Wrote ${output.tsvPath}")
}
```

- [ ] **Step 5: Register the Gradle task**

Modify `integration-tests/skia-evidence/build.gradle.kts` by adding a `JavaExec` task near existing evidence tasks:

```kotlin
tasks.register<JavaExec>("generateGmFidelityWave3ScoreFirstEvidence") {
    group = "verification"
    description = "Generates score-first GM Fidelity Wave 3 candidate evidence from the Skia dashboard."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.skia.evidence.ScoreFirstGmFidelityWave3CliKt")
    args(rootProject.layout.projectDirectory.asFile.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json"))
    outputs.dir(rootProject.layout.projectDirectory.dir("reports/gpu-renderer/gm-fidelity-wave-3-score-first"))
}
```

- [ ] **Step 6: Run tests and generate the report**

Run:

```bash
./gradlew :integration-tests:skia-evidence:test --tests org.graphiks.kanvas.skia.evidence.ScoreFirstGmFidelityWave3Test
./gradlew :integration-tests:skia-evidence:generateGmFidelityWave3ScoreFirstEvidence
```

Expected: tests pass; report files are written under `reports/gpu-renderer/gm-fidelity-wave-3-score-first/`.

- [ ] **Step 7: Commit**

```bash
git add integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3.kt \
  integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Cli.kt \
  integration-tests/skia-evidence/src/test/kotlin/org/graphiks/kanvas/skia/evidence/ScoreFirstGmFidelityWave3Test.kt \
  integration-tests/skia-evidence/build.gradle.kts \
  reports/gpu-renderer/gm-fidelity-wave-3-score-first
git commit -m "Add score-first GM fidelity wave evidence"
```

---

### Task 2: Group A Bitmap Tile-Mode Boundary Tests

**Files:**
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLoweringTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLowering.kt`

**Interfaces:**
- Consumes: `GPUBitmapShaderMaterialLowering.planSource(source, context)` and `GPUImageShaderPlan`.
- Produces: a reviewed decision boundary for `Repeat`, `Mirror`, and `Decal`: either accepted with distinct keys or refused with stable diagnostic codes. This task starts with `Repeat` because `bmp_filter_quality_repeat` and `tilemode_decal` make tile mode a likely first root cause.

- [ ] **Step 1: Write failing tests for repeat/mirror/decal separation**

Append these tests to `BitmapShaderMaterialLoweringTest.kt`:

```kotlin
@Test
fun `bitmap shader material lowering accepts repeat tile mode as a distinct source plan`() {
    val source = GPUMaterialSourceDescriptor.Image(
        plan = GPUImageShaderPlan(
            imageSourceKey = "repeat-image-key",
            sampling = GPUMaterialSamplingPlan(
                tileModeX = GPUMaterialTileMode.Repeat,
                tileModeY = GPUMaterialTileMode.Clamp,
                filterMode = "linear",
                mipmapMode = "none",
            ),
            colorTreatment = "sampled-unpremul-srgb-to-target",
        ),
    )
    val context = GPUMaterialLoweringContext(
        capabilityClass = "test-capability",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
    )

    val sourcePlan = GPUBitmapShaderMaterialLowering.planSource(source, context)
    val accepted = assertIs<GPUMaterialSourcePlan.Accepted>(sourcePlan)

    assertEquals(GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID, accepted.snippetId)
    assertTrue(accepted.diagnostics.any { it.code == "accepted.material_source.bitmap_shader" })
}

@Test
fun `bitmap shader material keys include tile modes`() {
    val context = GPUMaterialLoweringContext(
        capabilityClass = "test-capability",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
    )
    val clamp = GPUMaterialSourceDescriptor.Image(
        plan = GPUImageShaderPlan(
            imageSourceKey = "image-key",
            sampling = GPUMaterialSamplingPlan(GPUMaterialTileMode.Clamp, GPUMaterialTileMode.Clamp, "linear", "none"),
            colorTreatment = "sampled-unpremul-srgb-to-target",
        ),
    )
    val repeat = GPUMaterialSourceDescriptor.Image(
        plan = GPUImageShaderPlan(
            imageSourceKey = "image-key",
            sampling = GPUMaterialSamplingPlan(GPUMaterialTileMode.Repeat, GPUMaterialTileMode.Clamp, "linear", "none"),
            colorTreatment = "sampled-unpremul-srgb-to-target",
        ),
    )

    val clampPlan = assertIs<GPUMaterialSourcePlan.Accepted>(GPUBitmapShaderMaterialLowering.planSource(clamp, context))
    val repeatPlan = assertIs<GPUMaterialSourcePlan.Accepted>(GPUBitmapShaderMaterialLowering.planSource(repeat, context))

    assertTrue(
        GPUBitmapShaderMaterialLowering.deriveMaterialKey(clampPlan, context).value !=
            GPUBitmapShaderMaterialLowering.deriveMaterialKey(repeatPlan, context).value,
    )
}
```

- [ ] **Step 2: Run tests to verify the current boundary fails**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.materials.BitmapShaderMaterialLoweringTest
```

Expected: the new repeat acceptance tests fail because `Repeat` currently returns `unsupported.material.bitmap_tile_mode_unimplemented`.

- [ ] **Step 3: Implement the minimal material-source boundary**

Modify `BitmapShaderMaterialLowering.kt`:

```kotlin
private val supportedBitmapTileModes = setOf(
    GPUMaterialTileMode.Clamp,
    GPUMaterialTileMode.Repeat,
    GPUMaterialTileMode.Mirror,
    GPUMaterialTileMode.Decal,
)
```

Replace the existing clamp-only check with:

```kotlin
if (plan.sampling.tileModeX !in supportedBitmapTileModes ||
    plan.sampling.tileModeY !in supportedBitmapTileModes
) {
    return GPUMaterialSourcePlan.Refused(
        GPUMaterialSourceDiagnostic(
            code = "unsupported.material.bitmap_tile_mode_unknown",
            sourceKind = GPUMaterialSourceKind.ImageShader,
            message = "Bitmap shader tile mode must be one of ${supportedBitmapTileModes.joinToString { it.name }} " +
                "(got ${plan.sampling.tileModeX}/${plan.sampling.tileModeY})",
            terminal = true,
        ),
    )
}
```

Update `bitmapShaderMaterialKeyPreimage` so keys include tile/filter facts:

```kotlin
private fun bitmapShaderMaterialKeyPreimage(
    source: GPUMaterialSourceDescriptor.Image,
    context: GPUMaterialLoweringContext,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.ImageShader,
        snippetId = GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPUBitmapShaderMaterialDictionary.BitmapShaderMaterialLayoutHash,
        uniformLayoutLabel = "BitmapShaderMaterialBlock(texture:group1.binding1,sampler@group1.binding2)",
        payloadFields = listOf("texture@group1.binding1", "sampler@group1.binding2"),
        codeShapeFacts = listOf(
            "sourceFunction=bitmap_shader_source",
            "payloadBlock=BitmapShaderMaterialBlock",
            "tileModeX=${source.plan.sampling.tileModeX.name}",
            "tileModeY=${source.plan.sampling.tileModeY.name}",
            "filterMode=${source.plan.sampling.filterMode}",
            "mipmapMode=${source.plan.sampling.mipmapMode}",
        ),
        featureFlags = listOf("bitmap-shader-material-abi-v1"),
    )
```

Then update `deriveMaterialKey` to call `bitmapShaderMaterialKeyPreimage(source = source, context = context)`.

- [ ] **Step 4: Run material tests**

Run:

```bash
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.materials.BitmapShaderMaterialLoweringTest
```

Expected: all bitmap shader material lowering tests pass.

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLowering.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BitmapShaderMaterialLoweringTest.kt
git commit -m "Accept bounded bitmap shader tile modes"
```

---

### Task 3: Group A Targeted Render Probe

**Files:**
- Modify only if generated output changes: `integration-tests/skia/src/test/resources/generated-renders/image/*.png`
- Modify only if score task updates it: `integration-tests/skia/test-similarity-scores.properties`

**Interfaces:**
- Consumes: Group A material boundary from Task 2.
- Produces: exact before/after dashboard rows for `bmp_filter_quality_repeat`, `tilemode_decal`, `imageshader_tinyscale`, `pictureshader`, `pictureshader_alpha`, and `pictureshader_localwrapper`.

- [ ] **Step 1: Capture pre-probe candidate rows**

Run:

```bash
python3 - <<'PY'
import json, pathlib
p = pathlib.Path("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")
data = json.loads(p.read_text())
names = {"bmp_filter_quality_repeat","tilemode_decal","imageshader_tinyscale","pictureshader","pictureshader_alpha","pictureshader_localwrapper"}
for row in data["gms"]:
    if row["name"] in names:
        print(row["name"], row.get("similarity"), row.get("matchingPixels"), row.get("totalPixels"), row.get("isPassing"))
PY
```

Expected: prints all rows present in the current dashboard. If a row is absent, record that absence in the task report and continue with present rows.

- [ ] **Step 2: Generate targeted renders**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=bmp_filter_quality_repeat -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=tilemode_decal -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=imageshader_tinyscale -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=pictureshader -Pgm.includeBlocking=true
```

Expected: each command exits successfully or reports that the row is not selected by the current filter. Do not edit reference images.

- [ ] **Step 3: Regenerate dashboard for exact scores**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
```

Expected: Gradle task succeeds. Dashboard may still contain unrelated fail rows; record whether targeted Group A rows moved.

- [ ] **Step 4: Decide continue/fix/stop for Group A**

Run the same Python extraction from Step 1. Apply this decision rule:

- If any targeted row improves, keep generated PNG changes for that row and proceed to Step 5.
- If no targeted row changes, keep Task 2 only if tests prove a correct renderer boundary; do not commit generated PNG churn.
- If any targeted row regresses materially, restore only generated renders for that row and open a follow-up diagnostic before continuing.

- [ ] **Step 5: Run score gate if generated renders changed**

Run:

```bash
./gradlew :integration-tests:skia:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Guardrail check**

Run:

```bash
git diff --name-only -- integration-tests/skia/src/test/resources/reference
git diff --check
```

Expected: first command prints no output; second command prints no output.

- [ ] **Step 7: Commit generated evidence when justified**

```bash
git add integration-tests/skia/src/test/resources/generated-renders/image integration-tests/skia/test-similarity-scores.properties
git commit -m "Regenerate image sampling GM evidence"
```

Skip this commit if no generated render or score file changed.

---

### Task 4: Group B Composite Diagnostic Gate

**Files:**
- Modify: `reports/gpu-renderer/gm-fidelity-wave-3-score-first/evidence.md`
- Create when needed: `reports/gpu-renderer/gm-fidelity-wave-3-score-first/composite-candidates.tsv`
- No renderer source changes in this task.

**Interfaces:**
- Consumes: Task 1 evidence report.
- Produces: a ranked Group B diagnostic list that names the first 3 composite rows to implement next.

- [ ] **Step 1: Extract Group B rows from the report TSV**

Run:

```bash
awk -F '\t' 'NR == 1 || $1 == "B"' reports/gpu-renderer/gm-fidelity-wave-3-score-first/candidates.tsv \
  > reports/gpu-renderer/gm-fidelity-wave-3-score-first/composite-candidates.tsv
sed -n '1,20p' reports/gpu-renderer/gm-fidelity-wave-3-score-first/composite-candidates.tsv
```

Expected: header plus Group B candidates sorted by unmatched pixels.

- [ ] **Step 2: Record the first Group B implementation slice**

Append this section to `reports/gpu-renderer/gm-fidelity-wave-3-score-first/evidence.md`:

```markdown
## Group B Next Slice

The first composite slice is limited to the top three present Group B rows from
`composite-candidates.tsv`. The implementation worker must choose tests from
the specific mechanism visible in the source row:

- `transparency_check`: premul/alpha compositing boundary.
- `draw_image_set_rect_to_rect`: image-set rect mapping and clipping boundary.
- `tablecolorfilter` or `hslcolorfilter`: color-filter math boundary.

No global blend or color-filter behavior may change without focused unit tests.
```

- [ ] **Step 3: Commit the diagnostic**

```bash
git add reports/gpu-renderer/gm-fidelity-wave-3-score-first/evidence.md \
  reports/gpu-renderer/gm-fidelity-wave-3-score-first/composite-candidates.tsv
git commit -m "Document composite GM wave three candidates"
```

---

### Task 5: Group C And D Stop-Rule Inventory

**Files:**
- Create: `reports/gpu-renderer/gm-fidelity-wave-3-score-first/clip-path-runtime-candidates.tsv`
- Modify: `reports/gpu-renderer/gm-fidelity-wave-3-score-first/evidence.md`

**Interfaces:**
- Consumes: Task 1 evidence report.
- Produces: explicit stop-rule inventory for complex clip/path residuals and runtime-effect rows.

- [ ] **Step 1: Extract Group C and D rows**

Run:

```bash
awk -F '\t' 'NR == 1 || $1 == "C" || $1 == "D"' reports/gpu-renderer/gm-fidelity-wave-3-score-first/candidates.tsv \
  > reports/gpu-renderer/gm-fidelity-wave-3-score-first/clip-path-runtime-candidates.tsv
sed -n '1,40p' reports/gpu-renderer/gm-fidelity-wave-3-score-first/clip-path-runtime-candidates.tsv
```

Expected: header plus Group C/D candidates sorted within the original candidate ordering.

- [ ] **Step 2: Append stop-rule notes**

Append:

```markdown
## Group C/D Stop Rules

Group C complex clip rows may produce diagnostics instead of support if the
root cause is broad clip-stack parity. A support claim requires a bounded
clip/path route test and before/after dashboard rows.

Group D runtime-effect rows may become support only for registered Kanvas
descriptors with Kotlin CPU behavior and parser-validated WGSL. Arbitrary
Skia/SkSL input remains an explicit unsupported diagnostic.
```

- [ ] **Step 3: Commit**

```bash
git add reports/gpu-renderer/gm-fidelity-wave-3-score-first/evidence.md \
  reports/gpu-renderer/gm-fidelity-wave-3-score-first/clip-path-runtime-candidates.tsv
git commit -m "Document clip path and runtime GM stop rules"
```

---

### Task 6: Final Dashboard, Review Package, And PR Prep

**Files:**
- Modify if changed: `integration-tests/skia/src/test/resources/generated-renders/**`
- Modify if changed: `integration-tests/skia/test-similarity-scores.properties`
- Modify: PR body draft outside repo or in final message only.

**Interfaces:**
- Consumes: all accepted commits from Tasks 1-5.
- Produces: ready branch with full dashboard evidence and external review result.

- [ ] **Step 1: Run final focused tests**

Run:

```bash
./gradlew :integration-tests:skia-evidence:test --tests org.graphiks.kanvas.skia.evidence.ScoreFirstGmFidelityWave3Test
./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.materials.BitmapShaderMaterialLoweringTest
```

Expected: both commands pass.

- [ ] **Step 2: Run full dashboard and score gate**

Run:

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
./gradlew :integration-tests:skia:test
```

Expected: both commands exit with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Guardrails**

Run:

```bash
git diff --name-only origin/codex/master-after-path-gm...HEAD -- integration-tests/skia/src/test/resources/reference
git diff --check
git status --short --branch
```

Expected: no reference output, no diff-check output, and only intentional tracked changes before any final commit.

- [ ] **Step 4: Commit any final generated score/render evidence**

```bash
git add integration-tests/skia/src/test/resources/generated-renders integration-tests/skia/test-similarity-scores.properties
git commit -m "Regenerate GM fidelity wave three scores"
```

Skip if there are no generated render or score changes.

- [ ] **Step 5: Create review package**

Run:

```bash
/Users/chaos/.codex/plugins/cache/openai-curated-remote/superpowers/6.1.1/skills/subagent-driven-development/scripts/review-package \
  "$(git merge-base origin/codex/master-after-path-gm HEAD)" HEAD
```

Expected: a `.superpowers/sdd/review-*.diff` file is written. Do not commit `.superpowers` files.

- [ ] **Step 6: External review**

Ask an external reviewer to check Critical/Important blockers only:

```text
Review GM Fidelity Wave 3 score-first branch.
Base: origin/codex/master-after-path-gm
Focus: no reference image changes, no threshold lowering, correctness of score-first evidence, bitmap shader tile-mode semantics, generated render/score scope, and dashboard regressions.
```

Expected: approved or actionable findings. Fix Critical/Important findings before PR.

- [ ] **Step 7: Push and PR**

```bash
git push -u origin codex/gm-fidelity-wave-3-score-first
gh pr create --draft --base codex/master-after-path-gm --head codex/gm-fidelity-wave-3-score-first \
  --title "Add GM fidelity wave 3 score-first evidence" \
  --body "Stacked on #2027. Adds score-first candidate evidence and the first Group A image/sampling slice. Verification: skia-evidence tests, bitmap shader material tests, full Skia dashboard, Skia test, reference guardrail."
```

Expected: a draft PR URL.

---

## Self-Review Notes

- Spec coverage: Tasks 1, 4, and 5 cover broad score-first ranking and multi-family inventory. Tasks 2 and 3 cover the first implementation slice from Work Group A. Task 6 covers full verification, review, and PR.
- Guardrails: reference images, thresholds, unsupported rows, SkSL, WebGPU, and dependency-substitute constraints are copied into Global Constraints and final guardrails.
- Scope: the plan intentionally does not implement Groups B-D renderer fixes yet. It creates their ranked inventories and stop-rule boundaries, then starts implementation with Group A as approved in the spec.
- Red-flag scan: no incomplete markers or vague generic test steps remain. Group B-D renderer implementation is explicitly deferred by design, not left as an unspecified implementation hole.
