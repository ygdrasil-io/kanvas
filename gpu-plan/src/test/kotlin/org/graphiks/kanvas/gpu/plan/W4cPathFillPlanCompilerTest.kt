package org.graphiks.kanvas.gpu.plan

import kotlin.math.cos
import kotlin.math.sin
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
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.ImageResourceSnapshot
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
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class W4cPathFillPlanCompilerTest {
    private val compiler = W4cPathFillPlanCompiler()

    @Test
    fun directTriangleAdmissionSealsOneDirectRenderPass() {
        val graph = ready(listOf(triangle()))
        val passes = graph.passes()
        val direct = assertIs<PlanPass.RenderPass>(passes[0])
        val draw = assertIs<PathFillDraw>(direct.draws().single())

        assertEquals(W4cPathFillPlanCompiler.CAPABILITY_ID, graph.capabilityId)
        assertEquals(PathFillStrategy.DirectTriangle, draw.strategy)
        assertEquals(AttachmentLoadPlan.ClearTransparent, direct.load)
        assertEquals(AttachmentStorePlan.Store, direct.store)
        assertIs<PlanPass.ReadbackPass>(passes[1])
        assertEquals(
            listOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.ReadbackStaging,
                PlanResourceRole.VertexData,
                PlanResourceRole.IndexData,
                PlanResourceRole.UniformData,
            ),
            graph.resources().map { it.role },
        )
    }

    @Test
    fun concaveAndEvenOddPathsSealAdjacentStencilGroupsAndFrameLifetimes() {
        val graph = ready(listOf(triangle(), concave(), evenOddHole()))
        val passes = graph.passes()
        val firstDirect = assertIs<PlanPass.RenderPass>(passes[0])
        val firstProducer = assertIs<PlanPass.StencilProducer>(passes[1])
        val firstCover = assertIs<PlanPass.StencilCover>(passes[2])
        val secondProducer = assertIs<PlanPass.StencilProducer>(passes[3])
        val secondCover = assertIs<PlanPass.StencilCover>(passes[4])
        val readback = assertIs<PlanPass.ReadbackPass>(passes[5])

        assertEquals(PathFillStrategy.DirectTriangle, assertIs<PathFillDraw>(firstDirect.draws().single()).strategy)
        assertEquals(PathFillStrategy.StencilCover, firstProducer.draw.strategy)
        assertTrue(firstProducer.draw === firstCover.draw)
        assertEquals(PlanAtomicGroupId("w4c:1"), firstProducer.atomicGroup)
        assertEquals(firstProducer.atomicGroup, firstCover.atomicGroup)
        assertEquals(AttachmentLoadPlan.Load, firstProducer.load)
        assertEquals(AttachmentLoadPlan.Load, firstCover.load)
        assertEquals(PlanDepthStencilLoadStore.ClearZeroStore, firstProducer.depthStencilLoadStore)
        assertEquals(PlanDepthStencilLoadStore.LoadStoreTestReset, firstCover.depthStencilLoadStore)
        assertEquals(PlanAtomicGroupId("w4c:2"), secondProducer.atomicGroup)
        assertEquals(secondProducer.atomicGroup, secondCover.atomicGroup)
        passes.zipWithNext().forEach { (before, after) ->
            assertTrue(PlanPassDependency(before.id, after.id) in graph.dependencies())
        }
        assertEquals(readback.id, graph.passes().last().id)

        val resources = graph.resources().associateBy { it.role }
        assertEquals(0, resources.getValue(PlanResourceRole.LogicalTarget).firstPassIndex)
        assertEquals(6, resources.getValue(PlanResourceRole.LogicalTarget).lastPassIndexExclusive)
        assertEquals(5, resources.getValue(PlanResourceRole.ReadbackStaging).firstPassIndex)
        assertEquals(6, resources.getValue(PlanResourceRole.ReadbackStaging).lastPassIndexExclusive)
        listOf(PlanResourceRole.VertexData, PlanResourceRole.IndexData, PlanResourceRole.UniformData).forEach { role ->
            assertEquals(0, resources.getValue(role).firstPassIndex)
            assertEquals(6, resources.getValue(role).lastPassIndexExclusive)
        }
        assertEquals(1, resources.getValue(PlanResourceRole.DepthStencil).firstPassIndex)
        assertEquals(6, resources.getValue(PlanResourceRole.DepthStencil).lastPassIndexExclusive)
        assertEquals(
            graph.resources().fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) },
            graph.peakFrameLocalBytes,
        )
    }

    @Test
    fun selectionAdmitsW4cPathsMetadataAndExactDrawBoundaries() {
        val metadataAccepted = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.SetTransform(Matrix3x3F32.Identity),
                SceneCommand.SetClip(ClipStackNode.Empty),
                SceneCommand.Annotation.of(RectF32(0f, 0f, 4f, 4f), "source", "test"),
                triangle(
                    transform = Matrix3x3F32(sx = -1f, sy = 1f, tx = 4f),
                    clip = ClipStackNode.DeviceRect.of(RectF32(1f, 0f, 4f, 4f), antiAlias = false),
                ),
            ),
        )
        val one = sceneOf(listOf(triangle()))
        val many = sceneOf(List(512) { triangle() })
        val tooMany = sceneOf(List(513) { triangle() })

        assertIs<GpuPlanSelection.Candidate>(compiler.select(metadataAccepted, target(metadataAccepted)))
        assertIs<GpuPlanSelection.Candidate>(compiler.select(one, target(one)))
        assertIs<GpuPlanSelection.Candidate>(compiler.select(many, target(many)))
        assertIs<GpuPlanSelection.Candidate>(select(concave()))
        assertIs<GpuPlanSelection.Candidate>(select(evenOddHole()))
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(tooMany, target(tooMany)))
    }

    @Test
    fun selectionRejectsAnyOutOfScopeDrawAtomically() {
        val base = triangle().node
        val rejected = listOf(
            SceneCommand.Draw(base.copy(coverage = CoverageRequest.ANTIALIASED)),
            SceneCommand.Draw(base.copy(origin = DrawOrigin.TEXT_EXPANDED_PATH)),
            SceneCommand.Draw(base.copy(geometry = GeometryNode.Path(inverseTriangle()))),
            SceneCommand.Draw(base.copy(paint = paint(style = PaintStyleNode.STROKE, strokeWidth = 0f))),
            SceneCommand.Draw(base.copy(transform = Matrix3x3F32.rotation(0.25f))),
            SceneCommand.Draw(base.copy(clip = ClipStackNode.DeviceRect.of(RectF32(0f, 0f, 4f, 4f), antiAlias = true))),
            SceneCommand.Draw(base.copy(clip = ClipStackNode.DeviceRect.of(RectF32(0f, 0f, 0f, 4f), antiAlias = false))),
            SceneCommand.Draw(base.copy(clip = ClipStackNode.Operations.of(
                listOf(ClipEntry(GeometryNode.Path(trianglePath()), ClipOperation.INTERSECT)),
            ))),
            SceneCommand.Draw(base.copy(material = MaterialNode.Transparent)),
            SceneCommand.Draw(base.copy(blend = BlendNode.Mode(BlendMode.SRC))),
        )

        rejected.forEach { command -> assertIs<GpuPlanSelection.NotCandidate>(select(command)) }
        assertIs<GpuPlanSelection.NotCandidate>(select(listOf(triangle(), rectDraw())))
        val displayP3 = sceneOf(listOf(triangle()), ColorSpace.DISPLAY_P3)
        assertIs<GpuPlanSelection.NotCandidate>(compiler.select(displayP3, target(displayP3)))
        assertIs<GpuPlanSelection.NotCandidate>(select(SceneCommand.Draw(
            base.copy(geometry = GeometryNode.Path(PathBuilder().build())),
        )))
    }

    @Test
    fun selectionClassifiesStructuralPathContradictionsBeforeW4cAdmission() {
        val base = triangle().node
        val image = ImageResourceSnapshot.rgba8(1, 1, ByteArray(4), ColorSpace.SRGB)
        val contradictory = listOf(
            base.copy(paint = null),
            base.copy(origin = DrawOrigin.RECT),
            base.copy(geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 4f, 4f))),
            base.copy(resource = image),
            base.copy(operationBlendMode = BlendMode.SRC_OVER),
        )

        contradictory.forEach { node ->
            assertIs<GpuPlanSelection.InvalidScene>(select(SceneCommand.Draw(node)))
        }
        assertIs<GpuPlanSelection.NotCandidate>(
            select(SceneCommand.Draw(base.copy(origin = DrawOrigin.TEXT_EXPANDED_PATH))),
        )
    }

    @Test
    fun selectionClassifiesNonFiniteInputsBeforeScopeGaps() {
        val base = triangle().node
        val nonFinitePath = SceneCommand.Draw(base.copy(
            geometry = GeometryNode.Path(
                PathBuilder().moveTo(Float.NaN, 0f).lineTo(2f, 0f).lineTo(0f, 2f).close().build(),
            ),
        ))
        val nonFiniteTransform = SceneCommand.Draw(base.copy(transform = Matrix3x3F32(tx = Float.NaN)))
        val nonFiniteClip = SceneCommand.Draw(base.copy(
            clip = ClipStackNode.DeviceRect.of(RectF32(Float.NaN, 0f, 4f, 4f), antiAlias = false),
        ))

        assertIs<GpuPlanSelection.InvalidScene>(select(nonFinitePath))
        assertIs<GpuPlanSelection.InvalidScene>(select(nonFiniteTransform))
        assertIs<GpuPlanSelection.InvalidScene>(select(nonFiniteClip))
    }

    @Test
    fun selectionPropagatesGeometryLimitsWithoutMakingPartialCandidates() {
        val winding256 = SceneCommand.Draw(pathNode(regularPolygon(256)))
        val overflow = SceneCommand.Draw(pathNode(
            PathBuilder()
                .moveTo(0f, 0f)
                .lineTo(Float.MAX_VALUE, 0f)
                .lineTo(0f, 1f)
                .close()
                .build(),
        ))

        assertEquals(
            "w4c.path.resource_limit",
            assertIs<GpuPlanSelection.ResourceLimitExceeded>(select(winding256)).diagnostics().single().code.value,
        )
        assertIs<GpuPlanSelection.ResourceLimitExceeded>(select(overflow))
    }

    @Test
    fun selectionAdmitsWindingStencilAtIts255EdgeBoundary() {
        val winding255 = SceneCommand.Draw(pathNode(regularPolygon(255)))

        val graph = ready(listOf(winding255))

        assertEquals(
            PathFillStrategy.StencilCover,
            assertIs<PlanPass.StencilProducer>(graph.passes().first()).draw.strategy,
        )
    }

    @Test
    fun selectionAppliesTheAttemptedEdgeLimitAcrossTheWholeFrame() {
        val evenOdd513 = pathNode(regularPolygon(513, FillRule.EVEN_ODD))
        val scene = sceneOf(List(512) { SceneCommand.Draw(evenOdd513) })

        val result = compiler.select(scene, target(scene))

        assertEquals(
            "w4c.path.resource_limit",
            assertIs<GpuPlanSelection.ResourceLimitExceeded>(result).diagnostics().single().code.value,
        )
    }

    @Test
    fun planningKeepsHostOverflowTerminalAndCapabilityAbsencePromoted() {
        val manyTriangles = sceneOf(List(512) { triangle() })
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(manyTriangles, target(manyTriangles)),
        ).candidate
        val hostOverflow = compiler.plan(
            candidate,
            capabilities(
                uniformAlignment = 1 shl 22,
                maxBufferSizeBytes = Long.MAX_VALUE,
                policy = PlanBufferAllocationPolicy.of(1L shl 22, 1L shl 22, 1L shl 22),
            ),
            PlanBudget(Long.MAX_VALUE),
        )
        val oneTriangle = sceneOf(listOf(triangle()))
        val directCandidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(oneTriangle, target(oneTriangle)),
        ).candidate
        val absentRenderPass = compiler.plan(
            directCandidate,
            capabilities(operations = PlanOperationCapability.entries.toSet() - PlanOperationCapability.RenderPass),
            PlanBudget(1L shl 20),
        )

        assertIs<RenderPlanResult.ResourceLimitExceeded>(hostOverflow)
        assertEquals(
            "w4c.capability.operation",
            assertIs<RenderPlanResult.GapOnPromotedScope>(absentRenderPass).diagnostics.single().code.value,
        )
    }

    @Test
    fun planningAcceptsTheExactMaxBufferSizeAndPromotesOneByteLess() {
        val scene = sceneOf(listOf(triangle()))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val policy = PlanBufferAllocationPolicy.of(64, 64, 512)

        val exact = compiler.plan(
            candidate,
            capabilities(maxBufferSizeBytes = 1_024, policy = policy),
            PlanBudget(4_096),
        )
        val below = compiler.plan(
            candidate,
            capabilities(maxBufferSizeBytes = 1_023, policy = policy),
            PlanBudget(4_096),
        )

        assertIs<RenderPlanResult.Ready<RenderGraph>>(exact)
        assertEquals(
            "w4c.capability.buffer_size",
            assertIs<RenderPlanResult.GapOnPromotedScope>(below).diagnostics.single().code.value,
        )
    }

    @Test
    fun planningPromotesInvalidAllocationAlignments() {
        val scene = sceneOf(listOf(triangle()))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val invalidCapabilities = listOf(
            capabilities(copyBytesPerRowAlignment = 3),
            capabilities(uniformAlignment = 3),
            capabilities(policy = PlanBufferAllocationPolicy.of(3, 4, 4)),
        )

        invalidCapabilities.forEach { capabilities ->
            val result = compiler.plan(candidate, capabilities, PlanBudget(1L shl 20))
            assertEquals(
                "w4c.capability.allocation_policy",
                assertIs<RenderPlanResult.GapOnPromotedScope>(result).diagnostics.single().code.value,
            )
        }
    }

    @Test
    fun planningPromotesMissingD24S8ForDirectOnlyPaths() {
        val scene = sceneOf(listOf(triangle()))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate

        val result = compiler.plan(
            candidate,
            capabilities(depthStencilFormats = emptySet()),
            PlanBudget(1L shl 20),
        )

        assertEquals(
            "w4c.capability.depth_stencil_format",
            assertIs<RenderPlanResult.GapOnPromotedScope>(result).diagnostics.single().code.value,
        )
    }

    @Test
    fun planningPromotesMissingStencilOperationsForDirectOnlyPaths() {
        val scene = sceneOf(listOf(triangle()))
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate

        listOf(
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        ).forEach { missing ->
            val result = compiler.plan(
                candidate,
                capabilities(operations = PlanOperationCapability.entries.toSet() - missing),
                PlanBudget(1L shl 20),
            )

            assertEquals(
                "w4c.capability.operation",
                assertIs<RenderPlanResult.GapOnPromotedScope>(result).diagnostics.single().code.value,
            )
        }
    }

    @Test
    fun planningRejectsCounterfeitAndForeignCandidates() {
        val scene = sceneOf(listOf(triangle()))
        val selected = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        val counterfeit = object : GpuPlanCandidate {
            override val capabilityId: String = selected.capabilityId
            override val sceneCanonicalId = selected.sceneCanonicalId
            override val target: RenderTargetDescriptor = selected.target
        }
        val otherCompiler = W4cPathFillPlanCompiler()

        listOf(
            compiler.plan(counterfeit, capabilities(), PlanBudget(1L shl 20)),
            otherCompiler.plan(selected, capabilities(), PlanBudget(1L shl 20)),
        ).forEach { result ->
            assertEquals(
                "gpu-plan.selection.invalid-candidate",
                assertIs<RenderPlanResult.InvalidScene>(result).diagnostics.single().code.value,
            )
        }
    }

    @Test
    fun `plan identity changes when only sample or resolve support changes`() {
        val commands = listOf(triangle())
        val baseCapabilities = capabilities()

        assertNotEquals(
            ready(commands, capabilities = baseCapabilities).id,
            ready(commands, capabilities = withAdditionalFourSampleColorSupport(baseCapabilities)).id,
        )
        assertNotEquals(
            ready(commands, capabilities = baseCapabilities).id,
            ready(commands, capabilities = capabilities(textureResolveSupports = setOf(colorResolveSupport()))).id,
        )
    }

    private fun ready(
        commands: Collection<SceneCommand>,
        capabilities: PlanCapabilitySnapshot = capabilities(),
    ): RenderGraph {
        val scene = sceneOf(commands)
        val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target(scene))).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities, PlanBudget(1L shl 20)),
        ).plan
    }

    private fun select(command: SceneCommand.Draw): GpuPlanSelection = select(listOf(command))

    private fun select(commands: Collection<SceneCommand>): GpuPlanSelection {
        val scene = sceneOf(commands)
        return compiler.select(scene, target(scene))
    }

    private fun sceneOf(
        commands: Collection<SceneCommand>,
        colorSpace: ColorSpace = ColorSpace.SRGB,
    ): SceneSnapshot = SceneSnapshot.of(SceneExtent(4, 4), colorSpace, commands)

    private fun target(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun triangle(
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        clip: ClipStackNode = ClipStackNode.Empty,
    ): SceneCommand.Draw = SceneCommand.Draw(pathNode(trianglePath(), transform, clip))

    private fun concave(): SceneCommand.Draw = SceneCommand.Draw(pathNode(
        PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(4f, 3f)
            .lineTo(2f, 1f)
            .lineTo(0f, 3f)
            .close()
            .build(),
    ))

    private fun evenOddHole(): SceneCommand.Draw = SceneCommand.Draw(pathNode(
        PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32(0f, 0f, 4f, 4f))
            .addRect(RectF32(1f, 1f, 3f, 3f))
            .build(),
    ))

    private fun inverseTriangle(): PathF32 = PathBuilder(FillRule.INVERSE_WINDING)
        .moveTo(0f, 0f)
        .lineTo(4f, 0f)
        .lineTo(0f, 3f)
        .close()
        .build()

    private fun trianglePath(): PathF32 = PathBuilder()
        .moveTo(0f, 0f)
        .lineTo(4f, 0f)
        .lineTo(0f, 3f)
        .close()
        .build()

    private fun regularPolygon(
        sideCount: Int,
        fillRule: FillRule = FillRule.WINDING,
    ): PathF32 {
        val builder = PathBuilder(fillRule)
        repeat(sideCount) { index ->
            val angle = index * 2.0 * Math.PI / sideCount
            val x = (2.0 + cos(angle)).toFloat()
            val y = (2.0 + sin(angle)).toFloat()
            if (index == 0) builder.moveTo(x, y) else builder.lineTo(x, y)
        }
        return builder.close().build()
    }

    private fun pathNode(
        path: PathF32,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        clip: ClipStackNode = ClipStackNode.Empty,
    ): DrawNode = DrawNode(
        geometry = GeometryNode.Path(path),
        material = MaterialNode.Solid(COLOR),
        coverage = CoverageRequest.HARD_EDGE,
        clip = clip,
        blend = BlendNode.SrcOver,
        effects = EffectStack.Empty,
        transform = transform,
        origin = DrawOrigin.PATH,
        paint = paint(),
    )

    private fun rectDraw(): SceneCommand.Draw = SceneCommand.Draw(
        DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 4f, 4f)),
            material = MaterialNode.Solid(COLOR),
            coverage = CoverageRequest.HARD_EDGE,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RECT,
            paint = paint(),
        ),
    )

    private fun paint(
        style: PaintStyleNode = PaintStyleNode.FILL,
        strokeWidth: Float = 0f,
    ): PaintNode = PaintNode(
        COLOR,
        null,
        BlendMode.SRC_OVER,
        null,
        null,
        null,
        null,
        null,
        style,
        strokeWidth,
        StrokeCapNode.BUTT,
        StrokeJoinNode.MITER,
        4f,
        false,
    )

    private fun capabilities(
        uniformAlignment: Int = 256,
        maxBufferSizeBytes: Long = 1L shl 20,
        policy: PlanBufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        operations: Set<PlanOperationCapability> = PlanOperationCapability.entries.toSet(),
        copyBytesPerRowAlignment: Int = 256,
        depthStencilFormats: Set<PlanDepthStencilFormat> = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        textureResolveSupports: Set<PlanTextureResolveSupport> = emptySet(),
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = maxBufferSizeBytes,
        copyBytesPerRowAlignment = copyBytesPerRowAlignment,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = uniformAlignment,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = operations,
        bufferAllocationPolicy = policy,
        supportedDepthStencilFormats = depthStencilFormats,
        supportedTextureResolveSupports = textureResolveSupports,
    )

    private fun colorResolveSupport(): PlanTextureResolveSupport = PlanTextureResolveSupport.of(
        PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        4,
        1,
    )

    private fun withAdditionalFourSampleColorSupport(base: PlanCapabilitySnapshot): PlanCapabilitySnapshot =
        PlanCapabilitySnapshot.of(
            deviceGeneration = base.deviceGeneration,
            maxTextureDimension2D = base.maxTextureDimension2D,
            maxBufferSizeBytes = base.maxBufferSizeBytes,
            copyBytesPerRowAlignment = base.copyBytesPerRowAlignment,
            supportedFormats = base.supportedFormats(),
            minUniformBufferOffsetAlignment = base.minUniformBufferOffsetAlignment,
            maxDynamicUniformBuffersPerPipelineLayout = base.maxDynamicUniformBuffersPerPipelineLayout,
            supportedOperations = base.supportedOperations(),
            bufferAllocationPolicy = base.bufferAllocationPolicy,
            supportedDepthStencilFormats = base.supportedDepthStencilFormats(),
            supportedTextureSampleSupports = base.supportedTextureSampleSupports() + PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
            supportedTextureResolveSupports = base.supportedTextureResolveSupports(),
        )

    private companion object {
        val COLOR: ColorARGB = ColorARGB.fromPackedUInt(0x80FF0000u)
    }
}
