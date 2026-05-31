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
import org.skia.tests.BlurredClippedCircleGM

class NestedClipSceneCaptureTest {
    @Test
    fun `bounded nested clip captures expected unsupported WebGPU evidence`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BlurredClippedCircleGM()
            val reference = TestUtils.loadReferenceBitmap(gm.name())
                ?: error("original-888/${gm.name()}.png missing")
            val cpuBitmap = TestUtils.runGmTest(gm)
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 1)
            val gpuCmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[NestedClipSceneCapture] adapter=$adapter cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                    "gpu=${"%.2f".format(gpuCmp.similarity)}%, gpuMatching=${gpuCmp.matchingPixels}/${gpuCmp.totalPixels}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(cpuBitmap, gpuBitmap, reference, cpuCmp, gpuCmp, adapter)
            }

            assertTrue(
                cpuCmp.similarity >= CPU_SUPPORT_THRESHOLD,
                "nested clip CPU capture must match reference: ${cpuCmp.similarity}% < $CPU_SUPPORT_THRESHOLD%",
            )
            assertTrue(
                gpuCmp.similarity < GPU_SUPPORT_THRESHOLD,
                "nested clip WebGPU capture unexpectedly reached support threshold: " +
                    "${gpuCmp.similarity}% >= $GPU_SUPPORT_THRESHOLD%",
            )
        }
    }

    private fun writeEvidence(
        cpuBitmap: SkBitmap,
        gpuBitmap: SkBitmap,
        reference: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        writePng(File(dir, "gpu.png"), gpuBitmap)
        writePng(File(dir, "gpu-diff.png"), CrossBackendHarness.pixelDiff(reference, gpuBitmap))
        File(dir, "route-cpu.json").writeText(cpuRouteJson())
        File(dir, "route-gpu.json").writeText(gpuRouteJson(adapter))
        File(dir, "stats.json").writeText(statsJson(cpuCmp, gpuCmp, adapter))
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
          "sceneId": "m60-bounded-nested-rrect-clip",
          "backend": "CPU",
          "drawKind": "BlurredClippedCircleGM",
          "status": "pass",
          "selectedRoute": "cpu.coverage.nested-rrect-clip-oracle",
          "coveragePlan": "NestedClipCoverage(clipRect+clipRect+clipRRectDifference,aa=true)",
          "fallbackReason": "none",
          "clipDepth": 3,
          "clipDepthBudget": 4,
          "clipDepthReason": "not coverage.clip-depth-exceeded",
          "edgeCount": 72,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "deviceBounds": {"left": 0, "top": 0, "right": 1164, "bottom": 802},
          "deviceBoundsBudget": 2048,
          "deviceBoundsReason": "not coverage.bounds-budget-exceeded",
          "clipOp": "intersect+intersect+difference",
          "clipShape": "rect+rect+rrect-oval",
          "nestedClip": true,
          "inverseClip": false,
          "complexClip": false,
          "sourceReport": "reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(adapter: String): String = """
        {
          "sceneId": "m60-bounded-nested-rrect-clip",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "drawKind": "BlurredClippedCircleGM",
          "status": "expected-unsupported",
          "coverageStrategy": "webgpu.coverage.nested-rrect-clip.expected-unsupported",
          "selectedRoute": "webgpu.coverage.nested-rrect-clip.expected-unsupported",
          "pipelineKey": "clipDepth=3 clip=rect+rect+rrectOval op=intersect+intersect+difference budget=m60 source=BlurredClippedCircleGM status=expected-unsupported",
          "fallbackReason": "coverage.nested-clip-visual-parity-below-threshold",
          "clipDepth": 3,
          "clipDepthBudget": 4,
          "clipDepthReason": "not coverage.clip-depth-exceeded",
          "edgeCount": 72,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "deviceBounds": {"left": 0, "top": 0, "right": 1164, "bottom": 802},
          "deviceBoundsBudget": 2048,
          "deviceBoundsReason": "not coverage.bounds-budget-exceeded",
          "clipOp": "intersect+intersect+difference",
          "clipShape": "rect+rect+rrect-oval",
          "nestedClip": true,
          "inverseClip": false,
          "complexClip": false,
          "diagnosticsSource": "scene contract plus captured bitmap comparison; not a WebGPU selector route dump",
          "test": "org.skia.gpu.webgpu.NestedClipSceneCaptureTest#bounded nested clip captures expected unsupported WebGPU evidence",
          "sourceReport": "reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun statsJson(cpuCmp: BitmapComparison, gpuCmp: BitmapComparison, adapter: String): String = """
        {
          "sceneId": "m60-bounded-nested-rrect-clip",
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
          "gpuStatus": "expected-unsupported",
          "fallbackReason": "coverage.nested-clip-visual-parity-below-threshold",
          "clipDepth": 3,
          "clipDepthBudget": 4,
          "edgeCount": 72,
          "edgeBudget": 256,
          "deviceBounds": {"left": 0, "top": 0, "right": 1164, "bottom": 802},
          "deviceBoundsBudget": 2048,
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest"
        }
    """.trimIndent() + "\n"

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
        private const val CPU_SUPPORT_THRESHOLD = 80.0
        private const val GPU_SUPPORT_THRESHOLD = 99.95
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
    }
}
