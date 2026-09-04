package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
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
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class W4bAnalyticRRectPlanCompilerTest {
    private val compiler = W4bAnalyticRRectPlanCompiler()

    @Test
    fun `rrect admission produces normalized device analytic rrect draw`() {
        val graph = ready(rrect(transform = Matrix3x3F32(sx = -1f, sy = 1f, tx = 4f)))

        val draw = assertIs<AnalyticRRectDraw>(renderPass(graph).draws().single())
        assertEquals(W4bAnalyticRRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
        assertEquals(DrawOrigin.RRECT, draw.origin)
        assertEquals(
            RRectF32.of(
                RectF32(0f, 0f, 4f, 4f),
                CornerRadiiF32.of(2f, 1f), CornerRadiiF32.of(1f, 1f),
                CornerRadiiF32.of(0.5f, 1f), CornerRadiiF32.of(1f, 2f),
            ),
            draw.copyDeviceShape(),
        )
        assertEquals(RectI32(0, 0, 4, 4), draw.copyRasterBounds())
        assertEquals(RectI32(0, 0, 4, 4), draw.copyScissor())
    }

    @Test
    fun `rect and rrect remain ordered analytic rrect draws with their origins`() {
        val graph = ready(listOf(rect(), rrect()))
        val draws = renderPass(graph).draws().map { assertIs<AnalyticRRectDraw>(it) }

        assertEquals(listOf(DrawOrigin.RECT, DrawOrigin.RRECT), draws.map { it.origin })
        assertPositiveZeros(draws.first().copyDeviceShape())
    }

    @Test
    fun `selection rejects scenes without rrect provenance or with mismatched geometry provenance`() {
        assertIs<GpuPlanSelection.NotCandidate>(select(rect()))
        assertIs<GpuPlanSelection.NotCandidate>(select(SceneCommand.Draw(rrect().node.copy(origin = DrawOrigin.RECT))))
    }

    @Test
    fun `selection rejects rendering axes outside W4b`() {
        val base = rrect().node
        val rejected = listOf(
            base.copy(clip = ClipStackNode.DeviceRect.of(RectF32(0f, 0f, 4f, 4f), antiAlias = true)),
            base.copy(clip = operationClip(GeometryNode.Path(PathBuilder().addRect(RectF32(0f, 0f, 4f, 4f)).build()))),
            base.copy(clip = operationClip(GeometryNode.RRect.of(RRectF32.of(RectF32(0f, 0f, 4f, 4f), 1f)))),
            base.copy(transform = Matrix3x3F32.rotation(0.25f)),
            base.copy(paint = requireNotNull(base.paint).copy(shader = MaterialNode.Transparent)),
            base.copy(blend = BlendNode.Mode(BlendMode.SRC)),
            base.copy(coverage = CoverageRequest.HARD_EDGE),
        )

        rejected.forEach { node -> assertIs<GpuPlanSelection.NotCandidate>(select(SceneCommand.Draw(node))) }
        assertIs<GpuPlanSelection.NotCandidate>(select(rrect(), colorSpace = ColorSpace.DISPLAY_P3))
    }

    @Test
    fun `negative and non finite rrect radii invalidate the scene`() {
        val negative = rrect(shape = RRectF32.of(RectF32(0f, 0f, 4f, 4f), CornerRadiiF32.of(-1f)))
        val nonFinite = rrect(shape = RRectF32.of(RectF32(0f, 0f, 4f, 4f), CornerRadiiF32.of(Float.NaN)))

        assertIs<GpuPlanSelection.InvalidScene>(select(negative))
        assertIs<GpuPlanSelection.InvalidScene>(select(nonFinite))
    }

    @Test
    fun `W4b accepts 512 visual draws with rrect provenance and rejects 513`() {
        val accepted = List(511) { rect() } + rrect()
        val rejected = accepted + rect()

        assertIs<GpuPlanSelection.Candidate>(select(accepted))
        assertIs<GpuPlanSelection.NotCandidate>(select(rejected))
    }

    @Test
    fun `ready graph declares the exact five frame local resources`() {
        val graph = ready(rrect())
        val resources = graph.resources()

        assertEquals(5, resources.size)
        assertEquals(
            listOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.ReadbackStaging,
                PlanResourceRole.VertexData,
                PlanResourceRole.IndexData,
                PlanResourceRole.UniformData,
            ),
            resources.map { it.role },
        )
        assertEquals(listOf(0, 1, 0, 0, 0), resources.map { it.firstPassIndex })
        assertEquals(listOf(2, 2, 2, 2, 2), resources.map { it.lastPassIndexExclusive })
        assertEquals(resources.fold(0L) { sum, resource -> Math.addExact(sum, resource.byteSize) }, graph.peakFrameLocalBytes)
    }

    private fun ready(command: SceneCommand.Draw): RenderGraph = ready(listOf(command))

    private fun ready(commands: Collection<SceneCommand>): RenderGraph {
        val scene = SceneSnapshot.of(SceneExtent(4, 4), ColorSpace.SRGB, commands)
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun renderPass(graph: RenderGraph): PlanPass.RenderPass =
        assertIs<PlanPass.RenderPass>(graph.passes().single { it is PlanPass.RenderPass })

    private fun rect(): SceneCommand.Draw = SceneCommand.Draw(DrawNode(
        geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
        material = MaterialNode.Solid(COLOR),
        coverage = CoverageRequest.ANTIALIASED,
        clip = ClipStackNode.Empty,
        blend = BlendNode.SrcOver,
        effects = EffectStack.Empty,
        transform = Matrix3x3F32.Identity,
        origin = DrawOrigin.RECT,
        paint = paint(),
    ))

    private fun rrect(
        shape: RRectF32 = RRECT,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
    ): SceneCommand.Draw = SceneCommand.Draw(DrawNode(
        geometry = GeometryNode.RRect.of(shape),
        material = MaterialNode.Solid(COLOR),
        coverage = CoverageRequest.ANTIALIASED,
        clip = ClipStackNode.Empty,
        blend = BlendNode.SrcOver,
        effects = EffectStack.Empty,
        transform = transform,
        origin = DrawOrigin.RRECT,
        paint = paint(),
    ))

    private fun paint(): PaintNode = PaintNode(
        COLOR, null, BlendMode.SRC_OVER, null, null, null, null, null,
        PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true,
    )

    private fun operationClip(geometry: GeometryNode): ClipStackNode = ClipStackNode.Operations.of(
        listOf(ClipEntry(geometry, ClipOperation.INTERSECT)),
    )

    private fun select(command: SceneCommand.Draw, colorSpace: ColorSpace = ColorSpace.SRGB): GpuPlanSelection =
        select(listOf(command), colorSpace)

    private fun select(commands: Collection<SceneCommand>, colorSpace: ColorSpace = ColorSpace.SRGB): GpuPlanSelection {
        val scene = SceneSnapshot.of(SceneExtent(4, 4), colorSpace, commands)
        return compiler.select(scene, target(scene))
    }

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun assertPositiveZeros(shape: RRectF32) {
        listOf(
            shape.topLeft.x, shape.topLeft.y, shape.topRight.x, shape.topRight.y,
            shape.bottomRight.x, shape.bottomRight.y, shape.bottomLeft.x, shape.bottomLeft.y,
        ).forEach { radius -> assertEquals(0, radius.toRawBits()) }
    }

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
    )

    private companion object {
        val COLOR: ColorARGB = ColorARGB.fromPackedUInt(0x80FF0000u)
        val RRECT: RRectF32 = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(1f, 1f), CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 2f), CornerRadiiF32.of(0.5f, 1f),
        )
    }
}
