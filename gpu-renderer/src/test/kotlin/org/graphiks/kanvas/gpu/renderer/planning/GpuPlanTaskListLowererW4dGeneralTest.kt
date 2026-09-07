package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanTextureResolveSupport
import org.graphiks.kanvas.gpu.plan.PlanTextureSampleSupport
import org.graphiks.kanvas.gpu.plan.RenderGraph
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleLoadTransition
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4dBinaryMaskFetch
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
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
    fun `lowers a transformed hard path at one sample without a resolve task`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graphOf(pathDraw(antiAlias = false)))
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(listOf(org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame), renders.map { it.samplePlan })
        assertEquals(listOf(0), renders.map { it.drawPackets.single().commandIdValue })
        val authority = assertNotNull(renders.single().drawPackets.single().corePrimitivePreparedAuthority)
        assertEquals(null, authority.w4dGeneralPreparedAuthority?.sampleContinuation)
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
    fun `seals every AA path packet before preserving its continuation`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graphOf(pathDraw(), pathDraw(concave = true))),
        )

        val packets = lowered.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap { it.drawPackets }

        assertEquals(3, packets.size)
        val authority = assertNotNull(
            assertNotNull(packets.first().corePrimitivePreparedAuthority).w4dGeneralPreparedAuthority,
        )
        packets.drop(1).forEach { packet ->
            assertSame(authority, assertNotNull(packet.corePrimitivePreparedAuthority).w4dGeneralPreparedAuthority)
        }
        assertEquals("w4d.2-general-prepared-authority-v1", authority.version)
        val continuation = assertNotNull(authority.sampleContinuation)
        assertEquals("w4d.2-path-sample-continuation-v1", continuation.version)
        assertEquals(
            listOf(
                GPUSampleLoadTransition.FreshClear,
                GPUSampleLoadTransition.RetainedLoad,
                GPUSampleLoadTransition.RetainedLoad,
            ),
            continuation.transitions.map { it.loadTransition },
        )
        assertEquals(
            List(3) { GPUSampleStoreAction.Store },
            continuation.transitions.map { it.storeAction },
        )
        assertEquals(
            listOf(
                GPUSampleResolveAction.Skip,
                GPUSampleResolveAction.Skip,
                GPUSampleResolveAction.ResolveCanonical,
            ),
            continuation.transitions.map { it.resolveAction },
        )
        assertFailsWith<UnsupportedOperationException> {
            (continuation.transitions as MutableList).clear()
        }
    }

    @Test
    fun `lowers mixed AA and hard paths without changing visual order`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lower(graphOf(pathDraw(), pathDraw(antiAlias = false))),
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
        val authority = assertNotNull(
            assertNotNull(renders.first().drawPackets.single().corePrimitivePreparedAuthority)
                .w4dGeneralPreparedAuthority,
        )
        val continuation = assertNotNull(authority.sampleContinuation)
        assertEquals(listOf(0, 1), continuation.transitions.map { it.commandIdValue })
        assertEquals(
            listOf(GPUSampleResolveAction.Skip, GPUSampleResolveAction.ResolveCanonical),
            continuation.transitions.map { it.resolveAction },
        )
        val binaryMask = authority.binaryMaskCoverageContracts.single()
        assertEquals(GPUW4dBinaryMaskFetch.TextureLoadIntegerAtTargetTexelUnfiltered, binaryMask.fetch)
        assertEquals(4, binaryMask.broadcastSampleCountI32)
        assertTrue(binaryMask.broadcastsSameBinaryColorAndAlpha)
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
