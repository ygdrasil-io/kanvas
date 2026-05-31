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
        assertEquals(
            "coverage.glyph-mask-dependency-unavailable",
            StandardCoverageReason.GlyphMaskDependencyUnavailable.code,
        )
        assertEquals("coverage.stencil-cover-unavailable", StandardCoverageReason.StencilCoverUnavailable.code)
        assertEquals("coverage.edge-count-exceeded", StandardCoverageReason.EdgeCountExceeded.code)
        assertEquals("coverage.verb-budget-exceeded", StandardCoverageReason.VerbBudgetExceeded.code)
        assertEquals(
            "coverage.cubic-segment-budget-exceeded",
            StandardCoverageReason.CubicSegmentBudgetExceeded.code,
        )
        assertEquals("coverage.dash-budget-exceeded", StandardCoverageReason.DashBudgetExceeded.code)
        assertEquals("coverage.clip-depth-exceeded", StandardCoverageReason.ClipDepthExceeded.code)
        assertEquals("coverage.bounds-budget-exceeded", StandardCoverageReason.BoundsBudgetExceeded.code)
        assertEquals(
            "coverage.stroke-outline-edge-count-exceeded",
            StandardCoverageReason.StrokeOutlineEdgeCountExceeded.code,
        )
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
    fun imageRectAxisAlignedLoweringKeepsSamplingPayloadOutsideGeometry() {
        val descriptor = ImageRectLowering.lower(
            source = FloatRect(0f, 0f, 4f, 4f),
            destination = FloatRect(2f, 3f, 10f, 11f),
            transform = TransformFacts(
                matrix = MatrixSpec.Identity,
                isAxisAligned = true,
                hasPerspective = false,
                maxScale = 2f,
                isInvertible = true,
            ),
            payloadRef = ImageSamplingPayloadRef("image.quadrants.rgba8888"),
        )

        val coverage = assertIs<CoveragePlan.AnalyticRect>(descriptor.coveragePlan)
        assertEquals(FloatRect(2f, 3f, 10f, 11f), coverage.bounds)
        assertEquals(true, coverage.aa)
        assertEquals("geometry.image-rect.analytic-rect", descriptor.routeIdentifier)
        assertEquals(
            "geometry owns source/destination/transform coverage; paint owns sampling, pixels, filtering, and colorspace",
            descriptor.payloadBoundary,
        )
        assertEquals(
            "imageRectDescriptor(route=geometry.image-rect.analytic-rect,source=0.0,0.0,4.0,4.0," +
                "destination=2.0,3.0,10.0,11.0,axisAligned=true,perspective=false," +
                "payload=image.quadrants.rgba8888,payloadBoundary=geometry owns source/destination/transform coverage; " +
                "paint owns sampling, pixels, filtering, and colorspace,coverage=AnalyticRect(2.0,3.0,10.0,11.0,aa=true))",
            descriptor.dump(),
        )
        assertEquals("paint-owned", descriptor.primitive.sampling.filter)
    }

    @Test
    fun imageRectTransformedLoweringUsesPathLikeCoverageWithoutMovingSamplingPayload() {
        val descriptor = ImageRectLowering.lower(
            source = FloatRect(0f, 0f, 4f, 4f),
            destination = FloatRect(2f, 3f, 10f, 11f),
            transform = TransformFacts(
                matrix = MatrixSpec(
                    m00 = 0f,
                    m01 = -1f,
                    m02 = 12f,
                    m10 = 1f,
                    m11 = 0f,
                    m12 = 0f,
                    m20 = 0f,
                    m21 = 0f,
                    m22 = 1f,
                ),
                isAxisAligned = false,
                hasPerspective = false,
                maxScale = 1f,
                isInvertible = true,
            ),
            payloadRef = ImageSamplingPayloadRef("image.rotated.rgba8888"),
        )

        val coverage = assertIs<CoveragePlan.PathCoverage>(descriptor.coveragePlan)
        assertEquals(PathFillType.Winding, coverage.fillType)
        assertEquals(true, coverage.aa)
        assertEquals(false, coverage.inverse)
        assertEquals("geometry.image-rect.path-like", descriptor.routeIdentifier)
        assertEquals("image.rotated.rgba8888", descriptor.payloadRef.id)
        assertEquals("paint-owned", descriptor.primitive.sampling.filter)
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
    fun glyphMaskHandoffNamesTextOwnerAndLowersToAlphaMask() {
        val descriptor = GlyphMaskLowering.lower(
            run = GlyphRunRef("glyph-run.title.3"),
            maskRef = AlphaMaskRef("glyph-atlas.page0.run-title-3"),
            maskBounds = IntRect(4, 5, 20, 21),
        )

        val coverage = assertIs<CoveragePlan.AlphaMask>(descriptor.coveragePlan)
        assertEquals(AlphaMaskRef("glyph-atlas.page0.run-title-3"), coverage.ref)
        assertEquals(IntRect(4, 5, 20, 21), coverage.bounds)
        assertEquals(MaskFormat.A8, coverage.format)
        assertEquals("geometry.glyph-mask.alpha-mask-handoff", descriptor.routeIdentifier)
        assertEquals(null, descriptor.dependencyDiagnostic)
        assertEquals(
            "discovery=text-glyph-infrastructure,rasterization=text-glyph-infrastructure," +
                "atlasLifetime=text-glyph-infrastructure,invalidation=text-glyph-infrastructure",
            descriptor.owner.dump(),
        )
        assertEquals(
            "glyphMaskDescriptor(route=geometry.glyph-mask.alpha-mask-handoff,run=glyph-run.title.3," +
                "owner=discovery=text-glyph-infrastructure,rasterization=text-glyph-infrastructure," +
                "atlasLifetime=text-glyph-infrastructure,invalidation=text-glyph-infrastructure," +
                "maskRef=glyph-atlas.page0.run-title-3,bounds=4,5,20,21,format=A8,diagnostic=none," +
                "coverage=AlphaMask(ref=glyph-atlas.page0.run-title-3,bounds=4,5,20,21,format=A8))",
            descriptor.dump(),
        )

        val model = assertIs<CoverageLoweringResult.CoverageModelResult>(
            CoveragePlanAdapter.lower(descriptor.coveragePlan),
        )
        assertEquals(CoverageModel.AlphaMask(IntRect(4, 5, 20, 21), MaskFormat.A8), model.coverage)
    }

    @Test
    fun glyphMaskWithoutTextOwnedMaskEmitsStableDependencyDiagnostic() {
        val descriptor = GlyphMaskLowering.lower(
            run = GlyphRunRef("glyph-run.missing-atlas"),
            maskRef = null,
            maskBounds = null,
        )

        val unsupported = assertIs<CoveragePlan.Unsupported>(descriptor.coveragePlan)
        assertEquals(StandardCoverageReason.GlyphMaskDependencyUnavailable, unsupported.reason)
        assertEquals("geometry.glyph-mask.dependency-gated", descriptor.routeIdentifier)
        assertEquals(StandardCoverageReason.GlyphMaskDependencyUnavailable, descriptor.dependencyDiagnostic)
        assertEquals(
            "glyphMaskDescriptor(route=geometry.glyph-mask.dependency-gated,run=glyph-run.missing-atlas," +
                "owner=discovery=text-glyph-infrastructure,rasterization=text-glyph-infrastructure," +
                "atlasLifetime=text-glyph-infrastructure,invalidation=text-glyph-infrastructure," +
                "maskRef=none,bounds=none,format=A8,diagnostic=coverage.glyph-mask-dependency-unavailable," +
                "coverage=Unsupported(reason=coverage.glyph-mask-dependency-unavailable))",
            descriptor.dump(),
        )

        val strategy = assertIs<CoverageLoweringResult.StrategyResult>(
            CoveragePlanAdapter.lower(descriptor.coveragePlan),
        )
        val fallback = assertIs<CoverageBackendStrategy.UnsupportedFallback>(strategy.strategy)
        assertEquals(StandardCoverageReason.GlyphMaskDependencyUnavailable, fallback.reason)
        assertEquals(FallbackPlan.RefuseDiagnostic("coverage.glyph-mask-dependency-unavailable"), fallback.fallback)
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
