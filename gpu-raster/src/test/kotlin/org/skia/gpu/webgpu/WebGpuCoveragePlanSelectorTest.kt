package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.skia.pipeline.AlphaMaskRef
import org.skia.pipeline.AaClipRef
import org.skia.pipeline.ClipStackBackendDisposition
import org.skia.pipeline.ClipStackBreadthMatrix
import org.skia.pipeline.ClipInteraction
import org.skia.pipeline.ClipShapeSpec
import org.skia.pipeline.CoverageAtlasRef
import org.skia.pipeline.CoverageCachePolicy
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.FloatRect
import org.skia.pipeline.IntRect
import org.skia.pipeline.MaskFormat
import org.skia.pipeline.PathFillType
import org.skia.pipeline.Point
import org.skia.pipeline.RRectSpec
import org.skia.pipeline.StandardCoverageReason

class WebGpuCoveragePlanSelectorTest {
    @Test
    fun `analytic rect selection adds only coverage kind code axis`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "axis-aligned-filled-rect",
            plan = CoveragePlan.AnalyticRect(FloatRect(2f, 1f, 7f, 6f), aa = true),
        )

        assertEquals(WebGpuCoverageStrategy.AnalyticRect, selection.strategy)
        assertEquals("webgpu.coverage.analytic-rect", selection.routeIdentifier)
        assertEquals(
            listOf(
                PipelineKeyClassification(
                    axis = "coverageKind",
                    axisClass = PipelineKeyAxisClass.Code,
                    value = "analyticRect",
                ),
            ),
            selection.pipelineAxes,
        )
        assertFalse(selection.pipelineKeyDump().contains("2.0"))
        assertTrue(selection.dump().contains("diagnostic=none"))
    }

    @Test
    fun `analytic rrect selection is driven by coverage plan without uniform key axes`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "axis-aligned-filled-rrect",
            plan = CoveragePlan.AnalyticRRect(
                shape = RRectSpec(
                    bounds = FloatRect(2f, 2f, 14f, 14f),
                    topLeftRadius = Point(4f, 4f),
                    topRightRadius = Point(4f, 4f),
                    bottomRightRadius = Point(4f, 4f),
                    bottomLeftRadius = Point(4f, 4f),
                ),
                aa = true,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.AnalyticRRect, selection.strategy)
        assertEquals("webgpu.coverage.analytic-rrect", selection.routeIdentifier)
        assertTrue(selection.pipelineKeyDump().contains("code=[coverageKind=analyticRRect]"))
        assertFalse(selection.pipelineKeyDump().contains("14.0"))
    }

    @Test
    fun `unsupported span coverage emits shared gpu diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "span-fixture",
            plan = CoveragePlan.SpanRuns(IntRect(0, 0, 8, 8)),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertEquals(StandardCoverageReason.SpanRunsUnsupported, selection.diagnostic?.reason)
        assertTrue(selection.diagnostic?.dump()?.contains("backend=GPU") == true)
        assertTrue(selection.diagnostic?.dump()?.contains("coverage.span-runs-unsupported") == true)
    }

    @Test
    fun `glyph alpha mask coverage emits explicit webgpu unsupported diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "glyph-mask",
            plan = CoveragePlan.AlphaMask(
                ref = AlphaMaskRef("glyph-atlas.page0.run-title-3"),
                bounds = IntRect(4, 5, 20, 21),
                format = MaskFormat.A8,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertEquals(StandardCoverageReason.AlphaMaskUnsupported, selection.diagnostic?.reason)
        assertTrue(selection.dump().contains("drawKind=glyph-mask"))
        assertTrue(selection.dump().contains("coverage=AlphaMask(format=A8)"))
        assertTrue(selection.diagnostic?.dump()?.contains("coverage.alpha-mask-unsupported") == true)
    }

    @Test
    fun `glyph mask dependency diagnostic is preserved through webgpu selector`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "glyph-mask",
            plan = CoveragePlan.Unsupported(StandardCoverageReason.GlyphMaskDependencyUnavailable),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertEquals(StandardCoverageReason.GlyphMaskDependencyUnavailable, selection.diagnostic?.reason)
        assertTrue(selection.dump().contains("coverage=Unsupported(reason=coverage.glyph-mask-dependency-unavailable)"))
    }

    @Test
    fun `persistent coverage atlas emits policy unavailable diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "coverage-atlas-fixture",
            plan = CoveragePlan.CoverageAtlas(
                ref = CoverageAtlasRef("atlas-persistent"),
                bounds = IntRect(0, 0, 16, 16),
                cachePolicy = CoverageCachePolicy.PersistentByShapeKey,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertEquals(StandardCoverageReason.AtlasPolicyUnavailable, selection.diagnostic?.reason)
        assertTrue(selection.dump().contains("CoverageAtlas(policy=PersistentByShapeKey)"))
        assertTrue(selection.diagnostic?.dump()?.contains("coverage.atlas-policy-unavailable") == true)
    }

    @Test
    fun `analytic clip is accepted and recorded in webgpu selection dump`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "analytic-clipped-rect",
            plan = CoveragePlan.AnalyticRect(FloatRect(0f, 0f, 8f, 8f), aa = true),
            clipInteraction = ClipInteraction.AnalyticShape(
                ClipShapeSpec(bounds = FloatRect(1f, 1f, 7f, 7f), kind = "rrect-intersect"),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.AnalyticRect, selection.strategy)
        assertEquals(null, selection.diagnostic)
        assertTrue(selection.dump().contains("clip=AnalyticShape(rrect-intersect,1.0,1.0,7.0,7.0)"))
    }

    @Test
    fun `arbitrary aa clip emits stable gpu diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "aa-clip-path",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 1, edgeCount = 4),
            clipInteraction = ClipInteraction.AaClip(
                ref = AaClipRef("cpu.sk-aa-clip.fixture"),
                bounds = IntRect(0, 0, 8, 8),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals(StandardCoverageReason.ArbitraryAaClipUnsupported, selection.diagnostic?.reason)
        assertTrue(selection.diagnostic?.dump()?.contains("backend=GPU") == true)
        assertTrue(selection.dump().contains("coverage.arbitrary-aa-clip-unsupported"))
        assertTrue(selection.dump().contains("clip=AaClip(ref=cpu.sk-aa-clip.fixture,bounds=0,0,8,8)"))
    }

    @Test
    fun `clip stack breadth matrix maps webgpu support and refusal diagnostics`() {
        ClipStackBreadthMatrix.cases.forEach { case ->
            val selection = WebGpuCoveragePlanSelector.select(
                drawKind = "clip-stack-${case.family}",
                plan = CoveragePlan.AnalyticRect(FloatRect(0f, 0f, 16f, 16f), aa = true),
                clipInteraction = case.webGpuClip,
            )

            when (case.webGpuDisposition) {
                ClipStackBackendDisposition.Supported -> {
                    assertEquals(WebGpuCoverageStrategy.AnalyticRect, selection.strategy, case.family)
                    assertEquals(null, selection.diagnostic, case.family)
                }
                ClipStackBackendDisposition.Refused -> {
                    assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy, case.family)
                    assertEquals("webgpu.coverage.refuse", selection.routeIdentifier, case.family)
                    assertEquals(case.webGpuReason, selection.diagnostic?.reason, case.family)
                    assertTrue(selection.dump().contains(case.webGpuReason?.code ?: ""), case.family)
                }
            }
        }
    }

    @Test
    fun `strategy inventory separates adapter evidence states from refused and compatibility branches`() {
        val byBranch = WebGpuCoverageStrategyInventory.rows.associateBy { it.branch }

        assertEquals(true, WebGpuCoverageStrategyInventory.ciAdapterLaneAvailable)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterPass, byBranch.getValue("analytic-rect").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterPass, byBranch.getValue("analytic-rrect").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterPass, byBranch.getValue("path-aa-stroke-primitive").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterPass, byBranch.getValue("path-convex-fan").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterPass, byBranch.getValue("path-stencil-cover").status)
        assertEquals(WebGpuCoverageEvidenceStatus.Proven, byBranch.getValue("path-mask-or-atlas-selector").status)
        assertEquals("webgpu.coverage.path-mask-or-atlas", byBranch.getValue("path-mask-or-atlas-selector").routeIdentifier)
        assertTrue(byBranch.getValue("path-mask-or-atlas-selector").evidence.contains("selector-only proof"))
        assertEquals(WebGpuCoverageEvidenceStatus.Compatibility, byBranch.getValue("full-scissor").status)
        assertEquals(WebGpuCoverageEvidenceStatus.Refused, byBranch.getValue("span-runs").status)
        assertEquals(StandardCoverageReason.SpanRunsUnsupported, byBranch.getValue("span-runs").diagnosticReason)
        assertEquals(WebGpuCoverageEvidenceStatus.Refused, byBranch.getValue("alpha-mask").status)
        assertEquals(StandardCoverageReason.AlphaMaskUnsupported, byBranch.getValue("alpha-mask").diagnosticReason)
        assertEquals(WebGpuCoverageEvidenceStatus.Refused, byBranch.getValue("coverage-atlas").status)
        assertEquals(StandardCoverageReason.AtlasPolicyUnavailable, byBranch.getValue("coverage-atlas").diagnosticReason)
        assertEquals(WebGpuCoverageEvidenceStatus.Refused, byBranch.getValue("path-edge-overflow").status)
        assertEquals(StandardCoverageReason.EdgeCountExceeded, byBranch.getValue("path-edge-overflow").diagnosticReason)
        assertEquals(WebGpuCoverageEvidenceStatus.Refused, byBranch.getValue("arbitrary-aa-clip").status)
        assertEquals(
            StandardCoverageReason.ArbitraryAaClipUnsupported,
            byBranch.getValue("arbitrary-aa-clip").diagnosticReason,
        )
        assertEquals(listOf("path-mask-or-atlas-selector"), WebGpuCoverageStrategyInventory.rows.filter {
            it.status == WebGpuCoverageEvidenceStatus.Proven
        }.map { it.branch })

        val dump = WebGpuCoverageStrategyInventory.dump()
        assertTrue(dump.contains("branch=analytic-rect"))
        assertTrue(dump.contains("branch=path-mask-or-atlas-selector"))
        assertTrue(dump.contains("status=adapter-pass"))
        assertTrue(dump.contains("status=proven"))
        assertTrue(dump.contains("status=refused"))
        assertTrue(dump.contains("status=compatibility"))
        assertTrue(dump.contains("reason=coverage.arbitrary-aa-clip-unsupported"))
    }

    @Test
    fun `strategy inventory marks promoted routes as adapter-fail when adapter lane fails`() {
        val byBranch = WebGpuCoverageStrategyInventory
            .rowsForCiAdapterStatus(CiAdapterLaneStatus.AdapterFail)
            .associateBy { it.branch }

        assertEquals(WebGpuCoverageEvidenceStatus.AdapterFail, byBranch.getValue("analytic-rect").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterFail, byBranch.getValue("analytic-rrect").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterFail, byBranch.getValue("path-convex-fan").status)
        assertEquals(WebGpuCoverageEvidenceStatus.AdapterFail, byBranch.getValue("path-stencil-cover").status)
        assertTrue(byBranch.getValue("analytic-rect").evidence.contains("adapter-fail"))
    }

    @Test
    fun `strategy inventory uses blocked-no-adapter-lane only when required adapter lane is missing`() {
        val byBranch = WebGpuCoverageStrategyInventory
            .rowsForCiAdapterStatus(CiAdapterLaneStatus.BlockedNoAdapterLane)
            .associateBy { it.branch }

        assertEquals(WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane, byBranch.getValue("analytic-rect").status)
        assertEquals(WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane, byBranch.getValue("analytic-rrect").status)
        assertEquals(WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane, byBranch.getValue("path-aa-stroke-primitive").status)
        assertEquals(WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane, byBranch.getValue("path-convex-fan").status)
        assertEquals(WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane, byBranch.getValue("path-stencil-cover").status)
        assertTrue(byBranch.getValue("analytic-rect").evidence.contains("lane is unavailable"))
    }

    @Test
    fun `path coverage convex fan selection records pipeline axes`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "simple-filled-path",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = true,
                contourCount = 1,
                edgeCount = 5,
                pathVerbCount = 5,
                maxCubicSegmentsPerCubic = 4,
                dashIntervalCount = 2,
                clipStackDepth = 1,
                deviceBounds = FloatRect(3f, 3f, 13f, 13f),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.CpuPreparedConvexFan, selection.strategy)
        assertEquals("webgpu.coverage.path-convex-fan", selection.routeIdentifier)
        assertEquals(
            "preimage=pipeline.key v=1 layout=[] code=[coverageKind=pathConvexFan] " +
                "state=[pathFillRule=winding,topology=triangleList];" +
                "hash=b40ccbe42d1f6c656423594d71289cb0116a7f9f6300bb457422796f26cde5f0;" +
                "uniformFacts=[]",
            selection.pipelineKeyDump(),
        )
        assertTrue(selection.dump().contains("coverage=PathCoverage(fillType=Winding,aa=true,inverse=false)"))
        assertTrue(selection.dump().contains("pathVerbCount=5/$WEBGPU_PATH_AA_VERB_BUDGET"))
        assertTrue(selection.dump().contains("coverageEdgeCount=5/$WEBGPU_PATH_AA_EDGE_BUDGET"))
        assertTrue(selection.dump().contains("cubicMaxSegmentsPerCubic=4/$WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET"))
        assertTrue(selection.dump().contains("dashIntervalCount=2/$WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET"))
        assertTrue(selection.dump().contains("clipStackDepth=1/$WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET"))
        assertTrue(selection.dump().contains("deviceBounds=3.0,3.0,13.0,13.0"))
        assertTrue(selection.dump().contains("diagnostic=none"))
    }

    @Test
    fun `path coverage concave multi contour and inverse select stencil cover`() {
        val concave = WebGpuCoveragePlanSelector.select(
            drawKind = "concave-path",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = false, contourCount = 1, edgeCount = 7),
        )
        val inverse = WebGpuCoveragePlanSelector.select(
            drawKind = "inverse-path",
            plan = CoveragePlan.PathCoverage(PathFillType.EvenOdd, aa = false, inverse = true),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 1, edgeCount = 4),
        )
        val multiContour = WebGpuCoveragePlanSelector.select(
            drawKind = "multi-contour-path",
            plan = CoveragePlan.PathCoverage(PathFillType.EvenOdd, aa = false, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(isConvex = true, contourCount = 2, edgeCount = 8),
        )

        assertEquals(WebGpuCoverageStrategy.StencilCover, concave.strategy)
        assertEquals(WebGpuCoverageStrategy.StencilCover, inverse.strategy)
        assertEquals(WebGpuCoverageStrategy.StencilCover, multiContour.strategy)
        assertEquals("webgpu.coverage.path-stencil-cover", inverse.routeIdentifier)
        assertTrue(inverse.pipelineKeyDump().contains("state=[pathFillRule=evenOdd,topology=triangleList]"))
    }

    @Test
    fun `aa path edge budget overflow emits stable gpu diagnostic`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-edge-overflow",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET + 1,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals(StandardCoverageReason.EdgeCountExceeded, selection.diagnostic?.reason)
        assertEquals("coverage.edge-count-exceeded", selection.diagnostic?.reason?.code)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertTrue(selection.diagnostic?.dump()?.contains("backend=GPU") == true)
        assertTrue(selection.diagnostic?.dump()?.contains("action=RefuseDiagnostic(coverage.edge-count-exceeded)") == true)
        assertTrue(selection.dump().contains("coverage.edge-count-exceeded"))
        assertTrue(selection.dump().contains("coverageEdgeCount=${WEBGPU_PATH_AA_EDGE_BUDGET + 1}/$WEBGPU_PATH_AA_EDGE_BUDGET"))
        assertTrue(selection.pipelineKeyDump().contains("code=[coverageKind=pathCoverageUnsupported]"))
    }

    @Test
    fun `aa path verb budget overflow emits stable gpu diagnostic while edge budget remains within limit`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-verb-overflow",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                pathVerbCount = WEBGPU_PATH_AA_VERB_BUDGET + 1,
                maxCubicSegmentsPerCubic = WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET,
                dashIntervalCount = WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET,
                clipStackDepth = WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET,
                deviceBounds = FloatRect(0f, 0f, WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET, 64f),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals(StandardCoverageReason.VerbBudgetExceeded, selection.diagnostic?.reason)
        assertEquals("coverage.verb-budget-exceeded", selection.diagnostic?.reason?.code)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertTrue(selection.dump().contains("pathVerbCount=${WEBGPU_PATH_AA_VERB_BUDGET + 1}/$WEBGPU_PATH_AA_VERB_BUDGET"))
        assertTrue(selection.dump().contains("coverageEdgeCount=$WEBGPU_PATH_AA_EDGE_BUDGET/$WEBGPU_PATH_AA_EDGE_BUDGET"))
        assertTrue(selection.dump().contains("coverage.verb-budget-exceeded"))
    }

    @Test
    fun `edge budget remains the first refusal when multiple path budgets overflow`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-multiple-budget-overflow",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET + 1,
                pathVerbCount = WEBGPU_PATH_AA_VERB_BUDGET + 1,
                maxCubicSegmentsPerCubic = WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET + 1,
                dashIntervalCount = WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET + 1,
                clipStackDepth = WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET + 1,
                deviceBounds = FloatRect(0f, 0f, WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET + 1f, 64f),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals(StandardCoverageReason.EdgeCountExceeded, selection.diagnostic?.reason)
        assertTrue(selection.dump().contains("coverage.edge-count-exceeded"))
        assertTrue(selection.dump().contains("pathVerbCount=${WEBGPU_PATH_AA_VERB_BUDGET + 1}/$WEBGPU_PATH_AA_VERB_BUDGET"))
    }

    @Test
    fun `non edge budget overflow does not select stroke or mask fallback routes`() {
        val strokeSelection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-verb-overflow-stroke-fallback",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                pathVerbCount = WEBGPU_PATH_AA_VERB_BUDGET + 1,
                strokeOutlineFallbackEnabled = true,
            ),
        )
        val maskSelection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-verb-overflow-mask-fallback",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                pathVerbCount = WEBGPU_PATH_AA_VERB_BUDGET + 1,
                maskOrAtlasFallbackEnabled = true,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, strokeSelection.strategy)
        assertEquals(StandardCoverageReason.VerbBudgetExceeded, strokeSelection.diagnostic?.reason)
        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, maskSelection.strategy)
        assertEquals(StandardCoverageReason.VerbBudgetExceeded, maskSelection.diagnostic?.reason)
    }

    @Test
    fun `m60 path budget reasons are emitted after edge budget passes`() {
        fun selectWithFacts(facts: WebGpuPathCoverageFacts): WebGpuCoverageSelection =
            WebGpuCoveragePlanSelector.select(
                drawKind = "path-budget-overflow",
                plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
                pathFacts = facts,
            )

        val cubic = selectWithFacts(
            WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                maxCubicSegmentsPerCubic = WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET + 1,
            ),
        )
        val dash = selectWithFacts(
            WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                dashIntervalCount = WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET + 1,
            ),
        )
        val clip = selectWithFacts(
            WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                clipStackDepth = WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET + 1,
            ),
        )
        val bounds = selectWithFacts(
            WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET,
                deviceBounds = FloatRect(0f, 0f, WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET + 1f, 64f),
            ),
        )

        assertEquals(StandardCoverageReason.CubicSegmentBudgetExceeded, cubic.diagnostic?.reason)
        assertEquals("coverage.cubic-segment-budget-exceeded", cubic.diagnostic?.reason?.code)
        assertEquals(StandardCoverageReason.DashBudgetExceeded, dash.diagnostic?.reason)
        assertEquals(StandardCoverageReason.ClipDepthExceeded, clip.diagnostic?.reason)
        assertEquals(StandardCoverageReason.BoundsBudgetExceeded, bounds.diagnostic?.reason)
    }

    @Test
    fun `selected stroke primitive edge overflow selects bounded stroke route`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "stroke-outline-overflow",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 2,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET + 1,
                strokeOutlineFallbackEnabled = true,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.PathAaStrokePrimitive, selection.strategy)
        assertEquals("webgpu.coverage.path-aa-stroke-primitive", selection.routeIdentifier)
        assertEquals(null, selection.diagnostic)
        assertTrue(selection.pipelineKeyDump().contains("code=[coverageKind=pathAaStrokePrimitive]"))
    }

    @Test
    fun `bounded stroke cap join facts emit stable parity blocker`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "bounded-stroke-cap-join",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = 18,
                pathVerbCount = 9,
                dashIntervalCount = 0,
                deviceBounds = FloatRect(0f, 0f, 192f, 128f),
                strokeWidth = 10f,
                strokeCaps = listOf("butt", "round", "square"),
                strokeJoins = listOf("bevel", "round", "bevel"),
            ),
        )

        assertEquals(WebGpuCoverageStrategy.RefuseDiagnostic, selection.strategy)
        assertEquals("webgpu.coverage.refuse", selection.routeIdentifier)
        assertEquals("coverage.stroke-cap-join-visual-parity-below-threshold", selection.diagnostic?.reason?.code)
        assertTrue(selection.pipelineKeyDump().contains("code=[coverageKind=pathAaStrokeCapJoinBlocked]"))
        assertTrue(selection.dump().contains("strokeWidth=10.0"))
        assertTrue(selection.dump().contains("strokeCaps=butt+round+square"))
        assertTrue(selection.dump().contains("strokeJoins=bevel+round+bevel"))
    }

    @Test
    fun `edge overflow can select explicit mask or atlas fallback when enabled`() {
        val selection = WebGpuCoveragePlanSelector.select(
            drawKind = "path-edge-overflow-mask",
            plan = CoveragePlan.PathCoverage(PathFillType.Winding, aa = true, inverse = false),
            pathFacts = WebGpuPathCoverageFacts(
                isConvex = false,
                contourCount = 1,
                edgeCount = WEBGPU_PATH_AA_EDGE_BUDGET + 1,
                maskOrAtlasFallbackEnabled = true,
            ),
        )

        assertEquals(WebGpuCoverageStrategy.CoverageMaskOrAtlasFallback, selection.strategy)
        assertEquals("webgpu.coverage.path-mask-or-atlas", selection.routeIdentifier)
        assertTrue(selection.pipelineKeyDump().contains("code=[coverageKind=pathMaskOrAtlas]"))
        assertTrue(selection.dump().contains("diagnostic=none"))
    }

    @Test
    fun `pipeline key diagnostics classify coverage kind as code axis`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 16, 16).use { device ->
                val key = device.buildPipelineKeyForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "coverageKind" to "analyticRect",
                        "generatedPath" to "true",
                        "pathFillRule" to "winding",
                    ),
                )
                assertTrue(
                    key.contains(
                        "preimage=pipeline.key v=1 layout=[] code=[coverageKind=analyticRect,generatedPath=true] " +
                            "state=[blendMode=kSrcOver,pathFillRule=winding]",
                    ),
                )
                assertTrue(key.contains("hash="))
            }
        }
    }

    @Test
    fun `webgpu path fixtures match raster oracle when adapter is available`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val paint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            }
            assertRgbaNear(
                renderRaster { drawPath(convexPath(), paint) },
                renderGpu(ctx) { drawPath(convexPath(), paint) },
                minExactPixelRatio = 0.88,
                maxChannelDelta = 3,
                maxObservedChannelDelta = 255,
            )
            assertRgbaNear(
                renderRaster { drawPath(concavePath(), paint) },
                renderGpu(ctx) { drawPath(concavePath(), paint) },
                minExactPixelRatio = 0.84,
                maxChannelDelta = 4,
                maxObservedChannelDelta = 255,
            )
            assertRgbaNear(
                renderRaster { drawPath(inverseEvenOddPath(), paint) },
                renderGpu(ctx) { drawPath(inverseEvenOddPath(), paint) },
                minExactPixelRatio = 0.88,
                maxChannelDelta = 4,
                maxObservedChannelDelta = 255,
            )
        }
    }

    @Test
    fun `warm path frame does not create unbounded pipeline cache entries`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                }
                val canvas = SkCanvas(device)
                canvas.drawPath(convexPath(), paint)
                device.flush()
                val cold = device.cacheTelemetrySnapshot()

                canvas.drawPath(convexPath(), paint)
                device.flush()
                val warm = device.cacheTelemetrySnapshot()

                assertTrue(cold.pipelineCacheEntryCount >= 1, "cold path frame should create at least one pipeline")
                assertEquals(
                    cold.pipelineCacheEntryCount,
                    warm.pipelineCacheEntryCount,
                    "warm path frame should reuse pipeline cache entries (cold=$cold warm=$warm)",
                )
            }
        }
    }

    @Test
    fun `webgpu rect and rrect fixtures match raster oracle when adapter is available`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val rectPaint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = false
            }
            val rrectPaint = SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            }
            assertArrayEquals(
                renderRaster { drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), rectPaint) },
                renderGpu(ctx) { drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), rectPaint) },
                "analytic rect GPU output should match raster oracle",
            )
            assertRgbaNear(
                renderRaster {
                    drawRRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f), rrectPaint)
                },
                renderGpu(ctx) {
                    drawRRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f), rrectPaint)
                },
                minExactPixelRatio = 0.90,
                maxChannelDelta = 2,
            )
        }
    }

    @Test
    fun `production device records coverage selector routes for rect rrect and path`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                }

                canvas.drawRect(SkRect.MakeLTRB(2f, 1f, 7f, 6f), paint)
                val rect = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.analytic-rect", rect?.routeIdentifier)
                assertTrue(rect?.pipelineKeyDump?.contains("code=[coverageKind=analyticRect]") == true)
                assertTrue(rect?.selectionDump?.contains("diagnostic=none") == true)
                assertTrue(rect?.productionDump?.contains("mode=Default") == true)
                assertTrue(rect?.productionDump?.contains("backend=GPU") == true)
                assertTrue(rect?.productionDump?.contains("cacheCounters=shaderModules=") == true)

                canvas.drawRRect(SkRRect.MakeRectXY(SkRect.MakeLTRB(2f, 2f, 14f, 14f), 4f, 4f), paint)
                val rrect = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.analytic-rrect", rrect?.routeIdentifier)
                assertTrue(rrect?.pipelineKeyDump?.contains("code=[coverageKind=analyticRRect]") == true)

                canvas.drawPath(convexPath(), paint)
                val path = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.path-convex-fan", path?.routeIdentifier)
                assertTrue(path?.pipelineKeyDump?.contains("code=[coverageKind=pathConvexFan]") == true)
                assertFalse(path?.pipelineKeyDump?.contains("3.0") == true)
                assertTrue(path?.selectionDump?.contains("pathAaBudgets=pathVerbCount=") == true)
                assertTrue(path?.selectionDump?.contains("coverageEdgeCount=") == true)
                assertTrue(path?.productionDump?.contains("budgetDiagnostics=pathVerbCount=") == true)
                assertTrue(path?.productionDump?.contains("deviceBounds=") == true)

                canvas.drawPath(concavePath(), paint)
                val concave = device.coverageSelectionDiagnosticsForTests()
                assertEquals("webgpu.coverage.path-stencil-cover", concave?.routeIdentifier)
                assertTrue(concave?.pipelineKeyDump?.contains("code=[coverageKind=pathStencilCover]") == true)
                assertTrue(concave?.selectionDump?.contains("diagnostic=none") == true)
                assertTrue(concave?.selectionDump?.contains("pathAaBudgets=pathVerbCount=") == true)
            }
        }
    }

    @Test
    fun `production device records coverage selector route for image rect`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                SkCanvas(device).drawImageRect(
                    image = quadrantImage(),
                    src = SkRect.MakeLTRB(0f, 0f, 2f, 2f),
                    dst = SkRect.MakeLTRB(2f, 2f, 10f, 10f),
                )

                val imageRect = device.coverageSelectionDiagnosticsForTests()
                assertEquals("axis-aligned-image-rect", imageRect?.drawKind)
                assertEquals("webgpu.coverage.analytic-rect", imageRect?.routeIdentifier)
                assertTrue(imageRect?.pipelineKeyDump?.contains("code=[coverageKind=analyticRect]") == true)
                assertTrue(imageRect?.productionDump?.contains("coveragePlan=AnalyticRect(aa=true)") == true)
                assertTrue(imageRect?.productionDump?.contains("backend=GPU") == true)
            }
        }
    }

    @Test
    fun `production device rollback flag records selector disabled route dump`() =
        withWebGpuCoverageSelectorFlag("false") {
            val context = WebGpuContext.createOrNull()
            Assumptions.assumeTrue(context != null, "No WebGPU adapter")
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    SkCanvas(device).drawRect(
                        SkRect.MakeLTRB(2f, 1f, 7f, 6f),
                        SkPaint().apply {
                            color = SK_ColorBLACK
                            isAntiAlias = true
                        },
                    )
                    val diagnostics = device.coverageSelectionDiagnosticsForTests()
                    assertEquals("Rollback", diagnostics?.mode)
                    assertEquals("webgpu.coverage.selector-disabled", diagnostics?.routeIdentifier)
                    assertTrue(diagnostics?.productionDump?.contains("fallbackReason=coverage.webgpu-selector-disabled") == true)
                }
            }
        }

    @Test
    fun `image rect rollback flag records selector disabled route dump`() =
        withWebGpuCoverageSelectorFlag("false") {
            val context = WebGpuContext.createOrNull()
            Assumptions.assumeTrue(context != null, "No WebGPU adapter")
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    SkCanvas(device).drawImageRect(
                        image = quadrantImage(),
                        src = SkRect.MakeLTRB(0f, 0f, 2f, 2f),
                        dst = SkRect.MakeLTRB(2f, 2f, 10f, 10f),
                    )
                    val diagnostics = device.coverageSelectionDiagnosticsForTests()
                    assertEquals("Rollback", diagnostics?.mode)
                    assertEquals("axis-aligned-image-rect", diagnostics?.drawKind)
                    assertEquals("webgpu.coverage.selector-disabled", diagnostics?.routeIdentifier)
                    assertTrue(diagnostics?.productionDump?.contains("fallbackReason=coverage.webgpu-selector-disabled") == true)
                }
            }
        }

    private fun renderRaster(draw: SkCanvas.() -> Unit): ByteArray {
        val bitmap = SkBitmap(W, H, colorType = SkColorType.kRGBA_8888).apply {
            eraseColor(SK_ColorWHITE)
        }
        val canvas = SkCanvas(SkBitmapDevice(bitmap))
        canvas.draw()
        return bitmap.pixels8888.toRgbaBytes()
    }

    private fun renderGpu(context: WebGpuContext, draw: SkCanvas.() -> Unit): ByteArray =
        SkWebGpuDevice(context, W, H).use { device ->
            device.setBackground(SK_ColorWHITE)
            SkCanvas(device).draw()
            device.flush()
        }

    private fun IntArray.toRgbaBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val pixel = this[i]
            out[i * 4] = ((pixel ushr 16) and 0xFF).toByte()
            out[i * 4 + 1] = ((pixel ushr 8) and 0xFF).toByte()
            out[i * 4 + 2] = (pixel and 0xFF).toByte()
            out[i * 4 + 3] = ((pixel ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun quadrantImage(): SkImage {
        val bitmap = SkBitmap(2, 2, colorType = SkColorType.kRGBA_8888)
        bitmap.setPixel(0, 0, 0xFFFF0000.toInt())
        bitmap.setPixel(1, 0, 0xFF00FF00.toInt())
        bitmap.setPixel(0, 1, 0xFF0000FF.toInt())
        bitmap.setPixel(1, 1, 0xFFFFFFFF.toInt())
        return SkImage.Make(bitmap)
    }

    private fun assertRgbaNear(
        expected: ByteArray,
        actual: ByteArray,
        minExactPixelRatio: Double,
        maxChannelDelta: Int,
        maxObservedChannelDelta: Int = 10,
    ) {
        assertEquals(expected.size, actual.size)
        var exactPixels = 0
        var maxDelta = 0
        for (i in expected.indices step 4) {
            var pixelExact = true
            for (channel in 0 until 4) {
                val delta = kotlin.math.abs(
                    (expected[i + channel].toInt() and 0xFF) -
                        (actual[i + channel].toInt() and 0xFF),
                )
                maxDelta = maxOf(maxDelta, delta)
                if (delta > maxChannelDelta) pixelExact = false
            }
            if (pixelExact) exactPixels++
        }
        val totalPixels = expected.size / 4
        val exactRatio = exactPixels.toDouble() / totalPixels.toDouble()
        assertTrue(
            exactRatio >= minExactPixelRatio,
            "rrect/simple-shape GPU diff exceeded tolerance: exactRatio=$exactRatio maxDelta=$maxDelta",
        )
        assertTrue(
            maxDelta <= maxObservedChannelDelta,
            "rrect/simple-shape GPU max channel delta exceeded tolerance: exactRatio=$exactRatio maxDelta=$maxDelta",
        )
    }

    private fun <T> withWebGpuCoverageSelectorFlag(value: String?, block: () -> T): T {
        val key = "kanvas.webgpu.coverageSelector.enabled"
        val previous = System.getProperty(key)
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previous)
            }
        }
    }

    private companion object {
        const val W: Int = 16
        const val H: Int = 16

        fun convexPath(): SkPath = SkPathBuilder()
            .moveTo(3f, 3f)
            .lineTo(13f, 4f)
            .lineTo(11f, 13f)
            .lineTo(4f, 12f)
            .close()
            .detach()

        fun concavePath(): SkPath = SkPathBuilder()
            .moveTo(2f, 2f)
            .lineTo(14f, 2f)
            .lineTo(8f, 8f)
            .lineTo(14f, 14f)
            .lineTo(2f, 14f)
            .close()
            .detach()

        fun inverseEvenOddPath(): SkPath = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseEvenOdd)
            .addRect(SkRect.MakeLTRB(4f, 4f, 12f, 12f))
            .detach()
    }
}
