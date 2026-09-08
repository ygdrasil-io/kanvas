package org.graphiks.kanvas.gpu.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.ClipPreparationLimitsI32
import org.graphiks.math.geometry.ClipPreparationLimitsI64
import org.graphiks.math.geometry.ClipPreparationPolicyF64
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class W4eClipPlanCompilerTest {
    private val compiler = W4eClipPlanCompiler()

    @Test
    fun `AA path difference clip emits one ordered linear mask chain and clips the W4d color consumer`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip))
        val graph = compile(scene)

        assertEquals(W4eClipPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskInitialize>().size)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().size)
        assertEquals(ClipCombineOperation.Difference, graph.passes().filterIsInstance<PlanPass.ClipMaskFold>().single().operation)
        assertEquals(2, graph.resources().count { it.role == PlanResourceRole.CoverageMaskAccumulator })
        assertTrue(graph.resources().any { it.role == PlanResourceRole.CoverageMaskMultisampleScratch && it.sampleCountI32 == 4 })
        assertTrue(graph.resources().any { it.role == PlanResourceRole.CoverageMaskDepthStencil && it.sampleCountI32 == 4 })
        assertTrue(graph.passes().filterIsInstance<PlanPass.PathRenderPass>().any { it.draw is ClippedGeneralPathDraw })
        assertTrue(graph.verifyW4eCompilerWitness())
    }

    @Test
    fun `hard integral rect intersection uses a scissor without mask allocation`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Rect.of(RectF32(1f, 1f, 14f, 14f)),
                operation = ClipOperation.INTERSECT,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )
        val graph = compile(sceneOf(pathDraw(clip = clip)))

        assertEquals(W4eClipPlanCompiler.HARD_CAPABILITY_ID, graph.capabilityId)
        assertTrue(graph.resources().none { it.role == PlanResourceRole.CoverageMaskAccumulator })
        val draw = graph.passes().filterIsInstance<PlanPass.PathRenderPass>().single().draw
        assertIs<ClippedGeneralPathDraw>(draw)
        assertIs<ClipPlanStrategy.Scissor>(draw.clip)
    }

    @Test
    fun `exact repeated stack shares one mask preparation across consumers`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val graph = compile(sceneOf(
            pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip),
            pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip),
        ))

        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskInitialize>().size)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().size)
        assertEquals(2, graph.passes().filterIsInstance<PlanPass.PathRenderPass>().count { it.draw is ClippedGeneralPathDraw })
    }

    @Test
    fun `distinct clip stacks retain independent ordered mask chains in one frame`() {
        val first = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val second = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.2f)),
            ),
        )

        val graph = compile(sceneOf(
            pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = first),
            pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = second),
        ))

        assertEquals(2, graph.passes().filterIsInstance<PlanPass.ClipMaskInitialize>().size)
        assertEquals(2, graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().size)
        assertEquals(2, graph.passes().filterIsInstance<PlanPass.PathRenderPass>().count { it.draw is ClippedGeneralPathDraw })
    }

    @Test
    fun `capability chain promotes operation clips to W4e without a legacy mapper`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip))

        val selected = CapabilityCompilerChain.of(listOf(W4cPathFillPlanCompiler(), W4dPathStrokePlanCompiler()))
            .select(scene, target(scene))

        assertIs<GpuPlanSelection.Candidate>(selected)
    }

    @Test
    fun `hard path consumer keeps the W4d one-sample binary route inside an AA frame`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val graph = compile(sceneOf(
            pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip),
            pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip),
        ))

        assertEquals(W4eClipPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
        assertTrue(graph.resources().any { it.role == PlanResourceRole.PathHardEdgeMask && it.sampleCountI32 == 1 })
        assertTrue(graph.passes().filterIsInstance<PlanPass.PathRenderPass>().any { it.draw is ClippedBinaryMaskedPathDraw })
    }

    @Test
    fun `complex clips stay attached to both passes of a W4d stencil pair`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val graph = compile(sceneOf(pathDraw(
            coverage = CoverageRequest.ANTIALIASED,
            clip = clip,
            path = concavePath(),
        )))

        val stencilPair = graph.passes().filterIsInstance<PlanPass.PathRenderPass>().filter {
            it.phase == PathRenderPhase.MultisampleStencilProducer ||
                it.phase == PathRenderPhase.MultisampleStencilColorCover
        }
        assertEquals(2, stencilPair.size)
        assertTrue(stencilPair.all { it.draw is ClippedGeneralPathDraw })
    }

    @Test
    fun `inverse path clip retains inverse coverage at its ordered producer`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(inverseClipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )

        val graph = compile(sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip)))

        assertTrue(graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().single().inverseCoverage)
    }

    @Test
    fun `inverse draw combines bounded inverse coverage with its complex clip mask`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val graph = compile(sceneOf(pathDraw(
            coverage = CoverageRequest.ANTIALIASED,
            clip = clip,
            path = inverseClipPath(),
        )))

        val strategy = assertIs<ClippedGeneralPathDraw>(
            graph.passes().filterIsInstance<PlanPass.PathRenderPass>().last().draw,
        ).clip
        assertIs<ClipPlanStrategy.InverseMask>(strategy)
    }

    @Test
    fun `pooled clip capacity refuses the frame before publishing a graph`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip))
        val fullGraph = compile(scene)
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate

        assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(fullGraph.peakFrameLocalBytes)),
        )

        val result: RenderPlanResult.ResourceLimitExceeded = assertIs(
            compiler.plan(candidate, capabilities(), PlanBudget(fullGraph.peakFrameLocalBytes - 1L)),
        )

        assertEquals(W4ePlanDiagnostics.BudgetFrameLocalExceeded, result.diagnostics.single().code)
    }

    @Test
    fun `legacy clip transforms are a stable W4e non-candidate`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.LegacyUnavailable("unknown", false),
            ),
        )

        val selection = assertIs<GpuPlanSelection.NotCandidate>(
            compiler.select(sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip)),
                RenderTargetDescriptor(SceneExtent(16, 16), ColorSpace.SRGB)),
        )

        assertEquals(W4ePlanDiagnostics.LegacyUnavailable, selection.diagnostics().single().code)
    }

    @Test
    fun `513 clip entries are refused before graph emission`() {
        val entry = ClipEntry(
            geometry = GeometryNode.Rect.of(RectF32(1f, 1f, 14f, 14f)),
            operation = ClipOperation.INTERSECT,
            antiAlias = false,
            transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
        )
        val clip = ClipStackNode.Operations.of(List(513) { entry })

        assertIs<GpuPlanSelection.ResourceLimitExceeded>(
            compiler.select(sceneOf(pathDraw(clip = clip)), RenderTargetDescriptor(SceneExtent(16, 16), ColorSpace.SRGB)),
        )
    }

    @Test
    fun `hard path clips use a single-sample mask without entering the AA4 route`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )

        val graph = compile(sceneOf(pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip)))

        assertEquals(W4eClipPlanCompiler.HARD_CAPABILITY_ID, graph.capabilityId)
        assertTrue(graph.resources().none { it.role == PlanResourceRole.MultisampleColorTarget })
        assertTrue(graph.resources().any { it.role == PlanResourceRole.CoverageMaskDepthStencil && it.sampleCountI32 == 1 })
    }

    @Test
    fun `hard draw promotes its frame when an AA path clip needs four-sample coverage`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )

        val graph = compile(sceneOf(pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip)))

        assertEquals(W4eClipPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
        assertTrue(graph.resources().any { it.role == PlanResourceRole.MultisampleColorTarget && it.sampleCountI32 == 4 })
        assertTrue(graph.passes().filterIsInstance<PlanPass.PathRenderPass>().any { it.draw is ClippedBinaryMaskedPathDraw })
    }

    @Test
    fun `intersecting an empty clip produces zero coverage without allocating an orphaned mask pool`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(PathBuilder().build()),
                operation = ClipOperation.INTERSECT,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        val graph = compile(sceneOf(pathDraw(clip = clip)))

        assertTrue(graph.resources().none { it.role in setOf(
            PlanResourceRole.CoverageMaskAccumulator,
            PlanResourceRole.CoverageMaskScratch,
            PlanResourceRole.CoverageMaskMultisampleScratch,
            PlanResourceRole.CoverageMaskDepthStencil,
        ) })
        val strategy = assertIs<ClippedGeneralPathDraw>(
            graph.passes().filterIsInstance<PlanPass.PathRenderPass>().single().draw,
        ).clip
        assertTrue(assertIs<ClipPlanStrategy.Scissor>(strategy).copyDomainI32().isEmpty)
        assertTrue(graph.verifyW4eCompilerWitness())
    }

    @Test
    fun `difference with an inverse-empty clip produces zero coverage without a mask pool`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(PathBuilder(FillRule.INVERSE_WINDING).build()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        val graph = compile(sceneOf(pathDraw(clip = clip)))

        assertTrue(graph.resources().none { it.role in clipMaskRoles() })
        val strategy = assertIs<ClippedGeneralPathDraw>(
            graph.passes().filterIsInstance<PlanPass.PathRenderPass>().single().draw,
        ).clip
        assertTrue(assertIs<ClipPlanStrategy.Scissor>(strategy).copyDomainI32().isEmpty)
    }

    @Test
    fun `empty and identity-only clip stacks publish no unused mask resources`() {
        val empty = ClipStackNode.Operations.of(emptyList())
        val identityOnly = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(PathBuilder().build()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        listOf(empty, identityOnly).forEach { clip ->
            val graph = compile(sceneOf(pathDraw(clip = clip)))
            assertTrue(graph.resources().none { it.role in clipMaskRoles() })
            assertTrue(graph.verifyW4eCompilerWitness())
        }
    }

    @Test
    fun `ordered mask folds keep non-identity operations after empty identities`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(PathBuilder().build()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
            ClipEntry(
                geometry = GeometryNode.RRect.of(RRectF32.of(RectF32(2f, 2f, 14f, 14f), 2f)),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        val graph = compile(sceneOf(pathDraw(clip = clip)))

        assertEquals(
            listOf(ClipCombineOperation.Intersect, ClipCombineOperation.Difference),
            graph.passes().filterIsInstance<PlanPass.ClipMaskFold>().map { it.operation },
        )
    }

    @Test
    fun `AA rounded-rectangle clips use a four-sample producer and promote hard consumers`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.RRect.of(RRectF32.of(RectF32(2f, 2f, 14f, 14f), 2f)),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        val graph = compile(sceneOf(pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip)))

        val producer = graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().single()
        assertTrue(producer.antiAlias)
        assertEquals(4, producer.sampleCountI32)
        assertEquals(W4eClipPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
    }

    @Test
    fun `AA rectangle clip keeps analytic AA in its one-sample producer`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Rect.of(RectF32(2.25f, 2.25f, 13.75f, 13.75f)),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        val graph = compile(sceneOf(pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip)))

        val producer = graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().single()
        assertTrue(producer.antiAlias)
        assertEquals(1, producer.sampleCountI32)
        assertEquals(W4eClipPlanCompiler.HARD_CAPABILITY_ID, graph.capabilityId)
    }

    @Test
    fun `empty inverse draw stays explicit as a hard binary cover in an AA4 clip frame`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val graph = compile(sceneOf(
            pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip, path = PathBuilder(FillRule.INVERSE_WINDING).build()),
            pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip),
        ))

        assertEquals(W4eClipPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
        val inverse = assertIs<ClippedBinaryMaskedPathDraw>(
            graph.passes().filterIsInstance<PlanPass.PathRenderPass>().first {
                it.phase == PathRenderPhase.HardEdgeBinaryColorCover
            }.draw,
        ).clip
        assertEquals(
            InverseInteriorCoverageF32.Zero,
            assertIs<ClipPlanStrategy.InverseMask>(inverse).geometryF32.interiorCoverageF32,
        )
    }

    @Test
    fun `empty inverse draw alone remains an explicit full-domain clipped draw`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )

        val graph = compile(sceneOf(pathDraw(
            coverage = CoverageRequest.HARD_EDGE,
            clip = clip,
            path = PathBuilder(FillRule.INVERSE_WINDING).build(),
        )))

        assertEquals(1, graph.visualCommandCount)
        val inverse = assertIs<ClippedGeneralPathDraw>(
            graph.passes().filterIsInstance<PlanPass.PathRenderPass>().last().draw,
        ).clip
        assertEquals(
            InverseInteriorCoverageF32.Zero,
            assertIs<ClipPlanStrategy.InverseMask>(inverse).geometryF32.interiorCoverageF32,
        )
    }

    @Test
    fun `identity-only clip stack keeps an inverse zero draw domain-bound without allocating a mask`() {
        val graph = compile(sceneOf(pathDraw(
            coverage = CoverageRequest.HARD_EDGE,
            clip = ClipStackNode.Operations.of(emptyList()),
            path = PathBuilder(FillRule.INVERSE_WINDING).build(),
        )))

        assertTrue(graph.resources().none { it.role in clipMaskRoles() })
        val inverse = assertIs<ClippedGeneralPathDraw>(
            graph.passes().filterIsInstance<PlanPass.PathRenderPass>().last().draw,
        ).clip
        assertIs<ClipPlanStrategy.InverseDomain>(inverse)
    }

    @Test
    fun `512 clip entries compile successfully`() {
        val entry = ClipEntry(
            geometry = GeometryNode.Rect.of(RectF32(1f, 1f, 14f, 14f)),
            operation = ClipOperation.INTERSECT,
            antiAlias = false,
            transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
        )

        val graph = compile(sceneOf(pathDraw(clip = ClipStackNode.Operations.of(List(512) { entry }))))

        assertTrue(graph.verifyW4eCompilerWitness())
    }

    @Test
    fun `missing sampled linear mask support remains a promoted W4e capability gap`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate

        val result = assertIs<RenderPlanResult.GapOnPromotedScope>(
            compiler.plan(candidate, capabilities(includeMasks = false), PlanBudget(1L shl 20)),
        )

        assertEquals(W4ePlanDiagnostics.MaskFormatUnavailable, result.diagnostics.single().code)
    }

    @Test
    fun `missing four-sample mask support remains a promoted W4e capability gap`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val scene = sceneOf(pathDraw(clip = clip))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate

        val result = assertIs<RenderPlanResult.GapOnPromotedScope>(
            compiler.plan(candidate, capabilities(includeAaMaskSamples = false), PlanBudget(1L shl 20)),
        )

        assertEquals(W4ePlanDiagnostics.SampleCountUnavailable, result.diagnostics.single().code)
    }

    @Test
    fun `empty AA identities do not require four-sample clip support for a hard producer`() {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Path(PathBuilder().build()),
                operation = ClipOperation.DIFFERENCE,
                antiAlias = true,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
            ClipEntry(
                geometry = GeometryNode.Path(clipPath()),
                operation = ClipOperation.INTERSECT,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
            ),
        )
        val scene = sceneOf(pathDraw(clip = clip))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate

        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(includeAaMaskSamples = false), PlanBudget(1L shl 20)),
        ).plan

        assertEquals(W4eClipPlanCompiler.HARD_CAPABILITY_ID, graph.capabilityId)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().single().sampleCountI32)
    }

    @Test
    fun `clip plans do not reuse pools across different clip transforms`() {
        val first = complexClip(
            ClipEntry(GeometryNode.Path(clipPath()), ClipOperation.INTERSECT, true,
                ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f))),
        )
        val second = complexClip(
            ClipEntry(GeometryNode.Path(clipPath()), ClipOperation.INTERSECT, true,
                ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.2f))),
        )

        val graph = compile(sceneOf(pathDraw(clip = first), pathDraw(clip = second)))

        assertEquals(4, graph.resources().count { it.role == PlanResourceRole.CoverageMaskAccumulator })
    }

    @Test
    fun `clip plans do not reuse pools across sample plans`() {
        val clip = complexClip(
            ClipEntry(GeometryNode.Path(clipPath()), ClipOperation.INTERSECT, true,
                ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f))),
        )

        val graph = compile(sceneOf(
            pathDraw(coverage = CoverageRequest.HARD_EDGE, clip = clip),
            pathDraw(coverage = CoverageRequest.ANTIALIASED, clip = clip),
        ))

        assertEquals(4, graph.resources().count { it.role == PlanResourceRole.CoverageMaskAccumulator })
    }

    @Test
    fun `clip plan identities and resources remain extent-specific`() {
        val clip = complexClip(
            ClipEntry(GeometryNode.Path(clipPath()), ClipOperation.INTERSECT, true,
                ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f))),
        )
        val first = compile(sceneOf(pathDraw(clip = clip)))
        val secondScene = sceneOf(SceneExtent(32, 32), pathDraw(clip = clip))
        val second = compile(secondScene)

        assertNotEquals(first.id, second.id)
        assertEquals(32, second.targetExtent.width)
        assertEquals(32, second.targetExtent.height)
        assertTrue(second.resources().filter { it.role == PlanResourceRole.CoverageMaskAccumulator }
            .all { it.copyExtent()?.width == 32 && it.copyExtent()?.height == 32 })
    }

    @Test
    fun `entry attempted-edge limit refuses a clip before graph publication`() {
        assertClipLimitReason(
            W4eClipPlanCompiler(ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerEntryI32 = 1),
            )),
            "EntryAttemptedEdgeLimit",
        )
    }

    @Test
    fun `frame attempted-edge limit refuses a clip before graph publication`() {
        assertClipLimitReason(
            W4eClipPlanCompiler(ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerFrameI32 = 1),
            )),
            "FrameAttemptedEdgeLimit",
        )
    }

    @Test
    fun `entry vertex limit refuses a clip before graph publication`() {
        assertClipLimitReason(
            W4eClipPlanCompiler(ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxEmittedVertexCountPerEntryI32 = 1),
            )),
            "EntryVertexLimit",
        )
    }

    @Test
    fun `entry index limit refuses a clip before graph publication`() {
        assertClipLimitReason(
            W4eClipPlanCompiler(ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxEmittedIndexCountPerEntryI32 = 1),
            )),
            "EntryIndexLimit",
        )
    }

    @Test
    fun `entry snapshot limit refuses a clip before graph publication`() {
        assertClipLimitReason(
            W4eClipPlanCompiler(ClipPreparationPolicyF64(
                limitsI64 = ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 1L),
            )),
            "EntrySnapshotByteLimit",
        )
    }

    @Test
    fun `distinct clip stacks cumulatively enforce the frame vertex limit`() {
        assertDistinctClipFrameLimit(FrameLimitAxis.Vertices)
    }

    @Test
    fun `distinct clip stacks cumulatively enforce the frame index limit`() {
        assertDistinctClipFrameLimit(FrameLimitAxis.Indices)
    }

    @Test
    fun `distinct clip stacks cumulatively enforce the frame snapshot limit`() {
        assertDistinctClipFrameLimit(FrameLimitAxis.SnapshotBytes)
    }

    @Test
    fun `reused clip stack debits each strict frame ledger threshold once`() {
        FrameLimitAxis.entries.forEach { axis ->
            val clip = clipForLedger(Matrix3x3F32.Identity)
            val scene = sceneOf(pathDraw(clip = clip), pathDraw(clip = clip))

            assertIs<GpuPlanSelection.Candidate>(
                compilerForFrameLimit(axis).select(scene, target(scene)),
            )
        }
    }

    @Test
    fun `draw-count limit wins before clip preparation can consume its frame ledger`() {
        val boundedCompiler = W4eClipPlanCompiler(ClipPreparationPolicyF64(
            limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerFrameI32 = 1),
        ))
        val clip = complexClip(
            ClipEntry(GeometryNode.Rect.of(RectF32(1f, 1f, 14f, 14f)), ClipOperation.INTERSECT, false,
                ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity)),
        )
        val scene = sceneOf(*List(513) { pathDraw(clip = clip) }.toTypedArray())

        val result = assertIs<GpuPlanSelection.ResourceLimitExceeded>(boundedCompiler.select(scene, target(scene)))

        assertTrue(result.diagnostics().single().message.contains("at most 512 visual path draws"))
    }

    private fun compile(scene: SceneSnapshot, planner: W4eClipPlanCompiler = compiler): RenderGraph {
        val candidate = assertIs<GpuPlanSelection.Candidate>(planner.select(scene, target(scene))).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            planner.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun sceneOf(vararg draws: SceneCommand.Draw): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(16, 16), ColorSpace.SRGB, draws.toList(),
    )

    private fun sceneOf(extent: SceneExtent, vararg draws: SceneCommand.Draw): SceneSnapshot = SceneSnapshot.of(
        extent, ColorSpace.SRGB, draws.toList(),
    )

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun complexClip(vararg entries: ClipEntry): ClipStackNode = ClipStackNode.Operations.of(entries.toList())

    private fun clipMaskRoles(): Set<PlanResourceRole> = setOf(
        PlanResourceRole.CoverageMaskAccumulator,
        PlanResourceRole.CoverageMaskScratch,
        PlanResourceRole.CoverageMaskMultisampleScratch,
        PlanResourceRole.CoverageMaskDepthStencil,
    )

    private fun assertClipLimitReason(planner: W4eClipPlanCompiler, reason: String) {
        val clip = complexClip(
            ClipEntry(
                geometry = GeometryNode.Rect.of(RectF32(1f, 1f, 14f, 14f)),
                operation = ClipOperation.INTERSECT,
                antiAlias = false,
                transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
            ),
        )

        val result = assertIs<GpuPlanSelection.ResourceLimitExceeded>(
            planner.select(sceneOf(pathDraw(clip = clip)), RenderTargetDescriptor(SceneExtent(16, 16), ColorSpace.SRGB)),
        )

        assertTrue(result.diagnostics().single().message.contains(reason))
    }

    private fun assertDistinctClipFrameLimit(axis: FrameLimitAxis) {
        val scene = sceneOf(
            pathDraw(clip = clipForLedger(Matrix3x3F32.Identity)),
            pathDraw(clip = clipForLedger(Matrix3x3F32.rotation(0.1f))),
        )

        val result = assertIs<GpuPlanSelection.ResourceLimitExceeded>(
            compilerForFrameLimit(axis).select(scene, target(scene)),
        )

        assertTrue(result.diagnostics().single().message.contains(axis.reason))
    }

    private fun compilerForFrameLimit(axis: FrameLimitAxis): W4eClipPlanCompiler = W4eClipPlanCompiler(
        ClipPreparationPolicyF64(
            limitsI32 = ClipPreparationLimitsI32().copy(
                maxEmittedVertexCountPerFrameI32 = if (axis == FrameLimitAxis.Vertices) axis.limit.toInt() else Int.MAX_VALUE,
                maxEmittedIndexCountPerFrameI32 = if (axis == FrameLimitAxis.Indices) axis.limit.toInt() else Int.MAX_VALUE,
            ),
            limitsI64 = ClipPreparationLimitsI64().copy(
                maxSnapshotByteCountPerFrameI64 = if (axis == FrameLimitAxis.SnapshotBytes) axis.limit else Long.MAX_VALUE,
            ),
        ),
    )

    private fun clipForLedger(transform: Matrix3x3F32): ClipStackNode = complexClip(
        ClipEntry(
            geometry = GeometryNode.Rect.of(RectF32(1f, 1f, 14f, 14f)),
            operation = ClipOperation.INTERSECT,
            antiAlias = false,
            transform = ClipTransformSnapshot.Known.of(transform),
        ),
    )

    private fun pathDraw(
        coverage: CoverageRequest = CoverageRequest.HARD_EDGE,
        clip: ClipStackNode,
        path: PathF32 = path(),
    ): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xC0FF0000u)
        return SceneCommand.Draw(DrawNode(
            geometry = GeometryNode.Path(path),
            material = MaterialNode.Solid(color),
            coverage = coverage,
            clip = clip,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.rotation(0.25f),
            origin = DrawOrigin.PATH,
            paint = PaintNode(
                color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f,
                coverage == CoverageRequest.ANTIALIASED,
            ),
        ))
    }

    private fun path(): PathF32 = PathBuilder()
        .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build()

    private fun clipPath(): PathF32 = PathBuilder()
        .moveTo(3f, 3f).lineTo(13f, 3f).lineTo(3f, 13f).close().build()

    private fun concavePath(): PathF32 = PathBuilder()
        .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(12f, 12f).lineTo(7f, 6f).lineTo(2f, 12f).close().build()

    private fun inverseClipPath(): PathF32 = PathBuilder(FillRule.INVERSE_WINDING)
        .moveTo(3f, 3f).lineTo(13f, 3f).lineTo(3f, 13f).close().build()

    private fun capabilities(
        includeMasks: Boolean = true,
        includeAaMaskSamples: Boolean = true,
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = setOf(
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource)),
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, setOf(PlanResourceUsage.RenderAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1, setOf(PlanResourceUsage.DepthStencilAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4, setOf(PlanResourceUsage.DepthStencilAttachment)),
        ).let { supports -> if (includeMasks) supports + buildSet {
            add(PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)))
            if (includeAaMaskSamples) add(PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 4, setOf(PlanResourceUsage.RenderAttachment)))
        } else supports },
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, 1),
        ).let { supports -> if (includeMasks && includeAaMaskSamples) supports +
            PlanTextureResolveSupport.of(PlanTextureFormat.CoverageMask, 4, 1) else supports },
    )

    private enum class FrameLimitAxis(val limit: Long, val reason: String) {
        Vertices(4L, "FrameVertexLimit"),
        Indices(6L, "FrameIndexLimit"),
        SnapshotBytes(48L, "FrameSnapshotByteLimit"),
    }
}
