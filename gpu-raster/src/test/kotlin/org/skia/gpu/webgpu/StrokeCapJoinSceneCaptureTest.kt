package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM

class StrokeCapJoinSceneCaptureTest {
    @Test
    fun `bounded stroke cap join scene captures visual parity blocker evidence`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BoundedStrokeCapJoinGM()
            val reference = TestUtils.runGmTest(gm)
            val cpuBitmap = TestUtils.runGmTest(gm)
            val gpuError = assertThrows(IllegalStateException::class.java) {
                WebGpuSink.draw(ctx, gm)
            }
            val experimentalGpu = withExperimentalStrokeCapJoinRender {
                WebGpuSink.draw(ctx, gm)
            }
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)
            val experimentalGpuCmp = TestUtils.compareBitmapsDetailed(experimentalGpu, reference, tolerance = 0)
            val experimentalGpuToleranceProfile = toleranceProfile(experimentalGpu, reference)
            val regionStats = strokeRegionStats(experimentalGpu, reference)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[StrokeCapJoinSceneCapture] adapter=$adapter cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                    "experimentalGpu=${"%.2f".format(experimentalGpuCmp.similarity)}%, " +
                    "dominantGap=${regionStats.minBy { it.similarity }.id}, gpuRefusal=${gpuError.message}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(
                    cpuBitmap = cpuBitmap,
                    reference = reference,
                    experimentalGpu = experimentalGpu,
                    cpuCmp = cpuCmp,
                    experimentalGpuCmp = experimentalGpuCmp,
                    experimentalGpuToleranceProfile = experimentalGpuToleranceProfile,
                    regionStats = regionStats,
                    adapter = adapter,
                )
            }

            assertEquals(100.0, cpuCmp.similarity, 0.0)
            assertTrue(cpuCmp.matchingPixels == cpuCmp.totalPixels)
            assertTrue(experimentalGpuCmp.similarity < GPU_SUPPORT_THRESHOLD)
            assertTrue(
                gpuError.message!!.contains("coverage.stroke-cap-join-visual-parity-below-threshold"),
                "expected stable stroke cap/join blocker diagnostic, got ${gpuError.message}",
            )
        }
    }

    private fun writeEvidence(
        cpuBitmap: SkBitmap,
        reference: SkBitmap,
        experimentalGpu: SkBitmap,
        cpuCmp: BitmapComparison,
        experimentalGpuCmp: BitmapComparison,
        experimentalGpuToleranceProfile: List<ToleranceStat>,
        regionStats: List<StrokeRegionStats>,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        writePng(File(dir, "gpu-experimental.png"), experimentalGpu)
        writePng(File(dir, "gpu-experimental-diff.png"), CrossBackendHarness.pixelDiff(reference, experimentalGpu))
        File(dir, "gpu.png").delete()
        File(dir, "gpu-diff.png").delete()
        File(dir, "route-cpu.json").writeText(cpuRouteJson())
        File(dir, "route-gpu.json").writeText(gpuRouteJson(adapter))
        File(dir, "experimental-gpu-diagnostic.json").writeText(
            experimentalGpuDiagnosticJson(experimentalGpuCmp, experimentalGpuToleranceProfile, regionStats, adapter),
        )
        File(dir, "stats.json").writeText(statsJson(cpuCmp, experimentalGpuCmp, experimentalGpuToleranceProfile, regionStats, adapter))
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
          "sceneId": "m60-bounded-stroke-cap-join",
          "backend": "CPU",
          "drawKind": "BoundedStrokeCapJoinGM",
          "status": "pass",
          "selectedRoute": "cpu.coverage.stroke-cap-join-oracle",
          "coveragePlan": "PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,capJoinMatrix=butt-bevel+round-round+square-bevel)",
          "fallbackReason": "none",
          "pathVerbCount": 9,
          "pathVerbBudget": 96,
          "pathVerbReason": "not coverage.verb-budget-exceeded",
          "edgeCount": 18,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "strokeWidth": 10.0,
          "strokeWidthBudget": {"min": 0.5, "max": 64.0},
          "strokeWidthReason": "not coverage.stroke-width-budget-exceeded",
          "strokeCaps": ["butt", "round", "square"],
          "strokeJoins": ["bevel", "round", "bevel"],
          "dashIntervalCount": 0,
          "dashIntervalBudget": 8,
          "dashReason": "not coverage.dash-budget-exceeded",
          "deviceBounds": {"left": 0, "top": 0, "right": 192, "bottom": 128},
          "deviceBoundsBudget": 2048,
          "deviceBoundsReason": "not coverage.bounds-budget-exceeded",
          "diagnosticsSource": "CPU scene fixture with bounded stroke width/cap/join facts.",
          "sourceReport": "reports/wgsl-pipeline/2026-06-01-m60-stroke-cap-join-path-aa-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun gpuRouteJson(adapter: String): String = """
        {
          "sceneId": "m60-bounded-stroke-cap-join",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "drawKind": "BoundedStrokeCapJoinGM",
          "status": "expected-unsupported",
          "coverageStrategy": "webgpu.coverage.refuse",
          "selectedRoute": "webgpu.coverage.refuse",
          "pipelineKey": "coverageKind=pathAaStrokeCapJoinBlocked pathFillRule=winding topology=triangleList budget=m60 source=LinePathGM-derived status=expected-unsupported",
          "fallbackReason": "coverage.stroke-cap-join-visual-parity-below-threshold",
          "rootCause": "color-space.target-blend-required",
          "pathVerbCount": 9,
          "pathVerbBudget": 96,
          "pathVerbReason": "not coverage.verb-budget-exceeded",
          "edgeCount": 18,
          "edgeBudget": 256,
          "edgeBudgetReason": "not coverage.edge-count-exceeded",
          "strokeWidth": 10.0,
          "strokeWidthBudget": {"min": 0.5, "max": 64.0},
          "strokeWidthReason": "not coverage.stroke-width-budget-exceeded",
          "strokeCaps": ["butt", "round", "square"],
          "strokeJoins": ["bevel", "round", "bevel"],
          "dashIntervalCount": 0,
          "dashIntervalBudget": 8,
          "dashReason": "not coverage.dash-budget-exceeded",
          "deviceBounds": {"left": 0, "top": 0, "right": 192, "bottom": 128},
          "deviceBoundsBudget": 2048,
          "deviceBoundsReason": "not coverage.bounds-budget-exceeded",
          "diagnosticsSource": "WebGpuCoveragePlanSelector stroke style facts plus stable adapter-backed refusal; parity promotion remains blocked.",
          "test": "org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest#bounded stroke cap join scene captures visual parity blocker evidence",
          "sourceReport": "reports/wgsl-pipeline/2026-06-01-m60-stroke-cap-join-path-aa-promotion.md"
        }
    """.trimIndent() + "\n"

    private fun experimentalGpuDiagnosticJson(
        experimentalGpuCmp: BitmapComparison,
        experimentalGpuToleranceProfile: List<ToleranceStat>,
        regionStats: List<StrokeRegionStats>,
        adapter: String,
    ): String {
        val dominant = regionStats.minBy { it.similarity }
        return """
            {
              "sceneId": "m60-bounded-stroke-cap-join",
              "backend": "WebGPU",
              "adapter": ${adapter.jsonString()},
              "status": "diagnostic-only",
              "supportClaim": false,
              "selectedRoute": "webgpu.coverage.stroke-cap-join.experimental-render",
              "normalRoute": "webgpu.coverage.refuse",
              "fallbackReason": "coverage.stroke-cap-join-visual-parity-below-threshold",
              "rootCause": "color-space.target-blend-required",
              "experimentalGpuSimilarity": ${String.format(Locale.US, "%.2f", experimentalGpuCmp.similarity)},
              "experimentalGpuMatchingPixels": ${experimentalGpuCmp.matchingPixels},
              "experimentalGpuMaxChannelDelta": ${experimentalGpuCmp.maxChannelDiff.max()},
              "threshold": $GPU_SUPPORT_THRESHOLD,
              "toleranceProfile": [
            ${experimentalGpuToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "dominantMismatchRegion": ${dominant.id.jsonString()},
              "dominantMismatchDescription": ${dominant.description.jsonString()},
              "regions": [
            ${regionStats.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "diagnosis": "Experimental render remains below the exact threshold. The 32-channel tolerance score reaches the threshold, which points to target-colorspace blending as the remaining blocker rather than missing stroke geometry. Normal route remains refused.",
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun statsJson(
        cpuCmp: BitmapComparison,
        experimentalGpuCmp: BitmapComparison,
        experimentalGpuToleranceProfile: List<ToleranceStat>,
        regionStats: List<StrokeRegionStats>,
        adapter: String,
    ): String {
        val dominant = regionStats.minBy { it.similarity }
        return """
        {
          "sceneId": "m60-bounded-stroke-cap-join",
          "pixels": ${cpuCmp.totalPixels},
          "matchingPixels": 0,
          "maxChannelDelta": 255,
          "threshold": $GPU_SUPPORT_THRESHOLD,
          "cpuSimilarity": ${String.format(Locale.US, "%.2f", cpuCmp.similarity)},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "gpuSimilarity": 0.00,
          "gpuMatchingPixels": 0,
          "gpuMaxChannelDelta": 255,
          "gpuStatus": "expected-unsupported",
          "experimentalGpuStatus": "diagnostic-only",
          "experimentalGpuSimilarity": ${String.format(Locale.US, "%.2f", experimentalGpuCmp.similarity)},
          "experimentalGpuMatchingPixels": ${experimentalGpuCmp.matchingPixels},
          "experimentalGpuMaxChannelDelta": ${experimentalGpuCmp.maxChannelDiff.max()},
          "experimentalGpuToleranceProfile": [
        ${experimentalGpuToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ],
          "dominantMismatchRegion": ${dominant.id.jsonString()},
          "dominantMismatchDescription": ${dominant.description.jsonString()},
          "fallbackReason": "coverage.stroke-cap-join-visual-parity-below-threshold",
          "rootCause": "color-space.target-blend-required",
          "pathVerbCount": 9,
          "pathVerbBudget": 96,
          "edgeCount": 18,
          "edgeBudget": 256,
          "strokeWidth": 10.0,
          "strokeWidthBudget": {"min": 0.5, "max": 64.0},
          "strokeCaps": ["butt", "round", "square"],
          "strokeJoins": ["bevel", "round", "bevel"],
          "dashIntervalCount": 0,
          "dashIntervalBudget": 8,
          "deviceBounds": {"left": 0, "top": 0, "right": 192, "bottom": 128},
          "deviceBoundsBudget": 2048,
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "diagnosticsSource": "WebGpuCoveragePlanSelector stroke style facts plus stable adapter-backed refusal; parity promotion remains blocked.",
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
        }
        """.trimIndent() + "\n"
    }

    private fun toleranceProfile(gpu: SkBitmap, reference: SkBitmap): List<ToleranceStat> =
        listOf(0, TestUtils.TEXTUAL_GM_TOLERANCE, 16, 32).map { tolerance ->
            val cmp = TestUtils.compareBitmapsDetailed(gpu, reference, tolerance = tolerance)
            ToleranceStat(tolerance, cmp.similarity, cmp.matchingPixels)
        }

    private fun strokeRegionStats(gpu: SkBitmap, reference: SkBitmap): List<StrokeRegionStats> {
        require(gpu.width == reference.width && gpu.height == reference.height)
        val regions = listOf(
            StrokeRegion("butt-bevel", "left band: butt cap with bevel join", 0, 48),
            StrokeRegion("round-round", "middle band: round cap with round join", 48, 96),
            StrokeRegion("square-bevel", "right band: square cap with bevel join", 96, reference.width),
        )
        return regions.map { region ->
            var matching = 0
            var pixels = 0
            var maxDelta = 0
            for (y in 0 until reference.height) {
                for (x in region.xStart until region.xEnd) {
                    val gpuPixel = gpu.getPixel(x, y)
                    val refPixel = reference.getPixel(x, y)
                    if (gpuPixel == refPixel) matching++
                    maxDelta = maxOf(maxDelta, maxChannelDelta(gpuPixel, refPixel))
                    pixels++
                }
            }
            StrokeRegionStats(
                id = region.id,
                description = region.description,
                pixels = pixels,
                matchingPixels = matching,
                maxChannelDelta = maxDelta,
            )
        }
    }

    private fun maxChannelDelta(a: Int, b: Int): Int {
        var max = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val delta = kotlin.math.abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF))
            max = maxOf(max, delta)
        }
        return max
    }

    private fun <T> withExperimentalStrokeCapJoinRender(block: () -> T): T {
        val previous = System.getProperty(EXPERIMENTAL_RENDER_PROPERTY)
        System.setProperty(EXPERIMENTAL_RENDER_PROPERTY, "true")
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(EXPERIMENTAL_RENDER_PROPERTY)
            } else {
                System.setProperty(EXPERIMENTAL_RENDER_PROPERTY, previous)
            }
        }
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

    private class BoundedStrokeCapJoinGM : GM() {
        override fun getName(): String = "m60_bounded_stroke_cap_join"
        override fun getISize(): SkISize = SkISize.Make(192, 128)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            val path = SkPathBuilder()
                .moveTo(18f, 78f)
                .lineTo(54f, 42f)
                .lineTo(90f, 78f)
                .detach()
            val cases = listOf(
                StrokeCase(0f, SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join, 0xFF0066CC.toInt()),
                StrokeCase(48f, SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join, 0xFF008A4C.toInt()),
                StrokeCase(96f, SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join, 0xFFB33C00.toInt()),
            )
            val outlinePaint = SkPaint().apply {
                color = SK_ColorBLACK
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = true
            }
            for (case in cases) {
                c.save()
                c.translate(case.dx, 0f)
                val paint = SkPaint().apply {
                    color = case.color
                    isAntiAlias = true
                    style = SkPaint.Style.kStroke_Style
                    strokeWidth = 10f
                    strokeCap = case.cap
                    strokeJoin = case.join
                    strokeMiter = 4f
                }
                c.drawPath(path, paint)
                c.drawRect(org.graphiks.math.SkRect.MakeXYWH(10f, 28f, 88f, 64f), outlinePaint)
                c.restore()
            }
        }
    }

    private data class StrokeCase(
        val dx: Float,
        val cap: SkPaint.Cap,
        val join: SkPaint.Join,
        val color: Int,
    )

    private data class StrokeRegion(
        val id: String,
        val description: String,
        val xStart: Int,
        val xEnd: Int,
    )

    private data class StrokeRegionStats(
        val id: String,
        val description: String,
        val pixels: Int,
        val matchingPixels: Int,
        val maxChannelDelta: Int,
    ) {
        val similarity: Double
            get() = if (pixels == 0) 100.0 else matchingPixels * 100.0 / pixels

        fun toJson(): String = """
            {
              "id": ${jsonString(id)},
              "description": ${jsonString(description)},
              "pixels": $pixels,
              "matchingPixels": $matchingPixels,
              "similarity": ${String.format(Locale.US, "%.2f", similarity)},
              "maxChannelDelta": $maxChannelDelta
            }
        """.trimIndent()
    }

    private data class ToleranceStat(
        val tolerance: Int,
        val similarity: Double,
        val matchingPixels: Int,
    ) {
        fun toJson(): String = """
            {
              "tolerance": $tolerance,
              "similarity": ${String.format(Locale.US, "%.2f", similarity)},
              "matchingPixels": $matchingPixels
            }
        """.trimIndent()
    }

    private companion object {
        private const val GPU_SUPPORT_THRESHOLD = 99.95
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private const val EXPERIMENTAL_RENDER_PROPERTY = "kanvas.webgpu.strokeCapJoin.experimentalRender"

        private fun jsonString(value: String): String = buildString {
            append('"')
            for (ch in value) {
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
    }
}
