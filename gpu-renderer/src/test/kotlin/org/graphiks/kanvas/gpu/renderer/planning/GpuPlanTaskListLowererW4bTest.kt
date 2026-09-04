package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.AnalyticRRectDraw
import org.graphiks.kanvas.gpu.plan.AnalyticRectDraw
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDrawDataResources
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4bAnalyticRRectPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
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
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class GpuPlanTaskListLowererW4bTest {
    private val lowerer = GpuPlanTaskListLowerer()

    @Test
    fun `sealed W4b graph lowers ordered RRect packets with shared W4b scratch`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(request(readyW4bGraph())))
        val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single()

        assertEquals(listOf(0L, 1L), render.drawPackets.map { it.sortKey })
        assertEquals(listOf("packet.w4b.0", "packet.w4b.1"), render.drawPackets.map { it.packetId.value })
        assertEquals(listOf("pass.w4b.main", "pass.w4b.main"), render.drawPackets.map { it.passId })
        assertEquals(listOf("w4b-analytic-rrect", "w4b-analytic-rrect"), render.drawPackets.map { it.insertionReasonCode })
        assertEquals(
            listOf(GPUCorePrimitiveGeometry.RRect::class, GPUCorePrimitiveGeometry.RRect::class),
            render.drawPackets.map { assertIs<GPUDrawSemanticPayload.CorePrimitive>(it.semanticPayload).geometry::class },
        )
        render.drawPackets.forEach { packet ->
            val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
            assertEquals(GPUCorePrimitiveSourceFamily.RRect, semantic.sourceFamily)
            assertEquals(GPUCorePrimitiveCoverageMode.ScalarAA, semantic.coverageMode)
            assertNotNull(semantic.rrectGeometryAuthority)
        }

        val scratch = assertNotNull(render.drawPackets.first().corePrimitivePreparedAuthority?.w4bSessionScratch)
        assertSame(scratch, render.drawPackets.last().corePrimitivePreparedAuthority?.w4bSessionScratch)
        assertEquals(listOf(DrawOrigin.RECT, DrawOrigin.RRECT), scratch.draws.map { it.origin })
        assertEquals(5, readyW4bGraph().resources().size)
    }

    @Test
    fun `post Ready W4b contradictions are terminal invalid plans`() {
        val base = readyW4bGraph()
        val analytic = renderOf(base).draws().filterIsInstance<AnalyticRRectDraw>()
        val injectedRect = AnalyticRectDraw.of(
            commandIndex = analytic.first().commandIndex,
            color = analytic.first().color,
            deviceBounds = analytic.first().copyDeviceShape().rect,
            rasterBounds = analytic.first().copyRasterBounds(),
            scissor = analytic.first().copyScissor(),
        )
        val drawTypeContradiction = graphLike(
            base,
            render = renderLike(base, listOf(injectedRect, analytic.last())),
        )
        val lifetimeResources = base.resources().toMutableList()
        lifetimeResources[4] = resourceLike(lifetimeResources[4], firstPassIndex = 1)
        val lifetimeContradiction = graphLike(
            base,
            resources = lifetimeResources,
            omitDrawDataResources = true,
        )
        val rrectChangedToRect = AnalyticRRectDraw.of(
            commandIndex = analytic.last().commandIndex,
            color = analytic.last().color,
            origin = DrawOrigin.RECT,
            deviceShape = analytic.last().copyDeviceShape(),
            rasterBounds = analytic.last().copyRasterBounds(),
            scissor = analytic.last().copyScissor(),
        )
        val provenanceContradiction = graphLike(
            base,
            render = renderLike(base, listOf(analytic.first(), rrectChangedToRect)),
        )

        listOf(drawTypeContradiction, lifetimeContradiction, provenanceContradiction).forEach { graph ->
            assertIs<GpuPlanLoweringResult.InvalidPlan>(lowerer.lower(request(graph)))
        }
    }

    @Test
    fun `divergent sealed W4b device snapshot is unsupported`() {
        val divergent = graphLike(readyW4bGraph(), capabilities = planCapabilities(deviceGeneration = 8))

        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(lowerer.lower(request(divergent)))
    }

    @Test
    fun `planned W4b authority refuses structural scratch erasure`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(request(readyW4bGraph())))
        val authority = requireNotNull(
            lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
                .drawPackets.first().corePrimitivePreparedAuthority,
        )

        assertFailsWith<IllegalArgumentException> {
            authority.copy(w4bSessionScratch = null)
        }
    }

    private fun readyW4bGraph(): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            listOf(rect(), rrect()),
        )
        val compiler = W4bAnalyticRRectPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun rect(): SceneCommand.Draw = SceneCommand.Draw(
        DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
            material = MaterialNode.Solid(ColorARGB.fromPackedUInt(0x80ff0000u)),
            coverage = CoverageRequest.ANTIALIASED,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RECT,
        ),
    )

    private fun rrect(): SceneCommand.Draw = SceneCommand.Draw(
        DrawNode(
            geometry = GeometryNode.RRect.of(
                RRectF32.of(
                    RectF32(0f, 0f, 4f, 4f),
                    CornerRadiiF32.of(1f, 1f),
                    CornerRadiiF32.of(2f, 1f),
                    CornerRadiiF32.of(1f, 2f),
                    CornerRadiiF32.of(0.5f, 1f),
                ),
            ),
            material = MaterialNode.Solid(ColorARGB.fromPackedUInt(0x800000ffu)),
            coverage = CoverageRequest.ANTIALIASED,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RRECT,
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
        recordingId = GPURecordingID("w4b-lowering"),
    )

    private fun graphLike(
        base: RenderGraph,
        capabilities: PlanCapabilitySnapshot = base.capabilities,
        resources: List<PlanResource> = base.resources(),
        render: PlanPass.RenderPass = renderOf(base),
        omitDrawDataResources: Boolean = false,
    ): RenderGraph {
        val readback = assertIs<PlanPass.ReadbackPass>(base.passes().last())
        val effectiveRender = PlanPass.RenderPass(
            ordinal = 0,
            target = resources[0].id,
            draws = render.draws(),
            load = render.load,
            store = render.store,
            drawDataResources = if (omitDrawDataResources) {
                null
            } else {
                PlanDrawDataResources(resources[2].id, resources[3].id, resources[4].id)
            },
        )
        val effectiveReadback = PlanPass.ReadbackPass(
            ordinal = 0,
            source = resources[0].id,
            staging = resources[1].id,
            bytesPerRow = readback.bytesPerRow,
        )
        return RenderGraph.of(
            id = base.id,
            capabilityId = base.capabilityId,
            targetExtent = base.targetExtent,
            colorFormat = base.colorFormat,
            capabilities = capabilities,
            budget = base.budget,
            visualCommandCount = base.visualCommandCount,
            resources = resources,
            passes = listOf(effectiveRender, effectiveReadback),
            dependencies = listOf(PlanPassDependency(effectiveRender.id, effectiveReadback.id)),
            peakFrameLocalBytes = peak(resources, passCount = 2),
        )
    }

    private fun renderLike(base: RenderGraph, draws: List<org.graphiks.kanvas.gpu.plan.PlanDraw>): PlanPass.RenderPass =
        PlanPass.RenderPass(
            ordinal = 0,
            target = renderOf(base).target,
            draws = draws,
            load = AttachmentLoadPlan.ClearTransparent,
            store = AttachmentStorePlan.Store,
            drawDataResources = renderOf(base).drawDataResources,
        )

    private fun resourceLike(
        resource: PlanResource,
        firstPassIndex: Int = resource.firstPassIndex,
    ): PlanResource = PlanResource.of(
        role = resource.role,
        ordinal = resource.ordinal,
        kind = resource.kind,
        format = resource.format,
        extent = resource.copyExtent(),
        byteSize = resource.byteSize,
        usages = resource.usages(),
        lifetime = PlanResourceLifetime.FrameLocal,
        firstPassIndex = firstPassIndex,
        lastPassIndexExclusive = resource.lastPassIndexExclusive,
    )

    private fun renderOf(graph: RenderGraph): PlanPass.RenderPass =
        assertIs<PlanPass.RenderPass>(graph.passes().first())

    private fun peak(resources: List<PlanResource>, passCount: Int): Long =
        (0 until passCount).maxOf { passIndex ->
            resources.filter { resource ->
                resource.firstPassIndex <= passIndex && passIndex < resource.lastPassIndexExclusive
            }.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) }
        }

    private fun planCapabilities(deviceGeneration: Int = 7): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = deviceGeneration.toLong(),
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
    )

    private fun rendererCapabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("w4b.scalar_aa", "test", "supported", true, "w4b")),
        snapshotId = "w4b-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1L,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb),
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(setOf(1))),
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )
}
