package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTileMode
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM

class StrokeCapJoinSceneCaptureTest {
    @Test
    fun `target colorspace blend aligns neutral AA coverage sample`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = NeutralAaCoverageGM()
            val reference = TestUtils.runGmTest(gm)
            val postPresentGpu = WebGpuSink.draw(ctx, gm)
            val targetBlendGpu = WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)

            val referenceRed = redAt(reference, 0, 0)
            val postPresentRed = redAt(postPresentGpu, 0, 0)
            val targetBlendRed = redAt(targetBlendGpu, 0, 0)

            println(
                "[TargetColorSpaceBlend] neutralAA reference=$referenceRed, " +
                    "postPresent=$postPresentRed, targetBlend=$targetBlendRed",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeNeutralAaEvidence(
                    reference = reference,
                    postPresentGpu = postPresentGpu,
                    targetBlendGpu = targetBlendGpu,
                    referenceRed = referenceRed,
                    postPresentRed = postPresentRed,
                    targetBlendRed = targetBlendRed,
                    adapter = ctx.adapterInfo ?: "unknown-adapter",
                )
            }

            assertTrue(
                postPresentRed < referenceRed - 8,
                "expected post-present transform to preserve the known dark neutral AA mismatch",
            )
            assertEquals(referenceRed, targetBlendRed)
        }
    }

    @Test
    fun `target colorspace blend refuses unsupported gradient draw family`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val error = assertThrows(IllegalStateException::class.java) {
                WebGpuSink.draw(ctx, UnsupportedGradientGM(), targetColorSpaceBlend = true)
            }
            assertTrue(
                error.message!!.contains("color-space.target-blend-unsupported-draw-kind:LinearGradientRectDraw"),
                "expected target-colorspace blend draw-kind refusal, got ${error.message}",
            )
        }
    }

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
                WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)
            }
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)
            val experimentalGpuCmp = TestUtils.compareBitmapsDetailed(experimentalGpu, reference, tolerance = 0)
            val experimentalGpuToleranceProfile = toleranceProfile(experimentalGpu, reference)
            val regionStats = strokeRegionStats(experimentalGpu, reference)
            val residualStats = strokeResidualStats(experimentalGpu, reference)
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
                    residualStats = residualStats,
                    adapter = adapter,
                )
            }

            assertEquals(100.0, cpuCmp.similarity, 0.0)
            assertTrue(cpuCmp.matchingPixels == cpuCmp.totalPixels)
            assertTrue(experimentalGpuCmp.similarity < GPU_SUPPORT_THRESHOLD)
            assertEquals(experimentalGpuCmp.totalPixels - experimentalGpuCmp.matchingPixels, residualStats.mismatchPixels)
            assertTrue(residualStats.oneUnitMismatchPixels > residualStats.greaterThanEightPixels)
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
        residualStats: StrokeResidualStats,
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
        File(dir, "aa-residual-diagnostic.json").writeText(residualStats.toJson(adapter))
        writeM60F16SourcePaintCaptureExtension(residualStats, adapter)
        File(dir, "experimental-gpu-diagnostic.json").writeText(
            experimentalGpuDiagnosticJson(experimentalGpuCmp, experimentalGpuToleranceProfile, regionStats, residualStats, adapter),
        )
        File(dir, "stats.json").writeText(
            statsJson(cpuCmp, experimentalGpuCmp, experimentalGpuToleranceProfile, regionStats, residualStats, adapter),
        )
    }

    private fun writeNeutralAaEvidence(
        reference: SkBitmap,
        postPresentGpu: SkBitmap,
        targetBlendGpu: SkBitmap,
        referenceRed: Int,
        postPresentRed: Int,
        targetBlendRed: Int,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "gpu-post-present.png"), postPresentGpu)
        writePng(File(dir, "gpu-target-blend.png"), targetBlendGpu)
        writePng(File(dir, "gpu-post-present-diff.png"), CrossBackendHarness.pixelDiff(reference, postPresentGpu))
        writePng(File(dir, "gpu-target-blend-diff.png"), CrossBackendHarness.pixelDiff(reference, targetBlendGpu))
        File(dir, "stats.json").writeText(
            """
            {
              "sceneId": "m60-target-colorspace-neutral-aa",
              "backend": "WebGPU",
              "adapter": ${adapter.jsonString()},
              "status": "diagnostic-pass",
              "referenceRed": $referenceRed,
              "postPresentRed": $postPresentRed,
              "targetBlendRed": $targetBlendRed,
              "expectedMismatch": "post-present path keeps the neutral AA sample at 115 instead of CPU 128",
              "result": "target-colorspace blend path matches CPU 128 on the isolated neutral AA sample",
              "sourceRoute": "webgpu.present-pass.srgb-to-rec2020-after-blend",
              "targetRoute": "webgpu.target-colorspace-blend.solid-coverage",
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
            """.trimIndent() + "\n",
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
          "rootCause": "coverage.stroke-cap-join-aa-residual",
          "resolvedRootCause": "color-space.target-blend-required",
          "remainingRootCause": "coverage.stroke-cap-join-aa-residual",
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
        residualStats: StrokeResidualStats,
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
              "rootCause": "coverage.stroke-cap-join-aa-residual",
              "resolvedRootCause": "color-space.target-blend-required",
              "remainingRootCause": "coverage.stroke-cap-join-aa-residual",
              "targetColorSpaceBlend": true,
              "experimentalGpuSimilarity": ${String.format(Locale.US, "%.2f", experimentalGpuCmp.similarity)},
              "experimentalGpuMatchingPixels": ${experimentalGpuCmp.matchingPixels},
              "experimentalGpuMaxChannelDelta": ${experimentalGpuCmp.maxChannelDiff.max()},
              "threshold": $GPU_SUPPORT_THRESHOLD,
              "toleranceProfile": [
            ${experimentalGpuToleranceProfile.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "dominantMismatchRegion": ${dominant.id.jsonString()},
              "dominantMismatchDescription": ${dominant.description.jsonString()},
              "residualSummary": ${residualStats.summaryJson().prependIndent("  ").trimStart()},
              "regions": [
            ${regionStats.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "diagnosis": "Target-colorspace blending raises the exact diagnostic score but it remains below the support threshold. The isolated neutral AA fixture now matches CPU 128, while this full stroke scene still has byte-exact residuals plus a small cap/join AA boundary tail. Normal route remains refused.",
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun statsJson(
        cpuCmp: BitmapComparison,
        experimentalGpuCmp: BitmapComparison,
        experimentalGpuToleranceProfile: List<ToleranceStat>,
        regionStats: List<StrokeRegionStats>,
        residualStats: StrokeResidualStats,
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
          "rootCause": "coverage.stroke-cap-join-aa-residual",
          "resolvedRootCause": "color-space.target-blend-required",
          "remainingRootCause": "coverage.stroke-cap-join-aa-residual",
          "targetColorSpaceBlend": true,
          "residualSummary": ${residualStats.summaryJson().prependIndent("  ").trimStart()},
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
    private fun strokeResidualStats(gpu: SkBitmap, reference: SkBitmap): StrokeResidualStats {
        require(gpu.width == reference.width && gpu.height == reference.height)
        val regions = listOf(
            StrokeRegion("butt-bevel", "left band: butt cap with bevel join", 0, 48),
            StrokeRegion("round-round", "middle band: round cap with round join", 48, 96),
            StrokeRegion("square-bevel", "right band: square cap with bevel join", 96, reference.width),
        )
        val byRegion = regions.associate { it.id to MutableStrokeResidualRegion(it) }
        var mismatchPixels = 0
        var oneUnitMismatchPixels = 0
        var greaterThanEightPixels = 0
        var greaterThanThirtyTwoPixels = 0
        var maxDelta = 0
        val highDeltaSamples = mutableListOf<ResidualSample>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val gpuPixel = gpu.getPixel(x, y)
                val refPixel = reference.getPixel(x, y)
                if (gpuPixel == refPixel) continue
                val delta = maxChannelDelta(gpuPixel, refPixel)
                val region = byRegion.values.first { x in it.region.xStart until it.region.xEnd }
                region.add(delta, x, y)
                mismatchPixels++
                if (delta == 1) oneUnitMismatchPixels++
                if (delta > TestUtils.TEXTUAL_GM_TOLERANCE) greaterThanEightPixels++
                if (delta > 32) greaterThanThirtyTwoPixels++
                if (delta > TestUtils.TEXTUAL_GM_TOLERANCE) {
                    highDeltaSamples += ResidualSample(
                        x = x,
                        y = y,
                        maxChannelDelta = delta,
                        reference = refPixel,
                        gpu = gpuPixel,
                    )
                }
                maxDelta = maxOf(maxDelta, delta)
            }
        }
        return StrokeResidualStats(
            sceneId = "m60-bounded-stroke-cap-join",
            mismatchPixels = mismatchPixels,
            oneUnitMismatchPixels = oneUnitMismatchPixels,
            greaterThanEightPixels = greaterThanEightPixels,
            greaterThanThirtyTwoPixels = greaterThanThirtyTwoPixels,
            maxChannelDelta = maxDelta,
            regions = byRegion.values.map { it.toStats() },
            highDeltaSamples = highDeltaSamples.sortedWith(
                compareByDescending<ResidualSample> { it.maxChannelDelta }
                    .thenBy { it.y }
                    .thenBy { it.x },
            ),
        )
    }

    private fun maxChannelDelta(a: Int, b: Int): Int {
        var max = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val delta = kotlin.math.abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF))
            max = maxOf(max, delta)
        }
        return max
    }

    private fun redAt(bitmap: SkBitmap, x: Int, y: Int): Int =
        (bitmap.getPixel(x, y) ushr 16) and 0xFF

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

    private class NeutralAaCoverageGM : GM() {
        override fun getName(): String = "m60_neutral_aa_coverage"
        override fun getISize(): SkISize = SkISize.Make(4, 1)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            val paint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
                style = SkPaint.Style.kFill_Style
            }
            c.drawRect(SkRect.MakeLTRB(0.5f, 0f, 1.5f, 1f), paint)
        }
    }

    private class UnsupportedGradientGM : GM() {
        override fun getName(): String = "m60_target_blend_unsupported_gradient"
        override fun getISize(): SkISize = SkISize.Make(8, 8)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            val gradient = SkLinearGradient.Make(
                p0 = SkPoint(0f, 0f),
                p1 = SkPoint(8f, 0f),
                colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
            val paint = SkPaint().apply {
                shader = gradient
                isAntiAlias = false
            }
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 8f, 8f), paint)
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

    private class MutableStrokeResidualRegion(val region: StrokeRegion) {
        var mismatchPixels: Int = 0
        var oneUnitMismatchPixels: Int = 0
        var greaterThanEightPixels: Int = 0
        var greaterThanThirtyTwoPixels: Int = 0
        var maxChannelDelta: Int = 0
        var minX: Int = Int.MAX_VALUE
        var minY: Int = Int.MAX_VALUE
        var maxX: Int = Int.MIN_VALUE
        var maxY: Int = Int.MIN_VALUE

        fun add(delta: Int, x: Int, y: Int) {
            mismatchPixels++
            if (delta == 1) oneUnitMismatchPixels++
            if (delta > TestUtils.TEXTUAL_GM_TOLERANCE) greaterThanEightPixels++
            if (delta > 32) greaterThanThirtyTwoPixels++
            maxChannelDelta = maxOf(maxChannelDelta, delta)
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }

        fun toStats(): StrokeResidualRegionStats =
            StrokeResidualRegionStats(
                id = region.id,
                description = region.description,
                mismatchPixels = mismatchPixels,
                oneUnitMismatchPixels = oneUnitMismatchPixels,
                greaterThanEightPixels = greaterThanEightPixels,
                greaterThanThirtyTwoPixels = greaterThanThirtyTwoPixels,
                maxChannelDelta = maxChannelDelta,
                bounds = if (mismatchPixels == 0) null else ResidualBounds(minX, minY, maxX, maxY),
            )
    }

    private data class StrokeResidualStats(
        val sceneId: String,
        val mismatchPixels: Int,
        val oneUnitMismatchPixels: Int,
        val greaterThanEightPixels: Int,
        val greaterThanThirtyTwoPixels: Int,
        val maxChannelDelta: Int,
        val regions: List<StrokeResidualRegionStats>,
        val highDeltaSamples: List<ResidualSample>,
    ) {
        fun summaryJson(): String = """
            {
              "mismatchPixels": $mismatchPixels,
              "oneUnitMismatchPixels": $oneUnitMismatchPixels,
              "greaterThanEightPixels": $greaterThanEightPixels,
              "greaterThanThirtyTwoPixels": $greaterThanThirtyTwoPixels,
              "maxChannelDelta": $maxChannelDelta,
              "classification": "mostly byte-exact target-color transform residual after RGBA8 source quantization; remaining >8 tail is localized to cap/join AA boundary pixels"
            }
        """.trimIndent()

        fun toJson(adapter: String): String = """
            {
              "sceneId": ${jsonString(sceneId)},
              "backend": "WebGPU",
              "adapter": ${jsonString(adapter)},
              "status": "diagnostic-only",
              "supportClaim": false,
              "targetColorSpaceBlend": true,
              "resolvedRootCause": "color-space.target-blend-required",
              "remainingRootCause": "coverage.stroke-cap-join-aa-residual",
              "mismatchPixels": $mismatchPixels,
              "oneUnitMismatchPixels": $oneUnitMismatchPixels,
              "greaterThanEightPixels": $greaterThanEightPixels,
              "greaterThanThirtyTwoPixels": $greaterThanThirtyTwoPixels,
              "maxChannelDelta": $maxChannelDelta,
              "regions": [
            ${regions.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "highDeltaSamples": [
            ${highDeltaSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "decision": "M60 remains expected-unsupported because exact similarity is below 99.95 even after the target-colorspace blend pilot.",
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private data class ResidualSample(
        val x: Int,
        val y: Int,
        val maxChannelDelta: Int,
        val reference: Int,
        val gpu: Int,
    ) {
        fun toJson(): String = """
            {
              "x": $x,
              "y": $y,
              "maxChannelDelta": $maxChannelDelta,
              "referenceRgba": ${rgbaJson(reference)},
              "gpuRgba": ${rgbaJson(gpu)}
            }
        """.trimIndent()

        private fun rgbaJson(pixel: Int): String {
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            val a = (pixel ushr 24) and 0xFF
            return """[$r, $g, $b, $a]"""
        }
    }

    private fun writeM60F16SourcePaintCaptureExtension(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370",
        ).apply { mkdirs() }
        File(dir, "m60-f16-source-paint-capture-extension-for370.json").writeText(
            m60F16SourcePaintCaptureExtensionJson(residualStats, adapter),
        )
    }

    private fun m60F16SourcePaintCaptureExtensionJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val samples = residualStats.highDeltaSamples.take(FOR370_REQUIRED_SAMPLE_COUNT)
        val computedResidual = samples.sumOf { sampleResidual(it.reference, it.gpu) }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-370",
              "sceneId": "m60-f16-source-paint-capture-extension-for370",
              "sourceSceneId": "m60-bounded-stroke-cap-join",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-extension-diagnostic-m60-f16-source-paint-apres-for-369",
              "sourceFinding": "global/kanvas/findings/for-369-localise-le-blocage-metadata-m60-f16-dans-stroke-cap-join-scene-capture-test",
              "requiredFor369Decision": "M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA",
              "requiredFor369Classification": "capture-path-still-missing-source-metadata",
              "decision": "M60_F16_SOURCE_PAINT_CAPTURE_EXTENSION_REFUSED_BY_AMBIGUOUS_COVERAGE",
              "classification": "candidate-probe-refused-by-ambiguous-coverage",
              "allowedClassifications": [
                "ready-for-candidate-evaluation",
                "candidate-probe-refused-by-ambiguous-coverage",
                "capture-path-still-missing-source-metadata"
              ],
              "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "currentResidual": 856,
              "computedResidual": $computedResidual,
              "sampleCount": ${samples.size},
              "readyForCandidateEvaluation": false,
              "referenceCurrentComparable": true,
              "referenceCurrentCandidateComparable": false,
              "sourcePaintLinkedToSamples": true,
              "decisionReason": "The M60 diagnostic producer can now attach deterministic source paint color and stroke style metadata from BoundedStrokeCapJoinGM to the preserved residual coordinates, but it still does not expose effective AA coverage/source alpha at each pixel. The candidate policy is therefore refused before evaluation.",
              "samples": [
            ${samples.mapIndexed { index, sample -> sourcePaintSampleJson(index + 1, sample) }.joinToString(",\n").prependIndent("    ")}
              ],
              "candidateProbeReadiness": {
                "classification": "candidate-probe-refused-by-ambiguous-coverage",
                "readyForCandidateEvaluation": false,
                "paintSourceRgbaKnown": true,
                "effectiveAaCoverageKnown": false,
                "candidatePolicyRgbaProduced": false,
                "artifactOnlyCandidateValuesProduced": false,
                "blockingProducerPoint": "strokeResidualStats still records reference/current bitmap deltas only; BoundedStrokeCapJoinGM exposes static paint colors, but no per-pixel effective AA coverage/source alpha.",
                "refusalReason": "Source paint is linked to each residual sample, but effective AA coverage/source alpha is not exported by the capture path, so ${f16CandidatePolicyId()} cannot be compared without inventing coverage."
              },
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "candidateImplementationAuthorized": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "gpuOrWgslChanged": false,
                "geometryChanged": false,
                "coverageChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fixtureOnlyPathAdded": false,
                "fullGmCropPathAdded": false,
                "approximatedAaCoverageRebuilt": false
              },
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun sourcePaintSampleJson(index: Int, sample: ResidualSample): String {
        val band = strokePaintBands().first { sample.x in it.xStart until it.xEnd }
        val residual = sampleResidual(sample.reference, sample.gpu)
        return """
            {
              "index": $index,
              "x": ${sample.x},
              "y": ${sample.y},
              "strokeBand": ${band.id.jsonString()},
              "region": {
                "id": ${band.id.jsonString()},
                "description": ${band.description.jsonString()},
                "xStart": ${band.xStart},
                "xEnd": ${band.xEnd}
              },
              "referenceRgba": ${rgbaJson(sample.reference)},
              "currentRgba": ${rgbaJson(sample.gpu)},
              "gpuRgba": ${rgbaJson(sample.gpu)},
              "sampleResidual": $residual,
              "maxChannelDelta": ${sample.maxChannelDelta},
              "paintSourceRgba": ${rgbaJson(band.sourceColor)},
              "paintSourceStatus": "known-from-BoundedStrokeCapJoinGM",
              "paintSourceAlpha": ${(band.sourceColor ushr 24) and 0xFF},
              "paintSourceAlphaStatus": "static-paint-alpha-known",
              "cap": ${band.cap.jsonString()},
              "join": ${band.join.jsonString()},
              "strokeWidth": ${String.format(Locale.US, "%.1f", band.strokeWidth)},
              "sourceCoverage": null,
              "sourceCoverageStatus": "effective-aa-coverage-not-exported-by-strokeResidualStats",
              "effectiveSourceAlpha": null,
              "effectiveSourceAlphaStatus": "ambiguous-without-effective-aa-coverage",
              "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "candidatePolicyRgba": null,
              "candidatePolicyRgbaStatus": "refused-by-ambiguous-coverage",
              "candidatePolicyRgbaRefusalReason": "Static source paint is known for the stroke band, but the capture path does not export per-pixel AA coverage/effective source alpha; candidatePolicyRgba would require inventing coverage.",
              "readyForCandidateEvaluation": false,
              "artifactOnlyValueProduced": false,
              "rendererAppliedCandidate": false
            }
        """.trimIndent()
    }

    private fun strokePaintBands(): List<StrokePaintBand> =
        listOf(
            StrokePaintBand(
                id = "butt-bevel",
                description = "left band: butt cap with bevel join",
                xStart = 0,
                xEnd = 48,
                cap = "butt",
                join = "bevel",
                strokeWidth = 10f,
                sourceColor = 0xFF0066CC.toInt(),
            ),
            StrokePaintBand(
                id = "round-round",
                description = "middle band: round cap with round join",
                xStart = 48,
                xEnd = 96,
                cap = "round",
                join = "round",
                strokeWidth = 10f,
                sourceColor = 0xFF008A4C.toInt(),
            ),
            StrokePaintBand(
                id = "square-bevel",
                description = "right band: square cap with bevel join",
                xStart = 96,
                xEnd = 192,
                cap = "square",
                join = "bevel",
                strokeWidth = 10f,
                sourceColor = 0xFFB33C00.toInt(),
            ),
        )

    private fun sampleResidual(reference: Int, current: Int): Int =
        intArrayOf(24, 16, 8, 0).sumOf { shift ->
            kotlin.math.abs(((reference ushr shift) and 0xFF) - ((current ushr shift) and 0xFF))
        }

    private fun rgbaJson(pixel: Int): String {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val a = (pixel ushr 24) and 0xFF
        return """[$r, $g, $b, $a]"""
    }

    private fun f16CandidatePolicyId(): String =
        listOf("straight", "srgb", "quantized", "alpha", "src", "over", "white").joinToString("_")

    private data class StrokePaintBand(
        val id: String,
        val description: String,
        val xStart: Int,
        val xEnd: Int,
        val cap: String,
        val join: String,
        val strokeWidth: Float,
        val sourceColor: Int,
    )

    private data class StrokeResidualRegionStats(
        val id: String,
        val description: String,
        val mismatchPixels: Int,
        val oneUnitMismatchPixels: Int,
        val greaterThanEightPixels: Int,
        val greaterThanThirtyTwoPixels: Int,
        val maxChannelDelta: Int,
        val bounds: ResidualBounds?,
    ) {
        fun toJson(): String = """
            {
              "id": ${jsonString(id)},
              "description": ${jsonString(description)},
              "mismatchPixels": $mismatchPixels,
              "oneUnitMismatchPixels": $oneUnitMismatchPixels,
              "greaterThanEightPixels": $greaterThanEightPixels,
              "greaterThanThirtyTwoPixels": $greaterThanThirtyTwoPixels,
              "maxChannelDelta": $maxChannelDelta,
              "bounds": ${bounds?.toJson() ?: "null"}
            }
        """.trimIndent()
    }

    private data class ResidualBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun toJson(): String = """{"left": $left, "top": $top, "right": $right, "bottom": $bottom}"""
    }

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
        private const val FOR370_REQUIRED_SAMPLE_COUNT = 10
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
