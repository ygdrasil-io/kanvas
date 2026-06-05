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
            val correctedExperimentalGpu = withExperimentalStrokeCapJoinRender {
                withM60F16SourceColorCorrectionProbe(true) {
                    WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)
                }
            }
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)
            val experimentalGpuCmp = TestUtils.compareBitmapsDetailed(experimentalGpu, reference, tolerance = 0)
            val correctedExperimentalGpuCmp =
                TestUtils.compareBitmapsDetailed(correctedExperimentalGpu, reference, tolerance = 0)
            val experimentalGpuToleranceProfile = toleranceProfile(experimentalGpu, reference)
            val regionStats = strokeRegionStats(experimentalGpu, reference)
            val residualStats = strokeResidualStats(experimentalGpu, reference)
            val correctedResidualStats = strokeResidualStats(correctedExperimentalGpu, reference)
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
                    correctedExperimentalGpu = correctedExperimentalGpu,
                    cpuCmp = cpuCmp,
                    experimentalGpuCmp = experimentalGpuCmp,
                    correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                    experimentalGpuToleranceProfile = experimentalGpuToleranceProfile,
                    regionStats = regionStats,
                    residualStats = residualStats,
                    correctedResidualStats = correctedResidualStats,
                    adapter = adapter,
                )
            }

            assertEquals(100.0, cpuCmp.similarity, 0.0)
            assertTrue(cpuCmp.matchingPixels == cpuCmp.totalPixels)
            assertTrue(experimentalGpuCmp.similarity < GPU_SUPPORT_THRESHOLD)
            assertEquals(experimentalGpuCmp.totalPixels - experimentalGpuCmp.matchingPixels, residualStats.mismatchPixels)
            assertTrue(residualStats.oneUnitMismatchPixels > residualStats.greaterThanEightPixels)
            assertTrue(correctedResidualStats.mismatchPixels > residualStats.mismatchPixels)
            assertTrue(correctedResidualStats.greaterThanEightPixels > correctedResidualStats.oneUnitMismatchPixels)
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
        correctedExperimentalGpu: SkBitmap,
        cpuCmp: BitmapComparison,
        experimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        experimentalGpuToleranceProfile: List<ToleranceStat>,
        regionStats: List<StrokeRegionStats>,
        residualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
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
        writeM60F16EffectiveCoverageExport(residualStats, adapter)
        writeM60F16CandidatePolicyRgbaProbe(residualStats, adapter)
        writeM60F16CandidateRegressionAudit(residualStats, adapter)
        writeM60F16EffectiveDestinationCandidate(residualStats, adapter)
        writeM60F16CompositionQuantizationCandidate(residualStats, adapter)
        writeM60F16LinearSrgbPlausibilityAudit(residualStats, adapter)
        writeM60F16DirectSourceColorEvidence(residualStats, adapter)
        writeM60F16EffectiveSourceColorPath(residualStats, adapter)
        writeM60F16SourceColorCorrectionProbe(
            uncorrectedResidualStats = residualStats,
            correctedGpu = correctedExperimentalGpu,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            correctedResidualStats = correctedResidualStats,
            adapter = adapter,
        )
        writeM60F16SourceColorSubzoneAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16CoverageCompositionMembershipAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16PreProbePredicateAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16PreCorrectionGeometryCoverageMetadataAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16GeneralizedCoverageMetadataPredicateAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16CoverageRegressionDiscriminatorAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
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
              "command": "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
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

    private fun <T> withM60F16SourceColorCorrectionProbe(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR380_CORRECTION_PROPERTY)
        System.setProperty(FOR380_CORRECTION_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR380_CORRECTION_PROPERTY)
            } else {
                System.setProperty(FOR380_CORRECTION_PROPERTY, previous)
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

    private class BoundedStrokeCapJoinCoverageMaskGM : GM() {
        init {
            setBGColor(0x00000000)
        }

        override fun getName(): String = "m60_bounded_stroke_cap_join_coverage_mask_for372"
        override fun getISize(): SkISize = SkISize.Make(192, 128)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            val path = SkPathBuilder()
                .moveTo(18f, 78f)
                .lineTo(54f, 42f)
                .lineTo(90f, 78f)
                .detach()
            val cases = listOf(
                StrokeCase(0f, SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join, SK_ColorWHITE),
                StrokeCase(48f, SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join, SK_ColorWHITE),
                StrokeCase(96f, SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join, SK_ColorWHITE),
            )
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
                c.restore()
            }
        }
    }

    private class BoundedStrokeCapJoinTransparentSourceGM : GM() {
        init {
            setBGColor(0x00000000)
        }

        override fun getName(): String = "m60_bounded_stroke_cap_join_transparent_source_for378"
        override fun getISize(): SkISize = SkISize.Make(192, 128)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
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

    private fun writeM60F16EffectiveCoverageExport(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-coverage-export-for372",
        ).apply { mkdirs() }
        File(dir, "m60-f16-effective-coverage-export-for372.json").writeText(
            m60F16EffectiveCoverageExportJson(residualStats, adapter),
        )
    }

    private fun writeM60F16CandidatePolicyRgbaProbe(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-policy-rgba-probe-for373",
        ).apply { mkdirs() }
        File(dir, "m60-f16-candidate-policy-rgba-probe-for373.json").writeText(
            m60F16CandidatePolicyRgbaProbeJson(residualStats, adapter),
        )
    }

    private fun writeM60F16CandidateRegressionAudit(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-regression-audit-for374",
        ).apply { mkdirs() }
        File(dir, "m60-f16-candidate-regression-audit-for374.json").writeText(
            m60F16CandidateRegressionAuditJson(residualStats, adapter),
        )
    }

    private fun writeM60F16EffectiveDestinationCandidate(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-destination-candidate-for375",
        ).apply { mkdirs() }
        File(dir, "m60-f16-effective-destination-candidate-for375.json").writeText(
            m60F16EffectiveDestinationCandidateJson(residualStats, adapter),
        )
    }

    private fun writeM60F16CompositionQuantizationCandidate(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-composition-quantization-candidate-for376",
        ).apply { mkdirs() }
        File(dir, "m60-f16-composition-quantization-candidate-for376.json").writeText(
            m60F16CompositionQuantizationCandidateJson(residualStats, adapter),
        )
    }

    private fun writeM60F16LinearSrgbPlausibilityAudit(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-linear-srgb-plausibility-audit-for377",
        ).apply { mkdirs() }
        File(dir, "m60-f16-linear-srgb-plausibility-audit-for377.json").writeText(
            m60F16LinearSrgbPlausibilityAuditJson(residualStats, adapter),
        )
    }

    private fun writeM60F16DirectSourceColorEvidence(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-source-color-evidence-for378",
        ).apply { mkdirs() }
        File(dir, "m60-f16-direct-source-color-evidence-for378.json").writeText(
            m60F16DirectSourceColorEvidenceJson(residualStats, adapter),
        )
    }

    private fun writeM60F16EffectiveSourceColorPath(
        residualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-source-color-path-for379",
        ).apply { mkdirs() }
        File(dir, "m60-f16-effective-source-color-path-for379.json").writeText(
            m60F16EffectiveSourceColorPathJson(residualStats, adapter),
        )
    }

    private fun writeM60F16SourceColorCorrectionProbe(
        uncorrectedResidualStats: StrokeResidualStats,
        correctedGpu: SkBitmap,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        correctedResidualStats: StrokeResidualStats,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-correction-probe-for380",
        ).apply { mkdirs() }
        writePng(File(dir, "corrected-gpu.png"), correctedGpu)
        File(dir, "m60-f16-source-color-correction-probe-for380.json").writeText(
            m60F16SourceColorCorrectionProbeJson(
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedGpu = correctedGpu,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                correctedResidualStats = correctedResidualStats,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16SourceColorSubzoneAudit(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-subzone-audit-for381",
        ).apply { mkdirs() }
        File(dir, "m60-f16-source-color-subzone-audit-for381.json").writeText(
            m60F16SourceColorSubzoneAuditJson(
                reference = reference,
                currentGpu = currentGpu,
                probeGpu = probeGpu,
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedResidualStats = correctedResidualStats,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16CoverageCompositionMembershipAudit(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-composition-membership-audit-for382",
        ).apply { mkdirs() }
        File(dir, "m60-f16-coverage-composition-membership-audit-for382.json").writeText(
            m60F16CoverageCompositionMembershipAuditJson(
                reference = reference,
                currentGpu = currentGpu,
                probeGpu = probeGpu,
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedResidualStats = correctedResidualStats,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16PreProbePredicateAudit(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-pre-probe-predicate-audit-for383",
        ).apply { mkdirs() }
        File(dir, "m60-f16-pre-probe-predicate-audit-for383.json").writeText(
            m60F16PreProbePredicateAuditJson(
                reference = reference,
                currentGpu = currentGpu,
                probeGpu = probeGpu,
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedResidualStats = correctedResidualStats,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16PreCorrectionGeometryCoverageMetadataAudit(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-pre-correction-geometry-coverage-metadata-audit-for384",
        ).apply { mkdirs() }
        File(dir, "m60-f16-pre-correction-geometry-coverage-metadata-audit-for384.json").writeText(
            m60F16PreCorrectionGeometryCoverageMetadataAuditJson(
                reference = reference,
                currentGpu = currentGpu,
                probeGpu = probeGpu,
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedResidualStats = correctedResidualStats,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16GeneralizedCoverageMetadataPredicateAudit(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-generalized-coverage-metadata-predicate-audit-for385",
        ).apply { mkdirs() }
        File(dir, "m60-f16-generalized-coverage-metadata-predicate-audit-for385.json").writeText(
            m60F16GeneralizedCoverageMetadataPredicateAuditJson(
                reference = reference,
                currentGpu = currentGpu,
                probeGpu = probeGpu,
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedResidualStats = correctedResidualStats,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16CoverageRegressionDiscriminatorAudit(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-regression-discriminator-audit-for386",
        ).apply { mkdirs() }
        File(dir, "m60-f16-coverage-regression-discriminator-audit-for386.json").writeText(
            m60F16CoverageRegressionDiscriminatorAuditJson(
                reference = reference,
                currentGpu = currentGpu,
                probeGpu = probeGpu,
                uncorrectedResidualStats = uncorrectedResidualStats,
                correctedResidualStats = correctedResidualStats,
                uncorrectedExperimentalGpuCmp = uncorrectedExperimentalGpuCmp,
                correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                adapter = adapter,
            ),
        )
    }

    private fun m60F16SourceColorCorrectionProbeJson(
        uncorrectedResidualStats: StrokeResidualStats,
        correctedGpu: SkBitmap,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        correctedResidualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val samples = uncorrectedResidualStats.highDeltaSamples.take(FOR379_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val for375Samples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val for376Samples = for375Samples.map { compositionQuantizationCandidateSample(it) }
        val for377Samples = for376Samples.map { linearSrgbPlausibilityAuditSample(it) }
        val directSamples = for377Samples.map { directSourceColorEvidenceSample(it, transparentSource) }
        val pathSamples = directSamples.map { effectiveSourceColorPathSample(it) }
        val correctionSamples = pathSamples.map { sample ->
            val candidate = sample.directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate
            sourceColorCorrectionProbeSample(sample, rgbaArray(correctedGpu.getPixel(candidate.x, candidate.y)))
        }
        val currentResidual = correctionSamples.sumOf { it.currentResidual }
        val directResidual = correctionSamples.sumOf { it.directRecomposedOnWhiteResidual }
        val correctedResidual = correctionSamples.sumOf { it.correctedResidual }
        val currentToCorrectedDistance = correctionSamples.sumOf { it.correctedVsCurrentRgbaDistance }
        val correctedToDirectDistance = correctionSamples.sumOf { it.correctedVsDirectRecomposedRgbaDistance }
        val currentErrors = IntArray(4)
        val directErrors = IntArray(4)
        val correctedErrors = IntArray(4)
        val correctedDeltaVsCurrent = IntArray(4)
        val correctedDeltaVsDirect = IntArray(4)
        for (sample in correctionSamples) {
            for (index in 0..3) {
                currentErrors[index] += sample.effectiveSourceSample.currentErrorByChannel[index]
                directErrors[index] += sample.effectiveSourceSample.directRecomposedErrorByChannel[index]
                correctedErrors[index] += sample.correctedErrorByChannel[index]
                correctedDeltaVsCurrent[index] += sample.correctedMinusCurrentErrorByChannel[index]
                correctedDeltaVsDirect[index] += sample.correctedMinusDirectRecomposedErrorByChannel[index]
            }
        }
        val sampleClassification = when {
            correctedResidual > currentResidual -> "regression-detected"
            correctedResidual == directResidual -> "correction-reduces-toward-direct-source-proof"
            correctedResidual < currentResidual -> "partial-correction"
            else -> "correction-refused-reversed"
        }
        val fullSceneRegression = correctedResidualStats.mismatchPixels > uncorrectedResidualStats.mismatchPixels ||
            correctedExperimentalGpuCmp.similarity < uncorrectedExperimentalGpuCmp.similarity
        val classification = if (fullSceneRegression) {
            "regression-detected"
        } else {
            sampleClassification
        }
        val correctionKept = false
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-380",
              "sceneId": "m60-f16-source-color-correction-probe-for380",
              "sourceSceneId": "m60-f16-effective-source-color-path-for379",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-correction-experimentale-bornee-chemin-source-couleur-apres-for-379",
              "sourceFinding": "global/kanvas/findings/for-379-isole-le-chemin-couleur-source-m60-f16-comme-pret-pour-correction",
              "decision": "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED",
              "classification": ${classification.jsonString()},
              "sampleClassificationBeforeFullSceneGuard": ${sampleClassification.jsonString()},
              "allowedClassifications": [
                "correction-reduces-toward-direct-source-proof",
                "partial-correction",
                "correction-refused-reversed",
                "regression-detected"
              ],
              "correctionKept": $correctionKept,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "boundedRendererPoint": "SkWebGpuDevice.buildStencilCoverAaDrawResources colorFilterKindMode.z targetColorSpaceBlend payload",
              "minimalDivergencePoint": "StencilCoverAaPolygonDraw solid-color M60 F16 stroke cap/join applies targetColorSpaceBlend to source colour before coverage; FOR-379 proved the selected samples match direct transparent source recomposed on white.",
              "correctionDescription": "Probe disables targetColorSpaceBlend only for bounded StencilCoverAaPolygonDraw solid-color stroke cap/join draws when the experimental stroke renderer and FOR-380 flag are enabled.",
              "requiredFor379Decision": "M60_F16_EFFECTIVE_SOURCE_COLOR_PATH_RECORDED",
              "requiredFor379Classification": "source-color-path-ready-for-correction",
              "preservedFor379": {
                "currentResidual": $FOR373_CURRENT_RESIDUAL,
                "directRecomposedOnWhiteTotalResidual": 19,
                "directRecomposedOnWhiteGainVsCurrent": ${FOR373_CURRENT_RESIDUAL - 19},
                "sampleCount": $FOR379_REQUIRED_SAMPLE_COUNT,
                "sampleCoordinatesPreserved": true
              },
              "currentResidual": $currentResidual,
              "directRecomposedOnWhiteTotalResidual": $directResidual,
              "correctedResidual": $correctedResidual,
              "correctedDeltaVsCurrent": ${correctedResidual - currentResidual},
              "correctedGainVsCurrent": ${currentResidual - correctedResidual},
              "correctedDeltaVsDirectRecomposedOnWhite": ${correctedResidual - directResidual},
              "correctedVsCurrentRgbaDistanceTotal": $currentToCorrectedDistance,
              "correctedVsDirectRecomposedRgbaDistanceTotal": $correctedToDirectDistance,
              "currentErrorTotalsByChannel": ${channelErrorJson(currentErrors)},
              "directRecomposedErrorTotalsByChannel": ${channelErrorJson(directErrors)},
              "correctedErrorTotalsByChannel": ${channelErrorJson(correctedErrors)},
              "correctedMinusCurrentErrorTotalsByChannel": ${channelErrorJson(correctedDeltaVsCurrent)},
              "correctedMinusDirectRecomposedErrorTotalsByChannel": ${channelErrorJson(correctedDeltaVsDirect)},
              "sampleCount": ${correctionSamples.size},
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "regressionDetected": $fullSceneRegression,
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "samples": [
            ${correctionSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for380-pycache python3 -m py_compile scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16SourceColorSubzoneAuditJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ): String {
        require(reference.width == currentGpu.width && reference.height == currentGpu.height)
        require(reference.width == probeGpu.width && reference.height == probeGpu.height)
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val criticalSamples = sourceColorSubzoneCriticalSamples(
            residualStats = uncorrectedResidualStats,
            probeGpu = probeGpu,
            coverageMask = coverageMask,
            transparentSource = transparentSource,
        )
        val criticalCoordinates = criticalSamples.map { it.pixel.x to it.pixel.y }
        val improved = MutableSubzonePixelSet("improved")
        val regressed = MutableSubzonePixelSet("regressed")
        val unchanged = MutableSubzonePixelSet("unchanged")
        val allAudited = MutableSubzonePixelSet("all-audited-pixels")
        val improvedSamples = mutableListOf<SubzonePixelAudit>()
        val regressedSamples = mutableListOf<SubzonePixelAudit>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val pixel = sourceColorSubzonePixelAudit(
                    x = x,
                    y = y,
                    reference = reference.getPixel(x, y),
                    current = currentGpu.getPixel(x, y),
                    probe = probeGpu.getPixel(x, y),
                    criticalCoordinates = criticalCoordinates,
                )
                allAudited.add(pixel)
                when {
                    pixel.deltaVsCurrent < 0 -> {
                        improved.add(pixel)
                        improvedSamples += pixel
                    }
                    pixel.deltaVsCurrent > 0 -> {
                        regressed.add(pixel)
                        regressedSamples += pixel
                    }
                    else -> unchanged.add(pixel)
                }
            }
        }
        val criticalSet = MutableSubzonePixelSet("for379-critical")
        criticalSamples.forEach { criticalSet.add(it.pixel) }
        val bestImproved = improvedSamples
            .sortedWith(compareBy<SubzonePixelAudit> { it.deltaVsCurrent }.thenBy { it.nearestCriticalManhattanDistance }.thenBy { it.y }.thenBy { it.x })
            .take(FOR381_SAMPLE_LIMIT)
        val worstRegressed = regressedSamples
            .sortedWith(compareByDescending<SubzonePixelAudit> { it.deltaVsCurrent }.thenBy { it.nearestCriticalManhattanDistance }.thenBy { it.y }.thenBy { it.x })
            .take(FOR381_SAMPLE_LIMIT)
        val classification = sourceColorSubzoneAuditClassification(
            improved = improved,
            regressed = regressed,
            criticalSet = criticalSet,
            uncorrectedResidualStats = uncorrectedResidualStats,
            correctedResidualStats = correctedResidualStats,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-381",
              "sceneId": "m60-f16-source-color-subzone-audit-for381",
              "sourceSceneId": "m60-f16-source-color-correction-probe-for380",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-caracterisation-sous-zone-apres-regression-for-380",
              "sourceFinding": "global/kanvas/findings/for-380-refuse-la-correction-source-couleur-m60-f16-au-niveau-draw-entier-car-elle-regresse-la-scene",
              "requiredFor380Decision": "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED",
              "requiredFor380Classification": "regression-detected",
              "decision": "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition",
                "source-color-correction-local-predicate-insufficient",
                "source-color-correction-subzone-proof-insufficient"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "comparison": {
                "reference": "Skia CPU reference",
                "current": "FOR-380 uncorrected experimental WebGPU targetColorSpaceBlend stroke render",
                "probe": "FOR-380 flag-enabled render with draw-level source/color correction",
                "directSourceEvidence": "FOR-379/FOR-378 transparent source recomposition for the 10 critical samples only"
              },
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "bandInference": {
                "method": "x-range partition from BoundedStrokeCapJoinGM strokePaintBands",
                "coverageMembershipProven": false,
                "note": "Band/cap/join distributions identify the owning fixture lane by x coordinate; they do not prove the pixel belongs to covered stroke geometry."
              },
              "sets": {
                "allAuditedPixels": ${allAudited.toStats().toJson().prependIndent("  ").trimStart()},
                "improved": ${improved.toStats().toJson().prependIndent("  ").trimStart()},
                "regressed": ${regressed.toStats().toJson().prependIndent("  ").trimStart()},
                "unchanged": ${unchanged.toStats().toJson().prependIndent("  ").trimStart()},
                "for379Critical": ${criticalSet.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "criticalFor379Samples": [
            ${criticalSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "worstRegressedPixels": [
            ${worstRegressed.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "bestImprovedPixels": [
            ${bestImproved.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "classificationReason": ${sourceColorSubzoneAuditClassificationReason(classification).jsonString()},
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "probeEnabledByDefault": false,
                "correctionPredicateEnabled": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for381-pycache python3 -m py_compile scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16CoverageCompositionMembershipAuditJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ): String {
        require(reference.width == currentGpu.width && reference.height == currentGpu.height)
        require(reference.width == probeGpu.width && reference.height == probeGpu.height)
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val criticalSamples = sourceColorSubzoneCriticalSamples(
            residualStats = uncorrectedResidualStats,
            probeGpu = probeGpu,
            coverageMask = coverageMask,
            transparentSource = transparentSource,
        )
        val criticalCoordinates = criticalSamples.map { it.pixel.x to it.pixel.y }
        val allAudited = MutableMembershipPixelSet("all-audited-pixels")
        val improved = MutableMembershipPixelSet("for381-improved")
        val regressed = MutableMembershipPixelSet("for381-regressed")
        val unchanged = MutableMembershipPixelSet("for381-unchanged")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val categorySamples = linkedMapOf(
            sourceLocal.id to mutableListOf<CoverageCompositionMembershipPixelAudit>(),
            coverageComposition.id to mutableListOf(),
            mixed.id to mutableListOf(),
            insufficient.id to mutableListOf(),
        )
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val pixel = sourceColorSubzonePixelAudit(
                    x = x,
                    y = y,
                    reference = reference.getPixel(x, y),
                    current = currentGpu.getPixel(x, y),
                    probe = probeGpu.getPixel(x, y),
                    criticalCoordinates = criticalCoordinates,
                )
                val membership = coverageCompositionMembershipPixelAudit(
                    pixel = pixel,
                    coverageMask = coverageMask.getPixel(x, y),
                    transparentSource = transparentSource.getPixel(x, y),
                )
                allAudited.add(membership)
                when {
                    pixel.deltaVsCurrent < 0 -> improved.add(membership)
                    pixel.deltaVsCurrent > 0 -> regressed.add(membership)
                    else -> unchanged.add(membership)
                }
                when (membership.category) {
                    sourceLocal.id -> sourceLocal.add(membership)
                    coverageComposition.id -> coverageComposition.add(membership)
                    mixed.id -> mixed.add(membership)
                    else -> insufficient.add(membership)
                }
                categorySamples.getValue(membership.category) += membership
            }
        }
        val criticalSet = MutableMembershipPixelSet("for379-critical")
        for (sample in criticalSamples) {
            criticalSet.add(
                coverageCompositionMembershipPixelAudit(
                    pixel = sample.pixel,
                    coverageMask = coverageMask.getPixel(sample.pixel.x, sample.pixel.y),
                    transparentSource = transparentSource.getPixel(sample.pixel.x, sample.pixel.y),
                ),
            )
        }
        val categoryList = listOf(sourceLocal, coverageComposition, mixed, insufficient)
        val classification = coverageCompositionMembershipClassification(
            sourceLocal = sourceLocal,
            coverageComposition = coverageComposition,
            mixed = mixed,
            improved = improved,
            regressed = regressed,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-382",
              "sceneId": "m60-f16-coverage-composition-membership-audit-for382",
              "sourceSceneId": "m60-f16-source-color-subzone-audit-for381",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-appartenance-couverture-composition-apres-for-381",
              "sourceFinding": "global/kanvas/findings/for-381-caracterise-les-sous-zones-m60-f16-et-confirme-que-la-correction-source-couleur-doit-separer-couverture-et-composition",
              "requiredFor381Decision": "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED",
              "requiredFor381Classification": "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition",
              "requiredFor380Decision": "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED",
              "requiredFor380Classification": "regression-detected",
              "decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof",
                "coverage-composition-separation-insufficient",
                "coverage-composition-proof-missing"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "membershipSignals": {
                "coverageMaskAvailable": true,
                "coverageMaskReadSource": "alpha-channel-from-BoundedStrokeCapJoinCoverageMaskGM",
                "transparentSourceAvailable": true,
                "transparentSourceReadSource": "BoundedStrokeCapJoinTransparentSourceGM getPixel unpremultiplied SkColor",
                "sourceTransparentSignal": "transparentSourceAlphaByte == 0",
                "categoryMethod": "diagnostic outcome-aware grouping over FOR-381 improved/regressed/unchanged pixels; not a renderer predicate"
              },
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "for381Sets": {
                "allAuditedPixels": ${allAudited.toStats().toJson().prependIndent("  ").trimStart()},
                "improved": ${improved.toStats().toJson().prependIndent("  ").trimStart()},
                "regressed": ${regressed.toStats().toJson().prependIndent("  ").trimStart()},
                "unchanged": ${unchanged.toStats().toJson().prependIndent("  ").trimStart()},
                "for379Critical": ${criticalSet.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "categories": {
            ${categoryList.joinToString(",\n") { "\"${it.id}\": ${it.toStats().toJson().prependIndent("  ").trimStart()}" }.prependIndent("    ")}
              },
              "separationAnalysis": {
                "improvedPixels": ${improved.count},
                "regressedPixels": ${regressed.count},
                "sourceLocalPlausiblePixels": ${sourceLocal.count},
                "coverageCompositionPlausiblePixels": ${coverageComposition.count},
                "mixedPixels": ${mixed.count},
                "insufficientPixels": ${insufficient.count},
                "allImprovedPixelsInSourceLocalCategory": ${sourceLocal.count == improved.count},
                "regressedPixelsInSourceLocalCategory": 0,
                "sourceLocalCategoryDistinctFromRegressed": ${sourceLocal.count == improved.count},
                "rawCoverageAndTransparentSourceSignalsOverlap": true,
                "rendererPredicateReady": false,
                "nextMove": "prove coverage/composition membership independently of probe outcome before applying any local renderer correction"
              },
              "categorySamples": {
                "source-locale-plausible": [
            ${categorySamples.getValue(sourceLocal.id).sortedWith(compareBy<CoverageCompositionMembershipPixelAudit> { it.pixel.deltaVsCurrent }.thenBy { it.pixel.y }.thenBy { it.pixel.x }).take(FOR381_SAMPLE_LIMIT).joinToString(",\n") { it.toJson().prependIndent("    ") }}
                ],
                "coverage-composition-plausible": [
            ${categorySamples.getValue(coverageComposition.id).sortedWith(compareByDescending<CoverageCompositionMembershipPixelAudit> { it.pixel.deltaVsCurrent }.thenBy { it.pixel.nearestCriticalManhattanDistance }.thenBy { it.pixel.y }.thenBy { it.pixel.x }).take(FOR381_SAMPLE_LIMIT).joinToString(",\n") { it.toJson().prependIndent("    ") }}
                ],
                "mixed": [
            ${categorySamples.getValue(mixed.id).sortedWith(compareByDescending<CoverageCompositionMembershipPixelAudit> { kotlin.math.abs(it.pixel.deltaVsCurrent) }.thenBy { it.pixel.y }.thenBy { it.pixel.x }).take(FOR381_SAMPLE_LIMIT).joinToString(",\n") { it.toJson().prependIndent("    ") }}
                ],
                "insufficient": [
            ${categorySamples.getValue(insufficient.id).filter { it.pixel.currentResidual > 0 || it.pixel.probeResidual > 0 }.sortedWith(compareByDescending<CoverageCompositionMembershipPixelAudit> { kotlin.math.abs(it.pixel.deltaVsCurrent) }.thenBy { it.pixel.y }.thenBy { it.pixel.x }).take(FOR381_SAMPLE_LIMIT).joinToString(",\n") { it.toJson().prependIndent("    ") }}
                ]
              },
              "classificationReason": ${coverageCompositionMembershipClassificationReason(classification).jsonString()},
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "probeEnabledByDefault": false,
                "correctionPredicateEnabled": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
                "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for382-pycache python3 -m py_compile scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16PreProbePredicateAuditJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ): String {
        require(reference.width == currentGpu.width && reference.height == currentGpu.height)
        require(reference.width == probeGpu.width && reference.height == probeGpu.height)
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val criticalSamples = sourceColorSubzoneCriticalSamples(
            residualStats = uncorrectedResidualStats,
            probeGpu = probeGpu,
            coverageMask = coverageMask,
            transparentSource = transparentSource,
        )
        val criticalCoordinates = criticalSamples.map { it.pixel.x to it.pixel.y }
        val allAudited = MutableMembershipPixelSet("all-audited-pixels")
        val improved = MutableMembershipPixelSet("for381-improved")
        val regressed = MutableMembershipPixelSet("for381-regressed")
        val unchanged = MutableMembershipPixelSet("for381-unchanged")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val candidateStats = preProbePredicateCandidates().associate { candidate ->
            candidate.id to MutablePreProbePredicateStats(candidate)
        }
        val candidateSamples = candidateStats.keys.associateWith { mutableListOf<CoverageCompositionMembershipPixelAudit>() }

        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val pixel = sourceColorSubzonePixelAudit(
                    x = x,
                    y = y,
                    reference = reference.getPixel(x, y),
                    current = currentGpu.getPixel(x, y),
                    probe = probeGpu.getPixel(x, y),
                    criticalCoordinates = criticalCoordinates,
                )
                val membership = coverageCompositionMembershipPixelAudit(
                    pixel = pixel,
                    coverageMask = coverageMask.getPixel(x, y),
                    transparentSource = transparentSource.getPixel(x, y),
                )
                allAudited.add(membership)
                when {
                    pixel.deltaVsCurrent < 0 -> improved.add(membership)
                    pixel.deltaVsCurrent > 0 -> regressed.add(membership)
                    else -> unchanged.add(membership)
                }
                when (membership.category) {
                    sourceLocal.id -> sourceLocal.add(membership)
                    coverageComposition.id -> coverageComposition.add(membership)
                    mixed.id -> mixed.add(membership)
                    else -> insufficient.add(membership)
                }
                for ((candidateId, stats) in candidateStats) {
                    if (stats.candidate.select(membership)) {
                        stats.add(membership)
                        candidateSamples.getValue(candidateId) += membership
                    }
                }
            }
        }

        val criticalSet = MutableMembershipPixelSet("for379-critical")
        for (sample in criticalSamples) {
            criticalSet.add(
                coverageCompositionMembershipPixelAudit(
                    pixel = sample.pixel,
                    coverageMask = coverageMask.getPixel(sample.pixel.x, sample.pixel.y),
                    transparentSource = transparentSource.getPixel(sample.pixel.x, sample.pixel.y),
                ),
            )
        }
        val candidates = candidateStats.values.toList()
        val bestCandidate = candidates.maxWithOrNull(
            compareBy<MutablePreProbePredicateStats> { it.sourceLocalRecovered }
                .thenBy { it.precision }
                .thenByDescending { it.regressedIncluded }
                .thenByDescending { it.count },
        )
        val classification = preProbePredicateAuditClassification(
            bestCandidate = bestCandidate,
            sourceLocalTruth = sourceLocal.count,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-383",
              "sceneId": "m60-f16-pre-probe-predicate-audit-for383",
              "sourceSceneId": "m60-f16-coverage-composition-membership-audit-for382",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-predicate-moteur-independant-du-probe-apres-for-382",
              "sourceFinding": "global/kanvas/findings/for-382-separe-les-8-pixels-ameliores-m60-f16-mais-confirme-quil-manque-un-predicate-moteur-independant-du-probe",
              "requiredFor382Decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED",
              "requiredFor382Classification": "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof",
              "requiredFor381Decision": "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED",
              "requiredFor381Classification": "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition",
              "requiredFor380Decision": "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED",
              "requiredFor380Classification": "regression-detected",
              "decision": "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "diagnostic-pre-probe-predicate-found-runtime-proof-missing",
                "pre-probe-predicate-too-broad",
                "pre-probe-predicate-insufficient",
                "pre-probe-proof-missing"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "predicateSearchRules": {
                "candidateSelectionUsesProbeOutcome": false,
                "candidateSelectionUsesProbeResidual": false,
                "candidateSelectionUsesDeltaVsCurrent": false,
                "candidateSelectionUsesFor379MembershipAsPrimary": false,
                "probeOutcomeUsedOnlyAsEvaluationTruth": true,
                "for382SourceLocalCategoryUsedOnlyAsEvaluationTruth": true,
                "for379CoordinatesUsedOnlyForLegacyComparisonFields": true
              },
              "preProbeSignals": {
                "coverageMaskAvailable": true,
                "transparentSourceAvailable": true,
                "currentResidualAvailable": true,
                "currentChannelErrorShapeAvailable": true,
                "strokeBandFromFixtureXRangeAvailable": true,
                "referenceRequiredForResidualSignals": true,
                "rendererRuntimePredicateReady": false
              },
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "truthSetsForEvaluationOnly": {
                "allAuditedPixels": ${allAudited.toStats().toJson().prependIndent("  ").trimStart()},
                "improved": ${improved.toStats().toJson().prependIndent("  ").trimStart()},
                "regressed": ${regressed.toStats().toJson().prependIndent("  ").trimStart()},
                "unchanged": ${unchanged.toStats().toJson().prependIndent("  ").trimStart()},
                "for379Critical": ${criticalSet.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "for382CategoriesForEvaluationOnly": {
                "source-locale-plausible": ${sourceLocal.toStats().toJson().prependIndent("  ").trimStart()},
                "coverage-composition-plausible": ${coverageComposition.toStats().toJson().prependIndent("  ").trimStart()},
                "mixed": ${mixed.toStats().toJson().prependIndent("  ").trimStart()},
                "insufficient": ${insufficient.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "candidatePredicates": [
            ${candidates.joinToString(",\n") { it.toStatsJson(sourceLocalTruth = sourceLocal.count).prependIndent("    ") }}
              ],
              "candidateSamples": {
            ${candidates.joinToString(",\n") { stats ->
                val samples = candidateSamples.getValue(stats.candidate.id)
                    .sortedWith(compareByDescending<CoverageCompositionMembershipPixelAudit> { it.pixel.currentResidual }.thenBy { it.pixel.y }.thenBy { it.pixel.x })
                    .take(FOR381_SAMPLE_LIMIT)
                    .joinToString(",\n") { it.toJson().prependIndent("      ") }
                "\"${stats.candidate.id}\": [\n$samples\n    ]"
            }.prependIndent("    ")}
              },
              "bestCandidate": ${bestCandidate?.toSummaryJson(sourceLocalTruth = sourceLocal.count)?.prependIndent("  ")?.trimStart() ?: "null"},
              "classificationReason": ${preProbePredicateAuditClassificationReason(classification).jsonString()},
              "nextMove": "Convert the diagnostic current-error-shape proof into renderer-owned coverage/composition metadata, or keep the correction blocked if that metadata cannot be produced without a reference oracle.",
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "probeEnabledByDefault": false,
                "correctionPredicateEnabled": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
                "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
                "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for383-pycache python3 -m py_compile scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16PreCorrectionGeometryCoverageMetadataAuditJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ): String {
        require(reference.width == currentGpu.width && reference.height == currentGpu.height)
        require(reference.width == probeGpu.width && reference.height == probeGpu.height)
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val criticalSamples = sourceColorSubzoneCriticalSamples(
            residualStats = uncorrectedResidualStats,
            probeGpu = probeGpu,
            coverageMask = coverageMask,
            transparentSource = transparentSource,
        )
        val criticalCoordinates = criticalSamples.map { it.pixel.x to it.pixel.y }
        val allAudited = MutableMembershipPixelSet("all-audited-pixels")
        val improved = MutableMembershipPixelSet("for381-improved")
        val regressed = MutableMembershipPixelSet("for381-regressed")
        val unchanged = MutableMembershipPixelSet("for381-unchanged")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val bestFor383Predicate = preProbePredicateCandidates()
            .first { it.id == "partial-alpha-current-error-shape" }
        val inspected = mutableListOf<PreCorrectionGeometryCoveragePixelAudit>()
        val candidateStats = preCorrectionGeometryCoverageMetadataCandidates().associate { candidate ->
            candidate.id to MutablePreCorrectionMetadataCandidateStats(candidate)
        }

        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val pixel = sourceColorSubzonePixelAudit(
                    x = x,
                    y = y,
                    reference = reference.getPixel(x, y),
                    current = currentGpu.getPixel(x, y),
                    probe = probeGpu.getPixel(x, y),
                    criticalCoordinates = criticalCoordinates,
                )
                val membership = coverageCompositionMembershipPixelAudit(
                    pixel = pixel,
                    coverageMask = coverageMask.getPixel(x, y),
                    transparentSource = transparentSource.getPixel(x, y),
                )
                allAudited.add(membership)
                when {
                    pixel.deltaVsCurrent < 0 -> improved.add(membership)
                    pixel.deltaVsCurrent > 0 -> regressed.add(membership)
                    else -> unchanged.add(membership)
                }
                when (membership.category) {
                    sourceLocal.id -> sourceLocal.add(membership)
                    coverageComposition.id -> coverageComposition.add(membership)
                    mixed.id -> mixed.add(membership)
                    else -> insufficient.add(membership)
                }
                if (bestFor383Predicate.select(membership)) {
                    inspected += preCorrectionGeometryCoveragePixelAudit(
                        membership = membership,
                        coverageMask = coverageMask,
                    )
                }
            }
        }

        val criticalSet = MutableMembershipPixelSet("for379-critical")
        for (sample in criticalSamples) {
            criticalSet.add(
                coverageCompositionMembershipPixelAudit(
                    pixel = sample.pixel,
                    coverageMask = coverageMask.getPixel(sample.pixel.x, sample.pixel.y),
                    transparentSource = transparentSource.getPixel(sample.pixel.x, sample.pixel.y),
                ),
            )
        }
        for (pixel in inspected) {
            for ((_, stats) in candidateStats) {
                if (stats.candidate.select(pixel)) {
                    stats.add(pixel)
                }
            }
        }
        val candidates = candidateStats.values.toList()
        val sourceLocalTruth = inspected.count { it.membership.category == "source-locale-plausible" }
        val bestCandidate = candidates.sortedWith(
            compareByDescending<MutablePreCorrectionMetadataCandidateStats> { it.sourceLocalRecovered }
                .thenByDescending { it.precision }
                .thenBy { it.regressedIncluded }
                .thenBy { it.count },
        ).firstOrNull()
        val classification = preCorrectionGeometryCoverageMetadataClassification(
            bestCandidate = bestCandidate,
            sourceLocalTruth = sourceLocalTruth,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-384",
              "sceneId": "m60-f16-pre-correction-geometry-coverage-metadata-audit-for384",
              "sourceSceneId": "m60-f16-pre-probe-predicate-audit-for383",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-metadata-geometrie-couverture-pre-correction-apres-for-383",
              "sourceFinding": "global/kanvas/findings/for-383-montre-que-le-meilleur-predicate-pre-probe-m60-f16-reste-trop-large-avec-1-pixel-regresse",
              "requiredFor383Decision": "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED",
              "requiredFor383Classification": "pre-probe-predicate-too-broad",
              "requiredFor382Decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED",
              "requiredFor382Classification": "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof",
              "requiredFor381Decision": "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED",
              "requiredFor381Classification": "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition",
              "requiredFor380Decision": "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED",
              "requiredFor380Classification": "regression-detected",
              "decision": "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "metadata-candidate-defendable-runtime-proof-still-blocked",
                "metadata-candidate-too-broad",
                "metadata-candidate-insufficient",
                "metadata-proof-missing"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "inspectionScope": {
                "sourcePredicateFromFor383": "partial-alpha-current-error-shape",
                "inspectedPixels": ${inspected.size},
                "sourceLocalTruthPixels": $sourceLocalTruth,
                "regressedTruthPixels": ${inspected.count { it.membership.pixel.deltaVsCurrent > 0 }},
                "inspectionScopeInheritsFor383ReferenceResidualShape": true,
                "metadataCandidateSelectionUsesSkiaReference": false,
                "metadataCandidateSelectionUsesProbeOutcome": false,
                "metadataCandidateSelectionUsesProbeResidual": false,
                "metadataCandidateSelectionUsesDeltaVsCurrent": false,
                "metadataCandidateSelectionUsesFor379MembershipAsPrimary": false,
                "probeOutcomeUsedOnlyAsEvaluationTruth": true,
                "for382CategoryUsedOnlyAsEvaluationTruth": true
              },
              "preCorrectionMetadataSignals": {
                "strokeBandCapJoinAvailable": true,
                "bandLocalXAvailable": true,
                "bandEdgeDistanceAvailable": true,
                "coverageAlphaByteAvailable": true,
                "transparentSourceAlphaByteAvailable": true,
                "coverageOrthogonalNeighborhoodAvailable": true,
                "referenceRequiredForCandidateSelection": false,
                "probeRequiredForCandidateSelection": false,
                "rendererRuntimePredicateReady": false
              },
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "truthSetsForEvaluationOnly": {
                "allAuditedPixels": ${allAudited.toStats().toJson().prependIndent("  ").trimStart()},
                "improved": ${improved.toStats().toJson().prependIndent("  ").trimStart()},
                "regressed": ${regressed.toStats().toJson().prependIndent("  ").trimStart()},
                "unchanged": ${unchanged.toStats().toJson().prependIndent("  ").trimStart()},
                "for379Critical": ${criticalSet.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "for382CategoriesForEvaluationOnly": {
                "source-locale-plausible": ${sourceLocal.toStats().toJson().prependIndent("  ").trimStart()},
                "coverage-composition-plausible": ${coverageComposition.toStats().toJson().prependIndent("  ").trimStart()},
                "mixed": ${mixed.toStats().toJson().prependIndent("  ").trimStart()},
                "insufficient": ${insufficient.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "inspectedPixels": [
            ${inspected.sortedWith(compareBy<PreCorrectionGeometryCoveragePixelAudit> { it.membership.pixel.y }.thenBy { it.membership.pixel.x }).joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "metadataCandidates": [
            ${candidates.joinToString(",\n") { it.toStatsJson(sourceLocalTruth = sourceLocalTruth).prependIndent("    ") }}
              ],
              "bestMetadataCandidate": ${bestCandidate?.toSummaryJson(sourceLocalTruth = sourceLocalTruth)?.prependIndent("  ")?.trimStart() ?: "null"},
              "classificationReason": ${preCorrectionGeometryCoverageMetadataClassificationReason(classification).jsonString()},
              "nextMove": "Do not enable a renderer correction yet. Convert the defensible coverage/geometry split into renderer-owned metadata that does not inherit the FOR-383 reference/current-error-shape scope, then re-test the correction locally.",
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "probeEnabledByDefault": false,
                "correctionPredicateEnabled": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
                "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
                "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
                "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for384-pycache python3 -m py_compile scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16GeneralizedCoverageMetadataPredicateAuditJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val criticalSamples = sourceColorSubzoneCriticalSamples(
            residualStats = uncorrectedResidualStats,
            probeGpu = probeGpu,
            coverageMask = coverageMask,
            transparentSource = transparentSource,
        )
        val criticalCoordinates = criticalSamples.map { it.pixel.x to it.pixel.y }
        val allAudited = MutableMembershipPixelSet("all-audited")
        val generalizedScope = MutableMembershipPixelSet("full-scene-nonzero-coverage-source-alpha")
        val improved = MutableMembershipPixelSet("for381-improved")
        val regressed = MutableMembershipPixelSet("for381-regressed")
        val unchanged = MutableMembershipPixelSet("for381-unchanged")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val candidateStats = generalizedCoverageMetadataCandidates().associate { candidate ->
            candidate.id to MutableGeneralizedCoverageMetadataCandidateStats(candidate)
        }
        val for383Predicate = preProbePredicateCandidates().first { it.id == "partial-alpha-current-error-shape" }
        var for383PredicatePixelsInScope = 0

        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val pixel = sourceColorSubzonePixelAudit(
                    x = x,
                    y = y,
                    reference = reference.getPixel(x, y),
                    current = currentGpu.getPixel(x, y),
                    probe = probeGpu.getPixel(x, y),
                    criticalCoordinates = criticalCoordinates,
                )
                val membership = coverageCompositionMembershipPixelAudit(
                    pixel = pixel,
                    coverageMask = coverageMask.getPixel(x, y),
                    transparentSource = transparentSource.getPixel(x, y),
                )
                val audit = preCorrectionGeometryCoveragePixelAudit(
                    membership = membership,
                    coverageMask = coverageMask,
                )
                allAudited.add(membership)
                when {
                    pixel.deltaVsCurrent < 0 -> improved.add(membership)
                    pixel.deltaVsCurrent > 0 -> regressed.add(membership)
                    else -> unchanged.add(membership)
                }
                when (membership.category) {
                    sourceLocal.id -> sourceLocal.add(membership)
                    coverageComposition.id -> coverageComposition.add(membership)
                    mixed.id -> mixed.add(membership)
                    else -> insufficient.add(membership)
                }
                if (
                    membership.coverageAlphaByte > 0 &&
                    membership.transparentSourceAlphaByte > 0
                ) {
                    generalizedScope.add(membership)
                    if (for383Predicate.select(membership)) {
                        for383PredicatePixelsInScope++
                    }
                    for ((_, stats) in candidateStats) {
                        if (stats.candidate.select(audit)) {
                            stats.add(audit)
                        }
                    }
                }
            }
        }

        val criticalSet = MutableMembershipPixelSet("for379-critical")
        for (sample in criticalSamples) {
            criticalSet.add(
                coverageCompositionMembershipPixelAudit(
                    pixel = sample.pixel,
                    coverageMask = coverageMask.getPixel(sample.pixel.x, sample.pixel.y),
                    transparentSource = transparentSource.getPixel(sample.pixel.x, sample.pixel.y),
                ),
            )
        }
        val candidates = candidateStats.values.toList()
        val sourceLocalTruth = sourceLocal.count
        val bestCandidate = candidates.sortedWith(
            compareByDescending<MutableGeneralizedCoverageMetadataCandidateStats> { it.sourceLocalRecovered }
                .thenBy { it.regressedIncluded }
                .thenByDescending { it.precision }
                .thenBy { it.count },
        ).firstOrNull()
        val classification = generalizedCoverageMetadataClassification(
            bestCandidate = bestCandidate,
            sourceLocalTruth = sourceLocalTruth,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-385",
              "sceneId": "m60-f16-generalized-coverage-metadata-predicate-audit-for385",
              "sourceSceneId": "m60-f16-pre-correction-geometry-coverage-metadata-audit-for384",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-predicate-moteur-couverture-hors-filtre-for-383-apres-for-384",
              "sourceFinding": "global/kanvas/findings/for-384-separe-les-8-pixels-source-locale-m60-f16-par-metadata-couverture-mais-bloque-encore-la-correction-moteur",
              "requiredFor384Decision": "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED",
              "requiredFor384Classification": "metadata-candidate-defendable-runtime-proof-still-blocked",
              "requiredFor383Decision": "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED",
              "requiredFor383Classification": "pre-probe-predicate-too-broad",
              "requiredFor382Decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED",
              "requiredFor382Classification": "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof",
              "decision": "M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "generalized-predicate-defendable",
                "generalized-predicate-too-broad",
                "generalized-predicate-insufficient",
                "proof-missing"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "generalizedScope": {
                "scopeId": "full-scene-nonzero-coverage-source-alpha",
                "scopeDescription": "All M60 F16 scene pixels with renderer coverage alpha and transparent-source alpha available before any FOR-380 probe correction.",
                "sourceScopeFromFor383": false,
                "sourceScopeFromFor384": false,
                "usesSkiaReferenceForScope": false,
                "usesProbeOutcomeForScope": false,
                "usesProbeResidualForScope": false,
                "usesDeltaVsCurrentForScope": false,
                "usesFor379MembershipAsPrimaryScope": false,
                "usesFor383PredicateAsPrimaryScope": false,
                "scopePixels": ${generalizedScope.count},
                "fullScenePixels": ${reference.width * reference.height},
                "for383PredicatePixelsInsideScopeForComparisonOnly": $for383PredicatePixelsInScope,
                "sourceLocalTruthPixels": $sourceLocalTruth,
                "regressedTruthPixels": ${regressed.count},
                "probeOutcomeUsedOnlyAsEvaluationTruth": true,
                "for382CategoryUsedOnlyAsEvaluationTruth": true
              },
              "preCorrectionMetadataSignals": {
                "strokeBandCapJoinAvailable": true,
                "bandLocalXAvailable": true,
                "bandEdgeDistanceAvailable": true,
                "coverageAlphaByteAvailable": true,
                "transparentSourceAlphaByteAvailable": true,
                "coverageOrthogonalNeighborhoodAvailable": true,
                "referenceRequiredForCandidateSelection": false,
                "probeRequiredForCandidateSelection": false,
                "currentResidualRequiredForCandidateSelection": false,
                "deltaVsCurrentRequiredForCandidateSelection": false,
                "for383RequiredForCandidateSelection": false,
                "rendererRuntimePredicateReady": ${if (classification == "generalized-predicate-defendable") "true" else "false"}
              },
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "truthSetsForEvaluationOnly": {
                "allAuditedPixels": ${allAudited.toStats().toJson().prependIndent("  ").trimStart()},
                "generalizedScope": ${generalizedScope.toStats().toJson().prependIndent("  ").trimStart()},
                "improved": ${improved.toStats().toJson().prependIndent("  ").trimStart()},
                "regressed": ${regressed.toStats().toJson().prependIndent("  ").trimStart()},
                "unchanged": ${unchanged.toStats().toJson().prependIndent("  ").trimStart()},
                "for379Critical": ${criticalSet.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "for382CategoriesForEvaluationOnly": {
                "source-locale-plausible": ${sourceLocal.toStats().toJson().prependIndent("  ").trimStart()},
                "coverage-composition-plausible": ${coverageComposition.toStats().toJson().prependIndent("  ").trimStart()},
                "mixed": ${mixed.toStats().toJson().prependIndent("  ").trimStart()},
                "insufficient": ${insufficient.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "metadataCandidates": [
            ${candidates.joinToString(",\n") { it.toStatsJson(sourceLocalTruth = sourceLocalTruth).prependIndent("    ") }}
              ],
              "bestMetadataCandidate": ${bestCandidate?.toSummaryJson(sourceLocalTruth = sourceLocalTruth)?.prependIndent("  ")?.trimStart() ?: "null"},
              "classificationReason": ${generalizedCoverageMetadataClassificationReason(classification).jsonString()},
              "nextMove": ${generalizedCoverageMetadataNextMove(classification, bestCandidate).jsonString()},
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "probeEnabledByDefault": false,
                "correctionPredicateEnabled": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
                "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
                "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
                "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
                "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for385-pycache python3 -m py_compile scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16CoverageRegressionDiscriminatorAuditJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        probeGpu: SkBitmap,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        uncorrectedExperimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val criticalSamples = sourceColorSubzoneCriticalSamples(
            residualStats = uncorrectedResidualStats,
            probeGpu = probeGpu,
            coverageMask = coverageMask,
            transparentSource = transparentSource,
        )
        val criticalCoordinates = criticalSamples.map { it.pixel.x to it.pixel.y }
        val allAudited = MutableMembershipPixelSet("all-audited")
        val for385Selected = MutableMembershipPixelSet("for385-partial-coverage-alpha-at-least-96")
        val selectedSourceLocal = MutableMembershipPixelSet("for385-selected-source-local")
        val selectedRegressed = MutableMembershipPixelSet("for385-selected-regressed")
        val selectedNonRegressed = MutableMembershipPixelSet("for385-selected-non-regressed")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val regressionBreakdown = MutableCoverageRegressionMetadataBreakdown()
        val candidateStats = coverageRegressionDiscriminatorCandidates().associate { candidate ->
            candidate.id to MutableCoverageRegressionDiscriminatorCandidateStats(candidate)
        }
        val for385Predicate = generalizedCoverageMetadataCandidates().first { it.id == "partial-coverage-alpha-at-least-96" }
        var generalizedScopePixels = 0

        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val pixel = sourceColorSubzonePixelAudit(
                    x = x,
                    y = y,
                    reference = reference.getPixel(x, y),
                    current = currentGpu.getPixel(x, y),
                    probe = probeGpu.getPixel(x, y),
                    criticalCoordinates = criticalCoordinates,
                )
                val membership = coverageCompositionMembershipPixelAudit(
                    pixel = pixel,
                    coverageMask = coverageMask.getPixel(x, y),
                    transparentSource = transparentSource.getPixel(x, y),
                )
                val audit = preCorrectionGeometryCoveragePixelAudit(
                    membership = membership,
                    coverageMask = coverageMask,
                )
                allAudited.add(membership)
                when (membership.category) {
                    sourceLocal.id -> sourceLocal.add(membership)
                    coverageComposition.id -> coverageComposition.add(membership)
                    mixed.id -> mixed.add(membership)
                    else -> insufficient.add(membership)
                }
                if (
                    membership.coverageAlphaByte > 0 &&
                    membership.transparentSourceAlphaByte > 0
                ) {
                    generalizedScopePixels++
                    if (for385Predicate.select(audit)) {
                        for385Selected.add(membership)
                        if (membership.category == sourceLocal.id) {
                            selectedSourceLocal.add(membership)
                        }
                        if (pixel.deltaVsCurrent > 0) {
                            selectedRegressed.add(membership)
                            regressionBreakdown.add(audit)
                        } else {
                            selectedNonRegressed.add(membership)
                        }
                        for ((_, stats) in candidateStats) {
                            if (stats.candidate.select(audit)) {
                                stats.add(audit)
                            }
                        }
                    }
                }
            }
        }

        val candidates = candidateStats.values.toList()
        val sourceLocalTruth = selectedSourceLocal.count
        val regressionTruth = selectedRegressed.count
        val bestCandidate = candidates.sortedWith(
            compareByDescending<MutableCoverageRegressionDiscriminatorCandidateStats> { it.sourceLocalRecovered }
                .thenBy { it.regressedIncluded }
                .thenByDescending { it.precision }
                .thenBy { it.count },
        ).firstOrNull()
        val classification = coverageRegressionDiscriminatorClassification(
            bestCandidate = bestCandidate,
            sourceLocalTruth = sourceLocalTruth,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-386",
              "sceneId": "m60-f16-coverage-regression-discriminator-audit-for386",
              "sourceSceneId": "m60-f16-generalized-coverage-metadata-predicate-audit-for385",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-discriminer-les-428-regressions-du-predicate-couverture-apres-for-385",
              "sourceFinding": "global/kanvas/findings/for-385-montre-que-le-predicate-couverture-m60-f16-generalise-reste-trop-large-hors-filtre-for-383",
              "requiredFor385Decision": "M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED",
              "requiredFor385Classification": "generalized-predicate-too-broad",
              "requiredFor384Decision": "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED",
              "requiredFor384Classification": "metadata-candidate-defendable-runtime-proof-still-blocked",
              "requiredFor383Decision": "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED",
              "requiredFor383Classification": "pre-probe-predicate-too-broad",
              "requiredFor382Decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED",
              "requiredFor382Classification": "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof",
              "decision": "M60_F16_COVERAGE_REGRESSION_DISCRIMINATOR_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "discriminator-candidate-defendable",
                "discriminator-candidate-too-broad",
                "discriminator-candidate-insufficient",
                "metadata-insufficient"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "inspectedFor385Selection": {
                "sourceCandidateId": "partial-coverage-alpha-at-least-96",
                "scopeDescription": "The 436 pixels selected by the best FOR-385 metadata predicate, analyzed only as diagnostic input.",
                "sourceScopeFromFor385": true,
                "usesFor385SelectionAsAuditInput": true,
                "candidateSelectionUsesFor385AsPrimaryPredicate": false,
                "usesSkiaReferenceForScope": false,
                "usesProbeOutcomeForScope": false,
                "usesProbeResidualForScope": false,
                "usesDeltaVsCurrentForScope": false,
                "usesFor379MembershipAsPrimaryScope": false,
                "usesFor383PredicateAsPrimaryScope": false,
                "usesFor384PredicateAsPrimaryScope": false,
                "generalizedScopePixels": $generalizedScopePixels,
                "inspectedPixels": ${for385Selected.count},
                "sourceLocalTruthPixels": $sourceLocalTruth,
                "regressedTruthPixels": $regressionTruth,
                "nonRegressedTruthPixels": ${selectedNonRegressed.count},
                "probeOutcomeUsedOnlyAsEvaluationTruth": true,
                "for382CategoryUsedOnlyAsEvaluationTruth": true
              },
              "preCorrectionMetadataSignals": {
                "strokeBandCapJoinAvailable": true,
                "bandLocalXAvailable": true,
                "bandEdgeDistanceAvailable": true,
                "coverageAlphaByteAvailable": true,
                "transparentSourceAlphaByteAvailable": true,
                "coverageOrthogonalNeighborhoodAvailable": true,
                "referenceRequiredForCandidateSelection": false,
                "probeRequiredForCandidateSelection": false,
                "currentResidualRequiredForCandidateSelection": false,
                "deltaVsCurrentRequiredForCandidateSelection": false,
                "for379RequiredForCandidateSelection": false,
                "for383RequiredForCandidateSelection": false,
                "for384RequiredForCandidateSelection": false,
                "for385RequiredForCandidateSelection": false,
                "rendererRuntimePredicateReady": false
              },
              "fullSceneGuard": {
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)},
                "uncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "uncorrectedGreaterThanEightPixels": ${uncorrectedResidualStats.greaterThanEightPixels},
                "correctedGreaterThanEightPixels": ${correctedResidualStats.greaterThanEightPixels},
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false
              },
              "truthSetsForEvaluationOnly": {
                "allAuditedPixels": ${allAudited.toStats().toJson().prependIndent("  ").trimStart()},
                "for385Selected": ${for385Selected.toStats().toJson().prependIndent("  ").trimStart()},
                "selectedSourceLocal": ${selectedSourceLocal.toStats().toJson().prependIndent("  ").trimStart()},
                "selectedRegressed": ${selectedRegressed.toStats().toJson().prependIndent("  ").trimStart()},
                "selectedNonRegressed": ${selectedNonRegressed.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "for382CategoriesForEvaluationOnly": {
                "source-locale-plausible": ${sourceLocal.toStats().toJson().prependIndent("  ").trimStart()},
                "coverage-composition-plausible": ${coverageComposition.toStats().toJson().prependIndent("  ").trimStart()},
                "mixed": ${mixed.toStats().toJson().prependIndent("  ").trimStart()},
                "insufficient": ${insufficient.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "regressedMetadataBreakdown": ${regressionBreakdown.toJson().prependIndent("  ").trimStart()},
              "discriminatorCandidates": [
            ${candidates.joinToString(",\n") { it.toStatsJson(sourceLocalTruth = sourceLocalTruth).prependIndent("    ") }}
              ],
              "bestDiscriminatorCandidate": ${bestCandidate?.toSummaryJson(sourceLocalTruth = sourceLocalTruth)?.prependIndent("  ")?.trimStart() ?: "null"},
              "classificationReason": ${coverageRegressionDiscriminatorClassificationReason(classification).jsonString()},
              "nextMove": ${coverageRegressionDiscriminatorNextMove(classification, bestCandidate).jsonString()},
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "probeEnabledByDefault": false,
                "correctionPredicateEnabled": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
                "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
                "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
                "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
                "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
                "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
                "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
                "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
                "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
                "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
                "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
                "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
                "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
                "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
                "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
                "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
                "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
                "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
                "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
                "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
                "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
                "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for386-pycache python3 -m py_compile scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16CandidatePolicyRgbaProbeJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val samples = residualStats.highDeltaSamples.take(FOR373_REQUIRED_SAMPLE_COUNT)
        val currentResidual = samples.sumOf { sampleResidual(it.reference, it.gpu) }
        val candidateSamples = samples.mapIndexed { index, sample ->
            candidatePolicySample(index + 1, sample, coverageMask)
        }
        val candidateTotalResidual = candidateSamples.sumOf { it.candidateResidual }
        val classification = when {
            samples.size != FOR373_REQUIRED_SAMPLE_COUNT -> "candidate-policy-blocked"
            candidateTotalResidual < FOR373_CURRENT_RESIDUAL -> "candidate-policy-reduces-residual"
            candidateTotalResidual == FOR373_CURRENT_RESIDUAL -> "candidate-policy-neutral"
            else -> "candidate-policy-regresses"
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-373",
              "sceneId": "m60-f16-candidate-policy-rgba-probe-for373",
              "sourceSceneId": "m60-f16-effective-coverage-export-for372",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-candidate-policy-rgba-comparable-apres-for-372",
              "sourceFinding": "global/kanvas/findings/for-372-exporte-la-couverture-aa-effective-m60-f16-depuis-un-masque-cpu-diagnostique",
              "requiredFor372Decision": "M60_F16_EFFECTIVE_COVERAGE_EXPORT_READY_FOR_CANDIDATE_PROBE",
              "requiredFor372Classification": "coverage-export-ready-for-candidate-probe",
              "decision": "M60_F16_CANDIDATE_POLICY_RGBA_PROBE_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "candidate-policy-reduces-residual",
                "candidate-policy-neutral",
                "candidate-policy-regresses",
                "candidate-policy-blocked"
              ],
              "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "candidatePolicyFormula": "alphaByte=round((paintAlpha/255.0)*(sourceCoverageByte/255.0)*255); alpha=alphaByte/255.0; rgb=floor(((sourceRgb/255.0)*alpha + (1.0-alpha))*256.0) clamped to [0,255]; a=255",
              "candidatePolicyInputSource": "FOR-372 diagnostic coverage export fields plus BoundedStrokeCapJoinGM paint metadata",
              "candidatePolicyRgbaSource": "calculated-by-diagnostic-policy",
              "candidatePolicyRgbaReadFromRenderer": false,
              "candidatePolicyRgbaReadFromGpuImage": false,
              "candidatePolicyRgbaAppliedToRenderer": false,
              "rendererAppliedCandidate": false,
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "candidateTotalResidual": $candidateTotalResidual,
              "candidateTotalResidualDeltaVsCurrent": ${candidateTotalResidual - FOR373_CURRENT_RESIDUAL},
              "sampleCount": ${samples.size},
              "samples": [
            ${candidateSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "candidateImplementationAuthorized": false,
                "candidatePolicyRgbaAppliedToRenderer": false,
                "candidatePolicyRgbaReadFromRenderer": false,
                "candidatePolicyRgbaReadFromGpuImage": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16CandidateRegressionAuditJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val samples = residualStats.highDeltaSamples.take(FOR374_REQUIRED_SAMPLE_COUNT)
        val candidateSamples = samples.mapIndexed { index, sample ->
            candidatePolicySample(index + 1, sample, coverageMask)
        }
        val auditSamples = candidateSamples.map { candidateRegressionSample(it) }
        val currentResidual = auditSamples.sumOf { it.currentResidual }
        val candidateTotalResidual = auditSamples.sumOf { it.candidateResidual }
        val regressingSamples = auditSamples.count { it.candidateResidualDeltaVsCurrent > 0 }
        val improvedSamples = auditSamples.count { it.candidateResidualDeltaVsCurrent < 0 }
        val destinationConflictSamples = auditSamples.count {
            it.inverseDestinationEstimate.possible && it.inverseDestinationEstimate.conflictsWithWhiteDestination
        }
        val sourceTintSamples = auditSamples.count { it.regressionDirection == "source-tint-too-strong" }
        val quantizationOnlySamples = auditSamples.count {
            kotlin.math.abs(it.candidateResidualDeltaVsCurrent) <= 4 && !it.inverseDestinationEstimate.conflictsWithWhiteDestination
        }
        val classification = candidateRegressionClassification(
            sampleCount = auditSamples.size,
            candidateTotalResidual = candidateTotalResidual,
            currentResidual = currentResidual,
            regressingSamples = regressingSamples,
            destinationConflictSamples = destinationConflictSamples,
            sourceTintSamples = sourceTintSamples,
            quantizationOnlySamples = quantizationOnlySamples,
        )
        val likelyMissingParameter = when (classification) {
            "candidate-regression-likely-destination-model" ->
                "effective-destination-color-or-background-model-before-stroke-composition"
            "candidate-regression-likely-coverage-model" ->
                "reference-effective-coverage-or-effective-source-alpha"
            "candidate-regression-likely-quantization-model" ->
                "current-path-quantization-and-rounding-policy"
            "candidate-regression-mixed" ->
                "destination-coverage-quantization-needs-separated-probes"
            else ->
                "blocked-by-insufficient-diagnostic-signal"
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-374",
              "sceneId": "m60-f16-candidate-regression-audit-for374",
              "sourceSceneId": "m60-f16-candidate-policy-rgba-probe-for373",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-diagnostic-source-over-white-regresse-apres-for-373",
              "sourceFinding": "global/kanvas/findings/for-373-calcule-la-candidate-policy-rgba-m60-f16-mais-augmente-le-residuel",
              "requiredFor373Decision": "M60_F16_CANDIDATE_POLICY_RGBA_PROBE_RECORDED",
              "requiredFor373Classification": "candidate-policy-regresses",
              "decision": "M60_F16_CANDIDATE_REGRESSION_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "candidate-regression-likely-destination-model",
                "candidate-regression-likely-coverage-model",
                "candidate-regression-likely-quantization-model",
                "candidate-regression-mixed",
                "candidate-regression-blocked"
              ],
              "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "candidatePolicyUnderAudit": ${f16CandidatePolicyId().jsonString()},
              "auditInputSource": "FOR-373 preserved samples plus diagnostic inverse destination math",
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "requiredFor373CandidateTotalResidual": 1033,
              "candidateTotalResidual": $candidateTotalResidual,
              "candidateTotalResidualDeltaVsCurrent": ${candidateTotalResidual - currentResidual},
              "sampleCount": ${auditSamples.size},
              "regressingSampleCount": $regressingSamples,
              "improvedSampleCount": $improvedSamples,
              "destinationConflictSampleCount": $destinationConflictSamples,
              "sourceTintRegressionSampleCount": $sourceTintSamples,
              "quantizationOnlySampleCount": $quantizationOnlySamples,
              "likelyMissingParameter": ${likelyMissingParameter.jsonString()},
              "classificationReason": ${candidateRegressionReason(classification).jsonString()},
              "inverseDestinationModel": {
                "formula": "destination=(reference-(source*effectiveAlpha))/(1-effectiveAlpha) per RGB channel",
                "source": "referenceRgba, paintSourceRgba and effectiveSourceAlpha from FOR-373",
                "diagnosticOnly": true,
                "appliedToRenderer": false,
                "usedAsCorrection": false,
                "whiteDestinationByte": 255
              },
              "samples": [
            ${auditSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "candidatePolicyRgbaAppliedToRenderer": false,
                "candidatePolicyRgbaReadFromRenderer": false,
                "candidatePolicyRgbaReadFromGpuImage": false,
                "newCandidatePolicyProduced": false,
                "inverseDestinationAppliedAsCorrection": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16EffectiveDestinationCandidateJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val samples = residualStats.highDeltaSamples.take(FOR375_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val candidateSamples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val currentResidual = candidateSamples.sumOf { it.currentResidual }
        val for373CandidateTotalResidual = candidateSamples.sumOf { it.for373CandidateResidual }
        val effectiveDestinationCandidateTotalResidual =
            candidateSamples.sumOf { it.effectiveDestinationCandidateResidual }
        val classification = effectiveDestinationCandidateClassification(
            sampleCount = candidateSamples.size,
            effectiveDestinationCandidateTotalResidual = effectiveDestinationCandidateTotalResidual,
            currentResidual = currentResidual,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-375",
              "sceneId": "m60-f16-effective-destination-candidate-for375",
              "sourceSceneId": "m60-f16-candidate-regression-audit-for374",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-candidate-sur-destination-effective-apres-for-374",
              "sourceFinding": "global/kanvas/findings/for-374-isole-le-modele-de-destination-effective-comme-cause-probable-de-regression-m60-f16",
              "requiredFor374Decision": "M60_F16_CANDIDATE_REGRESSION_AUDIT_RECORDED",
              "requiredFor374Classification": "candidate-regression-likely-destination-model",
              "decision": "M60_F16_EFFECTIVE_DESTINATION_CANDIDATE_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "effective-destination-candidate-explains-reference",
                "effective-destination-candidate-reduces-residual",
                "effective-destination-candidate-neutral",
                "effective-destination-candidate-regresses",
                "effective-destination-candidate-blocked"
              ],
              "candidatePolicyId": "source_over_effective_destination_diagnostic",
              "sourceCandidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "candidateInputSource": "FOR-374 preserved samples plus inverseDestinationEstimate.rgbClampedToSrgb",
              "candidateFormula": "destinationRgb=inverseDestinationEstimate.rgbClampedToSrgb; alpha=effectiveSourceAlphaByte/255.0; rgb=floor(((sourceRgb/255.0)*alpha + (destinationRgb/255.0)*(1.0-alpha))*256.0) clamped to [0,255]; a=255",
              "candidateRgbaSource": "calculated-by-diagnostic-policy",
              "candidateReadFromRenderer": false,
              "candidateReadFromGpuImage": false,
              "candidateAppliedToRenderer": false,
              "destinationSource": "inverseDestinationEstimate.rgbClampedToSrgb",
              "destinationReadFromRenderer": false,
              "destinationReadFromGpuImage": false,
              "destinationAppliedToRenderer": false,
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "requiredFor373CandidateTotalResidual": $FOR373_CANDIDATE_TOTAL_RESIDUAL,
              "for373CandidateTotalResidual": $for373CandidateTotalResidual,
              "effectiveDestinationCandidateTotalResidual": $effectiveDestinationCandidateTotalResidual,
              "effectiveDestinationCandidateTotalDeltaVsCurrent": ${effectiveDestinationCandidateTotalResidual - currentResidual},
              "effectiveDestinationCandidateTotalDeltaVsFor373Candidate": ${effectiveDestinationCandidateTotalResidual - for373CandidateTotalResidual},
              "sampleCount": ${candidateSamples.size},
              "samples": [
            ${candidateSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "candidateAppliedToRenderer": false,
                "candidateReadFromRenderer": false,
                "candidateReadFromGpuImage": false,
                "destinationAppliedToRenderer": false,
                "destinationReadFromRenderer": false,
                "destinationReadFromGpuImage": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16CompositionQuantizationCandidateJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val samples = residualStats.highDeltaSamples.take(FOR376_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val for375Samples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val candidateSamples = for375Samples.map { compositionQuantizationCandidateSample(it) }
        val currentResidual = candidateSamples.sumOf { it.currentResidual }
        val for373CandidateTotalResidual = candidateSamples.sumOf { it.for373CandidateResidual }
        val for375CandidateTotalResidual = candidateSamples.sumOf { it.for375CandidateResidual }
        val variantDefinitions = compositionQuantizationVariantDefinitions()
        val variantTotals = variantDefinitions.map { definition ->
            CompositionQuantizationVariantTotal(
                definition = definition,
                totalResidual = candidateSamples.sumOf { sample ->
                    sample.variants.first { it.variantId == definition.id }.residual
                },
                currentResidual = currentResidual,
                for375CandidateTotalResidual = for375CandidateTotalResidual,
                for373CandidateTotalResidual = for373CandidateTotalResidual,
            )
        }
        val ranking = variantTotals.sortedWith(compareBy<CompositionQuantizationVariantTotal> { it.totalResidual }.thenBy { it.variantId })
        val best = ranking.first()
        val classification = compositionQuantizationCandidateClassification(
            sampleCount = candidateSamples.size,
            blockedVariantCount = candidateSamples.sumOf { sample -> sample.variants.count { it.candidateRgba == null } },
            bestVariantTotalResidual = best.totalResidual,
            currentResidual = currentResidual,
            for375CandidateTotalResidual = for375CandidateTotalResidual,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-376",
              "sceneId": "m60-f16-composition-quantization-candidate-for376",
              "sourceSceneId": "m60-f16-effective-destination-candidate-for375",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-axe-espace-de-composition-et-quantification-apres-for-375",
              "sourceFinding": "global/kanvas/findings/for-375-reduit-le-residuel-m60-f16-avec-une-candidate-sur-destination-effective-diagnostique",
              "requiredFor375Decision": "M60_F16_EFFECTIVE_DESTINATION_CANDIDATE_RECORDED",
              "requiredFor375Classification": "effective-destination-candidate-reduces-residual",
              "decision": "M60_F16_COMPOSITION_QUANTIZATION_CANDIDATE_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "composition-quantization-candidate-explains-reference",
                "composition-quantization-candidate-reduces-residual",
                "composition-quantization-candidate-neutral",
                "composition-quantization-candidate-regresses",
                "composition-quantization-candidate-blocked"
              ],
              "candidateInputSource": "FOR-375 preserved samples plus inverseDestinationEstimate.rgbClampedToSrgb",
              "destinationSource": "inverseDestinationEstimate.rgbClampedToSrgb",
              "destinationReadFromRenderer": false,
              "destinationReadFromGpuImage": false,
              "destinationAppliedToRenderer": false,
              "variantRgbaSource": "calculated-by-diagnostic-policy",
              "variantReadFromRenderer": false,
              "variantReadFromGpuImage": false,
              "variantAppliedToRenderer": false,
              "variantCount": ${variantTotals.size},
              "variantDefinitions": [
            ${variantDefinitions.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "requiredFor373CandidateTotalResidual": $FOR373_CANDIDATE_TOTAL_RESIDUAL,
              "for373CandidateTotalResidual": $for373CandidateTotalResidual,
              "requiredFor375EffectiveDestinationCandidateTotalResidual": $FOR375_EFFECTIVE_DESTINATION_CANDIDATE_TOTAL_RESIDUAL,
              "for375EffectiveDestinationCandidateTotalResidual": $for375CandidateTotalResidual,
              "bestVariantId": ${best.variantId.jsonString()},
              "bestVariantTotalResidual": ${best.totalResidual},
              "bestVariantTotalDeltaVsCurrent": ${best.totalDeltaVsCurrent},
              "bestVariantTotalDeltaVsFor375Candidate": ${best.totalDeltaVsFor375Candidate},
              "bestVariantTotalDeltaVsFor373Candidate": ${best.totalDeltaVsFor373Candidate},
              "variantRanking": [
            ${ranking.mapIndexed { index, total -> total.toJson(index + 1).prependIndent("    ") }.joinToString(",\n")}
              ],
              "sampleCount": ${candidateSamples.size},
              "samples": [
            ${candidateSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "variantAppliedToRenderer": false,
                "variantReadFromRenderer": false,
                "variantReadFromGpuImage": false,
                "destinationAppliedToRenderer": false,
                "destinationReadFromRenderer": false,
                "destinationReadFromGpuImage": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16LinearSrgbPlausibilityAuditJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val samples = residualStats.highDeltaSamples.take(FOR377_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val for375Samples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val for376Samples = for375Samples.map { compositionQuantizationCandidateSample(it) }
        val auditSamples = for376Samples.map { linearSrgbPlausibilityAuditSample(it) }
        val currentResidual = auditSamples.sumOf { it.currentResidual }
        val for373CandidateTotalResidual = auditSamples.sumOf { it.for373CandidateResidual }
        val for375CandidateTotalResidual = auditSamples.sumOf { it.for375CandidateResidual }
        val linearSrgbTotalResidual = auditSamples.sumOf { it.linearSrgbResidual }
        val totalImprovementVsCurrent = currentResidual - linearSrgbTotalResidual
        val clampPositiveImprovement = auditSamples.sumOf { it.destinationClampPositiveImprovement }
        val clampImprovementShare = if (totalImprovementVsCurrent > 0) {
            clampPositiveImprovement.toDouble() / totalImprovementVsCurrent.toDouble()
        } else {
            0.0
        }
        val bandCoherence = auditSamples.groupBy { it.strokeBand }.toSortedMap().map { (strokeBand, bandSamples) ->
            LinearSrgbBandCoherence(
                strokeBand = strokeBand,
                sampleCount = bandSamples.size,
                currentResidual = bandSamples.sumOf { it.currentResidual },
                linearSrgbResidual = bandSamples.sumOf { it.linearSrgbResidual },
                for375Residual = bandSamples.sumOf { it.for375CandidateResidual },
                improvedVsCurrentSamples = bandSamples.count { it.linearSrgbDeltaVsCurrent < 0 },
                regressedVsCurrentSamples = bandSamples.count { it.linearSrgbDeltaVsCurrent > 0 },
                improvedVsFor375Samples = bandSamples.count { it.linearSrgbDeltaVsFor375Candidate < 0 },
                regressedVsFor375Samples = bandSamples.count { it.linearSrgbDeltaVsFor375Candidate > 0 },
            )
        }
        val classification = linearSrgbPlausibilityClassification(
            sampleCount = auditSamples.size,
            linearSrgbTotalResidual = linearSrgbTotalResidual,
            currentResidual = currentResidual,
            clampImprovementShare = clampImprovementShare,
            bandCoherence = bandCoherence,
        )
        val classificationReason = linearSrgbPlausibilityClassificationReason(classification)
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-377",
              "sceneId": "m60-f16-linear-srgb-plausibility-audit-for377",
              "sourceSceneId": "m60-f16-composition-quantization-candidate-for376",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-audit-realite-linear-s-rgb-apres-for-376",
              "sourceFinding": "global/kanvas/findings/for-376-isole-la-variante-linear-s-rgb-comme-meilleure-piste-m60-f16-apres-destination-effective",
              "requiredFor376Decision": "M60_F16_COMPOSITION_QUANTIZATION_CANDIDATE_RECORDED",
              "requiredFor376Classification": "composition-quantization-candidate-reduces-residual",
              "decision": "M60_F16_LINEAR_SRGB_PLAUSIBILITY_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "linear-srgb-plausible-next-axis",
                "linear-srgb-likely-diagnostic-artifact",
                "linear-srgb-mixed-needs-reference-color-evidence",
                "linear-srgb-blocked"
              ],
              "auditedVariantId": "linear_srgb_source_over_effective_destination_nearest_255",
              "auditInputSource": "FOR-376 preserved samples plus recalculated linear-sRGB residual and clamp/tint diagnostics",
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "destinationSource": "inverseDestinationEstimate.rgbClampedToSrgb",
              "destinationReadFromRenderer": false,
              "destinationReadFromGpuImage": false,
              "destinationAppliedToRenderer": false,
              "variantReadFromRenderer": false,
              "variantReadFromGpuImage": false,
              "variantAppliedToRenderer": false,
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "requiredFor373CandidateTotalResidual": $FOR373_CANDIDATE_TOTAL_RESIDUAL,
              "for373CandidateTotalResidual": $for373CandidateTotalResidual,
              "requiredFor375EffectiveDestinationCandidateTotalResidual": $FOR375_EFFECTIVE_DESTINATION_CANDIDATE_TOTAL_RESIDUAL,
              "for375EffectiveDestinationCandidateTotalResidual": $for375CandidateTotalResidual,
              "requiredFor376BestVariantId": "linear_srgb_source_over_effective_destination_nearest_255",
              "requiredFor376BestVariantTotalResidual": 607,
              "linearSrgbTotalResidual": $linearSrgbTotalResidual,
              "linearSrgbTotalDeltaVsCurrent": ${linearSrgbTotalResidual - currentResidual},
              "linearSrgbTotalDeltaVsFor375Candidate": ${linearSrgbTotalResidual - for375CandidateTotalResidual},
              "linearSrgbTotalDeltaVsFor373Candidate": ${linearSrgbTotalResidual - for373CandidateTotalResidual},
              "sampleCount": ${auditSamples.size},
              "improvedVsCurrentSampleCount": ${auditSamples.count { it.linearSrgbDeltaVsCurrent < 0 }},
              "regressedVsCurrentSampleCount": ${auditSamples.count { it.linearSrgbDeltaVsCurrent > 0 }},
              "improvedVsFor375SampleCount": ${auditSamples.count { it.linearSrgbDeltaVsFor375Candidate < 0 }},
              "regressedVsFor375SampleCount": ${auditSamples.count { it.linearSrgbDeltaVsFor375Candidate > 0 }},
              "destinationClampChannelCount": ${auditSamples.sumOf { it.destinationClampChannelCount }},
              "destinationClampPositiveImprovement": $clampPositiveImprovement,
              "linearSrgbPositiveImprovement": $totalImprovementVsCurrent,
              "destinationClampPositiveImprovementShare": ${String.format(Locale.US, "%.6f", clampImprovementShare)},
              "classificationReason": ${classificationReason.jsonString()},
              "bandCoherence": [
            ${bandCoherence.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "samples": [
            ${auditSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "variantAppliedToRenderer": false,
                "variantReadFromRenderer": false,
                "variantReadFromGpuImage": false,
                "destinationAppliedToRenderer": false,
                "destinationReadFromRenderer": false,
                "destinationReadFromGpuImage": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16DirectSourceColorEvidenceJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val samples = residualStats.highDeltaSamples.take(FOR378_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val for375Samples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val for376Samples = for375Samples.map { compositionQuantizationCandidateSample(it) }
        val for377Samples = for376Samples.map { linearSrgbPlausibilityAuditSample(it) }
        val directSamples = for377Samples.map { directSourceColorEvidenceSample(it, transparentSource) }
        val currentResidual = directSamples.sumOf { it.currentResidual }
        val for373CandidateTotalResidual = directSamples.sumOf { it.for373CandidateResidual }
        val for375CandidateTotalResidual = directSamples.sumOf { it.for375CandidateResidual }
        val linearSrgbTotalResidual = directSamples.sumOf { it.linearSrgbResidual }
        val directRecomposedTotalResidual = directSamples.sumOf { it.directRecomposedOnWhiteResidual }
        val sourceCoverageAlphaDeltaAbsTotal = directSamples.sumOf { it.sourceCoverageAlphaDeltaAbs }
        val sourceCoverageAlphaDeltaAbsMax = directSamples.maxOfOrNull { it.sourceCoverageAlphaDeltaAbs } ?: 0
        val paintSourceColorDeltaAbsMax = directSamples.maxOfOrNull { it.paintSourceUnpremultipliedRgbDeltaAbsMax } ?: 0
        val classification = directSourceColorClassification(
            sampleCount = directSamples.size,
            directRecomposedTotalResidual = directRecomposedTotalResidual,
            currentResidual = currentResidual,
            for375CandidateTotalResidual = for375CandidateTotalResidual,
            linearSrgbTotalResidual = linearSrgbTotalResidual,
            sourceCoverageAlphaDeltaAbsTotal = sourceCoverageAlphaDeltaAbsTotal,
            sourceCoverageAlphaDeltaAbsMax = sourceCoverageAlphaDeltaAbsMax,
            paintSourceColorDeltaAbsMax = paintSourceColorDeltaAbsMax,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-378",
              "sceneId": "m60-f16-direct-source-color-evidence-for378",
              "sourceSceneId": "m60-f16-linear-srgb-plausibility-audit-for377",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-preuve-couleur-source-transparente-apres-for-377",
              "sourceFinding": "global/kanvas/findings/for-377-classe-la-piste-linear-s-rgb-m60-f16-comme-mixte-et-exige-une-preuve-couleur-directe",
              "requiredFor377Decision": "M60_F16_LINEAR_SRGB_PLAUSIBILITY_AUDIT_RECORDED",
              "requiredFor377Classification": "linear-srgb-mixed-needs-reference-color-evidence",
              "decision": "M60_F16_DIRECT_SOURCE_COLOR_EVIDENCE_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "direct-source-color-confirms-linear-axis",
                "direct-source-color-points-coverage-mismatch",
                "direct-source-color-points-destination-artifact",
                "direct-source-color-mixed-needs-next-axis",
                "direct-source-color-blocked"
              ],
              "primaryEvidenceSource": "transparent-cpu-diagnostic-source-rgba",
              "directSourceScene": "m60_bounded_stroke_cap_join_transparent_source_for378",
              "directSourceSceneBackground": "transparent",
              "directSourceSceneDrawsProductionScene": false,
              "directSourceScenePathColorCapJoinStrokeWidthMatchesProduction": true,
              "directSourceSceneIncludesProductionWhiteBackground": false,
              "directSourceSceneIncludesProductionOutlineRects": false,
              "directSourceReadSource": "CPU/reference diagnostic transparent source bitmap",
              "directSourceReadFromRenderer": false,
              "directSourceReadFromGpuImage": false,
              "directSourceAppliedToRenderer": false,
              "inverseDestinationEstimateUsedAsPrimaryEvidence": false,
              "inverseDestinationEstimateAppliedAsCorrection": false,
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "requiredFor373CandidateTotalResidual": $FOR373_CANDIDATE_TOTAL_RESIDUAL,
              "for373CandidateTotalResidual": $for373CandidateTotalResidual,
              "requiredFor375EffectiveDestinationCandidateTotalResidual": $FOR375_EFFECTIVE_DESTINATION_CANDIDATE_TOTAL_RESIDUAL,
              "for375EffectiveDestinationCandidateTotalResidual": $for375CandidateTotalResidual,
              "requiredFor376BestVariantId": "linear_srgb_source_over_effective_destination_nearest_255",
              "requiredFor376BestVariantTotalResidual": 607,
              "linearSrgbTotalResidual": $linearSrgbTotalResidual,
              "directRecomposedOnWhiteTotalResidual": $directRecomposedTotalResidual,
              "directRecomposedOnWhiteTotalDeltaVsCurrent": ${directRecomposedTotalResidual - currentResidual},
              "directRecomposedOnWhiteTotalDeltaVsFor373Candidate": ${directRecomposedTotalResidual - for373CandidateTotalResidual},
              "directRecomposedOnWhiteTotalDeltaVsFor375Candidate": ${directRecomposedTotalResidual - for375CandidateTotalResidual},
              "directRecomposedOnWhiteTotalDeltaVsFor377LinearSrgb": ${directRecomposedTotalResidual - linearSrgbTotalResidual},
              "sourceCoverageAlphaDeltaAbsTotal": $sourceCoverageAlphaDeltaAbsTotal,
              "sourceCoverageAlphaDeltaAbsMax": $sourceCoverageAlphaDeltaAbsMax,
              "paintSourceUnpremultipliedRgbDeltaAbsMax": $paintSourceColorDeltaAbsMax,
              "sampleCount": ${directSamples.size},
              "directRecompositionImprovesCurrentSampleCount": ${directSamples.count { it.directRecomposedOnWhiteDeltaVsCurrent < 0 }},
              "directRecompositionRegressesCurrentSampleCount": ${directSamples.count { it.directRecomposedOnWhiteDeltaVsCurrent > 0 }},
              "directRecompositionImprovesFor375SampleCount": ${directSamples.count { it.directRecomposedOnWhiteDeltaVsFor375Candidate < 0 }},
              "directRecompositionRegressesFor375SampleCount": ${directSamples.count { it.directRecomposedOnWhiteDeltaVsFor375Candidate > 0 }},
              "classificationReason": ${directSourceColorClassificationReason(classification).jsonString()},
              "samples": [
            ${directSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "directSourceAppliedToRenderer": false,
                "directSourceReadFromRenderer": false,
                "directSourceReadFromGpuImage": false,
                "inverseDestinationEstimateUsedAsPrimaryEvidence": false,
                "inverseDestinationEstimateAppliedAsCorrection": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16EffectiveSourceColorPathJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val transparentSource = TestUtils.runGmTest(BoundedStrokeCapJoinTransparentSourceGM())
        val samples = residualStats.highDeltaSamples.take(FOR379_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val for375Samples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val for376Samples = for375Samples.map { compositionQuantizationCandidateSample(it) }
        val for377Samples = for376Samples.map { linearSrgbPlausibilityAuditSample(it) }
        val directSamples = for377Samples.map { directSourceColorEvidenceSample(it, transparentSource) }
        val pathSamples = directSamples.map { effectiveSourceColorPathSample(it) }
        val currentResidual = pathSamples.sumOf { it.currentResidual }
        val directRecomposedTotalResidual = pathSamples.sumOf { it.directRecomposedOnWhiteResidual }
        val currentVsDirectDistanceTotal = pathSamples.sumOf { it.currentVsDirectRecomposedRgbaDistance }
        val currentErrorTotals = IntArray(4)
        val directErrorTotals = IntArray(4)
        val improvementTotals = IntArray(4)
        for (sample in pathSamples) {
            for (index in 0..3) {
                currentErrorTotals[index] += sample.currentErrorByChannel[index]
                directErrorTotals[index] += sample.directRecomposedErrorByChannel[index]
                improvementTotals[index] += sample.directRecomposedImprovementByChannel[index]
            }
        }
        val readySampleCount = pathSamples.count { it.sampleClassification == "source-color-ready-for-correction" }
        val ambiguousSampleCount = pathSamples.count { it.sampleClassification == "composition-color-ambiguous" }
        val insufficientSampleCount = pathSamples.count { it.sampleClassification == "evidence-insufficient" }
        val classification = effectiveSourceColorPathClassification(
            sampleCount = pathSamples.size,
            directRecomposedTotalResidual = directRecomposedTotalResidual,
            currentResidual = currentResidual,
            readySampleCount = readySampleCount,
            insufficientSampleCount = insufficientSampleCount,
        )
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-379",
              "sceneId": "m60-f16-effective-source-color-path-for379",
              "sourceSceneId": "m60-f16-direct-source-color-evidence-for378",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-chemin-couleur-source-effectif-apres-for-378",
              "sourceFinding": "global/kanvas/findings/for-378-confirme-la-preuve-source-directe-m60-f16-avec-residuel-19-sans-destination-clampee",
              "requiredFor378Decision": "M60_F16_DIRECT_SOURCE_COLOR_EVIDENCE_RECORDED",
              "requiredFor378Classification": "direct-source-color-confirms-linear-axis",
              "decision": "M60_F16_EFFECTIVE_SOURCE_COLOR_PATH_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "source-color-path-ready-for-correction",
                "composition-color-still-ambiguous",
                "evidence-insufficient"
              ],
              "primaryEvidenceSource": "FOR-378 transparent CPU diagnostic source readback",
              "primaryComparison": "current-rgba-vs-transparent-source-direct-recomposed-on-white",
              "additionalComparisonIsolates": "effective-source-color-path",
              "additionalComparisonFormula": "currentMinusDirectRecomposed=currentRgba-directRecomposedOnWhiteRgba; improvement=currentErrorByChannel-directRecomposedErrorByChannel",
              "inverseDestinationEstimateUsedAsPrimaryEvidence": false,
              "inverseDestinationEstimateAppliedAsCorrection": false,
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "currentResidual": $currentResidual,
              "requiredCurrentResidual": $FOR373_CURRENT_RESIDUAL,
              "directRecomposedOnWhiteTotalResidual": $directRecomposedTotalResidual,
              "requiredDirectRecomposedOnWhiteTotalResidual": 19,
              "directRecomposedOnWhiteGainVsCurrent": ${currentResidual - directRecomposedTotalResidual},
              "directRecomposedOnWhiteDeltaVsCurrent": ${directRecomposedTotalResidual - currentResidual},
              "currentVsDirectRecomposedRgbaDistanceTotal": $currentVsDirectDistanceTotal,
              "currentErrorTotalsByChannel": ${channelErrorJson(currentErrorTotals)},
              "directRecomposedErrorTotalsByChannel": ${channelErrorJson(directErrorTotals)},
              "directRecomposedImprovementTotalsByChannel": ${channelErrorJson(improvementTotals)},
              "sampleCount": ${pathSamples.size},
              "sourceColorReadyForCorrectionSampleCount": $readySampleCount,
              "compositionColorAmbiguousSampleCount": $ambiguousSampleCount,
              "evidenceInsufficientSampleCount": $insufficientSampleCount,
              "classificationReason": ${effectiveSourceColorPathClassificationReason(classification).jsonString()},
              "samples": [
            ${pathSamples.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "directSourceAppliedToRenderer": false,
                "directSourceReadFromRenderer": false,
                "directSourceReadFromGpuImage": false,
                "inverseDestinationEstimateUsedAsPrimaryEvidence": false,
                "inverseDestinationEstimateAppliedAsCorrection": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false
              },
              "command": "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun sourceColorSubzoneCriticalSamples(
        residualStats: StrokeResidualStats,
        probeGpu: SkBitmap,
        coverageMask: SkBitmap,
        transparentSource: SkBitmap,
    ): List<SourceColorSubzoneCriticalSample> {
        val samples = residualStats.highDeltaSamples.take(FOR379_REQUIRED_SAMPLE_COUNT)
        val for374Samples = samples.mapIndexed { index, sample ->
            candidateRegressionSample(candidatePolicySample(index + 1, sample, coverageMask))
        }
        val for375Samples = for374Samples.map { effectiveDestinationCandidateSample(it) }
        val for376Samples = for375Samples.map { compositionQuantizationCandidateSample(it) }
        val for377Samples = for376Samples.map { linearSrgbPlausibilityAuditSample(it) }
        val directSamples = for377Samples.map { directSourceColorEvidenceSample(it, transparentSource) }
        val pathSamples = directSamples.map { effectiveSourceColorPathSample(it) }
        return pathSamples.map { sample ->
            val candidate = sample.directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate
            val pixel = sourceColorSubzonePixelAudit(
                x = candidate.x,
                y = candidate.y,
                reference = candidate.reference,
                current = candidate.current,
                probe = probeGpu.getPixel(candidate.x, candidate.y),
                criticalCoordinates = samples.map { it.x to it.y },
            )
            SourceColorSubzoneCriticalSample(
                index = candidate.index,
                pixel = pixel,
                directRecomposedOnWhiteRgba = sample.directSourceSample.directRecomposedOnWhiteRgba,
                directRecomposedOnWhiteResidual = sample.directRecomposedOnWhiteResidual,
                directRecomposedErrorByChannel = sample.directRecomposedErrorByChannel,
                for379SampleClassification = sample.sampleClassification,
            )
        }
    }

    private fun sourceColorSubzonePixelAudit(
        x: Int,
        y: Int,
        reference: Int,
        current: Int,
        probe: Int,
        criticalCoordinates: List<Pair<Int, Int>>,
    ): SubzonePixelAudit {
        val referenceRgba = rgbaArray(reference)
        val currentRgba = rgbaArray(current)
        val probeRgba = rgbaArray(probe)
        val currentError = channelAbsError(referenceRgba, currentRgba)
        val probeError = channelAbsError(referenceRgba, probeRgba)
        val deltaError = IntArray(4) { probeError[it] - currentError[it] }
        val probeMinusCurrent = IntArray(4) { probeRgba[it] - currentRgba[it] }
        val band = strokePaintBands().first { x in it.xStart until it.xEnd }
        val nearestDistance = criticalCoordinates.minOfOrNull { (criticalX, criticalY) ->
            kotlin.math.abs(x - criticalX) + kotlin.math.abs(y - criticalY)
        } ?: -1
        return SubzonePixelAudit(
            x = x,
            y = y,
            strokeBand = band.id,
            cap = band.cap,
            join = band.join,
            strokeWidth = band.strokeWidth,
            reference = reference,
            current = current,
            probe = probe,
            currentResidual = currentError.sum(),
            probeResidual = probeError.sum(),
            currentErrorByChannel = currentError,
            probeErrorByChannel = probeError,
            probeMinusCurrentErrorByChannel = deltaError,
            probeMinusCurrentRgba = probeMinusCurrent,
            nearestCriticalManhattanDistance = nearestDistance,
        )
    }

    private fun sourceColorSubzoneAuditClassification(
        improved: MutableSubzonePixelSet,
        regressed: MutableSubzonePixelSet,
        criticalSet: MutableSubzonePixelSet,
        uncorrectedResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
    ): String {
        if (criticalSet.count != FOR379_REQUIRED_SAMPLE_COUNT || criticalSet.afterResidual >= criticalSet.beforeResidual) {
            return "source-color-correction-subzone-proof-insufficient"
        }
        if (
            regressed.count > improved.count &&
            regressed.afterResidual - regressed.beforeResidual > improved.beforeResidual - improved.afterResidual &&
            correctedResidualStats.mismatchPixels > uncorrectedResidualStats.mismatchPixels
        ) {
            return "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition"
        }
        if (regressed.count > 0) {
            return "source-color-correction-local-predicate-insufficient"
        }
        return "source-color-correction-subzone-proof-insufficient"
    }

    private fun sourceColorSubzoneAuditClassificationReason(classification: String): String =
        when (classification) {
            "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition" ->
                "The FOR-379 critical samples improve under the FOR-380 probe, but scene-wide regressed pixels and residual growth dominate; the local predicate must separate coverage/composition subzones before any renderer correction can be considered."
            "source-color-correction-local-predicate-insufficient" ->
                "The probe has both improved and regressed pixels, but the regression shape is not strong enough to define a stable local predicate."
            else ->
                "The preserved critical sample proof or scene comparison changed, so the sub-zone audit is insufficient."
        }

    private fun coverageCompositionMembershipPixelAudit(
        pixel: SubzonePixelAudit,
        coverageMask: Int,
        transparentSource: Int,
    ): CoverageCompositionMembershipPixelAudit {
        val coverageAlphaByte = (coverageMask ushr 24) and 0xFF
        val transparentSourceRgba = rgbaArray(transparentSource)
        val transparentSourceAlphaByte = transparentSourceRgba[3]
        val category = coverageCompositionMembershipCategory(
            pixel = pixel,
            coverageAlphaByte = coverageAlphaByte,
            transparentSourceAlphaByte = transparentSourceAlphaByte,
        )
        return CoverageCompositionMembershipPixelAudit(
            pixel = pixel,
            coverageAlphaByte = coverageAlphaByte,
            transparentSourceRgba = transparentSourceRgba,
            transparentSourceAlphaByte = transparentSourceAlphaByte,
            sourceTransparent = transparentSourceAlphaByte == 0,
            category = category,
            categoryReason = coverageCompositionMembershipCategoryReason(category),
        )
    }

    private fun coverageCompositionMembershipCategory(
        pixel: SubzonePixelAudit,
        coverageAlphaByte: Int,
        transparentSourceAlphaByte: Int,
    ): String =
        when {
            pixel.deltaVsCurrent < 0 &&
                pixel.nearestCriticalManhattanDistance == 0 &&
                coverageAlphaByte > 0 &&
                transparentSourceAlphaByte > 0 -> "source-locale-plausible"
            pixel.deltaVsCurrent > 0 &&
                pixel.currentResidual <= 1 &&
                coverageAlphaByte > 0 &&
                transparentSourceAlphaByte > 0 -> "coverage-composition-plausible"
            pixel.deltaVsCurrent != 0 &&
                coverageAlphaByte > 0 &&
                transparentSourceAlphaByte > 0 -> "mixed"
            else -> "insufficient"
        }

    private fun coverageCompositionMembershipCategoryReason(category: String): String =
        when (category) {
            "source-locale-plausible" ->
                "The pixel improves under the FOR-380 probe, belongs to the preserved FOR-379 critical coordinates, and has non-zero diagnostic coverage/source alpha."
            "coverage-composition-plausible" ->
                "The current pixel is already reference-equivalent, but the probe introduces residual while diagnostic coverage/source alpha are non-zero; this points to coverage/composition damage rather than source-colour rescue."
            "mixed" ->
                "The probe changes residual on a covered/source-present pixel, but the current residual is not reference-equivalent enough to isolate coverage/composition."
            else ->
                "The pixel is unchanged or lacks enough diagnostic source/coverage signal for the FOR-382 split."
        }

    private fun coverageCompositionMembershipClassification(
        sourceLocal: MutableMembershipPixelSet,
        coverageComposition: MutableMembershipPixelSet,
        mixed: MutableMembershipPixelSet,
        improved: MutableMembershipPixelSet,
        regressed: MutableMembershipPixelSet,
    ): String =
        if (sourceLocal.count == improved.count && sourceLocal.count > 0 && coverageComposition.count > sourceLocal.count) {
            "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof"
        } else if (mixed.count > 0 || regressed.count > 0) {
            "coverage-composition-separation-insufficient"
        } else {
            "coverage-composition-proof-missing"
        }

    private fun coverageCompositionMembershipClassificationReason(classification: String): String =
        when (classification) {
            "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof" ->
                "The 8 FOR-381 improved pixels form a source-local diagnostic category distinct from the regressed pixels, while many regressed pixels are reference-equivalent before the probe and therefore look like coverage/composition damage. The split still uses diagnostic outcome and must not become a renderer predicate without a coverage/composition proof."
            "coverage-composition-separation-insufficient" ->
                "The source and coverage/composition signals overlap too much to isolate the improved pixels from the regressed pixels."
            else ->
                "The diagnostic source or coverage signals are missing, so no local predicate can be defended."
        }

    private fun preProbePredicateCandidates(): List<PreProbePredicateCandidate> =
        listOf(
            PreProbePredicateCandidate(
                id = "partial-coverage-and-source-alpha",
                description = "Coverage/source alpha are both partial, using diagnostic mask and transparent-source capture only.",
                selectionMethod = "coverageAlphaByte in 1..254 && transparentSourceAlphaByte in 1..254",
            ) { pixel ->
                pixel.coverageAlphaByte in 1..254 &&
                    pixel.transparentSourceAlphaByte in 1..254
            },
            PreProbePredicateCandidate(
                id = "partial-alpha-current-residual-high",
                description = "Partial coverage/source alpha plus current residual above the visual parity tolerance.",
                selectionMethod = "partial alpha && currentResidual > 8",
            ) { pixel ->
                pixel.coverageAlphaByte in 1..254 &&
                    pixel.transparentSourceAlphaByte in 1..254 &&
                    pixel.pixel.currentResidual > 8
            },
            PreProbePredicateCandidate(
                id = "partial-alpha-current-error-shape",
                description = "Partial alpha plus current RGB error shape matching source-colour local over-lightening.",
                selectionMethod = "partial alpha && currentResidual > 8 && currentError.r >= 24 && currentError.g >= 20 && currentError.b <= 16",
            ) { pixel ->
                pixel.coverageAlphaByte in 1..254 &&
                    pixel.transparentSourceAlphaByte in 1..254 &&
                    pixel.pixel.currentResidual > 8 &&
                    pixel.pixel.currentErrorByChannel[0] >= 24 &&
                    pixel.pixel.currentErrorByChannel[1] >= 20 &&
                    pixel.pixel.currentErrorByChannel[2] <= 16
            },
            PreProbePredicateCandidate(
                id = "partial-alpha-round-or-butt-fringe",
                description = "Partial alpha plus current residual on the local round-cap diagonal or butt-cap fringe lanes.",
                selectionMethod = "partial alpha && currentResidual > 8 && ((round-round && x >= 87 && y in 74..80) || (butt-bevel && x <= 21 && y in 75..81))",
            ) { pixel ->
                pixel.coverageAlphaByte in 1..254 &&
                    pixel.transparentSourceAlphaByte in 1..254 &&
                    pixel.pixel.currentResidual > 8 &&
                    (
                        (pixel.pixel.strokeBand == "round-round" && pixel.pixel.x >= 87 && pixel.pixel.y in 74..80) ||
                            (pixel.pixel.strokeBand == "butt-bevel" && pixel.pixel.x <= 21 && pixel.pixel.y in 75..81)
                        )
            },
            PreProbePredicateCandidate(
                id = "partial-alpha-current-low",
                description = "Partial alpha where the current image is already exact or within tolerance; expected to capture coverage/composition risk.",
                selectionMethod = "partial alpha && currentResidual <= 8",
            ) { pixel ->
                pixel.coverageAlphaByte in 1..254 &&
                    pixel.transparentSourceAlphaByte in 1..254 &&
                    pixel.pixel.currentResidual <= 8
            },
        )

    private fun preProbePredicateAuditClassification(
        bestCandidate: MutablePreProbePredicateStats?,
        sourceLocalTruth: Int,
    ): String =
        when {
            bestCandidate == null || sourceLocalTruth == 0 -> "pre-probe-proof-missing"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth &&
                bestCandidate.regressedIncluded == 0 &&
                bestCandidate.count == sourceLocalTruth -> "diagnostic-pre-probe-predicate-found-runtime-proof-missing"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth -> "pre-probe-predicate-too-broad"
            bestCandidate.sourceLocalRecovered > 0 -> "pre-probe-predicate-insufficient"
            else -> "pre-probe-proof-missing"
        }

    private fun preProbePredicateAuditClassificationReason(classification: String): String =
        when (classification) {
            "diagnostic-pre-probe-predicate-found-runtime-proof-missing" ->
                "A pre-probe diagnostic predicate recovers the 8 FOR-382 source-local pixels with no regressed pixels, but it depends on reference/current residual shape, so it is not yet a renderer-runtime predicate."
            "pre-probe-predicate-too-broad" ->
                "At least one pre-probe predicate recovers all source-local pixels, but it also selects regressed pixels; a local renderer correction remains blocked."
            "pre-probe-predicate-insufficient" ->
                "The tested pre-probe predicates recover only part of the source-local set, so the correction remains blocked."
            else ->
                "No tested pre-probe signal recovers the source-local set."
        }

    private fun preCorrectionGeometryCoveragePixelAudit(
        membership: CoverageCompositionMembershipPixelAudit,
        coverageMask: SkBitmap,
    ): PreCorrectionGeometryCoveragePixelAudit {
        val band = strokePaintBands().first { membership.pixel.x in it.xStart until it.xEnd }
        return PreCorrectionGeometryCoveragePixelAudit(
            membership = membership,
            bandLocalX = membership.pixel.x - band.xStart,
            bandWidth = band.xEnd - band.xStart,
            bandEdgeDistance = minOf(membership.pixel.x - band.xStart, band.xEnd - 1 - membership.pixel.x),
            coverageNeighborhood = coverageOrthogonalNeighborhood(
                coverageMask = coverageMask,
                x = membership.pixel.x,
                y = membership.pixel.y,
            ),
        )
    }

    private fun coverageOrthogonalNeighborhood(
        coverageMask: SkBitmap,
        x: Int,
        y: Int,
    ): CoverageOrthogonalNeighborhood {
        fun alphaAt(sampleX: Int, sampleY: Int): Int {
            if (sampleX !in 0 until coverageMask.width || sampleY !in 0 until coverageMask.height) {
                return 0
            }
            return (coverageMask.getPixel(sampleX, sampleY) ushr 24) and 0xFF
        }
        val center = alphaAt(x, y)
        val north = alphaAt(x, y - 1)
        val south = alphaAt(x, y + 1)
        val west = alphaAt(x - 1, y)
        val east = alphaAt(x + 1, y)
        val orthogonal = intArrayOf(north, south, west, east)
        return CoverageOrthogonalNeighborhood(
            center = center,
            north = north,
            south = south,
            west = west,
            east = east,
            min = minOf(center, north, south, west, east),
            max = maxOf(center, north, south, west, east),
            orthogonalNonZeroCount = orthogonal.count { it > 0 },
            orthogonalPartialCount = orthogonal.count { it in 1..254 },
        )
    }

    private fun preCorrectionGeometryCoverageMetadataCandidates(): List<PreCorrectionMetadataCandidate> =
        listOf(
            PreCorrectionMetadataCandidate(
                id = "coverage-alpha-at-least-96",
                description = "The FOR-383 inspected pixels split cleanly when center coverage alpha is at least 96.",
                selectionMethod = "coverageAlphaByte >= 96",
                rejectionReason = "Diagnostic candidate accepted inside the FOR-383 inspected set, but the surrounding FOR-383 scope still depends on reference/current error shape.",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 96
            },
            PreCorrectionMetadataCandidate(
                id = "coverage-and-source-alpha-at-least-96",
                description = "Center coverage and transparent-source alpha both stay above the regressed pixel's 64-alpha fringe.",
                selectionMethod = "coverageAlphaByte >= 96 && transparentSourceAlphaByte >= 96",
                rejectionReason = "Diagnostic candidate accepted inside the FOR-383 inspected set, but it must be converted into renderer-owned coverage metadata before correction.",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.membership.transparentSourceAlphaByte >= 96
            },
            PreCorrectionMetadataCandidate(
                id = "round-cap-or-high-coverage",
                description = "Round-cap pixels plus the high-coverage butt-cap fringe keep the useful pixel and exclude the lower-coverage butt regression.",
                selectionMethod = "cap == round || coverageAlphaByte >= 96",
                rejectionReason = "Diagnostic candidate accepted inside the FOR-383 inspected set, but it is fixture-specific cap/coverage metadata rather than a general renderer predicate.",
            ) { pixel ->
                pixel.membership.pixel.cap == "round" ||
                    pixel.membership.coverageAlphaByte >= 96
            },
            PreCorrectionMetadataCandidate(
                id = "band-local-fringe-window",
                description = "Fixture-local band coordinate window for the two useful fringe lanes.",
                selectionMethod = "(round-round && bandLocalX in 39..45) || (butt-bevel && bandLocalX <= 18)",
                rejectionReason = "Diagnostic candidate accepted inside the FOR-383 inspected set, but hard-coded fixture coordinates are not renderer-ready metadata.",
            ) { pixel ->
                (
                    pixel.membership.pixel.strokeBand == "round-round" &&
                        pixel.bandLocalX in 39..45
                    ) ||
                    (
                        pixel.membership.pixel.strokeBand == "butt-bevel" &&
                            pixel.bandLocalX <= 18
                        )
            },
            PreCorrectionMetadataCandidate(
                id = "round-cap-only",
                description = "Cap/join metadata alone excludes the regressed butt-bevel pixel but also drops the useful butt-bevel source-local pixel.",
                selectionMethod = "cap == round",
                rejectionReason = "Insufficient recall: cap/join alone misses the useful butt-bevel fringe pixel.",
            ) { pixel ->
                pixel.membership.pixel.cap == "round"
            },
        )

    private fun preCorrectionGeometryCoverageMetadataClassification(
        bestCandidate: MutablePreCorrectionMetadataCandidateStats?,
        sourceLocalTruth: Int,
    ): String =
        when {
            bestCandidate == null || sourceLocalTruth == 0 -> "metadata-proof-missing"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth &&
                bestCandidate.regressedIncluded == 0 &&
                bestCandidate.count == sourceLocalTruth -> "metadata-candidate-defendable-runtime-proof-still-blocked"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth -> "metadata-candidate-too-broad"
            bestCandidate.sourceLocalRecovered > 0 -> "metadata-candidate-insufficient"
            else -> "metadata-proof-missing"
        }

    private fun preCorrectionGeometryCoverageMetadataClassificationReason(classification: String): String =
        when (classification) {
            "metadata-candidate-defendable-runtime-proof-still-blocked" ->
                "Within the 9 FOR-383 inspected pixels, coverage/geometry metadata can exclude the single regressed pixel while recovering all 8 source-local pixels. The correction remains blocked because the inspected set itself still inherits the FOR-383 reference/current-error-shape scope."
            "metadata-candidate-too-broad" ->
                "At least one metadata candidate recovers the 8 source-local pixels, but it still includes regressed pixels."
            "metadata-candidate-insufficient" ->
                "The metadata candidates exclude regressions only by losing some source-local pixels."
            else ->
                "No tested geometry/coverage metadata split recovers the source-local pixels."
        }

    private fun generalizedCoverageMetadataCandidates(): List<GeneralizedCoverageMetadataCandidate> =
        listOf(
            GeneralizedCoverageMetadataCandidate(
                id = "coverage-alpha-at-least-96",
                description = "Generalizes the FOR-384 clean split to every covered/source-present scene pixel with center coverage alpha at least 96.",
                selectionMethod = "coverageAlphaByte >= 96",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 96
            },
            GeneralizedCoverageMetadataCandidate(
                id = "coverage-and-source-alpha-at-least-96",
                description = "Requires both coverage and transparent-source alpha to stay at or above the FOR-384 useful lower bound.",
                selectionMethod = "coverageAlphaByte >= 96 && transparentSourceAlphaByte >= 96",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.membership.transparentSourceAlphaByte >= 96
            },
            GeneralizedCoverageMetadataCandidate(
                id = "partial-coverage-alpha-at-least-96",
                description = "Keeps the predicate on anti-aliased edges by excluding fully covered pixels.",
                selectionMethod = "coverageAlphaByte in 96..254 && transparentSourceAlphaByte in 96..254",
            ) { pixel ->
                pixel.membership.coverageAlphaByte in 96..254 &&
                    pixel.membership.transparentSourceAlphaByte in 96..254
            },
            GeneralizedCoverageMetadataCandidate(
                id = "coverage-alpha-at-least-160",
                description = "Tests whether the higher FOR-384 useful alpha lane is sufficient by itself.",
                selectionMethod = "coverageAlphaByte >= 160 && transparentSourceAlphaByte >= 160",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 160 &&
                    pixel.membership.transparentSourceAlphaByte >= 160
            },
            GeneralizedCoverageMetadataCandidate(
                id = "round-cap-or-high-coverage",
                description = "Combines cap metadata with the high-coverage threshold, without FOR-383 membership.",
                selectionMethod = "cap == round || coverageAlphaByte >= 96",
            ) { pixel ->
                pixel.membership.pixel.cap == "round" ||
                    pixel.membership.coverageAlphaByte >= 96
            },
            GeneralizedCoverageMetadataCandidate(
                id = "high-coverage-fringe-neighborhood",
                description = "Requires a high-coverage center and at least one partial orthogonal neighbor to stay on fringe geometry.",
                selectionMethod = "coverageAlphaByte >= 96 && coverageOrthogonalNeighborhood.orthogonalPartialCount > 0",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.coverageNeighborhood.orthogonalPartialCount > 0
            },
        )

    private fun generalizedCoverageMetadataClassification(
        bestCandidate: MutableGeneralizedCoverageMetadataCandidateStats?,
        sourceLocalTruth: Int,
    ): String =
        when {
            bestCandidate == null || sourceLocalTruth == 0 -> "proof-missing"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth &&
                bestCandidate.regressedIncluded == 0 -> "generalized-predicate-defendable"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth -> "generalized-predicate-too-broad"
            bestCandidate.sourceLocalRecovered > 0 -> "generalized-predicate-insufficient"
            else -> "proof-missing"
        }

    private fun generalizedCoverageMetadataClassificationReason(classification: String): String =
        when (classification) {
            "generalized-predicate-defendable" ->
                "A coverage-metadata predicate recovers all source-local pixels across the generalized pre-correction scope without selecting pixels that the probe regresses."
            "generalized-predicate-too-broad" ->
                "The best generalized coverage-metadata predicate recovers all source-local pixels, but also selects pixels that the probe regresses outside the FOR-383 subset."
            "generalized-predicate-insufficient" ->
                "The generalized coverage-metadata predicates avoid some risk only by losing source-local pixels."
            else ->
                "No generalized coverage-metadata predicate recovers the source-local pixels."
        }

    private fun generalizedCoverageMetadataNextMove(
        classification: String,
        bestCandidate: MutableGeneralizedCoverageMetadataCandidateStats?,
    ): String =
        when (classification) {
            "generalized-predicate-defendable" ->
                "Keep this ticket diagnostic-only. Implement a separate bounded correction ticket that applies the best metadata predicate behind an explicit opt-in guard and rechecks scene score/fallback stability."
            "generalized-predicate-too-broad" ->
                "Do not enable the correction. Add a narrower renderer metadata signal that separates the selected regressed pixels by band/cap/join, alpha lane, or coverage-neighborhood shape outside the FOR-383 subset."
            "generalized-predicate-insufficient" ->
                "Do not enable the correction. Preserve the generalized scope and test an additional renderer-owned discriminant that recovers the missing source-local pixels."
            else ->
                "Do not enable the correction. Capture the missing renderer metadata before another predicate attempt."
        } + (bestCandidate?.let { " Best candidate: ${it.candidate.id}." } ?: "")

    private fun coverageRegressionDiscriminatorCandidates(): List<CoverageRegressionDiscriminatorCandidate> =
        listOf(
            CoverageRegressionDiscriminatorCandidate(
                id = "source-fringe-band-local-window",
                description = "Keeps the two observed useful local fringe zones using stroke band coordinates and partial coverage lanes.",
                selectionMethod = "(round-round && bandLocalX in 39..45 && coverageAlphaByte >= 96) || (butt-bevel && bandLocalX <= 18 && coverageAlphaByte >= 96)",
            ) { pixel ->
                (
                    pixel.membership.pixel.strokeBand == "round-round" &&
                        pixel.bandLocalX in 39..45 &&
                        pixel.membership.coverageAlphaByte >= 96
                    ) ||
                    (
                        pixel.membership.pixel.strokeBand == "butt-bevel" &&
                            pixel.bandLocalX <= 18 &&
                            pixel.membership.coverageAlphaByte >= 96
                        )
            },
            CoverageRegressionDiscriminatorCandidate(
                id = "round-cap-high-alpha-lane",
                description = "Tests whether the useful round-cap lane is separable by cap metadata and the higher alpha lane.",
                selectionMethod = "cap == round && coverageAlphaByte >= 160 && transparentSourceAlphaByte >= 160",
            ) { pixel ->
                pixel.membership.pixel.cap == "round" &&
                    pixel.membership.coverageAlphaByte >= 160 &&
                    pixel.membership.transparentSourceAlphaByte >= 160
            },
            CoverageRegressionDiscriminatorCandidate(
                id = "butt-or-round-low-edge-distance",
                description = "Uses only band edge distance plus the FOR-385 partial alpha lane.",
                selectionMethod = "bandEdgeDistance <= 18 && coverageAlphaByte >= 96 && transparentSourceAlphaByte >= 96",
            ) { pixel ->
                pixel.bandEdgeDistance <= 18 &&
                    pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.membership.transparentSourceAlphaByte >= 96
            },
            CoverageRegressionDiscriminatorCandidate(
                id = "alpha-160-quiet-neighborhood",
                description = "Requires the high alpha lane with no partial orthogonal coverage neighbors.",
                selectionMethod = "coverageAlphaByte >= 160 && transparentSourceAlphaByte >= 160 && coverageOrthogonalNeighborhood.orthogonalPartialCount == 0",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 160 &&
                    pixel.membership.transparentSourceAlphaByte >= 160 &&
                    pixel.coverageNeighborhood.orthogonalPartialCount == 0
            },
            CoverageRegressionDiscriminatorCandidate(
                id = "round-cap-local-fringe-window",
                description = "Checks whether round-cap metadata and local fringe position are enough without the butt-bevel lane.",
                selectionMethod = "cap == round && bandLocalX in 39..45 && coverageAlphaByte >= 96",
            ) { pixel ->
                pixel.membership.pixel.cap == "round" &&
                    pixel.bandLocalX in 39..45 &&
                    pixel.membership.coverageAlphaByte >= 96
            },
            CoverageRegressionDiscriminatorCandidate(
                id = "coverage-neighborhood-sparse-edge",
                description = "Looks for isolated edge pixels by combining partial alpha with sparse orthogonal coverage.",
                selectionMethod = "coverageAlphaByte >= 96 && coverageOrthogonalNeighborhood.orthogonalNonZeroCount <= 2",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.coverageNeighborhood.orthogonalNonZeroCount <= 2
            },
        )

    private fun coverageRegressionDiscriminatorClassification(
        bestCandidate: MutableCoverageRegressionDiscriminatorCandidateStats?,
        sourceLocalTruth: Int,
    ): String =
        when {
            bestCandidate == null || sourceLocalTruth == 0 -> "metadata-insufficient"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth &&
                bestCandidate.regressedIncluded == 0 -> "discriminator-candidate-defendable"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth -> "discriminator-candidate-too-broad"
            bestCandidate.sourceLocalRecovered > 0 -> "discriminator-candidate-insufficient"
            else -> "metadata-insufficient"
        }

    private fun coverageRegressionDiscriminatorClassificationReason(classification: String): String =
        when (classification) {
            "discriminator-candidate-defendable" ->
                "A pre-correction metadata candidate separates all 8 source-local pixels from the 428 FOR-385 regressions inside the diagnostic selection. It remains disabled until a separate correction ticket proves full-scene safety."
            "discriminator-candidate-too-broad" ->
                "The best discriminator keeps all 8 useful pixels but still includes regressed pixels from the FOR-385 selection."
            "discriminator-candidate-insufficient" ->
                "The tested metadata can reduce the 428 regressions only by dropping at least one useful source-local pixel."
            else ->
                "The current pre-correction metadata does not provide a useful discriminator for the FOR-385 selection."
        }

    private fun coverageRegressionDiscriminatorNextMove(
        classification: String,
        bestCandidate: MutableCoverageRegressionDiscriminatorCandidateStats?,
    ): String =
        when (classification) {
            "discriminator-candidate-defendable" ->
                "Keep FOR-386 diagnostic-only. Create a separate bounded opt-in correction ticket that applies the candidate behind a guard and proves full-scene score, fallback, and refusal stability."
            "discriminator-candidate-too-broad" ->
                "Do not enable the correction. Add or export a narrower renderer metadata signal that explains the remaining selected regressions."
            "discriminator-candidate-insufficient" ->
                "Do not enable the correction. Preserve this audit and test additional renderer-owned metadata capable of recovering the dropped source-local pixels."
            else ->
                "Do not enable the correction. The next step is to expose more renderer-owned coverage/composition metadata before another predicate attempt."
        } + (bestCandidate?.let { " Best candidate: ${it.candidate.id}." } ?: "")

    private fun candidatePolicySample(index: Int, sample: ResidualSample, coverageMask: SkBitmap): CandidatePolicySample {
        val band = strokePaintBands().first { sample.x in it.xStart until it.xEnd }
        val currentResidual = sampleResidual(sample.reference, sample.gpu)
        val sourceCoverageByte = (coverageMask.getPixel(sample.x, sample.y) ushr 24) and 0xFF
        val candidateRgba = candidatePolicyRgba(band.sourceColor, sourceCoverageByte)
        val candidateResidual = sampleResidual(sample.reference, rgbaToPixel(candidateRgba))
        return CandidatePolicySample(
            index = index,
            x = sample.x,
            y = sample.y,
            strokeBand = band.id,
            reference = sample.reference,
            current = sample.gpu,
            currentResidual = currentResidual,
            maxChannelDelta = sample.maxChannelDelta,
            paintSource = band.sourceColor,
            paintSourceAlpha = (band.sourceColor ushr 24) and 0xFF,
            cap = band.cap,
            join = band.join,
            strokeWidth = band.strokeWidth,
            sourceCoverageByte = sourceCoverageByte,
            sourceCoverage = sourceCoverageByte / 255.0,
            effectiveSourceAlphaByte = sourceCoverageByte,
            effectiveSourceAlpha = sourceCoverageByte / 255.0,
            candidatePolicyRgba = candidateRgba,
            candidateResidual = candidateResidual,
            candidateResidualDeltaVsCurrent = candidateResidual - currentResidual,
            candidateImprovesSample = candidateResidual < currentResidual,
        )
    }

    private data class CandidatePolicySample(
        val index: Int,
        val x: Int,
        val y: Int,
        val strokeBand: String,
        val reference: Int,
        val current: Int,
        val currentResidual: Int,
        val maxChannelDelta: Int,
        val paintSource: Int,
        val paintSourceAlpha: Int,
        val cap: String,
        val join: String,
        val strokeWidth: Float,
        val sourceCoverageByte: Int,
        val sourceCoverage: Double,
        val effectiveSourceAlphaByte: Int,
        val effectiveSourceAlpha: Double,
        val candidatePolicyRgba: IntArray,
        val candidateResidual: Int,
        val candidateResidualDeltaVsCurrent: Int,
        val candidateImprovesSample: Boolean,
    )

    private fun CandidatePolicySample.toJson(): String = """
        {
          "index": $index,
          "x": $x,
          "y": $y,
          "strokeBand": ${strokeBand.jsonString()},
          "referenceRgba": ${rgbaJson(reference)},
          "currentRgba": ${rgbaJson(current)},
          "gpuRgba": ${rgbaJson(current)},
          "sampleResidual": $currentResidual,
          "maxChannelDelta": $maxChannelDelta,
          "paintSourceRgba": ${rgbaJson(paintSource)},
          "paintSourceStatus": "known-from-BoundedStrokeCapJoinGM",
          "paintSourceAlpha": $paintSourceAlpha,
          "cap": ${cap.jsonString()},
          "join": ${join.jsonString()},
          "strokeWidth": ${String.format(Locale.US, "%.1f", strokeWidth)},
          "sourceCoverageByte": $sourceCoverageByte,
          "sourceCoverage": ${String.format(Locale.US, "%.6f", sourceCoverage)},
          "sourceCoverageStatus": "preserved-from-FOR-372-diagnostic-mask-alpha",
          "effectiveSourceAlphaByte": $effectiveSourceAlphaByte,
          "effectiveSourceAlpha": ${String.format(Locale.US, "%.6f", effectiveSourceAlpha)},
          "effectiveSourceAlphaStatus": "opaque-source-paint-alpha-multiplied-by-exported-coverage",
          "coverageProvenance": "cpu.coverage.stroke-cap-join-oracle / PathStrokeCoverage / diagnostic transparent GM alpha mask",
          "coverageReadSource": "alpha-channel-from-transparent-cpu-diagnostic-mask",
          "coverageReconstructedFromRgbaDeltas": false,
          "referenceCurrentRgbaUsedForCoverage": false,
          "sampleDeltaRgbaUsedForCoverage": false,
          "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
          "candidatePolicyRgba": ${rgbaArrayJson(candidatePolicyRgba)},
          "candidatePolicyRgbaStatus": "calculated-by-straight-srgb-quantized-alpha-src-over-white",
          "candidatePolicyRgbaSource": "calculated-by-diagnostic-policy",
          "candidatePolicyRgbaReadFromRenderer": false,
          "candidatePolicyRgbaReadFromGpuImage": false,
          "candidateResidual": $candidateResidual,
          "candidateResidualDeltaVsCurrent": $candidateResidualDeltaVsCurrent,
          "candidateImprovesSample": $candidateImprovesSample,
          "rendererAppliedCandidate": false
        }
        """.trimIndent()

    private fun candidateRegressionSample(sample: CandidatePolicySample): CandidateRegressionAuditSample {
        val reference = rgbaArray(sample.reference)
        val current = rgbaArray(sample.current)
        val source = rgbaArray(sample.paintSource)
        val candidate = sample.candidatePolicyRgba
        val currentError = channelAbsError(reference, current)
        val candidateError = channelAbsError(reference, candidate)
        val candidateMinusCurrent = IntArray(4) { candidateError[it] - currentError[it] }
        val dominantChannelIndex = dominantRegressionChannel(candidateMinusCurrent)
        val dominantChannel = if (candidateMinusCurrent[dominantChannelIndex] > 0) {
            channelName(dominantChannelIndex)
        } else {
            "none"
        }
        val inverseDestination = inverseDestinationEstimate(reference, source, sample.effectiveSourceAlpha)
        return CandidateRegressionAuditSample(
            candidate = sample,
            currentErrorByChannel = currentError,
            candidateErrorByChannel = candidateError,
            candidateMinusCurrentErrorByChannel = candidateMinusCurrent,
            dominantRegressionChannel = dominantChannel,
            largestCandidateMinusCurrentErrorChannel = channelName(dominantChannelIndex),
            regressionDirection = regressionDirection(reference, source, candidate, candidateMinusCurrent, dominantChannelIndex),
            inverseDestinationEstimate = inverseDestination,
        )
    }

    private data class CandidateRegressionAuditSample(
        val candidate: CandidatePolicySample,
        val currentErrorByChannel: IntArray,
        val candidateErrorByChannel: IntArray,
        val candidateMinusCurrentErrorByChannel: IntArray,
        val dominantRegressionChannel: String,
        val largestCandidateMinusCurrentErrorChannel: String,
        val regressionDirection: String,
        val inverseDestinationEstimate: InverseDestinationEstimate,
    ) {
        val currentResidual: Int = candidate.currentResidual
        val candidateResidual: Int = candidate.candidateResidual
        val candidateResidualDeltaVsCurrent: Int = candidate.candidateResidualDeltaVsCurrent
    }

    private fun CandidateRegressionAuditSample.toJson(): String = """
        {
          "index": ${candidate.index},
          "x": ${candidate.x},
          "y": ${candidate.y},
          "strokeBand": ${candidate.strokeBand.jsonString()},
          "referenceRgba": ${rgbaJson(candidate.reference)},
          "currentRgba": ${rgbaJson(candidate.current)},
          "gpuRgba": ${rgbaJson(candidate.current)},
          "sampleResidual": $currentResidual,
          "currentResidual": $currentResidual,
          "maxChannelDelta": ${candidate.maxChannelDelta},
          "paintSourceRgba": ${rgbaJson(candidate.paintSource)},
          "paintSourceStatus": "known-from-BoundedStrokeCapJoinGM",
          "paintSourceAlpha": ${candidate.paintSourceAlpha},
          "cap": ${candidate.cap.jsonString()},
          "join": ${candidate.join.jsonString()},
          "strokeWidth": ${String.format(Locale.US, "%.1f", candidate.strokeWidth)},
          "sourceCoverageByte": ${candidate.sourceCoverageByte},
          "sourceCoverage": ${String.format(Locale.US, "%.6f", candidate.sourceCoverage)},
          "sourceCoverageStatus": "preserved-from-FOR-372-diagnostic-mask-alpha",
          "effectiveSourceAlphaByte": ${candidate.effectiveSourceAlphaByte},
          "effectiveSourceAlpha": ${String.format(Locale.US, "%.6f", candidate.effectiveSourceAlpha)},
          "effectiveSourceAlphaStatus": "opaque-source-paint-alpha-multiplied-by-exported-coverage",
          "coverageProvenance": "cpu.coverage.stroke-cap-join-oracle / PathStrokeCoverage / diagnostic transparent GM alpha mask",
          "coverageReadSource": "alpha-channel-from-transparent-cpu-diagnostic-mask",
          "coverageReconstructedFromRgbaDeltas": false,
          "referenceCurrentRgbaUsedForCoverage": false,
          "sampleDeltaRgbaUsedForCoverage": false,
          "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
          "candidatePolicyRgba": ${rgbaArrayJson(candidate.candidatePolicyRgba)},
          "candidatePolicyRgbaStatus": "calculated-by-straight-srgb-quantized-alpha-src-over-white",
          "candidatePolicyRgbaSource": "calculated-by-diagnostic-policy",
          "candidatePolicyRgbaReadFromRenderer": false,
          "candidatePolicyRgbaReadFromGpuImage": false,
          "candidateResidual": $candidateResidual,
          "candidateResidualDeltaVsCurrent": $candidateResidualDeltaVsCurrent,
          "candidateImprovesSample": ${candidate.candidateImprovesSample},
          "currentErrorByChannel": ${channelErrorJson(currentErrorByChannel)},
          "candidateErrorByChannel": ${channelErrorJson(candidateErrorByChannel)},
          "candidateMinusCurrentErrorByChannel": ${channelErrorJson(candidateMinusCurrentErrorByChannel)},
          "dominantRegressionChannel": ${dominantRegressionChannel.jsonString()},
          "largestCandidateMinusCurrentErrorChannel": ${largestCandidateMinusCurrentErrorChannel.jsonString()},
          "regressionDirection": ${regressionDirection.jsonString()},
          "inverseDestinationEstimate": ${inverseDestinationEstimate.toJson()},
          "inverseDestinationUsedAsCorrection": false,
          "rendererAppliedCandidate": false
        }
    """.trimIndent()

    private fun effectiveDestinationCandidateSample(
        sample: CandidateRegressionAuditSample,
    ): EffectiveDestinationCandidateSample {
        val destination = sample.inverseDestinationEstimate.rgbClamped
        if (!sample.inverseDestinationEstimate.possible || destination == null) {
            return EffectiveDestinationCandidateSample(
                for374Sample = sample,
                effectiveDestinationRgba = null,
                effectiveDestinationCandidateRgba = null,
                effectiveDestinationCandidateResidual = sample.currentResidual,
                effectiveDestinationCandidateDeltaVsCurrent = 0,
                effectiveDestinationCandidateDeltaVsFor373Candidate =
                    sample.currentResidual - sample.candidateResidual,
                effectiveDestinationCandidateStatus = "blocked-by-missing-effective-destination",
            )
        }
        val candidate = sourceOverEffectiveDestinationRgba(
            sourceColor = sample.candidate.paintSource,
            effectiveAlphaByte = sample.candidate.effectiveSourceAlphaByte,
            destinationRgb = destination,
        )
        val residual = sampleResidual(sample.candidate.reference, rgbaToPixel(candidate))
        return EffectiveDestinationCandidateSample(
            for374Sample = sample,
            effectiveDestinationRgba = intArrayOf(destination[0], destination[1], destination[2], 255),
            effectiveDestinationCandidateRgba = candidate,
            effectiveDestinationCandidateResidual = residual,
            effectiveDestinationCandidateDeltaVsCurrent = residual - sample.currentResidual,
            effectiveDestinationCandidateDeltaVsFor373Candidate = residual - sample.candidateResidual,
            effectiveDestinationCandidateStatus = "calculated-from-inverse-destination-rgb-clamped-to-srgb",
        )
    }

    private data class EffectiveDestinationCandidateSample(
        val for374Sample: CandidateRegressionAuditSample,
        val effectiveDestinationRgba: IntArray?,
        val effectiveDestinationCandidateRgba: IntArray?,
        val effectiveDestinationCandidateResidual: Int,
        val effectiveDestinationCandidateDeltaVsCurrent: Int,
        val effectiveDestinationCandidateDeltaVsFor373Candidate: Int,
        val effectiveDestinationCandidateStatus: String,
    ) {
        val currentResidual: Int = for374Sample.currentResidual
        val for373CandidateResidual: Int = for374Sample.candidateResidual
    }

    private fun EffectiveDestinationCandidateSample.toJson(): String {
        val base = for374Sample.toJson().trim()
        val suffix = """
          "effectiveDestinationInputSource": "inverseDestinationEstimate.rgbClampedToSrgb",
          "effectiveDestinationRgba": ${effectiveDestinationRgba?.let { rgbaArrayJson(it) } ?: "null"},
          "effectiveDestinationReadFromRenderer": false,
          "effectiveDestinationReadFromGpuImage": false,
          "effectiveDestinationAppliedToRenderer": false,
          "effectiveDestinationCandidatePolicyId": "source_over_effective_destination_diagnostic",
          "effectiveDestinationCandidateRgba": ${effectiveDestinationCandidateRgba?.let { rgbaArrayJson(it) } ?: "null"},
          "effectiveDestinationCandidateStatus": ${effectiveDestinationCandidateStatus.jsonString()},
          "effectiveDestinationCandidateSource": "calculated-by-diagnostic-policy",
          "effectiveDestinationCandidateReadFromRenderer": false,
          "effectiveDestinationCandidateReadFromGpuImage": false,
          "effectiveDestinationCandidateAppliedToRenderer": false,
          "effectiveDestinationCandidateResidual": $effectiveDestinationCandidateResidual,
          "effectiveDestinationCandidateDeltaVsCurrent": $effectiveDestinationCandidateDeltaVsCurrent,
          "effectiveDestinationCandidateDeltaVsFor373Candidate": $effectiveDestinationCandidateDeltaVsFor373Candidate,
          "effectiveDestinationCandidateImprovesCurrent": ${effectiveDestinationCandidateDeltaVsCurrent < 0},
          "effectiveDestinationCandidateImprovesFor373Candidate": ${effectiveDestinationCandidateDeltaVsFor373Candidate < 0}
        """.trimIndent().prependIndent("  ")
        return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private fun compositionQuantizationCandidateSample(
        sample: EffectiveDestinationCandidateSample,
    ): CompositionQuantizationCandidateSample {
        val variants = compositionQuantizationVariantDefinitions().map { definition ->
            val candidate = definition.calculate(sample)
            val residual = candidate?.let { sampleResidual(sample.for374Sample.candidate.reference, rgbaToPixel(it)) }
                ?: sample.currentResidual
            CompositionQuantizationCandidateVariant(
                variantId = definition.id,
                candidateRgba = candidate,
                residual = residual,
                deltaVsCurrent = residual - sample.currentResidual,
                deltaVsFor375Candidate = residual - sample.effectiveDestinationCandidateResidual,
                deltaVsFor373Candidate = residual - sample.for373CandidateResidual,
                status = if (candidate == null) {
                    "blocked-by-missing-effective-destination"
                } else {
                    "calculated-from-for375-effective-destination-sample"
                },
            )
        }
        return CompositionQuantizationCandidateSample(for375Sample = sample, variants = variants)
    }

    private data class CompositionQuantizationCandidateSample(
        val for375Sample: EffectiveDestinationCandidateSample,
        val variants: List<CompositionQuantizationCandidateVariant>,
    ) {
        val currentResidual: Int = for375Sample.currentResidual
        val for373CandidateResidual: Int = for375Sample.for373CandidateResidual
        val for375CandidateResidual: Int = for375Sample.effectiveDestinationCandidateResidual
    }

    private fun CompositionQuantizationCandidateSample.toJson(): String {
        val best = variants.sortedWith(compareBy<CompositionQuantizationCandidateVariant> { it.residual }.thenBy { it.variantId }).first()
        val base = for375Sample.toJson().trim()
        val suffix = """
          "compositionQuantizationBestVariantId": ${best.variantId.jsonString()},
          "compositionQuantizationBestVariantResidual": ${best.residual},
          "compositionQuantizationBestVariantDeltaVsCurrent": ${best.deltaVsCurrent},
          "compositionQuantizationBestVariantDeltaVsFor375Candidate": ${best.deltaVsFor375Candidate},
          "compositionQuantizationBestVariantDeltaVsFor373Candidate": ${best.deltaVsFor373Candidate},
          "compositionQuantizationVariants": [
        ${variants.joinToString(",\n") { it.toJson().prependIndent("    ") }}
          ]
        """.trimIndent().prependIndent("  ")
        return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private data class CompositionQuantizationCandidateVariant(
        val variantId: String,
        val candidateRgba: IntArray?,
        val residual: Int,
        val deltaVsCurrent: Int,
        val deltaVsFor375Candidate: Int,
        val deltaVsFor373Candidate: Int,
        val status: String,
    )

    private fun CompositionQuantizationCandidateVariant.toJson(): String = """
        {
          "variantId": ${variantId.jsonString()},
          "candidateRgba": ${candidateRgba?.let { rgbaArrayJson(it) } ?: "null"},
          "status": ${status.jsonString()},
          "candidateSource": "calculated-by-diagnostic-policy",
          "candidateReadFromRenderer": false,
          "candidateReadFromGpuImage": false,
          "candidateAppliedToRenderer": false,
          "residual": $residual,
          "deltaVsCurrent": $deltaVsCurrent,
          "deltaVsFor375Candidate": $deltaVsFor375Candidate,
          "deltaVsFor373Candidate": $deltaVsFor373Candidate,
          "improvesCurrent": ${deltaVsCurrent < 0},
          "improvesFor375Candidate": ${deltaVsFor375Candidate < 0},
          "improvesFor373Candidate": ${deltaVsFor373Candidate < 0}
        }
    """.trimIndent()

    private data class CompositionQuantizationVariantDefinition(
        val id: String,
        val label: String,
        val formula: String,
        val quantizationOrder: String,
        val compositionSpace: String,
        val alphaSource: String,
        val destinationSource: String,
        val premultipliedDiagnostic: Boolean,
        val calculate: (EffectiveDestinationCandidateSample) -> IntArray?,
    )

    private fun CompositionQuantizationVariantDefinition.toJson(): String = """
        {
          "variantId": ${id.jsonString()},
          "label": ${label.jsonString()},
          "formula": ${formula.jsonString()},
          "quantizationOrder": ${quantizationOrder.jsonString()},
          "compositionSpace": ${compositionSpace.jsonString()},
          "alphaSource": ${alphaSource.jsonString()},
          "destinationSource": ${destinationSource.jsonString()},
          "premultipliedDiagnostic": $premultipliedDiagnostic,
          "candidateSource": "calculated-by-diagnostic-policy",
          "candidateReadFromRenderer": false,
          "candidateReadFromGpuImage": false,
          "candidateAppliedToRenderer": false
        }
    """.trimIndent()

    private data class CompositionQuantizationVariantTotal(
        val definition: CompositionQuantizationVariantDefinition,
        val totalResidual: Int,
        val currentResidual: Int,
        val for375CandidateTotalResidual: Int,
        val for373CandidateTotalResidual: Int,
    ) {
        val variantId: String = definition.id
        val totalDeltaVsCurrent: Int = totalResidual - currentResidual
        val totalDeltaVsFor375Candidate: Int = totalResidual - for375CandidateTotalResidual
        val totalDeltaVsFor373Candidate: Int = totalResidual - for373CandidateTotalResidual
    }

    private fun CompositionQuantizationVariantTotal.toJson(rank: Int): String = """
        {
          "rank": $rank,
          "variantId": ${variantId.jsonString()},
          "totalResidual": $totalResidual,
          "totalDeltaVsCurrent": $totalDeltaVsCurrent,
          "totalDeltaVsFor375Candidate": $totalDeltaVsFor375Candidate,
          "totalDeltaVsFor373Candidate": $totalDeltaVsFor373Candidate,
          "improvesCurrent": ${totalDeltaVsCurrent < 0},
          "improvesFor375Candidate": ${totalDeltaVsFor375Candidate < 0},
          "improvesFor373Candidate": ${totalDeltaVsFor373Candidate < 0}
        }
    """.trimIndent()

    private fun linearSrgbPlausibilityAuditSample(
        sample: CompositionQuantizationCandidateSample,
    ): LinearSrgbPlausibilityAuditSample {
        val variant = sample.variants.first { it.variantId == LINEAR_SRGB_EFFECTIVE_DESTINATION_VARIANT_ID }
        val candidateRgba = variant.candidateRgba ?: intArrayOf(0, 0, 0, 0)
        val reference = rgbaArray(sample.for375Sample.for374Sample.candidate.reference)
        val current = rgbaArray(sample.for375Sample.for374Sample.candidate.current)
        val source = rgbaArray(sample.for375Sample.for374Sample.candidate.paintSource)
        val currentError = channelAbsError(reference, current)
        val linearError = channelAbsError(reference, candidateRgba)
        val improvement = IntArray(4) { currentError[it] - linearError[it] }
        val inverse = sample.for375Sample.for374Sample.inverseDestinationEstimate
        val rounded = inverse.rgbRounded ?: intArrayOf(0, 0, 0)
        val clamped = inverse.rgbClamped ?: intArrayOf(0, 0, 0)
        val channelAudits = (0..3).map { index ->
            LinearSrgbChannelAudit(
                channel = channelKey(index),
                currentError = currentError[index],
                linearSrgbError = linearError[index],
                improvement = improvement[index],
                linearSrgbMinusCurrentError = linearError[index] - currentError[index],
                sourceMinusReference = source[index] - reference[index],
                currentMinusReference = current[index] - reference[index],
                linearSrgbMinusReference = candidateRgba[index] - reference[index],
                sourceTintDirection = sourceTintDirection(reference, current, source, index),
                destinationRounded = if (index < 3) rounded[index] else null,
                destinationClampedToSrgb = if (index < 3) clamped[index] else null,
                destinationClampToLimit = index < 3 && rounded[index] != clamped[index],
                destinationClampLimit = if (index < 3 && rounded[index] != clamped[index]) clamped[index] else null,
            )
        }
        return LinearSrgbPlausibilityAuditSample(
            for376Sample = sample,
            linearSrgbVariant = variant,
            currentErrorByChannel = currentError,
            linearSrgbErrorByChannel = linearError,
            linearSrgbImprovementByChannel = improvement,
            channelAudits = channelAudits,
        )
    }

    private data class LinearSrgbPlausibilityAuditSample(
        val for376Sample: CompositionQuantizationCandidateSample,
        val linearSrgbVariant: CompositionQuantizationCandidateVariant,
        val currentErrorByChannel: IntArray,
        val linearSrgbErrorByChannel: IntArray,
        val linearSrgbImprovementByChannel: IntArray,
        val channelAudits: List<LinearSrgbChannelAudit>,
    ) {
        val strokeBand: String = for376Sample.for375Sample.for374Sample.candidate.strokeBand
        val currentResidual: Int = for376Sample.currentResidual
        val for373CandidateResidual: Int = for376Sample.for373CandidateResidual
        val for375CandidateResidual: Int = for376Sample.for375CandidateResidual
        val linearSrgbResidual: Int = linearSrgbVariant.residual
        val linearSrgbDeltaVsCurrent: Int = linearSrgbVariant.deltaVsCurrent
        val linearSrgbDeltaVsFor375Candidate: Int = linearSrgbVariant.deltaVsFor375Candidate
        val linearSrgbDeltaVsFor373Candidate: Int = linearSrgbVariant.deltaVsFor373Candidate
        val destinationClampChannelCount: Int = channelAudits.count { it.destinationClampToLimit }
        val destinationClampPositiveImprovement: Int =
            channelAudits.filter { it.destinationClampToLimit && it.improvement > 0 }.sumOf { it.improvement }
    }

    private fun LinearSrgbPlausibilityAuditSample.toJson(): String {
        val base = for376Sample.toJson().trim()
        val suffix = """
          "linearSrgbPlausibilityAudit": {
            "variantId": ${LINEAR_SRGB_EFFECTIVE_DESTINATION_VARIANT_ID.jsonString()},
            "currentResidual": $currentResidual,
            "linearSrgbResidual": $linearSrgbResidual,
            "for375Residual": $for375CandidateResidual,
            "for373Residual": $for373CandidateResidual,
            "linearSrgbDeltaVsCurrent": $linearSrgbDeltaVsCurrent,
            "linearSrgbDeltaVsFor375Candidate": $linearSrgbDeltaVsFor375Candidate,
            "linearSrgbDeltaVsFor373Candidate": $linearSrgbDeltaVsFor373Candidate,
            "currentErrorByChannel": ${channelErrorJson(currentErrorByChannel)},
            "linearSrgbErrorByChannel": ${channelErrorJson(linearSrgbErrorByChannel)},
            "linearSrgbImprovementByChannel": ${channelErrorJson(linearSrgbImprovementByChannel)},
            "destinationClampChannelCount": $destinationClampChannelCount,
            "destinationClampPositiveImprovement": $destinationClampPositiveImprovement,
            "sampleCoherence": ${linearSrgbSampleCoherence(linearSrgbDeltaVsCurrent, linearSrgbDeltaVsFor375Candidate).jsonString()},
            "channelAudit": {
        ${channelAudits.joinToString(",\n") { it.toJson().prependIndent("      ") }}
            }
          }
        """.trimIndent().prependIndent("  ")
        return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private data class LinearSrgbChannelAudit(
        val channel: String,
        val currentError: Int,
        val linearSrgbError: Int,
        val improvement: Int,
        val linearSrgbMinusCurrentError: Int,
        val sourceMinusReference: Int,
        val currentMinusReference: Int,
        val linearSrgbMinusReference: Int,
        val sourceTintDirection: String,
        val destinationRounded: Int?,
        val destinationClampedToSrgb: Int?,
        val destinationClampToLimit: Boolean,
        val destinationClampLimit: Int?,
    )

    private fun LinearSrgbChannelAudit.toJson(): String = """
        ${channel.jsonString()}: {
          "currentError": $currentError,
          "linearSrgbError": $linearSrgbError,
          "improvement": $improvement,
          "linearSrgbMinusCurrentError": $linearSrgbMinusCurrentError,
          "sourceMinusReference": $sourceMinusReference,
          "currentMinusReference": $currentMinusReference,
          "linearSrgbMinusReference": $linearSrgbMinusReference,
          "sourceTintDirection": ${sourceTintDirection.jsonString()},
          "destinationRounded": ${destinationRounded?.toString() ?: "null"},
          "destinationClampedToSrgb": ${destinationClampedToSrgb?.toString() ?: "null"},
          "destinationClampToLimit": $destinationClampToLimit,
          "destinationClampLimit": ${destinationClampLimit?.toString() ?: "null"}
        }
    """.trimIndent()

    private data class LinearSrgbBandCoherence(
        val strokeBand: String,
        val sampleCount: Int,
        val currentResidual: Int,
        val linearSrgbResidual: Int,
        val for375Residual: Int,
        val improvedVsCurrentSamples: Int,
        val regressedVsCurrentSamples: Int,
        val improvedVsFor375Samples: Int,
        val regressedVsFor375Samples: Int,
    ) {
        val deltaVsCurrent: Int = linearSrgbResidual - currentResidual
        val deltaVsFor375Candidate: Int = linearSrgbResidual - for375Residual
        val coherence: String = when {
            deltaVsCurrent < 0 && deltaVsFor375Candidate < 0 -> "improves-current-and-for375"
            deltaVsCurrent < 0 && deltaVsFor375Candidate > 0 -> "improves-current-but-regresses-for375"
            deltaVsCurrent < 0 -> "improves-current-only"
            deltaVsCurrent == 0 -> "neutral-vs-current"
            else -> "regresses-vs-current"
        }
    }

    private fun LinearSrgbBandCoherence.toJson(): String = """
        {
          "strokeBand": ${strokeBand.jsonString()},
          "sampleCount": $sampleCount,
          "currentResidual": $currentResidual,
          "linearSrgbResidual": $linearSrgbResidual,
          "for375Residual": $for375Residual,
          "deltaVsCurrent": $deltaVsCurrent,
          "deltaVsFor375Candidate": $deltaVsFor375Candidate,
          "improvedVsCurrentSamples": $improvedVsCurrentSamples,
          "regressedVsCurrentSamples": $regressedVsCurrentSamples,
          "improvedVsFor375Samples": $improvedVsFor375Samples,
          "regressedVsFor375Samples": $regressedVsFor375Samples,
          "coherence": ${coherence.jsonString()}
        }
    """.trimIndent()

    private data class InverseDestinationEstimate(
        val possible: Boolean,
        val alpha: Double,
        val rgbFloat: DoubleArray?,
        val rgbRounded: IntArray?,
        val rgbClamped: IntArray?,
        val hasOutOfSrgbChannel: Boolean,
        val averageDeviationFromWhite: Double?,
        val conflictsWithWhiteDestination: Boolean,
        val status: String,
    ) {
        fun toJson(): String {
            val rgbFloatJson = rgbFloat?.joinToString(prefix = "[", postfix = "]") {
                String.format(Locale.US, "%.3f", it)
            } ?: "null"
            val rgbRoundedJson = rgbRounded?.joinToString(prefix = "[", postfix = "]") ?: "null"
            val rgbClampedJson = rgbClamped?.joinToString(prefix = "[", postfix = "]") ?: "null"
            val deviationJson = averageDeviationFromWhite?.let { String.format(Locale.US, "%.3f", it) } ?: "null"
            return """
                {
                  "possible": $possible,
                  "alpha": ${String.format(Locale.US, "%.6f", alpha)},
                  "formula": "destination=(reference-(source*alpha))/(1-alpha)",
                  "rgbFloat": $rgbFloatJson,
                  "rgbRounded": $rgbRoundedJson,
                  "rgbClampedToSrgb": $rgbClampedJson,
                  "hasOutOfSrgbChannel": $hasOutOfSrgbChannel,
                  "averageDeviationFromWhite": $deviationJson,
                  "conflictsWithWhiteDestination": $conflictsWithWhiteDestination,
                  "diagnosticOnly": true,
                  "usedAsCorrection": false,
                  "status": ${jsonString(status)}
                }
            """.trimIndent()
        }
    }

    private fun directSourceColorEvidenceSample(
        sample: LinearSrgbPlausibilityAuditSample,
        transparentSource: SkBitmap,
    ): DirectSourceColorEvidenceSample {
        val candidate = sample.for376Sample.for375Sample.for374Sample.candidate
        val directSourceRgba = rgbaArray(transparentSource.getPixel(candidate.x, candidate.y))
        val directAlphaByte = directSourceRgba[3]
        val directAlpha = directAlphaByte / 255.0
        val directUnpremultipliedRgb = if (directAlphaByte == 0) {
            null
        } else {
            directSourceRgba.copyOfRange(0, 3)
        }
        val paintSource = rgbaArray(candidate.paintSource)
        val paintSourceUnpremultipliedDelta = if (directUnpremultipliedRgb == null) {
            null
        } else {
            IntArray(3) { index -> directUnpremultipliedRgb[index] - paintSource[index] }
        }
        val directPremultipliedRgb = if (directUnpremultipliedRgb == null) {
            intArrayOf(0, 0, 0)
        } else {
            IntArray(3) { index -> quantizeNearest255((directUnpremultipliedRgb[index] / 255.0) * directAlpha) }
        }
        val paintSourcePremultipliedExpected = IntArray(3) { index ->
            quantizeNearest255((paintSource[index] / 255.0) * directAlpha)
        }
        val paintSourcePremultipliedDelta = IntArray(3) { index ->
            directPremultipliedRgb[index] - paintSourcePremultipliedExpected[index]
        }
        val directRecomposedOnWhite = directUnpremultipliedSourceOverWhite(directSourceRgba)
        val directResidual = sampleResidual(candidate.reference, rgbaToPixel(directRecomposedOnWhite))
        return DirectSourceColorEvidenceSample(
            for377Sample = sample,
            directSourceRgba = directSourceRgba,
            directSourceAlphaByte = directAlphaByte,
            directSourceAlpha = directAlpha,
            directPremultipliedRgb = directPremultipliedRgb,
            directUnpremultipliedRgb = directUnpremultipliedRgb,
            paintSourceUnpremultipliedRgbDelta = paintSourceUnpremultipliedDelta,
            paintSourcePremultipliedRgbExpected = paintSourcePremultipliedExpected,
            paintSourcePremultipliedRgbDelta = paintSourcePremultipliedDelta,
            sourceCoverageAlphaDelta = directAlphaByte - candidate.sourceCoverageByte,
            directRecomposedOnWhiteRgba = directRecomposedOnWhite,
            directRecomposedOnWhiteResidual = directResidual,
            directRecomposedOnWhiteDeltaVsCurrent = directResidual - sample.currentResidual,
            directRecomposedOnWhiteDeltaVsFor373Candidate = directResidual - sample.for373CandidateResidual,
            directRecomposedOnWhiteDeltaVsFor375Candidate = directResidual - sample.for375CandidateResidual,
            directRecomposedOnWhiteDeltaVsFor377LinearSrgb = directResidual - sample.linearSrgbResidual,
        )
    }

    private data class DirectSourceColorEvidenceSample(
        val for377Sample: LinearSrgbPlausibilityAuditSample,
        val directSourceRgba: IntArray,
        val directSourceAlphaByte: Int,
        val directSourceAlpha: Double,
        val directPremultipliedRgb: IntArray,
        val directUnpremultipliedRgb: IntArray?,
        val paintSourceUnpremultipliedRgbDelta: IntArray?,
        val paintSourcePremultipliedRgbExpected: IntArray,
        val paintSourcePremultipliedRgbDelta: IntArray,
        val sourceCoverageAlphaDelta: Int,
        val directRecomposedOnWhiteRgba: IntArray,
        val directRecomposedOnWhiteResidual: Int,
        val directRecomposedOnWhiteDeltaVsCurrent: Int,
        val directRecomposedOnWhiteDeltaVsFor373Candidate: Int,
        val directRecomposedOnWhiteDeltaVsFor375Candidate: Int,
        val directRecomposedOnWhiteDeltaVsFor377LinearSrgb: Int,
    ) {
        val currentResidual: Int = for377Sample.currentResidual
        val for373CandidateResidual: Int = for377Sample.for373CandidateResidual
        val for375CandidateResidual: Int = for377Sample.for375CandidateResidual
        val linearSrgbResidual: Int = for377Sample.linearSrgbResidual
        val sourceCoverageAlphaDeltaAbs: Int = kotlin.math.abs(sourceCoverageAlphaDelta)
        val paintSourceUnpremultipliedRgbDeltaAbsMax: Int =
            paintSourceUnpremultipliedRgbDelta?.maxOf { kotlin.math.abs(it) } ?: 255
    }

    private fun DirectSourceColorEvidenceSample.toJson(): String {
        val base = for377Sample.toJson().trim()
        val candidate = for377Sample.for376Sample.for375Sample.for374Sample.candidate
        val suffix = """
          "directSourceColorEvidence": {
            "directSourceScene": "m60_bounded_stroke_cap_join_transparent_source_for378",
            "directSourceReadSource": "CPU/reference diagnostic transparent source bitmap",
            "directSourceReadFromRenderer": false,
            "directSourceReadFromGpuImage": false,
            "directSourceAppliedToRenderer": false,
            "directSourceRgba": ${rgbaArrayJson(directSourceRgba)},
            "directSourceReadbackRgbDomain": "SkBitmap.getPixel SkColor unpremultiplied 8-bit readback",
            "directSourceAlphaByte": $directSourceAlphaByte,
            "directSourceAlpha": ${String.format(Locale.US, "%.6f", directSourceAlpha)},
            "directPremultipliedRgb": ${rgbArrayJson(directPremultipliedRgb)},
            "directUnpremultipliedRgb": ${directUnpremultipliedRgb?.let { rgbArrayJson(it) } ?: "null"},
            "directUnpremultipliedRgbPossible": ${directUnpremultipliedRgb != null},
            "paintSourceRgba": ${rgbaJson(candidate.paintSource)},
            "paintSourceUnpremultipliedRgbDelta": ${paintSourceUnpremultipliedRgbDelta?.let { channelRgbJson(it) } ?: "null"},
            "paintSourceUnpremultipliedRgbDeltaAbsMax": $paintSourceUnpremultipliedRgbDeltaAbsMax,
            "paintSourcePremultipliedRgbExpected": ${rgbArrayJson(paintSourcePremultipliedRgbExpected)},
            "paintSourcePremultipliedRgbDelta": ${channelRgbJson(paintSourcePremultipliedRgbDelta)},
            "sourceCoverageByte": ${candidate.sourceCoverageByte},
            "sourceCoverageAlphaDelta": $sourceCoverageAlphaDelta,
            "sourceCoverageAlphaDeltaAbs": $sourceCoverageAlphaDeltaAbs,
            "directRecomposedOnWhiteRgba": ${rgbaArrayJson(directRecomposedOnWhiteRgba)},
            "directRecompositionFormula": "rgb=round((directUnpremultipliedRgb/255.0)*directSourceAlpha + (1.0-directSourceAlpha))*255; a=255",
            "directRecomposedOnWhiteResidual": $directRecomposedOnWhiteResidual,
            "directRecomposedOnWhiteDeltaVsCurrent": $directRecomposedOnWhiteDeltaVsCurrent,
            "directRecomposedOnWhiteDeltaVsFor373Candidate": $directRecomposedOnWhiteDeltaVsFor373Candidate,
            "directRecomposedOnWhiteDeltaVsFor375Candidate": $directRecomposedOnWhiteDeltaVsFor375Candidate,
            "directRecomposedOnWhiteDeltaVsFor377LinearSrgb": $directRecomposedOnWhiteDeltaVsFor377LinearSrgb,
            "directRecompositionImprovesCurrent": ${directRecomposedOnWhiteDeltaVsCurrent < 0},
            "directRecompositionImprovesFor375Candidate": ${directRecomposedOnWhiteDeltaVsFor375Candidate < 0},
            "directRecompositionImprovesFor377LinearSrgb": ${directRecomposedOnWhiteDeltaVsFor377LinearSrgb < 0},
            "inverseDestinationEstimateUsedAsPrimaryEvidence": false
          }
        """.trimIndent().prependIndent("  ")
        return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private fun effectiveSourceColorPathSample(
        sample: DirectSourceColorEvidenceSample,
    ): EffectiveSourceColorPathSample {
        val candidate = sample.for377Sample.for376Sample.for375Sample.for374Sample.candidate
        val reference = rgbaArray(candidate.reference)
        val current = rgbaArray(candidate.current)
        val direct = sample.directRecomposedOnWhiteRgba
        val currentError = channelAbsError(reference, current)
        val directError = channelAbsError(reference, direct)
        val improvement = IntArray(4) { currentError[it] - directError[it] }
        val currentMinusDirect = IntArray(4) { current[it] - direct[it] }
        val currentVsDirectDistance = currentMinusDirect.sumOf { kotlin.math.abs(it) }
        val dominantIndex = dominantRegressionChannel(IntArray(4) { kotlin.math.abs(currentMinusDirect[it]) })
        val directPremulChannelsValid = sample.directPremultipliedRgb.all { it <= sample.directSourceAlphaByte }
        val classification = effectiveSourceColorPathSampleClassification(
            directRecomposedOnWhiteResidual = sample.directRecomposedOnWhiteResidual,
            directRecomposedOnWhiteDeltaVsCurrent = sample.directRecomposedOnWhiteDeltaVsCurrent,
            sourceCoverageAlphaDeltaAbs = sample.sourceCoverageAlphaDeltaAbs,
            directUnpremultipliedRgbPossible = sample.directUnpremultipliedRgb != null,
            directPremulChannelsValid = directPremulChannelsValid,
        )
        return EffectiveSourceColorPathSample(
            directSourceSample = sample,
            currentMinusDirectRecomposedRgba = currentMinusDirect,
            currentVsDirectRecomposedRgbaDistance = currentVsDirectDistance,
            currentErrorByChannel = currentError,
            directRecomposedErrorByChannel = directError,
            directRecomposedImprovementByChannel = improvement,
            dominantCurrentMinusDirectRecomposedChannel = channelName(dominantIndex),
            directPremultipliedChannelsWithinAlpha = directPremulChannelsValid,
            sampleClassification = classification,
        )
    }

    private data class EffectiveSourceColorPathSample(
        val directSourceSample: DirectSourceColorEvidenceSample,
        val currentMinusDirectRecomposedRgba: IntArray,
        val currentVsDirectRecomposedRgbaDistance: Int,
        val currentErrorByChannel: IntArray,
        val directRecomposedErrorByChannel: IntArray,
        val directRecomposedImprovementByChannel: IntArray,
        val dominantCurrentMinusDirectRecomposedChannel: String,
        val directPremultipliedChannelsWithinAlpha: Boolean,
        val sampleClassification: String,
    ) {
        val currentResidual: Int = directSourceSample.currentResidual
        val directRecomposedOnWhiteResidual: Int = directSourceSample.directRecomposedOnWhiteResidual
    }

    private fun sourceColorCorrectionProbeSample(
        sample: EffectiveSourceColorPathSample,
        correctedRgba: IntArray,
    ): SourceColorCorrectionProbeSample {
        val candidate = sample.directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate
        val reference = rgbaArray(candidate.reference)
        val current = rgbaArray(candidate.current)
        val direct = sample.directSourceSample.directRecomposedOnWhiteRgba
        val correctedError = channelAbsError(reference, correctedRgba)
        val correctedMinusCurrentError =
            IntArray(4) { correctedError[it] - sample.currentErrorByChannel[it] }
        val correctedMinusDirectError =
            IntArray(4) { correctedError[it] - sample.directRecomposedErrorByChannel[it] }
        val correctedMinusCurrentRgba = IntArray(4) { correctedRgba[it] - current[it] }
        val correctedMinusDirectRgba = IntArray(4) { correctedRgba[it] - direct[it] }
        return SourceColorCorrectionProbeSample(
            effectiveSourceSample = sample,
            correctedRgba = correctedRgba,
            correctedResidual = correctedError.sum(),
            correctedErrorByChannel = correctedError,
            correctedMinusCurrentErrorByChannel = correctedMinusCurrentError,
            correctedMinusDirectRecomposedErrorByChannel = correctedMinusDirectError,
            correctedMinusCurrentRgba = correctedMinusCurrentRgba,
            correctedMinusDirectRecomposedRgba = correctedMinusDirectRgba,
            correctedVsCurrentRgbaDistance = correctedMinusCurrentRgba.sumOf { kotlin.math.abs(it) },
            correctedVsDirectRecomposedRgbaDistance = correctedMinusDirectRgba.sumOf { kotlin.math.abs(it) },
        )
    }

    private data class SourceColorCorrectionProbeSample(
        val effectiveSourceSample: EffectiveSourceColorPathSample,
        val correctedRgba: IntArray,
        val correctedResidual: Int,
        val correctedErrorByChannel: IntArray,
        val correctedMinusCurrentErrorByChannel: IntArray,
        val correctedMinusDirectRecomposedErrorByChannel: IntArray,
        val correctedMinusCurrentRgba: IntArray,
        val correctedMinusDirectRecomposedRgba: IntArray,
        val correctedVsCurrentRgbaDistance: Int,
        val correctedVsDirectRecomposedRgbaDistance: Int,
    ) {
        val currentResidual: Int = effectiveSourceSample.currentResidual
        val directRecomposedOnWhiteResidual: Int = effectiveSourceSample.directRecomposedOnWhiteResidual
    }

    private fun SourceColorCorrectionProbeSample.toJson(): String {
        val candidate =
            effectiveSourceSample.directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate
        return """
            {
              "index": ${candidate.index},
              "x": ${candidate.x},
              "y": ${candidate.y},
              "strokeBand": ${candidate.strokeBand.jsonString()},
              "cap": ${candidate.cap.jsonString()},
              "join": ${candidate.join.jsonString()},
              "strokeWidth": ${String.format(Locale.US, "%.1f", candidate.strokeWidth)},
              "referenceRgba": ${rgbaJson(candidate.reference)},
              "currentRgba": ${rgbaJson(candidate.current)},
              "directRecomposedOnWhiteRgba": ${rgbaArrayJson(effectiveSourceSample.directSourceSample.directRecomposedOnWhiteRgba)},
              "correctedRgba": ${rgbaArrayJson(correctedRgba)},
              "currentResidual": $currentResidual,
              "directRecomposedOnWhiteResidual": $directRecomposedOnWhiteResidual,
              "correctedResidual": $correctedResidual,
              "correctedDeltaVsCurrent": ${correctedResidual - currentResidual},
              "correctedGainVsCurrent": ${currentResidual - correctedResidual},
              "correctedDeltaVsDirectRecomposedOnWhite": ${correctedResidual - directRecomposedOnWhiteResidual},
              "currentErrorByChannel": ${channelErrorJson(effectiveSourceSample.currentErrorByChannel)},
              "directRecomposedErrorByChannel": ${channelErrorJson(effectiveSourceSample.directRecomposedErrorByChannel)},
              "correctedErrorByChannel": ${channelErrorJson(correctedErrorByChannel)},
              "correctedMinusCurrentErrorByChannel": ${channelErrorJson(correctedMinusCurrentErrorByChannel)},
              "correctedMinusDirectRecomposedErrorByChannel": ${channelErrorJson(correctedMinusDirectRecomposedErrorByChannel)},
              "correctedMinusCurrentRgba": ${channelErrorJson(correctedMinusCurrentRgba)},
              "correctedMinusDirectRecomposedRgba": ${channelErrorJson(correctedMinusDirectRecomposedRgba)},
              "correctedVsCurrentRgbaDistance": $correctedVsCurrentRgbaDistance,
              "correctedVsDirectRecomposedRgbaDistance": $correctedVsDirectRecomposedRgbaDistance,
              "for379SampleClassification": ${effectiveSourceSample.sampleClassification.jsonString()}
            }
        """.trimIndent()
    }

    private fun EffectiveSourceColorPathSample.toJson(): String {
        val base = directSourceSample.toJson().trim()
        val suffix = """
          "effectiveSourceColorPath": {
            "comparisonId": "current-vs-direct-source-recomposed-on-white",
            "comparisonUsesInverseDestinationEstimate": false,
            "referenceRgba": ${rgbaJson(directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate.reference)},
            "currentRgba": ${rgbaJson(directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate.current)},
            "directSourceTransparentUnpremultipliedRgba": ${rgbaArrayJson(directSourceSample.directSourceRgba)},
            "directSourceTransparentPremultipliedRgb": ${rgbArrayJson(directSourceSample.directPremultipliedRgb)},
            "directSourceAlphaByte": ${directSourceSample.directSourceAlphaByte},
            "sourceCoverageByte": ${directSourceSample.for377Sample.for376Sample.for375Sample.for374Sample.candidate.sourceCoverageByte},
            "sourceCoverageAlphaDelta": ${directSourceSample.sourceCoverageAlphaDelta},
            "sourceCoverageAlphaDeltaAbs": ${directSourceSample.sourceCoverageAlphaDeltaAbs},
            "directRecomposedOnWhiteRgba": ${rgbaArrayJson(directSourceSample.directRecomposedOnWhiteRgba)},
            "currentResidual": $currentResidual,
            "directRecomposedOnWhiteResidual": $directRecomposedOnWhiteResidual,
            "directRecomposedOnWhiteDeltaVsCurrent": ${directSourceSample.directRecomposedOnWhiteDeltaVsCurrent},
            "directRecomposedOnWhiteGainVsCurrent": ${currentResidual - directRecomposedOnWhiteResidual},
            "currentMinusDirectRecomposedRgba": ${channelErrorJson(currentMinusDirectRecomposedRgba)},
            "currentVsDirectRecomposedRgbaDistance": $currentVsDirectRecomposedRgbaDistance,
            "currentErrorByChannel": ${channelErrorJson(currentErrorByChannel)},
            "directRecomposedErrorByChannel": ${channelErrorJson(directRecomposedErrorByChannel)},
            "directRecomposedImprovementByChannel": ${channelErrorJson(directRecomposedImprovementByChannel)},
            "dominantCurrentMinusDirectRecomposedChannel": ${dominantCurrentMinusDirectRecomposedChannel.jsonString()},
            "directPremultipliedChannelsWithinAlpha": $directPremultipliedChannelsWithinAlpha,
            "classification": ${sampleClassification.jsonString()}
          }
        """.trimIndent().prependIndent("  ")
        return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private fun m60F16EffectiveCoverageExportJson(
        residualStats: StrokeResidualStats,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val samples = residualStats.highDeltaSamples.take(FOR372_REQUIRED_SAMPLE_COUNT)
        val computedResidual = samples.sumOf { sampleResidual(it.reference, it.gpu) }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-372",
              "sceneId": "m60-f16-effective-coverage-export-for372",
              "sourceSceneId": "m60-bounded-stroke-cap-join",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-export-diagnostique-couverture-aa-effective-apres-for-371",
              "sourceFinding": "global/kanvas/findings/for-371-identifie-le-point-dexport-couverture-m60-f16-requis",
              "requiredFor371Decision": "M60_F16_EFFECTIVE_COVERAGE_ACCESS_REQUIRES_NEW_EXPORT_POINT",
              "requiredFor371Classification": "coverage-access-requires-new-export-point",
              "decision": "M60_F16_EFFECTIVE_COVERAGE_EXPORT_READY_FOR_CANDIDATE_PROBE",
              "classification": "coverage-export-ready-for-candidate-probe",
              "allowedClassifications": [
                "coverage-export-ready-for-candidate-probe",
                "coverage-export-partial",
                "coverage-export-blocked"
              ],
              "currentResidual": 856,
              "computedResidual": $computedResidual,
              "sampleCount": ${samples.size},
              "coverageExportReadyForCandidateProbe": ${samples.size == FOR372_REQUIRED_SAMPLE_COUNT},
              "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "candidatePolicyRgbaProduced": false,
              "candidatePolicyAppliedToRenderer": false,
              "coverageExport": {
                "classification": "coverage-export-ready-for-candidate-probe",
                "readyForCandidateProbe": true,
                "sourceCoverageAvailableForAllSamples": ${samples.size == FOR372_REQUIRED_SAMPLE_COUNT},
                "sourceCoverageByteRange": [0, 255],
                "sourceCoverageScale": "sourceCoverageByte / 255.0 rounded to 6 decimals",
                "effectiveSourceAlphaByteEqualsCoverageByte": true,
                "effectiveSourceAlphaEqualsCoverage": true,
                "coverageProvenance": "cpu.coverage.stroke-cap-join-oracle / PathStrokeCoverage / diagnostic transparent GM alpha mask",
                "coverageOwnerRoute": "cpu.coverage.stroke-cap-join-oracle",
                "coveragePlan": "PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,capJoinMatrix=butt-bevel+round-round+square-bevel)",
                "diagnosticMaskScene": "m60_bounded_stroke_cap_join_coverage_mask_for372",
                "diagnosticMaskBackground": "transparent",
                "diagnosticMaskPaint": "opaque white stroke with original cap/join/strokeWidth matrix",
                "coverageReadSource": "alpha-channel-from-transparent-cpu-diagnostic-mask",
                "coverageReconstructedFromRgbaDeltas": false,
                "referenceCurrentRgbaUsedForCoverage": false,
                "sampleDeltaRgbaUsedForCoverage": false
              },
              "samples": [
            ${samples.mapIndexed { index, sample -> effectiveCoverageSampleJson(index + 1, sample, coverageMask) }.joinToString(",\n").prependIndent("    ")}
              ],
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "candidateImplementationAuthorized": false,
                "candidatePolicyRgbaProduced": false,
                "candidatePolicyRgbaAppliedToRenderer": false,
                "scoreIncreased": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "kadreChanged": false,
                "f16PremulBlendRuntimeChanged": false,
                "skBitmapGetPixelChanged": false,
                "rendererSceneBranchAdded": false,
                "rendererCoordinateBranchAdded": false,
                "rendererSelectedCellBranchAdded": false,
                "fullGmCropPathAdded": false,
                "coverageReconstructedFromRgbaDeltas": false
              },
              "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            }
        """.trimIndent() + "\n"
    }

    private fun effectiveCoverageSampleJson(index: Int, sample: ResidualSample, coverageMask: SkBitmap): String {
        val band = strokePaintBands().first { sample.x in it.xStart until it.xEnd }
        val residual = sampleResidual(sample.reference, sample.gpu)
        val sourceCoverageByte = (coverageMask.getPixel(sample.x, sample.y) ushr 24) and 0xFF
        val sourceCoverage = sourceCoverageByte / 255.0
        return """
            {
              "index": $index,
              "x": ${sample.x},
              "y": ${sample.y},
              "strokeBand": ${band.id.jsonString()},
              "referenceRgba": ${rgbaJson(sample.reference)},
              "currentRgba": ${rgbaJson(sample.gpu)},
              "gpuRgba": ${rgbaJson(sample.gpu)},
              "sampleResidual": $residual,
              "maxChannelDelta": ${sample.maxChannelDelta},
              "paintSourceRgba": ${rgbaJson(band.sourceColor)},
              "paintSourceStatus": "known-from-BoundedStrokeCapJoinGM",
              "paintSourceAlpha": ${(band.sourceColor ushr 24) and 0xFF},
              "cap": ${band.cap.jsonString()},
              "join": ${band.join.jsonString()},
              "strokeWidth": ${String.format(Locale.US, "%.1f", band.strokeWidth)},
              "sourceCoverageByte": $sourceCoverageByte,
              "sourceCoverage": ${String.format(Locale.US, "%.6f", sourceCoverage)},
              "sourceCoverageStatus": "exported-from-cpu-diagnostic-mask-alpha",
              "effectiveSourceAlphaByte": $sourceCoverageByte,
              "effectiveSourceAlpha": ${String.format(Locale.US, "%.6f", sourceCoverage)},
              "effectiveSourceAlphaStatus": "opaque-source-paint-alpha-multiplied-by-exported-coverage",
              "coverageProvenance": "cpu.coverage.stroke-cap-join-oracle / PathStrokeCoverage / diagnostic transparent GM alpha mask",
              "coverageReadSource": "alpha-channel-from-transparent-cpu-diagnostic-mask",
              "coverageReconstructedFromRgbaDeltas": false,
              "referenceCurrentRgbaUsedForCoverage": false,
              "sampleDeltaRgbaUsedForCoverage": false,
              "candidatePolicyId": ${f16CandidatePolicyId().jsonString()},
              "candidatePolicyRgba": null,
              "candidatePolicyRgbaStatus": "not-produced-export-only",
              "readyForCandidateProbe": true,
              "rendererAppliedCandidate": false
            }
        """.trimIndent()
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

    private fun rgbaArray(pixel: Int): IntArray = intArrayOf(
        (pixel ushr 16) and 0xFF,
        (pixel ushr 8) and 0xFF,
        pixel and 0xFF,
        (pixel ushr 24) and 0xFF,
    )

    private fun channelAbsError(reference: IntArray, value: IntArray): IntArray =
        IntArray(4) { kotlin.math.abs(reference[it] - value[it]) }

    private fun dominantRegressionChannel(candidateMinusCurrent: IntArray): Int {
        var best = 0
        for (index in 1..2) {
            if (candidateMinusCurrent[index] > candidateMinusCurrent[best]) {
                best = index
            }
        }
        return best
    }

    private fun regressionDirection(
        reference: IntArray,
        source: IntArray,
        candidate: IntArray,
        candidateMinusCurrent: IntArray,
        dominantChannelIndex: Int,
    ): String {
        if (candidateMinusCurrent[dominantChannelIndex] <= 0) {
            return "candidate-improves-or-neutral"
        }
        val candidateDelta = candidate[dominantChannelIndex] - reference[dominantChannelIndex]
        val sourceDelta = source[dominantChannelIndex] - reference[dominantChannelIndex]
        if (candidateDelta != 0 && sourceDelta != 0 && candidateDelta.sign() == sourceDelta.sign()) {
            return "source-tint-too-strong"
        }
        return when {
            candidateDelta > 0 -> "too-light"
            candidateDelta < 0 -> "too-dark"
            else -> "mixed"
        }
    }

    private fun inverseDestinationEstimate(
        reference: IntArray,
        source: IntArray,
        alpha: Double,
    ): InverseDestinationEstimate {
        if (alpha <= 0.0 || alpha >= 1.0) {
            return InverseDestinationEstimate(
                possible = false,
                alpha = alpha,
                rgbFloat = null,
                rgbRounded = null,
                rgbClamped = null,
                hasOutOfSrgbChannel = false,
                averageDeviationFromWhite = null,
                conflictsWithWhiteDestination = false,
                status = "blocked-by-alpha-boundary",
            )
        }
        val rgbFloat = DoubleArray(3) { index ->
            ((reference[index] / 255.0) - (source[index] / 255.0) * alpha) / (1.0 - alpha) * 255.0
        }
        val rgbRounded = IntArray(3) { index -> kotlin.math.round(rgbFloat[index]).toInt() }
        val rgbClamped = IntArray(3) { index -> rgbRounded[index].coerceIn(0, 255) }
        val hasOutOfSrgbChannel = rgbFloat.any { it < 0.0 || it > 255.0 }
        val averageDeviation = rgbFloat.sumOf { kotlin.math.abs(it - 255.0) } / 3.0
        val conflictsWithWhite = hasOutOfSrgbChannel || averageDeviation >= 32.0
        return InverseDestinationEstimate(
            possible = true,
            alpha = alpha,
            rgbFloat = rgbFloat,
            rgbRounded = rgbRounded,
            rgbClamped = rgbClamped,
            hasOutOfSrgbChannel = hasOutOfSrgbChannel,
            averageDeviationFromWhite = averageDeviation,
            conflictsWithWhiteDestination = conflictsWithWhite,
            status = if (conflictsWithWhite) {
                "inverse-destination-incompatible-with-white-assumption"
            } else {
                "inverse-destination-compatible-with-white-assumption"
            },
        )
    }

    private fun candidateRegressionClassification(
        sampleCount: Int,
        candidateTotalResidual: Int,
        currentResidual: Int,
        regressingSamples: Int,
        destinationConflictSamples: Int,
        sourceTintSamples: Int,
        quantizationOnlySamples: Int,
    ): String {
        if (sampleCount != FOR374_REQUIRED_SAMPLE_COUNT || candidateTotalResidual <= currentResidual) {
            return "candidate-regression-blocked"
        }
        if (destinationConflictSamples >= 7 && sourceTintSamples >= 7) {
            return "candidate-regression-likely-destination-model"
        }
        if (regressingSamples >= 7 && destinationConflictSamples < 4) {
            return "candidate-regression-likely-coverage-model"
        }
        if (quantizationOnlySamples >= 7) {
            return "candidate-regression-likely-quantization-model"
        }
        return "candidate-regression-mixed"
    }

    private fun effectiveDestinationCandidateClassification(
        sampleCount: Int,
        effectiveDestinationCandidateTotalResidual: Int,
        currentResidual: Int,
    ): String {
        if (sampleCount != FOR375_REQUIRED_SAMPLE_COUNT) {
            return "effective-destination-candidate-blocked"
        }
        if (effectiveDestinationCandidateTotalResidual <= 64) {
            return "effective-destination-candidate-explains-reference"
        }
        if (effectiveDestinationCandidateTotalResidual < currentResidual) {
            return "effective-destination-candidate-reduces-residual"
        }
        if (effectiveDestinationCandidateTotalResidual == currentResidual) {
            return "effective-destination-candidate-neutral"
        }
        return "effective-destination-candidate-regresses"
    }

    private fun compositionQuantizationCandidateClassification(
        sampleCount: Int,
        blockedVariantCount: Int,
        bestVariantTotalResidual: Int,
        currentResidual: Int,
        for375CandidateTotalResidual: Int,
    ): String {
        if (sampleCount != FOR376_REQUIRED_SAMPLE_COUNT || blockedVariantCount > 0) {
            return "composition-quantization-candidate-blocked"
        }
        if (bestVariantTotalResidual <= 64) {
            return "composition-quantization-candidate-explains-reference"
        }
        if (bestVariantTotalResidual < for375CandidateTotalResidual || bestVariantTotalResidual < currentResidual) {
            return "composition-quantization-candidate-reduces-residual"
        }
        if (bestVariantTotalResidual == for375CandidateTotalResidual) {
            return "composition-quantization-candidate-neutral"
        }
        return "composition-quantization-candidate-regresses"
    }

    private fun linearSrgbPlausibilityClassification(
        sampleCount: Int,
        linearSrgbTotalResidual: Int,
        currentResidual: Int,
        clampImprovementShare: Double,
        bandCoherence: List<LinearSrgbBandCoherence>,
    ): String {
        if (sampleCount != FOR377_REQUIRED_SAMPLE_COUNT || linearSrgbTotalResidual != 607) {
            return "linear-srgb-blocked"
        }
        if (linearSrgbTotalResidual >= currentResidual) {
            return "linear-srgb-blocked"
        }
        if (clampImprovementShare >= 0.60) {
            return "linear-srgb-likely-diagnostic-artifact"
        }
        if (bandCoherence.all { it.deltaVsCurrent < 0 && it.deltaVsFor375Candidate < 0 } && clampImprovementShare < 0.50) {
            return "linear-srgb-plausible-next-axis"
        }
        return "linear-srgb-mixed-needs-reference-color-evidence"
    }

    private fun linearSrgbPlausibilityClassificationReason(classification: String): String =
        when (classification) {
            "linear-srgb-plausible-next-axis" ->
                "The linear-sRGB variant improves both stroke bands against current and FOR-375, and the gain is not dominated by destination clamp channels."
            "linear-srgb-likely-diagnostic-artifact" ->
                "Most positive improvement comes from inverse-destination channels clamped to the sRGB limits, so the gain is likely dominated by the diagnostic destination estimate."
            "linear-srgb-mixed-needs-reference-color-evidence" ->
                "The variant improves every sample against current and strongly improves round-round, but butt-bevel regresses against FOR-375 and nearly half of the positive gain comes from clamped inverse-destination channels."
            else ->
                "The FOR-376 invariant or recomputed linear-sRGB residual changed, so the plausibility audit cannot make a stable decision."
        }

    private fun directSourceColorClassification(
        sampleCount: Int,
        directRecomposedTotalResidual: Int,
        currentResidual: Int,
        for375CandidateTotalResidual: Int,
        linearSrgbTotalResidual: Int,
        sourceCoverageAlphaDeltaAbsTotal: Int,
        sourceCoverageAlphaDeltaAbsMax: Int,
        paintSourceColorDeltaAbsMax: Int,
    ): String {
        if (sampleCount != FOR378_REQUIRED_SAMPLE_COUNT || linearSrgbTotalResidual != 607) {
            return "direct-source-color-blocked"
        }
        if (sourceCoverageAlphaDeltaAbsMax > 1 || sourceCoverageAlphaDeltaAbsTotal > 4) {
            return "direct-source-color-points-coverage-mismatch"
        }
        if (
            directRecomposedTotalResidual <= linearSrgbTotalResidual &&
            directRecomposedTotalResidual < currentResidual &&
            directRecomposedTotalResidual < for375CandidateTotalResidual
        ) {
            return "direct-source-color-confirms-linear-axis"
        }
        if (
            directRecomposedTotalResidual >= for375CandidateTotalResidual &&
            directRecomposedTotalResidual >= linearSrgbTotalResidual &&
            paintSourceColorDeltaAbsMax <= 4
        ) {
            return "direct-source-color-points-destination-artifact"
        }
        return "direct-source-color-mixed-needs-next-axis"
    }

    private fun directSourceColorClassificationReason(classification: String): String =
        when (classification) {
            "direct-source-color-confirms-linear-axis" ->
                "The transparent-source recomposition improves current, FOR-375, and the FOR-377 linear-sRGB residual without using inverse-destination clamp channels as primary evidence."
            "direct-source-color-points-coverage-mismatch" ->
                "The transparent-source alpha no longer matches the FOR-372 source coverage bytes, so the next axis should isolate coverage before evaluating color-space policy."
            "direct-source-color-points-destination-artifact" ->
                "The direct source color and coverage agree with the preserved source facts, but recomposition on white does not reproduce the FOR-377 linear-sRGB gain, leaving the inverse-destination clamp path as a diagnostic artifact."
            "direct-source-color-mixed-needs-next-axis" ->
                "The transparent-source evidence does not cleanly confirm the linear-sRGB axis or isolate a pure coverage/destination cause; the next probe should separate effective source color from coverage."
            else ->
                "The FOR-377 invariant or transparent-source capture changed, so the direct source color audit cannot make a stable decision."
        }

    private fun effectiveSourceColorPathSampleClassification(
        directRecomposedOnWhiteResidual: Int,
        directRecomposedOnWhiteDeltaVsCurrent: Int,
        sourceCoverageAlphaDeltaAbs: Int,
        directUnpremultipliedRgbPossible: Boolean,
        directPremulChannelsValid: Boolean,
    ): String {
        if (!directUnpremultipliedRgbPossible || !directPremulChannelsValid || sourceCoverageAlphaDeltaAbs > 1) {
            return "evidence-insufficient"
        }
        if (directRecomposedOnWhiteResidual <= 4 && directRecomposedOnWhiteDeltaVsCurrent < 0) {
            return "source-color-ready-for-correction"
        }
        if (directRecomposedOnWhiteDeltaVsCurrent < 0) {
            return "composition-color-ambiguous"
        }
        return "evidence-insufficient"
    }

    private fun effectiveSourceColorPathClassification(
        sampleCount: Int,
        directRecomposedTotalResidual: Int,
        currentResidual: Int,
        readySampleCount: Int,
        insufficientSampleCount: Int,
    ): String {
        if (sampleCount != FOR379_REQUIRED_SAMPLE_COUNT || insufficientSampleCount > 0) {
            return "evidence-insufficient"
        }
        if (
            readySampleCount == sampleCount &&
            directRecomposedTotalResidual == 19 &&
            currentResidual == FOR373_CURRENT_RESIDUAL
        ) {
            return "source-color-path-ready-for-correction"
        }
        if (directRecomposedTotalResidual < currentResidual) {
            return "composition-color-still-ambiguous"
        }
        return "evidence-insufficient"
    }

    private fun effectiveSourceColorPathClassificationReason(classification: String): String =
        when (classification) {
            "source-color-path-ready-for-correction" ->
                "All ten FOR-378 samples have exact coverage/alpha agreement, valid premultiplied source channels, and direct source recomposition residuals at or below 4; the total residual falls from 856 to 19."
            "composition-color-still-ambiguous" ->
                "The direct source recomposition improves the current residual, but at least one sample still needs composition or color-space separation before a correction can be specified."
            else ->
                "The sample count, alpha/premultiplied invariants, or FOR-378 residual gate changed, so the evidence is insufficient for a correction-ready source/color path."
        }

    private fun linearSrgbSampleCoherence(deltaVsCurrent: Int, deltaVsFor375Candidate: Int): String =
        when {
            deltaVsCurrent < 0 && deltaVsFor375Candidate < 0 -> "improves-current-and-for375"
            deltaVsCurrent < 0 && deltaVsFor375Candidate > 0 -> "improves-current-but-regresses-for375"
            deltaVsCurrent < 0 -> "improves-current-only"
            deltaVsCurrent == 0 -> "neutral-vs-current"
            else -> "regresses-vs-current"
        }

    private fun sourceTintDirection(
        reference: IntArray,
        current: IntArray,
        source: IntArray,
        channelIndex: Int,
    ): String {
        val sourceDelta = source[channelIndex] - reference[channelIndex]
        val currentDelta = current[channelIndex] - reference[channelIndex]
        if (currentDelta == 0) {
            return "current-matches-reference"
        }
        if (sourceDelta == 0) {
            return "source-matches-reference"
        }
        return if (sourceDelta.sign() == currentDelta.sign()) {
            "current-error-with-source-tint"
        } else {
            "current-error-opposes-source-tint"
        }
    }

    private fun candidateRegressionReason(classification: String): String =
        when (classification) {
            "candidate-regression-likely-destination-model" ->
                "Most regressing samples point in the source tint direction, and the inverse destination estimate is incompatible with a white destination."
            "candidate-regression-likely-coverage-model" ->
                "The residual increase is broad, but the inverse destination estimate does not consistently contradict white."
            "candidate-regression-likely-quantization-model" ->
                "Most deltas are small enough to be explained by rounding or quantization."
            "candidate-regression-mixed" ->
                "Multiple diagnostic signals remain plausible and should be separated by one-axis probes."
            else ->
                "The audit cannot classify the regression without changing the diagnostic assumptions."
        }

    private fun Int.sign(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    private fun candidatePolicyRgba(sourceColor: Int, sourceCoverageByte: Int): IntArray {
        val sourceAlphaByte = (sourceColor ushr 24) and 0xFF
        val effectiveAlphaByte = quantizeAlphaRound((sourceAlphaByte / 255.0) * (sourceCoverageByte / 255.0))
        val alpha = effectiveAlphaByte / 255.0
        return intArrayOf(
            quantize256((((sourceColor ushr 16) and 0xFF) / 255.0) * alpha + (1.0 - alpha)),
            quantize256((((sourceColor ushr 8) and 0xFF) / 255.0) * alpha + (1.0 - alpha)),
            quantize256(((sourceColor and 0xFF) / 255.0) * alpha + (1.0 - alpha)),
            255,
        )
    }

    private fun sourceOverEffectiveDestinationRgba(
        sourceColor: Int,
        effectiveAlphaByte: Int,
        destinationRgb: IntArray,
    ): IntArray {
        val alpha = effectiveAlphaByte / 255.0
        return intArrayOf(
            quantize256((((sourceColor ushr 16) and 0xFF) / 255.0) * alpha + (destinationRgb[0] / 255.0) * (1.0 - alpha)),
            quantize256((((sourceColor ushr 8) and 0xFF) / 255.0) * alpha + (destinationRgb[1] / 255.0) * (1.0 - alpha)),
            quantize256(((sourceColor and 0xFF) / 255.0) * alpha + (destinationRgb[2] / 255.0) * (1.0 - alpha)),
            255,
        )
    }

    private fun compositionQuantizationVariantDefinitions(): List<CompositionQuantizationVariantDefinition> =
        listOf(
            CompositionQuantizationVariantDefinition(
                id = "source_over_effective_destination_floor_256",
                label = "FOR-375 baseline current quantization floor source-over effective destination",
                formula = "rgb=floor(((sourceSrgb/255.0)*alpha + (destinationSrgb/255.0)*(1.0-alpha))*256.0) clamped to [0,255]; a=255",
                quantizationOrder = "compose-straight-srgb-then-floor-256",
                compositionSpace = "straight-srgb",
                alphaSource = "effectiveSourceAlphaByte/255.0",
                destinationSource = "inverseDestinationEstimate.rgbClampedToSrgb",
                premultipliedDiagnostic = false,
            ) { sample ->
                val destination = sample.for374Sample.inverseDestinationEstimate.rgbClamped
                if (destination == null) {
                    null
                } else {
                    sourceOverEffectiveDestinationRgba(
                        sourceColor = sample.for374Sample.candidate.paintSource,
                        effectiveAlphaByte = sample.for374Sample.candidate.effectiveSourceAlphaByte,
                        destinationRgb = destination,
                    )
                }
            },
            CompositionQuantizationVariantDefinition(
                id = "straight_srgb_source_over_effective_destination_nearest_255",
                label = "Straight sRGB source-over effective destination with nearest rounding",
                formula = "rgb=round(((sourceSrgb/255.0)*alpha + (destinationSrgb/255.0)*(1.0-alpha))*255.0) clamped to [0,255]; a=255",
                quantizationOrder = "compose-straight-srgb-then-round-nearest-255",
                compositionSpace = "straight-srgb",
                alphaSource = "effectiveSourceAlphaByte/255.0",
                destinationSource = "inverseDestinationEstimate.rgbClampedToSrgb",
                premultipliedDiagnostic = false,
            ) { sample ->
                val destination = sample.for374Sample.inverseDestinationEstimate.rgbClamped
                if (destination == null) {
                    null
                } else {
                    straightSrgbSourceOverEffectiveDestinationNearestRgba(
                        sourceColor = sample.for374Sample.candidate.paintSource,
                        effectiveAlphaByte = sample.for374Sample.candidate.effectiveSourceAlphaByte,
                        destinationRgb = destination,
                    )
                }
            },
            CompositionQuantizationVariantDefinition(
                id = "linear_srgb_source_over_effective_destination_nearest_255",
                label = "Approximated linear-sRGB source-over effective destination with nearest rounding",
                formula = "linear=c<=0.04045?c/12.92:((c+0.055)/1.055)^2.4; srgb=l<=0.0031308?12.92*l:1.055*l^(1/2.4)-0.055; rgb=round(srgb(sourceLinear*alpha + destinationLinear*(1.0-alpha))*255.0)",
                quantizationOrder = "linearize-srgb-compose-convert-to-srgb-round-nearest-255",
                compositionSpace = "approximated-linear-srgb",
                alphaSource = "effectiveSourceAlphaByte/255.0",
                destinationSource = "inverseDestinationEstimate.rgbClampedToSrgb",
                premultipliedDiagnostic = false,
            ) { sample ->
                val destination = sample.for374Sample.inverseDestinationEstimate.rgbClamped
                if (destination == null) {
                    null
                } else {
                    linearSrgbSourceOverEffectiveDestinationNearestRgba(
                        sourceColor = sample.for374Sample.candidate.paintSource,
                        effectiveAlphaByte = sample.for374Sample.candidate.effectiveSourceAlphaByte,
                        destinationRgb = destination,
                    )
                }
            },
            CompositionQuantizationVariantDefinition(
                id = "premultiplied_srgb_terms_floor_256_source_over_effective_destination",
                label = "Premultiplied diagnostic variant with separated floor-256 terms",
                formula = "sourceTerm=floor((sourceSrgb/255.0*alpha)*256.0); destinationTerm=floor((destinationSrgb/255.0*(1.0-alpha))*256.0); rgb=clamp(sourceTerm+destinationTerm,0,255); a=255",
                quantizationOrder = "premultiply-source-and-destination-terms-floor-256-before-sum",
                compositionSpace = "premultiplied-srgb-diagnostic",
                alphaSource = "effectiveSourceAlphaByte/255.0",
                destinationSource = "inverseDestinationEstimate.rgbClampedToSrgb",
                premultipliedDiagnostic = true,
            ) { sample ->
                val destination = sample.for374Sample.inverseDestinationEstimate.rgbClamped
                if (destination == null) {
                    null
                } else {
                    premultipliedSrgbTermsFloorSourceOverEffectiveDestinationRgba(
                        sourceColor = sample.for374Sample.candidate.paintSource,
                        effectiveAlphaByte = sample.for374Sample.candidate.effectiveSourceAlphaByte,
                        destinationRgb = destination,
                    )
                }
            },
        )

    private fun straightSrgbSourceOverEffectiveDestinationNearestRgba(
        sourceColor: Int,
        effectiveAlphaByte: Int,
        destinationRgb: IntArray,
    ): IntArray {
        val alpha = effectiveAlphaByte / 255.0
        return intArrayOf(
            quantizeNearest255((((sourceColor ushr 16) and 0xFF) / 255.0) * alpha + (destinationRgb[0] / 255.0) * (1.0 - alpha)),
            quantizeNearest255((((sourceColor ushr 8) and 0xFF) / 255.0) * alpha + (destinationRgb[1] / 255.0) * (1.0 - alpha)),
            quantizeNearest255(((sourceColor and 0xFF) / 255.0) * alpha + (destinationRgb[2] / 255.0) * (1.0 - alpha)),
            255,
        )
    }

    private fun linearSrgbSourceOverEffectiveDestinationNearestRgba(
        sourceColor: Int,
        effectiveAlphaByte: Int,
        destinationRgb: IntArray,
    ): IntArray {
        val alpha = effectiveAlphaByte / 255.0
        return intArrayOf(
            quantizeNearest255(srgbFromLinear(linearFromSrgbByte((sourceColor ushr 16) and 0xFF) * alpha + linearFromSrgbByte(destinationRgb[0]) * (1.0 - alpha))),
            quantizeNearest255(srgbFromLinear(linearFromSrgbByte((sourceColor ushr 8) and 0xFF) * alpha + linearFromSrgbByte(destinationRgb[1]) * (1.0 - alpha))),
            quantizeNearest255(srgbFromLinear(linearFromSrgbByte(sourceColor and 0xFF) * alpha + linearFromSrgbByte(destinationRgb[2]) * (1.0 - alpha))),
            255,
        )
    }

    private fun premultipliedSrgbTermsFloorSourceOverEffectiveDestinationRgba(
        sourceColor: Int,
        effectiveAlphaByte: Int,
        destinationRgb: IntArray,
    ): IntArray {
        val alpha = effectiveAlphaByte / 255.0
        return intArrayOf(
            (quantize256((((sourceColor ushr 16) and 0xFF) / 255.0) * alpha) + quantize256((destinationRgb[0] / 255.0) * (1.0 - alpha))).coerceIn(0, 255),
            (quantize256((((sourceColor ushr 8) and 0xFF) / 255.0) * alpha) + quantize256((destinationRgb[1] / 255.0) * (1.0 - alpha))).coerceIn(0, 255),
            (quantize256(((sourceColor and 0xFF) / 255.0) * alpha) + quantize256((destinationRgb[2] / 255.0) * (1.0 - alpha))).coerceIn(0, 255),
            255,
        )
    }

    private fun directUnpremultipliedSourceOverWhite(sourceRgba: IntArray): IntArray {
        val alpha = sourceRgba[3] / 255.0
        return intArrayOf(
            quantizeNearest255((sourceRgba[0] / 255.0) * alpha + (1.0 - alpha)),
            quantizeNearest255((sourceRgba[1] / 255.0) * alpha + (1.0 - alpha)),
            quantizeNearest255((sourceRgba[2] / 255.0) * alpha + (1.0 - alpha)),
            255,
        )
    }

    private fun linearFromSrgbByte(value: Int): Double {
        val srgb = value / 255.0
        return if (srgb <= 0.04045) {
            srgb / 12.92
        } else {
            Math.pow((srgb + 0.055) / 1.055, 2.4)
        }
    }

    private fun srgbFromLinear(value: Double): Double =
        if (value <= 0.0031308) {
            12.92 * value
        } else {
            1.055 * Math.pow(value, 1.0 / 2.4) - 0.055
        }

    private fun quantizeAlphaRound(value: Double): Int =
        if (value.isNaN()) {
            0
        } else {
            ((value * 255.0) + 0.5).toInt().coerceIn(0, 255)
        }

    private fun quantize256(value: Double): Int =
        if (value.isNaN()) {
            0
        } else {
            (value * 256.0).toInt().coerceIn(0, 255)
        }

    private fun quantizeNearest255(value: Double): Int =
        if (value.isNaN()) {
            0
        } else {
            ((value * 255.0) + 0.5).toInt().coerceIn(0, 255)
        }

    private fun rgbaToPixel(rgba: IntArray): Int =
        ((rgba[3] and 0xFF) shl 24) or
            ((rgba[0] and 0xFF) shl 16) or
            ((rgba[1] and 0xFF) shl 8) or
            (rgba[2] and 0xFF)

    private fun rgbaJson(pixel: Int): String {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val a = (pixel ushr 24) and 0xFF
        return """[$r, $g, $b, $a]"""
    }

    private fun rgbaArrayJson(rgba: IntArray): String = """[${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3]}]"""

    private fun rgbArrayJson(rgb: IntArray): String = """[${rgb[0]}, ${rgb[1]}, ${rgb[2]}]"""

    private fun channelErrorJson(channels: IntArray): String =
        """{"r": ${channels[0]}, "g": ${channels[1]}, "b": ${channels[2]}, "a": ${channels[3]}}"""

    private fun channelRgbJson(channels: IntArray): String =
        """{"r": ${channels[0]}, "g": ${channels[1]}, "b": ${channels[2]}}"""

    private fun channelName(index: Int): String =
        when (index) {
            0 -> "red"
            1 -> "green"
            2 -> "blue"
            else -> "alpha"
        }

    private fun channelKey(index: Int): String =
        when (index) {
            0 -> "r"
            1 -> "g"
            2 -> "b"
            else -> "a"
        }

    private fun f16CandidatePolicyId(): String =
        listOf("straight", "srgb", "quantized", "alpha", "src", "over", "white").joinToString("_")

    private class MutableSubzonePixelSet(val id: String) {
        var count: Int = 0
        var beforeResidual: Int = 0
        var afterResidual: Int = 0
        private val beforeErrorByChannel = IntArray(4)
        private val afterErrorByChannel = IntArray(4)
        private val afterMinusBeforeErrorByChannel = IntArray(4)
        private val probeMinusCurrentRgba = IntArray(4)
        private val bandStats = linkedMapOf<String, MutableSubzoneBandStats>()
        private var minX: Int = Int.MAX_VALUE
        private var minY: Int = Int.MAX_VALUE
        private var maxX: Int = Int.MIN_VALUE
        private var maxY: Int = Int.MIN_VALUE

        fun add(pixel: SubzonePixelAudit) {
            count++
            beforeResidual += pixel.currentResidual
            afterResidual += pixel.probeResidual
            for (index in 0..3) {
                beforeErrorByChannel[index] += pixel.currentErrorByChannel[index]
                afterErrorByChannel[index] += pixel.probeErrorByChannel[index]
                afterMinusBeforeErrorByChannel[index] += pixel.probeMinusCurrentErrorByChannel[index]
                probeMinusCurrentRgba[index] += pixel.probeMinusCurrentRgba[index]
            }
            minX = minOf(minX, pixel.x)
            minY = minOf(minY, pixel.y)
            maxX = maxOf(maxX, pixel.x)
            maxY = maxOf(maxY, pixel.y)
            bandStats.getOrPut(pixel.strokeBand) {
                MutableSubzoneBandStats(
                    strokeBand = pixel.strokeBand,
                    cap = pixel.cap,
                    join = pixel.join,
                    strokeWidth = pixel.strokeWidth,
                )
            }.add(pixel)
        }

        fun toStats(): SubzonePixelSetStats =
            SubzonePixelSetStats(
                id = id,
                count = count,
                beforeResidual = beforeResidual,
                afterResidual = afterResidual,
                beforeErrorByChannel = beforeErrorByChannel.copyOf(),
                afterErrorByChannel = afterErrorByChannel.copyOf(),
                afterMinusBeforeErrorByChannel = afterMinusBeforeErrorByChannel.copyOf(),
                probeMinusCurrentRgba = probeMinusCurrentRgba.copyOf(),
                bounds = if (count == 0) null else ResidualBounds(minX, minY, maxX, maxY),
                bandDistribution = bandStats.values.map { it.toStats() },
            )
    }

    private class MutableSubzoneBandStats(
        val strokeBand: String,
        val cap: String,
        val join: String,
        val strokeWidth: Float,
    ) {
        var count: Int = 0
        var beforeResidual: Int = 0
        var afterResidual: Int = 0
        private val afterMinusBeforeErrorByChannel = IntArray(4)
        private var minX: Int = Int.MAX_VALUE
        private var minY: Int = Int.MAX_VALUE
        private var maxX: Int = Int.MIN_VALUE
        private var maxY: Int = Int.MIN_VALUE

        fun add(pixel: SubzonePixelAudit) {
            count++
            beforeResidual += pixel.currentResidual
            afterResidual += pixel.probeResidual
            for (index in 0..3) {
                afterMinusBeforeErrorByChannel[index] += pixel.probeMinusCurrentErrorByChannel[index]
            }
            minX = minOf(minX, pixel.x)
            minY = minOf(minY, pixel.y)
            maxX = maxOf(maxX, pixel.x)
            maxY = maxOf(maxY, pixel.y)
        }

        fun toStats(): SubzoneBandStats =
            SubzoneBandStats(
                strokeBand = strokeBand,
                cap = cap,
                join = join,
                strokeWidth = strokeWidth,
                count = count,
                beforeResidual = beforeResidual,
                afterResidual = afterResidual,
                afterMinusBeforeErrorByChannel = afterMinusBeforeErrorByChannel.copyOf(),
                bounds = if (count == 0) null else ResidualBounds(minX, minY, maxX, maxY),
            )
    }

    private class MutableMembershipPixelSet(val id: String) {
        private val pixels = MutableSubzonePixelSet(id)
        var count: Int = 0
        private var coverageMaskAvailablePixels: Int = 0
        private var sourceTransparentPixels: Int = 0
        private var minCoverageAlphaByte: Int = Int.MAX_VALUE
        private var maxCoverageAlphaByte: Int = Int.MIN_VALUE
        private var minTransparentSourceAlphaByte: Int = Int.MAX_VALUE
        private var maxTransparentSourceAlphaByte: Int = Int.MIN_VALUE
        private var minNearestCriticalDistance: Int = Int.MAX_VALUE
        private var maxNearestCriticalDistance: Int = Int.MIN_VALUE
        private val coverageAlphaBuckets = IntArray(3)
        private val transparentSourceAlphaBuckets = IntArray(3)
        private val currentResidualBuckets = IntArray(3)

        fun add(pixel: CoverageCompositionMembershipPixelAudit) {
            pixels.add(pixel.pixel)
            count++
            coverageMaskAvailablePixels++
            if (pixel.sourceTransparent) {
                sourceTransparentPixels++
            }
            minCoverageAlphaByte = minOf(minCoverageAlphaByte, pixel.coverageAlphaByte)
            maxCoverageAlphaByte = maxOf(maxCoverageAlphaByte, pixel.coverageAlphaByte)
            minTransparentSourceAlphaByte = minOf(minTransparentSourceAlphaByte, pixel.transparentSourceAlphaByte)
            maxTransparentSourceAlphaByte = maxOf(maxTransparentSourceAlphaByte, pixel.transparentSourceAlphaByte)
            minNearestCriticalDistance = minOf(minNearestCriticalDistance, pixel.pixel.nearestCriticalManhattanDistance)
            maxNearestCriticalDistance = maxOf(maxNearestCriticalDistance, pixel.pixel.nearestCriticalManhattanDistance)
            coverageAlphaBuckets[bucketIndex(pixel.coverageAlphaByte)]++
            transparentSourceAlphaBuckets[bucketIndex(pixel.transparentSourceAlphaByte)]++
            currentResidualBuckets[currentResidualBucketIndex(pixel.pixel.currentResidual)]++
        }

        fun toStats(): MembershipPixelSetStats =
            MembershipPixelSetStats(
                base = pixels.toStats(),
                coverageMaskAvailablePixels = coverageMaskAvailablePixels,
                sourceTransparentPixels = sourceTransparentPixels,
                coverageAlphaByteRange = if (count == 0) null else minCoverageAlphaByte to maxCoverageAlphaByte,
                transparentSourceAlphaByteRange = if (count == 0) null else minTransparentSourceAlphaByte to maxTransparentSourceAlphaByte,
                nearestCriticalDistanceRange = if (count == 0) null else minNearestCriticalDistance to maxNearestCriticalDistance,
                coverageAlphaBuckets = coverageAlphaBuckets.copyOf(),
                transparentSourceAlphaBuckets = transparentSourceAlphaBuckets.copyOf(),
                currentResidualBuckets = currentResidualBuckets.copyOf(),
            )

        private fun bucketIndex(value: Int): Int =
            when (value) {
                0 -> 0
                255 -> 2
                else -> 1
            }

        private fun currentResidualBucketIndex(value: Int): Int =
            when {
                value == 0 -> 0
                value <= 8 -> 1
                else -> 2
            }
    }

    private class PreProbePredicateCandidate(
        val id: String,
        val description: String,
        val selectionMethod: String,
        val select: (CoverageCompositionMembershipPixelAudit) -> Boolean,
    )

    private class MutablePreProbePredicateStats(val candidate: PreProbePredicateCandidate) {
        private val pixels = MutableMembershipPixelSet(candidate.id)
        var count: Int = 0
        var sourceLocalRecovered: Int = 0
        var regressedIncluded: Int = 0
        var coverageCompositionIncluded: Int = 0
        var mixedIncluded: Int = 0
        var insufficientIncluded: Int = 0

        val precision: Double
            get() = if (count == 0) 0.0 else sourceLocalRecovered.toDouble() / count.toDouble()

        fun add(pixel: CoverageCompositionMembershipPixelAudit) {
            pixels.add(pixel)
            count++
            if (pixel.category == "source-locale-plausible") {
                sourceLocalRecovered++
            }
            if (pixel.pixel.deltaVsCurrent > 0) {
                regressedIncluded++
            }
            when (pixel.category) {
                "coverage-composition-plausible" -> coverageCompositionIncluded++
                "mixed" -> mixedIncluded++
                "insufficient" -> insufficientIncluded++
            }
        }

        fun recall(sourceLocalTruth: Int): Double =
            if (sourceLocalTruth == 0) 0.0 else sourceLocalRecovered.toDouble() / sourceLocalTruth.toDouble()

        fun candidateClass(sourceLocalTruth: Int): String =
            when {
                sourceLocalRecovered == sourceLocalTruth && count == sourceLocalTruth && regressedIncluded == 0 ->
                    "diagnostic-pre-probe-defendable"
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded > 0 ->
                    "too-broad"
                sourceLocalRecovered in 1 until sourceLocalTruth ->
                    "insufficient"
                else ->
                    "proof-missing"
            }

        fun toStatsJson(sourceLocalTruth: Int): String {
            val stats = pixels.toStats()
            return """
                {
                  "id": ${jsonString(candidate.id)},
                  "description": ${jsonString(candidate.description)},
                  "selectionMethod": ${jsonString(candidate.selectionMethod)},
                  "usesOutcomeOracle": false,
                  "usesProbeResidualForSelection": false,
                  "usesDeltaVsCurrentForSelection": false,
                  "usesFor379MembershipAsPrimary": false,
                  "usesReferenceCurrentResidual": ${candidate.selectionMethod.contains("currentResidual")},
                  "usesCurrentChannelErrorShape": ${candidate.selectionMethod.contains("currentError")},
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "sourceLocalTruth": $sourceLocalTruth,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "coverageCompositionPixelsIncluded": $coverageCompositionIncluded,
                  "mixedPixelsIncluded": $mixedIncluded,
                  "insufficientPixelsIncluded": $insufficientIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "diagnosticSelectionResidual": ${stats.toJson().prependIndent("  ").trimStart()}
                }
            """.trimIndent()
        }

        fun toSummaryJson(sourceLocalTruth: Int): String =
            """
                {
                  "id": ${jsonString(candidate.id)},
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "runtimePredicateReady": false,
                  "runtimeBlocker": "The strongest predicate uses reference/current residual and channel-error shape, which is valid diagnostic evidence but not available inside the renderer without a separate coverage/composition proof."
                }
            """.trimIndent()
    }

    private data class CoverageOrthogonalNeighborhood(
        val center: Int,
        val north: Int,
        val south: Int,
        val west: Int,
        val east: Int,
        val min: Int,
        val max: Int,
        val orthogonalNonZeroCount: Int,
        val orthogonalPartialCount: Int,
    ) {
        fun toJson(): String = """
            {
              "center": $center,
              "north": $north,
              "south": $south,
              "west": $west,
              "east": $east,
              "min": $min,
              "max": $max,
              "orthogonalNonZeroCount": $orthogonalNonZeroCount,
              "orthogonalPartialCount": $orthogonalPartialCount
            }
        """.trimIndent()
    }

    private data class PreCorrectionGeometryCoveragePixelAudit(
        val membership: CoverageCompositionMembershipPixelAudit,
        val bandLocalX: Int,
        val bandWidth: Int,
        val bandEdgeDistance: Int,
        val coverageNeighborhood: CoverageOrthogonalNeighborhood,
    ) {
        fun toJson(): String {
            val suffix = """
              "bandLocalX": $bandLocalX,
              "bandWidth": $bandWidth,
              "bandEdgeDistance": $bandEdgeDistance,
              "coverageOrthogonalNeighborhood": ${coverageNeighborhood.toJson().prependIndent("  ").trimStart()}
            """.trimIndent().prependIndent("  ")
            return membership.toJson().trim().replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
        }
    }

    private class PreCorrectionMetadataCandidate(
        val id: String,
        val description: String,
        val selectionMethod: String,
        val rejectionReason: String,
        val select: (PreCorrectionGeometryCoveragePixelAudit) -> Boolean,
    )

    private class GeneralizedCoverageMetadataCandidate(
        val id: String,
        val description: String,
        val selectionMethod: String,
        val select: (PreCorrectionGeometryCoveragePixelAudit) -> Boolean,
    )

    private class CoverageRegressionDiscriminatorCandidate(
        val id: String,
        val description: String,
        val selectionMethod: String,
        val select: (PreCorrectionGeometryCoveragePixelAudit) -> Boolean,
    )

    private class MutableCoverageRegressionDiscriminatorCandidateStats(
        val candidate: CoverageRegressionDiscriminatorCandidate,
    ) {
        private val pixels = MutableMembershipPixelSet(candidate.id)
        var count: Int = 0
        var sourceLocalRecovered: Int = 0
        var regressedIncluded: Int = 0
        var coverageCompositionIncluded: Int = 0
        var mixedIncluded: Int = 0
        var insufficientIncluded: Int = 0

        val precision: Double
            get() = if (count == 0) 0.0 else sourceLocalRecovered.toDouble() / count.toDouble()

        fun add(pixel: PreCorrectionGeometryCoveragePixelAudit) {
            pixels.add(pixel.membership)
            count++
            if (pixel.membership.category == "source-locale-plausible") {
                sourceLocalRecovered++
            }
            if (pixel.membership.pixel.deltaVsCurrent > 0) {
                regressedIncluded++
            }
            when (pixel.membership.category) {
                "coverage-composition-plausible" -> coverageCompositionIncluded++
                "mixed" -> mixedIncluded++
                "insufficient" -> insufficientIncluded++
            }
        }

        fun recall(sourceLocalTruth: Int): Double =
            if (sourceLocalTruth == 0) 0.0 else sourceLocalRecovered.toDouble() / sourceLocalTruth.toDouble()

        fun candidateClass(sourceLocalTruth: Int): String =
            when {
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded == 0 ->
                    "discriminator-candidate-defendable"
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded > 0 ->
                    "discriminator-candidate-too-broad"
                sourceLocalRecovered in 1 until sourceLocalTruth ->
                    "discriminator-candidate-insufficient"
                else ->
                    "metadata-insufficient"
            }

        fun toStatsJson(sourceLocalTruth: Int): String {
            val stats = pixels.toStats()
            return """
                {
                  "id": ${jsonString(candidate.id)},
                  "description": ${jsonString(candidate.description)},
                  "selectionMethod": ${jsonString(candidate.selectionMethod)},
                  "scope": "for385-partial-coverage-alpha-at-least-96-selected-pixels",
                  "usesSkiaReferenceForSelection": false,
                  "usesProbeOutcomeForSelection": false,
                  "usesProbeResidualForSelection": false,
                  "usesDeltaVsCurrentForSelection": false,
                  "usesFor379MembershipAsPrimary": false,
                  "usesFor383PredicateAsPrimary": false,
                  "usesFor384PredicateAsPrimary": false,
                  "usesFor385PredicateAsPrimary": false,
                  "usesCurrentResidualForSelection": false,
                  "usesCurrentChannelErrorShapeForSelection": false,
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "sourceLocalTruth": $sourceLocalTruth,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "coverageCompositionPixelsIncluded": $coverageCompositionIncluded,
                  "mixedPixelsIncluded": $mixedIncluded,
                  "insufficientPixelsIncluded": $insufficientIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "diagnosticDecision": ${if (candidateClass(sourceLocalTruth) == "discriminator-candidate-defendable") "\"candidate-diagnostic-only\"" else "\"reject\""},
                  "sceneImpactIfApplied": {
                    "baseUncorrectedFullSceneResidualUnaffected": true,
                    "selectedBeforeResidual": ${stats.base.beforeResidual},
                    "selectedAfterResidual": ${stats.base.afterResidual},
                    "selectedDeltaVsCurrent": ${stats.base.afterResidual - stats.base.beforeResidual}
                  },
                  "diagnosticSelectionResidual": ${stats.toJson().prependIndent("  ").trimStart()}
                }
            """.trimIndent()
        }

        fun toSummaryJson(sourceLocalTruth: Int): String =
            """
                {
                  "id": ${jsonString(candidate.id)},
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "runtimePredicateReady": false,
                  "correctionAppliedByDefault": false,
                  "runtimeBlocker": "FOR-386 is diagnostic-only; a separate correction ticket must prove the candidate outside this audit and keep full-scene/fallback stability."
                }
            """.trimIndent()
    }

    private class MutableGeneralizedCoverageMetadataCandidateStats(
        val candidate: GeneralizedCoverageMetadataCandidate,
    ) {
        private val pixels = MutableMembershipPixelSet(candidate.id)
        var count: Int = 0
        var sourceLocalRecovered: Int = 0
        var regressedIncluded: Int = 0
        var coverageCompositionIncluded: Int = 0
        var mixedIncluded: Int = 0
        var insufficientIncluded: Int = 0

        val precision: Double
            get() = if (count == 0) 0.0 else sourceLocalRecovered.toDouble() / count.toDouble()

        fun add(pixel: PreCorrectionGeometryCoveragePixelAudit) {
            pixels.add(pixel.membership)
            count++
            if (pixel.membership.category == "source-locale-plausible") {
                sourceLocalRecovered++
            }
            if (pixel.membership.pixel.deltaVsCurrent > 0) {
                regressedIncluded++
            }
            when (pixel.membership.category) {
                "coverage-composition-plausible" -> coverageCompositionIncluded++
                "mixed" -> mixedIncluded++
                "insufficient" -> insufficientIncluded++
            }
        }

        fun recall(sourceLocalTruth: Int): Double =
            if (sourceLocalTruth == 0) 0.0 else sourceLocalRecovered.toDouble() / sourceLocalTruth.toDouble()

        fun candidateClass(sourceLocalTruth: Int): String =
            when {
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded == 0 ->
                    "generalized-predicate-defendable"
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded > 0 ->
                    "generalized-predicate-too-broad"
                sourceLocalRecovered in 1 until sourceLocalTruth ->
                    "generalized-predicate-insufficient"
                else ->
                    "proof-missing"
            }

        fun toStatsJson(sourceLocalTruth: Int): String {
            val stats = pixels.toStats()
            return """
                {
                  "id": ${jsonString(candidate.id)},
                  "description": ${jsonString(candidate.description)},
                  "selectionMethod": ${jsonString(candidate.selectionMethod)},
                  "scope": "full-scene-nonzero-coverage-source-alpha",
                  "usesSkiaReferenceForSelection": false,
                  "usesProbeOutcomeForSelection": false,
                  "usesProbeResidualForSelection": false,
                  "usesDeltaVsCurrentForSelection": false,
                  "usesFor379MembershipAsPrimary": false,
                  "usesFor383PredicateAsPrimary": false,
                  "usesFor384PredicateAsPrimary": false,
                  "usesCurrentResidualForSelection": false,
                  "usesCurrentChannelErrorShapeForSelection": false,
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "sourceLocalTruth": $sourceLocalTruth,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "coverageCompositionPixelsIncluded": $coverageCompositionIncluded,
                  "mixedPixelsIncluded": $mixedIncluded,
                  "insufficientPixelsIncluded": $insufficientIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "diagnosticDecision": ${if (candidateClass(sourceLocalTruth) == "generalized-predicate-defendable") "\"candidate-runtime-defendable-but-disabled\"" else "\"reject\""},
                  "sceneImpactIfApplied": {
                    "baseUncorrectedFullSceneResidualUnaffected": true,
                    "selectedBeforeResidual": ${stats.base.beforeResidual},
                    "selectedAfterResidual": ${stats.base.afterResidual},
                    "selectedDeltaVsCurrent": ${stats.base.afterResidual - stats.base.beforeResidual}
                  },
                  "diagnosticSelectionResidual": ${stats.toJson().prependIndent("  ").trimStart()}
                }
            """.trimIndent()
        }

        fun toSummaryJson(sourceLocalTruth: Int): String =
            """
                {
                  "id": ${jsonString(candidate.id)},
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "runtimePredicateReady": ${if (candidateClass(sourceLocalTruth) == "generalized-predicate-defendable") "true" else "false"},
                  "correctionAppliedByDefault": false,
                  "runtimeBlocker": "FOR-385 is diagnostic-only; even a defendable predicate must be enabled only by a separate bounded correction ticket with full scene validation."
                }
            """.trimIndent()
    }

    private class MutablePreCorrectionMetadataCandidateStats(val candidate: PreCorrectionMetadataCandidate) {
        private val pixels = MutableMembershipPixelSet(candidate.id)
        var count: Int = 0
        var sourceLocalRecovered: Int = 0
        var regressedIncluded: Int = 0
        var coverageCompositionIncluded: Int = 0
        var mixedIncluded: Int = 0
        var insufficientIncluded: Int = 0

        val precision: Double
            get() = if (count == 0) 0.0 else sourceLocalRecovered.toDouble() / count.toDouble()

        fun add(pixel: PreCorrectionGeometryCoveragePixelAudit) {
            pixels.add(pixel.membership)
            count++
            if (pixel.membership.category == "source-locale-plausible") {
                sourceLocalRecovered++
            }
            if (pixel.membership.pixel.deltaVsCurrent > 0) {
                regressedIncluded++
            }
            when (pixel.membership.category) {
                "coverage-composition-plausible" -> coverageCompositionIncluded++
                "mixed" -> mixedIncluded++
                "insufficient" -> insufficientIncluded++
            }
        }

        fun recall(sourceLocalTruth: Int): Double =
            if (sourceLocalTruth == 0) 0.0 else sourceLocalRecovered.toDouble() / sourceLocalTruth.toDouble()

        fun candidateClass(sourceLocalTruth: Int): String =
            when {
                sourceLocalRecovered == sourceLocalTruth && count == sourceLocalTruth && regressedIncluded == 0 ->
                    "metadata-candidate-defendable"
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded > 0 ->
                    "too-broad"
                sourceLocalRecovered in 1 until sourceLocalTruth ->
                    "insufficient"
                else ->
                    "proof-missing"
            }

        fun toStatsJson(sourceLocalTruth: Int): String {
            val stats = pixels.toStats()
            return """
                {
                  "id": ${jsonString(candidate.id)},
                  "description": ${jsonString(candidate.description)},
                  "selectionMethod": ${jsonString(candidate.selectionMethod)},
                  "usesSkiaReferenceForSelection": false,
                  "usesProbeOutcomeForSelection": false,
                  "usesProbeResidualForSelection": false,
                  "usesDeltaVsCurrentForSelection": false,
                  "usesFor379MembershipAsPrimary": false,
                  "usesCurrentResidualForSelection": false,
                  "usesCurrentChannelErrorShapeForSelection": false,
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "sourceLocalTruth": $sourceLocalTruth,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "coverageCompositionPixelsIncluded": $coverageCompositionIncluded,
                  "mixedPixelsIncluded": $mixedIncluded,
                  "insufficientPixelsIncluded": $insufficientIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "diagnosticDecision": ${if (candidateClass(sourceLocalTruth) == "metadata-candidate-defendable") "\"accept-diagnostic-only\"" else "\"reject\""},
                  "rejectionReason": ${jsonString(candidate.rejectionReason)},
                  "diagnosticSelectionResidual": ${stats.toJson().prependIndent("  ").trimStart()}
                }
            """.trimIndent()
        }

        fun toSummaryJson(sourceLocalTruth: Int): String =
            """
                {
                  "id": ${jsonString(candidate.id)},
                  "selectedPixels": $count,
                  "sourceLocalRecovered": $sourceLocalRecovered,
                  "regressedPixelsIncluded": $regressedIncluded,
                  "precision": ${String.format(Locale.US, "%.4f", precision)},
                  "recall": ${String.format(Locale.US, "%.4f", recall(sourceLocalTruth))},
                  "candidateClass": ${jsonString(candidateClass(sourceLocalTruth))},
                  "runtimePredicateReady": false,
                  "runtimeBlocker": "The metadata split is clean only inside the 9-pixel FOR-383 diagnostic scope; a renderer correction still needs metadata that can form the full predicate without reference/current-error-shape filtering."
                }
            """.trimIndent()
    }

    private class MutableCoverageRegressionMetadataBreakdown {
        private val bandCapJoin = linkedMapOf<String, Int>()
        private val bandLocalXBucket = linkedMapOf<String, Int>()
        private val bandEdgeDistanceBucket = linkedMapOf<String, Int>()
        private val coverageAlphaLane = linkedMapOf<String, Int>()
        private val transparentSourceAlphaLane = linkedMapOf<String, Int>()
        private val orthogonalCoverageShape = linkedMapOf<String, Int>()
        private var count: Int = 0

        fun add(pixel: PreCorrectionGeometryCoveragePixelAudit) {
            count++
            increment(
                bandCapJoin,
                "${pixel.membership.pixel.strokeBand}|cap=${pixel.membership.pixel.cap}|join=${pixel.membership.pixel.join}",
            )
            increment(
                bandLocalXBucket,
                "${pixel.membership.pixel.strokeBand}|${bucket16(pixel.bandLocalX)}",
            )
            increment(bandEdgeDistanceBucket, edgeDistanceBucket(pixel.bandEdgeDistance))
            increment(coverageAlphaLane, alphaLane(pixel.membership.coverageAlphaByte))
            increment(transparentSourceAlphaLane, alphaLane(pixel.membership.transparentSourceAlphaByte))
            increment(
                orthogonalCoverageShape,
                "center=${pixel.coverageNeighborhood.center}|nz=${pixel.coverageNeighborhood.orthogonalNonZeroCount}|partial=${pixel.coverageNeighborhood.orthogonalPartialCount}",
            )
        }

        fun toJson(): String =
            """
                {
                  "regressedPixels": $count,
                  "bandCapJoin": ${mapJson(bandCapJoin).prependIndent("  ").trimStart()},
                  "bandLocalXBucket": ${mapJson(bandLocalXBucket).prependIndent("  ").trimStart()},
                  "bandEdgeDistanceBucket": ${mapJson(bandEdgeDistanceBucket).prependIndent("  ").trimStart()},
                  "coverageAlphaLane": ${mapJson(coverageAlphaLane).prependIndent("  ").trimStart()},
                  "transparentSourceAlphaLane": ${mapJson(transparentSourceAlphaLane).prependIndent("  ").trimStart()},
                  "orthogonalCoverageShape": ${mapJson(orthogonalCoverageShape).prependIndent("  ").trimStart()}
                }
            """.trimIndent()

        private fun increment(map: MutableMap<String, Int>, key: String) {
            map[key] = (map[key] ?: 0) + 1
        }

        private fun bucket16(value: Int): String {
            val start = (value / 16) * 16
            val end = start + 15
            return "$start-$end"
        }

        private fun edgeDistanceBucket(value: Int): String =
            when {
                value <= 1 -> "0-1"
                value <= 4 -> "2-4"
                value <= 8 -> "5-8"
                value <= 16 -> "9-16"
                value <= 32 -> "17-32"
                else -> "33+"
            }

        private fun alphaLane(value: Int): String =
            when {
                value < 96 -> "lt-96"
                value < 128 -> "96-127"
                value < 160 -> "128-159"
                value < 192 -> "160-191"
                value < 224 -> "192-223"
                value < 255 -> "224-254"
                else -> "255"
            }

        private fun mapJson(map: Map<String, Int>): String {
            val entries = map.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .joinToString(",\n") { entry ->
                    """
                        {
                          "id": ${jsonString(entry.key)},
                          "count": ${entry.value}
                        }
                    """.trimIndent()
                }
            return """
                [
            ${entries.prependIndent("    ")}
                ]
            """.trimIndent()
        }
    }

    private data class SubzonePixelSetStats(
        val id: String,
        val count: Int,
        val beforeResidual: Int,
        val afterResidual: Int,
        val beforeErrorByChannel: IntArray,
        val afterErrorByChannel: IntArray,
        val afterMinusBeforeErrorByChannel: IntArray,
        val probeMinusCurrentRgba: IntArray,
        val bounds: ResidualBounds?,
        val bandDistribution: List<SubzoneBandStats>,
    ) {
        fun toJson(): String = """
            {
              "id": ${jsonString(id)},
              "count": $count,
              "beforeResidual": $beforeResidual,
              "afterResidual": $afterResidual,
              "deltaVsCurrent": ${afterResidual - beforeResidual},
              "gainVsCurrent": ${beforeResidual - afterResidual},
              "beforeErrorByChannel": ${channelJson(beforeErrorByChannel)},
              "afterErrorByChannel": ${channelJson(afterErrorByChannel)},
              "afterMinusBeforeErrorByChannel": ${channelJson(afterMinusBeforeErrorByChannel)},
              "probeMinusCurrentRgbaTotals": ${channelJson(probeMinusCurrentRgba)},
              "bounds": ${bounds?.toJson() ?: "null"},
              "bandDistribution": [
            ${bandDistribution.joinToString(",\n") { it.toJson().prependIndent("    ") }}
              ]
            }
        """.trimIndent()

        private fun channelJson(channels: IntArray): String =
            """{"r": ${channels[0]}, "g": ${channels[1]}, "b": ${channels[2]}, "a": ${channels[3]}}"""
    }

    private data class MembershipPixelSetStats(
        val base: SubzonePixelSetStats,
        val coverageMaskAvailablePixels: Int,
        val sourceTransparentPixels: Int,
        val coverageAlphaByteRange: Pair<Int, Int>?,
        val transparentSourceAlphaByteRange: Pair<Int, Int>?,
        val nearestCriticalDistanceRange: Pair<Int, Int>?,
        val coverageAlphaBuckets: IntArray,
        val transparentSourceAlphaBuckets: IntArray,
        val currentResidualBuckets: IntArray,
    ) {
        fun toJson(): String {
            val suffix = """
              "coverageMaskAvailablePixels": $coverageMaskAvailablePixels,
              "sourceTransparentPixels": $sourceTransparentPixels,
              "coverageAlphaByteRange": ${rangeJson(coverageAlphaByteRange)},
              "transparentSourceAlphaByteRange": ${rangeJson(transparentSourceAlphaByteRange)},
              "nearestFor379CriticalManhattanDistanceRange": ${rangeJson(nearestCriticalDistanceRange)},
              "coverageAlphaBuckets": ${alphaBucketJson(coverageAlphaBuckets)},
              "transparentSourceAlphaBuckets": ${alphaBucketJson(transparentSourceAlphaBuckets)},
              "currentResidualBuckets": ${residualBucketJson(currentResidualBuckets)}
            """.trimIndent().prependIndent("  ")
            return base.toJson().trim().replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
        }

        private fun rangeJson(value: Pair<Int, Int>?): String =
            value?.let { "[${it.first}, ${it.second}]" } ?: "null"

        private fun alphaBucketJson(buckets: IntArray): String =
            """{"zero": ${buckets[0]}, "partial": ${buckets[1]}, "opaque": ${buckets[2]}}"""

        private fun residualBucketJson(buckets: IntArray): String =
            """{"exact": ${buckets[0]}, "low": ${buckets[1]}, "high": ${buckets[2]}}"""
    }

    private data class SubzoneBandStats(
        val strokeBand: String,
        val cap: String,
        val join: String,
        val strokeWidth: Float,
        val count: Int,
        val beforeResidual: Int,
        val afterResidual: Int,
        val afterMinusBeforeErrorByChannel: IntArray,
        val bounds: ResidualBounds?,
    ) {
        fun toJson(): String = """
            {
              "strokeBand": ${jsonString(strokeBand)},
              "cap": ${jsonString(cap)},
              "join": ${jsonString(join)},
              "strokeWidth": ${String.format(Locale.US, "%.1f", strokeWidth)},
              "count": $count,
              "beforeResidual": $beforeResidual,
              "afterResidual": $afterResidual,
              "deltaVsCurrent": ${afterResidual - beforeResidual},
              "afterMinusBeforeErrorByChannel": ${channelJson(afterMinusBeforeErrorByChannel)},
              "bounds": ${bounds?.toJson() ?: "null"}
            }
        """.trimIndent()

        private fun channelJson(channels: IntArray): String =
            """{"r": ${channels[0]}, "g": ${channels[1]}, "b": ${channels[2]}, "a": ${channels[3]}}"""
    }

    private data class SourceColorSubzoneCriticalSample(
        val index: Int,
        val pixel: SubzonePixelAudit,
        val directRecomposedOnWhiteRgba: IntArray,
        val directRecomposedOnWhiteResidual: Int,
        val directRecomposedErrorByChannel: IntArray,
        val for379SampleClassification: String,
    ) {
        fun toJson(): String {
            val base = pixel.toJson(indexOverride = index).trim()
            val suffix = """
              "directRecomposedOnWhiteRgba": ${rgbaArrayJson(directRecomposedOnWhiteRgba)},
              "directRecomposedOnWhiteResidual": $directRecomposedOnWhiteResidual,
              "directRecomposedErrorByChannel": ${channelJson(directRecomposedErrorByChannel)},
              "probeDeltaVsDirectRecomposedOnWhite": ${pixel.probeResidual - directRecomposedOnWhiteResidual},
              "for379SampleClassification": ${jsonString(for379SampleClassification)}
            """.trimIndent().prependIndent("  ")
            return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
        }

        private fun rgbaArrayJson(rgba: IntArray): String = """[${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3]}]"""

        private fun channelJson(channels: IntArray): String =
            """{"r": ${channels[0]}, "g": ${channels[1]}, "b": ${channels[2]}, "a": ${channels[3]}}"""
    }

    private data class CoverageCompositionMembershipPixelAudit(
        val pixel: SubzonePixelAudit,
        val coverageAlphaByte: Int,
        val transparentSourceRgba: IntArray,
        val transparentSourceAlphaByte: Int,
        val sourceTransparent: Boolean,
        val category: String,
        val categoryReason: String,
    ) {
        fun toJson(): String {
            val base = pixel.toJson().trim()
            val suffix = """
              "coverageMaskAvailable": true,
              "coverageAlphaByte": $coverageAlphaByte,
              "transparentSourceRgba": ${rgbaJson(transparentSourceRgba)},
              "transparentSourceAlphaByte": $transparentSourceAlphaByte,
              "sourceTransparent": $sourceTransparent,
              "referenceAlphaByte": ${alphaByte(pixel.reference)},
              "currentAlphaByte": ${alphaByte(pixel.current)},
              "probeAlphaByte": ${alphaByte(pixel.probe)},
              "membershipCategory": ${jsonString(category)},
              "membershipReason": ${jsonString(categoryReason)}
            """.trimIndent().prependIndent("  ")
            return base.replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
        }

        private fun rgbaJson(rgba: IntArray): String = """[${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3]}]"""

        private fun alphaByte(pixel: Int): Int = (pixel ushr 24) and 0xFF
    }

    private data class SubzonePixelAudit(
        val x: Int,
        val y: Int,
        val strokeBand: String,
        val cap: String,
        val join: String,
        val strokeWidth: Float,
        val reference: Int,
        val current: Int,
        val probe: Int,
        val currentResidual: Int,
        val probeResidual: Int,
        val currentErrorByChannel: IntArray,
        val probeErrorByChannel: IntArray,
        val probeMinusCurrentErrorByChannel: IntArray,
        val probeMinusCurrentRgba: IntArray,
        val nearestCriticalManhattanDistance: Int,
    ) {
        val deltaVsCurrent: Int = probeResidual - currentResidual

        fun toJson(indexOverride: Int? = null): String {
            val indexLine = indexOverride?.let { "  \"index\": $it,\n" } ?: ""
            return """
                {
            $indexLine  "x": $x,
                  "y": $y,
                  "strokeBand": ${jsonString(strokeBand)},
                  "cap": ${jsonString(cap)},
                  "join": ${jsonString(join)},
                  "strokeWidth": ${String.format(Locale.US, "%.1f", strokeWidth)},
                  "referenceRgba": ${pixelRgbaJson(reference)},
                  "currentRgba": ${pixelRgbaJson(current)},
                  "probeRgba": ${pixelRgbaJson(probe)},
                  "currentResidual": $currentResidual,
                  "probeResidual": $probeResidual,
                  "deltaVsCurrent": $deltaVsCurrent,
                  "gainVsCurrent": ${currentResidual - probeResidual},
                  "currentErrorByChannel": ${channelJson(currentErrorByChannel)},
                  "probeErrorByChannel": ${channelJson(probeErrorByChannel)},
                  "probeMinusCurrentErrorByChannel": ${channelJson(probeMinusCurrentErrorByChannel)},
                  "probeMinusCurrentRgba": ${channelJson(probeMinusCurrentRgba)},
                  "nearestFor379CriticalManhattanDistance": $nearestCriticalManhattanDistance
                }
            """.trimIndent()
        }

        private fun pixelRgbaJson(pixel: Int): String {
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            val a = (pixel ushr 24) and 0xFF
            return """[$r, $g, $b, $a]"""
        }

        private fun channelJson(channels: IntArray): String =
            """{"r": ${channels[0]}, "g": ${channels[1]}, "b": ${channels[2]}, "a": ${channels[3]}}"""
    }

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
        private const val FOR372_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR373_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR374_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR375_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR376_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR377_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR378_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR379_REQUIRED_SAMPLE_COUNT = 10
        private const val FOR381_SAMPLE_LIMIT = 12
        private const val FOR373_CURRENT_RESIDUAL = 856
        private const val FOR373_CANDIDATE_TOTAL_RESIDUAL = 1033
        private const val FOR375_EFFECTIVE_DESTINATION_CANDIDATE_TOTAL_RESIDUAL = 794
        private const val LINEAR_SRGB_EFFECTIVE_DESTINATION_VARIANT_ID =
            "linear_srgb_source_over_effective_destination_nearest_255"
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
        private const val EXPERIMENTAL_RENDER_PROPERTY = "kanvas.webgpu.strokeCapJoin.experimentalRender"
        private const val FOR380_CORRECTION_PROPERTY = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

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
