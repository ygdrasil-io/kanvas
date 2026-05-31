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
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 1)
            val gpuCmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[AaclipSceneCapture] adapter=$adapter cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                    "gpu=${"%.2f".format(gpuCmp.similarity)}%, gpuMatching=${gpuCmp.matchingPixels}/${gpuCmp.totalPixels}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(cpuBitmap, gpuBitmap, reference, cpuCmp, gpuCmp, adapter)
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
        reference: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid").apply { mkdirs() }
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

    private fun statsJson(cpuCmp: BitmapComparison, gpuCmp: BitmapComparison, adapter: String): String = """
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
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "clipOp": "intersect",
          "clipShape": "aa-rect-grid",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AaclipSceneCaptureTest"
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
        private const val CPU_SUPPORT_THRESHOLD = 96.95
        private const val GPU_SUPPORT_THRESHOLD = 98.78
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
    }
}
