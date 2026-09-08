package org.graphiks.kanvas.gpu.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private fun compile(scene: SceneSnapshot): RenderGraph {
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun sceneOf(vararg draws: SceneCommand.Draw): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(16, 16), ColorSpace.SRGB, draws.toList(),
    )

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun complexClip(vararg entries: ClipEntry): ClipStackNode = ClipStackNode.Operations.of(entries.toList())

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

    private fun capabilities(includeMasks: Boolean = true): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
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
        ).let { supports -> if (includeMasks) supports + setOf(
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 4, setOf(PlanResourceUsage.RenderAttachment)),
        ) else supports },
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, 1),
        ).let { supports -> if (includeMasks) supports + PlanTextureResolveSupport.of(PlanTextureFormat.CoverageMask, 4, 1) else supports },
    )
}
