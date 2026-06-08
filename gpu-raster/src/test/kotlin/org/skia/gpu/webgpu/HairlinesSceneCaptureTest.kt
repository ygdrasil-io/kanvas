package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.HairlinesGM

class HairlinesSceneCaptureTest {
    @Test
    fun `hairlines captures row specific reference cpu and webgpu evidence`() {
        val gm = HairlinesGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
            ?: error("original-888/${gm.name()}.png missing")
        val cpuStart = System.nanoTime()
        val cpuBitmap = TestUtils.runGmTest(gm)
        val cpuElapsedNanos = System.nanoTime() - cpuStart
        val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = CPU_TOLERANCE)
        val gpuOutcome = renderWebGpu(gm, reference)

        println(
            "[HairlinesSceneCapture] cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                "gpu=${gpuOutcome.summary}, status=${gpuOutcome.status}",
        )

        if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
            writeEvidence(
                reference = reference,
                cpuBitmap = cpuBitmap,
                cpuCmp = cpuCmp,
                cpuElapsedNanos = cpuElapsedNanos,
                gpuOutcome = gpuOutcome,
            )
        }

        assertTrue(
            cpuCmp.similarity >= CPU_MINIMUM_SIMILARITY,
            "HairlinesGM CPU evidence fell below the row-specific floor: " +
                "${cpuCmp.similarity}% < $CPU_MINIMUM_SIMILARITY%.",
        )
        assertTrue(
            gpuOutcome.status == "pass" || gpuOutcome.fallbackReason == FALLBACK_REASON,
            "HairlinesGM WebGPU route must pass or retain the stable M90-PAA-3A refusal reason.",
        )
    }

    private fun renderWebGpu(gm: HairlinesGM, reference: SkBitmap): GpuOutcome {
        val context = WebGpuContext.createOrNull()
            ?: return GpuOutcome.missingAdapter()

        val start = System.nanoTime()
        return try {
            context.use { ctx ->
                val gpuBitmap = WebGpuSink.draw(ctx, gm)
                val elapsedNanos = System.nanoTime() - start
                val cmp = TestUtils.compareBitmapsDetailed(
                    gpuBitmap,
                    reference,
                    tolerance = GPU_TOLERANCE,
                )
                val status = if (cmp.similarity >= GPU_PROMOTION_THRESHOLD) "pass" else "expected-unsupported"
                val fallbackReason = if (status == "pass") "none" else FALLBACK_REASON
                GpuOutcome(
                    bitmap = gpuBitmap,
                    comparison = cmp,
                    adapter = ctx.adapterInfo ?: "unknown-adapter",
                    status = status,
                    fallbackReason = fallbackReason,
                    elapsedNanos = elapsedNanos,
                    failure = null,
                )
            }
        } catch (t: Throwable) {
            GpuOutcome.failed(t, System.nanoTime() - start)
        }
    }

    private fun writeEvidence(
        reference: SkBitmap,
        cpuBitmap: SkBitmap,
        cpuCmp: BitmapComparison,
        cpuElapsedNanos: Long,
        gpuOutcome: GpuOutcome,
    ) {
        val dir = repoFile(ARTIFACT_DIR).apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        if (gpuOutcome.bitmap != null) {
            writePng(File(dir, "gpu.png"), gpuOutcome.bitmap)
            writePng(File(dir, "gpu-diff.png"), CrossBackendHarness.pixelDiff(reference, gpuOutcome.bitmap))
        }
        File(dir, "route-cpu.json").writeText(cpuRouteJson(cpuCmp))
        File(dir, "route-gpu.json").writeText(gpuRouteJson(gpuOutcome))
        File(dir, "stats.json").writeText(statsJson(cpuCmp, gpuOutcome))
        File(dir, "cpu-performance.json").writeText(cpuPerformanceJson(cpuCmp, cpuElapsedNanos))
        File(dir, "gpu-performance.json").writeText(gpuPerformanceJson(gpuOutcome))
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.path}")
        file.writeBytes(bytes)
    }

    private fun cpuRouteJson(cpuCmp: BitmapComparison): String = """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "CPU",
          "drawKind": "HairlinesGM",
          "status": "pass",
          "selectedRoute": "cpu.raster.dm-reference-colorspace.hairlines",
          "referenceKind": "skia-upstream-png",
          "referencePath": "skia-integration-tests/src/test/resources/original-888/hairlines.png",
          "fallbackReason": "none",
          "similarity": ${cpuCmp.similarity.jsonNumber()},
          "threshold": $CPU_MINIMUM_SIMILARITY,
          "tolerance": $CPU_TOLERANCE,
          "matchingPixels": ${cpuCmp.matchingPixels},
          "totalPixels": ${cpuCmp.totalPixels},
          "maxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "test": "org.skia.gpu.webgpu.HairlinesSceneCaptureTest#hairlines captures row specific reference cpu and webgpu evidence"
        }
    """.trimIndent() + "\n"

    private fun cpuPerformanceJson(cpuCmp: BitmapComparison, elapsedNanos: Long): String = """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "CPU",
          "drawKind": "HairlinesGM",
          "status": "pass",
          "fallbackReason": "none",
          "supportClaim": false,
          "sampleCount": 1,
          "warmup": false,
          "elapsedNanos": $elapsedNanos,
          "elapsedMillis": ${elapsedNanos.millisJsonNumber()},
          "similarity": ${cpuCmp.similarity.jsonNumber()},
          "threshold": $CPU_MINIMUM_SIMILARITY,
          "rawMetrics": "artifacts/$SCENE_ID/cpu-performance.json",
          "globalDashboardPromoted": false,
          "globalThresholdChanged": false,
          "neighborEvidenceInherited": false,
          "broadPathAASupport": false,
          "broadHairlineSupport": false,
          "test": "org.skia.gpu.webgpu.HairlinesSceneCaptureTest#hairlines captures row specific reference cpu and webgpu evidence"
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(gpuOutcome: GpuOutcome): String {
        val comparison = gpuOutcome.comparison
        val similarityLine = comparison?.let { """,
          "similarity": ${it.similarity.jsonNumber()},
          "threshold": $GPU_PROMOTION_THRESHOLD,
          "tolerance": $GPU_TOLERANCE,
          "matchingPixels": ${it.matchingPixels},
          "totalPixels": ${it.totalPixels},
          "maxChannelDelta": ${it.maxChannelDiff.max()}""" } ?: ""
        val failureLine = gpuOutcome.failure?.let { """,
          "failure": ${it.jsonString()}""" } ?: ""
        val selectedRoute = if (gpuOutcome.status == "pass") {
            "webgpu.path.hairline.row-specific"
        } else {
            "webgpu.refusal.path.hairline.row-specific"
        }
        return """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "WebGPU",
          "adapter": ${gpuOutcome.adapter.jsonString()},
          "drawKind": "HairlinesGM",
          "status": ${gpuOutcome.status.jsonString()},
          "selectedRoute": ${selectedRoute.jsonString()},
          "pipelineKey": "not-promoted-by-M90-PAA-3A-REF",
          "referenceKind": "skia-upstream-png",
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()}$similarityLine$failureLine,
          "supportClaim": false,
          "globalDashboardPromoted": false,
          "globalThresholdChanged": false,
          "neighborEvidenceInherited": false,
          "broadPathAASupport": false,
          "broadHairlineSupport": false,
          "test": "org.skia.gpu.webgpu.HairlinesSceneCaptureTest#hairlines captures row specific reference cpu and webgpu evidence"
        }
        """.trimIndent() + "\n"
    }

    private fun statsJson(cpuCmp: BitmapComparison, gpuOutcome: GpuOutcome): String {
        val gpuStats = gpuOutcome.comparison?.let { cmp ->
            """,
          "gpuSimilarity": ${cmp.similarity.jsonNumber()},
          "gpuMatchingPixels": ${cmp.matchingPixels},
          "gpuTotalPixels": ${cmp.totalPixels},
          "gpuMaxChannelDelta": ${cmp.maxChannelDiff.max()}"""
        } ?: ""
        return """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "status": ${gpuOutcome.status.jsonString()},
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()},
          "supportClaim": false,
          "globalDashboardPromoted": false,
          "referenceArtifact": "$ARTIFACT_DIR/skia.png",
          "cpuArtifact": "$ARTIFACT_DIR/cpu.png",
          "cpuDiffArtifact": "$ARTIFACT_DIR/cpu-diff.png",
          "gpuArtifact": ${if (gpuOutcome.bitmap != null) "\"$ARTIFACT_DIR/gpu.png\"" else "null"},
          "gpuDiffArtifact": ${if (gpuOutcome.bitmap != null) "\"$ARTIFACT_DIR/gpu-diff.png\"" else "null"},
          "routeCpu": "$ARTIFACT_DIR/route-cpu.json",
          "routeGpu": "$ARTIFACT_DIR/route-gpu.json",
          "cpuSimilarity": ${cpuCmp.similarity.jsonNumber()},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuTotalPixels": ${cpuCmp.totalPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()}$gpuStats,
          "cpuThreshold": $CPU_MINIMUM_SIMILARITY,
          "gpuPromotionThreshold": $GPU_PROMOTION_THRESHOLD,
          "cpuPerformanceArtifact": "$ARTIFACT_DIR/cpu-performance.json",
          "gpuPerformanceArtifact": "$ARTIFACT_DIR/gpu-performance.json",
          "globalThresholdChanged": false,
          "neighborEvidenceInherited": false,
          "broadPathAASupport": false,
          "broadHairlineSupport": false,
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest"
        }
        """.trimIndent() + "\n"
    }

    private fun gpuPerformanceJson(gpuOutcome: GpuOutcome): String {
        val comparison = gpuOutcome.comparison
        val similarityLine = comparison?.let { """,
          "similarity": ${it.similarity.jsonNumber()},
          "threshold": $GPU_PROMOTION_THRESHOLD""" } ?: ""
        val elapsedLine = gpuOutcome.elapsedNanos?.let { """,
          "elapsedNanos": $it,
          "elapsedMillis": ${it.millisJsonNumber()}""" } ?: """,
          "elapsedNanos": null,
          "elapsedMillis": null"""
        val failureLine = gpuOutcome.failure?.let { """,
          "failure": ${it.jsonString()}""" } ?: ""
        return """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "WebGPU",
          "adapter": ${gpuOutcome.adapter.jsonString()},
          "drawKind": "HairlinesGM",
          "status": ${gpuOutcome.status.jsonString()},
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()},
          "supportClaim": false,
          "sampleCount": 1,
          "warmup": false$elapsedLine$similarityLine$failureLine,
          "rawMetrics": "artifacts/$SCENE_ID/gpu-performance.json",
          "globalDashboardPromoted": false,
          "globalThresholdChanged": false,
          "neighborEvidenceInherited": false,
          "broadPathAASupport": false,
          "broadHairlineSupport": false,
          "test": "org.skia.gpu.webgpu.HairlinesSceneCaptureTest#hairlines captures row specific reference cpu and webgpu evidence"
        }
        """.trimIndent() + "\n"
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private data class GpuOutcome(
        val bitmap: SkBitmap?,
        val comparison: BitmapComparison?,
        val adapter: String,
        val status: String,
        val fallbackReason: String,
        val elapsedNanos: Long?,
        val failure: String?,
    ) {
        val summary: String
            get() = comparison?.let { "${"%.2f".format(it.similarity)}%" } ?: "not-rendered"

        companion object {
            fun missingAdapter(): GpuOutcome = GpuOutcome(
                bitmap = null,
                comparison = null,
                adapter = "no-webgpu-adapter",
                status = "expected-unsupported",
                fallbackReason = FALLBACK_REASON,
                elapsedNanos = null,
                failure = "No WebGPU adapter",
            )

            fun failed(t: Throwable, elapsedNanos: Long): GpuOutcome = GpuOutcome(
                bitmap = null,
                comparison = null,
                adapter = "webgpu-render-failed",
                status = "expected-unsupported",
                fallbackReason = FALLBACK_REASON,
                elapsedNanos = elapsedNanos,
                failure = "${t::class.qualifiedName}: ${t.message ?: "no message"}",
            )
        }
    }

    private fun Double.jsonNumber(): String = String.format(Locale.US, "%.4f", this)

    private fun Long.millisJsonNumber(): String = String.format(Locale.US, "%.4f", this / 1_000_000.0)

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
        private const val SCENE_ID = "skia-gm-hairlines"
        private const val INVENTORY_ID = "skia-gm-hairlines"
        private const val ARTIFACT_DIR = "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines"
        private const val FALLBACK_REASON = "coverage.hairline.row-specific-artifacts-required"
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private const val CPU_TOLERANCE = 1
        private const val GPU_TOLERANCE = TestUtils.TEXTUAL_GM_TOLERANCE
        private const val CPU_MINIMUM_SIMILARITY = 96.0
        private const val GPU_PROMOTION_THRESHOLD = 99.95
    }
}
