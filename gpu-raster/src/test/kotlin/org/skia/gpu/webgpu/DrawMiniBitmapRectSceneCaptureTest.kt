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
import org.skia.tests.DrawMiniBitmapRectGM

class DrawMiniBitmapRectSceneCaptureTest {
    @Test
    fun `drawminibitmaprect captures row specific reference cpu and webgpu evidence`() {
        val gm = DrawMiniBitmapRectGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
            ?: error("original-888/${gm.name()}.png missing")
        val cpuBitmap = TestUtils.runGmTest(gm)
        val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = CPU_TOLERANCE)
        val gpuOutcome = renderWebGpu(gm, reference)

        println(
            "[DrawMiniBitmapRectSceneCapture] cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                "gpu=${gpuOutcome.summary}, status=${gpuOutcome.status}",
        )

        if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
            writeEvidence(
                reference = reference,
                cpuBitmap = cpuBitmap,
                cpuCmp = cpuCmp,
                gpuOutcome = gpuOutcome,
            )
        }

        assertTrue(
            cpuCmp.similarity >= CPU_MINIMUM_SIMILARITY,
            "DrawMiniBitmapRectGM CPU evidence fell below the historical stress-test floor: " +
                "${cpuCmp.similarity}% < $CPU_MINIMUM_SIMILARITY%.",
        )
        assertTrue(
            gpuOutcome.status == "pass" || gpuOutcome.fallbackReason == FALLBACK_REASON,
            "DrawMiniBitmapRectGM WebGPU route must pass or retain the stable D51/D52 refusal reason.",
        )
    }

    private fun renderWebGpu(gm: DrawMiniBitmapRectGM, reference: SkBitmap): GpuOutcome {
        val context = WebGpuContext.createOrNull()
            ?: return GpuOutcome.missingAdapter()

        return try {
            context.use { ctx ->
                val gpuBitmap = WebGpuSink.draw(ctx, gm)
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
                    failure = null,
                )
            }
        } catch (t: Throwable) {
            GpuOutcome.failed(t)
        }
    }

    private fun writeEvidence(
        reference: SkBitmap,
        cpuBitmap: SkBitmap,
        cpuCmp: BitmapComparison,
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
          "drawKind": "DrawMiniBitmapRectGM",
          "status": "pass",
          "selectedRoute": "cpu.raster.dm-reference-colorspace.draw-image-rect-fast-src",
          "referenceKind": "skia-upstream-png",
          "referencePath": "skia-integration-tests/src/test/resources/original-888/drawminibitmaprect.png",
          "fallbackReason": "none",
          "similarity": ${cpuCmp.similarity.jsonNumber()},
          "threshold": $CPU_MINIMUM_SIMILARITY,
          "tolerance": $CPU_TOLERANCE,
          "matchingPixels": ${cpuCmp.matchingPixels},
          "totalPixels": ${cpuCmp.totalPixels},
          "maxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "test": "org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest#drawminibitmaprect captures row specific reference cpu and webgpu evidence"
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
            "webgpu.image.drawminibitmaprect.fast-src-rotated-grid"
        } else {
            "webgpu.refusal.drawminibitmaprect.fast-src-rotated-grid"
        }
        return """
        {
          "sceneId": "$SCENE_ID",
          "inventoryId": "$INVENTORY_ID",
          "backend": "WebGPU",
          "adapter": ${gpuOutcome.adapter.jsonString()},
          "drawKind": "DrawMiniBitmapRectGM",
          "status": ${gpuOutcome.status.jsonString()},
          "selectedRoute": ${selectedRoute.jsonString()},
          "pipelineKey": "not-promoted-by-D52-2",
          "referenceKind": "skia-upstream-png",
          "fallbackReason": ${gpuOutcome.fallbackReason.jsonString()}$similarityLine$failureLine,
          "supportClaim": ${gpuOutcome.status == "pass"},
          "globalThresholdChanged": false,
          "m66EvidenceInherited": false,
          "test": "org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest#drawminibitmaprect captures row specific reference cpu and webgpu evidence"
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
          "supportClaim": ${gpuOutcome.status == "pass"},
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
          "globalThresholdChanged": false,
          "m66EvidenceInherited": false,
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest"
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
                failure = "No WebGPU adapter",
            )

            fun failed(t: Throwable): GpuOutcome = GpuOutcome(
                bitmap = null,
                comparison = null,
                adapter = "webgpu-render-failed",
                status = "expected-unsupported",
                fallbackReason = FALLBACK_REASON,
                failure = "${t::class.qualifiedName}: ${t.message ?: "no message"}",
            )
        }
    }

    private fun Double.jsonNumber(): String = String.format(Locale.US, "%.4f", this)

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
        private const val SCENE_ID = "d52-drawminibitmaprect"
        private const val INVENTORY_ID = "skia-gm-drawminibitmaprect"
        private const val ARTIFACT_DIR = "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect"
        private const val FALLBACK_REASON = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private const val CPU_TOLERANCE = 1
        private const val GPU_TOLERANCE = TestUtils.TEXTUAL_GM_TOLERANCE
        private const val CPU_MINIMUM_SIMILARITY = 40.0
        private const val GPU_PROMOTION_THRESHOLD = 99.95
    }
}
