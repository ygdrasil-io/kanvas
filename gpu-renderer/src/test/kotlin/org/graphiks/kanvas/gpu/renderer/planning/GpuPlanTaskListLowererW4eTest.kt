package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
import org.graphiks.kanvas.gpu.plan.PlanPass
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedInverseInteriorCoverage
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
import org.graphiks.math.geometry.FillRule
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

    @Test
    fun `lowering materializes the sealed mask chain before its color consumer`() {
        val graph = aaMaskGraph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(graph.passes().size - 1, renders.size)
        assertEquals(
            graph.passes().dropLast(1).map { pass -> pass.id.value },
            renders.map { render -> render.taskId.value.substringAfterLast('.') },
        )
        assertEquals(lowered.taskList.tasks.size - 1, lowered.taskList.dependencies.size)
    }

    @Test
    fun `reused sealed clip emits one preparation chain before both consumers`() {
        val graph = aaMaskGraph(drawCount = 2)
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))

        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskInitialize>().size)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().size)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskFold>().size)
        assertEquals(graph.passes().size - 1, lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().size)
    }

    @Test
    fun `two consumers retain one sealed mask strategy and its sampled resource`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph(drawCount = 2))),
        )

        val consumers = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().mapNotNull { render ->
            render.drawPackets.single().w4ePreparedClipConsumer as? GPUW4ePreparedClipConsumerAuthority.Mask
        }
        assertEquals(2, consumers.size)
        assertEquals(1, consumers.map { it.maskResourceId }.distinct().size)
        lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().filter { render ->
            render.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.Mask
        }.forEach { render ->
            assertEquals(consumers.first().maskResourceId, render.resourceUses.single().resource.value.substringAfterLast('.'))
        }
    }

    @Test
    fun `inverse mask retains its full domain and zero interior for each reused consumer`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
                request(aaMaskGraph(drawCount = 2, inverse = true, inverseEmpty = true)),
            ),
        )

        val consumers = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().mapNotNull { render ->
            render.drawPackets.single().w4ePreparedClipConsumer as? GPUW4ePreparedClipConsumerAuthority.InverseMask
        }
        assertEquals(4, consumers.size)
        assertEquals(1, consumers.map { it.maskResourceId }.distinct().size)
        consumers.forEach { consumer ->
            assertEquals(0, consumer.domain.left)
            assertEquals(0, consumer.domain.top)
            assertEquals(16, consumer.domain.right)
            assertEquals(16, consumer.domain.bottom)
            assertIs<GPUW4ePreparedInverseInteriorCoverage.Zero>(consumer.interiorCoverage)
        }
    }

    @Test
    fun `inverting a sealed mask changes the prepared consumer while retaining finite interior`() {
        val mask = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph())),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().first { render ->
            render.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.Mask
        }
        val inverse = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph(inverse = true))),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().first { render ->
            render.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.InverseMask
        }

        assertIs<GPUW4ePreparedClipConsumerAuthority.Mask>(
            mask.drawPackets.single().w4ePreparedClipConsumer,
        )
        val inverseConsumer = assertIs<GPUW4ePreparedClipConsumerAuthority.InverseMask>(
            inverse.drawPackets.single().w4ePreparedClipConsumer,
        )
        val interior = assertIs<GPUW4ePreparedInverseInteriorCoverage.Geometry>(
            inverseConsumer.interiorCoverage,
        )
        assertEquals(16, inverseConsumer.domain.right)
        assertEquals(1, inverse.resourceUses.size)
        assertTrue(interior.copyGeometryF32().emittedNonZeroClosedEdgeCountI32 > 0)
    }

    @Test
    fun `inverse zero clip becomes a full-domain prepared consumer without a sampled mask`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
                request(aaMaskGraph(inverse = true, zeroClip = true, inverseEmpty = true)),
            ),
        )

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().filter { task ->
            task.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.InverseDomain
        }
        assertEquals(2, renders.size)
        renders.forEach { render ->
            val consumer = assertIs<GPUW4ePreparedClipConsumerAuthority.InverseDomain>(
                render.drawPackets.single().w4ePreparedClipConsumer,
            )
            assertEquals(16, consumer.domain.right)
            assertIs<GPUW4ePreparedInverseInteriorCoverage.Zero>(consumer.interiorCoverage)
            assertEquals(0, render.resourceUses.size)
        }
    }

    private fun aaMaskGraph(
        drawCount: Int = 1,
        inverse: Boolean = false,
        zeroClip: Boolean = false,
        inverseEmpty: Boolean = false,
    ): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(16, 16),
            ColorSpace.SRGB,
            List(drawCount) { pathDraw(inverse, zeroClip, inverseEmpty) },
        )
        val compiler = W4eClipPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun pathDraw(
        inverse: Boolean = false,
        zeroClip: Boolean = false,
        inverseEmpty: Boolean = false,
    ): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xC0FF0000u)
        val fillRule = if (inverse) FillRule.INVERSE_WINDING else FillRule.WINDING
        val clipPath = if (zeroClip) {
            PathBuilder().build()
        } else {
            PathBuilder().moveTo(3f, 3f).lineTo(13f, 3f).lineTo(3f, 13f).close().build()
        }
        return SceneCommand.Draw(
            DrawNode(
                geometry = GeometryNode.Path(
                    if (inverseEmpty) PathBuilder(fillRule).build() else PathBuilder(fillRule)
                        .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build(),
                ),
                material = MaterialNode.Solid(color),
                coverage = CoverageRequest.ANTIALIASED,
                clip = ClipStackNode.Operations.of(listOf(
                    ClipEntry(
                        geometry = GeometryNode.Path(
                            clipPath,
                        ),
                        operation = if (zeroClip) ClipOperation.DIFFERENCE else ClipOperation.INTERSECT,
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
