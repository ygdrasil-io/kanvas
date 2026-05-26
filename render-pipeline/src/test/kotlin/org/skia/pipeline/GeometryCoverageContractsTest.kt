package org.skia.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeometryCoverageContractsTest {
    @Test
    fun reasonCodesAreStable() {
        assertEquals("geometry.nonfinite-input", StandardGeometryReason.NonFiniteInput.code)
        assertEquals("geometry.unsupported-perspective", StandardGeometryReason.UnsupportedPerspective.code)
        assertEquals("geometry.stroke-degenerate", StandardGeometryReason.StrokeDegenerate.code)
        assertEquals("geometry.path-effect-unsupported", StandardGeometryReason.PathEffectUnsupported.code)
        assertEquals("geometry.clip-stack-unsupported", StandardGeometryReason.ClipStackUnsupported.code)
        assertEquals(
            "geometry.compute-tessellation-not-enabled",
            StandardGeometryReason.ComputeTessellationNotEnabled.code,
        )

        assertEquals("coverage.span-runs-unsupported", StandardCoverageReason.SpanRunsUnsupported.code)
        assertEquals("coverage.alpha-mask-unsupported", StandardCoverageReason.AlphaMaskUnsupported.code)
        assertEquals("coverage.stencil-cover-unavailable", StandardCoverageReason.StencilCoverUnavailable.code)
        assertEquals("coverage.edge-count-exceeded", StandardCoverageReason.EdgeCountExceeded.code)
        assertEquals("coverage.atlas-policy-unavailable", StandardCoverageReason.AtlasPolicyUnavailable.code)
        assertEquals(
            "coverage.arbitrary-aa-clip-unsupported",
            StandardCoverageReason.ArbitraryAaClipUnsupported.code,
        )
    }

    @Test
    fun fullCoverageLowersToCoverageModelFull() {
        val result = CoveragePlanAdapter.lower(CoveragePlan.Full)

        val model = assertIs<CoverageLoweringResult.CoverageModelResult>(result)
        assertEquals(CoverageModel.Full, model.coverage)
    }

    @Test
    fun analyticRectLowersToCoverageModelAnalyticRect() {
        val bounds = FloatRect(1f, 2f, 9f, 10f)
        val result = CoveragePlanAdapter.lower(CoveragePlan.AnalyticRect(bounds, aa = true))

        val model = assertIs<CoverageLoweringResult.CoverageModelResult>(result)
        assertEquals(CoverageModel.AnalyticRect(bounds, aa = true), model.coverage)
    }

    @Test
    fun alphaMaskLowersToCoverageModelAlphaMask() {
        val bounds = IntRect(1, 2, 9, 10)
        val result = CoveragePlanAdapter.lower(
            CoveragePlan.AlphaMask(AlphaMaskRef("mask-a"), bounds, MaskFormat.A8),
        )

        val model = assertIs<CoverageLoweringResult.CoverageModelResult>(result)
        assertEquals(CoverageModel.AlphaMask(bounds, MaskFormat.A8), model.coverage)
    }

    @Test
    fun spanRunsLowerToCoverageModelSpan() {
        val result = CoveragePlanAdapter.lower(CoveragePlan.SpanRuns(IntRect(0, 0, 4, 4)))

        val model = assertIs<CoverageLoweringResult.CoverageModelResult>(result)
        assertEquals(CoverageModel.Span, model.coverage)
    }

    @Test
    fun pathCoverageLowersToExplicitBackendStrategy() {
        val result = CoveragePlanAdapter.lower(
            CoveragePlan.PathCoverage(fillType = PathFillType.Winding, aa = true, inverse = false),
        )

        val strategyResult = assertIs<CoverageLoweringResult.StrategyResult>(result)
        val strategy = assertIs<CoverageBackendStrategy.CpuSpanPath>(strategyResult.strategy)
        assertEquals(PathFillType.Winding, strategy.fillType)
        assertEquals(true, strategy.aa)
        assertEquals(false, strategy.inverse)
    }

    @Test
    fun coverageAtlasLowersToExplicitBackendStrategy() {
        val bounds = IntRect(2, 3, 10, 11)
        val result = CoveragePlanAdapter.lower(
            CoveragePlan.CoverageAtlas(
                ref = CoverageAtlasRef("atlas-a"),
                bounds = bounds,
                cachePolicy = CoverageCachePolicy.FrameLocal,
            ),
        )

        val strategyResult = assertIs<CoverageLoweringResult.StrategyResult>(result)
        val strategy = assertIs<CoverageBackendStrategy.CoverageAtlasSample>(strategyResult.strategy)
        assertEquals(CoverageAtlasRef("atlas-a"), strategy.ref)
        assertEquals(bounds, strategy.bounds)
        assertEquals(CoverageCachePolicy.FrameLocal, strategy.cachePolicy)
    }

    @Test
    fun unsupportedCoverageBridgesToFallbackReasonCodeOnly() {
        val result = CoveragePlanAdapter.lower(
            CoveragePlan.Unsupported(StandardCoverageReason.EdgeCountExceeded),
        )

        val strategyResult = assertIs<CoverageLoweringResult.StrategyResult>(result)
        val fallback = assertIs<CoverageBackendStrategy.UnsupportedFallback>(strategyResult.strategy)
        assertEquals(StandardCoverageReason.EdgeCountExceeded, fallback.reason)
        assertEquals("coverage.edge-count-exceeded", fallback.fallback.reason)
        assertEquals(FallbackPlan.RefuseDiagnostic("coverage.edge-count-exceeded"), fallback.fallback)
    }

    @Test
    fun rectDescriptorDumpIsStable() {
        val rect = FloatRect(0f, 0f, 20f, 10f)
        val geometry = GeometryPlan.Supported(
            primitive = GeometryPrimitive.Rect(source = rect, device = rect),
            bounds = GeometryBounds(conservative = rect, tight = rect),
            transform = TransformFacts(
                matrix = MatrixSpec.Identity,
                isAxisAligned = true,
                hasPerspective = false,
                maxScale = 1f,
                isInvertible = true,
            ),
            clip = ClipInteraction.None,
        )
        val coverage = CoveragePlan.AnalyticRect(bounds = rect, aa = false)
        val dump = CoverageDescriptorDump(
            geometryPlan = geometry,
            coveragePlan = coverage,
            loweringResult = CoveragePlanAdapter.lower(coverage),
        ).dump()

        assertEquals(
            """
            GeometryCoverageDescriptor(v1)
            geometry=Supported(Rect(device=0.0,0.0,20.0,10.0),clip=None)
            coverage=AnalyticRect(0.0,0.0,20.0,10.0,aa=false)
            lowering=CoverageModel.AnalyticRect(0.0,0.0,20.0,10.0,aa=false)
            """.trimIndent(),
            dump,
        )
    }
}
