package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanTextureResolveSupport
import org.graphiks.kanvas.gpu.plan.PlanTextureSampleSupport
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
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
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuPlanTaskListLowererW4eTest {
    @Test
    fun `AA clip lowering prepares four-sample mask resolve and D24S8 together`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph())),
        )

        val prepared = assertIs<GPUTask.PrepareResources>(lowered.taskList.tasks.first()).requests
        val clipTextures = prepared.filter { it.role in setOf(
            GPUFrameResourceRole.ClipMask,
            GPUFrameResourceRole.ClipDepthStencil,
        ) }
        assertEquals(
            listOf(1, 1, 1, 4, 4),
            clipTextures.map { assertIs<GPUFrameTextureDescriptor>(it.descriptor).sampleCount }.sorted(),
        )
        assertEquals(4, clipTextures.count { it.role == GPUFrameResourceRole.ClipMask })
        assertEquals(1, clipTextures.count { it.role == GPUFrameResourceRole.ClipDepthStencil })
    }

    @Test
    fun `lowering rejects a graph whose canonical W4e identity was forged`() {
        val graph = aaMaskGraph()
        val forged = RenderGraph.of(
            id = PlanId("forged-w4e-identity"),
            capabilityId = graph.capabilityId,
            targetExtent = graph.targetExtent,
            colorFormat = graph.colorFormat,
            capabilities = graph.capabilities,
            budget = graph.budget,
            visualCommandCount = graph.visualCommandCount,
            resources = graph.resources(),
            passes = graph.passes(),
            dependencies = graph.dependencies(),
            peakFrameLocalBytes = graph.peakFrameLocalBytes,
        )

        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(forged)))
    }

    private fun aaMaskGraph(): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(16, 16),
            ColorSpace.SRGB,
            listOf(pathDraw()),
        )
        val compiler = W4eClipPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun pathDraw(): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xC0FF0000u)
        return SceneCommand.Draw(
            DrawNode(
                geometry = GeometryNode.Path(
                    PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build(),
                ),
                material = MaterialNode.Solid(color),
                coverage = CoverageRequest.ANTIALIASED,
                clip = ClipStackNode.Operations.of(listOf(
                    ClipEntry(
                        geometry = GeometryNode.Path(
                            PathBuilder().moveTo(3f, 3f).lineTo(13f, 3f).lineTo(3f, 13f).close().build(),
                        ),
                        operation = ClipOperation.INTERSECT,
                        antiAlias = true,
                        transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
                    ),
                )),
                blend = BlendNode.SrcOver,
                effects = EffectStack.Empty,
                transform = Matrix3x3F32.rotation(0.25f),
                origin = DrawOrigin.PATH,
                paint = PaintNode(
                    color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                    PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true,
                ),
            ),
        )
    }

    private fun request(graph: RenderGraph): GpuPlanLoweringRequest = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = rendererCapabilities(),
        deviceGeneration = GPUDeviceGenerationID(7),
        currentBudget = graph.budget,
        frameId = GPUFrameID(8),
        recordingId = GPURecordingID("w4e-red"),
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
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource)),
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, setOf(PlanResourceUsage.RenderAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1, setOf(PlanResourceUsage.DepthStencilAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4, setOf(PlanResourceUsage.DepthStencilAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 4, setOf(PlanResourceUsage.RenderAttachment)),
        ),
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, 1),
            PlanTextureResolveSupport.of(PlanTextureFormat.CoverageMask, 4, 1),
        ),
    )

    private fun rendererCapabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4e", "device"),
        facts = emptyList(),
        snapshotId = "w4e-test",
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
        supportedTextureUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(mapOf(
            GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(setOf(1, 4), setOf(4)),
            GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(setOf(1, 4), setOf(4)),
            GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(setOf(1, 4)),
        )),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )
}
