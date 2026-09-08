package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
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
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class W4dGeneralPathPlanCompilerTest {
    private val compiler = W4dGeneralPathPlanCompiler()

    @Test
    fun `general lane owns only general transforms while narrow paths keep their routes`() {
        val identityHard = sceneOf(pathDraw())
        val axisHard = sceneOf(pathDraw(transform = Matrix3x3F32.translation(1f, 2f)))
        val affineHard = sceneOf(pathDraw(transform = Matrix3x3F32.rotation(0.25f)))
        val perspectiveHard = sceneOf(pathDraw(transform = Matrix3x3F32(persp0 = 0.01f)))
        val antialiased = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED))

        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(identityHard, target(identityHard)))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(axisHard, target(axisHard)))
        assertIs<GpuPlanSelection.Candidate>(compiler.select(affineHard, target(affineHard)))
        assertIs<GpuPlanSelection.Candidate>(compiler.select(perspectiveHard, target(perspectiveHard)))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(antialiased, target(antialiased)))

        val chain = CapabilityCompilerChain.of(listOf(W4cPathFillPlanCompiler(), W4dPathStrokePlanCompiler()))
        assertEquals(W4cPathFillPlanCompiler.CAPABILITY_ID, compile(chain, identityHard).capabilityId)
        assertEquals(W4dPathStrokePlanCompiler.CAPABILITY_ID, compile(chain, sceneOf(pathDraw(style = PaintStyleNode.STROKE))).capabilityId)
    }

    @Test
    fun `all hard transformed frame stays single sample without a resolve`() {
        val scene = sceneOf(pathDraw(transform = Matrix3x3F32.rotation(0.25f)))
        val graph = compile(compiler, scene)

        assertEquals(W4dGeneralPathPlanCompiler.HARD_CAPABILITY_ID, graph.capabilityId)
        assertTrue(graph.resources().none { it.role == PlanResourceRole.MultisampleColorTarget })
        assertTrue(graph.resources().none { it.role == PlanResourceRole.PathHardEdgeMask })
        graph.passes().filterIsInstance<PlanPass.PathRenderPass>().forEach { pass ->
            assertEquals(SamplePlan.SingleSample, pass.draw.sample)
            assertEquals(null, pass.resolveTarget)
        }
    }

    @Test
    fun `mixed AA frame preserves ordered hard binary coverage and resolves only at the final color draw`() {
        val transform = Matrix3x3F32.rotation(0.25f)
        val mixed = sceneOf(
            pathDraw(coverage = CoverageRequest.ANTIALIASED, transform = transform, path = concavePath()),
            pathDraw(transform = transform),
        )
        val hardOnly = sceneOf(pathDraw(transform = transform))
        val graph = compile(compiler, mixed)
        val hardGraph = compile(compiler, hardOnly)
        val colorPasses = graph.passes().filterIsInstance<PlanPass.PathRenderPass>().filter { pass ->
            pass.phase in setOf(
                PathRenderPhase.MultisampleDirectColor,
                PathRenderPhase.MultisampleStencilColorCover,
                PathRenderPhase.HardEdgeBinaryColorCover,
            )
        }
        val hardCover = assertIs<PlanPass.PathRenderPass>(colorPasses.last())
        val binary = assertIs<BinaryMaskedPathDraw>(hardCover.draw)
        val hardOnlyDraw = hardGraph.passes().filterIsInstance<PlanPass.PathRenderPass>().first().draw

        assertEquals(W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
        assertEquals(listOf(0, 1), colorPasses.map { it.draw.commandIndex })
        assertEquals(CoveragePlan.BinaryMaskCover4, binary.coverage)
        assertEquals(SamplePlan.Multisample4, binary.sample)
        assertEquals(BinaryMaskFetchPlan.TextureLoadUnfiltered, binary.maskFetch)
        assertEquals(4, binary.broadcastSampleCountI32)
        assertEquals(CoveragePlan.FullOrScissor, binary.producer.coverage)
        assertEquals(SamplePlan.SingleSample, binary.producer.sample)
        assertContentEquals(directVertices(hardOnlyDraw), directVertices(binary.producer))
        assertEquals(hardOnlyDraw.copyScissorI32(), binary.copyScissorI32())
        assertEquals(binary.mask, graph.resources().single { it.role == PlanResourceRole.PathHardEdgeMask }.id)
        assertTrue(colorPasses.dropLast(1).all { it.resolveTarget == null })
        assertEquals(graph.resources().single { it.role == PlanResourceRole.LogicalTarget }.id, hardCover.resolveTarget)
    }

    @Test
    fun `perspective horizon and 513 admitted general draws are stable terminal refusals`() {
        val horizon = sceneOf(pathDraw(
            transform = Matrix3x3F32(persp0 = 1f, persp2 = 0f),
            path = PathBuilder().moveTo(0f, 2f).lineTo(4f, 2f).lineTo(0f, 6f).close().build(),
        ))
        val tooMany = sceneOf(*Array(513) {
            pathDraw(coverage = CoverageRequest.ANTIALIASED, transform = Matrix3x3F32.rotation(0.25f))
        })

        val invalid = assertIs<GpuPlanSelection.InvalidScene>(compiler.select(horizon, target(horizon)))
        assertEquals(W4dGeneralPlanDiagnostics.ProjectionHorizonCrossing, invalid.diagnostics().single().code)
        val limit = assertIs<GpuPlanSelection.ResourceLimitExceeded>(compiler.select(tooMany, target(tooMany)))
        assertEquals(W4dGeneralPlanDiagnostics.PathResourceLimit, limit.diagnostics().single().code)
        assertFailsWith<UnsupportedOperationException> {
            (invalid.diagnostics() as MutableList).clear()
        }
    }

    @Test
    fun `transformed AA device facts candidate ownership and capability identity fail closed`() {
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, transform = Matrix3x3F32.rotation(0.25f)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val base = aaCapabilities()
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, base, PlanBudget(1L shl 20)),
        ).plan
        val alteredFacts = capabilities(
            sampleSupports = base.supportedTextureSampleSupports() + PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
        )
        val altered = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, alteredFacts, PlanBudget(1L shl 20)),
        ).plan
        val missingFourSampleColor = capabilities(
            sampleSupports = base.supportedTextureSampleSupports().filterNot { support ->
                support.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) &&
                    support.sampleCountI32 == 4
            }.toSet(),
        )

        assertNotEquals(graph.id, altered.id)
        val unavailable = assertIs<RenderPlanResult.GapOnPromotedScope>(
            compiler.plan(candidate, missingFourSampleColor, PlanBudget(1L shl 20)),
        )
        assertEquals(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, unavailable.diagnostics.single().code)
        assertIs<RenderPlanResult.InvalidScene>(
            W4dGeneralPathPlanCompiler().plan(candidate, base, PlanBudget(1L shl 20)),
        )
    }

    @Test
    fun `transformed AA sample support and resolve support have distinct stable terminal diagnostics`() {
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, transform = Matrix3x3F32.rotation(0.25f)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val sampleMissing = capabilities(
            sampleSupports = aaCapabilities().supportedTextureSampleSupports().filterNot { support ->
                support.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) &&
                    support.sampleCountI32 == 4
            }.toSet(),
        )
        val resolveMissing = capabilities(sampleSupports = aaCapabilities().supportedTextureSampleSupports(), resolves = emptySet())

        val missingSample = assertIs<RenderPlanResult.GapOnPromotedScope>(
            compiler.plan(candidate, sampleMissing, PlanBudget(1L shl 20)),
        )
        val missingResolve = assertIs<RenderPlanResult.GapOnPromotedScope>(
            compiler.plan(candidate, resolveMissing, PlanBudget(1L shl 20)),
        )

        assertEquals(W4dGeneralPlanDiagnostics.TextureSampleSupportUnavailable, missingSample.diagnostics.single().code)
        assertEquals(W4dGeneralPlanDiagnostics.ResolveUnsupported, missingResolve.diagnostics.single().code)
    }

    @Test
    fun `transformed AA direct path accepts its unused depth attachment without stencil cover support`() {
        val scene = sceneOf(pathDraw(coverage = CoverageRequest.ANTIALIASED, transform = Matrix3x3F32.rotation(0.25f)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val capabilities = aaCapabilities(
            operations = PlanOperationCapability.entries.toSet() - PlanOperationCapability.StencilCover,
        )

        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities, PlanBudget(1L shl 20)),
        ).plan

        assertEquals(W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID, graph.capabilityId)
        val direct = graph.passes().filterIsInstance<PlanPass.PathRenderPass>().single()
        assertEquals(PathRenderPhase.MultisampleDirectColor, direct.phase)
        assertEquals(PlanResourceRole.DepthStencil, graph.resources().single { it.id == direct.depthStencil }.role)
    }

    @Test
    fun `transformed AA stencil path still requires stencil cover support`() {
        val scene = sceneOf(pathDraw(
            coverage = CoverageRequest.ANTIALIASED,
            transform = Matrix3x3F32.rotation(0.25f),
            path = concavePath(),
        ))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val capabilities = aaCapabilities(
            operations = PlanOperationCapability.entries.toSet() - PlanOperationCapability.StencilCover,
        )

        assertIs<RenderPlanResult.GapOnPromotedScope>(
            compiler.plan(candidate, capabilities, PlanBudget(1L shl 20)),
        )
    }

    private fun compile(compiler: GpuPlanCompiler, scene: SceneSnapshot): RenderGraph {
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, aaCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun directVertices(draw: PathRenderDraw): FloatArray {
        val geometry = when (val value = draw.copyPathGeometry()) {
            is PathDrawGeometry.Fill -> value.valueF32
            is PathDrawGeometry.Stroke -> value.valueF32.copyFillGeometryF32()
        }
        return requireNotNull(geometry.copyDirectTriangleF32OrNull()).copyVerticesF32()
    }

    private fun sceneOf(vararg draws: SceneCommand.Draw): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(16, 16),
        ColorSpace.SRGB,
        draws.toList(),
    )

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun pathDraw(
        style: PaintStyleNode = PaintStyleNode.FILL,
        coverage: CoverageRequest = CoverageRequest.HARD_EDGE,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        path: PathF32 = trianglePath(),
    ): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xC0FF0000u)
        return SceneCommand.Draw(
            DrawNode(
                geometry = GeometryNode.Path(path),
                material = MaterialNode.Solid(color),
                coverage = coverage,
                clip = ClipStackNode.Empty,
                blend = BlendNode.SrcOver,
                effects = EffectStack.Empty,
                transform = transform,
                origin = DrawOrigin.PATH,
                paint = PaintNode(
                    color = color,
                    shader = null,
                    blendMode = BlendMode.SRC_OVER,
                    blender = null,
                    colorFilter = null,
                    maskFilter = null,
                    imageFilter = null,
                    pathEffect = null,
                    style = style,
                    strokeWidth = 2f,
                    strokeCap = StrokeCapNode.BUTT,
                    strokeJoin = StrokeJoinNode.MITER,
                    strokeMiter = 4f,
                    antiAlias = coverage == CoverageRequest.ANTIALIASED,
                ),
            ),
        )
    }

    private fun trianglePath(): PathF32 = PathBuilder()
        .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build()

    private fun concavePath(): PathF32 = PathBuilder()
        .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(12f, 12f).lineTo(7f, 6f).lineTo(2f, 12f).close().build()

    private fun aaCapabilities(
        operations: Set<PlanOperationCapability> = PlanOperationCapability.entries.toSet(),
    ): PlanCapabilitySnapshot = capabilities(
        sampleSupports = setOf(
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                4,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                1,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        ),
        operations = operations,
    )

    private fun capabilities(
        sampleSupports: Set<PlanTextureSampleSupport> = emptySet(),
        resolves: Set<PlanTextureResolveSupport> = setOf(
            PlanTextureResolveSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                1,
            ),
        ),
        operations: Set<PlanOperationCapability> = PlanOperationCapability.entries.toSet(),
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = operations,
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = sampleSupports,
        supportedTextureResolveSupports = resolves,
    )
}
