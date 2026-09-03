package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.GradientStop
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
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class W3SolidRectPlanCompilerTest {
    private val compiler: GpuPlanCompiler = W3SolidRectPlanCompiler()

    @Test
    fun `two overlapping translucent solid rects produce a ready graph`() {
        val result = compiler.plan(
            scene = SceneSnapshot.of(
                SceneExtent(16, 8), ColorSpace.SRGB,
                listOf(solidRect(0f, 0f, 8f, 8f, 0x80FF0000u), solidRect(4f, 0f, 12f, 8f, 0x800000FFu)),
            ),
            target = target(16, 8),
            capabilities = supportedCapabilities(),
            budget = PlanBudget(1L shl 30),
        )

        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(result).plan
        assertEquals(2, graph.visualCommandCount)
        assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
    }

    @Test
    fun `all public SrcOver representations and pixel aligned clips compile`() {
        val blends = listOf(
            BlendNode.SrcOver,
            BlendNode.Mode(BlendMode.SRC_OVER),
            BlendNode.Paint(BlendMode.SRC_OVER, null),
        )
        blends.forEach { blend ->
            val graph = ready(sceneOf(solidRect(0f, 0f, 4f, 4f, 0xFF336699u, blend = blend, clip = deviceClip(1f, 1f, 3f, 3f))))
            assertEquals(RectI32(1, 1, 3, 3), firstDraw(graph).copyVisibleBounds())
        }
    }

    @Test
    fun `identity and scale translate transforms resolve all four corners including negative scales`() {
        val scaleTranslate = Matrix3x3F32(sx = -2f, sy = 2f, tx = 10f, ty = 1f)
        val graph = ready(SceneSnapshot.of(
            SceneExtent(12, 12), ColorSpace.SRGB,
            listOf(solidRect(1f, 2f, 3f, 4f, 0xFFFFFFFFu, transform = scaleTranslate)),
        ))

        assertEquals(RectI32(4, 5, 8, 9), firstDraw(graph).copyVisibleBounds())
    }

    @Test
    fun `empty and inverted source rectangles are gaps before negative scale normalization`() {
        val negativeScale = Matrix3x3F32(sx = -1f, sy = -1f, tx = 8f, ty = 8f)
        val empty = SceneSnapshot.of(
            SceneExtent(8, 8), ColorSpace.SRGB,
            listOf(solidRect(1f, 1f, 1f, 3f, 0xFFFFFFFFu, transform = negativeScale)),
        )
        val inverted = SceneSnapshot.of(
            SceneExtent(8, 8), ColorSpace.SRGB,
            listOf(solidRect(3f, 1f, 1f, 3f, 0xFFFFFFFFu, transform = negativeScale)),
        )

        assertGap(empty)
        assertGap(inverted)
    }

    @Test
    fun `provenance commands do not add draws`() {
        val scene = SceneSnapshot.of(
            SceneExtent(8, 8),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.SetTransform(Matrix3x3F32(sx = 2f, sy = 2f, tx = 0f, ty = 0f)),
                SceneCommand.SetClip(deviceClip(0f, 0f, 8f, 8f)),
                SceneCommand.Annotation.of(RectF32(0f, 0f, 8f, 8f), "origin", "fixture"),
                solidRect(0f, 0f, 4f, 4f, 0xFFFFFFFFu),
            ),
        )

        assertEquals(1, ready(scene).visualCommandCount)
    }

    @Test
    fun `DrawColor SrcOver covers target and converts straight sRGB to linear premultiplied`() {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 3), ColorSpace.SRGB,
            listOf(SceneCommand.DrawColor(ColorARGB.of(128, 128, 0, 0), BlendMode.SRC_OVER)),
        )

        val draw = firstDraw(ready(scene))
        assertEquals(RectI32(0, 0, 4, 3), draw.copyVisibleBounds())
        assertEquals(128f / 255f, draw.color.alpha, 0.00001f)
        assertEquals(0.10835f, draw.color.red, 0.0001f)
        assertEquals(0f, draw.color.green)
        assertEquals(0f, draw.color.blue)
    }

    @Test
    fun `hard edge and antialiased pixel aligned rectangles compile`() {
        listOf(CoverageRequest.HARD_EDGE, CoverageRequest.ANTIALIASED).forEach { coverage ->
            assertEquals(1, ready(sceneOf(solidRect(0f, 0f, 4f, 4f, 0xFF000000u, coverage = coverage))).visualCommandCount)
        }
        assertGap(sceneOf(solidRect(0.5f, 0f, 4f, 4f, 0xFF000000u, coverage = CoverageRequest.ANTIALIASED)))
    }

    @Test
    fun `coordinates beyond the Int range are geometry gaps`() {
        val result = compiler.plan(
            sceneOf(solidRect(0f, 0f, 2_147_483_648f, 2f, 0xFFFFFFFFu)),
            target(4, 4), supportedCapabilities(), PlanBudget(4096),
        )

        assertIs<RenderPlanResult.GapNotMigrated>(result)
        assertEquals("w3.geometry.not_pixel_aligned", diagnosticCode(result))
    }

    @Test
    fun `ordered large geometry and DeviceRect bounds are reduced by target intersection`() {
        val largeRight = 2_147_483_392f
        val geometry = ready(sceneOf(solidRect(-1_024f, 0f, largeRight, 4f, 0xFFFFFFFFu)))
        val clip = ready(sceneOf(solidRect(0f, 0f, 4f, 4f, 0xFFFFFFFFu, clip = deviceClip(-1_024f, 0f, largeRight, 4f))))

        assertEquals(RectI32(0, 0, 4, 4), firstDraw(geometry).copyVisibleBounds())
        assertEquals(RectI32(0, 0, 4, 4), firstDraw(clip).copyVisibleBounds())
    }

    @Test
    fun `non W3 draw axes remain gaps before promotion`() {
        val draw = solidDrawNode()
        val invalids = listOf(
            draw.copy(paint = w3Paint().copy(style = PaintStyleNode.STROKE)),
            draw.copy(paint = w3Paint().copy(shader = MaterialNode.Solid(ColorARGB.Blue))),
            draw.copy(paint = w3Paint().copy(blendMode = BlendMode.SRC)),
            draw.copy(paint = w3Paint().copy(blender = org.graphiks.kanvas.render.ir.BlenderNode.Mode(BlendMode.SRC_OVER))),
            draw.copy(resource = org.graphiks.kanvas.render.ir.ImageResourceSnapshot.rgba8(1, 1, byteArrayOf(0, 0, 0, 0), ColorSpace.SRGB)),
            draw.copy(operationBlendMode = BlendMode.SRC_OVER),
            draw.copy(effects = EffectStack.of(listOf(org.graphiks.kanvas.render.ir.ColorFilterNode.Luma))),
            draw.copy(material = MaterialNode.Transparent),
        )
        invalids.forEach { assertGap(sceneOf(SceneCommand.Draw(it))) }
    }

    @Test
    fun `each unsupported paint filter and effect remains a semantic gap`() {
        val draw = solidDrawNode()
        val invalids = listOf(
            draw.copy(paint = w3Paint().copy(colorFilter = org.graphiks.kanvas.render.ir.ColorFilterNode.Luma)),
            draw.copy(paint = w3Paint().copy(maskFilter = org.graphiks.kanvas.render.ir.MaskFilterNode.Blur(org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL, 1f))),
            draw.copy(paint = w3Paint().copy(pathEffect = org.graphiks.kanvas.render.ir.PathEffectNode.Corner(1f))),
            draw.copy(paint = w3Paint().copy(imageFilter = org.graphiks.kanvas.render.ir.ImageFilterNode.Blur(1f, 1f))),
            draw.copy(effects = EffectStack.of(listOf(org.graphiks.kanvas.render.ir.ColorFilterNode.Luma))),
        )

        invalids.forEach { drawNode ->
            assertEquals("w3.command.not_migrated", diagnosticCode(compiler.plan(sceneOf(SceneCommand.Draw(drawNode)), target(4, 4), supportedCapabilities(), PlanBudget(4096))))
        }
    }

    @Test
    fun `gradient and path families are semantic gaps`() {
        val gradient = solidDrawNode().copy(
            material = MaterialNode.LinearGradient.of(
                Point2F32(0f, 0f), Point2F32(4f, 0f),
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            ),
        )
        val path = solidDrawNode().copy(
            geometry = GeometryNode.Path(PathBuilder().addRect(RectF32(0f, 0f, 4f, 4f)).build()),
            origin = DrawOrigin.PATH,
        )

        listOf(gradient, path).forEach { draw ->
            assertEquals("w3.command.not_migrated", diagnosticCode(compiler.plan(sceneOf(SceneCommand.Draw(draw)), target(4, 4), supportedCapabilities(), PlanBudget(4096))))
        }
    }

    @Test
    fun `DrawColor outside W3 axes remains a gap`() {
        val cases = listOf(
            SceneCommand.DrawColor(ColorARGB.Red, BlendMode.SRC),
            SceneCommand.DrawColor(ColorARGB.Red, BlendMode.SRC_OVER, Matrix3x3F32(tx = 1f)),
            SceneCommand.DrawColor(ColorARGB.Red, BlendMode.SRC_OVER, clip = ClipStackNode.Operations.of(emptyList())),
        )
        cases.forEach { command -> assertGap(sceneOf(command)) }
    }

    @Test
    fun `semantic gaps cover unsupported scene forms and invisible frames`() {
        assertGap(SceneSnapshot.of(SceneExtent(4, 4), ColorSpace.SRGB, emptyList()))
        assertGap(sceneOf(SceneCommand.Clear(org.graphiks.math.color.ColorF32.Transparent)))
        assertGap(sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu, clip = deviceClip(3f, 3f, 4f, 4f))))
        assertGap(sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu, clip = ClipStackNode.Operations.of(emptyList()))))
        assertGap(SceneSnapshot.of(SceneExtent(4, 4), ColorSpace.LINEAR_SRGB, listOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu))))
    }

    @Test
    fun `fractional DeviceRect reports clip pixel alignment`() {
        val result = compiler.plan(
            sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu, clip = deviceClip(0.5f, 0f, 2f, 2f))),
            target(4, 4), supportedCapabilities(), PlanBudget(4096),
        )

        assertEquals("w3.clip.not_pixel_aligned", diagnosticCode(result))
    }

    @Test
    fun `scene target contradictions are invalid and recognized capability failures are terminal`() {
        val scene = sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu))
        val nonSrgbScene = SceneSnapshot.of(SceneExtent(4, 4), ColorSpace.LINEAR_SRGB, listOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu)))
        assertIs<RenderPlanResult.InvalidScene>(compiler.plan(scene, target(5, 4), supportedCapabilities(), PlanBudget(4096)))
        assertIs<RenderPlanResult.InvalidScene>(compiler.plan(scene, RenderTargetDescriptor(SceneExtent(4, 4), ColorSpace.LINEAR_SRGB), supportedCapabilities(), PlanBudget(4096)))
        assertIs<RenderPlanResult.InvalidScene>(compiler.plan(nonSrgbScene, target(4, 4), supportedCapabilities(), PlanBudget(4096)))
        assertIs<RenderPlanResult.GapNotMigrated>(compiler.plan(nonSrgbScene, RenderTargetDescriptor(SceneExtent(4, 4), ColorSpace.LINEAR_SRGB), supportedCapabilities(), PlanBudget(4096)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(scene, target(4, 4), supportedCapabilities(maxTextureDimension2D = 3), PlanBudget(4096)))
        assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(scene, target(4, 4), supportedCapabilities(formats = emptySet()), PlanBudget(4096)))
    }

    @Test
    fun `staging buffer capability is checked after semantic promotion`() {
        val result = compiler.plan(
            sceneOf(solidRect(0f, 0f, 4f, 4f, 0xFFFFFFFFu)), target(4, 4),
            supportedCapabilities(maxBufferSizeBytes = 1_023), PlanBudget(4096),
        )

        assertIs<RenderPlanResult.GapOnPromotedScope>(result)
        assertEquals("w3.capability.buffer_size", diagnosticCode(result))
    }

    @Test
    fun `non finite geometry and transforms are invalid scenes`() {
        val nonFiniteGeometry = sceneOf(solidRect(Float.NaN, 0f, 2f, 2f, 0xFFFFFFFFu))
        val nonFiniteTransform = sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu, transform = Matrix3x3F32(tx = Float.POSITIVE_INFINITY)))

        assertIs<RenderPlanResult.InvalidScene>(compiler.plan(nonFiniteGeometry, target(4, 4), supportedCapabilities(), PlanBudget(4096)))
        assertIs<RenderPlanResult.InvalidScene>(compiler.plan(nonFiniteTransform, target(4, 4), supportedCapabilities(), PlanBudget(4096)))
    }

    @Test
    fun `budget excess is resource limit exceeded`() {
        val result = compiler.plan(sceneOf(solidRect(0f, 0f, 4f, 4f, 0xFFFFFFFFu)), target(4, 4), supportedCapabilities(), PlanBudget(1))

        assertIs<RenderPlanResult.ResourceLimitExceeded>(result)
    }

    @Test
    fun `plan identity includes semantic inputs but excludes target label`() {
        val scene = sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu))
        val base = ready(scene, target = target(4, 4, "first"))
        assertEquals(base.id, ready(scene, target = target(4, 4, "second")).id)
        assertNotEquals(base.id, ready(sceneOf(solidRect(0f, 0f, 3f, 2f, 0xFFFFFFFFu))).id)
        assertNotEquals(base.id, ready(SceneSnapshot.of(SceneExtent(5, 4), ColorSpace.SRGB, listOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu)))).id)
        assertNotEquals(base.id, ready(scene, capabilities = supportedCapabilities(generation = 1)).id)
        assertNotEquals(base.id, ready(scene, budget = PlanBudget(4097)).id)
    }

    @Test
    fun `resources passes and exposed geometry preserve independent identities and snapshots`() {
        val graph = ready(sceneOf(solidRect(0f, 0f, 2f, 2f, 0xFFFFFFFFu)))
        assertEquals(2, graph.resources().map { it.id }.distinct().size)
        assertEquals(2, graph.passes().map { it.id }.distinct().size)
        val draw = firstDraw(graph)
        val leaked = draw.copyVisibleBounds()
        leaked.left = 99
        assertEquals(RectI32(0, 0, 2, 2), firstDraw(graph).copyVisibleBounds())
        assertTrue(graph.dependencies().isNotEmpty())
    }

    @Test
    fun `ready W3 graph closes resource pass lifetime and memory decisions`() {
        val scene = sceneOf(solidRect(0f, 0f, 4f, 4f, 0x80FF0000u, coverage = CoverageRequest.ANTIALIASED))
        val graph = ready(scene)
        val secondGraph = ready(scene)
        val target = graph.resources().single { it.role == PlanResourceRole.LogicalTarget }
        val staging = graph.resources().single { it.role == PlanResourceRole.ReadbackStaging }
        val render = assertIs<PlanPass.RenderPass>(graph.passes().single { it is PlanPass.RenderPass })
        val readback = assertIs<PlanPass.ReadbackPass>(graph.passes().single { it is PlanPass.ReadbackPass })

        assertEquals(graph.id, secondGraph.id)
        assertEquals(SizeI32(4, 4), graph.targetExtent)
        assertEquals(PlanResourceRole.LogicalTarget, target.role)
        assertEquals(0, target.ordinal)
        assertEquals(PlanResourceKind.Texture2D, target.kind)
        assertEquals(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, target.format)
        assertEquals(64, target.byteSize)
        assertEquals(SizeI32(4, 4), target.copyExtent())
        assertEquals(setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), target.usages())
        assertEquals(PlanResourceLifetime.FrameLocal, target.lifetime)
        assertEquals(0, target.firstPassIndex)
        assertEquals(2, target.lastPassIndexExclusive)
        assertEquals(PlanResourceRole.ReadbackStaging, staging.role)
        assertEquals(0, staging.ordinal)
        assertEquals(PlanResourceKind.Buffer, staging.kind)
        assertEquals(null, staging.format)
        assertEquals(null, staging.copyExtent())
        assertEquals(1_024, staging.byteSize)
        assertEquals(setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), staging.usages())
        assertEquals(1, staging.firstPassIndex)
        assertEquals(2, staging.lastPassIndexExclusive)
        assertEquals(PlanPassRole.MainRender, render.role)
        assertEquals(PlanPassRole.Readback, readback.role)
        assertEquals(0, render.ordinal)
        assertEquals(0, readback.ordinal)
        assertEquals(AttachmentLoadPlan.ClearTransparent, render.load)
        assertEquals(AttachmentStorePlan.Store, render.store)
        assertEquals(CoveragePlan.FullOrScissor, render.draws().single().coverage)
        assertEquals(SamplePlan.SingleSample, render.draws().single().sample)
        assertEquals(BlendPlan.SrcOver, render.draws().single().blend)
        assertEquals(256, readback.bytesPerRow)
        assertEquals(listOf(PlanPassDependency(render.id, readback.id)), graph.dependencies())
        assertEquals(1_088, graph.peakFrameLocalBytes)
    }

    private fun ready(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor = target(scene.extent.width, scene.extent.height),
        capabilities: PlanCapabilitySnapshot = supportedCapabilities(),
        budget: PlanBudget = PlanBudget(4096),
    ): RenderGraph = assertIs<RenderPlanResult.Ready<RenderGraph>>(compiler.plan(scene, target, capabilities, budget)).plan

    private fun assertGap(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor = RenderTargetDescriptor(scene.extent, scene.colorSpace),
    ) {
        assertIs<RenderPlanResult.GapNotMigrated>(compiler.plan(scene, target, supportedCapabilities(), PlanBudget(4096)))
    }

    private fun firstDraw(graph: RenderGraph): SolidRectDraw = assertIs<PlanPass.RenderPass>(graph.passes().first())
        .draws()
        .let { draws -> assertIs<SolidRectDraw>(draws.first()) }

    private fun sceneOf(vararg commands: SceneCommand): SceneSnapshot =
        SceneSnapshot.of(SceneExtent(4, 4), ColorSpace.SRGB, commands.toList())

    private fun target(width: Int, height: Int, label: String? = null): RenderTargetDescriptor =
        RenderTargetDescriptor(SceneExtent(width, height), ColorSpace.SRGB, label)

    private fun supportedCapabilities(
        generation: Long = 0,
        maxTextureDimension2D: Int = 64,
        maxBufferSizeBytes: Long = 1L shl 20,
        formats: Set<PlanLogicalColorFormat> = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(generation, maxTextureDimension2D, maxBufferSizeBytes, 256, formats)

    private fun diagnosticCode(result: RenderPlanResult<*>): String = when (result) {
        is RenderPlanResult.GapNotMigrated -> result.diagnostics.single().code.value
        is RenderPlanResult.GapOnPromotedScope -> result.diagnostics.single().code.value
        is RenderPlanResult.InvalidScene -> result.diagnostics.single().code.value
        is RenderPlanResult.ResourceLimitExceeded -> result.diagnostics.single().code.value
        is RenderPlanResult.Ready -> error("Ready graph has no refusal diagnostic")
    }

    private fun solidRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: UInt,
        blend: BlendNode = BlendNode.SrcOver,
        clip: ClipStackNode = ClipStackNode.Empty,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        coverage: CoverageRequest = CoverageRequest.HARD_EDGE,
    ): SceneCommand.Draw = SceneCommand.Draw(solidDrawNode(left, top, right, bottom, color, blend, clip, transform, coverage))

    private fun solidDrawNode(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 4f,
        bottom: Float = 4f,
        color: UInt = 0xFFFFFFFFu,
        blend: BlendNode = BlendNode.SrcOver,
        clip: ClipStackNode = ClipStackNode.Empty,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        coverage: CoverageRequest = CoverageRequest.HARD_EDGE,
    ): DrawNode = DrawNode(
        geometry = GeometryNode.Rect.of(RectF32(left, top, right, bottom)),
        material = MaterialNode.Solid(ColorARGB.fromPackedUInt(color)),
        coverage = coverage,
        clip = clip,
        blend = blend,
        effects = EffectStack.Empty,
        transform = transform,
        origin = DrawOrigin.RECT,
        paint = w3Paint(ColorARGB.fromPackedUInt(color)),
    )

    private fun w3Paint(color: ColorARGB = ColorARGB.White): PaintNode = PaintNode(
        color = color,
        shader = null,
        blendMode = BlendMode.SRC_OVER,
        blender = null,
        colorFilter = null,
        maskFilter = null,
        pathEffect = null,
        imageFilter = null,
        style = PaintStyleNode.FILL,
        strokeWidth = 0f,
        strokeCap = StrokeCapNode.BUTT,
        strokeJoin = StrokeJoinNode.MITER,
        strokeMiter = 4f,
        antiAlias = true,
    )

    private fun deviceClip(left: Float, top: Float, right: Float, bottom: Float): ClipStackNode.DeviceRect =
        ClipStackNode.DeviceRect.of(RectF32(left, top, right, bottom))
}
