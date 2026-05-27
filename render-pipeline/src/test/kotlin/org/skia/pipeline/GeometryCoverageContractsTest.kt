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
    fun persistentCoverageAtlasPolicyIsGatedByExecutableChecks() {
        val bounds = IntRect(2, 3, 10, 11)
        val result = CoveragePlanAdapter.lower(
            CoveragePlan.CoverageAtlas(
                ref = CoverageAtlasRef("atlas-persistent"),
                bounds = bounds,
                cachePolicy = CoverageCachePolicy.PersistentByShapeKey,
            ),
        )

        val strategyResult = assertIs<CoverageLoweringResult.StrategyResult>(result)
        val fallback = assertIs<CoverageBackendStrategy.UnsupportedFallback>(strategyResult.strategy)
        assertEquals(StandardCoverageReason.AtlasPolicyUnavailable, fallback.reason)
        assertEquals(FallbackPlan.RefuseDiagnostic("coverage.atlas-policy-unavailable"), fallback.fallback)

        val decision = CoverageAtlasPolicyGate.evaluate(CoverageCachePolicy.PersistentByShapeKey)
        assertEquals(CoverageAtlasPolicyVerdict.NoGo, decision.verdict)
        assertEquals(false, decision.persistentCacheEnabled)
        assertEquals(StandardCoverageReason.AtlasPolicyUnavailable, decision.reason)
        assertEquals(0, decision.hitCount)
        assertEquals(0, decision.missCount)
        assertEquals(0L, decision.residentBytes)
        assertEquals(0, decision.evictionCount)
        assertEquals(
            listOf(
                "shape-key",
                "transform-key",
                "invalidation",
                "memory-budget",
                "eviction",
                "cpu-gpu-sync",
                "owner-thread",
            ),
            decision.checks.map { it.name },
        )
        assertEquals(
            List(decision.checks.size) { CoverageAtlasPolicyCheckStatus.Missing },
            decision.checks.map { it.status },
        )
        assertEquals(
            "coverageAtlasPolicy(policy=PersistentByShapeKey,persistent=false,verdict=no-go," +
                "reason=coverage.atlas-policy-unavailable,hits=0,misses=0,residentBytes=0,evictions=0," +
                "checks=shape-key:Missing:shape key definition not accepted|" +
                "transform-key:Missing:transform key definition not accepted|" +
                "invalidation:Missing:invalidation policy not accepted|" +
                "memory-budget:Missing:memory budget not accepted|" +
                "eviction:Missing:eviction policy not accepted|" +
                "cpu-gpu-sync:Missing:CPU/GPU synchronization policy not accepted|" +
                "owner-thread:Missing:owner-thread handling not accepted)",
            decision.dump(),
        )
    }

    @Test
    fun nonPersistentCoverageAtlasPoliciesAreNotBlockedByPersistentGate() {
        val frameLocal = CoverageAtlasPolicyGate.evaluate(CoverageCachePolicy.FrameLocal)
        val noCache = CoverageAtlasPolicyGate.evaluate(CoverageCachePolicy.NoCache)

        assertEquals(CoverageAtlasPolicyVerdict.GoNonPersistent, frameLocal.verdict)
        assertEquals(false, frameLocal.persistentCacheEnabled)
        assertEquals(null, frameLocal.reason)
        assertEquals(
            List(frameLocal.checks.size) { CoverageAtlasPolicyCheckStatus.NotRequired },
            frameLocal.checks.map { it.status },
        )

        assertEquals(CoverageAtlasPolicyVerdict.GoNonPersistent, noCache.verdict)
        assertEquals(false, noCache.persistentCacheEnabled)
        assertEquals(null, noCache.reason)
        assertEquals(
            List(noCache.checks.size) { CoverageAtlasPolicyCheckStatus.NotRequired },
            noCache.checks.map { it.status },
        )

        val noCacheResult = CoveragePlanAdapter.lower(
            CoveragePlan.CoverageAtlas(
                ref = CoverageAtlasRef("atlas-no-cache"),
                bounds = IntRect(4, 5, 12, 13),
                cachePolicy = CoverageCachePolicy.NoCache,
            ),
        )
        val noCacheStrategyResult = assertIs<CoverageLoweringResult.StrategyResult>(noCacheResult)
        val noCacheStrategy = assertIs<CoverageBackendStrategy.CoverageAtlasSample>(
            noCacheStrategyResult.strategy,
        )
        assertEquals(CoverageCachePolicy.NoCache, noCacheStrategy.cachePolicy)
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
