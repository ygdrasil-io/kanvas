package org.skia.gpu.webgpu

import java.io.File
import java.security.MessageDigest
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
            val boundedRuntimeCorrectionResult = withExperimentalStrokeCapJoinRender {
                withM60F16BoundedRuntimeCorrectionProbe(true) {
                    WebGpuSink.drawWithM60F16FragmentLaneDiagnosticSnapshot(
                        ctx,
                        gm,
                        targetColorSpaceBlend = true,
                    )
                }
            }
            val boundedRuntimeCorrectionGpu = boundedRuntimeCorrectionResult.bitmap
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)
            val experimentalGpuCmp = TestUtils.compareBitmapsDetailed(experimentalGpu, reference, tolerance = 0)
            val correctedExperimentalGpuCmp =
                TestUtils.compareBitmapsDetailed(correctedExperimentalGpu, reference, tolerance = 0)
            val boundedRuntimeCorrectionGpuCmp =
                TestUtils.compareBitmapsDetailed(boundedRuntimeCorrectionGpu, reference, tolerance = 0)
            val experimentalGpuToleranceProfile = toleranceProfile(experimentalGpu, reference)
            val regionStats = strokeRegionStats(experimentalGpu, reference)
            val residualStats = strokeResidualStats(experimentalGpu, reference)
            val correctedResidualStats = strokeResidualStats(correctedExperimentalGpu, reference)
            val boundedRuntimeCorrectionResidualStats =
                strokeResidualStats(boundedRuntimeCorrectionGpu, reference)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[StrokeCapJoinSceneCapture] adapter=$adapter cpu=${"%.2f".format(cpuCmp.similarity)}%, " +
                    "experimentalGpu=${"%.2f".format(experimentalGpuCmp.similarity)}%, " +
                    "dominantGap=${regionStats.minBy { it.similarity }.id}, gpuRefusal=${gpuError.message}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                val fragmentLaneRuntimeSnapshot = withExperimentalStrokeCapJoinRender {
                    WebGpuSink.drawWithM60F16FragmentLaneDiagnosticSnapshot(
                        ctx,
                        gm,
                        targetColorSpaceBlend = true,
                    ).snapshot
                }
                val boundedCorrectionApplicationPointResult = withExperimentalStrokeCapJoinRender {
                    withM60F16BoundedRuntimeCorrectionProbe(true) {
                        withM60F16BoundedCorrectionApplicationPointDiagnostic(true) {
                            WebGpuSink.drawWithM60F16FragmentLaneDiagnosticSnapshot(
                                ctx,
                                gm,
                                targetColorSpaceBlend = true,
                            )
                        }
                    }
                }
                val coverageStencilContributionMapResult = withExperimentalStrokeCapJoinRender {
                    withM60F16BoundedRuntimeCorrectionProbe(true) {
                        withM60F16CoverageStencilContributionMapDiagnostic(true) {
                            WebGpuSink.drawWithM60F16FragmentLaneDiagnosticSnapshot(
                                ctx,
                                gm,
                                targetColorSpaceBlend = true,
                            )
                        }
                    }
                }
                val contributionIsolationResult = withExperimentalStrokeCapJoinRender {
                    withM60F16BandMetadataTransport(true) {
                        withM60F16BoundedRuntimeCorrectionProbe(true) {
                            withM60F16DirectPassWriteHook(true) {
                                withM60F16PredrawDstReadback(true) {
                                            withM60F16ShaderReturnDiagnostic(true) {
                                                withM60F16AaStencilCoverContributionIsolationDiagnostic(true) {
                                                    withM60F16IsolatedColorTargetRuntime(true) {
                                                        withM60F16StorageColorTargetComparison(true) {
                                                            WebGpuSink.drawWithM60F16FragmentLaneDiagnosticSnapshot(
                                                                ctx,
                                                                gm,
                                                                targetColorSpaceBlend = true,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                        }
                    }
                }
                writeEvidence(
                    cpuBitmap = cpuBitmap,
                    reference = reference,
                    experimentalGpu = experimentalGpu,
                    correctedExperimentalGpu = correctedExperimentalGpu,
                    boundedRuntimeCorrectionGpu = boundedRuntimeCorrectionGpu,
                    boundedCorrectionApplicationPointGpu = boundedCorrectionApplicationPointResult.bitmap,
                    coverageStencilContributionMapGpu = coverageStencilContributionMapResult.bitmap,
                    cpuCmp = cpuCmp,
                    experimentalGpuCmp = experimentalGpuCmp,
                    correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
                    boundedRuntimeCorrectionGpuCmp = boundedRuntimeCorrectionGpuCmp,
                    experimentalGpuToleranceProfile = experimentalGpuToleranceProfile,
                    regionStats = regionStats,
                    residualStats = residualStats,
                    correctedResidualStats = correctedResidualStats,
                    boundedRuntimeCorrectionResidualStats = boundedRuntimeCorrectionResidualStats,
                    fragmentLaneRuntimeSnapshot = fragmentLaneRuntimeSnapshot,
                    boundedRuntimeCorrectionSnapshot = boundedRuntimeCorrectionResult.snapshot,
                    boundedCorrectionApplicationPointSnapshot =
                        boundedCorrectionApplicationPointResult.applicationPointSnapshot,
                    coverageStencilContributionMapSnapshot =
                        coverageStencilContributionMapResult.coverageStencilContributionMapSnapshot,
                    aaStencilCoverPostPassRuntimeHookSnapshot =
                        coverageStencilContributionMapResult.aaStencilCoverPostPassRuntimeHookSnapshot,
                    aaStencilCoverPostPassReadbackSnapshot =
                        coverageStencilContributionMapResult.aaStencilCoverPostPassReadbackSnapshot,
                    aaStencilCoverPredrawDstReadbackSnapshot =
                        contributionIsolationResult.aaStencilCoverPredrawDstReadbackSnapshot,
                    aaStencilCoverContributionIsolationSnapshot =
                        contributionIsolationResult.aaStencilCoverContributionIsolationSnapshot,
                    aaStencilCoverShaderReturnDiagnosticSnapshot =
                        contributionIsolationResult.aaStencilCoverShaderReturnDiagnosticSnapshot,
                    aaStencilCoverIsolatedColorTargetSnapshot =
                        contributionIsolationResult.aaStencilCoverIsolatedColorTargetSnapshot,
                    aaStencilCoverStorageColorTargetComparisonSnapshot =
                        contributionIsolationResult.aaStencilCoverStorageColorTargetComparisonSnapshot,
                    aaStencilCoverShaderReturnStorageZeroCauseSnapshot =
                        contributionIsolationResult.aaStencilCoverShaderReturnStorageZeroCauseSnapshot,
                    aaStencilCoverFinalWgslDiagnosticSnapshot =
                        contributionIsolationResult.aaStencilCoverFinalWgslDiagnosticSnapshot,
                    aaStencilCoverContributionIsolationPostPassSnapshot =
                        contributionIsolationResult.aaStencilCoverPostPassReadbackSnapshot,
                    aaStencilCoverContributionIsolationBoundedRuntimeCorrectionProbe = true,
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
        boundedRuntimeCorrectionGpu: SkBitmap,
        boundedCorrectionApplicationPointGpu: SkBitmap,
        coverageStencilContributionMapGpu: SkBitmap,
        cpuCmp: BitmapComparison,
        experimentalGpuCmp: BitmapComparison,
        correctedExperimentalGpuCmp: BitmapComparison,
        boundedRuntimeCorrectionGpuCmp: BitmapComparison,
        experimentalGpuToleranceProfile: List<ToleranceStat>,
        regionStats: List<StrokeRegionStats>,
        residualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        boundedRuntimeCorrectionResidualStats: StrokeResidualStats,
        fragmentLaneRuntimeSnapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        boundedRuntimeCorrectionSnapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        boundedCorrectionApplicationPointSnapshot:
            SkWebGpuDevice.M60F16BoundedCorrectionApplicationPointSnapshot,
        coverageStencilContributionMapSnapshot:
            SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        aaStencilCoverPostPassRuntimeHookSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSnapshot,
        aaStencilCoverPostPassReadbackSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        aaStencilCoverPredrawDstReadbackSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        aaStencilCoverContributionIsolationSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        aaStencilCoverShaderReturnDiagnosticSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSnapshot,
        aaStencilCoverIsolatedColorTargetSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetSnapshot,
        aaStencilCoverStorageColorTargetComparisonSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverStorageColorTargetComparisonSnapshot,
        aaStencilCoverShaderReturnStorageZeroCauseSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        aaStencilCoverFinalWgslDiagnosticSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        aaStencilCoverContributionIsolationPostPassSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        aaStencilCoverContributionIsolationBoundedRuntimeCorrectionProbe: Boolean,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        writePng(File(dir, "gpu-experimental.png"), experimentalGpu)
        writePng(File(dir, "gpu-experimental-diff.png"), CrossBackendHarness.pixelDiff(reference, experimentalGpu))
        writePng(File(dir, "gpu-bounded-runtime-correction-for398.png"), boundedRuntimeCorrectionGpu)
        writePng(
            File(dir, "gpu-bounded-runtime-correction-for398-diff.png"),
            CrossBackendHarness.pixelDiff(reference, boundedRuntimeCorrectionGpu),
        )
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
        writeM60F16ResidualFringeDiscriminatorAudit(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16SourceCoverageFullSceneCandidate(
            reference = reference,
            currentGpu = experimentalGpu,
            probeGpu = correctedExperimentalGpu,
            uncorrectedResidualStats = residualStats,
            correctedResidualStats = correctedResidualStats,
            uncorrectedExperimentalGpuCmp = experimentalGpuCmp,
            correctedExperimentalGpuCmp = correctedExperimentalGpuCmp,
            adapter = adapter,
        )
        writeM60F16FragmentLaneRuntimeSnapshotExport(fragmentLaneRuntimeSnapshot, adapter)
        writeM60F16BoundedRuntimeCorrectionProbe(
            reference = reference,
            currentGpu = experimentalGpu,
            correctedGpu = boundedRuntimeCorrectionGpu,
            currentGpuCmp = experimentalGpuCmp,
            correctedGpuCmp = boundedRuntimeCorrectionGpuCmp,
            currentResidualStats = residualStats,
            correctedResidualStats = boundedRuntimeCorrectionResidualStats,
            snapshot = boundedRuntimeCorrectionSnapshot,
            adapter = adapter,
        )
        writeM60F16BoundedCorrectionApplicationPoint(
            reference = reference,
            currentGpu = experimentalGpu,
            correctedGpu = boundedRuntimeCorrectionGpu,
            applicationPointGpu = boundedCorrectionApplicationPointGpu,
            currentResidualStats = residualStats,
            correctedResidualStats = boundedRuntimeCorrectionResidualStats,
            snapshot = boundedCorrectionApplicationPointSnapshot,
            adapter = adapter,
        )
        writeM60F16CoverageStencilContributionMap(
            reference = reference,
            currentGpu = experimentalGpu,
            correctedGpu = boundedRuntimeCorrectionGpu,
            coverageStencilContributionMapGpu = coverageStencilContributionMapGpu,
            currentResidualStats = residualStats,
            correctedResidualStats = boundedRuntimeCorrectionResidualStats,
            snapshot = coverageStencilContributionMapSnapshot,
            adapter = adapter,
        )
        if (System.getProperty(FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY, "false").toBoolean()) {
            writeM60F16FinalResidualOriginMap(
                reference = reference,
                currentGpu = experimentalGpu,
                currentResidualStats = residualStats,
                snapshot = coverageStencilContributionMapSnapshot,
                adapter = adapter,
            )
        }
        if (System.getProperty(FOR402_PASS_WRITE_PROBE_PROPERTY, "false").toBoolean()) {
            writeM60F16PassWriteProbe(
                reference = reference,
                currentGpu = experimentalGpu,
                currentResidualStats = residualStats,
                snapshot = coverageStencilContributionMapSnapshot,
                adapter = adapter,
            )
        }
        if (System.getProperty(FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY, "false").toBoolean()) {
            writeM60F16AaStencilCoverRuntimeHook(
                reference = reference,
                currentGpu = experimentalGpu,
                currentResidualStats = residualStats,
                snapshot = aaStencilCoverPostPassRuntimeHookSnapshot,
                adapter = adapter,
            )
            writeM60F16AaStencilCoverPostPassReadback(
                reference = reference,
                currentGpu = experimentalGpu,
                currentResidualStats = residualStats,
                runtimeHookSnapshot = aaStencilCoverPostPassRuntimeHookSnapshot,
                readbackSnapshot = aaStencilCoverPostPassReadbackSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverContributionIsolationSnapshot.enabled) {
            writeM60F16AaStencilCoverContributionIsolation(
                snapshot = aaStencilCoverContributionIsolationSnapshot,
                postPassSnapshot = aaStencilCoverContributionIsolationPostPassSnapshot,
                boundedRuntimeCorrectionProbeEnabledForEvidenceRun =
                    aaStencilCoverContributionIsolationBoundedRuntimeCorrectionProbe,
                adapter = adapter,
            )
        }
        if (System.getProperty(FOR409_SOURCE_OVER_REPLAY_PROPERTY, "false").toBoolean()) {
            writeM60F16AaStencilCoverSourceOverReplay(
                snapshot = aaStencilCoverContributionIsolationSnapshot,
                postPassSnapshot = aaStencilCoverContributionIsolationPostPassSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverPredrawDstReadbackSnapshot.enabled) {
            writeM60F16AaStencilCoverPredrawDstReadback(
                predrawSnapshot = aaStencilCoverPredrawDstReadbackSnapshot,
                contributionSnapshot = aaStencilCoverContributionIsolationSnapshot,
                postPassSnapshot = aaStencilCoverContributionIsolationPostPassSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverShaderReturnDiagnosticSnapshot.enabled) {
            writeM60F16AaStencilCoverShaderReturnDiagnostic(
                shaderReturnSnapshot = aaStencilCoverShaderReturnDiagnosticSnapshot,
                contributionSnapshot = aaStencilCoverContributionIsolationSnapshot,
                predrawSnapshot = aaStencilCoverPredrawDstReadbackSnapshot,
                postPassSnapshot = aaStencilCoverContributionIsolationPostPassSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverIsolatedColorTargetSnapshot.enabled) {
            writeM60F16AaStencilCoverIsolatedColorTargetRuntime(
                snapshot = aaStencilCoverIsolatedColorTargetSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverStorageColorTargetComparisonSnapshot.enabled &&
            !aaStencilCoverShaderReturnStorageZeroCauseSnapshot.enabled
        ) {
            writeM60F16AaStencilCoverStorageColorTargetComparison(
                snapshot = aaStencilCoverStorageColorTargetComparisonSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverShaderReturnStorageZeroCauseSnapshot.enabled) {
            writeM60F16AaStencilCoverShaderReturnStorageZeroCause(
                snapshot = aaStencilCoverShaderReturnStorageZeroCauseSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverFinalWgslDiagnosticSnapshot.enabled) {
            writeM60F16AaStencilCoverFinalWgslDiagnostic(
                snapshot = aaStencilCoverFinalWgslDiagnosticSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverFinalWgslDiagnosticSnapshot.enabled &&
            aaStencilCoverShaderReturnStorageZeroCauseSnapshot.enabled
        ) {
            writeM60F16AaStencilCoverVerifiedReturnPathDiagnostic(
                finalWgslSnapshot = aaStencilCoverFinalWgslDiagnosticSnapshot,
                storageSnapshot = aaStencilCoverShaderReturnStorageZeroCauseSnapshot,
                adapter = adapter,
            )
        }
        if (aaStencilCoverFinalWgslDiagnosticSnapshot.enabled &&
            aaStencilCoverShaderReturnStorageZeroCauseSnapshot.enabled &&
            aaStencilCoverPredrawDstReadbackSnapshot.enabled &&
            aaStencilCoverContributionIsolationPostPassSnapshot.enabled
        ) {
            writeM60F16AaStencilCoverVerifiedSourceComparison(
                finalWgslSnapshot = aaStencilCoverFinalWgslDiagnosticSnapshot,
                storageSnapshot = aaStencilCoverShaderReturnStorageZeroCauseSnapshot,
                predrawSnapshot = aaStencilCoverPredrawDstReadbackSnapshot,
                postPassSnapshot = aaStencilCoverContributionIsolationPostPassSnapshot,
                adapter = adapter,
            )
            writeM60F16AaStencilCoverReferenceSourceCoverage(
                reference = reference,
                currentGpu = experimentalGpu,
                finalWgslSnapshot = aaStencilCoverFinalWgslDiagnosticSnapshot,
                storageSnapshot = aaStencilCoverShaderReturnStorageZeroCauseSnapshot,
                predrawSnapshot = aaStencilCoverPredrawDstReadbackSnapshot,
                postPassSnapshot = aaStencilCoverContributionIsolationPostPassSnapshot,
                adapter = adapter,
            )
        }
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

    private fun <T> withM60F16BoundedRuntimeCorrectionProbe(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY)
        System.setProperty(FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY)
            } else {
                System.setProperty(FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16BandMetadataTransport(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY)
        System.setProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY)
            } else {
                System.setProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16BoundedCorrectionApplicationPointDiagnostic(
        enabled: Boolean,
        block: () -> T,
    ): T {
        val previous = System.getProperty(FOR399_APPLICATION_POINT_DIAGNOSTIC_PROPERTY)
        System.setProperty(FOR399_APPLICATION_POINT_DIAGNOSTIC_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR399_APPLICATION_POINT_DIAGNOSTIC_PROPERTY)
            } else {
                System.setProperty(FOR399_APPLICATION_POINT_DIAGNOSTIC_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16CoverageStencilContributionMapDiagnostic(
        enabled: Boolean,
        block: () -> T,
    ): T {
        val previous = System.getProperty(FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY)
        System.setProperty(FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY)
            } else {
                System.setProperty(FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16DirectPassWriteHook(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY)
        System.setProperty(FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY)
            } else {
                System.setProperty(FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16AaStencilCoverContributionIsolationDiagnostic(
        enabled: Boolean,
        block: () -> T,
    ): T {
        val previous = System.getProperty(FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY)
        System.setProperty(FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY)
            } else {
                System.setProperty(FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16PredrawDstReadback(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR410_PREDRAW_DST_READBACK_PROPERTY)
        System.setProperty(FOR410_PREDRAW_DST_READBACK_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR410_PREDRAW_DST_READBACK_PROPERTY)
            } else {
                System.setProperty(FOR410_PREDRAW_DST_READBACK_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16ShaderReturnDiagnostic(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY)
        System.setProperty(FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY)
            } else {
                System.setProperty(FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16IsolatedColorTargetRuntime(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR417_ISOLATED_COLOR_TARGET_PROPERTY)
        System.setProperty(FOR417_ISOLATED_COLOR_TARGET_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR417_ISOLATED_COLOR_TARGET_PROPERTY)
            } else {
                System.setProperty(FOR417_ISOLATED_COLOR_TARGET_PROPERTY, previous)
            }
        }
    }

    private fun <T> withM60F16StorageColorTargetComparison(enabled: Boolean, block: () -> T): T {
        val previous = System.getProperty(FOR418_STORAGE_COLOR_TARGET_COMPARISON_PROPERTY)
        System.setProperty(FOR418_STORAGE_COLOR_TARGET_COMPARISON_PROPERTY, enabled.toString())
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(FOR418_STORAGE_COLOR_TARGET_COMPARISON_PROPERTY)
            } else {
                System.setProperty(FOR418_STORAGE_COLOR_TARGET_COMPARISON_PROPERTY, previous)
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

    private fun Pair<Int, Int>.pixelJson(): String = """{"x": $first, "y": $second}"""

    private fun FloatArray.floatJson(): String =
        joinToString(prefix = "[", postfix = "]") { value ->
            String.format(Locale.US, "%.9f", value)
        }

    private fun FloatArray.closeTo(other: FloatArray, epsilon: Float = 0.000001f): Boolean =
        size == other.size && indices.all { index -> kotlin.math.abs(this[index] - other[index]) <= epsilon }

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

    private fun writeM60F16ResidualFringeDiscriminatorAudit(
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
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-residual-fringe-discriminator-audit-for387",
        ).apply { mkdirs() }
        File(dir, "m60-f16-residual-fringe-discriminator-audit-for387.json").writeText(
            m60F16ResidualFringeDiscriminatorAuditJson(
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

    private fun writeM60F16SourceCoverageFullSceneCandidate(
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
            "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-coverage-full-scene-candidate-for389",
        ).apply { mkdirs() }
        File(dir, "m60-f16-source-coverage-full-scene-candidate-for389.json").writeText(
            m60F16SourceCoverageFullSceneCandidateJson(
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

    private fun writeM60F16FragmentLaneRuntimeSnapshotExport(
        snapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-fragment-lane-runtime-snapshot-export-for397",
        ).apply { mkdirs() }
        File(dir, "m60-f16-fragment-lane-runtime-snapshot-export-for397.json").writeText(
            m60F16FragmentLaneRuntimeSnapshotExportJson(snapshot, adapter),
        )
    }

    private fun writeM60F16BoundedRuntimeCorrectionProbe(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        correctedGpu: SkBitmap,
        currentGpuCmp: BitmapComparison,
        correctedGpuCmp: BitmapComparison,
        currentResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-bounded-runtime-correction-probe-for398",
        ).apply { mkdirs() }
        File(dir, "m60-f16-bounded-runtime-correction-probe-for398.json").writeText(
            m60F16BoundedRuntimeCorrectionProbeJson(
                reference = reference,
                currentGpu = currentGpu,
                correctedGpu = correctedGpu,
                currentGpuCmp = currentGpuCmp,
                correctedGpuCmp = correctedGpuCmp,
                currentResidualStats = currentResidualStats,
                correctedResidualStats = correctedResidualStats,
                snapshot = snapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16BoundedCorrectionApplicationPoint(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        correctedGpu: SkBitmap,
        applicationPointGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16BoundedCorrectionApplicationPointSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-bounded-correction-shader-application-point-for399",
        ).apply { mkdirs() }
        File(dir, "m60-f16-bounded-correction-shader-application-point-for399.json").writeText(
            m60F16BoundedCorrectionApplicationPointJson(
                reference = reference,
                currentGpu = currentGpu,
                correctedGpu = correctedGpu,
                applicationPointGpu = applicationPointGpu,
                currentResidualStats = currentResidualStats,
                correctedResidualStats = correctedResidualStats,
                snapshot = snapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16CoverageStencilContributionMap(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        correctedGpu: SkBitmap,
        coverageStencilContributionMapGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-coverage-stencil-contribution-map-for400",
        ).apply { mkdirs() }
        File(dir, "m60-f16-coverage-stencil-contribution-map-for400.json").writeText(
            m60F16CoverageStencilContributionMapJson(
                reference = reference,
                currentGpu = currentGpu,
                correctedGpu = correctedGpu,
                coverageStencilContributionMapGpu = coverageStencilContributionMapGpu,
                currentResidualStats = currentResidualStats,
                correctedResidualStats = correctedResidualStats,
                snapshot = snapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16FinalResidualOriginMap(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-final-residual-origin-map-for401",
        ).apply { mkdirs() }
        File(dir, "m60-f16-final-residual-origin-map-for401.json").writeText(
            m60F16FinalResidualOriginMapJson(
                reference = reference,
                currentGpu = currentGpu,
                currentResidualStats = currentResidualStats,
                snapshot = snapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16PassWriteProbe(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-pass-write-probe-for402",
        ).apply { mkdirs() }
        File(dir, "m60-f16-pass-write-probe-for402.json").writeText(
            m60F16PassWriteProbeJson(
                reference = reference,
                currentGpu = currentGpu,
                currentResidualStats = currentResidualStats,
                snapshot = snapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverRuntimeHook(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-runtime-hook-for404",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-runtime-hook-for404.json").writeText(
            m60F16AaStencilCoverRuntimeHookJson(
                reference = reference,
                currentGpu = currentGpu,
                currentResidualStats = currentResidualStats,
                snapshot = snapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverPostPassReadback(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        runtimeHookSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSnapshot,
        readbackSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-post-pass-readback-for405",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-post-pass-readback-for405.json").writeText(
            m60F16AaStencilCoverPostPassReadbackJson(
                reference = reference,
                currentGpu = currentGpu,
                currentResidualStats = currentResidualStats,
                runtimeHookSnapshot = runtimeHookSnapshot,
                readbackSnapshot = readbackSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverContributionIsolation(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        boundedRuntimeCorrectionProbeEnabledForEvidenceRun: Boolean,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-per-subdraw-hook-for408",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json").writeText(
            m60F16AaStencilCoverContributionIsolationJson(
                snapshot = snapshot,
                postPassSnapshot = postPassSnapshot,
                boundedRuntimeCorrectionProbeEnabledForEvidenceRun =
                    boundedRuntimeCorrectionProbeEnabledForEvidenceRun,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverSourceOverReplay(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-source-over-replay-for409",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-source-over-replay-for409.json").writeText(
            m60F16AaStencilCoverSourceOverReplayJson(
                snapshot = snapshot,
                postPassSnapshot = postPassSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverPredrawDstReadback(
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        contributionSnapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-predraw-dst-readback-for410",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json").writeText(
            m60F16AaStencilCoverPredrawDstReadbackJson(
                predrawSnapshot = predrawSnapshot,
                contributionSnapshot = contributionSnapshot,
                postPassSnapshot = postPassSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverShaderReturnDiagnostic(
        shaderReturnSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSnapshot,
        contributionSnapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json").writeText(
            m60F16AaStencilCoverShaderReturnDiagnosticJson(
                shaderReturnSnapshot = shaderReturnSnapshot,
                contributionSnapshot = contributionSnapshot,
                predrawSnapshot = predrawSnapshot,
                postPassSnapshot = postPassSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun writeM60F16AaStencilCoverIsolatedColorTargetRuntime(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417",
        ).apply { mkdirs() }
        File(dir, "raw-runtime-snapshot-for417.json").writeText(
            m60F16AaStencilCoverIsolatedColorTargetRuntimeRawJson(snapshot, adapter),
        )
    }

    private fun m60F16AaStencilCoverIsolatedColorTargetRuntimeRawJson(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetSnapshot,
        adapter: String,
    ): String {
        val events = snapshot.events.joinToString(",\n") { event ->
            val samples = event.samples.joinToString(",\n") { sample ->
                """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "readbackAttempted": ${sample.readbackAttempted},
                  "readbackAvailable": ${sample.readbackAvailable},
                  "scratchOutputRgbaFloat": ${sample.scratchOutputRgbaFloat.floatArrayOrNullJson()},
                  "scratchOutputRgba8": ${sample.scratchOutputRgba8.intArrayOrNullJson()},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
                """.trimIndent()
            }
            """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "scratchTargetEncoded": ${event.scratchTargetEncoded},
              "copyAttempted": ${event.copyAttempted},
              "copySucceeded": ${event.copySucceeded},
              "copyFailureReason": ${event.copyFailureReason?.jsonString() ?: "null"},
              "samples": [
                $samples
              ]
            }
            """.trimIndent()
        }
        return """
        {
          "schemaVersion": 1,
          "linear": "FOR-417",
          "sceneId": "m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417",
          "adapter": ${adapter.jsonString()},
          "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
          "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
          "propertyName": ${snapshot.propertyName.jsonString()},
          "enabled": ${snapshot.enabled},
          "requestedBoundary": ${snapshot.requestedBoundary.jsonString()},
          "observedBoundary": ${snapshot.observedBoundary.jsonString()},
          "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
          "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
          "scratchFormat": ${snapshot.scratchFormat.jsonString()},
          "sampleLimit": ${snapshot.sampleLimit},
          "events": [
            $events
          ]
        }
        """.trimIndent()
    }

    private fun writeM60F16AaStencilCoverStorageColorTargetComparison(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverStorageColorTargetComparisonSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-storage-vs-color-target-for418",
        ).apply { mkdirs() }
        File(dir, "raw-runtime-snapshot-for418.json").writeText(
            m60F16AaStencilCoverStorageColorTargetComparisonRawJson(snapshot, adapter),
        )
    }

    private fun m60F16AaStencilCoverStorageColorTargetComparisonRawJson(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverStorageColorTargetComparisonSnapshot,
        adapter: String,
    ): String {
        fun storageEventJson(event: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent): String {
            val samples = event.samples.joinToString(",\n") { sample ->
                """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "subdrawOrdinal": ${sample.subdrawOrdinal},
                  "subdrawRole": ${sample.subdrawRole.jsonString()},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "shaderObserved": ${sample.shaderObserved},
                  "candidateBranchReached": ${sample.candidateBranchReached},
                  "colorAfterColorFilter": ${sample.colorAfterColorFilter.floatArrayOrNullJson()},
                  "colorAfterTargetColorspaceIfNeeded": ${sample.colorAfterTargetColorspaceIfNeeded.floatArrayOrNullJson()},
                  "correctedColorBeforeCoverage": ${sample.correctedColorBeforeCoverage.floatArrayOrNullJson()},
                  "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.toString() ?: "null"},
                  "sourceAlphaAfterCoverage": ${sample.sourceAlphaAfterCoverage?.toString() ?: "null"},
                  "sourceColorBeforeQuantization": ${sample.sourceColorBeforeQuantization.floatArrayOrNullJson()},
                  "sourceColorSentToBlend": ${sample.sourceColorSentToBlend.floatArrayOrNullJson()},
                  "sourceFieldUsedByFOR408Replay": ${sample.sourceFieldUsedByFOR408Replay.floatArrayOrNullJson()},
                  "quantizedAlphaSentToBlend": ${sample.quantizedAlphaSentToBlend?.toString() ?: "null"},
                  "captureSynthetic": ${sample.captureSynthetic},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
                """.trimIndent()
            }
            return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "samples": [
                $samples
              ]
            }
            """.trimIndent()
        }

        fun colorEventJson(event: SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetEvent): String {
            val samples = event.samples.joinToString(",\n") { sample ->
                """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "readbackAttempted": ${sample.readbackAttempted},
                  "readbackAvailable": ${sample.readbackAvailable},
                  "scratchOutputRgbaFloat": ${sample.scratchOutputRgbaFloat.floatArrayOrNullJson()},
                  "scratchOutputRgba8": ${sample.scratchOutputRgba8.intArrayOrNullJson()},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
                """.trimIndent()
            }
            return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "scratchTargetEncoded": ${event.scratchTargetEncoded},
              "copyAttempted": ${event.copyAttempted},
              "copySucceeded": ${event.copySucceeded},
              "copyFailureReason": ${event.copyFailureReason?.jsonString() ?: "null"},
              "samples": [
                $samples
              ]
            }
            """.trimIndent()
        }

        val storageEvents = snapshot.storageEvents.joinToString(",\n") { storageEventJson(it) }
        val colorTargetEvents = snapshot.colorTargetEvents.joinToString(",\n") { colorEventJson(it) }
        return """
        {
          "schemaVersion": 1,
          "linear": "FOR-418",
          "sceneId": "m60-f16-aa-stencil-cover-storage-vs-color-target-for418",
          "adapter": ${adapter.jsonString()},
          "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
          "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
          "propertyName": ${snapshot.propertyName.jsonString()},
          "enabled": ${snapshot.enabled},
          "requestedBoundary": ${snapshot.requestedBoundary.jsonString()},
          "observedBoundary": ${snapshot.observedBoundary.jsonString()},
          "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
          "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
          "scratchFormat": ${snapshot.scratchFormat.jsonString()},
          "sampleLimit": ${snapshot.sampleLimit},
          "storageEvents": [
            $storageEvents
          ],
          "colorTargetEvents": [
            $colorTargetEvents
          ]
        }
        """.trimIndent()
    }

    private fun writeM60F16AaStencilCoverShaderReturnStorageZeroCause(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419",
        ).apply { mkdirs() }
        File(dir, "raw-runtime-snapshot-for419.json").writeText(
            m60F16AaStencilCoverShaderReturnStorageZeroCauseRawJson(snapshot, adapter),
        )
    }

    private fun m60F16AaStencilCoverShaderReturnStorageZeroCauseRawJson(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        adapter: String,
    ): String {
        fun storageEventJson(event: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent): String {
            val samples = event.samples.joinToString(",\n") { sample ->
                """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "subdrawOrdinal": ${sample.subdrawOrdinal},
                  "subdrawRole": ${sample.subdrawRole.jsonString()},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "shaderObserved": ${sample.shaderObserved},
                  "candidateBranchReached": ${sample.candidateBranchReached},
                  "colorAfterColorFilter": ${sample.colorAfterColorFilter.floatArrayOrNullJson()},
                  "colorAfterTargetColorspaceIfNeeded": ${sample.colorAfterTargetColorspaceIfNeeded.floatArrayOrNullJson()},
                  "correctedColorBeforeCoverage": ${sample.correctedColorBeforeCoverage.floatArrayOrNullJson()},
                  "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.toString() ?: "null"},
                  "sourceAlphaAfterCoverage": ${sample.sourceAlphaAfterCoverage?.toString() ?: "null"},
                  "sourceColorBeforeQuantization": ${sample.sourceColorBeforeQuantization.floatArrayOrNullJson()},
                  "sourceColorSentToBlend": ${sample.sourceColorSentToBlend.floatArrayOrNullJson()},
                  "sourceFieldUsedByFOR408Replay": ${sample.sourceFieldUsedByFOR408Replay.floatArrayOrNullJson()},
                  "quantizedAlphaSentToBlend": ${sample.quantizedAlphaSentToBlend?.toString() ?: "null"},
                  "captureSynthetic": ${sample.captureSynthetic},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
                """.trimIndent()
            }
            return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "samples": [
                $samples
              ]
            }
            """.trimIndent()
        }

        fun colorEventJson(event: SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetEvent): String {
            val samples = event.samples.joinToString(",\n") { sample ->
                """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "readbackAttempted": ${sample.readbackAttempted},
                  "readbackAvailable": ${sample.readbackAvailable},
                  "scratchOutputRgbaFloat": ${sample.scratchOutputRgbaFloat.floatArrayOrNullJson()},
                  "scratchOutputRgba8": ${sample.scratchOutputRgba8.intArrayOrNullJson()},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
                """.trimIndent()
            }
            return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "scratchTargetEncoded": ${event.scratchTargetEncoded},
              "copyAttempted": ${event.copyAttempted},
              "copySucceeded": ${event.copySucceeded},
              "copyFailureReason": ${event.copyFailureReason?.jsonString() ?: "null"},
              "samples": [
                $samples
              ]
            }
            """.trimIndent()
        }

        val storageEvents = snapshot.storageEvents.joinToString(",\n") { storageEventJson(it) }
        val colorTargetEvents = snapshot.colorTargetEvents.joinToString(",\n") { colorEventJson(it) }
        return """
        {
          "schemaVersion": 1,
          "linear": "FOR-419",
          "sceneId": "m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419",
          "adapter": ${adapter.jsonString()},
          "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
          "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
          "propertyName": ${snapshot.propertyName.jsonString()},
          "enabled": ${snapshot.enabled},
          "requestedBoundary": ${snapshot.requestedBoundary.jsonString()},
          "observedBoundary": ${snapshot.observedBoundary.jsonString()},
          "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
          "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
          "scratchFormat": ${snapshot.scratchFormat.jsonString()},
          "sampleLimit": ${snapshot.sampleLimit},
          "storageEvents": [
            $storageEvents
          ],
          "colorTargetEvents": [
            $colorTargetEvents
          ]
        }
        """.trimIndent()
    }

    private fun writeM60F16AaStencilCoverFinalWgslDiagnostic(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        adapter: String,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/artifacts/" +
                "m60-f16-aa-stencil-cover-final-wgsl-diagnostic-for420",
        ).apply { mkdirs() }
        File(dir, "m60-f16-aa-stencil-cover-final-wgsl-diagnostic-for420.json").writeText(
            m60F16AaStencilCoverFinalWgslDiagnosticJson(snapshot, adapter),
        )
    }

    private fun m60F16AaStencilCoverFinalWgslDiagnosticJson(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        adapter: String,
    ): String {
        val summaries = snapshot.variants.map { m60F16FinalWgslVariantSummary(it) }
        val normal = summaries.firstOrNull { it.logicalName == "normal-bounded-runtime-correction" }
        val diagnosticVariants = summaries.filter { it.logicalName != "normal-bounded-runtime-correction" }
        val missingSourceCount = summaries.count { it.sourceHash.isEmpty() }
        val fsEntryPointsPresent = summaries.all { it.functions["fs_inside"]?.present == true } &&
            summaries.all { it.functions["fs_outside"]?.present == true }
        val shaderReturnSharedByFor418 = summaries
            .firstOrNull { it.logicalName == "for418-storage-vs-color-target" }
            ?.sourceSharedWith == "for412-shader-return-storage"
        val applicationPointReturnedByDiagnostics = diagnosticVariants.all { summary ->
            val inside = summary.functions["fs_inside"]
            val outside = summary.functions["fs_outside"]
            inside?.returnsApplicationPointOutput == true && outside?.returnsApplicationPointOutput == true
        }
        val classification = when {
            missingSourceCount > 0 -> "wgsl-final-source-export-unavailable"
            !fsEntryPointsPresent -> "wgsl-final-source-entrypoint-missing"
            normal == null -> "wgsl-final-source-export-unavailable"
            applicationPointReturnedByDiagnostics && shaderReturnSharedByFor418 ->
                "diagnostic-final-wgsl-hooks-replace-render-return-path"
            else -> "diagnostic-final-wgsl-hooks-not-on-rendered-return-path"
        }
        val variantsJson = summaries.joinToString(",\n") { summary ->
            m60F16FinalWgslVariantSummaryJson(summary).prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-420",
              "sceneId": "m60-f16-aa-stencil-cover-final-wgsl-diagnostic-for420",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-420-m60-f16-exporter-wgsl-final-diagnostique-et-comparer-fs-inside-outside",
              "sourceFinding": "global/kanvas/findings/for-419-diagnostique-le-hook-storage-shader-return-hors-chemin-return-reel",
              "sourceArtifacts": {
                "for412": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json",
                "for418": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-storage-vs-color-target-for418/m60-f16-aa-stencil-cover-storage-vs-color-target-for418.json",
                "for419": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419/m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": ${snapshot.sourceOwner.jsonString()},
              "decision": "M60_F16_AA_STENCIL_COVER_FINAL_WGSL_DIAGNOSTIC_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "diagnostic-final-wgsl-hooks-replace-render-return-path",
                "diagnostic-final-wgsl-hooks-not-on-rendered-return-path",
                "wgsl-final-source-entrypoint-missing",
                "wgsl-final-source-export-unavailable"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "guards": {
                "finalWgslDiagnostic": {"guardId": ${snapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${snapshot.enabled}, "enabledByDefault": false},
                "shaderReturnDiagnostic": {"guardId": "$FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY", "enabledByDefault": false},
                "storageColorTargetComparison": {"guardId": "$FOR418_STORAGE_COLOR_TARGET_COMPARISON_PROPERTY", "enabledByDefault": false},
                "shaderReturnStorageZeroCause": {"guardId": "$FOR419_SHADER_RETURN_STORAGE_ZERO_CAUSE_PROPERTY", "enabledByDefault": false}
              },
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16AaStencilCoverFinalWgslDiagnosticSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "requestedBoundary": ${snapshot.requestedBoundary.jsonString()},
                "observedBoundary": ${snapshot.observedBoundary.jsonString()},
                "variantCount": ${summaries.size},
                "missingSourceCount": $missingSourceCount
              },
              "comparisonPolicy": {
                "hashAlgorithm": "SHA-256 over exact final WGSL String passed to ShaderModuleDescriptor(code = ...)",
                "sourceDumpPolicy": "No full WGSL dump is stored; each function summary is capped to bounded normalized lines.",
                "entryPointsCompared": ["fs_inside", "fs_outside"],
                "hooksCompared": [
                  "m60_f16_record_fragment_lane",
                  "m60_f16_record_application_point",
                  "m60_f16_application_point_output",
                  "return @location(0)"
                ]
              },
              "structuralSummary": {
                "for418UsesFor412ShaderSource": $shaderReturnSharedByFor418,
                "allVariantsHaveFsInsideAndFsOutside": $fsEntryPointsPresent,
                "diagnosticVariantsReturnApplicationPointOutput": $applicationPointReturnedByDiagnostics,
                "for419HasEntryStorageWrite": ${
            summaries.firstOrNull { it.logicalName == "for419-storage-zero-cause" }
                ?.functions
                ?.values
                ?.any { it.callsRecordFragmentLane } ?: false
        }
              },
              "variants": [
            $variantsJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "renderingFixApplied": false,
                "wgsl4kModified": false,
                "fullWgslDumpStored": false
              },
              "classificationReason": ${
            when (classification) {
                "diagnostic-final-wgsl-hooks-replace-render-return-path" ->
                    "The final WGSL given to createShaderModule shows FOR-412/FOR-418/FOR-419 fs_inside and fs_outside returning m60_f16_application_point_output; FOR-418 shares the FOR-412 shader source, and FOR-419 removes the entry storage hook while keeping the application-point return substitution."
                "diagnostic-final-wgsl-hooks-not-on-rendered-return-path" ->
                    "The final WGSL was exported, and the diagnostic helper functions exist, but fs_inside/fs_outside still return the normal bounded-runtime expression instead of m60_f16_application_point_output; the application-point storage hook is therefore not on the rendered @location(0) path."
                "wgsl-final-source-entrypoint-missing" ->
                    "At least one exported final WGSL source lacks fs_inside or fs_outside."
                else ->
                    "At least one expected final WGSL source was not available in the opt-in export snapshot."
            }.jsonString()
        },
              "nextStep": "Use the final-source proof to focus the next ticket on why the application-point storage side-channel still does not observe records at runtime, without changing M60 F16 rendering.",
              "validationCommands": [
                "rtk python3 scripts/validate_for420_m60_f16_aa_stencil_cover_final_wgsl_diagnostic.py",
                "rtk python3 scripts/validate_for419_m60_f16_aa_stencil_cover_shader_return_storage_zero_cause.py",
                "rtk python3 scripts/validate_for418_m60_f16_aa_stencil_cover_storage_vs_color_target.py",
                "rtk python3 scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py",
                "rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
                "rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
                "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
                "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
                "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for420-pycache python3 -m py_compile scripts/validate_for420_m60_f16_aa_stencil_cover_final_wgsl_diagnostic.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun writeM60F16AaStencilCoverVerifiedReturnPathDiagnostic(
        finalWgslSnapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        storageSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        adapter: String,
    ) {
        val sceneId = "m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421"
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/$sceneId").apply { mkdirs() }
        File(dir, "$sceneId.json").writeText(
            m60F16AaStencilCoverVerifiedReturnPathDiagnosticJson(
                sceneId = sceneId,
                finalWgslSnapshot = finalWgslSnapshot,
                storageSnapshot = storageSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun m60F16AaStencilCoverVerifiedReturnPathDiagnosticJson(
        sceneId: String,
        finalWgslSnapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        storageSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        adapter: String,
    ): String {
        val summaries = finalWgslSnapshot.variants.map { m60F16FinalWgslVariantSummary(it) }
        val diagnosticSummaries = summaries.filter { it.logicalName != "normal-bounded-runtime-correction" }
        val diagnosticReturnPathVerified = diagnosticSummaries.all { summary ->
            summary.functions["fs_inside"]?.returnsApplicationPointOutput == true &&
                summary.functions["fs_outside"]?.returnsApplicationPointOutput == true
        }
        val for419Summary = summaries.firstOrNull { it.logicalName == "for419-storage-zero-cause" }
        val for419EntryStorageDisabled =
            for419Summary?.functions?.values?.none { it.callsRecordFragmentLane } == true
        val storageSamples = storageSnapshot.storageEvents.flatMap { it.samples }
        val colorTargetSamples = storageSnapshot.colorTargetEvents.flatMap { it.samples }
        val storageObservedCount = storageSamples.count { it.shaderObserved }
        val storageNonzeroCount = storageSamples.count { it.sourceColorSentToBlend.isNonzeroFloatArray() }
        val colorTargetNonzeroCount = colorTargetSamples.count { it.scratchOutputRgbaFloat.isNonzeroFloatArray() }
        val classification = when {
            !diagnosticReturnPathVerified -> "diagnostic-return-path-instrumentation-unavailable"
            storageObservedCount > 0 && storageNonzeroCount > 0 && colorTargetNonzeroCount > 0 ->
                "verified-return-path-storage-nonzero"
            colorTargetNonzeroCount > 0 -> "verified-return-path-storage-still-zero"
            else -> "verified-return-path-color-target-unavailable"
        }
        val variantsJson = summaries.joinToString(",\n") { summary ->
            """
            {
              "logicalName": ${summary.logicalName.jsonString()},
              "sourceHashSha256": ${summary.sourceHash.jsonString()},
              "divergenceFromNormal": ${summary.divergenceFromNormal.jsonString()},
              "fsInsideReturnsApplicationPointOutput": ${summary.functions["fs_inside"]?.returnsApplicationPointOutput == true},
              "fsOutsideReturnsApplicationPointOutput": ${summary.functions["fs_outside"]?.returnsApplicationPointOutput == true},
              "fsInsideCallsEntryStorage": ${summary.functions["fs_inside"]?.callsRecordFragmentLane == true},
              "fsOutsideCallsEntryStorage": ${summary.functions["fs_outside"]?.callsRecordFragmentLane == true},
              "containsOutputNonzeroGate": ${summary.containsOutputNonzeroGate}
            }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-421",
              "sceneId": ${sceneId.jsonString()},
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-421-m60-f16-corriger-instrumentation-diagnostique-vrai-retour-wgsl",
              "sourceFinding": "global/kanvas/findings/for-420-export-wgsl-final-confirme-hooks-diagnostiques-hors-chemin-rendered-return",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "verified-return-path-storage-nonzero",
                "verified-return-path-storage-still-zero",
                "verified-return-path-color-target-unavailable",
                "diagnostic-return-path-instrumentation-unavailable"
              ],
              "supportClaim": false,
              "promoted": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "guards": {
                "finalWgslDiagnostic": {"guardId": ${finalWgslSnapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${finalWgslSnapshot.enabled}, "enabledByDefault": false},
                "shaderReturnStorageZeroCause": {"guardId": ${storageSnapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${storageSnapshot.enabled}, "enabledByDefault": false}
              },
              "structuralSummary": {
                "diagnosticReturnPathVerified": $diagnosticReturnPathVerified,
                "for419EntryStorageDisabled": $for419EntryStorageDisabled,
                "storageSampleCount": ${storageSamples.size},
                "storageObservedCount": $storageObservedCount,
                "storageNonzeroSourceCount": $storageNonzeroCount,
                "colorTargetSampleCount": ${colorTargetSamples.size},
                "colorTargetNonzeroCount": $colorTargetNonzeroCount
              },
              "variants": [
            $variantsJson
              ],
              "classificationReason": ${
            when (classification) {
                "verified-return-path-storage-nonzero" ->
                    "FOR-421 verified that fs_inside/fs_outside return the instrumented application-point helper in the final WGSL and the FOR-419 storage side-channel now observes nonzero source values on the true rendered return path."
                "verified-return-path-storage-still-zero" ->
                    "FOR-421 verified the rendered return path instrumentation, but storage still did not observe nonzero source values while the color target stayed nonzero."
                "verified-return-path-color-target-unavailable" ->
                    "FOR-421 verified the rendered return path instrumentation, but the scratch color target evidence was unavailable or zero."
                else ->
                    "FOR-421 could not verify that both diagnostic entry points return the application-point helper in the final WGSL."
            }.jsonString()
        },
              "nextStep": "Use the verified storage source values to compare against the scratch color target and isolate the remaining M60 F16 rendered-color mismatch without changing default rendering.",
              "validationCommands": [
                "rtk python3 scripts/validate_for421_m60_f16_aa_stencil_cover_verified_return_path_diagnostic.py",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun writeM60F16AaStencilCoverVerifiedSourceComparison(
        finalWgslSnapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        storageSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ) {
        val sceneId = "m60-f16-aa-stencil-cover-verified-source-comparison-for422"
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/$sceneId").apply { mkdirs() }
        File(dir, "$sceneId.json").writeText(
            m60F16AaStencilCoverVerifiedSourceComparisonJson(
                sceneId = sceneId,
                finalWgslSnapshot = finalWgslSnapshot,
                storageSnapshot = storageSnapshot,
                predrawSnapshot = predrawSnapshot,
                postPassSnapshot = postPassSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun m60F16AaStencilCoverVerifiedSourceComparisonJson(
        sceneId: String,
        finalWgslSnapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        storageSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ): String {
        val summaries = finalWgslSnapshot.variants.map { m60F16FinalWgslVariantSummary(it) }
        val diagnosticSummaries = summaries.filter { it.logicalName != "normal-bounded-runtime-correction" }
        val diagnosticReturnPathVerified = diagnosticSummaries.all { summary ->
            summary.functions["fs_inside"]?.returnsApplicationPointOutput == true &&
                summary.functions["fs_outside"]?.returnsApplicationPointOutput == true
        }
        val storageByKey = storageSnapshot.storageEvents
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val colorByKey = storageSnapshot.colorTargetEvents
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.firstOrNull { it.second.readbackAvailable } ?: values.first() }
        val predrawByKey = predrawSnapshot.events
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.firstOrNull { it.second.readbackAvailable } ?: values.first() }
        val postPassByKey = postPassSnapshot.events
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.firstOrNull { it.second.readbackAvailable } ?: values.first() }

        val selectedPoints = M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS.toSet()
        val keys = colorByKey.keys
            .filter { selectedPoints.contains(it.x to it.y) }
            .sortedWith(compareBy<M60F16DrawPixelKey> { it.drawIndex }.thenBy { it.y }.thenBy { it.x })
        val comparisons = keys.map { key ->
            val sourceRecords = storageByKey[key].orEmpty()
                .sortedWith(
                    compareBy<Pair<
                        SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
                        SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
                        >> { it.second.subdrawOrdinal }.thenBy { it.second.subdrawRole },
                )
            val verifiedSources = sourceRecords.filter { (_, sample) ->
                sample.shaderObserved && !sample.captureSynthetic && sample.sourceColorSentToBlend != null
            }
            val colorTarget = colorByKey[key]
            val predraw = predrawByKey[key]
            val postPass = postPassByKey[key]
            val scratch = colorTarget?.second?.scratchOutputRgbaFloat
            val dstBefore = predraw?.second?.dstBeforeRgbaFloat
            val dstAfter = postPass?.second?.observedRgbaFloat
            val sourceDeltas = if (scratch != null) {
                verifiedSources.mapNotNull { (_, sample) ->
                    sample.sourceColorSentToBlend?.let {
                        M60F16VerifiedSourceDelta(sample.subdrawOrdinal, sample.subdrawRole, rgbaDelta(it, scratch, FOR417_RECONSTRUCTION_TOLERANCE))
                    }
                }
            } else {
                emptyList()
            }
            val bestSourceDelta = sourceDeltas.minByOrNull { it.delta.maxChannel }
            val reconstructed = if (scratch != null && dstBefore != null) {
                sourceOverPremul(scratch, dstBefore)
            } else {
                null
            }
            val finalDelta = if (reconstructed != null && dstAfter != null) {
                rgbaDelta(reconstructed, dstAfter, FOR417_RECONSTRUCTION_TOLERANCE)
            } else {
                null
            }
            val dstMutationDelta = if (dstBefore != null && dstAfter != null) {
                rgbaDelta(dstAfter, dstBefore, FOR412_MATCH_TOLERANCE)
            } else {
                null
            }
            val decisive = scratch.isNonzeroFloatArray() ||
                verifiedSources.any { it.second.sourceColorSentToBlend.isNonzeroFloatArray() } ||
                dstMutationDelta?.withinTolerance == false
            val classification = when {
                !diagnosticReturnPathVerified || verifiedSources.isEmpty() ||
                    scratch == null || dstBefore == null || dstAfter == null ->
                    "verified-source-comparison-incomplete"
                bestSourceDelta?.delta?.withinTolerance != true ->
                    "verified-source-diverges-from-scratch"
                finalDelta?.withinTolerance == true ->
                    "verified-source-matches-scratch-and-final-mutation"
                else ->
                    "verified-source-matches-scratch-but-final-blend-diverges"
            }
            M60F16VerifiedSourceComparisonRecord(
                key = key,
                sourceRecords = sourceRecords,
                colorTarget = colorTarget,
                predraw = predraw,
                postPass = postPass,
                reconstructedSrcOver = reconstructed,
                sourceDeltas = sourceDeltas,
                bestSourceDelta = bestSourceDelta,
                scratchSourceOverVsFinalDelta = finalDelta,
                dstMutationDelta = dstMutationDelta,
                decisive = decisive,
                classification = classification,
            )
        }
        val decisiveComparisons = comparisons.filter { it.decisive }
        val globalClassification = when {
            decisiveComparisons.isEmpty() ||
                decisiveComparisons.any { it.classification == "verified-source-comparison-incomplete" } ->
                "verified-source-comparison-incomplete"
            decisiveComparisons.any { it.classification == "verified-source-diverges-from-scratch" } ->
                "verified-source-diverges-from-scratch"
            decisiveComparisons.all { it.classification == "verified-source-matches-scratch-and-final-mutation" } ->
                "verified-source-matches-scratch-and-final-mutation"
            else -> "verified-source-matches-scratch-but-final-blend-diverges"
        }
        val comparisonsJson = comparisons.joinToString(",\n") { comparison ->
            m60F16VerifiedSourceComparisonRecordJson(comparison).prependIndent("    ")
        }
        val classificationCounts = comparisons.groupingBy { it.classification }.eachCount()
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-422",
              "sceneId": ${sceneId.jsonString()},
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-422-m60-f16-comparer-source-verifiee-color-target-et-sortie-finale",
              "sourceFinding": "global/kanvas/findings/for-421-retour-wgsl-instrumente-verifie-storage-non-nul",
              "sourceArtifacts": {
                "for421": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421/m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421.json",
                "for417": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417/m60-f16-aa-stencil-cover-isolated-color-target-runtime-for417.json",
                "for414": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-draw-readback-for414/m60-f16-aa-stencil-cover-post-draw-readback-for414.json",
                "for418": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-storage-vs-color-target-for418/m60-f16-aa-stencil-cover-storage-vs-color-target-for418.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "classification": ${globalClassification.jsonString()},
              "globalClassification": ${globalClassification.jsonString()},
              "allowedClassifications": [
                "verified-source-matches-scratch-and-final-mutation",
                "verified-source-matches-scratch-but-final-blend-diverges",
                "verified-source-diverges-from-scratch",
                "verified-source-comparison-incomplete"
              ],
              "supportClaim": false,
              "promoted": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "guards": {
                "finalWgslDiagnostic": {"guardId": ${finalWgslSnapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${finalWgslSnapshot.enabled}, "enabledByDefault": false},
                "shaderReturnStorageZeroCause": {"guardId": ${storageSnapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${storageSnapshot.enabled}, "enabledByDefault": false},
                "predrawDstReadback": {"guardId": ${predrawSnapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${predrawSnapshot.enabled}, "enabledByDefault": false},
                "postPassReadback": {"guardId": ${postPassSnapshot.propertyName.jsonString()}, "enabledForEvidenceRun": ${postPassSnapshot.enabled}, "enabledByDefault": false}
              },
              "comparisonPolicy": {
                "sourceMeaning": "sourceColorSentToBlend is the non-synthetic vec4f captured by the verified FOR-421 return-path instrumentation.",
                "scratchMeaning": "scratchOutputRgbaFloat is the RGBA16Float no-blend color-target output captured from the same diagnostic draw pass.",
                "finalMeaning": "dstAfterRgbaFloat is the immediate post-draw destination sample observed by the FOR-405/FOR-414 readback path.",
                "math": "premultiplied float SrcOver: out = src + dst * (1 - src.a)",
                "sourceScratchTolerance": $FOR417_RECONSTRUCTION_TOLERANCE,
                "scratchFinalTolerance": $FOR417_RECONSTRUCTION_TOLERANCE,
                "boundedRecordPolicy": "One record per selected pixel and sampled draw; no WGSL source dump or full framebuffer dump is stored."
              },
              "structuralSummary": {
                "diagnosticReturnPathVerified": $diagnosticReturnPathVerified,
                "selectedPixelCount": ${selectedPoints.size},
                "localComparisonCount": ${comparisons.size},
                "decisiveComparisonCount": ${decisiveComparisons.size},
                "verifiedSourceSubdrawCount": ${comparisons.sumOf { record -> record.sourceRecords.count { it.second.sourceColorSentToBlend != null && it.second.shaderObserved && !it.second.captureSynthetic } }},
                "nonzeroVerifiedSourceSubdrawCount": ${comparisons.sumOf { record -> record.sourceRecords.count { it.second.sourceColorSentToBlend.isNonzeroFloatArray() } }},
                "scratchColorTargetObservedCount": ${comparisons.count { it.colorTarget?.second?.scratchOutputRgbaFloat != null }},
                "nonzeroScratchColorTargetCount": ${comparisons.count { it.colorTarget?.second?.scratchOutputRgbaFloat.isNonzeroFloatArray() }},
                "dstBeforeObservedCount": ${comparisons.count { it.predraw?.second?.dstBeforeRgbaFloat != null }},
                "dstAfterObservedCount": ${comparisons.count { it.postPass?.second?.observedRgbaFloat != null }},
                "sourceMatchesScratchCount": ${comparisons.count { it.bestSourceDelta?.delta?.withinTolerance == true }},
                "scratchReconstructsFinalCount": ${comparisons.count { it.scratchSourceOverVsFinalDelta?.withinTolerance == true }},
                "classificationCounts": {
                  "verified-source-matches-scratch-and-final-mutation": ${classificationCounts["verified-source-matches-scratch-and-final-mutation"] ?: 0},
                  "verified-source-matches-scratch-but-final-blend-diverges": ${classificationCounts["verified-source-matches-scratch-but-final-blend-diverges"] ?: 0},
                  "verified-source-diverges-from-scratch": ${classificationCounts["verified-source-diverges-from-scratch"] ?: 0},
                  "verified-source-comparison-incomplete": ${classificationCounts["verified-source-comparison-incomplete"] ?: 0}
                }
              },
              "localComparisons": [
            $comparisonsJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "fallbackChanged": false,
                "renderingFixApplied": false,
                "wgsl4kModified": false,
                "fullWgslDumpStored": false
              },
              "classificationReason": ${m60F16VerifiedSourceGlobalReason(globalClassification).jsonString()},
              "nextStep": ${m60F16VerifiedSourceNextStep(globalClassification).jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py",
                "rtk python3 scripts/validate_for421_m60_f16_aa_stencil_cover_verified_return_path_diagnostic.py",
                "rtk python3 scripts/validate_for420_m60_f16_aa_stencil_cover_final_wgsl_diagnostic.py",
                "rtk python3 scripts/validate_for419_m60_f16_aa_stencil_cover_shader_return_storage_zero_cause.py",
                "rtk python3 scripts/validate_for418_m60_f16_aa_stencil_cover_storage_vs_color_target.py",
                "rtk python3 scripts/validate_for417_m60_f16_aa_stencil_cover_isolated_color_target_runtime.py",
                "rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py",
                "rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py",
                "rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py",
                "rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py",
                "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for422-pycache python3 -m py_compile scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16VerifiedSourceComparisonRecordJson(
        record: M60F16VerifiedSourceComparisonRecord,
    ): String {
        val sourceSubdrawsJson = record.sourceRecords.joinToString(",\n") { (_, sample) ->
            val source = sample.sourceColorSentToBlend
            val sourceDelta = record.colorTarget?.second?.scratchOutputRgbaFloat?.let { scratch ->
                source?.let { rgbaDelta(it, scratch, FOR417_RECONSTRUCTION_TOLERANCE) }
            }
            """
                {
                  "subdrawOrdinal": ${sample.subdrawOrdinal},
                  "subdrawRole": ${sample.subdrawRole.jsonString()},
                  "shaderObserved": ${sample.shaderObserved},
                  "captureSynthetic": ${sample.captureSynthetic},
                  "candidateBranchReached": ${sample.candidateBranchReached},
                  "sourceColorSentToBlend": ${source.floatArrayOrNullJson()},
                  "sourceVsScratchDelta": ${sourceDelta?.let { rgbaDeltaJson(it) } ?: "null"},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
            """.trimIndent().prependIndent("        ")
        }
        return """
            {
              "x": ${record.key.x},
              "y": ${record.key.y},
              "drawIndex": ${record.key.drawIndex},
              "pipelineFamily": ${record.colorTarget?.first?.pipelineFamily?.jsonString() ?: "null"},
              "blendMode": ${record.colorTarget?.first?.blendMode?.jsonString() ?: "null"},
              "decisiveForGlobalClassification": ${record.decisive},
              "classification": ${record.classification.jsonString()},
              "classificationReason": ${m60F16VerifiedSourceLocalReason(record).jsonString()},
              "sourceSubdraws": [
            $sourceSubdrawsJson
              ],
              "scratchColorTarget": {
                "available": ${record.colorTarget?.second?.scratchOutputRgbaFloat != null},
                "scratchFormat": "RGBA16Float",
                "scratchOutputRgbaFloat": ${record.colorTarget?.second?.scratchOutputRgbaFloat.floatArrayOrNullJson()},
                "scratchOutputRgba8": ${record.colorTarget?.second?.scratchOutputRgba8.intArrayOrNullJson()},
                "classification": ${record.colorTarget?.second?.classification?.jsonString() ?: "null"},
                "reason": ${record.colorTarget?.second?.reason?.jsonString() ?: "null"}
              },
              "destination": {
                "dstBeforeRgbaFloat": ${record.predraw?.second?.dstBeforeRgbaFloat.floatArrayOrNullJson()},
                "dstBeforeRgba8": ${record.predraw?.second?.dstBeforeRgba8.intArrayOrNullJson()},
                "dstAfterRgbaFloat": ${record.postPass?.second?.observedRgbaFloat.floatArrayOrNullJson()},
                "dstAfterRgba8": ${record.postPass?.second?.observedRgba8.intArrayOrNullJson()},
                "dstAfterMinusBeforeDelta": ${record.dstMutationDelta?.let { rgbaDeltaJson(it) } ?: "null"}
              },
              "bestSourceVsScratchDelta": ${record.bestSourceDelta?.let { best ->
            """
                {
                  "subdrawOrdinal": ${best.subdrawOrdinal},
                  "subdrawRole": ${best.subdrawRole.jsonString()},
                  "delta": ${rgbaDeltaJson(best.delta)}
                }
            """.trimIndent()
        } ?: "null"},
              "reconstruction": {
                "formula": "SrcOver(scratchOutputRgbaFloat, dstBeforeRgbaFloat)",
                "reconstructedRgbaFloat": ${record.reconstructedSrcOver.floatArrayOrNullJson()},
                "reconstructedVsDstAfterDelta": ${record.scratchSourceOverVsFinalDelta?.let { rgbaDeltaJson(it) } ?: "null"}
              }
            }
        """.trimIndent()
    }

    private fun m60F16VerifiedSourceLocalReason(record: M60F16VerifiedSourceComparisonRecord): String = when (record.classification) {
        "verified-source-matches-scratch-and-final-mutation" ->
            "The verified source matches the RGBA16Float scratch output, and SrcOver(scratch, dstBefore) reconstructs the immediate post-draw destination within F16 tolerance."
        "verified-source-matches-scratch-but-final-blend-diverges" ->
            "The verified source matches the scratch output, but SrcOver(scratch, dstBefore) does not reconstruct the immediate post-draw destination."
        "verified-source-diverges-from-scratch" ->
            "The verified return-path source does not match the no-blend scratch color-target output for this draw/pixel."
        else ->
            "The comparison is incomplete because a verified source, scratch output, dstBefore, dstAfter, or final WGSL return-path proof is unavailable."
    }

    private fun m60F16VerifiedSourceGlobalReason(classification: String): String = when (classification) {
        "verified-source-matches-scratch-and-final-mutation" ->
            "For the decisive M60 F16 samples, the verified return-path source matches the no-blend color target and the SrcOver reconstruction reproduces the immediate final mutation."
        "verified-source-matches-scratch-but-final-blend-diverges" ->
            "The verified return-path source matches the scratch color target, but the fixed-function blend/store result diverges from the SrcOver reconstruction."
        "verified-source-diverges-from-scratch" ->
            "At least one decisive sample has a verified return-path source that diverges from the no-blend scratch color-target output."
        else ->
            "The run does not contain enough verified source, scratch, dstBefore, and dstAfter data to localize the M60 F16 residue."
    }

    private fun m60F16VerifiedSourceNextStep(classification: String): String = when (classification) {
        "verified-source-matches-scratch-and-final-mutation" ->
            "Stop treating the storage side-channel as suspect; use the verified source values to target the remaining color/coverage residual against the reference scene."
        "verified-source-matches-scratch-but-final-blend-diverges" ->
            "Inspect fixed-function blend, destination load/store, and attachment format behavior for the decisive M60 F16 samples."
        "verified-source-diverges-from-scratch" ->
            "Inspect the diagnostic pass difference between return-path storage and no-blend color-target writes before changing rendering behavior."
        else ->
            "Rerun the bounded diagnostics with FOR-421, scratch color target, predraw, and post-draw readbacks enabled."
    }

    private fun writeM60F16AaStencilCoverReferenceSourceCoverage(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        finalWgslSnapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        storageSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ) {
        val sceneId = "m60-f16-aa-stencil-cover-reference-source-coverage-for423"
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/$sceneId").apply { mkdirs() }
        File(dir, "$sceneId.json").writeText(
            m60F16AaStencilCoverReferenceSourceCoverageJson(
                sceneId = sceneId,
                reference = reference,
                currentGpu = currentGpu,
                finalWgslSnapshot = finalWgslSnapshot,
                storageSnapshot = storageSnapshot,
                predrawSnapshot = predrawSnapshot,
                postPassSnapshot = postPassSnapshot,
                adapter = adapter,
            ),
        )
    }

    private fun m60F16AaStencilCoverReferenceSourceCoverageJson(
        sceneId: String,
        reference: SkBitmap,
        currentGpu: SkBitmap,
        finalWgslSnapshot: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslDiagnosticSnapshot,
        storageSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnStorageZeroCauseSnapshot,
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ): String {
        val coverageMask = TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())
        val summaries = finalWgslSnapshot.variants.map { m60F16FinalWgslVariantSummary(it) }
        val diagnosticReturnPathVerified = summaries
            .filter { it.logicalName != "normal-bounded-runtime-correction" }
            .all { summary ->
                summary.functions["fs_inside"]?.returnsApplicationPointOutput == true &&
                    summary.functions["fs_outside"]?.returnsApplicationPointOutput == true
            }
        val storageByKey = storageSnapshot.storageEvents
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val colorByKey = storageSnapshot.colorTargetEvents
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.firstOrNull { it.second.readbackAvailable } ?: values.first() }
        val predrawByKey = predrawSnapshot.events
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.firstOrNull { it.second.readbackAvailable } ?: values.first() }
        val postPassByKey = postPassSnapshot.events
            .flatMap { event -> event.samples.map { sample -> M60F16DrawPixelKey(event.drawIndex, sample.x, sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.firstOrNull { it.second.readbackAvailable } ?: values.first() }
        val selectedPoints = M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS.toSet()
        val records = colorByKey.keys
            .filter { selectedPoints.contains(it.x to it.y) }
            .sortedWith(compareBy<M60F16DrawPixelKey> { it.drawIndex }.thenBy { it.y }.thenBy { it.x })
            .map { key ->
                val sourceRecords = storageByKey[key].orEmpty()
                    .sortedWith(
                        compareBy<Pair<
                            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
                            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
                            >> { it.second.subdrawOrdinal }.thenBy { it.second.subdrawRole },
                    )
                val verifiedSources = sourceRecords.filter { (_, sample) ->
                    sample.shaderObserved && !sample.captureSynthetic && sample.sourceColorSentToBlend != null
                }
                val bestSource = verifiedSources.maxByOrNull { (_, sample) -> sample.sourceColorSentToBlend?.getOrNull(3) ?: -1f }
                val band = strokePaintBands().firstOrNull { key.x in it.xStart until it.xEnd }
                val coverageByte = (coverageMask.getPixel(key.x, key.y) ushr 24) and 0xFF
                val expectedCoverage = coverageByte / 255f
                val expectedSource = band?.let { premulPaintSourceFloat(it.sourceColor, expectedCoverage) }
                val source = bestSource?.second?.sourceColorSentToBlend
                val sourceDelta = if (source != null && expectedSource != null) {
                    rgbaDelta(source, expectedSource, FOR423_REFERENCE_TOLERANCE)
                } else {
                    null
                }
                val coverageDelta = source?.let { kotlin.math.abs(it[3] - expectedCoverage) }
                val coverageMatches = coverageDelta != null && coverageDelta <= FOR423_REFERENCE_TOLERANCE
                val referenceRgba = rgbaArray(reference.getPixel(key.x, key.y))
                val currentRgba = rgbaArray(currentGpu.getPixel(key.x, key.y))
                val finalObserved = postPassByKey[key]?.second?.observedRgbaFloat
                val referenceDelta = finalObserved?.let {
                    rgbaDelta(it, rgbaByteArrayToFloat(referenceRgba), FOR423_REFERENCE_TOLERANCE)
                }
                val decisive = source != null && band != null && finalObserved != null
                val classification = when {
                    !diagnosticReturnPathVerified || source == null || band == null || finalObserved == null ->
                        "reference-comparison-incomplete"
                    !coverageMatches ->
                        "verified-coverage-diverges-from-reference"
                    sourceDelta?.withinTolerance == false ->
                        "verified-source-diverges-from-reference"
                    else ->
                        "verified-source-and-coverage-match-reference"
                }
                M60F16ReferenceSourceCoverageRecord(
                    key = key,
                    band = band,
                    coverageByte = coverageByte,
                    expectedCoverage = expectedCoverage,
                    bestSource = bestSource,
                    expectedSource = expectedSource,
                    sourceVsReferenceDelta = sourceDelta,
                    coverageDelta = coverageDelta,
                    referenceRgba = referenceRgba,
                    currentGpuRgba = currentRgba,
                    postPass = postPassByKey[key],
                    predraw = predrawByKey[key],
                    colorTarget = colorByKey[key],
                    finalVsReferenceDelta = referenceDelta,
                    decisive = decisive,
                    classification = classification,
                )
            }
        val decisiveRecords = records.filter { it.decisive }
        val globalClassification = when {
            decisiveRecords.isEmpty() || decisiveRecords.any { it.classification == "reference-comparison-incomplete" } ->
                "reference-comparison-incomplete"
            decisiveRecords.any { it.classification == "verified-coverage-diverges-from-reference" } ->
                "verified-coverage-diverges-from-reference"
            decisiveRecords.any { it.classification == "verified-source-diverges-from-reference" } ->
                "verified-source-diverges-from-reference"
            else -> "verified-source-and-coverage-match-reference"
        }
        val classificationCounts = records.groupingBy { it.classification }.eachCount()
        val recordsJson = records.joinToString(",\n") { record ->
            m60F16ReferenceSourceCoverageRecordJson(record).prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-423",
              "sceneId": ${sceneId.jsonString()},
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-423-m60-f16-comparer-source-coverage-verifiees-reference-scene",
              "sourceFinding": "global/kanvas/findings/for-422-source-verifiee-correspond-scratch-et-mutation-finale",
              "sourceArtifacts": {
                "for422": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-verified-source-comparison-for422/m60-f16-aa-stencil-cover-verified-source-comparison-for422.json",
                "for421": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421/m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421.json",
                "for372": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-coverage-export-for372/m60-f16-effective-coverage-export-for372.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "classification": ${globalClassification.jsonString()},
              "globalClassification": ${globalClassification.jsonString()},
              "allowedClassifications": [
                "verified-source-and-coverage-match-reference",
                "verified-source-diverges-from-reference",
                "verified-coverage-diverges-from-reference",
                "reference-comparison-incomplete"
              ],
              "supportClaim": false,
              "promoted": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "comparisonPolicy": {
                "verifiedSourceMeaning": "sourceColorSentToBlend is the non-synthetic vec4f captured by the FOR-421 verified return-path instrumentation.",
                "coverageReferenceMeaning": "coverageExpectedAlpha is read from BoundedStrokeCapJoinCoverageMaskGM alpha at the same selected pixel.",
                "sourceReferenceMeaning": "expectedSourcePremulRgbaFloat is paintSourceRgba premultiplied by coverageExpectedAlpha.",
                "finalReferenceMeaning": "referenceRgba is the Skia/reference bitmap pixel for the selected scene coordinate.",
                "tolerance": $FOR423_REFERENCE_TOLERANCE,
                "boundedRecordPolicy": "One record per selected pixel and sampled draw; no framebuffer dump or WGSL source dump is stored."
              },
              "structuralSummary": {
                "diagnosticReturnPathVerified": $diagnosticReturnPathVerified,
                "selectedPixelCount": ${selectedPoints.size},
                "localComparisonCount": ${records.size},
                "decisiveComparisonCount": ${decisiveRecords.size},
                "verifiedSourceRecordCount": ${records.count { it.bestSource != null }},
                "coverageReferenceCount": ${records.count { it.band != null }},
                "coverageDivergenceCount": ${records.count { it.classification == "verified-coverage-diverges-from-reference" }},
                "sourceDivergenceCount": ${records.count { it.classification == "verified-source-diverges-from-reference" }},
                "sourceCoverageMatchCount": ${records.count { it.classification == "verified-source-and-coverage-match-reference" }},
                "incompleteCount": ${records.count { it.classification == "reference-comparison-incomplete" }},
                "classificationCounts": {
                  "verified-source-and-coverage-match-reference": ${classificationCounts["verified-source-and-coverage-match-reference"] ?: 0},
                  "verified-source-diverges-from-reference": ${classificationCounts["verified-source-diverges-from-reference"] ?: 0},
                  "verified-coverage-diverges-from-reference": ${classificationCounts["verified-coverage-diverges-from-reference"] ?: 0},
                  "reference-comparison-incomplete": ${classificationCounts["reference-comparison-incomplete"] ?: 0}
                }
              },
              "localComparisons": [
            $recordsJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "fallbackChanged": false,
                "renderingFixApplied": false,
                "wgsl4kModified": false,
                "fullWgslDumpStored": false
              },
              "classificationReason": ${m60F16ReferenceSourceCoverageGlobalReason(globalClassification).jsonString()},
              "nextStep": ${m60F16ReferenceSourceCoverageNextStep(globalClassification).jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py",
                "rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py",
                "rtk python3 scripts/validate_for421_m60_f16_aa_stencil_cover_verified_return_path_diagnostic.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for423-pycache python3 -m py_compile scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16ReferenceSourceCoverageRecordJson(record: M60F16ReferenceSourceCoverageRecord): String {
        val sourceSample = record.bestSource?.second
        return """
            {
              "x": ${record.key.x},
              "y": ${record.key.y},
              "drawIndex": ${record.key.drawIndex},
              "pipelineFamily": ${record.colorTarget?.first?.pipelineFamily?.jsonString() ?: "null"},
              "blendMode": ${record.colorTarget?.first?.blendMode?.jsonString() ?: "null"},
              "decisiveForGlobalClassification": ${record.decisive},
              "classification": ${record.classification.jsonString()},
              "classificationReason": ${m60F16ReferenceSourceCoverageLocalReason(record).jsonString()},
              "reference": {
                "strokeBand": ${record.band?.id?.jsonString() ?: "null"},
                "cap": ${record.band?.cap?.jsonString() ?: "null"},
                "join": ${record.band?.join?.jsonString() ?: "null"},
                "paintSourceRgba": ${record.band?.sourceColor?.let { rgbaJson(it) } ?: "null"},
                "coverageExpectedByte": ${record.coverageByte},
                "coverageExpectedAlpha": ${String.format(Locale.US, "%.9f", record.expectedCoverage)},
                "expectedSourcePremulRgbaFloat": ${record.expectedSource.floatArrayOrNullJson()},
                "referenceRgba": ${rgbaArrayJson(record.referenceRgba)},
                "currentGpuRgba": ${rgbaArrayJson(record.currentGpuRgba)}
              },
              "verifiedSource": {
                "available": ${sourceSample?.sourceColorSentToBlend != null},
                "subdrawOrdinal": ${sourceSample?.subdrawOrdinal ?: "null"},
                "subdrawRole": ${sourceSample?.subdrawRole?.jsonString() ?: "null"},
                "sourceColorSentToBlend": ${sourceSample?.sourceColorSentToBlend.floatArrayOrNullJson()},
                "coverageObservedAlpha": ${sourceSample?.sourceColorSentToBlend?.getOrNull(3)?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
                "coverageVsReferenceDelta": ${record.coverageDelta?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
                "sourceVsReferenceDelta": ${record.sourceVsReferenceDelta?.let { rgbaDeltaJson(it) } ?: "null"}
              },
              "finalOutput": {
                "dstBeforeRgbaFloat": ${record.predraw?.second?.dstBeforeRgbaFloat.floatArrayOrNullJson()},
                "dstAfterRgbaFloat": ${record.postPass?.second?.observedRgbaFloat.floatArrayOrNullJson()},
                "dstAfterRgba8": ${record.postPass?.second?.observedRgba8.intArrayOrNullJson()},
                "dstAfterVsReferenceDelta": ${record.finalVsReferenceDelta?.let { rgbaDeltaJson(it) } ?: "null"}
              }
            }
        """.trimIndent()
    }

    private fun premulPaintSourceFloat(pixel: Int, coverage: Float): FloatArray {
        val rgba = rgbaArray(pixel)
        val alpha = (rgba[3] / 255f) * coverage
        return floatArrayOf(
            (rgba[0] / 255f) * alpha,
            (rgba[1] / 255f) * alpha,
            (rgba[2] / 255f) * alpha,
            alpha,
        )
    }

    private fun rgbaByteArrayToFloat(rgba: IntArray): FloatArray =
        FloatArray(4) { index -> rgba[index] / 255f }

    private fun m60F16ReferenceSourceCoverageLocalReason(record: M60F16ReferenceSourceCoverageRecord): String = when (record.classification) {
        "verified-source-and-coverage-match-reference" ->
            "The verified source and observed alpha match the CPU coverage-mask reference for this selected pixel."
        "verified-source-diverges-from-reference" ->
            "The verified source color differs from paintSourceRgba premultiplied by the CPU coverage-mask alpha."
        "verified-coverage-diverges-from-reference" ->
            "The verified source alpha differs from the CPU coverage-mask alpha for this selected pixel."
        else ->
            "The comparison is incomplete because the verified source, reference band, final output, or return-path proof is unavailable."
    }

    private fun m60F16ReferenceSourceCoverageGlobalReason(classification: String): String = when (classification) {
        "verified-source-and-coverage-match-reference" ->
            "For the decisive M60 F16 samples, verified source color and coverage match the CPU reference inputs; the residual must be explained outside this source/coverage pair."
        "verified-source-diverges-from-reference" ->
            "At least one decisive sample has verified source color that diverges from the source expected from the scene reference and CPU coverage mask."
        "verified-coverage-diverges-from-reference" ->
            "At least one decisive sample has verified source alpha/coverage that diverges from the CPU coverage-mask reference."
        else ->
            "The run does not contain enough verified source, coverage reference, and final output data to compare against the scene reference."
    }

    private fun m60F16ReferenceSourceCoverageNextStep(classification: String): String = when (classification) {
        "verified-source-and-coverage-match-reference" ->
            "Inspect scene assembly, reference/oracle mapping, or later composition because verified source and coverage no longer explain the residual."
        "verified-source-diverges-from-reference" ->
            "Target the shader source-color calculation for the selected M60 F16 AA stencil-cover subdraws."
        "verified-coverage-diverges-from-reference" ->
            "Target the AA stencil-cover coverage/stencil side: the verified alpha sent to blend does not match the CPU coverage-mask reference."
        else ->
            "Add the missing bounded measurement before attempting a renderer correction."
    }

    private data class M60F16FinalWgslFunctionSummary(
        val name: String,
        val present: Boolean,
        val signatureHasLocationReturn: Boolean,
        val callsRecordFragmentLane: Boolean,
        val callsRecordApplicationPoint: Boolean,
        val callsApplicationPointOutput: Boolean,
        val returnsApplicationPointOutput: Boolean,
        val returnStatementCount: Int,
        val orderedMarkers: List<String>,
        val boundedLines: List<String>,
    )

    private data class M60F16FinalWgslVariantSummary(
        val logicalName: String,
        val cacheKey: String,
        val shaderLabel: String,
        val sourceHash: String,
        val sourceLength: Int,
        val sourceSharedWith: String?,
        val intendedUse: String,
        val functions: Map<String, M60F16FinalWgslFunctionSummary>,
        val applicationPointOutput: M60F16FinalWgslFunctionSummary,
        val containsRecordApplicationPointFunction: Boolean,
        val containsRecordFragmentLaneFunction: Boolean,
        val containsOutputNonzeroGate: Boolean,
        val divergenceFromNormal: String,
    )

    private fun m60F16FinalWgslVariantSummary(
        variant: SkWebGpuDevice.M60F16AaStencilCoverFinalWgslVariant,
    ): M60F16FinalWgslVariantSummary {
        val source = variant.source
        val fsInside = m60F16FinalWgslFunctionSummary(source, "fs_inside")
        val fsOutside = m60F16FinalWgslFunctionSummary(source, "fs_outside")
        val applicationPointOutput = m60F16FinalWgslFunctionSummary(source, "m60_f16_application_point_output")
        val containsRecordApplicationPointFunction = source.contains("fn m60_f16_record_application_point(")
        val containsRecordFragmentLaneFunction = source.contains("fn m60_f16_record_fragment_lane(")
        val containsOutputNonzeroGate = source.contains("let output_nonzero")
        val divergence = when {
            variant.source.isEmpty() -> "source-unavailable"
            variant.logicalName == "normal-bounded-runtime-correction" -> "baseline-normal-bounded-runtime-correction"
            variant.sourceSharedWith == "for412-shader-return-storage" ->
                "shares-for412-shader-return-source; pipeline target differs in FOR-418"
            containsOutputNonzeroGate && fsInside.returnsApplicationPointOutput && fsOutside.returnsApplicationPointOutput ->
                "removes-entry-storage-hook-and-keeps-application-point-return-substitution"
            fsInside.returnsApplicationPointOutput && fsOutside.returnsApplicationPointOutput ->
                "replaces-normal-return-with-application-point-storage-hook"
            else -> "diagnostic-return-path-not-replaced"
        }
        return M60F16FinalWgslVariantSummary(
            logicalName = variant.logicalName,
            cacheKey = variant.cacheKey,
            shaderLabel = variant.shaderLabel,
            sourceHash = if (source.isEmpty()) "" else sha256Hex(source),
            sourceLength = source.length,
            sourceSharedWith = variant.sourceSharedWith,
            intendedUse = variant.intendedUse,
            functions = linkedMapOf(
                "fs_inside" to fsInside,
                "fs_outside" to fsOutside,
            ),
            applicationPointOutput = applicationPointOutput,
            containsRecordApplicationPointFunction = containsRecordApplicationPointFunction,
            containsRecordFragmentLaneFunction = containsRecordFragmentLaneFunction,
            containsOutputNonzeroGate = containsOutputNonzeroGate,
            divergenceFromNormal = divergence,
        )
    }

    private fun m60F16FinalWgslFunctionSummary(
        source: String,
        functionName: String,
    ): M60F16FinalWgslFunctionSummary {
        val function = extractWgslFunction(source, functionName)
        val text = function?.body.orEmpty()
        val signature = function?.signature.orEmpty()
        val markers = listOf(
            "m60_f16_record_fragment_lane" to text.indexOf("m60_f16_record_fragment_lane"),
            "m60_f16_bounded_runtime_corrected_color" to text.indexOf("m60_f16_bounded_runtime_corrected_color"),
            "m60_f16_quantize_after_bounded_runtime_correction" to
                text.indexOf("m60_f16_quantize_after_bounded_runtime_correction"),
            "m60_f16_application_point_output" to text.indexOf("m60_f16_application_point_output"),
            "m60_f16_record_application_point" to text.indexOf("m60_f16_record_application_point"),
            "return" to text.indexOf("return"),
        ).filter { it.second >= 0 }
            .sortedBy { it.second }
            .map { it.first }
        val boundedLines = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(18)
            .toList()
        return M60F16FinalWgslFunctionSummary(
            name = functionName,
            present = function != null,
            signatureHasLocationReturn = signature.contains("-> @location(0) vec4f"),
            callsRecordFragmentLane = text.contains("m60_f16_record_fragment_lane("),
            callsRecordApplicationPoint = text.contains("m60_f16_record_application_point("),
            callsApplicationPointOutput = text.contains("m60_f16_application_point_output("),
            returnsApplicationPointOutput = text.contains("return m60_f16_application_point_output("),
            returnStatementCount = Regex("\\breturn\\b").findAll(text).count(),
            orderedMarkers = markers,
            boundedLines = boundedLines,
        )
    }

    private data class WgslFunctionSlice(
        val signature: String,
        val body: String,
    )

    private fun extractWgslFunction(source: String, functionName: String): WgslFunctionSlice? {
        val marker = "fn $functionName"
        val start = source.indexOf(marker)
        if (start < 0) return null
        val braceStart = source.indexOf('{', start)
        if (braceStart < 0) return null
        var depth = 0
        var end = braceStart
        while (end < source.length) {
            when (source[end]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        val signature = source.substring(start, braceStart).trim()
                        val body = source.substring(braceStart + 1, end)
                        return WgslFunctionSlice(signature, body)
                    }
                }
            }
            end += 1
        }
        return null
    }

    private fun m60F16FinalWgslVariantSummaryJson(summary: M60F16FinalWgslVariantSummary): String {
        val functionsJson = summary.functions.values.joinToString(",\n") { function ->
            m60F16FinalWgslFunctionSummaryJson(function).prependIndent("      ")
        }
        return """
            {
              "logicalName": ${summary.logicalName.jsonString()},
              "cacheKey": ${summary.cacheKey.jsonString()},
              "shaderLabel": ${summary.shaderLabel.jsonString()},
              "sourceHashSha256": ${summary.sourceHash.jsonString()},
              "sourceLengthBytes": ${summary.sourceLength},
              "sourceSharedWith": ${summary.sourceSharedWith?.jsonString() ?: "null"},
              "intendedUse": ${summary.intendedUse.jsonString()},
              "containsRecordApplicationPointFunction": ${summary.containsRecordApplicationPointFunction},
              "containsRecordFragmentLaneFunction": ${summary.containsRecordFragmentLaneFunction},
              "containsOutputNonzeroGate": ${summary.containsOutputNonzeroGate},
              "divergenceFromNormal": ${summary.divergenceFromNormal.jsonString()},
              "functions": [
            $functionsJson
              ],
              "applicationPointOutput": ${m60F16FinalWgslFunctionSummaryJson(summary.applicationPointOutput)}
            }
        """.trimIndent()
    }

    private fun m60F16FinalWgslFunctionSummaryJson(summary: M60F16FinalWgslFunctionSummary): String {
        val markersJson = summary.orderedMarkers.joinToString(", ") { it.jsonString() }
        val linesJson = summary.boundedLines.joinToString(",\n") { it.jsonString().prependIndent("        ") }
        return """
            {
              "name": ${summary.name.jsonString()},
              "present": ${summary.present},
              "signatureHasLocationReturn": ${summary.signatureHasLocationReturn},
              "callsRecordFragmentLane": ${summary.callsRecordFragmentLane},
              "callsRecordApplicationPoint": ${summary.callsRecordApplicationPoint},
              "callsApplicationPointOutput": ${summary.callsApplicationPointOutput},
              "returnsApplicationPointOutput": ${summary.returnsApplicationPointOutput},
              "returnStatementCount": ${summary.returnStatementCount},
              "orderedMarkers": [$markersJson],
              "boundedNormalizedLines": [
            $linesJson
              ]
            }
        """.trimIndent()
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun FloatArray?.isNonzeroFloatArray(): Boolean =
        this != null && any { kotlin.math.abs(it) > 1.0e-9f }

    private fun m60F16FragmentLaneRuntimeSnapshotExportJson(
        snapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        adapter: String,
    ): String {
        val expected = M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
        val expectedSet = expected.toSet()
        val observedPixels = snapshot.samples
            .filter { it.observedCandidateLane }
            .map { it.x to it.y }
            .distinct()
            .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
        val observedSet = observedPixels.toSet()
        val falsePositives = observedPixels.filter { it !in expectedSet }
        val falseNegatives = expected.filter { it !in observedSet }
        val exactMatch = snapshot.enabled &&
            snapshot.samples.isNotEmpty() &&
            snapshot.samples.size == expected.size &&
            falsePositives.isEmpty() &&
            falseNegatives.isEmpty() &&
            snapshot.samples.all { it.valid && it.observedCandidateLane }
        val classification = when {
            !snapshot.enabled || snapshot.samples.isEmpty() -> "fragment-lane-runtime-snapshot-empty"
            exactMatch -> "fragment-lane-runtime-snapshot-exported"
            else -> "fragment-lane-runtime-snapshot-mismatch"
        }
        val reason = when (classification) {
            "fragment-lane-runtime-snapshot-exported" ->
                "The opt-in fragment diagnostic snapshot exported the 8 FOR-391 expected lane pixels."
            "fragment-lane-runtime-snapshot-empty" ->
                if (snapshot.enabled) {
                    "The diagnostic guard was enabled, but m60F16FragmentLaneDiagnosticSnapshot() exported zero samples."
                } else {
                    "The diagnostic guard was disabled for this scene evidence run, so no runtime samples were exported."
                }
            else ->
                "The runtime snapshot did not match the 8 FOR-391 expected lane pixels exactly."
        }
        val nextStep = when (classification) {
            "fragment-lane-runtime-snapshot-exported" ->
                "Use the exported predicate evidence only as diagnostic input; FOR-397 does not enable correction or promotion."
            "fragment-lane-runtime-snapshot-empty" ->
                "Rerun the scene with both FOR-394 and FOR-396 opt-in guards, then inspect the AA stencil-cover diagnostic binding path if samples remain empty."
            else ->
                "Inspect the reported false positives and false negatives before considering any predicate activation."
        }
        val samplesJson = snapshot.samples.joinToString(",\n") { sample ->
            """
            {
              "x": ${sample.x},
              "y": ${sample.y},
              "observedCandidateLane": ${sample.observedCandidateLane},
              "coverageSide": ${sample.coverageSide.jsonString()},
              "validExpectedSlot": ${sample.valid}
            }
            """.trimIndent().prependIndent("    ")
        }
        val expectedJson = expected.joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        val observedJson = observedPixels.joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        val falsePositiveJson = falsePositives.joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        val falseNegativeJson = falseNegatives.joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-397",
              "sceneId": "m60-f16-fragment-lane-runtime-snapshot-export-for397",
              "sourceSceneId": "m60-bounded-stroke-cap-join",
              "decision": "M60_F16_FRAGMENT_LANE_RUNTIME_SNAPSHOT_EXPORTED",
              "classification": "$classification",
              "allowedClassifications": [
                "fragment-lane-runtime-snapshot-exported",
                "fragment-lane-runtime-snapshot-mismatch",
                "fragment-lane-runtime-snapshot-empty"
              ],
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMethod": "writeM60F16FragmentLaneRuntimeSnapshotExport",
              "sourceFinding": "global/kanvas/findings/for-396-installe-un-canal-diagnostique-fragment-aa-stencil-cover-m60-f16-sans-preuve-exact-match-runtime-exportee",
              "requiredFor396Decision": "M60_F16_AA_STENCIL_COVER_FRAGMENT_LANE_DIAGNOSTIC_CHANNEL_INSTALLED",
              "requiredFor394Decision": "M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_RECORDED",
              "requiredFor391Decision": "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED",
              "supportClaim": false,
              "promoted": false,
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16FragmentLaneDiagnosticSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
                "sampleCount": ${snapshot.samples.size},
                "samples": [
            $samplesJson
                ]
              },
              "guards": {
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                }
              },
              "pixelComparison": {
                "comparisonStatus": "$classification",
                "measurementScope": "FOR-396 diagnostic slots exported by m60F16FragmentLaneDiagnosticSnapshot()",
                "expectedUsefulPixels": [
            $expectedJson
                ],
                "expectedUsefulPixelCount": ${expected.size},
                "shaderObservedPixels": [
            $observedJson
                ],
                "shaderObservedPixelCount": ${observedPixels.size},
                "falsePositives": [
            $falsePositiveJson
                ],
                "falsePositiveCount": ${falsePositives.size},
                "falseNegatives": [
            $falseNegativeJson
                ],
                "falseNegativeCount": ${falseNegatives.size},
                "falsePositiveFalseNegativeMeasured": ${snapshot.enabled && snapshot.samples.isNotEmpty()},
                "runtimeReadbackArtifactCaptured": ${snapshot.enabled && snapshot.samples.isNotEmpty()},
                "exactMatchProvenByRuntimeReadback": $exactMatch
              },
              "nonGoalsPreserved": {
                "m60F16CorrectionEnabled": false,
                "runtimePredicateActivated": false,
                "finalColorChanged": false,
                "coverageChanged": false,
                "fallbackChanged": false,
                "scoringChanged": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "for380ProbeRouteUsed": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${reason.jsonString()},
              "nextStep": ${nextStep.jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for397-pycache-parent python3 -m py_compile scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16BoundedRuntimeCorrectionProbeJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        correctedGpu: SkBitmap,
        currentGpuCmp: BitmapComparison,
        correctedGpuCmp: BitmapComparison,
        currentResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        adapter: String,
    ): String {
        val expected = M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
        val expectedSet = expected.toSet()
        val observedPixels = snapshot.samples
            .filter { it.observedCandidateLane }
            .map { it.x to it.y }
            .distinct()
            .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
        val observedSet = observedPixels.toSet()
        val falsePositives = observedPixels.filter { it !in expectedSet }
        val falseNegatives = expected.filter { it !in observedSet }
        val exactRuntimePredicate = snapshot.enabled &&
            snapshot.samples.isNotEmpty() &&
            snapshot.samples.size == expected.size &&
            falsePositives.isEmpty() &&
            falseNegatives.isEmpty() &&
            snapshot.samples.all { it.valid && it.observedCandidateLane }
        val changedPixels = changedPixels(currentGpu, correctedGpu)
        val outsideChangedPixels = changedPixels.filter { it !in expectedSet }
        val currentTotalResidual = imageResidual(currentGpu, reference)
        val correctedTotalResidual = imageResidual(correctedGpu, reference)
        val residualDelta = correctedTotalResidual - currentTotalResidual
        val predicateEvaluations = expected.map { (x, y) ->
            val referencePixel = reference.getPixel(x, y)
            val currentPixel = currentGpu.getPixel(x, y)
            val correctedPixel = correctedGpu.getPixel(x, y)
            PredicateCorrectionPixel(
                x = x,
                y = y,
                reference = referencePixel,
                current = currentPixel,
                corrected = correctedPixel,
                currentResidual = sampleResidual(referencePixel, currentPixel),
                correctedResidual = sampleResidual(referencePixel, correctedPixel),
            )
        }
        val currentPredicateResidual = predicateEvaluations.sumOf { it.currentResidual }
        val correctedPredicateResidual = predicateEvaluations.sumOf { it.correctedResidual }
        val improvedPredicatePixels = predicateEvaluations.count { it.correctedResidual < it.currentResidual }
        val regressedPredicatePixels = predicateEvaluations.count { it.correctedResidual > it.currentResidual }
        val unchangedPredicatePixels = predicateEvaluations.count { it.correctedResidual == it.currentResidual }
        val fullScenePixelDeltas = pixelResidualDeltas(currentGpu, correctedGpu, reference)
        val classification = when {
            !exactRuntimePredicate -> "bounded-correction-refused"
            outsideChangedPixels.isNotEmpty() -> "bounded-correction-refused"
            correctedTotalResidual > currentTotalResidual -> "bounded-correction-regresses"
            correctedTotalResidual < currentTotalResidual -> "bounded-correction-reduces-residual"
            else -> "bounded-correction-refused"
        }
        val reason = when (classification) {
            "bounded-correction-reduces-residual" ->
                "The FOR-398 opt-in shader changed only the FOR-397 pixels and reduced the total residual."
            "bounded-correction-regresses" ->
                "The FOR-398 opt-in shader kept the predicate bounded but increased total residual, so it is not promotable."
            else -> when {
                !exactRuntimePredicate ->
                    "The FOR-397 runtime predicate proof was not exact for this evidence run; correction remains refused."
                outsideChangedPixels.isNotEmpty() ->
                    "The FOR-398 correction changed pixels outside the 8 FOR-397 predicate pixels; correction remains refused."
                else ->
                    "The FOR-398 correction did not reduce total residual; correction remains refused."
            }
        }
        val nextStep = when (classification) {
            "bounded-correction-reduces-residual" ->
                "Audit the remaining residual and any predicate-pixel regressions before considering a separate promotion ticket."
            "bounded-correction-regresses" ->
                "Keep FOR-398 disabled and inspect the per-pixel residual deltas before changing the correction formula."
            else ->
                "Keep FOR-398 disabled and preserve FOR-397 as diagnostic evidence until the bounded predicate and correction formula are both safe."
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-398",
              "sceneId": "m60-f16-bounded-runtime-correction-probe-for398",
              "sourceSceneId": "m60-f16-fragment-lane-runtime-snapshot-export-for397",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-fragment-lane-runtime-snapshot-export-for397/m60-f16-fragment-lane-runtime-snapshot-export-for397.json",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-correction-experimentale-opt-in-bornee-aux-8-pixels-apres-for-397",
              "sourceFinding": "global/kanvas/findings/for-397-exporte-le-snapshot-runtime-du-canal-fragment-m60-f16-et-prouve-8-pixels-exacts",
              "decision": "M60_F16_BOUNDED_RUNTIME_CORRECTION_PROBE_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "bounded-correction-reduces-residual",
                "bounded-correction-regresses",
                "bounded-correction-refused"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "correctionGuard": ${FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY.jsonString()},
              "boundedRendererPoint": "SkWebGpuDevice M60 F16 derived AA stencil-cover shader; fs_inside/fs_outside skip targetColorSpaceBlend only when m60_f16_candidate_lane(frag.xy) is true",
              "requiredFor397Classification": "fragment-lane-runtime-snapshot-exported",
              "requiredFor396Decision": "M60_F16_AA_STENCIL_COVER_FRAGMENT_LANE_DIAGNOSTIC_CHANNEL_INSTALLED",
              "requiredFor394Decision": "M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_RECORDED",
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                },
                "boundedRuntimeCorrection": {
                  "guardId": "$FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                }
              },
              "residualComparison": {
                "currentTotalResidual": $currentTotalResidual,
                "correctedTotalResidual": $correctedTotalResidual,
                "deltaCorrectedMinusCurrent": $residualDelta,
                "gainVsCurrent": ${currentTotalResidual - correctedTotalResidual},
                "currentSimilarity": ${String.format(Locale.US, "%.6f", currentGpuCmp.similarity)},
                "correctedSimilarity": ${String.format(Locale.US, "%.6f", correctedGpuCmp.similarity)},
                "currentMismatchPixels": ${currentResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "fullSceneImprovedPixels": ${fullScenePixelDeltas.improvedPixels},
                "fullSceneRegressedPixels": ${fullScenePixelDeltas.regressedPixels},
                "fullSceneUnchangedPixels": ${fullScenePixelDeltas.unchangedPixels}
              },
              "predicateProof": {
                "api": "SkWebGpuDevice.m60F16FragmentLaneDiagnosticSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "exactMatchProvenByRuntimeReadback": $exactRuntimePredicate,
                "expectedPixelCount": ${expected.size},
                "observedPixelCount": ${observedPixels.size},
                "falsePositiveCount": ${falsePositives.size},
                "falseNegativeCount": ${falseNegatives.size},
                "expectedPixels": [
            ${expected.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "observedPixels": [
            ${observedPixels.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "falsePositives": [
            ${falsePositives.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "falseNegatives": [
            ${falseNegatives.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ]
              },
              "boundedMutationCheck": {
                "changedPixelCount": ${changedPixels.size},
                "changedPixelsWithinExpectedPredicate": ${outsideChangedPixels.isEmpty()},
                "outsideExpectedChangedPixelCount": ${outsideChangedPixels.size},
                "changedPixelsFirst64": [
            ${changedPixels.take(64).joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "outsideExpectedChangedPixelsFirst64": [
            ${outsideChangedPixels.take(64).joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ]
              },
              "predicateResidualComparison": {
                "currentPredicateResidual": $currentPredicateResidual,
                "correctedPredicateResidual": $correctedPredicateResidual,
                "deltaCorrectedMinusCurrent": ${correctedPredicateResidual - currentPredicateResidual},
                "improvedPixels": $improvedPredicatePixels,
                "regressedPixels": $regressedPredicatePixels,
                "unchangedPixels": $unchangedPredicatePixels,
                "pixels": [
            ${predicateEvaluations.joinToString(",\n") { it.toJson().prependIndent("    ") }}
                ]
              },
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "productionRuntimeConnected": false,
                "generalizedOutsideM60F16": false,
                "for380BroadCorrectionReintroduced": false
              },
              "classificationReason": ${reason.jsonString()},
              "nextStep": ${nextStep.jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for398_m60_f16_bounded_runtime_correction_probe.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for398-pycache-parent python3 -m py_compile scripts/validate_for398_m60_f16_bounded_runtime_correction_probe.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16BoundedCorrectionApplicationPointJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        correctedGpu: SkBitmap,
        applicationPointGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16BoundedCorrectionApplicationPointSnapshot,
        adapter: String,
    ): String {
        val expected = M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
        val expectedSet = expected.toSet()
        val observedPixels = snapshot.samples
            .filter { it.candidateBranchReached }
            .map { it.x to it.y }
            .distinct()
            .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
        val observedSet = observedPixels.toSet()
        val falsePositives = observedPixels.filter { it !in expectedSet }
        val falseNegatives = expected.filter { it !in observedSet }
        val branchHit = snapshot.enabled &&
            snapshot.samples.size == expected.size &&
            falsePositives.isEmpty() &&
            falseNegatives.isEmpty() &&
            snapshot.samples.all { it.valid && it.candidateBranchReached }
        val changedPixels = changedPixels(currentGpu, correctedGpu)
        val applicationPointChangedPixels = changedPixels(correctedGpu, applicationPointGpu)
        val currentTotalResidual = imageResidual(currentGpu, reference)
        val correctedTotalResidual = imageResidual(correctedGpu, reference)
        val filteredTargetDifferentCount = snapshot.samples.count {
            !it.colorAfterColorFilter.closeTo(it.colorAfterTargetColorspaceIfNeeded)
        }
        val blendInputNonZeroCount = snapshot.samples.count {
            it.colorSentToBlendBeforeQuantization.any { channel -> kotlin.math.abs(channel) > 0.000001f }
        }
        val effectiveContributionCount = snapshot.samples.count {
            it.coverageAlpha > 0.0f && it.sourceAlphaAfterCoverage > 0.0f
        }
        val classification = when {
            !branchHit -> "correction-branch-not-hit"
            changedPixels.isEmpty() && effectiveContributionCount == 0 ->
                "correction-overwritten-by-stencil-cover-composition"
            filteredTargetDifferentCount == 0 -> "correction-values-identical-before-blend"
            changedPixels.isEmpty() ->
                "correction-overwritten-by-stencil-cover-composition"
            else -> "correction-point-still-ambiguous"
        }
        val reason = when (classification) {
            "correction-branch-not-hit" ->
                "The FOR-399 shader readback did not observe all FOR-397 pixels entering the candidate branch."
            "correction-values-identical-before-blend" ->
                "The candidate branch was reached, but filtered and target-colorspace values were identical before blending."
            "correction-overwritten-by-stencil-cover-composition" ->
                "The candidate branch was reached and shader values differed before blending, but the FOR-398 corrected render still changed zero pixels."
            else ->
                "The candidate branch was reached, but the observed shader values and final pixels do not close the application-point question."
        }
        val samplesJson = snapshot.samples.joinToString(",\n") { sample ->
            val referencePixel = reference.getPixel(sample.x, sample.y)
            val currentPixel = currentGpu.getPixel(sample.x, sample.y)
            val correctedPixel = correctedGpu.getPixel(sample.x, sample.y)
            val applicationPointPixel = applicationPointGpu.getPixel(sample.x, sample.y)
            """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "side": ${sample.coverageSide.jsonString()},
                  "candidateBranchReached": ${sample.candidateBranchReached},
                  "valid": ${sample.valid},
                  "colorAfterApplyColorFilter": ${sample.colorAfterColorFilter.floatJson()},
                  "colorAfterApplyTargetColorspaceIfNeeded": ${sample.colorAfterTargetColorspaceIfNeeded.floatJson()},
                  "colorSentToBlendBeforeQuantization": ${sample.colorSentToBlendBeforeQuantization.floatJson()},
                  "coverageAlphaUsed": ${String.format(Locale.US, "%.9f", sample.coverageAlpha)},
                  "sourceAlphaAfterCoverage": ${String.format(Locale.US, "%.9f", sample.sourceAlphaAfterCoverage)},
                  "quantizedColorSentToBlend": ${sample.quantizedColorSentToBlend.floatJson()},
                  "filteredEqualsTargetColorspace": ${sample.colorAfterColorFilter.closeTo(sample.colorAfterTargetColorspaceIfNeeded)},
                  "referenceRgba": ${rgbaJson(referencePixel)},
                  "currentRgba": ${rgbaJson(currentPixel)},
                  "correctedRgba": ${rgbaJson(correctedPixel)},
                  "applicationPointDiagnosticRgba": ${rgbaJson(applicationPointPixel)},
                  "currentResidual": ${sampleResidual(referencePixel, currentPixel)},
                  "correctedResidual": ${sampleResidual(referencePixel, correctedPixel)},
                  "applicationPointResidual": ${sampleResidual(referencePixel, applicationPointPixel)},
                  "finalPixelChangedByFor398Correction": ${currentPixel != correctedPixel},
                  "finalPixelChangedByFor399Diagnostic": ${correctedPixel != applicationPointPixel},
                  "effectiveContributionHint": ${sample.coverageAlpha > 0.0f && sample.sourceAlphaAfterCoverage > 0.0f}
                }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-399",
              "sceneId": "m60-f16-bounded-correction-shader-application-point-for399",
              "sourceSceneId": "m60-f16-bounded-runtime-correction-probe-for398",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-bounded-runtime-correction-probe-for398/m60-f16-bounded-runtime-correction-probe-for398.json",
              "sourceFinding": "global/kanvas/findings/for-398-applique-une-sonde-de-correction-m60-f16-bornee-mais-refuse-la-promotion-faute-de-gain",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "decision": "M60_F16_BOUNDED_CORRECTION_SHADER_APPLICATION_POINT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "correction-branch-not-hit",
                "correction-values-identical-before-blend",
                "correction-overwritten-by-stencil-cover-composition",
                "correction-point-still-ambiguous"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "boundedRuntimeCorrection": {
                  "guardId": "$FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "applicationPointDiagnostic": {
                  "guardId": "$FOR399_APPLICATION_POINT_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                }
              },
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16BoundedCorrectionApplicationPointSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
                "sampleCount": ${snapshot.samples.size}
              },
              "predicateProof": {
                "expectedPixelCount": ${expected.size},
                "observedPixelCount": ${observedPixels.size},
                "candidateBranchHitAllExpectedPixels": $branchHit,
                "falsePositiveCount": ${falsePositives.size},
                "falseNegativeCount": ${falseNegatives.size},
                "expectedPixels": [
            ${expected.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "observedPixels": [
            ${observedPixels.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "falsePositives": [
            ${falsePositives.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "falseNegatives": [
            ${falseNegatives.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ]
              },
              "shaderApplicationPoint": {
                "measurementScope": "M60 F16 AA stencil-cover bounded FOR-397 pixels only",
                "sideField": "inside=fs_inside, outside=fs_outside",
                "capturedFields": [
                  "side",
                  "candidateBranchReached",
                  "colorAfterApplyColorFilter",
                  "colorAfterApplyTargetColorspaceIfNeeded",
                  "colorSentToBlendBeforeQuantization",
                  "coverageAlphaUsed",
                  "sourceAlphaAfterCoverage",
                  "quantizedColorSentToBlend",
                  "effectiveContributionHint"
                ],
                "filteredTargetDifferentCount": $filteredTargetDifferentCount,
                "blendInputNonZeroCount": $blendInputNonZeroCount,
                "effectiveContributionHintCount": $effectiveContributionCount,
                "samples": [
            $samplesJson
                ]
              },
              "renderComparison": {
                "currentTotalResidual": $currentTotalResidual,
                "correctedTotalResidual": $correctedTotalResidual,
                "gainVsCurrent": ${currentTotalResidual - correctedTotalResidual},
                "currentMismatchPixels": ${currentResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "for398ChangedPixelCount": ${changedPixels.size},
                "for399DiagnosticChangedPixelCount": ${applicationPointChangedPixels.size},
                "for399DiagnosticMatchesFor398Correction": ${applicationPointChangedPixels.isEmpty()}
              },
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "for380BroadCorrectionReintroduced": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${reason.jsonString()},
              "nextStep": "Keep FOR-398 and FOR-399 opt-in only; use this application-point evidence before changing the correction formula or opening any promotion ticket.",
              "validationCommands": [
                "rtk python3 scripts/validate_for399_m60_f16_bounded_correction_shader_application_point.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for399-pycache-parent python3 -m py_compile scripts/validate_for399_m60_f16_bounded_correction_shader_application_point.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16CoverageStencilContributionMapJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        correctedGpu: SkBitmap,
        coverageStencilContributionMapGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        correctedResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        adapter: String,
    ): String {
        val expected = M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
        val expectedSet = expected.toSet()
        val rawSamples = snapshot.samples
        val samples = collapseM60F16CoverageStencilContributionMapSamples(rawSamples)
        val observedSamples = samples.filter { it.observedByShader }
        val effectiveSamples = samples.filter { it.hasEffectiveContribution() }
        val predicateEffectiveSamples = effectiveSamples.filter { it.belongsToFor397Predicate }
        val neighborEffectiveSamples = effectiveSamples.filterNot { it.belongsToFor397Predicate }
        val insideEffectiveCount = effectiveSamples.count { it.coverageSide == "inside" }
        val outsideEffectiveCount = effectiveSamples.count { it.coverageSide == "outside" }
        val dominantUsefulSide = when {
            insideEffectiveCount > outsideEffectiveCount -> "inside"
            outsideEffectiveCount > insideEffectiveCount -> "outside"
            insideEffectiveCount == 0 && outsideEffectiveCount == 0 -> "none"
            else -> "mixed"
        }
        val classification = when {
            neighborEffectiveSamples.isNotEmpty() -> "neighbor-contribution-candidates-found"
            predicateEffectiveSamples.isEmpty() && effectiveSamples.isNotEmpty() ->
                "inside-outside-side-mismatch-suspected"
            effectiveSamples.isEmpty() -> "predicate-window-zero-contribution"
            else -> "coverage-stencil-map-inconclusive"
        }
        val classificationReason = when (classification) {
            "neighbor-contribution-candidates-found" ->
                "The bounded radius-1 window contains non-FOR-397 samples with non-zero coverage, source alpha, and blend input."
            "inside-outside-side-mismatch-suspected" ->
                "Useful contribution exists in the window but not on the FOR-397 predicate samples, so the side or stencil predicate should be audited before any correction."
            "predicate-window-zero-contribution" ->
                "No observed sample in the bounded radius-1 FOR-397 window has non-zero coverage, source alpha, and blend input."
            else ->
                "The bounded window was captured, but the contribution and side signals do not identify a stable next correction predicate."
        }
        val nextStep = when (classification) {
            "neighbor-contribution-candidates-found" ->
                "Keep M60 F16 unpromoted; use the neighbor rows as diagnostic-only candidates in a separate bounded predicate ticket before any correction is applied."
            "predicate-window-zero-contribution" ->
                "Refuse this local FOR-397 window for correction and move the next probe to the stencil/cover pass that actually contributes source colour."
            "inside-outside-side-mismatch-suspected" ->
                "Open a minimal side-mismatch audit that compares the contributing side against the FOR-397 outside-side branch before testing another correction."
            else ->
                "Do not correct; add a narrower instrumentation point only if it can expose the contributing cover pass without changing default rendering."
        }
        val correctedChangedPixels = changedPixels(currentGpu, correctedGpu)
        val mapChangedPixels = changedPixels(correctedGpu, coverageStencilContributionMapGpu)
        val samplesJson = samples.joinToString(",\n") { sample ->
            val referencePixel = reference.getPixel(sample.x, sample.y)
            val currentPixel = currentGpu.getPixel(sample.x, sample.y)
            val correctedPixel = correctedGpu.getPixel(sample.x, sample.y)
            val mapPixel = coverageStencilContributionMapGpu.getPixel(sample.x, sample.y)
            """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "belongsToFor397Predicate": ${sample.belongsToFor397Predicate},
                  "side": ${sample.coverageSide.jsonString()},
                  "observedByShader": ${sample.observedByShader},
                  "candidateBranchReached": ${sample.candidateBranchReached},
                  "valid": ${sample.valid},
                  "coverageAlpha": ${String.format(Locale.US, "%.9f", sample.coverageAlpha)},
                  "sourceAlphaAfterCoverage": ${String.format(Locale.US, "%.9f", sample.sourceAlphaAfterCoverage)},
                  "colorSentToBlend": ${sample.colorSentToBlendBeforeQuantization.floatJson()},
                  "quantizedColorSentToBlend": ${sample.quantizedColorSentToBlend.floatJson()},
                  "colorAfterApplyColorFilter": ${sample.colorAfterColorFilter.floatJson()},
                  "colorAfterApplyTargetColorspaceIfNeeded": ${sample.colorAfterTargetColorspaceIfNeeded.floatJson()},
                  "effectiveContribution": ${sample.hasEffectiveContribution()},
                  "referenceRgba": ${rgbaJson(referencePixel)},
                  "currentRgba": ${rgbaJson(currentPixel)},
                  "correctedRgba": ${rgbaJson(correctedPixel)},
                  "coverageStencilContributionMapRgba": ${rgbaJson(mapPixel)},
                  "currentResidualVsReference": ${sampleResidual(referencePixel, currentPixel)},
                  "correctedResidualVsReference": ${sampleResidual(referencePixel, correctedPixel)},
                  "coverageStencilContributionMapResidualVsReference": ${sampleResidual(referencePixel, mapPixel)},
                  "deltaResidualCurrentVsReference": ${sampleResidual(referencePixel, currentPixel)},
                  "finalPixelChangedByFor398Correction": ${currentPixel != correctedPixel},
                  "finalPixelChangedByFor400Diagnostic": ${correctedPixel != mapPixel}
                }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-400",
              "sceneId": "m60-f16-coverage-stencil-contribution-map-for400",
              "sourceSceneId": "m60-f16-bounded-correction-shader-application-point-for399",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-bounded-correction-shader-application-point-for399/m60-f16-bounded-correction-shader-application-point-for399.json",
              "sourceFinding": "global/kanvas/findings/for-399-prouve-que-la-correction-m60-f16-bornee-atteint-le-shader-mais-ne-contribue-pas-aux-pixels-finaux",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "decision": "M60_F16_COVERAGE_STENCIL_CONTRIBUTION_MAP_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "neighbor-contribution-candidates-found",
                "predicate-window-zero-contribution",
                "inside-outside-side-mismatch-suspected",
                "coverage-stencil-map-inconclusive"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "boundedRuntimeCorrection": {
                  "guardId": "$FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "coverageStencilContributionMap": {
                  "guardId": "$FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                }
              },
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16CoverageStencilContributionMapSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
                "windowRadius": ${snapshot.windowRadius},
                "sampleLimit": ${snapshot.sampleLimit},
                "sampleCount": ${samples.size},
                "rawReadbackSampleCount": ${rawSamples.size},
                "observedSampleCount": ${observedSamples.size}
              },
              "predicateWindow": {
                "radius": ${snapshot.windowRadius},
                "strictSampleLimit": ${snapshot.sampleLimit},
                "for397PixelCount": ${expected.size},
                "for397Pixels": [
            ${expected.joinToString(",\n") { it.pixelJson().prependIndent("    ") }}
                ],
                "sampleCount": ${samples.size},
                "predicateSampleCount": ${samples.count { it.belongsToFor397Predicate }},
                "neighborSampleCount": ${samples.count { !it.belongsToFor397Predicate }}
              },
              "contributionSummary": {
                "effectiveContributionCount": ${effectiveSamples.size},
                "predicateEffectiveContributionCount": ${predicateEffectiveSamples.size},
                "neighborEffectiveContributionCount": ${neighborEffectiveSamples.size},
                "insideEffectiveContributionCount": $insideEffectiveCount,
                "outsideEffectiveContributionCount": $outsideEffectiveCount,
                "dominantUsefulSide": ${dominantUsefulSide.jsonString()},
                "for397ObservedCount": ${samples.count { it.belongsToFor397Predicate && it.observedByShader }},
                "for397CandidateBranchReachedCount": ${samples.count { it.belongsToFor397Predicate && it.candidateBranchReached }},
                "expectedFor397PixelsPreserved": ${samples.filter { it.belongsToFor397Predicate }.map { it.x to it.y }.toSet() == expectedSet}
              },
              "samples": [
            $samplesJson
              ],
              "renderComparison": {
                "currentTotalResidual": ${imageResidual(currentGpu, reference)},
                "correctedTotalResidual": ${imageResidual(correctedGpu, reference)},
                "currentMismatchPixels": ${currentResidualStats.mismatchPixels},
                "correctedMismatchPixels": ${correctedResidualStats.mismatchPixels},
                "for398ChangedPixelCount": ${correctedChangedPixels.size},
                "for400DiagnosticChangedPixelCount": ${mapChangedPixels.size},
                "for400DiagnosticMatchesFor398Correction": ${mapChangedPixels.isEmpty()}
              },
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "for380BroadCorrectionReintroduced": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${classificationReason.jsonString()},
              "nextStep": ${nextStep.jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for400_m60_f16_coverage_stencil_contribution_map.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for400-pycache-parent python3 -m py_compile scripts/validate_for400_m60_f16_coverage_stencil_contribution_map.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun SkWebGpuDevice.M60F16CoverageStencilContributionMapSample.hasEffectiveContribution(): Boolean =
        coverageAlpha > 0.0f &&
            sourceAlphaAfterCoverage > 0.0f &&
            colorSentToBlendBeforeQuantization.any { kotlin.math.abs(it) > 0.000001f }

    private fun m60F16FinalResidualOriginMapJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        adapter: String,
    ): String {
        val for397Pixels = M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
        val for397Set = for397Pixels.toSet()
        val for400Window = m60F16For400WindowPixels(snapshot.windowRadius)
        val for400SamplesByPixel = collapseM60F16CoverageStencilContributionMapSamples(snapshot.samples)
            .associateBy { it.x to it.y }
        val selected = finalResidualOriginPixels(reference, currentGpu)
            .take(FOR401_FINAL_RESIDUAL_ORIGIN_MAP_SAMPLE_LIMIT)
            .map { pixel ->
                val coordinate = pixel.x to pixel.y
                val for400Sample = for400SamplesByPixel[coordinate]
                val attribution = when {
                    for400Sample?.hasEffectiveContribution() == true -> "writtenByM60AaStencilCover"
                    else -> "readbackOnlyUnknown"
                }
                pixel.copy(
                    belongsToFor397Predicate = coordinate in for397Set,
                    belongsToFor400Window = coordinate in for400Window,
                    attributionCandidate = attribution,
                )
            }
        val selectedInFor397 = selected.count { it.belongsToFor397Predicate }
        val selectedInFor400 = selected.count { it.belongsToFor400Window }
        val writtenByM60 = selected.count { it.attributionCandidate == "writtenByM60AaStencilCover" }
        val writtenByOther = selected.count { it.attributionCandidate == "writtenByOtherPath" }
        val readbackUnknown = selected.count { it.attributionCandidate == "readbackOnlyUnknown" }
        val classification = when {
            selected.isNotEmpty() && selectedInFor400 == 0 -> "residual-carried-outside-for400-window"
            writtenByOther > 0 -> "residual-carried-by-other-draw-path"
            selected.isNotEmpty() && readbackUnknown == selected.size -> "residual-visible-only-at-final-readback"
            else -> "residual-origin-inconclusive"
        }
        val classificationReason = when (classification) {
            "residual-carried-outside-for400-window" ->
                "The deterministic top-residual final pixels are all outside the FOR-400 radius-1 predicate window; the FOR-400 AA stencil-cover samples therefore cannot carry this residual."
            "residual-carried-by-other-draw-path" ->
                "At least one selected residual pixel has available evidence pointing away from the M60 AA stencil-cover contribution window."
            "residual-visible-only-at-final-readback" ->
                "Selected residual pixels are visible in the final GPU/reference readback, but available FOR-400 pass metadata does not identify an effective writer."
            else ->
                "The bounded residual map preserves the final-pixel evidence but available pass metadata is insufficient for a stable writer attribution."
        }
        val nextStep = when (classification) {
            "residual-carried-outside-for400-window" ->
                "Instrument the actual final selected residual coordinates with a draw/pass write trace before attempting another shader correction."
            "residual-visible-only-at-final-readback" ->
                "Add the smallest readback-boundary or pass-write probe that can distinguish final readback packing from a prior draw output."
            else ->
                "Keep M60 F16 unsupported and add narrower writer attribution only around the selected final residual pixels."
        }
        val selectedJson = selected.joinToString(",\n") { finalResidualOriginPixelJson(it).prependIndent("    ") }
        val for397Json = for397Pixels.joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        val for400WindowJson = for400Window
            .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-401",
              "sceneId": "m60-f16-final-residual-origin-map-for401",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-stencil-contribution-map-for400/m60-f16-coverage-stencil-contribution-map-for400.json",
              "sourceMemory": "global/kanvas/findings/for-400-prouve-que-la-fenetre-coverage-stencil-m60-f16-autour-des-pixels-for-397-ne-contribue-pas",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "decision": "M60_F16_FINAL_RESIDUAL_ORIGIN_MAP_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "residual-carried-outside-for400-window",
                "residual-carried-by-other-draw-path",
                "residual-visible-only-at-final-readback",
                "residual-origin-inconclusive"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "boundedRuntimeCorrection": {
                  "guardId": "$FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "coverageStencilContributionMap": {
                  "guardId": "$FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                },
                "finalResidualOriginMap": {
                  "guardId": "$FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                }
              },
              "historicalEvidence": {
                "for397PixelCount": ${for397Pixels.size},
                "for400WindowRadius": ${snapshot.windowRadius},
                "for400WindowPixelCount": ${for400Window.size},
                "for400RawReadbackSampleCount": ${snapshot.samples.size},
                "for400EffectiveContributionCount": ${collapseM60F16CoverageStencilContributionMapSamples(snapshot.samples).count { it.hasEffectiveContribution() }},
                "for400Classification": "predicate-window-zero-contribution",
                "for400ResidualBefore": 62748,
                "for400ResidualAfter": 62748,
                "for400SupportClaim": false,
                "for400Promoted": false
              },
              "selectionPolicy": {
                "description": "Select final pixels with non-zero total residual, sort by total residual descending, then y ascending, then x ascending, and take a bounded fixed sample.",
                "residualMetric": "sum(abs(referenceRgba-currentGpuRgba)) over r,g,b,a bytes",
                "sampleLimit": $FOR401_FINAL_RESIDUAL_ORIGIN_MAP_SAMPLE_LIMIT,
                "deterministicTieBreak": ["residualTotal desc", "y asc", "x asc"],
                "selectedPixelCount": ${selected.size}
              },
              "for397PredicatePixels": [
            $for397Json
              ],
              "for400WindowPixels": [
            $for400WindowJson
              ],
              "residualOriginSummary": {
                "currentTotalResidual": ${imageResidual(currentGpu, reference)},
                "currentMismatchPixels": ${currentResidualStats.mismatchPixels},
                "selectedResidualTotal": ${selected.sumOf { it.residualTotal }},
                "selectedInFor397PredicateCount": $selectedInFor397,
                "selectedInFor400WindowCount": $selectedInFor400,
                "selectedOutsideFor400WindowCount": ${selected.size - selectedInFor400},
                "writtenByM60AaStencilCoverCount": $writtenByM60,
                "writtenByOtherPathCount": $writtenByOther,
                "readbackOnlyUnknownCount": $readbackUnknown
              },
              "candidateAttribution": {
                "availableRuntimeWriterEvidence": "FOR-400 radius-1 M60 AA stencil-cover contribution samples only",
                "writtenByM60AaStencilCoverRule": "selected pixel has a FOR-400 sample with non-zero coverage, source alpha, and blend input",
                "writtenByOtherPathRule": "reserved for future draw/pass writer evidence; unavailable in this bounded artifact",
                "readbackOnlyUnknownRule": "final residual is visible, but no available FOR-400 effective contribution sample identifies the writer"
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for380BroadCorrectionReintroduced": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${classificationReason.jsonString()},
              "nextStep": ${nextStep.jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for401_m60_f16_final_residual_origin_map.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for401-pycache-agent python3 -m py_compile scripts/validate_for401_m60_f16_final_residual_origin_map.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16PassWriteProbeJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        adapter: String,
    ): String {
        val for397Pixels = M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
        val for397Set = for397Pixels.toSet()
        val for400Window = m60F16For400WindowPixels(snapshot.windowRadius)
        val for400SamplesByPixel = collapseM60F16CoverageStencilContributionMapSamples(snapshot.samples)
            .associateBy { it.x to it.y }
        val selected = finalResidualOriginPixels(reference, currentGpu)
            .take(FOR401_FINAL_RESIDUAL_ORIGIN_MAP_SAMPLE_LIMIT)
            .map { pixel ->
                val coordinate = pixel.x to pixel.y
                val attribution = when {
                    for400SamplesByPixel[coordinate]?.hasEffectiveContribution() == true ->
                        "writtenByM60AaStencilCover"
                    else -> "readbackOnlyUnknown"
                }
                pixel.copy(
                    belongsToFor397Predicate = coordinate in for397Set,
                    belongsToFor400Window = coordinate in for400Window,
                    attributionCandidate = attribution,
                ) to for400SamplesByPixel[coordinate]
            }
        val selectedPixels = selected.map { it.first }
        val selectedInFor397 = selectedPixels.count { it.belongsToFor397Predicate }
        val selectedInFor400 = selectedPixels.count { it.belongsToFor400Window }
        val for400EffectiveSelected = selected.count { (_, sample) -> sample?.hasEffectiveContribution() == true }
        val classification = "pass-write-probe-inconclusive"
        val classificationReason =
            "The FOR-402 opt-in artifact preserves the 16 FOR-401 final residual coordinates, " +
                "but Kanvas currently exposes only the FOR-400 radius-1 M60 AA stencil-cover " +
                "contribution snapshot, not a per-draw framebuffer write hook before final readback. " +
                "FOR-400 contribution evidence is retained as context only and is not treated as " +
                "proof of a pass write."
        val nextStep =
            "Instrument SkWebGpuDevice render-pass draw submission or the post-draw/pre-readback " +
                "texture boundary for these 16 coordinates, recording draw id and pipeline family " +
                "before final readback packing."
        val selectedJson = selected.joinToString(",\n") { (pixel, sample) ->
            passWriteProbePixelJson(pixel, sample).prependIndent("    ")
        }
        val for397Json = for397Pixels.joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        val for400WindowJson = for400Window
            .sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .joinToString(",\n") { it.pixelJson().prependIndent("    ") }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-402",
              "sceneId": "m60-f16-pass-write-probe-for402",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
              "sourceMemory": "global/kanvas/findings/for-401-localise-le-residu-m60-f16-au-readback-final-sans-writer-identifie",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMethod": "writeM60F16PassWriteProbe",
              "decision": "M60_F16_PASS_WRITE_PROBE_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "final-residual-written-by-m60-aa-stencil-cover",
                "final-residual-written-by-other-draw",
                "final-residual-not-observed-before-readback",
                "pass-write-probe-inconclusive"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "boundedRuntimeCorrection": {
                  "guardId": "$FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "coverageStencilContributionMap": {
                  "guardId": "$FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                },
                "finalResidualOriginMap": {
                  "guardId": "$FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "passWriteProbe": {
                  "guardId": "$FOR402_PASS_WRITE_PROBE_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                }
              },
              "sourceFinding": {
                "linear": "FOR-401",
                "classification": "residual-visible-only-at-final-readback",
                "selectedResidualTotal": 1560,
                "writtenByM60AaStencilCover": 0,
                "writtenByOtherPath": 0,
                "readbackOnlyUnknown": 16,
                "supportClaim": false,
                "promoted": false
              },
              "probeScope": {
                "sampleSource": "exact FOR-401 final residual selection",
                "sampleLimit": $FOR401_FINAL_RESIDUAL_ORIGIN_MAP_SAMPLE_LIMIT,
                "deterministicTieBreak": ["residualTotal desc", "y asc", "x asc"],
                "directPassWriteInstrumentationAvailable": false,
                "availableWriterEvidence": "SkWebGpuDevice.m60F16CoverageStencilContributionMapSnapshot() FOR-400 radius-1 M60 AA stencil-cover contribution samples",
                "missingInstrumentationPoint": "SkWebGpuDevice render-pass draw submission or post-draw/pre-readback texture boundary with draw id and pipeline family per sampled coordinate"
              },
              "for397PredicatePixels": [
            $for397Json
              ],
              "for400WindowPixels": [
            $for400WindowJson
              ],
              "passWriteSummary": {
                "currentTotalResidual": ${imageResidual(currentGpu, reference)},
                "currentMismatchPixels": ${currentResidualStats.mismatchPixels},
                "selectedPixelCount": ${selectedPixels.size},
                "selectedResidualTotal": ${selectedPixels.sumOf { it.residualTotal }},
                "selectedInFor397PredicateCount": $selectedInFor397,
                "selectedInFor400WindowCount": $selectedInFor400,
                "selectedOutsideFor400WindowCount": ${selectedPixels.size - selectedInFor400},
                "m60AaStencilCoverWriteCount": 0,
                "otherDrawWriteCount": 0,
                "notObservedBeforeReadbackCount": 0,
                "passWriteProbeInconclusiveCount": ${selectedPixels.size},
                "for400EffectiveSelectedContributionCount": $for400EffectiveSelected
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for380BroadCorrectionReintroduced": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${classificationReason.jsonString()},
              "nextStep": ${nextStep.jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py",
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for402-pycache-agent python3 -m py_compile scripts/validate_for402_m60_f16_pass_write_probe.py",
                "rtk git diff --check",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true -Dkanvas.webgpu.m60F16PassWriteProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun passWriteProbePixelJson(
        pixel: FinalResidualOriginPixel,
        for400Sample: SkWebGpuDevice.M60F16CoverageStencilContributionMapSample?,
    ): String {
        val hasFor400EffectiveContribution = for400Sample?.hasEffectiveContribution() == true
        val inspectedEvidence = if (for400Sample == null) {
            "no FOR-400 radius-1 sample for this coordinate"
        } else {
            "FOR-400 radius-1 M60 AA stencil-cover contribution sample"
        }
        val missingReason = if (hasFor400EffectiveContribution) {
            "FOR-400 reported an effective M60 AA stencil-cover contribution for this coordinate."
        } else {
            "No direct per-draw framebuffer write hook is available before final readback; FOR-400 contribution evidence alone cannot name a writer."
        }
        return """
            {
              "x": ${pixel.x},
              "y": ${pixel.y},
              "currentGpuRgba": ${rgbaJson(pixel.currentGpu)},
              "referenceRgba": ${rgbaJson(pixel.reference)},
              "residualByChannel": ${channelErrorJson(pixel.residualByChannel)},
              "residualTotal": ${pixel.residualTotal},
              "belongsToFor397Predicate": ${pixel.belongsToFor397Predicate},
              "belongsToFor400Window": ${pixel.belongsToFor400Window},
              "for401AttributionCandidate": ${pixel.attributionCandidate.jsonString()},
              "classification": "pass-write-probe-inconclusive",
              "observedWrite": {
                "observed": false,
                "available": false,
                "m60AaStencilCoverWriteObserved": false,
                "otherDrawWriteObserved": false,
                "drawId": null,
                "pipelineFamily": null,
                "inspectedEvidence": ${inspectedEvidence.jsonString()},
                "for400SamplePresent": ${for400Sample != null},
                "for400EffectiveContribution": $hasFor400EffectiveContribution,
                "reason": ${missingReason.jsonString()}
              }
            }
        """.trimIndent()
    }

    private fun m60F16AaStencilCoverRuntimeHookJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSnapshot,
        adapter: String,
    ): String {
        val selected = for401SelectedResidualOriginPixels()
        val samplesByPixel = snapshot.events
            .flatMap { event ->
                event.samples.map { sample -> (sample.x to sample.y) to (event to sample) }
            }
            .groupBy({ it.first }, { it.second })
        val selectedClassifications = selected.map { pixel ->
            val inspected = samplesByPixel[pixel.x to pixel.y].orEmpty()
            when {
                inspected.any { (_, sample) ->
                    sample.classification == "aa-stencil-cover-post-pass-observed"
                } -> "aa-stencil-cover-post-pass-observed"
                inspected.any { (_, sample) ->
                    sample.classification == "aa-stencil-cover-post-pass-readback-blocked"
                } -> "aa-stencil-cover-post-pass-readback-blocked"
                inspected.any { (_, sample) ->
                    sample.classification == "aa-stencil-cover-pass-not-targeting-coordinate"
                } -> "aa-stencil-cover-pass-not-targeting-coordinate"
                else -> "aa-stencil-cover-runtime-hook-inconclusive"
            }
        }
        val classification = when {
            selectedClassifications.any { it == "aa-stencil-cover-post-pass-observed" } ->
                "aa-stencil-cover-post-pass-observed"
            selectedClassifications.any { it == "aa-stencil-cover-post-pass-readback-blocked" } ->
                "aa-stencil-cover-post-pass-readback-blocked"
            selectedClassifications.all { it == "aa-stencil-cover-pass-not-targeting-coordinate" } ->
                "aa-stencil-cover-pass-not-targeting-coordinate"
            else -> "aa-stencil-cover-runtime-hook-inconclusive"
        }
        val selectedJson = selected.zip(selectedClassifications).joinToString(",\n") { (pixel, pixelClass) ->
            aaStencilCoverRuntimeHookPixelJson(
                pixel = pixel,
                classification = pixelClass,
                inspected = samplesByPixel[pixel.x to pixel.y].orEmpty(),
            ).prependIndent("    ")
        }
        val eventsJson = snapshot.events.joinToString(",\n") { event ->
            aaStencilCoverRuntimeHookEventJson(event).prependIndent("    ")
        }
        val classificationReason = when (classification) {
            "aa-stencil-cover-post-pass-observed" ->
                "A bounded post-pass sample was observed after StencilCoverAaPolygonDraw."
            "aa-stencil-cover-post-pass-readback-blocked" ->
                "The opt-in runtime hook reached the StencilCoverAaPolygonDraw post-cover boundary for the FOR-401 coordinates, but no post-pass texture readback is available before final present/readback."
            "aa-stencil-cover-pass-not-targeting-coordinate" ->
                "The opt-in runtime hook ran, but the inspected StencilCoverAaPolygonDraw passes did not target the FOR-401 coordinates."
            else ->
                "The opt-in runtime hook did not produce enough StencilCoverAaPolygonDraw evidence for the FOR-401 coordinates."
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-404",
              "sceneId": "m60-f16-aa-stencil-cover-runtime-hook-for404",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-pass-write-hook-for403/m60-f16-direct-pass-write-hook-for403.json",
              "sourceArtifacts": {
                "for401": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
                "for402": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-pass-write-probe-for402/m60-f16-pass-write-probe-for402.json",
                "for403": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-pass-write-hook-for403/m60-f16-direct-pass-write-hook-for403.json"
              },
              "sourceMemory": "global/kanvas/findings/for-403-refuse-le-hook-direct-pass-write-m60-f16-sans-frontiere-post-draw-pre-readback",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMethod": "writeM60F16AaStencilCoverRuntimeHook",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "decision": "M60_F16_AA_STENCIL_COVER_RUNTIME_HOOK_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "aa-stencil-cover-post-pass-observed",
                "aa-stencil-cover-post-pass-readback-blocked",
                "aa-stencil-cover-pass-not-targeting-coordinate",
                "aa-stencil-cover-runtime-hook-inconclusive"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "bandMetadataTransport": {
                  "guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "fragmentLaneDiagnostic": {
                  "guardId": "$M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "coverageStencilContributionMap": {
                  "guardId": "$FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false,
                  "evidencePolicy": "context-only-not-direct-write-proof"
                },
                "finalResidualOriginMap": {
                  "guardId": "$FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "passWriteProbe": {
                  "guardId": "$FOR402_PASS_WRITE_PROBE_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR402_PASS_WRITE_PROBE_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false
                },
                "aaStencilCoverPostPassRuntimeHook": {
                  "guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY",
                  "enabledForEvidenceRun": ${snapshot.enabled},
                  "enabledByDefault": false
                }
              },
              "sourceContext": {
                "for403Classification": "direct-pass-write-hook-inconclusive",
                "for403Finding": "global/kanvas/findings/for-403-refuse-le-hook-direct-pass-write-m60-f16-sans-frontiere-post-draw-pre-readback",
                "for400EvidencePolicy": "context-only-not-direct-write-proof",
                "for401Classification": "residual-visible-only-at-final-readback",
                "for401CurrentTotalResidual": 62748,
                "for401SelectedResidualTotal": 1560,
                "for401SelectedPixelCount": 16,
                "for402Classification": "pass-write-probe-inconclusive"
              },
              "runtimeHook": {
                "api": "SkWebGpuDevice.m60F16AaStencilCoverPostPassRuntimeHookSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "requestedBoundary": ${snapshot.requestedBoundary.jsonString()},
                "observedBoundary": ${snapshot.observedBoundary.jsonString()},
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "sampleLimit": ${snapshot.sampleLimit},
                "eventCount": ${snapshot.events.size},
                "postPassReadbackAvailable": false,
                "events": [
            $eventsJson
                ]
              },
              "postPassSummary": {
                "currentTotalResidual": 62748,
                "currentMismatchPixels": 1615,
                "selectedPixelCount": ${selected.size},
                "selectedResidualTotal": ${selected.sumOf { it.residualTotal }},
                "postPassObservedCount": ${selectedClassifications.count { it == "aa-stencil-cover-post-pass-observed" }},
                "postPassReadbackBlockedCount": ${selectedClassifications.count { it == "aa-stencil-cover-post-pass-readback-blocked" }},
                "passNotTargetingCoordinateCount": ${selectedClassifications.count { it == "aa-stencil-cover-pass-not-targeting-coordinate" }},
                "runtimeHookInconclusiveCount": ${selectedClassifications.count { it == "aa-stencil-cover-runtime-hook-inconclusive" }}
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for380BroadCorrectionReintroduced": false,
                "for400UsedAsDirectProof": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${classificationReason.jsonString()},
              "nextStep": "A real post-pass color sample still requires a WebGPU-safe texture copy/readback boundary between the AA stencil-cover render pass and final present/readback.",
              "validationCommands": [
                "rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py",
                "rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py",
                "rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true -Dkanvas.webgpu.m60F16PassWriteProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun aaStencilCoverRuntimeHookEventJson(
        event: SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookEvent,
    ): String {
        val samplesJson = event.samples.joinToString(",\n") { sample ->
            """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "postPassReadbackAvailable": ${sample.postPassReadbackAvailable},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "samples": [
            $samplesJson
              ]
            }
        """.trimIndent()
    }

    private fun aaStencilCoverRuntimeHookPixelJson(
        pixel: FinalResidualOriginPixel,
        classification: String,
        inspected: List<Pair<SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookEvent, SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSample>>,
    ): String {
        val targetingEvents = inspected.filter { (_, sample) -> sample.targetWithinScissor }
        val inspectedEventsJson = inspected.joinToString(",\n") { (event, sample) ->
            """
                {
                  "drawIndex": ${event.drawIndex},
                  "pipelineFamily": ${event.pipelineFamily.jsonString()},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "postPassReadbackAvailable": ${sample.postPassReadbackAvailable},
                  "classification": ${sample.classification.jsonString()}
                }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "x": ${pixel.x},
              "y": ${pixel.y},
              "currentGpuRgba": ${rgbaJson(pixel.currentGpu)},
              "referenceRgba": ${rgbaJson(pixel.reference)},
              "residualByChannel": ${channelErrorJson(pixel.residualByChannel)},
              "residualTotal": ${pixel.residualTotal},
              "for401AttributionCandidate": "readbackOnlyUnknown",
              "classification": ${classification.jsonString()},
              "postPass": {
                "observed": false,
                "readbackAvailable": false,
                "targetingStencilCoverAaDrawCount": ${targetingEvents.size},
                "inspectedStencilCoverAaDrawCount": ${inspected.size},
                "drawIds": [${targetingEvents.joinToString(", ") { it.first.drawIndex.toString() }}],
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "for400ContributionEvidenceUsedAsProof": false,
                "reason": "FOR-404 inspected the StencilCoverAaPolygonDraw post-cover runtime boundary; no color readback exists at that boundary, so FOR-400 remains contextual only.",
                "inspectedEvents": [
            $inspectedEventsJson
                ]
              }
            }
        """.trimIndent()
    }

    private fun m60F16AaStencilCoverPostPassReadbackJson(
        reference: SkBitmap,
        currentGpu: SkBitmap,
        currentResidualStats: StrokeResidualStats,
        runtimeHookSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSnapshot,
        readbackSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ): String {
        val selected = for401SelectedResidualOriginPixels()
        val samplesByPixel = readbackSnapshot.events
            .flatMap { event ->
                event.samples.map { sample -> (sample.x to sample.y) to (event to sample) }
            }
            .groupBy({ it.first }, { it.second })
        fun selectedSample(pixel: FinalResidualOriginPixel):
            Pair<SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackEvent, SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample>? {
            val inspected = samplesByPixel[pixel.x to pixel.y].orEmpty()
            return inspected.lastOrNull { (_, sample) ->
                sample.classification == "aa-stencil-cover-post-pass-color-observed"
            } ?: inspected.firstOrNull { (_, sample) ->
                sample.classification == "aa-stencil-cover-post-pass-format-unsupported"
            } ?: inspected.firstOrNull { (_, sample) ->
                sample.classification == "aa-stencil-cover-post-pass-copy-blocked"
            } ?: inspected.firstOrNull { (_, sample) ->
                sample.classification == "aa-stencil-cover-post-pass-readback-inconclusive"
            }
        }
        val selectedClassifications = selected.map { pixel ->
            selectedSample(pixel)?.second?.classification
                ?: "aa-stencil-cover-post-pass-readback-inconclusive"
        }
        val classification = when {
            selectedClassifications.any { it == "aa-stencil-cover-post-pass-color-observed" } ->
                "aa-stencil-cover-post-pass-color-observed"
            selectedClassifications.any { it == "aa-stencil-cover-post-pass-format-unsupported" } ->
                "aa-stencil-cover-post-pass-format-unsupported"
            selectedClassifications.any { it == "aa-stencil-cover-post-pass-copy-blocked" } ->
                "aa-stencil-cover-post-pass-copy-blocked"
            else -> "aa-stencil-cover-post-pass-readback-inconclusive"
        }
        val selectedJson = selected.zip(selectedClassifications).joinToString(",\n") { (pixel, pixelClass) ->
            aaStencilCoverPostPassReadbackPixelJson(
                pixel = pixel,
                classification = pixelClass,
                inspected = samplesByPixel[pixel.x to pixel.y].orEmpty(),
            ).prependIndent("    ")
        }
        val eventsJson = readbackSnapshot.events.joinToString(",\n") { event ->
            aaStencilCoverPostPassReadbackEventJson(event).prependIndent("    ")
        }
        val classificationReason = when (classification) {
            "aa-stencil-cover-post-pass-color-observed" ->
                "FOR-405 observed deterministic post-pass colors by sampling the intermediate RGBA16Float texture after StencilCoverAaPolygonDraw and before final present/readback."
            "aa-stencil-cover-post-pass-format-unsupported" ->
                "The localized runtime attempt reached StencilCoverAaPolygonDraw, but the intermediate format was not the M60 F16 RGBA16Float target required by this diagnostic."
            "aa-stencil-cover-post-pass-copy-blocked" ->
                "The localized runtime attempt reached StencilCoverAaPolygonDraw, but WebGPU copy/map readback failed before colors could be decoded."
            else ->
                "The localized runtime attempt ran, but did not produce enough in-bounds post-pass samples to identify colors for the FOR-401 coordinates."
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-405",
              "sceneId": "m60-f16-aa-stencil-cover-post-pass-readback-for405",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-runtime-hook-for404/m60-f16-aa-stencil-cover-runtime-hook-for404.json",
              "sourceArtifacts": {
                "for401": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
                "for404": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-runtime-hook-for404/m60-f16-aa-stencil-cover-runtime-hook-for404.json"
              },
              "sourceMemory": "global/kanvas/findings/for-404-ajoute-un-hook-runtime-borne-aa-stencil-cover-mais-bloque-sur-le-readback-post-pass",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMethod": "writeM60F16AaStencilCoverPostPassReadback",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "decision": "M60_F16_AA_STENCIL_COVER_POST_PASS_READBACK_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "aa-stencil-cover-post-pass-color-observed",
                "aa-stencil-cover-post-pass-format-unsupported",
                "aa-stencil-cover-post-pass-copy-blocked",
                "aa-stencil-cover-post-pass-readback-inconclusive"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "guards": {
                "experimentalStrokeRenderer": {
                  "guardId": "$EXPERIMENTAL_RENDER_PROPERTY",
                  "enabledForEvidenceRun": true,
                  "enabledByDefault": false
                },
                "aaStencilCoverPostPassRuntimeHook": {
                  "guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY",
                  "enabledForEvidenceRun": ${runtimeHookSnapshot.enabled},
                  "enabledByDefault": false
                },
                "aaStencilCoverPostPassReadback": {
                  "guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY",
                  "enabledForEvidenceRun": ${readbackSnapshot.enabled},
                  "enabledByDefault": false
                },
                "coverageStencilContributionMap": {
                  "guardId": "$FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY",
                  "enabledForEvidenceRun": ${System.getProperty(FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY, "false").toBoolean()},
                  "enabledByDefault": false,
                  "evidencePolicy": "context-only-not-direct-write-proof"
                }
              },
              "sourceContext": {
                "for404Classification": "aa-stencil-cover-post-pass-readback-blocked",
                "for404RuntimeApi": "SkWebGpuDevice.m60F16AaStencilCoverPostPassRuntimeHookSnapshot()",
                "for404RuntimeEventCount": ${runtimeHookSnapshot.events.size},
                "for400EvidencePolicy": "context-only-not-direct-write-proof",
                "for400UsedAsDirectProof": false,
                "for401Classification": "residual-visible-only-at-final-readback",
                "for401CurrentTotalResidual": 62748,
                "for401CurrentMismatchPixels": 1615,
                "for401SelectedResidualTotal": 1560,
                "for401SelectedPixelCount": 16
              },
              "runtimeReadback": {
                "api": "SkWebGpuDevice.m60F16AaStencilCoverPostPassReadbackSnapshot()",
                "propertyName": ${readbackSnapshot.propertyName.jsonString()},
                "enabled": ${readbackSnapshot.enabled},
                "requestedBoundary": ${readbackSnapshot.requestedBoundary.jsonString()},
                "observedBoundary": ${readbackSnapshot.observedBoundary.jsonString()},
                "diagnosticShader": ${readbackSnapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${readbackSnapshot.pipelineLayout.jsonString()},
                "intermediateFormat": ${readbackSnapshot.intermediateFormat.jsonString()},
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "sampleLimit": ${readbackSnapshot.sampleLimit},
                "eventCount": ${readbackSnapshot.events.size},
                "postPassReadbackAvailable": ${selectedClassifications.any { it == "aa-stencil-cover-post-pass-color-observed" }},
                "events": [
            $eventsJson
                ]
              },
              "postPassSummary": {
                "currentTotalResidual": 62748,
                "currentMismatchPixels": 1615,
                "selectedPixelCount": ${selected.size},
                "selectedResidualTotal": ${selected.sumOf { it.residualTotal }},
                "postPassObservedCount": ${selectedClassifications.count { it == "aa-stencil-cover-post-pass-color-observed" }},
                "postPassFormatUnsupportedCount": ${selectedClassifications.count { it == "aa-stencil-cover-post-pass-format-unsupported" }},
                "postPassCopyBlockedCount": ${selectedClassifications.count { it == "aa-stencil-cover-post-pass-copy-blocked" }},
                "postPassReadbackInconclusiveCount": ${selectedClassifications.count { it == "aa-stencil-cover-post-pass-readback-inconclusive" }}
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for380BroadCorrectionReintroduced": false,
                "for400UsedAsDirectProof": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": ${classificationReason.jsonString()},
              "validationCommands": [
                "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
                "rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py",
                "rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true -Dkanvas.webgpu.m60F16PassWriteProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun aaStencilCoverPostPassReadbackEventJson(
        event: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackEvent,
    ): String {
        val samplesJson = event.samples.joinToString(",\n") { sample ->
            """
                {
                  "x": ${sample.x},
                  "y": ${sample.y},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "readbackAttempted": ${sample.readbackAttempted},
                  "readbackAvailable": ${sample.readbackAvailable},
                  "observedRgbaFloat": ${sample.observedRgbaFloat?.floatJson() ?: "null"},
                  "observedRgba8": ${sample.observedRgba8?.let { intArrayJson(it) } ?: "null"},
                  "classification": ${sample.classification.jsonString()},
                  "reason": ${sample.reason.jsonString()}
                }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "copyAttempted": ${event.copyAttempted},
              "copySucceeded": ${event.copySucceeded},
              "copyFailureReason": ${event.copyFailureReason?.jsonString() ?: "null"},
              "samples": [
            $samplesJson
              ]
            }
        """.trimIndent()
    }

    private fun aaStencilCoverPostPassReadbackPixelJson(
        pixel: FinalResidualOriginPixel,
        classification: String,
        inspected: List<Pair<SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackEvent, SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample>>,
    ): String {
        val observed = inspected.lastOrNull { (_, sample) ->
            sample.classification == "aa-stencil-cover-post-pass-color-observed"
        }
        val targetingEvents = inspected.filter { (_, sample) -> sample.targetWithinScissor }
        val inspectedEventsJson = inspected.joinToString(",\n") { (event, sample) ->
            """
                {
                  "drawIndex": ${event.drawIndex},
                  "pipelineFamily": ${event.pipelineFamily.jsonString()},
                  "targetWithinScissor": ${sample.targetWithinScissor},
                  "readbackAttempted": ${sample.readbackAttempted},
                  "readbackAvailable": ${sample.readbackAvailable},
                  "classification": ${sample.classification.jsonString()}
                }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "x": ${pixel.x},
              "y": ${pixel.y},
              "currentGpuRgba": ${rgbaJson(pixel.currentGpu)},
              "referenceRgba": ${rgbaJson(pixel.reference)},
              "residualByChannel": ${channelErrorJson(pixel.residualByChannel)},
              "residualTotal": ${pixel.residualTotal},
              "for401AttributionCandidate": "readbackOnlyUnknown",
              "classification": ${classification.jsonString()},
              "postPass": {
                "observed": ${observed != null},
                "readbackAvailable": ${observed != null},
                "observedRgbaFloat": ${observed?.second?.observedRgbaFloat?.floatJson() ?: "null"},
                "observedRgba8": ${observed?.second?.observedRgba8?.let { intArrayJson(it) } ?: "null"},
                "targetingStencilCoverAaDrawCount": ${targetingEvents.size},
                "inspectedStencilCoverAaDrawCount": ${inspected.size},
                "drawIds": [${targetingEvents.joinToString(", ") { it.first.drawIndex.toString() }}],
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "for400ContributionEvidenceUsedAsProof": false,
                "reason": ${((observed?.second?.reason) ?: "FOR-405 did not observe a post-pass color for this coordinate.").jsonString()},
                "inspectedEvents": [
            $inspectedEventsJson
                ]
              }
            }
        """.trimIndent()
    }

    private fun intArrayJson(values: IntArray): String =
        values.joinToString(prefix = "[", postfix = "]")

    private fun m60F16AaStencilCoverContributionIsolationJson(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        boundedRuntimeCorrectionProbeEnabledForEvidenceRun: Boolean,
        adapter: String,
    ): String {
        val selected = M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS
        val samples = snapshot.events.flatMap { event -> event.samples.map { sample -> event to sample } }
        val observedSamples = samples.count { (_, sample) -> sample.shaderObserved }
        val missingFramebufferState = samples.count { (_, sample) ->
            sample.shaderObserved &&
                listOf(sample.dstBeforeRgbaFloat, sample.expectedSourceOverRgbaFloat, sample.dstAfterRgbaFloat)
                    .any { it == null }
        }
        val postPassByPixel = postPassSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to sample } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.lastOrNull { it.readbackAvailable } }
        val classification = when {
            observedSamples > 0 && missingFramebufferState > 0 ->
                "per-subdraw-framebuffer-state-unavailable"
            observedSamples > 0 -> "per-subdraw-inputs-captured"
            snapshot.enabled -> "per-subdraw-hook-no-samples"
            else -> "per-subdraw-hook-disabled"
        }
        val selectedJson = selected.joinToString(",\n") { (x, y) ->
            val pixelSamples = samples
                .filter { (_, sample) -> sample.x == x && sample.y == y }
                .sortedWith(
                    compareBy<Pair<
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                        >> { it.first.drawIndex }.thenBy { it.second.subdrawOrdinal },
                )
            val postPass = postPassByPixel[x to y]
            val pixelClass = when {
                pixelSamples.any { (_, sample) -> sample.shaderObserved && sample.missingFields.isNotEmpty() } ->
                    "per-subdraw-framebuffer-state-unavailable"
                pixelSamples.any { (_, sample) -> sample.shaderObserved } ->
                    "per-subdraw-inputs-captured"
                else -> "per-subdraw-sample-not-observed"
            }
            val subdrawJson = pixelSamples.joinToString(",\n") { (event, sample) ->
                aaStencilCoverContributionIsolationSampleJson(event, sample).prependIndent("      ")
            }
            """
            {
              "x": $x,
              "y": $y,
              "classification": ${pixelClass.jsonString()},
              "postPassDstAfterRgbaFloat": ${postPass?.observedRgbaFloat.floatArrayOrNullJson()},
              "postPassDstAfterRgba8": ${postPass?.observedRgba8.intArrayOrNullJson()},
              "subdrawCount": ${pixelSamples.size},
              "shaderObservedSubdrawCount": ${pixelSamples.count { (_, sample) -> sample.shaderObserved }},
              "perSubdraw": [
            $subdrawJson
              ]
            }
            """.trimIndent().prependIndent("    ")
        }
        val eventsJson = snapshot.events.joinToString(",\n") { event ->
            """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "scissor": ${intArrayJson(event.scissor)},
              "edgeCount": ${event.edgeCount},
              "coverVertexCount": ${event.coverVertexCount},
              "sampleCount": ${event.samples.size}
            }
            """.trimIndent().prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-408",
              "sceneId": "m60-f16-aa-stencil-cover-per-subdraw-hook-for408",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-408-m60-f16-hook-per-subdraw-aa-stencil-cover-contribution-isolation",
              "sourceMemory": {
                "for407": "global/kanvas/findings/for-407-formalise-le-manque-de-donnees-per-subdraw-pour-isoler-la-cause-m60-f16"
              },
              "sourceArtifacts": {
                "for401": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
                "for405": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json",
                "for406": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-post-pass-reference-comparison-for406/m60-f16-post-pass-reference-comparison-for406.json",
                "for407": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-contribution-isolation-for407/m60-f16-aa-stencil-cover-contribution-isolation-for407.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "decision": "M60_F16_AA_STENCIL_COVER_PER_SUBDRAW_HOOK_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "coverage-aa-wrong",
                "source-color-wrong",
                "blend-source-over-wrong",
                "draw-order-or-accumulation-wrong",
                "per-subdraw-framebuffer-state-unavailable",
                "per-subdraw-inputs-captured",
                "per-subdraw-hook-no-samples",
                "per-subdraw-hook-disabled"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "guards": {
                "experimentalStrokeRenderer": {"guardId": "$EXPERIMENTAL_RENDER_PROPERTY", "enabledForEvidenceRun": true, "enabledByDefault": false},
                "bandMetadataTransport": {"guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY", "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()}, "enabledByDefault": false},
                "boundedRuntimeCorrection": {"guardId": "$FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY", "enabledForEvidenceRun": $boundedRuntimeCorrectionProbeEnabledForEvidenceRun, "enabledByDefault": false, "usage": "opt-in diagnostic transport for FOR-408; not a default rendering correction"},
                "postPassReadback": {"guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY", "enabledForEvidenceRun": ${postPassSnapshot.enabled}, "enabledByDefault": false},
                "contributionIsolation": {"guardId": "$FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY", "enabledForEvidenceRun": ${snapshot.enabled}, "enabledByDefault": false}
              },
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16AaStencilCoverContributionIsolationSnapshot()",
                "propertyName": ${snapshot.propertyName.jsonString()},
                "enabled": ${snapshot.enabled},
                "requestedBoundary": ${snapshot.requestedBoundary.jsonString()},
                "observedBoundary": ${snapshot.observedBoundary.jsonString()},
                "diagnosticShader": ${snapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${snapshot.pipelineLayout.jsonString()},
                "sampleLimit": ${snapshot.sampleLimit},
                "eventCount": ${snapshot.events.size},
                "events": [
            $eventsJson
                ]
              },
              "isolationSummary": {
                "selectedPixelCount": ${selected.size},
                "rawRuntimeEventCount": ${snapshot.events.size},
                "rawRuntimeSampleCount": ${samples.size},
                "shaderObservedSubdrawCount": $observedSamples,
                "missingFramebufferStateSubdrawCount": $missingFramebufferState,
                "postPassObservedPixelCount": ${postPassByPixel.values.count { it != null }},
                "for400EvidencePolicy": "context-only-not-direct-write-proof",
                "for400UsedAsDirectProof": false
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for400UsedAsDirectProof": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": "FOR-408 captures shader-side source color and coverage per inside/outside subdraw where the bounded M60 F16 fragment hook observes a selected FOR-401 coordinate. WebGPU fixed-function blending still does not expose per-subdraw dstBefore/dstAfter framebuffer state or an independent source-over replay input at this fragment boundary.",
              "validationCommands": [
                "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
                "rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py",
                "rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
                "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
                "rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py",
                "rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun aaStencilCoverContributionIsolationSampleJson(
        event: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
        sample: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
    ): String {
        val missingJson = sample.missingFields.joinToString(", ") { it.jsonString() }
        return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "coordinate": {"x": ${sample.x}, "y": ${sample.y}},
              "subdrawOrdinal": ${sample.subdrawOrdinal},
              "subdrawRole": ${sample.subdrawRole.jsonString()},
              "targetWithinScissor": ${sample.targetWithinScissor},
              "shaderObserved": ${sample.shaderObserved},
              "dstBeforeRgbaFloat": ${sample.dstBeforeRgbaFloat.floatArrayOrNullJson()},
              "sourceColorPremulRgbaFloat": ${sample.sourceColorPremulRgbaFloat.floatArrayOrNullJson()},
              "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
              "blendMode": ${sample.blendMode.jsonString()},
              "expectedSourceOverRgbaFloat": ${sample.expectedSourceOverRgbaFloat.floatArrayOrNullJson()},
              "dstAfterRgbaFloat": ${sample.dstAfterRgbaFloat.floatArrayOrNullJson()},
              "postPassDstAfterRgbaFloat": ${sample.postPassDstAfterRgbaFloat.floatArrayOrNullJson()},
              "missingFields": [$missingJson],
              "classification": ${sample.classification.jsonString()},
              "reason": ${sample.reason.jsonString()}
            }
        """.trimIndent()
    }

    private fun m60F16AaStencilCoverPredrawDstReadbackJson(
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        contributionSnapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ): String {
        val selected = M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS
        val contributionByPixel = contributionSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val predrawByPixel = predrawSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val postPassByPixel = postPassSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to sample } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.lastOrNull { it.readbackAvailable } }
        val pixels = selected.map { (x, y) ->
            val predrawSamples = predrawByPixel[x to y].orEmpty()
                .sortedBy { it.first.drawIndex }
            val firstCaptured = predrawSamples.firstOrNull { (_, sample) ->
                sample.targetWithinScissor && sample.readbackAvailable && sample.dstBeforeRgbaFloat != null
            }
            val contributionSamples = contributionByPixel[x to y].orEmpty()
                .sortedWith(
                    compareBy<Pair<
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                        >> { it.first.drawIndex }.thenBy { it.second.subdrawOrdinal },
                )
            val observedFor409 = contributionSamples.count { (_, sample) ->
                sample.shaderObserved &&
                    sample.sourceColorPremulRgbaFloat != null &&
                    sample.coverageOrAaAlpha != null &&
                    sample.blendMode == "kSrcOver"
            }
            PredrawDstPixelModel(
                x = x,
                y = y,
                predrawSamples = predrawSamples,
                firstCaptured = firstCaptured,
                contributionSamples = contributionSamples,
                observedFor409SubdrawCount = observedFor409,
                postPass = postPassByPixel[x to y],
                classification = when {
                    firstCaptured != null -> "predraw-dst-captured"
                    predrawSamples.any { (_, sample) -> sample.targetWithinScissor } -> "predraw-dst-unavailable"
                    else -> "predraw-dst-unavailable"
                },
            )
        }
        val capturedCount = pixels.count { it.firstCaptured != null }
        val globalClassification = when {
            capturedCount == selected.size -> "predraw-dst-captured"
            capturedCount > 0 -> "predraw-dst-partial"
            else -> "predraw-dst-unavailable"
        }
        val selectedJson = pixels.joinToString(",\n") { pixel ->
            predrawDstPixelJson(pixel).prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-410",
              "sceneId": "m60-f16-aa-stencil-cover-predraw-dst-readback-for410",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-410-m60-f16-capturer-letat-destination-avant-aa-stencil-cover-pour-le-replay-source-over",
              "sourceFinding": "global/kanvas/findings/for-409-confirme-que-le-replay-source-over-m60-f16-manque-encore-letat-initial-destination",
              "sourceArtifacts": {
                "for401": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
                "for405": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json",
                "for408": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json",
                "for409": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-source-over-replay-for409/m60-f16-aa-stencil-cover-source-over-replay-for409.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "decision": "M60_F16_AA_STENCIL_COVER_PREDRAW_DST_READBACK_RECORDED",
              "classification": ${globalClassification.jsonString()},
              "globalClassification": ${globalClassification.jsonString()},
              "allowedClassifications": [
                "predraw-dst-captured",
                "predraw-dst-partial",
                "predraw-dst-unavailable"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "guards": {
                "experimentalStrokeRenderer": {"guardId": "$EXPERIMENTAL_RENDER_PROPERTY", "enabledForEvidenceRun": true, "enabledByDefault": false},
                "bandMetadataTransport": {"guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY", "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()}, "enabledByDefault": false},
                "predrawDstReadback": {"guardId": "$FOR410_PREDRAW_DST_READBACK_PROPERTY", "enabledForEvidenceRun": ${predrawSnapshot.enabled}, "enabledByDefault": false},
                "contributionIsolation": {"guardId": "$FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY", "enabledForEvidenceRun": ${contributionSnapshot.enabled}, "enabledByDefault": false},
                "postPassReadback": {"guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY", "enabledForEvidenceRun": ${postPassSnapshot.enabled}, "enabledByDefault": false}
              },
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16AaStencilCoverPredrawDstReadbackSnapshot()",
                "propertyName": ${predrawSnapshot.propertyName.jsonString()},
                "enabled": ${predrawSnapshot.enabled},
                "requestedBoundary": ${predrawSnapshot.requestedBoundary.jsonString()},
                "observedBoundary": ${predrawSnapshot.observedBoundary.jsonString()},
                "diagnosticShader": ${predrawSnapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${predrawSnapshot.pipelineLayout.jsonString()},
                "intermediateFormat": ${predrawSnapshot.intermediateFormat.jsonString()},
                "sampleLimit": ${predrawSnapshot.sampleLimit},
                "eventCount": ${predrawSnapshot.events.size}
              },
              "scope": {
                "scene": "M60 F16 bounded stroke cap/join target-colorspace blend",
                "pixelSet": "FOR-401 selected residual coordinates",
                "selectedPixelCount": ${selected.size},
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "blendMode": "kSrcOver",
                "generalizedOutsideM60F16": false
              },
              "predrawSummary": {
                "selectedPixelCount": ${pixels.size},
                "capturedPixelCount": $capturedCount,
                "unavailablePixelCount": ${pixels.size - capturedCount},
                "inspectedDrawCount": ${predrawSnapshot.events.size},
                "for408ObservedReplayInputSubdrawCount": ${pixels.sumOf { it.observedFor409SubdrawCount }},
                "for409ReplayPossiblePixelCount": ${pixels.count { it.firstCaptured != null && it.observedFor409SubdrawCount > 0 }},
                "postPassObservedPixelCount": ${pixels.count { it.postPass != null }}
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "generalizedOutsideM60F16": false,
                "syntheticDstBeforeUsed": false
              },
              "classificationReason": "FOR-410 records only destination state read from the WebGPU intermediate immediately before bounded M60 F16 StencilCoverAaPolygonDraw render passes. Null dstBefore values are preserved as unavailable and are not synthesized.",
              "validationCommands": [
                "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
                "rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py",
                "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
                "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun predrawDstPixelJson(model: PredrawDstPixelModel): String {
        val inspectedDraws = model.predrawSamples
            .filter { (_, sample) -> sample.targetWithinScissor }
            .map { it.first.drawIndex }
            .distinct()
        val allPredrawJson = model.predrawSamples.joinToString(",\n") { (event, sample) ->
            predrawDstSampleJson(event, sample).prependIndent("        ")
        }
        val relationJson = model.contributionSamples.joinToString(",\n") { (event, sample) ->
            """
                {
                  "drawIndex": ${event.drawIndex},
                  "subdrawOrdinal": ${sample.subdrawOrdinal},
                  "subdrawRole": ${sample.subdrawRole.jsonString()},
                  "shaderObserved": ${sample.shaderObserved},
                  "sourceColorPremulRgbaFloat": ${sample.sourceColorPremulRgbaFloat.floatArrayOrNullJson()},
                  "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
                  "blendMode": ${sample.blendMode.jsonString()}
                }
            """.trimIndent().prependIndent("        ")
        }
        val first = model.firstCaptured
        return """
            {
              "x": ${model.x},
              "y": ${model.y},
              "classification": ${model.classification.jsonString()},
              "drawIndexInspected": ${inspectedDraws.joinToString(prefix = "[", postfix = "]")},
              "predrawStatus": ${model.classification.jsonString()},
              "dstBeforeRgbaFloat": ${first?.second?.dstBeforeRgbaFloat.floatArrayOrNullJson()},
              "dstBeforeRgba8": ${first?.second?.dstBeforeRgba8.intArrayOrNullJson()},
              "dstBeforeSource": ${first?.let { "FOR-410 drawIndex ${it.first.drawIndex} predraw compute readback".jsonString() } ?: "null"},
              "firstCapturedDrawIndex": ${first?.first?.drawIndex ?: "null"},
              "for408Relation": {
                "observedReplayInputSubdrawCount": ${model.observedFor409SubdrawCount},
                "subdraws": [
            $relationJson
                ]
              },
              "for409Replay": {
                "becomesPossible": ${first != null && model.observedFor409SubdrawCount > 0},
                "requiredInput": "dstBeforeRgbaFloat before first observed source-over subdraw",
                "sourceOverReplayArtifact": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-source-over-replay-for409/m60-f16-aa-stencil-cover-source-over-replay-for409.json",
                "postPassObservedRgbaFloat": ${model.postPass?.observedRgbaFloat.floatArrayOrNullJson()}
              },
              "predrawSamples": [
            $allPredrawJson
              ],
              "classificationReason": ${if (first != null) {
            "A non-synthetic predraw destination sample was captured before the first inspected StencilCoverAaPolygonDraw targeting this coordinate."
        } else {
            "No non-synthetic predraw destination sample was captured for an inspected StencilCoverAaPolygonDraw targeting this coordinate."
        }.jsonString()}
            }
        """.trimIndent()
    }

    private fun predrawDstSampleJson(
        event: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackEvent,
        sample: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSample,
    ): String = """
        {
          "drawIndex": ${event.drawIndex},
          "pipelineFamily": ${event.pipelineFamily.jsonString()},
          "targetWithinScissor": ${sample.targetWithinScissor},
          "readbackAttempted": ${sample.readbackAttempted},
          "readbackAvailable": ${sample.readbackAvailable},
          "dstBeforeRgbaFloat": ${sample.dstBeforeRgbaFloat.floatArrayOrNullJson()},
          "dstBeforeRgba8": ${sample.dstBeforeRgba8.intArrayOrNullJson()},
          "classification": ${sample.classification.jsonString()},
          "reason": ${sample.reason.jsonString()}
        }
    """.trimIndent()

    private fun m60F16AaStencilCoverShaderReturnDiagnosticJson(
        shaderReturnSnapshot: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSnapshot,
        contributionSnapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        predrawSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ): String {
        val selected = M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS
        val shaderByPixel = shaderReturnSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val contributionByPixel = contributionSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val predrawByPixel = predrawSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to (event to sample) } }
            .groupBy({ it.first }, { it.second })
        val postPassByPixel = postPassSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to sample } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.lastOrNull { it.readbackAvailable } }
        val pixels = selected.map { (x, y) ->
            val shaderSamples = shaderByPixel[x to y].orEmpty()
                .sortedWith(
                    compareBy<Pair<
                        SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
                        SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
                        >> { it.first.drawIndex }.thenBy { it.second.subdrawOrdinal },
                )
            val contributionSamples = contributionByPixel[x to y].orEmpty()
                .sortedWith(
                    compareBy<Pair<
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                        >> { it.first.drawIndex }.thenBy { it.second.subdrawOrdinal },
                )
            val predraw = predrawByPixel[x to y].orEmpty()
                .sortedBy { it.first.drawIndex }
                .firstOrNull { (_, sample) ->
                    sample.targetWithinScissor && sample.readbackAvailable && sample.dstBeforeRgbaFloat != null
                }
            val observedShader = shaderSamples.filter { (_, sample) ->
                sample.shaderObserved && !sample.captureSynthetic && sample.sourceColorSentToBlend != null
            }
            val postPass = postPassByPixel[x to y]
            val dstBefore = predraw?.second?.dstBeforeRgbaFloat
            val replayed = if (dstBefore != null && observedShader.isNotEmpty()) {
                observedShader.fold(dstBefore.copyOf()) { dst, (_, sample) ->
                    val sourceColor = requireNotNull(sample.sourceColorSentToBlend)
                    sourceOverPremul(sourceColor, dst)
                }
            } else {
                null
            }
            val replayDelta = if (replayed != null && postPass?.observedRgbaFloat != null) {
                rgbaDelta(replayed, postPass.observedRgbaFloat)
            } else {
                null
            }
            val fieldMismatch = observedShader.any { (_, sample) ->
                val shaderSource = sample.sourceColorSentToBlend
                val for408Source = sample.sourceFieldUsedByFOR408Replay
                val coverage = sample.coverageOrAaAlpha
                shaderSource != null &&
                    for408Source != null &&
                    coverage != null &&
                    rgbaMaxAbsDelta(shaderSource, for408Source.map { it * coverage }.toFloatArray()) > FOR412_MATCH_TOLERANCE
            }
            val shaderNonZero = observedShader.any { (_, sample) ->
                sample.sourceColorSentToBlend?.any { kotlin.math.abs(it) > FOR412_MATCH_TOLERANCE } == true
            }
            val postPassColored = postPass?.observedRgbaFloat?.take(3)
                ?.any { kotlin.math.abs(it) > FOR412_MATCH_TOLERANCE } == true
            val allShaderZero = observedShader.isNotEmpty() && !shaderNonZero
            val classification = when {
                observedShader.isEmpty() || predraw?.second?.dstBeforeRgbaFloat == null ||
                    postPass?.observedRgbaFloat == null -> "shader-return-unavailable"
                replayDelta?.withinTolerance == true -> "shader-return-explains-post-pass"
                fieldMismatch || shaderNonZero -> "for408-source-field-mismatch"
                allShaderZero && postPassColored -> "shader-return-zero-but-post-pass-colored"
                else -> "shader-return-unavailable"
            }
            ShaderReturnPixelModel(
                x = x,
                y = y,
                shaderSamples = shaderSamples,
                contributionSamples = contributionSamples,
                firstPredraw = predraw,
                postPass = postPass,
                replayedRgbaFloat = replayed,
                replayVsPostPassDelta = replayDelta,
                observedShaderReturnCount = observedShader.size,
                for408SourceFieldMismatch = fieldMismatch,
                classification = classification,
            )
        }
        val globalClassification = when {
            pixels.all { it.classification == "shader-return-explains-post-pass" } ->
                "shader-return-explains-post-pass"
            pixels.any { it.classification == "for408-source-field-mismatch" } ->
                "for408-source-field-mismatch"
            pixels.any { it.classification == "shader-return-zero-but-post-pass-colored" } ->
                "shader-return-zero-but-post-pass-colored"
            else -> "shader-return-unavailable"
        }
        val selectedJson = pixels.joinToString(",\n") { pixel ->
            shaderReturnPixelJson(pixel).prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-412",
              "sceneId": "m60-f16-aa-stencil-cover-shader-return-diagnostic-for412",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-412-m60-f16-capturer-la-source-effective-envoyee-au-blend-aa-stencil-cover",
              "sourceFinding": "global/kanvas/findings/for-411-rejoue-source-over-avec-dst-before-et-classe-les-16-pixels-en-divergence-post-pass",
              "sourceArtifacts": {
                "for401": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
                "for405": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json",
                "for408": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json",
                "for410": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-predraw-dst-readback-for410/m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json",
                "for411": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-over-replay-with-predraw-dst-for411/m60-f16-source-over-replay-with-predraw-dst-for411.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "decision": "M60_F16_AA_STENCIL_COVER_SHADER_RETURN_DIAGNOSTIC_RECORDED",
              "classification": ${globalClassification.jsonString()},
              "globalClassification": ${globalClassification.jsonString()},
              "allowedClassifications": [
                "shader-return-explains-post-pass",
                "for408-source-field-mismatch",
                "shader-return-zero-but-post-pass-colored",
                "shader-return-unavailable"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "guards": {
                "experimentalStrokeRenderer": {"guardId": "$EXPERIMENTAL_RENDER_PROPERTY", "enabledForEvidenceRun": true, "enabledByDefault": false},
                "bandMetadataTransport": {"guardId": "$M60_F16_BAND_METADATA_TRANSPORT_PROPERTY", "enabledForEvidenceRun": ${System.getProperty(M60_F16_BAND_METADATA_TRANSPORT_PROPERTY, "false").toBoolean()}, "enabledByDefault": false},
                "shaderReturnDiagnostic": {"guardId": "$FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY", "enabledForEvidenceRun": ${shaderReturnSnapshot.enabled}, "enabledByDefault": false},
                "predrawDstReadback": {"guardId": "$FOR410_PREDRAW_DST_READBACK_PROPERTY", "enabledForEvidenceRun": ${predrawSnapshot.enabled}, "enabledByDefault": false},
                "sourceOverReplay": {"guardId": "$FOR409_SOURCE_OVER_REPLAY_PROPERTY", "enabledForEvidenceRun": ${System.getProperty(FOR409_SOURCE_OVER_REPLAY_PROPERTY, "false").toBoolean()}, "enabledByDefault": false},
                "contributionIsolation": {"guardId": "$FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY", "enabledForEvidenceRun": ${contributionSnapshot.enabled}, "enabledByDefault": false},
                "postPassReadback": {"guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY", "enabledForEvidenceRun": ${postPassSnapshot.enabled}, "enabledByDefault": false}
              },
              "runtimeSnapshot": {
                "api": "SkWebGpuDevice.m60F16AaStencilCoverShaderReturnDiagnosticSnapshot()",
                "propertyName": ${shaderReturnSnapshot.propertyName.jsonString()},
                "enabled": ${shaderReturnSnapshot.enabled},
                "requestedBoundary": ${shaderReturnSnapshot.requestedBoundary.jsonString()},
                "observedBoundary": ${shaderReturnSnapshot.observedBoundary.jsonString()},
                "diagnosticShader": ${shaderReturnSnapshot.diagnosticShader.jsonString()},
                "pipelineLayout": ${shaderReturnSnapshot.pipelineLayout.jsonString()},
                "sampleLimit": ${shaderReturnSnapshot.sampleLimit},
                "eventCount": ${shaderReturnSnapshot.events.size}
              },
              "scope": {
                "scene": "M60 F16 bounded stroke cap/join target-colorspace blend",
                "pixelSet": "FOR-401 selected residual coordinates",
                "selectedPixelCount": ${selected.size},
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "blendMode": "kSrcOver",
                "generalizedOutsideM60F16": false
              },
              "comparisonPolicy": {
                "shaderReturnMeaning": "sourceColorSentToBlend is the non-synthetic vec4f returned by the fragment shader @location(0), already premultiplied and coverage-applied",
                "correctedColorBeforeCoverageMeaning": "derived from captured colorAfterColorFilter or captured colorAfterTargetColorspaceIfNeeded according to the captured candidateBranchReached branch",
                "for408ReplayFieldMeaning": "sourceFieldUsedByFOR408Replay is the pre-existing FOR-408 sourceColorPremulRgbaFloat before FOR-411 coverage multiplication",
                "replayMath": "premultiplied float SrcOver with shader return: out = shaderReturn + dst * (1 - shaderReturn.a)",
                "noSyntheticShaderReturn": true,
                "for400UsedAsDirectProof": false,
                "tolerance": $FOR412_MATCH_TOLERANCE
              },
              "shaderReturnSummary": {
                "selectedPixelCount": ${pixels.size},
                "observedShaderReturnPixelCount": ${pixels.count { it.observedShaderReturnCount > 0 }},
                "observedShaderReturnSubdrawCount": ${pixels.sumOf { it.observedShaderReturnCount }},
                "for408SourceFieldMismatchPixelCount": ${pixels.count { it.for408SourceFieldMismatch }},
                "shaderReturnExplainsPostPassCount": ${pixels.count { it.classification == "shader-return-explains-post-pass" }},
                "for408SourceFieldMismatchCount": ${pixels.count { it.classification == "for408-source-field-mismatch" }},
                "shaderReturnZeroButPostPassColoredCount": ${pixels.count { it.classification == "shader-return-zero-but-post-pass-colored" }},
                "shaderReturnUnavailableCount": ${pixels.count { it.classification == "shader-return-unavailable" }},
                "predrawDstConsumedPixelCount": ${pixels.count { it.firstPredraw?.second?.dstBeforeRgbaFloat != null }},
                "postPassObservedPixelCount": ${pixels.count { it.postPass?.observedRgbaFloat != null }}
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for400UsedAsDirectProof": false,
                "generalizedOutsideM60F16": false,
                "syntheticShaderReturnUsed": false
              },
              "classificationReason": "FOR-412 captures the actual fragment shader return value sent to fixed-function blend for the bounded M60 F16 AA stencil-cover subdraws. The classification compares that value with the FOR-408/FOR-411 source field, a source-over replay using FOR-410 dstBefore, and the FOR-405 post-pass readback.",
              "validationCommands": [
                "rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py",
                "rtk python3 scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py",
                "rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py",
                "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
                "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun shaderReturnPixelJson(model: ShaderReturnPixelModel): String {
        val shaderSamplesJson = model.shaderSamples.joinToString(",\n") { (event, sample) ->
            shaderReturnSampleJson(event, sample).prependIndent("        ")
        }
        val contributionJson = model.contributionSamples.joinToString(",\n") { (event, sample) ->
            """
                {
                  "drawIndex": ${event.drawIndex},
                  "subdrawOrdinal": ${sample.subdrawOrdinal},
                  "subdrawRole": ${sample.subdrawRole.jsonString()},
                  "shaderObserved": ${sample.shaderObserved},
                  "sourceColorPremulRgbaFloat": ${sample.sourceColorPremulRgbaFloat.floatArrayOrNullJson()},
                  "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
                  "blendMode": ${sample.blendMode.jsonString()}
                }
            """.trimIndent().prependIndent("        ")
        }
        return """
            {
              "x": ${model.x},
              "y": ${model.y},
              "classification": ${model.classification.jsonString()},
              "drawIndexInspected": ${model.shaderSamples.map { it.first.drawIndex }.distinct().joinToString(prefix = "[", postfix = "]")},
              "subdrawOrdinalInspected": ${model.shaderSamples.map { it.second.subdrawOrdinal }.distinct().joinToString(prefix = "[", postfix = "]")},
              "observedShaderReturnCount": ${model.observedShaderReturnCount},
              "sourceReturnedToBlendAvailable": ${model.observedShaderReturnCount > 0},
              "captureSynthetic": ${model.shaderSamples.any { it.second.captureSynthetic }},
              "dstBeforeRgbaFloat": ${model.firstPredraw?.second?.dstBeforeRgbaFloat.floatArrayOrNullJson()},
              "dstBeforeSource": ${model.firstPredraw?.let { "FOR-410 drawIndex ${it.first.drawIndex} predraw compute readback".jsonString() } ?: "null"},
              "replayedWithShaderReturnRgbaFloat": ${model.replayedRgbaFloat.floatArrayOrNullJson()},
              "postPassObservedRgbaFloat": ${model.postPass?.observedRgbaFloat.floatArrayOrNullJson()},
              "postPassObservedRgba8": ${model.postPass?.observedRgba8.intArrayOrNullJson()},
              "shaderReturnReplayVsPostPassDelta": ${model.replayVsPostPassDelta?.let { rgbaDeltaJson(it) } ?: "null"},
              "for408SourceFieldMismatch": ${model.for408SourceFieldMismatch},
              "for408For411ReplayInputs": [
            $contributionJson
              ],
              "shaderReturnSubdraws": [
            $shaderSamplesJson
              ],
              "classificationReason": ${shaderReturnClassificationReason(model).jsonString()}
            }
        """.trimIndent()
    }

    private fun shaderReturnSampleJson(
        event: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
        sample: SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
    ): String {
        val for411EffectiveSource = if (
            sample.sourceFieldUsedByFOR408Replay != null &&
            sample.coverageOrAaAlpha != null
        ) {
            val replaySource = sample.sourceFieldUsedByFOR408Replay
            val coverageOrAaAlpha = sample.coverageOrAaAlpha
            replaySource.map { it * coverageOrAaAlpha }.toFloatArray()
        } else {
            null
        }
        val sourceDelta = if (sample.sourceColorSentToBlend != null && for411EffectiveSource != null) {
            rgbaDelta(sample.sourceColorSentToBlend, for411EffectiveSource)
        } else {
            null
        }
        return """
            {
              "drawIndex": ${event.drawIndex},
              "pipelineFamily": ${event.pipelineFamily.jsonString()},
              "fillType": ${event.fillType.jsonString()},
              "blendMode": ${event.blendMode.jsonString()},
              "subdrawOrdinal": ${sample.subdrawOrdinal},
              "subdrawRole": ${sample.subdrawRole.jsonString()},
              "targetWithinScissor": ${sample.targetWithinScissor},
              "shaderObserved": ${sample.shaderObserved},
              "candidateBranchReached": ${sample.candidateBranchReached},
              "captureSynthetic": ${sample.captureSynthetic},
              "colorAfterColorFilter": ${sample.colorAfterColorFilter.floatArrayOrNullJson()},
              "colorAfterTargetColorspaceIfNeeded": ${sample.colorAfterTargetColorspaceIfNeeded.floatArrayOrNullJson()},
              "correctedColorBeforeCoverage": ${sample.correctedColorBeforeCoverage.floatArrayOrNullJson()},
              "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
              "sourceAlphaAfterCoverage": ${sample.sourceAlphaAfterCoverage?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
              "sourceColorBeforeQuantization": ${sample.sourceColorBeforeQuantization.floatArrayOrNullJson()},
              "sourceColorSentToBlend": ${sample.sourceColorSentToBlend.floatArrayOrNullJson()},
              "sourceFieldUsedByFOR408Replay": ${sample.sourceFieldUsedByFOR408Replay.floatArrayOrNullJson()},
              "sourceFieldUsedByFOR411AfterCoverage": ${for411EffectiveSource.floatArrayOrNullJson()},
              "sourceReturnVsFOR411EffectiveSourceDelta": ${sourceDelta?.let { rgbaDeltaJson(it) } ?: "null"},
              "quantizedAlphaSentToBlend": ${sample.quantizedAlphaSentToBlend?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
              "classification": ${sample.classification.jsonString()},
              "reason": ${sample.reason.jsonString()}
            }
        """.trimIndent()
    }

    private fun sourceOverPremul(src: FloatArray, dst: FloatArray): FloatArray =
        FloatArray(4) { index -> src[index] + dst[index] * (1f - src[3]) }

    private fun rgbaDelta(
        actual: FloatArray,
        expected: FloatArray,
        tolerance: Float = FOR412_MATCH_TOLERANCE,
    ): RgbaFloatDelta {
        val signed = FloatArray(4) { index -> actual[index] - expected[index] }
        val absolute = FloatArray(4) { index -> kotlin.math.abs(signed[index]) }
        return RgbaFloatDelta(
            signed = signed,
            absolute = absolute,
            absoluteTotal = absolute.sum(),
            maxChannel = absolute.maxOrNull() ?: 0f,
            withinTolerance = (absolute.maxOrNull() ?: 0f) <= tolerance,
            tolerance = tolerance,
        )
    }

    private fun rgbaMaxAbsDelta(a: FloatArray, b: FloatArray): Float =
        FloatArray(4) { index -> kotlin.math.abs(a[index] - b[index]) }.maxOrNull() ?: 0f

    private fun rgbaDeltaJson(delta: RgbaFloatDelta): String = """
        {
          "signedRgbaFloat": ${delta.signed.floatJson()},
          "absoluteRgbaFloat": ${delta.absolute.floatJson()},
          "absoluteTotalFloat": ${String.format(Locale.US, "%.9f", delta.absoluteTotal)},
          "maxChannelFloat": ${String.format(Locale.US, "%.9f", delta.maxChannel)},
          "withinTolerance": ${delta.withinTolerance},
          "tolerance": ${String.format(Locale.US, "%.9f", delta.tolerance)}
        }
    """.trimIndent()

    private fun shaderReturnClassificationReason(model: ShaderReturnPixelModel): String = when (model.classification) {
        "shader-return-explains-post-pass" ->
            "The non-synthetic shader return replay with FOR-410 dstBefore reproduces the FOR-405 post-pass color within tolerance."
        "for408-source-field-mismatch" ->
            "The non-synthetic shader return differs from the FOR-408/FOR-411 effective source field, so the replay input field used before FOR-412 is not the value returned to blend."
        "shader-return-zero-but-post-pass-colored" ->
            "The captured shader return is zero while the FOR-405 post-pass color is non-zero, indicating the colored write is not explained by this observed subdraw return."
        else ->
            "A non-synthetic shader return, FOR-410 dstBefore, or FOR-405 post-pass color was unavailable for this coordinate."
    }

    private fun m60F16AaStencilCoverSourceOverReplayJson(
        snapshot: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        postPassSnapshot: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        adapter: String,
    ): String {
        val selected = M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS
        val samples = snapshot.events.flatMap { event -> event.samples.map { sample -> event to sample } }
        val postPassByPixel = postPassSnapshot.events
            .flatMap { event -> event.samples.map { sample -> (sample.x to sample.y) to sample } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.lastOrNull { it.readbackAvailable } }
        val pixelModels = selected.map { (x, y) ->
            val pixelSamples = samples
                .filter { (_, sample) -> sample.x == x && sample.y == y }
                .sortedWith(
                    compareBy<Pair<
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                        SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                        >> { it.first.drawIndex }.thenBy { it.second.subdrawOrdinal },
                )
            val observed = pixelSamples.filter { (_, sample) ->
                sample.shaderObserved &&
                    sample.sourceColorPremulRgbaFloat != null &&
                    sample.coverageOrAaAlpha != null &&
                    sample.blendMode == "kSrcOver"
            }
            SourceOverReplayPixelModel(
                x = x,
                y = y,
                postPass = postPassByPixel[x to y],
                subdraws = pixelSamples,
                observedReplayInputCount = observed.size,
                classification = when {
                    observed.isEmpty() -> "source-over-replay-no-observed-subdraw"
                    else -> "source-over-replay-insufficient-inputs"
                },
            )
        }
        val globalClassification = when {
            pixelModels.any { it.classification == "source-over-replay-insufficient-inputs" } ->
                "source-over-replay-insufficient-inputs"
            pixelModels.all { it.classification == "source-over-replay-no-observed-subdraw" } ->
                "source-over-replay-no-observed-subdraw"
            else -> "source-over-replay-insufficient-inputs"
        }
        val selectedJson = pixelModels.joinToString(",\n") { model ->
            sourceOverReplayPixelJson(model).prependIndent("    ")
        }
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-409",
              "sceneId": "m60-f16-aa-stencil-cover-source-over-replay-for409",
              "sourceSceneId": "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend",
              "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-for-409-m60-f16-replay-diagnostique-source-over-hors-fixed-function-blend",
              "sourceFinding": "global/kanvas/findings/for-408-ajoute-le-hook-per-subdraw-aa-stencil-cover-mais-confirme-le-blocage-framebuffer-m60-f16",
              "sourceArtifacts": {
                "for401": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json",
                "for405": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json",
                "for406": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-post-pass-reference-comparison-for406/m60-f16-post-pass-reference-comparison-for406.json",
                "for408": "reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json"
              },
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "runtimeOwner": "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
              "decision": "M60_F16_AA_STENCIL_COVER_SOURCE_OVER_REPLAY_RECORDED",
              "classification": ${globalClassification.jsonString()},
              "globalClassification": ${globalClassification.jsonString()},
              "allowedClassifications": [
                "source-over-replay-matches-post-pass",
                "source-over-replay-differs-post-pass",
                "source-over-replay-insufficient-inputs",
                "source-over-replay-no-observed-subdraw"
              ],
              "supportClaim": false,
              "promoted": false,
              "correctionAppliedByDefault": false,
              "defaultRenderingChanged": false,
              "thresholdChanged": false,
              "scoringChanged": false,
              "guards": {
                "experimentalStrokeRenderer": {"guardId": "$EXPERIMENTAL_RENDER_PROPERTY", "enabledForEvidenceRun": true, "enabledByDefault": false},
                "sourceOverReplay": {"guardId": "$FOR409_SOURCE_OVER_REPLAY_PROPERTY", "enabledForEvidenceRun": ${System.getProperty(FOR409_SOURCE_OVER_REPLAY_PROPERTY, "false").toBoolean()}, "enabledByDefault": false},
                "contributionIsolation": {"guardId": "$FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY", "enabledForEvidenceRun": ${snapshot.enabled}, "enabledByDefault": false},
                "postPassReadback": {"guardId": "$FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY", "enabledForEvidenceRun": ${postPassSnapshot.enabled}, "enabledByDefault": false}
              },
              "scope": {
                "scene": "M60 F16 bounded stroke cap/join target-colorspace blend",
                "pixelSet": "FOR-401 selected residual coordinates",
                "selectedPixelCount": ${selected.size},
                "pipelineFamily": "StencilCoverAaPolygonDraw",
                "blendMode": "kSrcOver",
                "generalizedOutsideM60F16": false
              },
              "replayPolicy": {
                "math": "premultiplied float SrcOver: out = src + dst * (1 - src.a)",
                "subdrawOrder": "drawIndex ascending, then subdrawOrdinal ascending",
                "initialStateRequirement": "exact destination premultiplied RGBA float before the first observed replay subdraw",
                "initialStateAvailable": false,
                "insufficientInputClassification": "source-over-replay-insufficient-inputs",
                "noSyntheticInitialState": true
              },
              "sourceContext": {
                "for408Classification": "per-subdraw-framebuffer-state-unavailable",
                "for408RuntimeApi": "SkWebGpuDevice.m60F16AaStencilCoverContributionIsolationSnapshot()",
                "for408ObservedSubdrawCount": ${pixelModels.sumOf { it.observedReplayInputCount }},
                "for405RuntimeApi": "SkWebGpuDevice.m60F16AaStencilCoverPostPassReadbackSnapshot()",
                "for405PostPassObservedPixelCount": ${pixelModels.count { it.postPass != null }},
                "for400EvidencePolicy": "context-only-not-direct-write-proof",
                "for400UsedAsDirectProof": false
              },
              "replaySummary": {
                "selectedPixelCount": ${pixelModels.size},
                "sourceOverReplayMatchesPostPassCount": ${pixelModels.count { it.classification == "source-over-replay-matches-post-pass" }},
                "sourceOverReplayDiffersPostPassCount": ${pixelModels.count { it.classification == "source-over-replay-differs-post-pass" }},
                "sourceOverReplayInsufficientInputsCount": ${pixelModels.count { it.classification == "source-over-replay-insufficient-inputs" }},
                "sourceOverReplayNoObservedSubdrawCount": ${pixelModels.count { it.classification == "source-over-replay-no-observed-subdraw" }},
                "observedReplayInputSubdrawCount": ${pixelModels.sumOf { it.observedReplayInputCount }},
                "usedSubdrawCount": 0,
                "excludedSubdrawCount": ${pixelModels.sumOf { it.subdraws.size }},
                "postPassObservedPixelCount": ${pixelModels.count { it.postPass != null }},
                "initialStateMissingPixelCount": ${pixelModels.count { it.observedReplayInputCount > 0 }}
              },
              "selectedPixels": [
            $selectedJson
              ],
              "nonGoalsPreserved": {
                "defaultRenderingChanged": false,
                "supportClaimRaised": false,
                "promoted": false,
                "thresholdChanged": false,
                "scoringChanged": false,
                "correctionApplied": false,
                "for400UsedAsDirectProof": false,
                "generalizedOutsideM60F16": false
              },
              "classificationReason": "FOR-409 can order FOR-408 observed source/coverage subdraw inputs and compare against FOR-405 post-pass colors, but the exact destination premultiplied RGBA float before the first observed subdraw is still unavailable. The diagnostic therefore refuses to synthesize an initial state and classifies the replay as insufficient inputs.",
              "validationCommands": [
                "rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py",
                "rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py",
                "rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py",
                "rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
                "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun sourceOverReplayPixelJson(model: SourceOverReplayPixelModel): String {
        val usedJson = model.subdraws.filter { (_, sample) ->
            model.classification in setOf(
                "source-over-replay-matches-post-pass",
                "source-over-replay-differs-post-pass",
            ) && sample.shaderObserved
        }.joinToString(",\n") { (event, sample) ->
            sourceOverReplaySubdrawJson(event, sample, used = true, reason = null).prependIndent("        ")
        }
        val excludedJson = model.subdraws.joinToString(",\n") { (event, sample) ->
            val reason = when {
                !sample.shaderObserved -> "subdraw-not-observed-by-for408-hook"
                sample.sourceColorPremulRgbaFloat == null -> "source-color-premul-missing"
                sample.coverageOrAaAlpha == null -> "coverage-or-aa-alpha-missing"
                sample.blendMode != "kSrcOver" -> "non-source-over-blend-mode"
                else -> "initial-state-before-first-observed-subdraw-unavailable"
            }
            sourceOverReplaySubdrawJson(event, sample, used = false, reason = reason).prependIndent("        ")
        }
        val initialMissingReason = if (model.observedReplayInputCount > 0) {
            "source-over-replay-initial-state-unavailable"
        } else {
            "source-over-replay-no-observed-subdraw"
        }
        return """
            {
              "x": ${model.x},
              "y": ${model.y},
              "classification": ${model.classification.jsonString()},
              "initialState": {
                "available": false,
                "rgbaFloat": null,
                "source": null,
                "missingReason": ${initialMissingReason.jsonString()},
                "verification": "FOR-408 does not expose dstBeforeRgbaFloat for the first observed subdraw; FOR-405 only exposes post-pass destination after the AA stencil-cover pass."
              },
              "observedSubdrawCount": ${model.observedReplayInputCount},
              "usedSubdrawCount": 0,
              "excludedSubdrawCount": ${model.subdraws.size},
              "replayOrder": "drawIndex-then-subdrawOrdinal",
              "replayInputsSufficient": false,
              "replayedRgbaFloat": null,
              "postPassObservedRgbaFloat": ${model.postPass?.observedRgbaFloat.floatArrayOrNullJson()},
              "postPassObservedRgba8": ${model.postPass?.observedRgba8.intArrayOrNullJson()},
              "replayVsPostPassDelta": null,
              "subdrawsUsed": [
            $usedJson
              ],
              "subdrawsExcluded": [
            $excludedJson
              ],
              "classificationReason": "FOR-408 provides observed source color and coverage for this pixel, but FOR-409 has no explicit initial destination state before the first observed subdraw."
            }
        """.trimIndent()
    }

    private fun sourceOverReplaySubdrawJson(
        event: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
        sample: SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
        used: Boolean,
        reason: String?,
    ): String = """
        {
          "drawIndex": ${event.drawIndex},
          "subdrawOrdinal": ${sample.subdrawOrdinal},
          "subdrawRole": ${sample.subdrawRole.jsonString()},
          "pipelineFamily": ${event.pipelineFamily.jsonString()},
          "blendMode": ${sample.blendMode.jsonString()},
          "shaderObserved": ${sample.shaderObserved},
          "sourceColorPremulRgbaFloat": ${sample.sourceColorPremulRgbaFloat.floatArrayOrNullJson()},
          "coverageOrAaAlpha": ${sample.coverageOrAaAlpha?.let { String.format(Locale.US, "%.9f", it) } ?: "null"},
          "usedForReplay": $used,
          "excludedReason": ${reason?.jsonString() ?: "null"}
        }
    """.trimIndent()

    private data class ShaderReturnPixelModel(
        val x: Int,
        val y: Int,
        val shaderSamples: List<
            Pair<
                SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
                SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
                >,
            >,
        val contributionSamples: List<
            Pair<
                SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                >,
            >,
        val firstPredraw: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackEvent,
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSample,
            >?,
        val postPass: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample?,
        val replayedRgbaFloat: FloatArray?,
        val replayVsPostPassDelta: RgbaFloatDelta?,
        val observedShaderReturnCount: Int,
        val for408SourceFieldMismatch: Boolean,
        val classification: String,
    )

    private data class RgbaFloatDelta(
        val signed: FloatArray,
        val absolute: FloatArray,
        val absoluteTotal: Float,
        val maxChannel: Float,
        val withinTolerance: Boolean,
        val tolerance: Float,
    )

    private data class M60F16DrawPixelKey(
        val drawIndex: Int,
        val x: Int,
        val y: Int,
    )

    private data class M60F16VerifiedSourceDelta(
        val subdrawOrdinal: Int,
        val subdrawRole: String,
        val delta: RgbaFloatDelta,
    )

    private data class M60F16VerifiedSourceComparisonRecord(
        val key: M60F16DrawPixelKey,
        val sourceRecords: List<
            Pair<
                SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
                SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
                >,
            >,
        val colorTarget: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetEvent,
            SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetSample,
            >?,
        val predraw: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackEvent,
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSample,
            >?,
        val postPass: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackEvent,
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample,
            >?,
        val reconstructedSrcOver: FloatArray?,
        val sourceDeltas: List<M60F16VerifiedSourceDelta>,
        val bestSourceDelta: M60F16VerifiedSourceDelta?,
        val scratchSourceOverVsFinalDelta: RgbaFloatDelta?,
        val dstMutationDelta: RgbaFloatDelta?,
        val decisive: Boolean,
        val classification: String,
    )

    private data class M60F16ReferenceSourceCoverageRecord(
        val key: M60F16DrawPixelKey,
        val band: StrokePaintBand?,
        val coverageByte: Int,
        val expectedCoverage: Float,
        val bestSource: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticEvent,
            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSample,
            >?,
        val expectedSource: FloatArray?,
        val sourceVsReferenceDelta: RgbaFloatDelta?,
        val coverageDelta: Float?,
        val referenceRgba: IntArray,
        val currentGpuRgba: IntArray,
        val postPass: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackEvent,
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample,
            >?,
        val predraw: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackEvent,
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSample,
            >?,
        val colorTarget: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetEvent,
            SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetSample,
            >?,
        val finalVsReferenceDelta: RgbaFloatDelta?,
        val decisive: Boolean,
        val classification: String,
    )

    private data class SourceOverReplayPixelModel(
        val x: Int,
        val y: Int,
        val postPass: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample?,
        val subdraws: List<
            Pair<
                SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                >,
            >,
        val observedReplayInputCount: Int,
        val classification: String,
    )

    private data class PredrawDstPixelModel(
        val x: Int,
        val y: Int,
        val predrawSamples: List<
            Pair<
                SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackEvent,
                SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSample,
                >,
            >,
        val firstCaptured: Pair<
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackEvent,
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSample,
            >?,
        val contributionSamples: List<
            Pair<
                SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationEvent,
                SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSample,
                >,
            >,
        val observedFor409SubdrawCount: Int,
        val postPass: SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSample?,
        val classification: String,
    )

    private fun FloatArray?.floatArrayOrNullJson(): String =
        this?.floatJson() ?: "null"

    private fun IntArray?.intArrayOrNullJson(): String =
        this?.let { intArrayJson(it) } ?: "null"

    private fun collapseM60F16CoverageStencilContributionMapSamples(
        samples: List<SkWebGpuDevice.M60F16CoverageStencilContributionMapSample>,
    ): List<SkWebGpuDevice.M60F16CoverageStencilContributionMapSample> =
        samples
            .groupBy { it.x to it.y }
            .map { (_, coordinateSamples) ->
                coordinateSamples.firstOrNull { it.hasEffectiveContribution() }
                    ?: coordinateSamples.firstOrNull { it.candidateBranchReached }
                    ?: coordinateSamples.firstOrNull { it.observedByShader }
                    ?: coordinateSamples.first()
            }
            .sortedWith(compareBy<SkWebGpuDevice.M60F16CoverageStencilContributionMapSample> { it.y }.thenBy { it.x })

    private fun m60F16For400WindowPixels(radius: Int): Set<Pair<Int, Int>> =
        M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS
            .flatMap { (x, y) ->
                ((y - radius)..(y + radius)).flatMap { yy ->
                    ((x - radius)..(x + radius)).map { xx -> xx to yy }
                }
            }
            .toSet()

    private fun finalResidualOriginPixels(
        reference: SkBitmap,
        currentGpu: SkBitmap,
    ): List<FinalResidualOriginPixel> {
        require(reference.width == currentGpu.width && reference.height == currentGpu.height)
        val out = mutableListOf<FinalResidualOriginPixel>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val referencePixel = reference.getPixel(x, y)
                val currentPixel = currentGpu.getPixel(x, y)
                val residualByChannel = residualByRgbaChannel(referencePixel, currentPixel)
                val residualTotal = residualByChannel.sum()
                if (residualTotal > 0) {
                    out += FinalResidualOriginPixel(
                        x = x,
                        y = y,
                        currentGpu = currentPixel,
                        reference = referencePixel,
                        residualByChannel = residualByChannel,
                        residualTotal = residualTotal,
                    )
                }
            }
        }
        return out.sortedWith(
            compareByDescending<FinalResidualOriginPixel> { it.residualTotal }
                .thenBy { it.y }
                .thenBy { it.x },
        )
    }

    private fun for401SelectedResidualOriginPixels(): List<FinalResidualOriginPixel> {
        fun pixel(
            x: Int,
            y: Int,
            current: IntArray,
            reference: IntArray,
            residual: IntArray,
            residualTotal: Int,
        ): FinalResidualOriginPixel =
            FinalResidualOriginPixel(
                x = x,
                y = y,
                currentGpu = rgbaToPixel(current),
                reference = rgbaToPixel(reference),
                residualByChannel = residual,
                residualTotal = residualTotal,
                attributionCandidate = "readbackOnlyUnknown",
            )
        return listOf(
            pixel(92, 75, intArrayOf(181, 191, 230, 255), intArrayOf(133, 150, 214, 255), intArrayOf(48, 41, 16, 0), 105),
            pixel(91, 76, intArrayOf(181, 191, 230, 255), intArrayOf(133, 150, 214, 255), intArrayOf(48, 41, 16, 0), 105),
            pixel(90, 77, intArrayOf(181, 191, 230, 255), intArrayOf(133, 150, 214, 255), intArrayOf(48, 41, 16, 0), 105),
            pixel(89, 78, intArrayOf(181, 191, 230, 255), intArrayOf(133, 150, 214, 255), intArrayOf(48, 41, 16, 0), 105),
            pixel(88, 79, intArrayOf(181, 191, 230, 255), intArrayOf(133, 150, 214, 255), intArrayOf(48, 41, 16, 0), 105),
            pixel(87, 80, intArrayOf(181, 191, 230, 255), intArrayOf(133, 150, 214, 255), intArrayOf(48, 41, 16, 0), 105),
            pixel(101, 37, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(102, 37, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(99, 38, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(100, 38, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(101, 38, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(102, 38, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(103, 38, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(104, 38, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(98, 39, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
            pixel(99, 39, intArrayOf(0, 138, 76, 255), intArrayOf(68, 121, 68, 255), intArrayOf(68, 17, 8, 0), 93),
        )
    }

    private fun residualByRgbaChannel(reference: Int, current: Int): IntArray =
        intArrayOf(
            kotlin.math.abs(((reference ushr 16) and 0xFF) - ((current ushr 16) and 0xFF)),
            kotlin.math.abs(((reference ushr 8) and 0xFF) - ((current ushr 8) and 0xFF)),
            kotlin.math.abs((reference and 0xFF) - (current and 0xFF)),
            kotlin.math.abs(((reference ushr 24) and 0xFF) - ((current ushr 24) and 0xFF)),
        )

    private data class FinalResidualOriginPixel(
        val x: Int,
        val y: Int,
        val currentGpu: Int,
        val reference: Int,
        val residualByChannel: IntArray,
        val residualTotal: Int,
        val belongsToFor397Predicate: Boolean = false,
        val belongsToFor400Window: Boolean = false,
        val attributionCandidate: String = "readbackOnlyUnknown",
    )

    private fun finalResidualOriginPixelJson(pixel: FinalResidualOriginPixel): String = """
        {
          "x": ${pixel.x},
          "y": ${pixel.y},
          "currentGpuRgba": ${rgbaJson(pixel.currentGpu)},
          "referenceRgba": ${rgbaJson(pixel.reference)},
          "residualByChannel": ${channelErrorJson(pixel.residualByChannel)},
          "residualTotal": ${pixel.residualTotal},
          "belongsToFor397Predicate": ${pixel.belongsToFor397Predicate},
          "belongsToFor400Window": ${pixel.belongsToFor400Window},
          "attributionCandidate": ${pixel.attributionCandidate.jsonString()}
        }
    """.trimIndent()

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

    private fun m60F16ResidualFringeDiscriminatorAuditJson(
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
        val for386Selected = MutableMembershipPixelSet("for386-source-fringe-band-local-window")
        val selectedSourceLocal = MutableMembershipPixelSet("for386-selected-source-local")
        val selectedRegressed = MutableMembershipPixelSet("for386-selected-regressed")
        val selectedNonRegressed = MutableMembershipPixelSet("for386-selected-non-regressed")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val regressionBreakdown = MutableResidualFringeMetadataBreakdown()
        val candidateStats = residualFringeDiscriminatorCandidates().associate { candidate ->
            candidate.id to MutableResidualFringeDiscriminatorCandidateStats(candidate)
        }
        val for385Predicate = generalizedCoverageMetadataCandidates().first { it.id == "partial-coverage-alpha-at-least-96" }
        val for386Predicate = coverageRegressionDiscriminatorCandidates().first { it.id == "source-fringe-band-local-window" }
        val selectedPixels = mutableListOf<PreCorrectionGeometryCoveragePixelAudit>()
        var for385SelectedPixels = 0

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
                    membership.transparentSourceAlphaByte > 0 &&
                    for385Predicate.select(audit)
                ) {
                    for385SelectedPixels++
                    if (for386Predicate.select(audit)) {
                        selectedPixels += audit
                        for386Selected.add(membership)
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
            compareByDescending<MutableResidualFringeDiscriminatorCandidateStats> { it.sourceLocalRecovered }
                .thenBy { it.regressedIncluded }
                .thenByDescending { it.precision }
                .thenBy { it.count },
        ).firstOrNull()
        val classification = residualFringeDiscriminatorClassification(
            bestCandidate = bestCandidate,
            sourceLocalTruth = sourceLocalTruth,
        )
        val sourceLocalSamples = selectedPixels
            .filter { it.membership.category == sourceLocal.id }
            .sortedWith(compareBy<PreCorrectionGeometryCoveragePixelAudit> { it.membership.pixel.y }.thenBy { it.membership.pixel.x })
        val regressedSamples = selectedPixels
            .filter { it.membership.pixel.deltaVsCurrent > 0 }
            .sortedWith(compareByDescending<PreCorrectionGeometryCoveragePixelAudit> { it.membership.pixel.deltaVsCurrent }.thenBy { it.membership.pixel.y }.thenBy { it.membership.pixel.x })
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-387",
              "sceneId": "m60-f16-residual-fringe-discriminator-audit-for387",
              "sourceSceneId": "m60-f16-coverage-regression-discriminator-audit-for386",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-exclure-les-27-regressions-restantes-apres-for-386",
              "sourceFinding": "global/kanvas/findings/for-386-montre-que-le-meilleur-discriminateur-m60-f16-reduit-les-428-regressions-mais-reste-trop-large-1",
              "requiredFor386Decision": "M60_F16_COVERAGE_REGRESSION_DISCRIMINATOR_AUDIT_RECORDED",
              "requiredFor386Classification": "discriminator-candidate-too-broad",
              "requiredFor385Decision": "M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED",
              "requiredFor385Classification": "generalized-predicate-too-broad",
              "requiredFor384Decision": "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED",
              "requiredFor384Classification": "metadata-candidate-defendable-runtime-proof-still-blocked",
              "requiredFor383Decision": "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED",
              "requiredFor383Classification": "pre-probe-predicate-too-broad",
              "requiredFor382Decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED",
              "requiredFor382Classification": "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof",
              "decision": "M60_F16_RESIDUAL_FRINGE_DISCRIMINATOR_AUDIT_RECORDED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "fringe-discriminator-defendable",
                "fringe-discriminator-too-broad",
                "fringe-discriminator-insufficient",
                "metadata-insufficient"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionKept": false,
              "correctionAppliedByDefault": false,
              "correctionFlag": ${FOR380_CORRECTION_PROPERTY.jsonString()},
              "inspectedFor386Selection": {
                "sourceCandidateId": "source-fringe-band-local-window",
                "parentFor385CandidateId": "partial-coverage-alpha-at-least-96",
                "scopeDescription": "The 35 pixels selected by the best FOR-386 local fringe discriminator, analyzed only as diagnostic input.",
                "sourceScopeFromFor386": true,
                "usesFor386SelectionAsAuditInput": true,
                "candidateSelectionUsesFor386AsPrimaryPredicate": false,
                "candidateSelectionUsesFor385AsPrimaryPredicate": false,
                "usesSkiaReferenceForScope": false,
                "usesProbeOutcomeForScope": false,
                "usesProbeResidualForScope": false,
                "usesDeltaVsCurrentForScope": false,
                "usesFor379MembershipAsPrimaryScope": false,
                "usesFor383PredicateAsPrimaryScope": false,
                "usesFor384PredicateAsPrimaryScope": false,
                "usesFor385PredicateAsPrimaryScope": false,
                "for385SelectedPixels": $for385SelectedPixels,
                "inspectedPixels": ${for386Selected.count},
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
                "fringeTopologyInferableFromOrthogonalCoverage": true,
                "sourceCoverageRelationAvailable": true,
                "referenceRequiredForCandidateSelection": false,
                "probeRequiredForCandidateSelection": false,
                "currentResidualRequiredForCandidateSelection": false,
                "deltaVsCurrentRequiredForCandidateSelection": false,
                "for379RequiredForCandidateSelection": false,
                "for383RequiredForCandidateSelection": false,
                "for384RequiredForCandidateSelection": false,
                "for385RequiredForCandidateSelection": false,
                "for386RequiredForCandidateSelection": false,
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
                "for386Selected": ${for386Selected.toStats().toJson().prependIndent("  ").trimStart()},
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
              "residualRegressionMetadataBreakdown": ${regressionBreakdown.toJson().prependIndent("  ").trimStart()},
              "sourceLocalPixelsForEvaluationOnly": [
            ${sourceLocalSamples.joinToString(",\n") { residualFringePixelJson(it).prependIndent("    ") }}
              ],
              "regressedPixelsForEvaluationOnly": [
            ${regressedSamples.joinToString(",\n") { residualFringePixelJson(it).prependIndent("    ") }}
              ],
              "fringeDiscriminatorCandidates": [
            ${candidates.joinToString(",\n") { it.toStatsJson(sourceLocalTruth = sourceLocalTruth).prependIndent("    ") }}
              ],
              "bestFringeDiscriminatorCandidate": ${bestCandidate?.toSummaryJson(sourceLocalTruth = sourceLocalTruth)?.prependIndent("  ")?.trimStart() ?: "null"},
              "classificationReason": ${residualFringeDiscriminatorClassificationReason(classification).jsonString()},
              "nextMove": ${residualFringeDiscriminatorNextMove(classification, bestCandidate).jsonString()},
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
                "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
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
                "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for387-pycache python3 -m py_compile scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
                "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
                "rtk git diff --check"
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun m60F16SourceCoverageFullSceneCandidateJson(
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
        val eligible = MutableMembershipPixelSet("nonzero-coverage-source-alpha")
        val selected = MutableMembershipPixelSet("source-color-and-oriented-coverage-lane")
        val selectedImproved = MutableMembershipPixelSet("selected-improved")
        val selectedUnchanged = MutableMembershipPixelSet("selected-unchanged")
        val selectedRegressed = MutableMembershipPixelSet("selected-regressed")
        val sourceLocal = MutableMembershipPixelSet("source-locale-plausible")
        val coverageComposition = MutableMembershipPixelSet("coverage-composition-plausible")
        val mixed = MutableMembershipPixelSet("mixed")
        val insufficient = MutableMembershipPixelSet("insufficient")
        val selectedPixels = mutableListOf<PreCorrectionGeometryCoveragePixelAudit>()
        var sourceLocalRecovered = 0

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
                if (membership.coverageAlphaByte > 0 && membership.transparentSourceAlphaByte > 0) {
                    eligible.add(membership)
                }
                if (sourceCoverageFullSceneCandidateSelect(audit)) {
                    selected.add(membership)
                    selectedPixels += audit
                    if (membership.category == sourceLocal.id) {
                        sourceLocalRecovered++
                    }
                    when {
                        pixel.deltaVsCurrent < 0 -> selectedImproved.add(membership)
                        pixel.deltaVsCurrent > 0 -> selectedRegressed.add(membership)
                        else -> selectedUnchanged.add(membership)
                    }
                }
            }
        }

        val allStats = allAudited.toStats()
        val selectedStats = selected.toStats()
        val sourceLocalTruth = sourceLocal.count
        val precision = if (selected.count == 0) 0.0 else sourceLocalRecovered.toDouble() / selected.count.toDouble()
        val recall = if (sourceLocalTruth == 0) 0.0 else sourceLocalRecovered.toDouble() / sourceLocalTruth.toDouble()
        val simulatedFullSceneAfterResidual =
            allStats.base.beforeResidual - selectedStats.base.beforeResidual + selectedStats.base.afterResidual
        val classification = sourceCoverageFullSceneCandidateClassification(
            selectedCount = selected.count,
            sourceLocalRecovered = sourceLocalRecovered,
            sourceLocalTruth = sourceLocalTruth,
            regressedCount = selectedRegressed.count,
            fullSceneBeforeResidual = allStats.base.beforeResidual,
            fullSceneAfterResidual = simulatedFullSceneAfterResidual,
        )
        val selectedSamples = selectedPixels
            .sortedWith(compareBy<PreCorrectionGeometryCoveragePixelAudit> { it.membership.pixel.y }.thenBy { it.membership.pixel.x })
            .take(FOR389_SELECTED_SAMPLE_LIMIT)
        return """
            {
              "schemaVersion": 1,
              "linear": "FOR-389",
              "sceneId": "m60-f16-source-coverage-full-scene-candidate-for389",
              "sourceSceneId": "m60-f16-composition-metadata-audit-for388",
              "adapter": ${adapter.jsonString()},
              "producer": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
              "producerMode": "-Dkanvas.sceneEvidence.write=true",
              "sourceMemory": "global/kanvas/ticket-drafts/draft-prochain-ticket-m60-f16-evaluer-candidat-source-couverture-full-scene-apres-for-388",
              "sourceFinding": "global/kanvas/findings/for-388-prouve-un-candidat-source-couverture-m60-f16-8-utiles-0-regression-sans-activation",
              "requiredFor388Decision": "M60_F16_COMPOSITION_METADATA_AUDIT_RECORDED",
              "requiredFor388Classification": "usable-correction-candidate",
              "requiredFor387Decision": "M60_F16_RESIDUAL_FRINGE_DISCRIMINATOR_AUDIT_RECORDED",
              "requiredFor387Classification": "fringe-discriminator-too-broad",
              "decision": "M60_F16_SOURCE_COVERAGE_FULL_SCENE_CANDIDATE_EVALUATED",
              "classification": ${classification.jsonString()},
              "allowedClassifications": [
                "full-scene-defendable",
                "full-scene-regresses",
                "runtime-metadata-insufficient"
              ],
              "auditDoesNotProduceCorrection": true,
              "auditDoesNotApplyRendererChange": true,
              "correctionAppliedByDefault": false,
              "correctionPredicateEnabled": false,
              "explicitOptInGuard": {
                "guardId": ${FOR389_CANDIDATE_GUARD_PROPERTY.jsonString()},
                "enabledByDefault": false,
                "mode": "diagnostic-simulation-only",
                "runtimeHookInstalled": false
              },
              "candidate": {
                "id": "source-color-and-oriented-coverage-lane",
                "description": "FOR-388 source-local blue contribution plus oriented coverage side, evaluated across the full M60 F16 scene.",
                "selectionMethod": "(round-round && transparentSourceColorFamily == blue-dominant-source && orientedCoverageSide in {west-terminal,north-west-solid}) || (butt-bevel && transparentSourceColorFamily == blue-dominant-source && orientedCoverageSide == north-east-solid && coverageAlphaByte >= 160)",
                "scope": "full-scene",
                "usesSkiaReferenceForSelection": false,
                "usesProbeOutcomeForSelection": false,
                "usesProbeResidualForSelection": false,
                "usesDeltaVsCurrentForSelection": false,
                "usesCurrentResidualForSelection": false,
                "usesFor387TruthAsSelection": false,
                "usesFor388TruthAsSelection": false,
                "usesFor379MembershipAsPrimary": false,
                "usesFor382CategoryForSelection": false,
                "selectedPixels": ${selected.count},
                "improvedPixels": ${selectedImproved.count},
                "unchangedPixels": ${selectedUnchanged.count},
                "regressedPixels": ${selectedRegressed.count},
                "sourceLocalRecovered": $sourceLocalRecovered,
                "sourceLocalTruth": $sourceLocalTruth,
                "precision": ${String.format(Locale.US, "%.4f", precision)},
                "recall": ${String.format(Locale.US, "%.4f", recall)},
                "selectedResidualIfApplied": ${selectedStats.toJson().prependIndent("  ").trimStart()}
              },
              "fullSceneImpactIfAppliedToSelectedPixelsOnly": {
                "baseUncorrectedFullSceneResidual": ${allStats.base.beforeResidual},
                "simulatedAfterResidual": $simulatedFullSceneAfterResidual,
                "deltaVsCurrent": ${simulatedFullSceneAfterResidual - allStats.base.beforeResidual},
                "gainVsCurrent": ${allStats.base.beforeResidual - simulatedFullSceneAfterResidual},
                "baseUncorrectedMismatchPixels": ${uncorrectedResidualStats.mismatchPixels},
                "fullProbeMismatchPixelsNotUsedForSelection": ${correctedResidualStats.mismatchPixels},
                "uncorrectedSimilarity": ${String.format(Locale.US, "%.2f", uncorrectedExperimentalGpuCmp.similarity)},
                "fullProbeSimilarityNotUsedForSelection": ${String.format(Locale.US, "%.2f", correctedExperimentalGpuCmp.similarity)}
              },
              "truthSetsForEvaluationOnly": {
                "allAuditedPixels": ${allStats.toJson().prependIndent("  ").trimStart()},
                "nonzeroCoverageSourceAlphaPixels": ${eligible.toStats().toJson().prependIndent("  ").trimStart()},
                "sourceLocalPixels": ${sourceLocal.toStats().toJson().prependIndent("  ").trimStart()},
                "coverageCompositionPixels": ${coverageComposition.toStats().toJson().prependIndent("  ").trimStart()},
                "mixedPixels": ${mixed.toStats().toJson().prependIndent("  ").trimStart()},
                "insufficientPixels": ${insufficient.toStats().toJson().prependIndent("  ").trimStart()},
                "selectedImprovedPixels": ${selectedImproved.toStats().toJson().prependIndent("  ").trimStart()},
                "selectedUnchangedPixels": ${selectedUnchanged.toStats().toJson().prependIndent("  ").trimStart()},
                "selectedRegressedPixels": ${selectedRegressed.toStats().toJson().prependIndent("  ").trimStart()}
              },
              "metadataAvailability": {
                "transparentSourceRgbaAvailable": true,
                "coverageOrthogonalNeighborhoodAvailable": true,
                "strokeBandCapJoinAvailable": true,
                "coverageAlphaByteAvailable": true,
                "selectionNeedsReference": false,
                "selectionNeedsProbe": false,
                "selectionNeedsResidual": false,
                "selectionNeedsDeltaVsCurrent": false,
                "rendererRuntimePredicateReady": false
              },
              "fullSceneGuard": {
                "fallbackReasonStable": "coverage.stroke-cap-join-visual-parity-below-threshold",
                "refusalsChanged": false,
                "normalRouteRemainsRefused": true,
                "diagnosticOnly": true
              },
              "selectedPixelSamples": [
            ${selectedSamples.joinToString(",\n") { sourceCoverageFullSceneCandidatePixelJson(it).prependIndent("    ") }}
              ],
              "classificationReason": ${sourceCoverageFullSceneCandidateClassificationReason(classification).jsonString()},
              "nextMove": ${sourceCoverageFullSceneCandidateNextMove(classification).jsonString()},
              "nonGoalsPreserved": {
                "rendererBehaviorChanged": false,
                "runtimeBehaviorChanged": false,
                "gpuOrWgslChanged": false,
                "geometryProductionChanged": false,
                "coverageProductionChanged": false,
                "fallbackChanged": false,
                "scoreChanged": false,
                "thresholdChanged": false,
                "promotionChanged": false,
                "correctionEnabled": false,
                "probeEnabledByDefault": false
              },
              "validationCommands": [
                "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
                "rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py",
                "rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py",
                "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
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

    private fun residualFringeDiscriminatorCandidates(): List<ResidualFringeDiscriminatorCandidate> =
        listOf(
            ResidualFringeDiscriminatorCandidate(
                id = "round-local-window-alpha-160-or-butt-edge-alpha-96",
                description = "Keeps the round useful lane at alpha >= 160 and the narrowest butt-bevel fringe lane at alpha >= 96.",
                selectionMethod = "(round-round && bandLocalX in 39..45 && coverageAlphaByte >= 160) || (butt-bevel && bandEdgeDistance <= 1 && coverageAlphaByte >= 96)",
            ) { pixel ->
                (
                    pixel.membership.pixel.strokeBand == "round-round" &&
                        pixel.bandLocalX in 39..45 &&
                        pixel.membership.coverageAlphaByte >= 160
                    ) ||
                    (
                        pixel.membership.pixel.strokeBand == "butt-bevel" &&
                            pixel.bandEdgeDistance <= 1 &&
                            pixel.membership.coverageAlphaByte >= 96
                        )
            },
            ResidualFringeDiscriminatorCandidate(
                id = "round-transition-or-butt-terminal-edge",
                description = "Uses orthogonal coverage topology to keep transition pixels on the round lane and terminal edge pixels on the butt lane.",
                selectionMethod = "(round-round && bandLocalX in 39..45 && orthogonalPartialCount >= 1) || (butt-bevel && bandLocalX <= 18 && bandEdgeDistance <= 1)",
            ) { pixel ->
                (
                    pixel.membership.pixel.strokeBand == "round-round" &&
                        pixel.bandLocalX in 39..45 &&
                        pixel.coverageNeighborhood.orthogonalPartialCount >= 1
                    ) ||
                    (
                        pixel.membership.pixel.strokeBand == "butt-bevel" &&
                            pixel.bandLocalX <= 18 &&
                            pixel.bandEdgeDistance <= 1
                        )
            },
            ResidualFringeDiscriminatorCandidate(
                id = "round-source-coverage-equal-alpha-160",
                description = "Tests whether the useful round lane separates by equal source/coverage alpha and the 160 alpha lane.",
                selectionMethod = "round-round && bandLocalX in 39..45 && coverageAlphaByte == transparentSourceAlphaByte && coverageAlphaByte >= 160",
            ) { pixel ->
                pixel.membership.pixel.strokeBand == "round-round" &&
                    pixel.bandLocalX in 39..45 &&
                    pixel.membership.coverageAlphaByte == pixel.membership.transparentSourceAlphaByte &&
                    pixel.membership.coverageAlphaByte >= 160
            },
            ResidualFringeDiscriminatorCandidate(
                id = "butt-low-edge-source-coverage-equal",
                description = "Checks the butt-bevel portion of the residual set using edge distance and source/coverage alpha relation only.",
                selectionMethod = "butt-bevel && bandLocalX <= 18 && bandEdgeDistance <= 1 && coverageAlphaByte == transparentSourceAlphaByte",
            ) { pixel ->
                pixel.membership.pixel.strokeBand == "butt-bevel" &&
                    pixel.bandLocalX <= 18 &&
                    pixel.bandEdgeDistance <= 1 &&
                    pixel.membership.coverageAlphaByte == pixel.membership.transparentSourceAlphaByte
            },
            ResidualFringeDiscriminatorCandidate(
                id = "alpha-160-fringe-transition",
                description = "Keeps alpha-160 fringe pixels that have at least one partial orthogonal neighbor.",
                selectionMethod = "coverageAlphaByte >= 160 && transparentSourceAlphaByte >= 160 && orthogonalPartialCount >= 1",
            ) { pixel ->
                pixel.membership.coverageAlphaByte >= 160 &&
                    pixel.membership.transparentSourceAlphaByte >= 160 &&
                    pixel.coverageNeighborhood.orthogonalPartialCount >= 1
            },
            ResidualFringeDiscriminatorCandidate(
                id = "local-window-edge-distance-le-17",
                description = "Keeps the full useful lane while excluding only the farthest FOR-386 residual butt-bevel edge pixels.",
                selectionMethod = "bandEdgeDistance <= 17 && coverageAlphaByte >= 96 && transparentSourceAlphaByte >= 96",
            ) { pixel ->
                pixel.bandEdgeDistance <= 17 &&
                    pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.membership.transparentSourceAlphaByte >= 96
            },
            ResidualFringeDiscriminatorCandidate(
                id = "local-window-edge-distance-le-8",
                description = "Narrows the FOR-386 local fringe window by requiring proximity to the stroke-band edge.",
                selectionMethod = "bandEdgeDistance <= 8 && coverageAlphaByte >= 96 && transparentSourceAlphaByte >= 96",
            ) { pixel ->
                pixel.bandEdgeDistance <= 8 &&
                    pixel.membership.coverageAlphaByte >= 96 &&
                    pixel.membership.transparentSourceAlphaByte >= 96
            },
        )

    private fun residualFringeDiscriminatorClassification(
        bestCandidate: MutableResidualFringeDiscriminatorCandidateStats?,
        sourceLocalTruth: Int,
    ): String =
        when {
            bestCandidate == null || sourceLocalTruth == 0 -> "metadata-insufficient"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth &&
                bestCandidate.regressedIncluded == 0 -> "fringe-discriminator-defendable"
            bestCandidate.sourceLocalRecovered == sourceLocalTruth -> "fringe-discriminator-too-broad"
            bestCandidate.sourceLocalRecovered > 0 -> "fringe-discriminator-insufficient"
            else -> "metadata-insufficient"
        }

    private fun residualFringeDiscriminatorClassificationReason(classification: String): String =
        when (classification) {
            "fringe-discriminator-defendable" ->
                "A pre-correction fringe metadata candidate separates all 8 source-local pixels from the 27 remaining FOR-386 regressions inside the diagnostic selection. It remains disabled until a separate correction ticket proves full-scene safety."
            "fringe-discriminator-too-broad" ->
                "The best residual fringe discriminator keeps all 8 useful pixels, but still includes regressed pixels from the FOR-386 selection."
            "fringe-discriminator-insufficient" ->
                "The tested residual fringe metadata reduces the 27 remaining regressions only by dropping at least one useful source-local pixel."
            else ->
                "The available pre-correction fringe metadata does not provide a useful discriminator for the FOR-386 residual selection."
        }

    private fun residualFringeDiscriminatorNextMove(
        classification: String,
        bestCandidate: MutableResidualFringeDiscriminatorCandidateStats?,
    ): String =
        when (classification) {
            "fringe-discriminator-defendable" ->
                "Keep FOR-387 diagnostic-only. Create a separate bounded opt-in correction ticket only after proving the candidate outside this 35-pixel audit scope with full-scene score and fallback stability."
            "fringe-discriminator-too-broad" ->
                "Do not enable the correction. Export a renderer-owned source/coverage composition or fringe-topology signal finer than local band coordinates."
            "fringe-discriminator-insufficient" ->
                "Do not enable the correction. Preserve this audit and test metadata that recovers the dropped source-local pixels without reintroducing the residual regressions."
            else ->
                "Do not enable the correction. The next step is a new renderer metadata export before another predicate attempt."
        } + (bestCandidate?.let { " Best candidate: ${it.candidate.id}." } ?: "")

    private fun residualFringePixelJson(pixel: PreCorrectionGeometryCoveragePixelAudit): String {
        val membership = pixel.membership
        val result = when {
            membership.category == "source-locale-plausible" -> "source-local-useful"
            membership.pixel.deltaVsCurrent > 0 -> "regressed-if-corrected"
            else -> "non-regressed"
        }
        val suffix = """
          "for387EvaluationResult": ${result.jsonString()},
          "fringeTopology": ${residualFringeTopology(pixel).jsonString()},
          "sourceCoverageRelation": ${sourceCoverageRelation(pixel).jsonString()}
        """.trimIndent().prependIndent("  ")
        return pixel.toJson().trim().replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private fun sourceCoverageFullSceneCandidateSelect(pixel: PreCorrectionGeometryCoveragePixelAudit): Boolean {
        val sourceFamily = transparentSourceColorFamily(pixel.membership.transparentSourceRgba)
        val orientedSide = orientedCoverageSide(pixel.coverageNeighborhood)
        return (
            pixel.membership.pixel.strokeBand == "round-round" &&
                sourceFamily == "blue-dominant-source" &&
                orientedSide in setOf("west-terminal", "north-west-solid")
            ) ||
            (
                pixel.membership.pixel.strokeBand == "butt-bevel" &&
                    sourceFamily == "blue-dominant-source" &&
                    orientedSide == "north-east-solid" &&
                    pixel.membership.coverageAlphaByte >= 160
                )
    }

    private fun sourceCoverageFullSceneCandidateClassification(
        selectedCount: Int,
        sourceLocalRecovered: Int,
        sourceLocalTruth: Int,
        regressedCount: Int,
        fullSceneBeforeResidual: Int,
        fullSceneAfterResidual: Int,
    ): String =
        when {
            selectedCount == 0 || sourceLocalTruth == 0 -> "runtime-metadata-insufficient"
            sourceLocalRecovered == sourceLocalTruth &&
                regressedCount == 0 &&
                fullSceneAfterResidual <= fullSceneBeforeResidual -> "full-scene-defendable"
            regressedCount > 0 || fullSceneAfterResidual > fullSceneBeforeResidual -> "full-scene-regresses"
            else -> "runtime-metadata-insufficient"
        }

    private fun sourceCoverageFullSceneCandidateClassificationReason(classification: String): String =
        when (classification) {
            "full-scene-defendable" ->
                "The candidate selects every source-local evaluation pixel across the full scene, selects no pixels regressed by the probe, and reduces the simulated full-scene residual when applied only to selected pixels."
            "full-scene-regresses" ->
                "The candidate selects at least one full-scene pixel that the probe would regress, or its selected-pixel simulation increases full-scene residual."
            else ->
                "The available runtime metadata does not recover the full source-local evaluation set without relying on probe or residual data."
        }

    private fun sourceCoverageFullSceneCandidateNextMove(classification: String): String =
        when (classification) {
            "full-scene-defendable" ->
                "Keep the correction disabled by default. A later implementation ticket may wire this predicate behind an explicit opt-in guard and repeat fallback, score, and CI evidence."
            "full-scene-regresses" ->
                "Do not wire the predicate. Capture narrower renderer-owned metadata before another correction attempt."
            else ->
                "Keep the correction disabled and capture the missing runtime metadata needed to make the predicate independent of diagnostic truth."
        }

    private fun sourceCoverageFullSceneCandidatePixelJson(pixel: PreCorrectionGeometryCoveragePixelAudit): String {
        val result = when {
            pixel.membership.category == "source-locale-plausible" -> "source-local-useful"
            pixel.membership.pixel.deltaVsCurrent > 0 -> "regressed-if-corrected"
            pixel.membership.pixel.deltaVsCurrent < 0 -> "improved-if-corrected"
            else -> "unchanged-if-corrected"
        }
        val suffix = """
          "for389EvaluationResult": ${result.jsonString()},
          "transparentSourceColorFamily": ${transparentSourceColorFamily(pixel.membership.transparentSourceRgba).jsonString()},
          "orientedCoverageSide": ${orientedCoverageSide(pixel.coverageNeighborhood).jsonString()},
          "sourceCoverageRelation": ${sourceCoverageRelation(pixel).jsonString()}
        """.trimIndent().prependIndent("  ")
        return pixel.toJson().trim().replace(Regex("\n\\s*}$"), ",\n$suffix\n}")
    }

    private fun transparentSourceColorFamily(rgba: IntArray): String {
        val red = rgba[0]
        val green = rgba[1]
        val blue = rgba[2]
        return when {
            blue > red + 40 && blue > green + 40 -> "blue-dominant-source"
            green > red + 30 && green > blue + 30 -> "green-dominant-source"
            red > green + 30 && red > blue + 30 -> "red-dominant-source"
            else -> "mixed-source"
        }
    }

    private fun orientedCoverageSide(neighborhood: CoverageOrthogonalNeighborhood): String {
        val solid = mutableSetOf<String>()
        val zero = mutableSetOf<String>()
        val partial = mutableSetOf<String>()
        for ((name, value) in listOf(
            "north" to neighborhood.north,
            "south" to neighborhood.south,
            "west" to neighborhood.west,
            "east" to neighborhood.east,
        )) {
            when (value) {
                255 -> solid += name
                0 -> zero += name
                else -> partial += name
            }
        }
        return when {
            solid == setOf("north", "west") && zero == setOf("south", "east") && partial.isEmpty() ->
                "north-west-solid"
            solid == setOf("south", "east") && zero == setOf("north", "west") && partial.isEmpty() ->
                "south-east-solid"
            solid == setOf("south", "west") && zero == setOf("north", "east") && partial.isEmpty() ->
                "south-west-solid"
            solid == setOf("north", "east") && zero == setOf("south", "west") && partial.isEmpty() ->
                "north-east-solid"
            solid == setOf("west") && zero == setOf("north", "south", "east") && partial.isEmpty() ->
                "west-terminal"
            else -> "other"
        }
    }

    private fun residualFringeTopology(pixel: PreCorrectionGeometryCoveragePixelAudit): String =
        when {
            pixel.coverageNeighborhood.orthogonalPartialCount >= 2 -> "multi-partial-transition"
            pixel.coverageNeighborhood.orthogonalPartialCount == 1 -> "single-partial-transition"
            pixel.coverageNeighborhood.orthogonalNonZeroCount <= 1 -> "isolated-or-terminal"
            pixel.coverageNeighborhood.orthogonalNonZeroCount == 2 -> "two-sided-solid-fringe"
            else -> "dense-solid-fringe"
        }

    private fun sourceCoverageRelation(pixel: PreCorrectionGeometryCoveragePixelAudit): String {
        val coverage = pixel.membership.coverageAlphaByte
        val source = pixel.membership.transparentSourceAlphaByte
        return when {
            coverage == source -> "coverage-equals-transparent-source-alpha"
            coverage > source -> "coverage-greater-than-transparent-source-alpha"
            else -> "coverage-less-than-transparent-source-alpha"
        }
    }

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

    private fun imageResidual(candidate: SkBitmap, reference: SkBitmap): Int {
        require(candidate.width == reference.width && candidate.height == reference.height)
        var residual = 0
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                residual += sampleResidual(reference.getPixel(x, y), candidate.getPixel(x, y))
            }
        }
        return residual
    }

    private fun changedPixels(a: SkBitmap, b: SkBitmap): List<Pair<Int, Int>> {
        require(a.width == b.width && a.height == b.height)
        val out = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) {
                    out += x to y
                }
            }
        }
        return out
    }

    private fun pixelResidualDeltas(current: SkBitmap, corrected: SkBitmap, reference: SkBitmap): PixelResidualDeltas {
        require(current.width == corrected.width && current.height == corrected.height)
        require(current.width == reference.width && current.height == reference.height)
        var improved = 0
        var regressed = 0
        var unchanged = 0
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val referencePixel = reference.getPixel(x, y)
                val currentResidual = sampleResidual(referencePixel, current.getPixel(x, y))
                val correctedResidual = sampleResidual(referencePixel, corrected.getPixel(x, y))
                when {
                    correctedResidual < currentResidual -> improved++
                    correctedResidual > currentResidual -> regressed++
                    else -> unchanged++
                }
            }
        }
        return PixelResidualDeltas(improved, regressed, unchanged)
    }

    private data class PixelResidualDeltas(
        val improvedPixels: Int,
        val regressedPixels: Int,
        val unchangedPixels: Int,
    )

    private data class PredicateCorrectionPixel(
        val x: Int,
        val y: Int,
        val reference: Int,
        val current: Int,
        val corrected: Int,
        val currentResidual: Int,
        val correctedResidual: Int,
    ) {
        fun toJson(): String = """
            {
              "x": $x,
              "y": $y,
              "referenceRgba": ${rgbaJson(reference)},
              "currentRgba": ${rgbaJson(current)},
              "correctedRgba": ${rgbaJson(corrected)},
              "currentResidual": $currentResidual,
              "correctedResidual": $correctedResidual,
              "deltaCorrectedMinusCurrent": ${correctedResidual - currentResidual},
              "gainVsCurrent": ${currentResidual - correctedResidual}
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

    private class ResidualFringeDiscriminatorCandidate(
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

    private class MutableResidualFringeDiscriminatorCandidateStats(
        val candidate: ResidualFringeDiscriminatorCandidate,
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
                    "fringe-discriminator-defendable"
                sourceLocalRecovered == sourceLocalTruth && regressedIncluded > 0 ->
                    "fringe-discriminator-too-broad"
                sourceLocalRecovered in 1 until sourceLocalTruth ->
                    "fringe-discriminator-insufficient"
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
                  "scope": "for386-source-fringe-band-local-window-selected-pixels",
                  "usesSkiaReferenceForSelection": false,
                  "usesProbeOutcomeForSelection": false,
                  "usesProbeResidualForSelection": false,
                  "usesDeltaVsCurrentForSelection": false,
                  "usesFor379MembershipAsPrimary": false,
                  "usesFor383PredicateAsPrimary": false,
                  "usesFor384PredicateAsPrimary": false,
                  "usesFor385PredicateAsPrimary": false,
                  "usesFor386PredicateAsPrimary": false,
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
                  "diagnosticDecision": ${if (candidateClass(sourceLocalTruth) == "fringe-discriminator-defendable") "\"candidate-diagnostic-only\"" else "\"reject\""},
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
                  "runtimeBlocker": "FOR-387 is diagnostic-only; any candidate must be proven outside the FOR-386 residual audit scope before renderer use."
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

    private class MutableResidualFringeMetadataBreakdown {
        private val bandCapJoin = linkedMapOf<String, Int>()
        private val bandLocalXBucket = linkedMapOf<String, Int>()
        private val bandEdgeDistanceBucket = linkedMapOf<String, Int>()
        private val coverageAlphaLane = linkedMapOf<String, Int>()
        private val transparentSourceAlphaLane = linkedMapOf<String, Int>()
        private val orthogonalCoverageShape = linkedMapOf<String, Int>()
        private val fringeTopology = linkedMapOf<String, Int>()
        private val sourceCoverageRelationBuckets = linkedMapOf<String, Int>()
        private var count: Int = 0

        fun add(pixel: PreCorrectionGeometryCoveragePixelAudit) {
            count++
            increment(
                bandCapJoin,
                "${pixel.membership.pixel.strokeBand}|cap=${pixel.membership.pixel.cap}|join=${pixel.membership.pixel.join}",
            )
            increment(
                bandLocalXBucket,
                "${pixel.membership.pixel.strokeBand}|${bucket8(pixel.bandLocalX)}",
            )
            increment(bandEdgeDistanceBucket, edgeDistanceBucket(pixel.bandEdgeDistance))
            increment(coverageAlphaLane, alphaLane(pixel.membership.coverageAlphaByte))
            increment(transparentSourceAlphaLane, alphaLane(pixel.membership.transparentSourceAlphaByte))
            increment(
                orthogonalCoverageShape,
                "center=${pixel.coverageNeighborhood.center}|n=${pixel.coverageNeighborhood.north}|s=${pixel.coverageNeighborhood.south}|w=${pixel.coverageNeighborhood.west}|e=${pixel.coverageNeighborhood.east}|nz=${pixel.coverageNeighborhood.orthogonalNonZeroCount}|partial=${pixel.coverageNeighborhood.orthogonalPartialCount}",
            )
            increment(fringeTopology, residualFringeTopology(pixel))
            increment(sourceCoverageRelationBuckets, sourceCoverageRelation(pixel))
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
                  "orthogonalCoverageShape": ${mapJson(orthogonalCoverageShape).prependIndent("  ").trimStart()},
                  "fringeTopology": ${mapJson(fringeTopology).prependIndent("  ").trimStart()},
                  "sourceCoverageRelation": ${mapJson(sourceCoverageRelationBuckets).prependIndent("  ").trimStart()}
                }
            """.trimIndent()

        private fun increment(map: MutableMap<String, Int>, key: String) {
            map[key] = (map[key] ?: 0) + 1
        }

        private fun bucket8(value: Int): String {
            val start = (value / 8) * 8
            val end = start + 7
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

        private fun residualFringeTopology(pixel: PreCorrectionGeometryCoveragePixelAudit): String =
            when {
                pixel.coverageNeighborhood.orthogonalPartialCount >= 2 -> "multi-partial-transition"
                pixel.coverageNeighborhood.orthogonalPartialCount == 1 -> "single-partial-transition"
                pixel.coverageNeighborhood.orthogonalNonZeroCount <= 1 -> "isolated-or-terminal"
                pixel.coverageNeighborhood.orthogonalNonZeroCount == 2 -> "two-sided-solid-fringe"
                else -> "dense-solid-fringe"
            }

        private fun sourceCoverageRelation(pixel: PreCorrectionGeometryCoveragePixelAudit): String {
            val coverage = pixel.membership.coverageAlphaByte
            val source = pixel.membership.transparentSourceAlphaByte
            return when {
                coverage == source -> "coverage-equals-transparent-source-alpha"
                coverage > source -> "coverage-greater-than-transparent-source-alpha"
                else -> "coverage-less-than-transparent-source-alpha"
            }
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
        private const val FOR398_BOUNDED_RUNTIME_CORRECTION_PROPERTY =
            "kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled"
        private const val FOR399_APPLICATION_POINT_DIAGNOSTIC_PROPERTY =
            "kanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled"
        private const val FOR400_COVERAGE_STENCIL_CONTRIBUTION_MAP_PROPERTY =
            "kanvas.webgpu.m60F16CoverageStencilContributionMap.enabled"
        private const val FOR401_FINAL_RESIDUAL_ORIGIN_MAP_PROPERTY =
            "kanvas.webgpu.m60F16FinalResidualOriginMap.enabled"
        private const val FOR402_PASS_WRITE_PROBE_PROPERTY =
            "kanvas.webgpu.m60F16PassWriteProbe.enabled"
        private const val FOR404_AA_STENCIL_COVER_RUNTIME_HOOK_PROPERTY =
            "kanvas.webgpu.m60F16DirectPassWriteHook.enabled"
        private const val FOR408_AA_STENCIL_COVER_CONTRIBUTION_ISOLATION_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled"
        private const val FOR409_SOURCE_OVER_REPLAY_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled"
        private const val FOR410_PREDRAW_DST_READBACK_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled"
        private const val FOR412_SHADER_RETURN_DIAGNOSTIC_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled"
        private const val FOR417_ISOLATED_COLOR_TARGET_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverIsolatedColorTarget.enabled"
        private const val FOR418_STORAGE_COLOR_TARGET_COMPARISON_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverStorageColorTargetComparison.enabled"
        private const val FOR419_SHADER_RETURN_STORAGE_ZERO_CAUSE_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled"
        private const val FOR420_FINAL_WGSL_DIAGNOSTIC_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled"
        private const val FOR412_MATCH_TOLERANCE = 0.000001f
        private const val FOR417_RECONSTRUCTION_TOLERANCE = 0.0006f
        private const val FOR423_REFERENCE_TOLERANCE = 2f / 255f
        private const val FOR401_FINAL_RESIDUAL_ORIGIN_MAP_SAMPLE_LIMIT = 16
        private const val M60_F16_BAND_METADATA_TRANSPORT_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
        private const val M60_F16_FRAGMENT_LANE_DIAGNOSTIC_PROPERTY =
            "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
        private const val FOR389_CANDIDATE_GUARD_PROPERTY =
            "kanvas.webgpu.m60F16SourceCoverageFullSceneCandidate.enabled"
        private const val FOR389_SELECTED_SAMPLE_LIMIT = 64
        private val M60_F16_FRAGMENT_LANE_EXPECTED_PIXELS = listOf(
            93 to 74,
            92 to 75,
            91 to 76,
            17 to 77,
            90 to 77,
            89 to 78,
            88 to 79,
            87 to 80,
        )
        private val M60_F16_DIRECT_PASS_WRITE_HOOK_POINTS = listOf(
            92 to 75,
            91 to 76,
            90 to 77,
            89 to 78,
            88 to 79,
            87 to 80,
            101 to 37,
            102 to 37,
            99 to 38,
            100 to 38,
            101 to 38,
            102 to 38,
            103 to 38,
            104 to 38,
            98 to 39,
            99 to 39,
        )

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
