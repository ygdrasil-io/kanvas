package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanTextureResolveSupport
import org.graphiks.kanvas.gpu.plan.PlanTextureSampleSupport
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.BinaryMaskedPathDraw
import org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4dBinaryMaskCoverGeometry
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4dBinaryMaskFetch
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4dBinaryMaskPipelineIntent
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
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
import org.graphiks.math.matrix.Matrix3x3F32

class GpuPlanTaskListLowererW4dGeneralTest {
    @Test
    fun `lowers a four-sample general AA path frame to prepared tasks`() {
        val graph = aaGraph()

        assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = rendererCapabilities(),
                    deviceGeneration = GPUDeviceGenerationID(7),
                    currentBudget = graph.budget,
                    frameId = GPUFrameID(8),
                    recordingId = GPURecordingID("w4d-general-red"),
                ),
            ),
        )
    }

    @Test
    fun `rejects a rebuilt structurally valid general graph without compiler witness`() {
        val compiled = aaGraph()
        val rebuilt = RenderGraph.of(
            id = PlanId("forged-w4d-general-graph"),
            capabilityId = compiled.capabilityId,
            targetExtent = compiled.targetExtent,
            colorFormat = compiled.colorFormat,
            capabilities = compiled.capabilities,
            budget = compiled.budget,
            visualCommandCount = compiled.visualCommandCount,
            resources = compiled.resources(),
            passes = compiled.passes(),
            dependencies = compiled.dependencies(),
            peakFrameLocalBytes = compiled.peakFrameLocalBytes,
        )

        assertIs<GpuPlanLoweringResult.InvalidPlan>(lower(rebuilt))
    }

    @Test
    fun `public graph contracts reject forged W4d resolve sample resource and atomic facts`() {
        val graph = graphOf(pathDraw(), pathDraw(antiAlias = false))
        val passes = graph.passes()
        val binary = passes.filterIsInstance<PlanPass.PathRenderPass>().single { pass ->
            pass.phase == org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeBinaryColorCover
        }
        val firstAaColor = passes.filterIsInstance<PlanPass.PathRenderPass>().first { pass ->
            pass.draw.sample == org.graphiks.kanvas.gpu.plan.SamplePlan.Multisample4 && pass !== binary
        }
        val binaryDraw = assertIs<BinaryMaskedPathDraw>(binary.draw)
        val logicalTarget = graph.resources().single { resource ->
            resource.role == PlanResourceRole.LogicalTarget
        }
        val mask = graph.resources().single { resource ->
            resource.role == PlanResourceRole.PathHardEdgeMask
        }

        val noResolve = copyPathPass(binary, resolveTarget = null)
        val earlyResolve = copyPathPass(firstAaColor, resolveTarget = binary.resolveTarget)
        val wrongSample = copyPathPass(binary, draw = binaryDraw.producer)
        val wrongMask = copyPathPass(
            binary,
            draw = BinaryMaskedPathDraw.of(binaryDraw.producer, logicalTarget.id),
        )
        val wrongAtomicGroup = copyPathPass(binary, atomicGroup = null)
        val wrongMaskFormatAndUsage = PlanResource.of(
            role = mask.role,
            ordinal = mask.ordinal,
            kind = PlanResourceKind.Texture2D,
            format = PlanTextureFormat.Color(graph.colorFormat),
            extent = requireNotNull(mask.copyExtent()),
            byteSize = mask.byteSize,
            usages = setOf(PlanResourceUsage.RenderAttachment),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = mask.firstPassIndex,
            lastPassIndexExclusive = mask.lastPassIndexExclusive,
            sampleCountI32 = mask.sampleCountI32,
        )

        listOf(
            { rebuild(graph, replacePasses = mapOf(binary.id to noResolve)) },
            {
                rebuild(
                    graph,
                    replacePasses = mapOf(firstAaColor.id to earlyResolve, binary.id to noResolve),
                )
            },
            { rebuild(graph, replacePasses = mapOf(binary.id to wrongSample)) },
            { rebuild(graph, replacePasses = mapOf(binary.id to wrongMask)) },
            { rebuild(graph, replacePasses = mapOf(binary.id to wrongAtomicGroup)) },
            { rebuild(graph, resources = graph.resources().map { if (it.id == mask.id) wrongMaskFormatAndUsage else it }) },
        ).forEach { forge ->
            assertFailsWith<IllegalArgumentException> { forge() }
        }

        val depthGraph = graphOf(pathDraw(concave = true))
        val depth = depthGraph.resources().single { resource ->
            resource.role == PlanResourceRole.DepthStencil
        }
        listOf(
            {
                PlanResource.of(
                    role = depth.role,
                    ordinal = depth.ordinal,
                    kind = PlanResourceKind.Texture2D,
                    format = PlanTextureFormat.Color(depthGraph.colorFormat),
                    extent = requireNotNull(depth.copyExtent()),
                    byteSize = depth.byteSize,
                    usages = setOf(PlanResourceUsage.DepthStencilAttachment),
                    lifetime = depth.lifetime,
                    firstPassIndex = depth.firstPassIndex,
                    lastPassIndexExclusive = depth.lastPassIndexExclusive,
                    sampleCountI32 = depth.sampleCountI32,
                )
            },
            {
                PlanResource.of(
                    role = depth.role,
                    ordinal = depth.ordinal,
                    kind = PlanResourceKind.Texture2D,
                    format = requireNotNull(depth.format),
                    extent = requireNotNull(depth.copyExtent()),
                    byteSize = depth.byteSize,
                    usages = setOf(PlanResourceUsage.RenderAttachment),
                    lifetime = depth.lifetime,
                    firstPassIndex = depth.firstPassIndex,
                    lastPassIndexExclusive = depth.lastPassIndexExclusive,
                    sampleCountI32 = depth.sampleCountI32,
                )
            },
        ).forEach { forgeDepth ->
            assertFailsWith<IllegalArgumentException> { forgeDepth() }
        }
    }

    @Test
    fun `lowerer rejects stale public budget and capability snapshots`() {
        val graph = aaGraph()

        assertIs<GpuPlanLoweringResult.InvalidPlan>(
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = rendererCapabilities(),
                    deviceGeneration = GPUDeviceGenerationID(7),
                    currentBudget = PlanBudget(graph.budget.maxFrameLocalBytes + 1L),
                    frameId = GPUFrameID(8),
                    recordingId = GPURecordingID("w4d-general-stale-budget"),
                ),
            ),
        )
        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = rendererCapabilities().copy(
                        supportedTextureUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    ),
                    deviceGeneration = GPUDeviceGenerationID(7),
                    currentBudget = graph.budget,
                    frameId = GPUFrameID(8),
                    recordingId = GPURecordingID("w4d-general-stale-capabilities"),
                ),
            ),
        )
    }

    @Test
    fun `lowers a transformed hard path at one sample without a resolve task`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graphOf(pathDraw(antiAlias = false)))
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(listOf(org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame), renders.map { it.samplePlan })
        assertEquals(listOf(0), renders.map { it.drawPackets.single().commandIdValue })
        assertEquals(null, renders.single().sampleContinuationKey)
    }

    @Test
    fun `lowers direct and stencil AA paths in their visual command order`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graphOf(pathDraw(), pathDraw(concave = true))),
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(listOf(0, 1, 1), renders.map { it.drawPackets.single().commandIdValue })
        assertEquals(
            List(3) { org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4) },
            renders.map { it.samplePlan },
        )
    }

    @Test
    fun `lowers every AA path packet as an ordered four sample render`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graphOf(pathDraw(), pathDraw(concave = true))),
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(3, renders.size)
        assertEquals(
            List(3) { org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4) },
            renders.map { it.samplePlan },
        )
        assertEquals(listOf("clear", "load", "load"), renders.map { it.loadStore.loadOp })
    }

    @Test
    fun `lowers mixed AA and hard paths without changing visual order`() {
        val graph = graphOf(pathDraw(), pathDraw(antiAlias = false))
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graph),
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(listOf(0, 1, 1), renders.map { it.drawPackets.single().commandIdValue })
        assertEquals(
            listOf(
                org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4),
                org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
                org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4),
            ),
            renders.map { it.samplePlan },
        )
        assertEquals(listOf("clear", "clear", "load"), renders.map { it.loadStore.loadOp })
        val binaryMaskPacket = renders.last().drawPackets.single()
        val consumer = assertNotNull(binaryMaskPacket.w4dBinaryMaskConsumer)
        val mask = graph.resources().single { resource ->
            resource.role == org.graphiks.kanvas.gpu.plan.PlanResourceRole.PathHardEdgeMask
        }
        assertEquals(mask.id.value, consumer.maskResourceId)
        assertEquals(consumer.resourceSlot, binaryMaskPacket.resourceSlot)
        assertEquals(consumer.bindingLayoutHash, binaryMaskPacket.bindingLayoutHash)
        assertEquals(consumer.renderPipelineKey, binaryMaskPacket.renderPipelineKey)
        assertEquals(GPUW4dBinaryMaskPipelineIntent.CoverageMaskConsumer, consumer.pipelineIntent)
        assertEquals(GPUW4dBinaryMaskFetch.TextureLoadIntegerAtTargetTexelUnfiltered, consumer.fetch)
        assertEquals(GPUW4dBinaryMaskCoverGeometry.TargetScissorQuad, consumer.coverGeometry)
        assertEquals(4, consumer.broadcastSampleCountI32)
        assertEquals(true, consumer.broadcastsSameBinaryColorAndAlpha)
        assertEquals(null, renders.first().drawPackets.single().w4dBinaryMaskConsumer)
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(binaryMaskPacket.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(
            listOf(
                semantic.scissorBounds.left.toFloat(), semantic.scissorBounds.top.toFloat(),
                semantic.scissorBounds.right.toFloat(), semantic.scissorBounds.top.toFloat(),
                semantic.scissorBounds.right.toFloat(), semantic.scissorBounds.bottom.toFloat(),
                semantic.scissorBounds.left.toFloat(), semantic.scissorBounds.bottom.toFloat(),
            ),
            geometry.vertices,
        )
        assertEquals(listOf(0, 1, 2, 0, 2, 3), geometry.indices)
    }

    @Test
    fun `preserves fill stroke hairline affine and perspective path order`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(
                graphOf(
                    pathDraw(),
                    pathDraw(style = PaintStyleNode.STROKE),
                    pathDraw(style = PaintStyleNode.STROKE, strokeWidth = 0f),
                    pathDraw(transform = Matrix3x3F32(persp0 = 0.01f)),
                ),
            ),
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(listOf(0, 1, 1, 2, 2, 3), renders.map { it.drawPackets.single().commandIdValue })
        assertEquals(
            List(6) { org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4) },
            renders.map { it.samplePlan },
        )
    }

    private fun aaGraph(): RenderGraph = graphOf(pathDraw())

    private fun graphOf(vararg draws: SceneCommand.Draw): RenderGraph {
        val scene = SceneSnapshot.of(SceneExtent(16, 16), ColorSpace.SRGB, draws.toList())
        val compiler = W4dGeneralPathPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun pathDraw(
        antiAlias: Boolean = true,
        concave: Boolean = false,
        style: PaintStyleNode = PaintStyleNode.FILL,
        strokeWidth: Float = 2f,
        transform: Matrix3x3F32 = Matrix3x3F32.rotation(0.25f),
    ): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xC0FF0000u)
        return SceneCommand.Draw(
            DrawNode(
                geometry = GeometryNode.Path(
                    if (concave) {
                        PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(12f, 12f)
                            .lineTo(7f, 6f).lineTo(2f, 12f).close().build()
                    } else {
                        PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build()
                    },
                ),
                material = MaterialNode.Solid(color),
                coverage = if (antiAlias) CoverageRequest.ANTIALIASED else CoverageRequest.HARD_EDGE,
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
                    strokeWidth = strokeWidth,
                    strokeCap = StrokeCapNode.BUTT,
                    strokeJoin = StrokeJoinNode.MITER,
                    strokeMiter = 4f,
                    antiAlias = antiAlias,
                ),
            ),
        )
    }

    private fun lower(graph: RenderGraph): GpuPlanLoweringResult = GpuPlanTaskListLowerer().lower(
        GpuPlanLoweringRequest(
            graph = graph,
            capabilities = rendererCapabilities(),
            deviceGeneration = GPUDeviceGenerationID(7),
            currentBudget = graph.budget,
            frameId = GPUFrameID(8),
            recordingId = GPURecordingID("w4d-general-red"),
        ),
    )

    private fun copyPathPass(
        pass: PlanPass.PathRenderPass,
        draw: org.graphiks.kanvas.gpu.plan.PathRenderDraw = pass.draw,
        atomicGroup: org.graphiks.kanvas.gpu.plan.PlanAtomicGroupId? = pass.atomicGroup,
        resolveTarget: org.graphiks.kanvas.gpu.plan.PlanResourceId? = pass.resolveTarget,
    ): PlanPass.PathRenderPass = PlanPass.PathRenderPass(
        ordinal = pass.ordinal,
        target = pass.target,
        draw = draw,
        phase = pass.phase,
        drawDataResources = pass.drawDataResources,
        atomicGroup = atomicGroup,
        depthStencil = pass.depthStencil,
        load = pass.load,
        store = pass.store,
        depthStencilAccess = pass.depthStencilAccess,
        depthStencilLoadStore = pass.depthStencilLoadStore,
        resolveTarget = resolveTarget,
    )

    private fun rebuild(
        graph: RenderGraph,
        resources: List<PlanResource> = graph.resources(),
        replacePasses: Map<org.graphiks.kanvas.gpu.plan.PlanPassId, PlanPass.PathRenderPass> = emptyMap(),
    ): RenderGraph = RenderGraph.of(
        id = PlanId("forged-${graph.id.value}"),
        capabilityId = graph.capabilityId,
        targetExtent = graph.targetExtent,
        colorFormat = graph.colorFormat,
        capabilities = graph.capabilities,
        budget = graph.budget,
        visualCommandCount = graph.visualCommandCount,
        resources = resources,
        passes = graph.passes().map { pass -> replacePasses[pass.id] ?: pass },
        dependencies = graph.dependencies(),
        peakFrameLocalBytes = graph.peakFrameLocalBytes,
    )

    private fun planCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 7,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = setOf(
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
                1,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                4,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        ),
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                1,
            ),
        ),
    )

    private fun rendererCapabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4d-general", "device"),
        facts = emptyList(),
        snapshotId = "w4d-general-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            GPUTextureFormat.RGBA8UnormSrgb,
            GPUTextureFormat.RGBA8Unorm,
            GPUTextureFormat.Depth24PlusStencil8,
        ),
        supportedTextureUsage = GPUTextureUsage.RenderAttachment or
            GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                    resolveSourceSampleCounts = setOf(4),
                ),
                GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1),
                ),
                GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                ),
            ),
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )
}
