package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
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
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class W4dPathStrokePlanCompilerTest {
    private val compiler = W4dPathStrokePlanCompiler()

    @Test
    fun selectsMixedFramesOnlyWhenTheyContainAStroke() {
        val mixed = sceneOf(listOf(pathDraw(PaintStyleNode.FILL), pathDraw(PaintStyleNode.STROKE)))
        val fills = sceneOf(listOf(pathDraw(PaintStyleNode.FILL)))

        assertIs<GpuPlanSelection.Candidate>(compiler.select(mixed, target(mixed)))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(fills, target(fills)))
    }

    @Test
    fun acceptsOneAnd512StrokeDrawsButNot513() {
        val one = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val many = sceneOf(List(512) { pathDraw(PaintStyleNode.STROKE) })
        val tooMany = sceneOf(List(513) { pathDraw(PaintStyleNode.STROKE) })

        assertIs<GpuPlanSelection.Candidate>(compiler.select(one, target(one)))
        assertIs<GpuPlanSelection.Candidate>(compiler.select(many, target(many)))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(tooMany, target(tooMany)))
    }

    @Test
    fun planningSealsTheMathStrokeAsTheOnlyPathDrawAuthority() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan

        assertEquals(W4dPathStrokePlanCompiler.CAPABILITY_ID, graph.capabilityId)
        assertIs<PathStrokeDraw>(assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw)
    }

    @Test
    fun zeroWidthStrokeAndFillSealsFiniteZeroInsteadOfHairline() {
        val scene = sceneOf(listOf(pathDraw(PaintStyleNode.STROKE_AND_FILL, 0f)))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan

        val draw = assertIs<PathStrokeDraw>(graph.passes().mapNotNull { pass ->
            when (pass) {
                is PlanPass.RenderPass -> pass.draws().singleOrNull()
                is PlanPass.StencilProducer -> pass.draw
                else -> null
            }
        }.single())
        assertEquals(PathStrokeDrawMode.StrokeAndFill, draw.mode)
        assertEquals(0.0, assertIs<PathStrokeWidthF64.Finite>(draw.styleF64.widthF64).valueF64)
    }

    @Test
    fun `513 visual draws are refused even when the first 512 are off target`() {
        val offTarget = pathDraw(PaintStyleNode.STROKE).let { command ->
            SceneCommand.Draw(command.node.copy(transform = Matrix3x3F32(tx = 1_000f, ty = 1_000f)))
        }
        val scene = sceneOf(List(512) { offTarget } + pathDraw(PaintStyleNode.STROKE))

        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(scene, target(scene)))
    }

    private fun sceneOf(draws: List<SceneCommand.Draw>): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(16, 16), ColorSpace.SRGB, draws,
    )

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun capabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
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
    )

    private fun pathDraw(style: PaintStyleNode, width: Float = 2f): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xFFFF0000u)
        val paint = PaintNode(
            color, null, BlendMode.SRC_OVER, null, null, null, null, null,
            style, width, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, false,
        )
        val path = PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build()
        return SceneCommand.Draw(DrawNode(
            GeometryNode.Path(path), MaterialNode.Solid(color), CoverageRequest.HARD_EDGE,
            ClipStackNode.Empty, BlendNode.SrcOver, EffectStack.Empty, Matrix3x3F32.Identity,
            DrawOrigin.PATH, paint,
        ))
    }
}
