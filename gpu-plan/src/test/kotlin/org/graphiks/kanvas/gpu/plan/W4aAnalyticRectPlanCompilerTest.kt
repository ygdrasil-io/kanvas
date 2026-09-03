package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class W4aAnalyticRectPlanCompilerTest {
    private val compiler = W4aAnalyticRectPlanCompiler()

    @Test
    fun `fractional rect seals exact device raster and scissor bounds`() {
        val graph = ready(
            solidRect(0.25f, 0.5f, 3.75f, 2.25f, clip = deviceClip(1f, 0f, 3f, 3f, antiAlias = false)),
            width = 4,
            height = 3,
        )

        val draw = assertIs<AnalyticRectDraw>(renderPass(graph).draws().single())
        assertEquals(RectF32(0.25f, 0.5f, 3.75f, 2.25f), draw.copyDeviceBounds())
        assertEquals(RectI32(0, 0, 4, 3), draw.copyRasterBounds())
        assertEquals(RectI32(1, 0, 3, 3), draw.copyScissor())
        assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
    }

    @Test
    fun `unsupported semantic axes are gaps while non finite input is invalid`() {
        val base = solidRect().node
        val gaps = listOf(
            "hard-edge" to base.copy(coverage = CoverageRequest.HARD_EDGE),
            "stroke" to base.copy(paint = requireNotNull(base.paint).copy(style = PaintStyleNode.STROKE, strokeWidth = 1f)),
            "non-solid" to base.copy(material = MaterialNode.Transparent),
            "aa-clip" to base.copy(clip = deviceClip(0f, 0f, 3f, 3f, antiAlias = true)),
            "fractional-clip" to base.copy(clip = deviceClip(0.5f, 0f, 3f, 3f, antiAlias = false)),
            "complex-clip" to base.copy(clip = ClipStackNode.Operations.of(emptyList())),
            "rotation" to base.copy(transform = Matrix3x3F32.rotation(0.25f)),
            "rrect" to base.copy(geometry = GeometryNode.RRect.of(RRectF32.of(RectF32(0f, 0f, 3f, 2f), 0.5f))),
            "path" to base.copy(geometry = GeometryNode.Path(PathBuilder().addRect(RectF32(0f, 0f, 3f, 2f)).build()), origin = DrawOrigin.PATH),
            "oval" to base.copy(geometry = GeometryNode.Path(PathBuilder().addOval(RectF32(0f, 0f, 3f, 2f)).build()), origin = DrawOrigin.PATH),
            "empty" to base.copy(geometry = GeometryNode.Rect.of(RectF32(1f, 0f, 1f, 2f))),
            "inverted" to base.copy(geometry = GeometryNode.Rect.of(RectF32(3f, 0f, 1f, 2f))),
            "skew" to base.copy(transform = Matrix3x3F32.skewing(0.25f, 0f)),
            "perspective" to base.copy(transform = Matrix3x3F32(persp0 = 0.1f)),
            "fully-clipped" to base.copy(clip = deviceClip(0f, 3f, 4f, 4f, antiAlias = false)),
        )
        gaps.forEach { (label, node) -> assertIs<GpuPlanSelection.NotCandidate>(select(SceneCommand.Draw(node)), label) }
        assertIs<GpuPlanSelection.NotCandidate>(select(SceneCommand.DrawColor(ColorARGB.White, BlendMode.SRC_OVER)))
        assertIs<GpuPlanSelection.InvalidScene>(select(SceneCommand.Draw(base.copy(
            geometry = GeometryNode.Rect.of(RectF32(Float.NaN, 0f, 1f, 1f)),
        ))))
    }

    @Test
    fun `mixed coordinates negative scale and metadata remain candidates`() {
        val metadata = listOf(
            SceneCommand.SetTransform(Matrix3x3F32.Identity),
            SceneCommand.SetClip(ClipStackNode.Empty),
            SceneCommand.Annotation.of(RectF32(0f, 0f, 4f, 3f), "fixture", "w4a"),
        )
        val mixed = ready(metadata + listOf(
            solidRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
            solidRect(left = 1.25f, top = 0.5f, right = 3.75f, bottom = 2.25f),
        ))
        assertEquals(2, renderPass(mixed).draws().size)
        assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, mixed.capabilityId)
        assertIs<GpuPlanSelection.Candidate>(select(solidRect(transform = Matrix3x3F32(sx = -1f, sy = 1f, tx = 4f))))
    }

    @Test
    fun `finite provenance metadata does not veto sealed W4a draws`() {
        val metadata = listOf(
            SceneCommand.SetTransform(Matrix3x3F32.rotation(0.25f)),
            SceneCommand.SetTransform(Matrix3x3F32.skewing(0.25f, 0f)),
            SceneCommand.SetTransform(Matrix3x3F32(persp0 = 0.1f)),
            SceneCommand.SetClip(deviceClip(0f, 0f, 4f, 3f, antiAlias = true)),
            SceneCommand.SetClip(deviceClip(0.5f, 0f, 4f, 3f, antiAlias = false)),
            SceneCommand.SetClip(ClipStackNode.Operations.of(emptyList())),
        )

        assertIs<GpuPlanSelection.Candidate>(select(metadata + solidRect()))
    }

    @Test
    fun `non finite provenance metadata remains invalid`() {
        val invalidMetadata = listOf(
            SceneCommand.SetTransform(Matrix3x3F32(tx = Float.NaN)),
            SceneCommand.SetClip(deviceClip(Float.NaN, 0f, 4f, 3f, antiAlias = false)),
            SceneCommand.Annotation.of(RectF32(Float.NaN, 0f, 4f, 3f), "fixture", "w4a"),
        )

        invalidMetadata.forEach { metadata ->
            assertIs<GpuPlanSelection.InvalidScene>(select(listOf(metadata, solidRect())))
        }
    }

    @Test
    fun `wide integral I32 clip remains valid and scissors to the target`() {
        val graph = ready(solidRect(
            clip = deviceClip(-2_147_483_648f, 0f, 4f, 3f, antiAlias = false),
        ))

        assertEquals(RectI32(0, 0, 4, 3), assertIs<AnalyticRectDraw>(renderPass(graph).draws().single()).copyScissor())
    }

    @Test
    fun `W4a limits visual rectangles not metadata`() {
        val metadata = SceneCommand.Annotation.of(RectF32(0f, 0f, 4f, 3f), "fixture", "w4a")
        assertIs<GpuPlanSelection.Candidate>(select(List(512) { solidRect() } + metadata))
        assertIs<GpuPlanSelection.NotCandidate>(select(List(513) { solidRect() }))
    }

    @Test
    fun `ready W4a graph declares exact pool-backed resources`() {
        val graph = ready(twoFractionalRects())
        assertEquals(
            listOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging, PlanResourceRole.VertexData,
                PlanResourceRole.IndexData, PlanResourceRole.UniformData),
            graph.resources().map { it.role },
        )
        assertEquals(25_392, graph.peakFrameLocalBytes)
        assertEquals(
            PlanDrawDataResources(
                planResourceId(PlanResourceRole.VertexData, 0), planResourceId(PlanResourceRole.IndexData, 0),
                planResourceId(PlanResourceRole.UniformData, 0),
            ),
            renderPass(graph).drawDataResources,
        )
    }

    @Test
    fun `physical limits fail closed at their exact boundary`() {
        val candidate = selected(twoFractionalRects())
        assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(candidate, supportedCapabilities(), PlanBudget(25_392)))
        assertIs<RenderPlanResult.ResourceLimitExceeded>(compiler.plan(candidate, supportedCapabilities(), PlanBudget(25_391)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, supportedCapabilities(maxBufferSizeBytes = 16_383), PlanBudget(25_392)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, supportedCapabilities(
            supportedOperations = PlanOperationCapability.entries.toSet() - PlanOperationCapability.UniformBuffer,
        ), PlanBudget(25_392)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, supportedCapabilities(maxDynamicUniformBuffers = 0), PlanBudget(25_392)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, supportedCapabilities(maxTextureDimension2D = 3), PlanBudget(25_392)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, supportedCapabilities(copyAlignment = 24), PlanBudget(25_392)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(candidate, supportedCapabilities(
            policy = PlanBufferAllocationPolicy.of(12_288, 4_096, 4_096),
        ), PlanBudget(25_392)))
    }

    @Test
    fun `finite bounds outside I32 are selected then fail as a resource limit`() {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 3), ColorSpace.SRGB,
            listOf(solidRect(left = -2_147_483_648f, right = 2_147_483_648f)),
        )
        val target = RenderTargetDescriptor(scene.extent, scene.colorSpace)
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target)).candidate
        assertIs<RenderPlanResult.ResourceLimitExceeded>(compiler.plan(candidate, supportedCapabilities(), PlanBudget(1L shl 20)))
    }

    @Test
    fun `plan identity includes physical snapshot and excludes target label`() {
        val commands = twoFractionalRects()
        assertEquals(ready(commands, label = "first").id, ready(commands, label = "second").id)
        assertNotEquals(ready(commands).id, ready(commands, generation = 1).id)
        assertNotEquals(ready(commands).id, ready(commands, budget = PlanBudget(25_393)).id)
    }

    private fun ready(command: SceneCommand, width: Int = 4, height: Int = 3): RenderGraph = ready(listOf(command), width, height)

    private fun ready(
        commands: List<SceneCommand>,
        width: Int = 4,
        height: Int = 3,
        label: String? = null,
        generation: Long = 0,
        budget: PlanBudget = PlanBudget(1L shl 20),
    ): RenderGraph {
        val scene = SceneSnapshot.of(SceneExtent(width, height), ColorSpace.SRGB, commands)
        val selected = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace, label)),
        )
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(selected.candidate, supportedCapabilities(generation = generation), budget),
        ).plan
    }

    private fun renderPass(graph: RenderGraph): PlanPass.RenderPass =
        assertIs<PlanPass.RenderPass>(graph.passes().single { it is PlanPass.RenderPass })

    private fun solidRect(
        left: Float = 0.25f,
        top: Float = 0.5f,
        right: Float = 3.75f,
        bottom: Float = 2.25f,
        clip: ClipStackNode = ClipStackNode.Empty,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        coverage: CoverageRequest = CoverageRequest.ANTIALIASED,
    ): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0x80FF0000u)
        return SceneCommand.Draw(DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(left, top, right, bottom)),
            material = MaterialNode.Solid(color),
            coverage = coverage,
            clip = clip,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = transform,
            origin = DrawOrigin.RECT,
            paint = PaintNode(
                color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true,
            ),
        ))
    }

    private fun deviceClip(left: Float, top: Float, right: Float, bottom: Float, antiAlias: Boolean): ClipStackNode =
        ClipStackNode.DeviceRect.of(RectF32(left, top, right, bottom), antiAlias)

    private fun select(command: SceneCommand): GpuPlanSelection = select(listOf(command))

    private fun select(commands: List<SceneCommand>): GpuPlanSelection {
        val scene = SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, commands)
        return compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace))
    }

    private fun selected(commands: List<SceneCommand>): GpuPlanCandidate =
        assertIs<GpuPlanSelection.Candidate>(select(commands)).candidate

    private fun twoFractionalRects(): List<SceneCommand> = listOf(
        solidRect(0.25f, 0.5f, 2.75f, 2.25f), solidRect(1.25f, 0.5f, 3.75f, 2.25f),
    )

    private fun supportedCapabilities(
        generation: Long = 0,
        maxTextureDimension2D: Int = 64,
        maxBufferSizeBytes: Long = 1L shl 20,
        copyAlignment: Int = 256,
        maxDynamicUniformBuffers: Int = 1,
        supportedOperations: Set<PlanOperationCapability> = PlanOperationCapability.entries.toSet(),
        policy: PlanBufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = generation,
        maxTextureDimension2D = maxTextureDimension2D,
        maxBufferSizeBytes = maxBufferSizeBytes,
        copyBytesPerRowAlignment = copyAlignment,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
        supportedOperations = supportedOperations,
        bufferAllocationPolicy = policy,
    )
}
