package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.AnalyticRectDraw
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDrawDataResources
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SolidRectDraw
import org.graphiks.kanvas.gpu.plan.W4aAnalyticRectPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class GpuPlanTaskListLowererW4aTest {
    private val lowerer = GpuPlanTaskListLowerer()

    @Test
    fun `valid W4a graph lowers to sealed analytic Uniform80 packets`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(request(readyW4aGraph())))
        val preparation = assertIs<GPUTask.PrepareResources>(lowered.taskList.tasks.first())
        val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single()

        assertEquals(
            listOf(GPUFrameResourceRole.SceneTarget, GPUFrameResourceRole.ReadbackStaging),
            preparation.requests.map { request -> request.role },
        )
        assertEquals(2, render.drawPackets.size)
        val scratch = assertNotNull(render.drawPackets.first().corePrimitivePreparedAuthority?.w4aSessionScratch)
        assertEquals("core-primitive-analytic-shape-uniform-pass", scratch.uniformPlan.sourceLabel)
        assertEquals(2, scratch.uniformPlan.slots.size)
        render.drawPackets.forEach { packet ->
            val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
            assertEquals(GPUCorePrimitiveCoverageMode.ScalarAA, semantic.coverageMode)
            assertEquals(80L, packet.corePrimitivePreparedAuthority
                ?.analyticShapeUniformSeal?.payloadBytes)
            assertEquals(scratch, packet.corePrimitivePreparedAuthority?.w4aSessionScratch)
        }
    }

    @Test
    fun `rejects an unknown capability id without emitting a task list`() {
        val base = readyW4aGraph()

        assertInvalid(graphLike(base, capabilityId = "other-w4a-capability"))
    }

    @Test
    fun `rejects a W4a resource role contradiction without emitting a task list`() {
        val base = readyW4aGraph()
        val resources = base.resources().toMutableList()
        val vertex = resources[2]
        resources[2] = resourceLike(vertex, role = PlanResourceRole.LogicalTarget, ordinal = 1)
        val render = renderLike(base, PlanDrawDataResources(resources[2].id, resources[3].id, resources[4].id))

        assertInvalid(graphLike(base, resources = resources, render = render))
    }

    @Test
    fun `rejects a W4a resource usage contradiction without emitting a task list`() {
        val base = readyW4aGraph()
        val resources = base.resources().toMutableList()
        resources[2] = resourceLike(resources[2], usages = setOf(PlanResourceUsage.Vertex))

        assertInvalid(graphLike(base, resources = resources))
    }

    @Test
    fun `rejects a W4a resource lifetime contradiction without emitting a task list`() {
        val base = readyW4aGraph()
        val resources = base.resources().toMutableList()
        resources[2] = resourceLike(resources[2], lastPassIndexExclusive = 1)

        assertInvalid(graphLike(base, resources = resources))
    }

    @Test
    fun `rejects swapped W4a V I U bindings without emitting a task list`() {
        val base = readyW4aGraph()
        val resources = base.resources()
        val render = renderLike(base, PlanDrawDataResources(resources[3].id, resources[2].id, resources[4].id))

        assertInvalid(graphLike(base, render = render))
    }

    @Test
    fun `rejects a W4a scratch capacity contradiction without emitting a task list`() {
        val base = readyW4aGraph()
        val resources = base.resources().toMutableList()
        resources[2] = resourceLike(resources[2], byteSize = resources[2].byteSize * 2L)

        assertInvalid(graphLike(base, resources = resources))
    }

    @Test
    fun `rejects a W4a uniform stride snapshot contradiction without emitting a task list`() {
        val base = readyW4aGraph()

        assertUnsupported(graphLike(base, capabilities = planCapabilities(minUniformAlignment = 128)))
    }

    @Test
    fun `rejects a W4a peak contradiction after recomputing the sealed readback row`() {
        val base = readyW4aGraph()
        val capabilities = planCapabilities(copyBytesPerRowAlignment = 128)

        assertInvalid(
            graphLike(base, capabilities = capabilities),
            rendererCapabilities(copyBytesPerRowAlignment = 128),
        )
    }

    @Test
    fun `rejects a non analytic draw type without emitting a task list`() {
        val base = readyW4aGraph()
        val analytic = renderOf(base).draws().filterIsInstance<AnalyticRectDraw>()
        val replacement = SolidRectDraw.of(
            commandIndex = analytic.first().commandIndex,
            color = analytic.first().color,
            visibleBounds = analytic.first().copyRasterBounds(),
            scissor = analytic.first().copyScissor(),
            coverage = CoveragePlan.AnalyticScalarAA,
        )
        val render = PlanPass.RenderPass(
            ordinal = 0,
            target = renderOf(base).target,
            draws = listOf(replacement, analytic.last()),
            load = renderOf(base).load,
            store = renderOf(base).store,
            drawDataResources = renderOf(base).drawDataResources,
        )

        assertInvalid(graphLike(base, render = render))
    }

    @Test
    fun `rejects non ScalarAA coverage without emitting a task list`() {
        val base = readyW4aGraph()
        val analytic = renderOf(base).draws().filterIsInstance<AnalyticRectDraw>()
        val replacement = SolidRectDraw.of(
            commandIndex = analytic.first().commandIndex,
            color = analytic.first().color,
            visibleBounds = analytic.first().copyRasterBounds(),
            scissor = analytic.first().copyScissor(),
            coverage = CoveragePlan.FullOrScissor,
        )
        val render = PlanPass.RenderPass(
            ordinal = 0,
            target = renderOf(base).target,
            draws = listOf(replacement, analytic.last()),
            load = renderOf(base).load,
            store = renderOf(base).store,
            drawDataResources = renderOf(base).drawDataResources,
        )

        assertInvalid(graphLike(base, render = render))
    }

    @Test
    fun `rejects a W4a scissor outside the planned raster without emitting a task list`() {
        val base = readyW4aGraph()
        val analytic = renderOf(base).draws().filterIsInstance<AnalyticRectDraw>()
        val changedScissor = AnalyticRectDraw.of(
            commandIndex = analytic.first().commandIndex,
            color = analytic.first().color,
            deviceBounds = analytic.first().copyDeviceBounds(),
            rasterBounds = analytic.first().copyRasterBounds(),
            scissor = RectI32(3, 0, 4, 3),
        )
        val render = PlanPass.RenderPass(
            ordinal = 0,
            target = renderOf(base).target,
            draws = listOf(changedScissor, analytic.last()),
            load = renderOf(base).load,
            store = renderOf(base).store,
            drawDataResources = renderOf(base).drawDataResources,
        )

        assertInvalid(graphLike(base, render = render))
    }

    @Test
    fun `rejects a stale W4a capability snapshot without emitting a task list`() {
        val base = readyW4aGraph()

        assertUnsupported(graphLike(base, capabilities = planCapabilities(deviceGeneration = 8)))
    }

    private fun readyW4aGraph(): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 3),
            ColorSpace.SRGB,
            listOf(
                rect(0.25f, 0.5f, 2.75f, 2.25f, 0x80ff0000u),
                rect(1.25f, 0.5f, 3.75f, 2.25f, 0x800000ffu),
            ),
        )
        val compiler = W4aAnalyticRectPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun rect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: UInt,
    ): SceneCommand.Draw = SceneCommand.Draw(
        DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(left, top, right, bottom)),
            material = MaterialNode.Solid(ColorARGB.fromPackedUInt(color)),
            coverage = CoverageRequest.ANTIALIASED,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RECT,
        ),
    )

    private fun request(
        graph: RenderGraph,
        capabilities: GPUCapabilities = rendererCapabilities(),
    ): GpuPlanLoweringRequest = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = capabilities,
        deviceGeneration = GPUDeviceGenerationID(7),
        currentBudget = graph.budget,
        frameId = GPUFrameID(4),
        recordingId = GPURecordingID("w4a-lowering"),
    )

    private fun planCapabilities(
        deviceGeneration: Int = 7,
        copyBytesPerRowAlignment: Int = 256,
        minUniformAlignment: Int = 256,
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = deviceGeneration.toLong(),
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = copyBytesPerRowAlignment,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = minUniformAlignment,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
    )

    private fun assertInvalid(
        graph: RenderGraph,
        capabilities: GPUCapabilities = rendererCapabilities(),
    ) {
        assertIs<GpuPlanLoweringResult.InvalidPlan>(lowerer.lower(request(graph, capabilities)))
    }

    private fun assertUnsupported(graph: RenderGraph) {
        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(lowerer.lower(request(graph)))
    }

    private fun graphLike(
        base: RenderGraph,
        capabilityId: String = base.capabilityId,
        capabilities: PlanCapabilitySnapshot = base.capabilities,
        resources: List<PlanResource> = base.resources(),
        render: PlanPass.RenderPass = renderOf(base),
        readback: PlanPass.ReadbackPass = readbackOf(base),
    ): RenderGraph = RenderGraph.of(
        id = base.id,
        capabilityId = capabilityId,
        targetExtent = base.targetExtent,
        colorFormat = base.colorFormat,
        capabilities = capabilities,
        budget = base.budget,
        visualCommandCount = base.visualCommandCount,
        resources = resources,
        passes = listOf(render, readback),
        dependencies = listOf(PlanPassDependency(render.id, readback.id)),
        peakFrameLocalBytes = peak(resources, 2),
    )

    private fun renderLike(base: RenderGraph, bindings: PlanDrawDataResources): PlanPass.RenderPass =
        PlanPass.RenderPass(
            ordinal = 0,
            target = renderOf(base).target,
            draws = renderOf(base).draws(),
            load = renderOf(base).load,
            store = renderOf(base).store,
            drawDataResources = bindings,
        )

    private fun resourceLike(
        resource: PlanResource,
        role: PlanResourceRole = resource.role,
        ordinal: Int = resource.ordinal,
        byteSize: Long = resource.byteSize,
        usages: Set<PlanResourceUsage> = resource.usages(),
        lastPassIndexExclusive: Int = resource.lastPassIndexExclusive,
    ): PlanResource = PlanResource.of(
        role = role,
        ordinal = ordinal,
        kind = resource.kind,
        format = resource.format,
        extent = resource.copyExtent(),
        byteSize = byteSize,
        usages = usages,
        lifetime = resource.lifetime,
        firstPassIndex = resource.firstPassIndex,
        lastPassIndexExclusive = lastPassIndexExclusive,
    )

    private fun renderOf(graph: RenderGraph): PlanPass.RenderPass =
        assertIs<PlanPass.RenderPass>(graph.passes().first())

    private fun readbackOf(graph: RenderGraph): PlanPass.ReadbackPass =
        assertIs<PlanPass.ReadbackPass>(graph.passes().last())

    private fun peak(resources: List<PlanResource>, passCount: Int): Long =
        (0 until passCount).maxOf { passIndex ->
            resources.filter { resource ->
                resource.firstPassIndex <= passIndex && passIndex < resource.lastPassIndexExclusive
            }.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) }
        }

    private fun rendererCapabilities(
        copyBytesPerRowAlignment: Long = 256L,
        minUniformBufferOffsetAlignment: Long = 256L,
    ): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("w4a.scalar_aa", "test", "supported", true, "w4a"),
        ),
        snapshotId = "w4a-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048L,
            copyBytesPerRowAlignment = copyBytesPerRowAlignment,
            minUniformBufferOffsetAlignment = minUniformBufferOffsetAlignment,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1L,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb),
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1),
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
