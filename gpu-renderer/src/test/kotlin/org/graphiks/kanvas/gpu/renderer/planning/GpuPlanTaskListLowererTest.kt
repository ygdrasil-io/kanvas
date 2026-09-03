package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.SolidRectDraw
import org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListAssembler
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreplannedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.W3SessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuPlanTaskListLowererTest {
    private val lowerer = GpuPlanTaskListLowerer()

    @Test
    fun `W3 lowerer emits encoded sRGB readback for the native completion boundary`() {
        val taskList = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph()))).taskList
        val readback = taskList.tasks.filterIsInstance<GPUTask.Readback>().single()

        assertEquals(GPUColorInterpretation.EncodedPremulSrgb, readback.request.outputColorInterpretation)
    }

    @Test
    fun `W3 packet carries the core semantic analysis record authority`() {
        val taskList = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph()))).taskList
        val packet = taskList.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)

        assertEquals(semantic.analysisRecordId, packet.analysisRecordId)
    }

    @Test
    fun `W3 multi draw frame seals one shared physical scratch with exact packed sizes`() {
        val graph = multiDrawGraph()
        val render = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph)),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().single()

        val packets = render.drawPackets
        assertEquals(2, packets.size)
        val scratch = requireNotNull(packets.first().corePrimitivePreparedAuthority?.w3SessionScratch)
        assertSame(scratch, requireNotNull(packets.last().corePrimitivePreparedAuthority?.w3SessionScratch))
        assertEquals(packets.map(GPUDrawPacket::packetId), scratch.packetIds)
        assertEquals(packets.map(GPUDrawPacket::commandIdValue), scratch.commandIds)
        assertEquals(64L, scratch.vertexBytes)
        assertEquals(48L, scratch.indexBytes)
        assertEquals(listOf(32L, 32L), scratch.uniformPlan.slots.map { it.payloadBytes })
    }

    @Test
    fun `W3 scratch accepts the sealed 1 through 512 draw range with exact device limits`() {
        listOf(1, 512).forEach { drawCount ->
            val render = assertIs<GpuPlanLoweringResult.Lowered>(
                lowerer.lower(validRequest(multiDrawGraph(drawCount))),
            ).taskList.tasks.filterIsInstance<GPUTask.Render>().single()
            val scratch = requireNotNull(render.drawPackets.first().corePrimitivePreparedAuthority?.w3SessionScratch)

            assertEquals(drawCount, render.drawPackets.size)
            assertTrue(render.drawPackets.all {
                it.corePrimitivePreparedAuthority?.w3SessionScratch === scratch
            })
            assertEquals(drawCount.toLong() * 32L, scratch.vertexBytes)
            assertEquals(drawCount.toLong() * 24L, scratch.indexBytes)
            assertEquals(1L shl 20, scratch.maxBufferSize)
            assertEquals(1L, scratch.maxDynamicUniformBuffersPerPipelineLayout)
        }
    }

    @Test
    fun `W3 scratch reports stable buffer and dynamic uniform capability refusals`() {
        val dynamicCapabilities = capabilities().let { capabilities ->
            capabilities.copy(
                limits = requireNotNull(capabilities.limits).copy(
                    maxDynamicUniformBuffersPerPipelineLayout = 0,
                ),
            )
        }
        val dynamic = assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            lowerer.lower(validRequest(rendererCapabilities = dynamicCapabilities)),
        )
        assertEquals("w3.capability.dynamic_uniform", dynamic.diagnostic.code.value)

        val buffer = assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            lowerer.lower(
                validRequest(
                    graph = multiDrawGraph(drawCount = 2, maxBufferSize = 256L),
                    rendererCapabilities = capabilities(maxBufferSize = 256L),
                ),
            ),
        )
        assertEquals("w3.capability.buffer_size", buffer.diagnostic.code.value)
    }

    @Test
    fun `W3 scratch refuses pooled buffer floors and rounded capacities before materialization`() {
        listOf(4L * 1024L, 16L * 1024L - 1L).forEach { maxBufferSize ->
            val refusal = assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
                lowerer.lower(
                    validRequest(
                        graph = multiDrawGraph(drawCount = 1, maxBufferSize = maxBufferSize),
                        rendererCapabilities = capabilities(maxBufferSize = maxBufferSize),
                    ),
                ),
            )

            assertEquals("w3.capability.buffer_size", refusal.diagnostic.code.value)
        }

        val roundedRefusal = assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            lowerer.lower(
                validRequest(
                    graph = multiDrawGraph(drawCount = 96, maxBufferSize = 24L * 1024L),
                    rendererCapabilities = capabilities(maxBufferSize = 24L * 1024L),
                ),
            ),
        )
        assertEquals("w3.capability.buffer_size", roundedRefusal.diagnostic.code.value)

        assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(
                validRequest(
                    graph = multiDrawGraph(drawCount = 1, maxBufferSize = 16L * 1024L),
                    rendererCapabilities = capabilities(maxBufferSize = 16L * 1024L),
                ),
            ),
        )
        assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(
                validRequest(
                    graph = multiDrawGraph(drawCount = 96, maxBufferSize = 32L * 1024L),
                    rendererCapabilities = capabilities(maxBufferSize = 32L * 1024L),
                ),
            ),
        )
    }

    @Test
    fun `preplanned W3 scratch must be shared and malformed packet authority refuses safely`() {
        val graph = multiDrawGraph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph))).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val first = render.drawPackets.first()
        val second = render.drawPackets.last()
        val originalAuthority = requireNotNull(second.corePrimitivePreparedAuthority)
        val originalScratch = requireNotNull(originalAuthority.w3SessionScratch)
        val foreignScratch = W3SessionScratchV1(
            planId = originalScratch.planId,
            capabilitySealHash = originalScratch.capabilitySealHash,
            deviceGeneration = originalScratch.deviceGeneration,
            target = originalScratch.target,
            staging = originalScratch.staging,
            targetBounds = originalScratch.targetBounds,
            packetIds = originalScratch.packetIds,
            commandIds = originalScratch.commandIds,
            structuralPipelineKey = originalScratch.structuralPipelineKey,
            uniformPlan = originalScratch.uniformPlan,
            maxBufferSize = originalScratch.maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = originalScratch.maxDynamicUniformBuffersPerPipelineLayout,
            vertexBytes = originalScratch.vertexBytes,
            indexBytes = originalScratch.indexBytes,
            poolCapacities = originalScratch.poolCapacities,
        )
        val foreignPacket = copyPacket(second, second.targetStateHash).attachCorePrimitivePreparedAuthority(
            originalAuthority.copy(w3SessionScratch = foreignScratch),
        )
        val nullClipPacket = copyPacket(
            second,
            second.targetStateHash,
            clipExecutionPlan = null,
        ).attachCorePrimitivePreparedAuthority(
            originalAuthority.copy(),
        )

        listOf(
            listOf(first, foreignPacket),
            listOf(first, nullClipPacket),
        ).forEach { packets ->
            val alteredRender = GPUTask.Render(
                render.taskId,
                render.recordingId,
                render.phase,
                render.target,
                render.loadStore,
                render.samplePlan,
                render.resourceUses,
                render.provisionalSegmentKey,
                packets,
                packets.associate { packet ->
                    packet.packetId to render.batchEligibilityByPacketId.getValue(packet.packetId)
                },
                render.sampleContinuationKey,
                render.compositeMembership,
                render.depthStencilLoadStore,
                render.preparedImageBindingsByPacketId,
                render.preparedTextBindingsByPacketId,
            )
            val base = GPUTaskList(
                lowered.frameId,
                lowered.capabilitySeal,
                lowered.recordingSeals,
                lowered.expectedReplayKeyHash,
                listOf(alteredRender),
                emptyList(),
                lowered.phaseOrder,
                lowered.memoryBudget,
                lowered.diagnostics,
            )
            val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
                GPUCorePrimitivePreplannedFrameRequest(
                    graph.id,
                    base,
                    readback.source,
                    assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                    prepare.requests[0],
                    readback.staging,
                    prepare.requests[1],
                    readback.request,
                    lowered.memoryBudget,
                    graph.passes()[0].id,
                    graph.passes()[1].id,
                ),
            )

            assertEquals(
                "w3.lowering.incompatible_plan",
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `preplanned W3 scratch rechecks device facts packing and canonical pipeline authority`() {
        val graph = multiDrawGraph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph))).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val scratch = requireNotNull(render.drawPackets.first().corePrimitivePreparedAuthority?.w3SessionScratch)

        fun assemble(
            packets: List<GPUDrawPacket> = render.drawPackets,
            memory: GPUFrameMemoryBudgetPlan = lowered.memoryBudget,
        ): GPUCorePrimitivePreparedFrameResult {
            val alteredRender = GPUTask.Render(
                render.taskId,
                render.recordingId,
                render.phase,
                render.target,
                render.loadStore,
                render.samplePlan,
                render.resourceUses,
                render.provisionalSegmentKey,
                packets,
                packets.associate { packet ->
                    packet.packetId to render.batchEligibilityByPacketId.getValue(packet.packetId)
                },
                render.sampleContinuationKey,
                render.compositeMembership,
                render.depthStencilLoadStore,
                render.preparedImageBindingsByPacketId,
                render.preparedTextBindingsByPacketId,
            )
            val base = GPUTaskList(
                lowered.frameId,
                lowered.capabilitySeal,
                lowered.recordingSeals,
                lowered.expectedReplayKeyHash,
                listOf(alteredRender),
                emptyList(),
                lowered.phaseOrder,
                memory,
                lowered.diagnostics,
            )
            return GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
                GPUCorePrimitivePreplannedFrameRequest(
                    graph.id,
                    base,
                    readback.source,
                    assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                    prepare.requests[0],
                    readback.staging,
                    prepare.requests[1],
                    readback.request,
                    memory,
                    graph.passes()[0].id,
                    graph.passes()[1].id,
                ),
            )
        }

        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(assemble())

        val forgedBufferLimit = lowered.memoryBudget.copy(
            deviceLimitFacts = lowered.memoryBudget.deviceLimitFacts.map { fact ->
                if (fact.name == "maxBufferSize") fact.copy(value = "512") else fact
            },
        )
        val forgedAlignment = lowered.memoryBudget.copy(
            deviceLimitFacts = lowered.memoryBudget.deviceLimitFacts.map { fact ->
                if (fact.name == "minUniformBufferOffsetAlignment") fact.copy(value = "128") else fact
            },
        )
        val forgedStructuralKey = scratch.structuralPipelineKey.copy(
            colorFormat = org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm,
        )
        val forgedScratch = W3SessionScratchV1(
            planId = scratch.planId,
            capabilitySealHash = scratch.capabilitySealHash,
            deviceGeneration = scratch.deviceGeneration,
            target = scratch.target,
            staging = scratch.staging,
            targetBounds = scratch.targetBounds,
            packetIds = scratch.packetIds,
            commandIds = scratch.commandIds,
            structuralPipelineKey = forgedStructuralKey,
            uniformPlan = scratch.uniformPlan,
            maxBufferSize = scratch.maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = scratch.maxDynamicUniformBuffersPerPipelineLayout,
            vertexBytes = scratch.vertexBytes,
            indexBytes = scratch.indexBytes,
            poolCapacities = scratch.poolCapacities,
        )
        val forgedPackets = render.drawPackets.map { packet ->
            val authority = requireNotNull(packet.corePrimitivePreparedAuthority)
            copyPacket(packet, packet.targetStateHash).attachCorePrimitivePreparedAuthority(
                authority.copy(
                    structuralPipelineKey = forgedStructuralKey,
                    w3SessionScratch = forgedScratch,
                ),
            )
        }

        listOf(
            assemble(memory = forgedBufferLimit),
            assemble(memory = forgedAlignment),
            assemble(packets = forgedPackets),
        ).forEach { result ->
            assertEquals(
                "w3.lowering.incompatible_plan",
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `public graph keeps original scene command index while packet ordering stays compact`() {
        val scene = SceneSnapshot.of(
            SceneExtent(2, 2),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Annotation.of(RectF32(0f, 0f, 2f, 2f), "origin", "test"),
                SceneCommand.SetTransform(Matrix3x3F32.Identity),
                SceneCommand.SetClip(ClipStackNode.Empty),
                SceneCommand.Draw(
                    DrawNode(
                        GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
                        MaterialNode.Solid(ColorARGB.fromPackedUInt(0xFF4080C0u)),
                        CoverageRequest.HARD_EDGE,
                        ClipStackNode.Empty,
                        BlendNode.SrcOver,
                        EffectStack.Empty,
                        Matrix3x3F32.Identity,
                        DrawOrigin.RECT,
                    ),
                ),
            ),
        )
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            W3SolidRectPlanCompiler().plan(
                scene,
                RenderTargetDescriptor(scene.extent, ColorSpace.SRGB),
                PlanCapabilitySnapshot.of(7, 2048, 1L shl 20, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
                PlanBudget(1024),
            ),
        ).plan

        val packet = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph))).taskList
            .tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        assertEquals(3, packet.commandIdValue)
        assertEquals(0, packet.originalPaintOrder)
        assertEquals(0L, packet.sortKey)
    }

    @Test
    fun `public graph whose I32 bounds cannot round trip through F32 is refused`() {
        val width = 16_777_217
        val graph = graph(
            extent = SizeI32(width, 1),
            targetByteSize = width.toLong() * 4L,
            readbackBytesPerRow = 67_109_120L,
            budget = PlanBudget(256L shl 20),
            capabilities = PlanCapabilitySnapshot.of(7, width, 256L shl 20, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
            drawBounds = RectI32(0, 0, width, 1),
        )

        val refusal = assertIs<GpuPlanLoweringResult.InvalidPlan>(
            lowerer.lower(
                validRequest(
                    graph,
                    currentBudget = graph.budget,
                    rendererCapabilities = capabilities(width.toLong(), 256L shl 20),
                ),
            ),
        )

        assertEquals("w3.lowering.incompatible_plan", refusal.diagnostic.code.value)
    }

    @Test
    fun `public graph at Int MAX I32 boundary is refused without saturating`() {
        val width = Int.MAX_VALUE
        val bytesPerRow = 8_589_934_592L
        val graph = graph(
            extent = SizeI32(width, 1),
            targetByteSize = width.toLong() * 4L,
            readbackBytesPerRow = bytesPerRow,
            budget = PlanBudget(32L shl 30),
            capabilities = PlanCapabilitySnapshot.of(7, width, 32L shl 30, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
            drawBounds = RectI32(0, 0, width, 1),
        )

        val refusal = assertIs<GpuPlanLoweringResult.InvalidPlan>(
            lowerer.lower(
                validRequest(
                    graph = graph,
                    currentBudget = graph.budget,
                    rendererCapabilities = capabilities(width.toLong(), 32L shl 30),
                ),
            ),
        )

        assertEquals("w3.lowering.incompatible_plan", refusal.diagnostic.code.value)
    }

    @Test
    fun `coherently re-signed blend semantic payload is refused by the W3 lowering envelope`() {
        val graph = graph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph))).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val original = render.drawPackets.single()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(original.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.Rect>(semantic.geometry)
        val reSigned = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                original.commandIdValue,
                GPUCorePrimitiveSourceFamily.Rect,
                GPUCorePrimitiveGeometryInput.Rect(geometry.left, geometry.top, geometry.right, geometry.bottom),
                semantic.premultipliedRgba,
                semantic.targetBounds,
                semantic.scissorBounds,
                semantic.clipCoveragePlan,
                semantic.clipExecutionPlanIdentity,
                "blend.re-signed",
                GPUFrameProvenance.None,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                semantic.analysisRecordId!!,
                semantic.analysisCommandFamily!!,
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                corePrimitiveRectGeometryAuthority(GPURect(geometry.left, geometry.top, geometry.right, geometry.bottom), GPUTransformFacts.identity()),
            ),
        )
        val altered = GPUTask.Render(
            render.taskId, render.recordingId, render.phase, render.target, render.loadStore, render.samplePlan,
            render.resourceUses, render.provisionalSegmentKey, listOf(copyPacket(original, original.targetStateHash, semanticPayload = reSigned)),
            render.batchEligibilityByPacketId, render.sampleContinuationKey, render.compositeMembership,
            render.depthStencilLoadStore, render.preparedImageBindingsByPacketId, render.preparedTextBindingsByPacketId,
        )
        val base = GPUTaskList(
            lowered.frameId, lowered.capabilitySeal, lowered.recordingSeals, lowered.expectedReplayKeyHash,
            listOf(altered), emptyList(), lowered.phaseOrder, lowered.memoryBudget, lowered.diagnostics,
        )

        val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(
                graph.id, base, readback.source,
                assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                prepare.requests[0], readback.staging, prepare.requests[1], readback.request,
                lowered.memoryBudget, graph.passes()[0].id, graph.passes()[1].id,
            ),
        )

        assertEquals("w3.lowering.incompatible_plan", assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value)
    }

    @Test
    fun `ready W3 graph lowers to a frame accepted by GPUFramePlanner`() {
        val graph = graph()
        assertEquals(0, assertIs<PlanPass.ReadbackPass>(graph.passes().last()).ordinal)
        val lowered = lowerer.lower(validRequest(graph = graph))

        val taskList = assertIs<GpuPlanLoweringResult.Lowered>(lowered, lowered.toString()).taskList
        assertIs<GPUTask.PrepareResources>(taskList.tasks[0])
        assertIs<GPUTask.Render>(taskList.tasks[1])
        assertIs<GPUTask.Readback>(taskList.tasks[2])
        val framePlan = GPUFramePlanner.plan(taskList)
        assertFalse(framePlan.atomicallyRefused)
        assertFalse(framePlan.steps.isEmpty())
    }

    @Test
    fun `lowered W3 frame preserves graph resource layout memory and pass dependencies`() {
        val graph = graph()
        val taskList = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph = graph)),
        ).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(taskList.tasks[0])
        val render = assertIs<GPUTask.Render>(taskList.tasks[1])
        val readback = assertIs<GPUTask.Readback>(taskList.tasks[2])
        val target = assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor)
        val staging = assertIs<GPUFrameBufferDescriptor>(prepare.requests[1].descriptor)

        assertEquals(graph.targetExtent.width, target.logicalBounds.width)
        assertEquals(graph.targetExtent.height, target.logicalBounds.height)
        assertEquals(GPUColorFormat.RGBA8UnormSrgb, target.format)
        assertEquals(1, target.sampleCount)
        assertEquals(512L, staging.byteSize)
        assertEquals(256L, staging.alignmentBytes)
        assertEquals(GPUFrameResourceRole.SceneTarget, prepare.requests[0].role)
        assertEquals(GPUFrameResourceRole.ReadbackStaging, prepare.requests[1].role)
        assertEquals(
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            prepare.requests[0].usages,
        )
        assertEquals(
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            prepare.requests[1].usages,
        )
        assertEquals(GPUFrameResourceLifetime.FrameLocal, prepare.requests[0].lifetime)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, prepare.requests[1].lifetime)
        assertEquals(16L, prepare.requests[0].byteSize)
        assertEquals(512L, prepare.requests[1].byteSize)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.target", prepare.requests[0].diagnosticLabel)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.staging", prepare.requests[1].diagnosticLabel)
        assertEquals(512L, taskList.memoryBudget.peakFrameTransientBytes)
        assertEquals(16L, taskList.memoryBudget.targetResidentBytes)
        assertEquals(1024L, taskList.memoryBudget.configuredAggregateBudgetBytes)
        assertEquals(null, taskList.memoryBudget.diagnostic)
        assertEquals(16L, taskList.memoryBudget.categoryTotals[GPUFrameMemoryCategory.CanonicalTarget])
        assertEquals(512L, taskList.memoryBudget.categoryTotals[GPUFrameMemoryCategory.ReadbackStaging])
        assertEquals(2, taskList.memoryBudget.allocations.size)
        assertEquals(GPUFrameMemoryResourceKind.Texture2D, taskList.memoryBudget.allocations[0].resourceKind)
        assertEquals(GPUFrameMemoryResourceKind.Buffer, taskList.memoryBudget.allocations[1].resourceKind)
        assertEquals(16L, taskList.memoryBudget.allocations[0].bytes)
        assertEquals(512L, taskList.memoryBudget.allocations[1].bytes)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.target", taskList.memoryBudget.allocations[0].label)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.staging", taskList.memoryBudget.allocations[1].label)
        assertEquals(GPUFrameMemoryCategory.CanonicalTarget, taskList.memoryBudget.allocations[0].category)
        assertEquals(GPUFrameMemoryCategory.ReadbackStaging, taskList.memoryBudget.allocations[1].category)
        assertEquals(target.logicalBounds, taskList.memoryBudget.allocations[0].extent)
        assertEquals(null, taskList.memoryBudget.allocations[1].extent)
        assertEquals(readback.source, render.target)
        assertEquals(GPULoadStorePlan("clear", GPUStorePlan.Store), render.loadStore)
        assertEquals(GPUSamplePlan.SingleSampleFrame, render.samplePlan)
        assertTrue(render.resourceUses.isEmpty())
        assertEquals(null, render.sampleContinuationKey)
        assertEquals(null, render.compositeMembership)
        assertEquals(null, render.depthStencilLoadStore)
        val packet = render.drawPackets.single()
        val authority = requireNotNull(packet.corePrimitivePreparedAuthority)
        val scratch = requireNotNull(authority.w3SessionScratch)
        assertEquals(graph.id.value, scratch.planId)
        assertEquals(render.target, scratch.target)
        assertEquals(readback.staging, scratch.staging)
        assertEquals(target.logicalBounds, scratch.targetBounds)
        assertEquals(listOf(packet.packetId), scratch.packetIds)
        assertEquals(listOf(packet.commandIdValue), scratch.commandIds)
        assertEquals(authority.structuralPipelineKey, scratch.structuralPipelineKey)
        assertEquals(32L, scratch.vertexBytes)
        assertEquals(24L, scratch.indexBytes)
        assertEquals(1, scratch.uniformPlan.slots.size)
        assertEquals(32L, scratch.uniformPlan.slots.single().payloadBytes)
        assertEquals(256L, scratch.uniformPlan.alignmentBytes)
        assertTrue(
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.ReusableScratch) == 0L,
        )
        assertEquals("packet.w3.0", packet.packetId.value)
        assertEquals("pass.w3.main", packet.passId)
        assertEquals("core-primitive-device-geometry", packet.vertexSourceLabel)
        assertEquals(1, packet.renderStepVersion)
        assertEquals(0, packet.commandIdValue)
        assertEquals(0, packet.originalPaintOrder)
        assertEquals(0L, packet.sortKey)
        assertEquals("scissor_0.0_0.0_2.0_2.0", packet.scissorBoundsHash)
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.Rect>(semantic.geometry)
        assertEquals(0f, geometry.left)
        assertEquals(0f, geometry.top)
        assertEquals(2f, geometry.right)
        assertEquals(2f, geometry.bottom)
        val material = assertIs<GPUCorePrimitiveMaterialPayload.SolidColor>(semantic.material)
        assertEquals(listOf(0.051269468f, 0.21586053f, 0.5271152f, 1f), semantic.premultipliedRgba)
        assertEquals(semantic.premultipliedRgba, material.premultipliedRgba)
        assertEquals(packet.blendPlan?.canonicalIdentity(), semantic.blendPlanIdentity)
        assertEquals(GPUCorePrimitiveCoverageMode.FullOrScissor, semantic.coverageMode)
        assertEquals(target.logicalBounds, semantic.scissorBounds)
        assertEquals(target.logicalBounds, semantic.targetBounds)
        assertEquals(readback.source, prepare.requests[0].resource)
        assertEquals(readback.staging, prepare.requests[1].resource)
        assertEquals(2, taskList.dependencies.size)
        assertEquals("resource-prepare", taskList.dependencies.first().dependencyKind)
        assertEquals("plan-pass-dependency", taskList.dependencies.last().dependencyKind)
        assertEquals("w3-plan-resource-availability", taskList.dependencies.first().reasonCode)
        assertEquals("w3-plan-render-before-readback", taskList.dependencies.last().reasonCode)
        assertEquals(render.taskId, taskList.dependencies.first().toTaskId)
        assertEquals(render.taskId, taskList.dependencies.last().fromTaskId)
        assertEquals(readback.taskId, taskList.dependencies.last().toTaskId)
        assertEquals("MainRender:0", taskList.dependencies.last().fromTaskId.value.substringAfter(".render."))
        assertEquals("Readback:0", readback.taskId.value.substringAfter(".readback."))
        assertEquals(0L, readback.request.bufferOffsetBytes)
        assertEquals("w3.${graph.id.value}.readback", readback.request.requestId.value)
        assertEquals("Rgba8Unorm", readback.request.pixelFormat.name)
        assertEquals(target.logicalBounds, readback.request.sourceBounds)
        assertEquals(GPUColorInterpretation.EncodedPremulSrgb, readback.request.outputColorInterpretation)
    }

    @Test
    fun `altered W3 packet envelope is refused before prepared tasks are emitted`() {
        val graph = graph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph = graph)),
        ).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val original = render.drawPackets.single()
        val originalSemantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(original.semanticPayload)
        val forgedRect = GPURect(0f, 0f, 1f, 1f)
        val forgedSemantic = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                original.commandIdValue,
                GPUCorePrimitiveSourceFamily.Rect,
                GPUCorePrimitiveGeometryInput.Rect(
                    forgedRect.left,
                    forgedRect.top,
                    forgedRect.right,
                    forgedRect.bottom,
                ),
                originalSemantic.premultipliedRgba,
                originalSemantic.targetBounds,
                originalSemantic.scissorBounds,
                originalSemantic.clipCoveragePlan,
                originalSemantic.clipExecutionPlanIdentity,
                originalSemantic.blendPlanIdentity,
                GPUFrameProvenance.None,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                "analysis.fill_rect.${original.commandIdValue}",
                "FillRect",
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                corePrimitiveRectGeometryAuthority(forgedRect, GPUTransformFacts.identity()),
            ),
        )
        val canonicalOrderRect = GPURect(0f, 0f, 2f, 2f)
        val canonicalOrderSemantic = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                1,
                GPUCorePrimitiveSourceFamily.Rect,
                GPUCorePrimitiveGeometryInput.Rect(
                    canonicalOrderRect.left,
                    canonicalOrderRect.top,
                    canonicalOrderRect.right,
                    canonicalOrderRect.bottom,
                ),
                originalSemantic.premultipliedRgba,
                originalSemantic.targetBounds,
                originalSemantic.scissorBounds,
                originalSemantic.clipCoveragePlan,
                originalSemantic.clipExecutionPlanIdentity,
                originalSemantic.blendPlanIdentity,
                GPUFrameProvenance.None,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                "analysis.fill_rect.1",
                "FillRect",
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                corePrimitiveRectGeometryAuthority(canonicalOrderRect, GPUTransformFacts.identity()),
            ),
        )
        val alteredPackets = listOf(
            copyPacket(
                original,
                targetStateHash = original.targetStateHash,
                semanticPayload = forgedSemantic,
            ),
            copyPacket(
                original,
                targetStateHash = original.targetStateHash,
                packetId = GPUDrawPacketID("packet.w3.1"),
                commandIdValue = 1,
                analysisRecordId = "w3.1",
                bindingListId = "binding.w3.1",
                semanticPayload = canonicalOrderSemantic,
                sortKey = 1L,
                sortKeyPreimage = "paint-order:1",
                originalPaintOrder = 1,
            ),
        )
        alteredPackets.forEach { altered ->
            val alteredRender = GPUTask.Render(
            render.taskId,
            render.recordingId,
            render.phase,
            render.target,
            render.loadStore,
            render.samplePlan,
            render.resourceUses,
            render.provisionalSegmentKey,
            listOf(altered),
            mapOf(
                altered.packetId to render.batchEligibilityByPacketId.getValue(original.packetId),
            ),
            render.sampleContinuationKey,
            render.compositeMembership,
            render.depthStencilLoadStore,
            render.preparedImageBindingsByPacketId,
            render.preparedTextBindingsByPacketId,
        )
            val base = GPUTaskList(
            lowered.frameId,
            lowered.capabilitySeal,
            lowered.recordingSeals,
            lowered.expectedReplayKeyHash,
            listOf(alteredRender),
            emptyList(),
            lowered.phaseOrder,
            lowered.memoryBudget,
            lowered.diagnostics,
        )

            val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(
                graph.id,
                base,
                readback.source,
                assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                prepare.requests[0],
                readback.staging,
                prepare.requests[1],
                readback.request,
                lowered.memoryBudget,
                graph.passes()[0].id,
                graph.passes()[1].id,
            ),
        )

            assertEquals(
                "w3.lowering.incompatible_plan",
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `W3 base list with composite commands is refused before prepared tasks are emitted`() {
        val graph = graph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph = graph)),
        ).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val base = GPUTaskList(
            lowered.frameId,
            lowered.capabilitySeal,
            lowered.recordingSeals,
            lowered.expectedReplayKeyHash,
            listOf(render),
            emptyList(),
            lowered.phaseOrder,
            lowered.memoryBudget,
            lowered.diagnostics,
            listOf(GPUPassCommand.BeginRenderPass("forged-composite", "clear/store")),
        )

        val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(
                graph.id,
                base,
                readback.source,
                assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                prepare.requests[0],
                readback.staging,
                prepare.requests[1],
                readback.request,
                lowered.memoryBudget,
                graph.passes()[0].id,
                graph.passes()[1].id,
            ),
        )

        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `unsupported planned decisions are rejected instead of replanned`() {
        val result = lowerer.lower(validRequest(graph = graph(capabilityId = "other-capability")))

        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GpuPlanLoweringResult.InvalidPlan>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `stale graph generation is an unsupported capability`() {
        val result = lowerer.lower(validRequest(deviceGeneration = GPUDeviceGenerationID(8)))

        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(result)
    }

    @Test
    fun `current budget divergence is an invalid plan`() {
        val result = lowerer.lower(validRequest(currentBudget = PlanBudget(2048)))

        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GpuPlanLoweringResult.InvalidPlan>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `W3 public graph with a wrong readback ordinal is refused`() {
        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GpuPlanLoweringResult.InvalidPlan>(
                lowerer.lower(validRequest(graph = graph(readbackOrdinal = 1))),
            ).diagnostic.code.value,
        )
    }

    @Test
    fun `W3 public graph divergence table is refused without replanning`() {
        val cases = listOf(
            "pass" to graph(readbackOrdinal = 1),
            "resource" to graph(
                targetUsages = setOf(
                    PlanResourceUsage.RenderAttachment,
                    PlanResourceUsage.CopySource,
                    PlanResourceUsage.MapRead,
                ),
            ),
            "resource-id" to graph(targetOrdinal = 1),
            "lifetime" to graph(stagingFirstPassIndex = 0),
            "cost" to graph(targetByteSize = 32L),
            "dependency" to graph(includeDependency = false),
            "layout" to graph(readbackBytesPerRow = 512L, budget = PlanBudget(2048)),
        )

        cases.forEach { (kind, divergentGraph) ->
            val result = lowerer.lower(
                validRequest(graph = divergentGraph, currentBudget = divergentGraph.budget),
            )
            val refusal = assertIs<GpuPlanLoweringResult.InvalidPlan>(result, kind)
            assertEquals("w3.lowering.incompatible_plan", refusal.diagnostic.code.value, kind)
        }
    }

    private fun validRequest(
        graph: RenderGraph = graph(),
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
        currentBudget: PlanBudget = PlanBudget(1024),
        rendererCapabilities: GPUCapabilities = capabilities(),
    ) = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = rendererCapabilities,
        deviceGeneration = deviceGeneration,
        currentBudget = currentBudget,
        frameId = GPUFrameID(3),
        recordingId = GPURecordingID("w3-lowering"),
    )

    private fun graph(
        capabilityId: String = "solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1",
        extent: SizeI32 = SizeI32(2, 2),
        capabilities: PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
            deviceGeneration = 7,
            maxTextureDimension2D = 2048,
            maxBufferSizeBytes = 1L shl 20,
            copyBytesPerRowAlignment = 256,
            supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        ),
        drawBounds: RectI32 = RectI32(0, 0, 2, 2),
        readbackOrdinal: Int? = null,
        targetOrdinal: Int = 0,
        targetByteSize: Long = 16L,
        targetUsages: Set<PlanResourceUsage> = setOf(
            PlanResourceUsage.RenderAttachment,
            PlanResourceUsage.CopySource,
        ),
        stagingFirstPassIndex: Int = 1,
        readbackBytesPerRow: Long = 256L,
        includeDependency: Boolean = true,
        budget: PlanBudget = PlanBudget(1024),
    ): RenderGraph {
        if (capabilityId == W3SolidRectPlanCompiler.CAPABILITY_ID && extent == SizeI32(2, 2) &&
            capabilities == PlanCapabilitySnapshot.of(7, 2048, 1L shl 20, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)) &&
            drawBounds == RectI32(0, 0, 2, 2) && readbackOrdinal == null &&
            targetOrdinal == 0 && targetByteSize == 16L &&
            targetUsages == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource) &&
            stagingFirstPassIndex == 1 && readbackBytesPerRow == 256L && includeDependency &&
            budget == PlanBudget(1024)
        ) {
            val scene = SceneSnapshot.of(
                SceneExtent(2, 2),
                ColorSpace.SRGB,
                listOf(
                    SceneCommand.Draw(
                        DrawNode(
                            geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
                            material = MaterialNode.Solid(ColorARGB.fromPackedUInt(0xFF4080C0u)),
                            coverage = CoverageRequest.HARD_EDGE,
                            clip = ClipStackNode.Empty,
                            blend = BlendNode.SrcOver,
                            effects = EffectStack.Empty,
                            transform = Matrix3x3F32.Identity,
                            origin = DrawOrigin.RECT,
                        ),
                    ),
                ),
            )
            return assertIs<RenderPlanResult.Ready<RenderGraph>>(
                W3SolidRectPlanCompiler().plan(
                    scene,
                    RenderTargetDescriptor(scene.extent, ColorSpace.SRGB),
                    PlanCapabilitySnapshot.of(
                        7,
                        2048,
                        1L shl 20,
                        256,
                        setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                    ),
                    PlanBudget(1024),
                ),
            ).plan
        }
        val target = PlanResource.of(
            PlanResourceRole.LogicalTarget, targetOrdinal, PlanResourceKind.Texture2D,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, extent, targetByteSize,
            targetUsages,
            PlanResourceLifetime.FrameLocal, 0, 2,
        )
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, readbackBytesPerRow * extent.height.toLong(),
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal, stagingFirstPassIndex, 2,
        )
        val render = PlanPass.RenderPass(
            0,
            target.id,
            listOf(
                SolidRectDraw.of(
                    commandIndex = 0,
                    color = ColorF32.of(0.25f, 0.5f, 0.75f, 1f),
                    visibleBounds = drawBounds,
                    scissor = drawBounds,
                    coverage = CoveragePlan.FullOrScissor,
                    sample = SamplePlan.SingleSample,
                    blend = BlendPlan.SrcOver,
                ),
            ),
            AttachmentLoadPlan.ClearTransparent,
            AttachmentStorePlan.Store,
        )
        val readback = PlanPass.ReadbackPass(readbackOrdinal ?: 0, target.id, staging.id, readbackBytesPerRow)
        return RenderGraph.of(
            PlanId("w3-plan"),
            capabilityId,
            extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            capabilities,
            budget,
            1,
            listOf(target, staging),
            listOf(render, readback),
            if (includeDependency) listOf(PlanPassDependency(render.id, readback.id)) else emptyList(),
            targetByteSize + readbackBytesPerRow * extent.height.toLong(),
        )
    }

    private fun multiDrawGraph(
        drawCount: Int = 2,
        maxBufferSize: Long = 1L shl 20,
    ): RenderGraph {
        require(drawCount in 1..512)
        val scene = SceneSnapshot.of(
            SceneExtent(2, 1),
            ColorSpace.SRGB,
            List(drawCount) { index ->
                SceneCommand.Draw(
                    DrawNode(
                        GeometryNode.Rect.of(
                            RectF32(
                                if (index % 2 == 0) 0f else 1f,
                                0f,
                                if (index % 2 == 0) 1f else 2f,
                                1f,
                            ),
                        ),
                        MaterialNode.Solid(
                            if (index % 2 == 0) ColorARGB.fromPackedUInt(0xFFFF0000u)
                            else ColorARGB.fromPackedUInt(0xFF0000FFu),
                        ),
                        CoverageRequest.HARD_EDGE,
                        ClipStackNode.Empty,
                        BlendNode.SrcOver,
                        EffectStack.Empty,
                        Matrix3x3F32.Identity,
                        DrawOrigin.RECT,
                    ),
                )
            },
        )
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            W3SolidRectPlanCompiler().plan(
                scene,
                RenderTargetDescriptor(scene.extent, ColorSpace.SRGB),
                PlanCapabilitySnapshot.of(
                    deviceGeneration = 7,
                    maxTextureDimension2D = 2048,
                    maxBufferSizeBytes = maxBufferSize,
                    copyBytesPerRowAlignment = 256,
                    supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                ),
                PlanBudget(1024),
            ),
        ).plan
    }

    private fun copyPacket(
        source: GPUDrawPacket,
        targetStateHash: String,
        packetId: GPUDrawPacketID = source.packetId,
        commandIdValue: Int = source.commandIdValue,
        analysisRecordId: String = source.analysisRecordId,
        bindingListId: String = source.bindingListId,
        scissorBoundsHash: String = source.scissorBoundsHash ?: error("W3 packet must carry a scissor authority"),
        semanticPayload: GPUDrawSemanticPayload? = source.semanticPayload,
        sortKey: Long = source.sortKey,
        sortKeyPreimage: String = source.sortKeyPreimage,
        originalPaintOrder: Int = source.originalPaintOrder,
        clipExecutionPlan: org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan? =
            source.clipExecutionPlan,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId,
        commandIdValue,
        analysisRecordId,
        source.passId,
        source.layerId,
        bindingListId,
        source.insertionReasonCode,
        sortKey,
        sortKeyPreimage,
        source.renderStepId,
        source.renderStepVersion,
        source.role,
        source.blendPlan,
        source.renderPipelineKey,
        source.computePipelineKey,
        source.bindingLayoutHash,
        semanticPayload?.payloadRef?.uniformSlot ?: source.uniformSlot,
        source.resourceSlot,
        semanticPayload,
        source.vertexSourceLabel,
        scissorBoundsHash,
        targetStateHash,
        originalPaintOrder,
        source.resourceGeneration,
        source.frameProvenance,
        source.clipCoveragePlan,
        clipExecutionPlan,
        source.diagnostics,
        source.clipProducerAuthority,
    )

    private fun capabilities(
        maxTextureDimension2D: Long = 2048L,
        maxBufferSize: Long = 1L shl 20,
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "test", "supported", true, "w3"),
            GPUCapabilityFact("first_slice.scissor.native", "test", "supported", true, "w3"),
        ),
        snapshotId = "w3-test",
        limits = GPULimits(
            maxTextureDimension2D,
            256,
            256,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.RGBA8UnormSrgb),
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1),
                ),
            ),
        ),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
