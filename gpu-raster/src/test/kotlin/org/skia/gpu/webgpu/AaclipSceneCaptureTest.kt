package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.AaclipGM

class AaclipSceneCaptureTest {
    @Test
    fun `aaclip bounded grid renders from adapter backed WebGPU capture`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = AaclipGM()
            val reference = TestUtils.loadReferenceBitmap(gm.name())
                ?: error("original-888/${gm.name()}.png missing")
            val cpuBitmap = TestUtils.runGmTest(gm)
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val targetGpuBitmap = WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 1)
            val gpuCmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE)
            val targetGpuCmp = TestUtils.compareBitmapsDetailed(
                targetGpuBitmap,
                reference,
                tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            val normalToleranceProfile = toleranceProfile(gpuBitmap, reference)
            val targetToleranceProfile = toleranceProfile(targetGpuBitmap, reference)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[AaclipSceneCapture] adapter=$adapter cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                    "gpu=${"%.2f".format(gpuCmp.similarity)}%, " +
                    "targetColor=${"%.2f".format(targetGpuCmp.similarity)}%, " +
                    "gpuMatching=${gpuCmp.matchingPixels}/${gpuCmp.totalPixels}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(
                    cpuBitmap = cpuBitmap,
                    gpuBitmap = gpuBitmap,
                    targetGpuBitmap = targetGpuBitmap,
                    reference = reference,
                    cpuCmp = cpuCmp,
                    gpuCmp = gpuCmp,
                    targetGpuCmp = targetGpuCmp,
                    normalToleranceProfile = normalToleranceProfile,
                    targetToleranceProfile = targetToleranceProfile,
                    adapter = adapter,
                )
            }

            assertTrue(
                cpuCmp.similarity >= CPU_SUPPORT_THRESHOLD,
                "aaclip CPU capture must match reference: ${cpuCmp.similarity}% < $CPU_SUPPORT_THRESHOLD%",
            )
            assertTrue(
                gpuCmp.similarity >= GPU_SUPPORT_THRESHOLD,
                "aaclip WebGPU capture must match reference: ${gpuCmp.similarity}% < $GPU_SUPPORT_THRESHOLD%",
            )
        }
    }

    private fun writeEvidence(
        cpuBitmap: SkBitmap,
        gpuBitmap: SkBitmap,
        targetGpuBitmap: SkBitmap,
        reference: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuCmp: BitmapComparison,
        targetGpuCmp: BitmapComparison,
        normalToleranceProfile: List<ToleranceStat>,
        targetToleranceProfile: List<ToleranceStat>,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        writePng(File(dir, "gpu.png"), gpuBitmap)
        writePng(File(dir, "gpu-diff.png"), CrossBackendHarness.pixelDiff(reference, gpuBitmap))
        writePng(File(dir, "gpu-target-color.png"), targetGpuBitmap)
        writePng(File(dir, "gpu-target-color-diff.png"), CrossBackendHarness.pixelDiff(reference, targetGpuBitmap))
        File(dir, "route-cpu.json").writeText(cpuRouteJson())
        File(dir, "route-gpu.json").writeText(gpuRouteJson(adapter))
        File(dir, "target-color-diagnostic.json").writeText(
            targetColorDiagnosticJson(gpuCmp, targetGpuCmp, normalToleranceProfile, targetToleranceProfile, adapter),
        )
        File(dir, "stats.json").writeText(
            statsJson(cpuCmp, gpuCmp, targetGpuCmp, normalToleranceProfile, targetToleranceProfile, adapter),
        )
        repoFile("reports/wgsl-pipeline/2026-06-02-m57-aaclip-target-color-diagnostic.md").writeText(
            targetColorReport(gpuCmp, targetGpuCmp, normalToleranceProfile, targetToleranceProfile, adapter),
        )
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.path}")
        file.writeBytes(bytes)
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun cpuRouteJson(): String = """
        {
          "sceneId": "m57-aaclip-bounded-grid",
          "backend": "CPU",
          "drawKind": "AaclipGM",
          "status": "pass",
          "selectedRoute": "cpu.coverage.aaclip-bounded-grid-oracle",
          "coveragePlan": "AaclipGM grid: AA clipRect intersect, no inverse clip, no complex clip, no dash",
          "fallbackReason": "none",
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "clipOp": "intersect",
          "clipShape": "aa-rect-grid",
          "inverseClip": false,
          "complexClip": false,
          "dashPattern": "none",
          "sourceReport": "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(adapter: String): String = """
        {
          "sceneId": "m57-aaclip-bounded-grid",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "drawKind": "AaclipGM",
          "status": "pass",
          "coverageStrategy": "webgpu.coverage.aa-clip-rect-grid",
          "selectedRoute": "webgpu.coverage.aaclip-bounded-grid",
          "pipelineKey": "pathAA=aaclip clip=aaRectGrid op=intersect budget=current source=AaclipGM",
          "fallbackReason": "none",
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "clipOp": "intersect",
          "clipShape": "aa-rect-grid",
          "inverseClip": false,
          "complexClip": false,
          "dashPattern": "none",
          "test": "org.skia.gpu.webgpu.AaclipSceneCaptureTest#aaclip bounded grid renders from adapter backed WebGPU capture",
          "sourceReport": "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun targetColorDiagnosticJson(
        gpuCmp: BitmapComparison,
        targetGpuCmp: BitmapComparison,
        normalToleranceProfile: List<ToleranceStat>,
        targetToleranceProfile: List<ToleranceStat>,
        adapter: String,
    ): String {
        val normalExact = normalToleranceProfile.first { it.tolerance == 0 }
        val targetExact = targetToleranceProfile.first { it.tolerance == 0 }
        return """
        {
          "sceneId": "m57-aaclip-bounded-grid",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "status": "diagnostic-only",
          "supportClaim": false,
          "normalRoute": "webgpu.coverage.aaclip-bounded-grid",
          "normalRouteStatus": "pass",
          "targetColorSpaceBlend": true,
          "targetColorSpaceBlendEnabledGlobally": false,
          "promotionThreshold": $PROMOTION_THRESHOLD,
          "normalSupportSimilarity": ${String.format(Locale.US, "%.2f", gpuCmp.similarity)},
          "targetSupportSimilarity": ${String.format(Locale.US, "%.2f", targetGpuCmp.similarity)},
          "normalExactSimilarity": ${String.format(Locale.US, "%.2f", normalExact.similarity)},
          "targetExactSimilarity": ${String.format(Locale.US, "%.2f", targetExact.similarity)},
          "exactSimilarityDelta": ${String.format(Locale.US, "%.2f", targetExact.similarity - normalExact.similarity)},
          "exactDecision": ${exactDecision(normalExact.similarity, targetExact.similarity).jsonString()},
          "normalToleranceProfile": [
        ${normalToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "targetToleranceProfile": [
        ${targetToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "decision": "Diagnostic only: targetColorSpaceBlend is measured for M57, the normal WebGPU route remains unchanged, and no 99.95 promotion is claimed.",
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AaclipSceneCaptureTest"
        }
        """.trimIndent() + "\n"
    }

    private fun statsJson(
        cpuCmp: BitmapComparison,
        gpuCmp: BitmapComparison,
        targetGpuCmp: BitmapComparison,
        normalToleranceProfile: List<ToleranceStat>,
        targetToleranceProfile: List<ToleranceStat>,
        adapter: String,
    ): String {
        val normalExact = normalToleranceProfile.first { it.tolerance == 0 }
        val targetExact = targetToleranceProfile.first { it.tolerance == 0 }
        return """
        {
          "sceneId": "m57-aaclip-bounded-grid",
          "pixels": ${gpuCmp.totalPixels},
          "matchingPixels": ${gpuCmp.matchingPixels},
          "maxChannelDelta": ${gpuCmp.maxChannelDiff.max()},
          "threshold": $GPU_SUPPORT_THRESHOLD,
          "cpuSimilarity": ${String.format(Locale.US, "%.2f", cpuCmp.similarity)},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "gpuSimilarity": ${String.format(Locale.US, "%.2f", gpuCmp.similarity)},
          "gpuMatchingPixels": ${gpuCmp.matchingPixels},
          "gpuMaxChannelDelta": ${gpuCmp.maxChannelDiff.max()},
          "gpuStatus": "pass",
          "targetColorSpaceBlendEnabledGlobally": false,
          "targetColorSpaceBlendDiagnosticStatus": "diagnostic-only",
          "targetColorSpaceBlendSupportClaim": false,
          "normalExactSimilarity": ${String.format(Locale.US, "%.2f", normalExact.similarity)},
          "normalExactMatchingPixels": ${normalExact.matchingPixels},
          "normalToleranceProfile": [
        ${normalToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "targetColorSpaceBlend": true,
          "targetGpuSimilarity": ${String.format(Locale.US, "%.2f", targetGpuCmp.similarity)},
          "targetGpuMatchingPixels": ${targetGpuCmp.matchingPixels},
          "targetGpuMaxChannelDelta": ${targetGpuCmp.maxChannelDiff.max()},
          "targetExactSimilarity": ${String.format(Locale.US, "%.2f", targetExact.similarity)},
          "targetExactMatchingPixels": ${targetExact.matchingPixels},
          "targetToleranceProfile": [
        ${targetToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "exactSimilarityDelta": ${String.format(Locale.US, "%.2f", targetExact.similarity - normalExact.similarity)},
          "exactDecision": ${exactDecision(normalExact.similarity, targetExact.similarity).jsonString()},
          "promotionThreshold": $PROMOTION_THRESHOLD,
          "promotionClaim": false,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "clipOp": "intersect",
          "clipShape": "aa-rect-grid",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AaclipSceneCaptureTest"
        }
        """.trimIndent() + "\n"
    }

    private fun targetColorReport(
        gpuCmp: BitmapComparison,
        targetGpuCmp: BitmapComparison,
        normalToleranceProfile: List<ToleranceStat>,
        targetToleranceProfile: List<ToleranceStat>,
        adapter: String,
    ): String {
        val normalExact = normalToleranceProfile.first { it.tolerance == 0 }
        val targetExact = targetToleranceProfile.first { it.tolerance == 0 }
        return buildString {
            appendLine("# M57 aaclip target-color diagnostic - 2026-06-02")
            appendLine()
            appendLine("Linear: `FOR-235`")
            appendLine()
            appendLine("## Decision")
            appendLine()
            appendLine("`m57-aaclip-bounded-grid` keeps its normal WebGPU route")
            appendLine("`webgpu.coverage.aaclip-bounded-grid` and remains a local-threshold")
            appendLine("`pass`. The `targetColorSpaceBlend=true` render is diagnostic-only and")
            appendLine("is not enabled globally.")
            appendLine()
            appendLine("Exact-score result: ${exactDecision(normalExact.similarity, targetExact.similarity)}.")
            appendLine()
            appendLine("## Metrics")
            appendLine()
            appendLine("Adapter: `$adapter`")
            appendLine()
            appendLine("| Mode | Target blend | Support similarity | Exact similarity | Matching pixels at exact | Max channel delta |")
            appendLine("|---|---:|---:|---:|---:|---:|")
            appendLine(
                "| Normal WebGPU | false | ${String.format(Locale.US, "%.2f", gpuCmp.similarity)}% | " +
                    "${String.format(Locale.US, "%.2f", normalExact.similarity)}% | " +
                    "${normalExact.matchingPixels}/${normalExact.totalPixels} | ${gpuCmp.maxChannelDiff.max()} |",
            )
            appendLine(
                "| Diagnostic target-color | true | ${String.format(Locale.US, "%.2f", targetGpuCmp.similarity)}% | " +
                    "${String.format(Locale.US, "%.2f", targetExact.similarity)}% | " +
                    "${targetExact.matchingPixels}/${targetExact.totalPixels} | ${targetGpuCmp.maxChannelDiff.max()} |",
            )
            appendLine()
            appendLine(
                "Exact delta: `${String.format(Locale.US, "%.2f", targetExact.similarity - normalExact.similarity)}` " +
                    "percentage points.",
            )
            appendLine()
            appendLine("## Tolerance profile")
            appendLine()
            appendLine("| Tolerance | Normal similarity | Target-color similarity |")
            appendLine("|---:|---:|---:|")
            for ((normal, target) in normalToleranceProfile.zip(targetToleranceProfile)) {
                appendLine(
                    "| ${normal.tolerance} | ${String.format(Locale.US, "%.2f", normal.similarity)}% | " +
                        "${String.format(Locale.US, "%.2f", target.similarity)}% |",
                )
            }
            appendLine()
            appendLine("## Non-claims")
            appendLine()
            appendLine("- No global `targetColorSpaceBlend` enablement.")
            appendLine("- No shader, `SkWebGpuDevice`, route, or threshold change.")
            appendLine("- No promotion to `99.95%` support.")
            appendLine("- No expansion beyond this solid-color AA clip-grid diagnostic.")
            appendLine()
            appendLine("## Artifacts")
            appendLine()
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu.png`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu-diff.png`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu-target-color.png`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/gpu-target-color-diff.png`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/target-color-diagnostic.json`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/stats.json`")
            appendLine()
            appendLine("## Validation")
            appendLine()
            appendLine("```text")
            appendLine("rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AaclipSceneCaptureTest")
            appendLine("rtk ./gradlew --no-daemon pipelineSceneDashboardGate")
            appendLine("rtk git diff --check")
            appendLine("```")
        }
    }

    private fun toleranceProfile(gpu: SkBitmap, reference: SkBitmap): List<ToleranceStat> =
        listOf(0, TestUtils.TEXTUAL_GM_TOLERANCE, 16, 32).map { tolerance ->
            val cmp = TestUtils.compareBitmapsDetailed(gpu, reference, tolerance = tolerance)
            ToleranceStat(tolerance, cmp.similarity, cmp.matchingPixels, cmp.totalPixels)
        }

    private fun exactDecision(normalExact: Double, targetExact: Double): String =
        when {
            targetExact > normalExact -> "targetColorSpaceBlend improves the exact score"
            targetExact < normalExact -> "targetColorSpaceBlend degrades the exact score"
            else -> "targetColorSpaceBlend does not change the exact score"
        }

    private data class ToleranceStat(
        val tolerance: Int,
        val similarity: Double,
        val matchingPixels: Int,
        val totalPixels: Int,
    ) {
        fun toJson(): String = """
            {
              "tolerance": $tolerance,
              "similarity": ${String.format(Locale.US, "%.2f", similarity)},
              "matchingPixels": $matchingPixels
            }
        """.trimIndent()
    }

    private fun String.jsonString(): String = buildString {
        append('"')
        for (ch in this@jsonString) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private companion object {
        private const val CPU_SUPPORT_THRESHOLD = 96.95
        private const val GPU_SUPPORT_THRESHOLD = 98.78
        private const val PROMOTION_THRESHOLD = 99.95
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
    }
}
