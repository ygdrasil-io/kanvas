package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitivePreparedFrameTaskListBuilderTest {
    @Test
    fun `clip resources have explicit mask and depth stencil roles`() {
        assertEquals("ClipMask", GPUFrameResourceRole.ClipMask.name)
        assertEquals("ClipDepthStencil", GPUFrameResourceRole.ClipDepthStencil.name)
    }

    @Test
    fun `refused base task remains refused with its original diagnostic`() {
        val base = recording(
            command(
                commandId = 8,
                paintOrder = 0,
                clip = GPUClipFacts.complexStack(GPUBounds(0f, 0f, 16f, 16f)),
            ),
        ).taskList
        val original = assertIs<GPUTask.Refused>(base.tasks.single()).diagnostic

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, emptyMap()).copy(
                targetBounds = GPUPixelBounds(1, 1, 1, 1),
                configuredAggregateBudgetBytes = 0,
            ),
        )

        val refused = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result)
        assertSame(original, refused.diagnostic)
        assertEquals("unsupported.clip.complex_stack", refused.diagnostic.code.value)
    }

    @Test
    fun `prepared packets preserve exact base ordering blend provenance and clip authorities`() {
        val base = recording(
            command(9, 7, GPUFrameProvenance.HarnessBackground, GPUClipCoveragePlan.NoClip),
            command(4, 2, GPUFrameProvenance.GmContent, scissor()),
        ).taskList.withClipPlans(
            mapOf(
                9 to GPUClipExecutionPlan.NoClip,
                4 to GPUClipExecutionPlan.ScissorOnly(targetBounds),
            ),
        )
        val basePackets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = basePackets.associate { packet -> packet.commandIdValue to semantic(packet) }

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(request(base, semantics)),
        ).taskList
        val prepared = taskList.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        assertEquals(basePackets.map { it.commandIdValue }, prepared.map { it.commandIdValue })
        assertEquals(basePackets.map { it.sortKey }, prepared.map { it.sortKey })
        assertEquals(basePackets.map { it.originalPaintOrder }, prepared.map { it.originalPaintOrder })
        assertEquals(basePackets.map { it.blendPlan }, prepared.map { it.blendPlan })
        assertEquals(basePackets.map { it.frameProvenance }, prepared.map { it.frameProvenance })
        assertEquals(basePackets.map { it.clipCoveragePlan }, prepared.map { it.clipCoveragePlan })
    }

    @Test
    fun `legacy mask without classified execution plan remains refused`() {
        val mask = GPUClipCoveragePlan.Mask(
            contentKey = "clip.mask.pending-b2",
            width = 16,
            height = 16,
            sampleCount = 1,
            resolvedBytes = 256,
            requiredBytes = 256,
            elements = listOf(
                GPUClipCoverageElement(
                    operation = GPUClipCoverageOperation.Intersect,
                    kind = GPUClipCoverageElementKind.Rect,
                    values = listOf(1f, 1f, 12f, 12f),
                    vertexCount = 0,
                    antiAlias = true,
                    fillRule = GPUClipFillRule.Winding,
                    inverseFill = false,
                ),
            ),
        )
        val base = recording(command(12, 0, GPUFrameProvenance.GmContent, mask)).taskList
        val packet = base.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, mapOf(packet.commandIdValue to semantic(packet))),
        )

        assertEquals(
            "invalid.recording.core_primitive_clip_execution_plan_missing",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `scissor and analytic execution plans add no clip producer or resource`() {
        val plans = listOf(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(0, 0, 12, 12)),
            GPUClipExecutionPlan.AnalyticCoverage(
                GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
                GPUPixelBounds(0, 0, 16, 16),
                true,
            ),
        )

        plans.forEachIndexed { index, plan ->
            val base = recording(command(20 + index, index)).taskList.withClipPlans(
                mapOf(20 + index to plan),
            )
            val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
            val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
                GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                    request(base, packets.associate { it.commandIdValue to semantic(it) }),
                ),
            ).taskList

            assertFalse(taskList.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets).any {
                it.role == GPUDrawPacketRole.ClipProducer || it.role == GPUDrawPacketRole.StencilProducer
            })
            assertFalse(taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().flatMap { it.requests }.any {
                it.role == GPUFrameResourceRole.ClipMask || it.role == GPUFrameResourceRole.ClipDepthStencil
            })
        }
    }

    @Test
    fun `stencil execution records one producer resource token and consumer accepted by frame planner`() {
        val plan = stencilPlan()
        val base = recording(command(30, 0)).taskList.withClipPlans(mapOf(30 to plan))
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().flatMap { it.requests }
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.ClipDepthStencil })
        assertEquals(plan.requiredBytes, preparations.single { it.role == GPUFrameResourceRole.ClipDepthStencil }.byteSize)
        val renders = taskList.tasks.filterIsInstance<GPUTask.Render>()
        val producer = renders.single { it.drawPackets.single().role == GPUDrawPacketRole.StencilProducer }
        val consumer = renders.single { it.drawPackets.single().role == GPUDrawPacketRole.Shading }
        assertTrue(taskList.dependencies.any {
            it.fromTaskId == producer.taskId && it.toTaskId == consumer.taskId &&
                it.useToken?.value == plan.orderingToken.value
        })
        assertEquals(
            plan.requiredBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil),
        )
        assertFalse(GPUFramePlanner.plan(taskList).atomicallyRefused)
    }

    @Test
    fun `equal mask plans dedupe ordered producers and retain one consumer per draw paint order`() {
        val plan = maskPlan()
        val base = recording(command(40, 0), command(41, 1)).taskList.withClipPlans(
            mapOf(40 to plan, 41 to plan),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val producers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer }
        assertEquals(2, producers.size)
        assertTrue(producers[0].drawPackets.single().insertionReasonCode.contains("Intersect"))
        assertTrue(producers[1].drawPackets.single().insertionReasonCode.contains("Difference"))
        val consumers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.Shading }
        assertEquals(listOf(0, 1), consumers.map { it.drawPackets.single().originalPaintOrder })
        assertTrue(consumers.all { it.drawPackets.size == 1 })
        val preparations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().flatMap { it.requests }
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.ClipMask })
        assertEquals(
            plan.resolvedBytes,
            taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.ReusableScratch),
        )
        assertFalse(GPUFramePlanner.plan(taskList).atomicallyRefused)
    }

    @Test
    fun `same clip key with a different full plan refuses before budget materialization`() {
        val first = maskPlan()
        val second = maskPlan(differenceAntiAlias = false)
        val base = recording(command(50, 0), command(51, 1)).taskList.withClipPlans(
            mapOf(50 to first, 51 to second),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            request(base, packets.associate { it.commandIdValue to semantic(it) }).copy(
                configuredAggregateBudgetBytes = 1,
            ),
        )

        assertEquals(
            "invalid.recording.core_primitive_clip_content_key_collision",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `canonical clip identities keep colliding sanitized content keys distinct`() {
        val slashPlan = maskPlan(contentKey = "clip.a/b")
        val questionPlan = maskPlan(contentKey = "clip.a?b")
        val base = recording(command(60, 0), command(61, 1)).taskList.withClipPlans(
            mapOf(60 to slashPlan, 61 to questionPlan),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList

        val producerIds = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer }
            .map { it.taskId }
        assertEquals(4, producerIds.size)
        assertEquals(producerIds.size, producerIds.distinct().size)
        val clipResources = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap { it.requests }
            .filter { it.role == GPUFrameResourceRole.ClipMask }
            .map { it.resource }
        assertEquals(2, clipResources.size)
        assertEquals(clipResources.size, clipResources.distinct().size)
    }

    @Test
    fun `every clip producer packet carries its exact typed producer authority`() {
        val stencil = stencilPlan()
        val mask = maskPlan()
        val base = recording(command(70, 0), command(71, 1)).taskList.withClipPlans(
            mapOf(70 to stencil, 71 to mask),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val producerPackets = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .map { it.drawPackets.single() }
            .filter { it.role == GPUDrawPacketRole.StencilProducer || it.role == GPUDrawPacketRole.ClipProducer }
        val stencilAuthority = assertIs<GPUClipProducerAuthority.Stencil>(
            producerPackets.single { it.role == GPUDrawPacketRole.StencilProducer }.clipProducerAuthority,
        )
        assertSame(stencil.producer, stencilAuthority.producer)
        val maskAuthorities = producerPackets.filter { it.role == GPUDrawPacketRole.ClipProducer }
            .map { packet -> assertIs<GPUClipProducerAuthority.Mask>(packet.clipProducerAuthority) }
        assertEquals(mask.producers, maskAuthorities.map(GPUClipProducerAuthority.Mask::producer))
    }

    @Test
    fun `base DAG translation preserves dependency authority and only replaces task ids`() {
        val base = recording(command(80, 0), command(81, 1)).taskList.withClipPlans(
            mapOf(80 to GPUClipExecutionPlan.NoClip, 81 to GPUClipExecutionPlan.NoClip),
        )
        val source = base.dependencies.single()
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)

        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                request(base, packets.associate { it.commandIdValue to semantic(it) }),
            ),
        ).taskList
        val translatedCandidates = taskList.dependencies.filter { dependency ->
            dependency.fromTaskId.value == "${source.fromTaskId.value}.core-consumer.0" &&
                dependency.toTaskId.value == "${source.toTaskId.value}.core-consumer.0"
        }
        assertEquals(1, translatedCandidates.size)
        val translated = translatedCandidates.single()

        assertEquals(source.dependencyKind, translated.dependencyKind)
        assertEquals(source.useToken, translated.useToken)
        assertEquals(source.reasonCode, translated.reasonCode)
    }

    private fun request(
        base: GPUTaskList,
        semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    ) = GPUCorePrimitivePreparedFrameRequest(
        baseTaskList = base,
        capabilities = capabilities(),
        target = GPUFrameTargetRef("target.core.authority"),
        targetBounds = targetBounds,
        semanticsByCommandId = semantics,
    )

    private fun semantic(packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket) =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = targetBounds,
                scissorBounds = targetBounds,
                clipCoveragePlan = requireNotNull(packet.clipCoveragePlan),
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
            ),
        )

    private fun recording(vararg commands: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand) =
        GPURecorder(GPURecordingID("recording.core.authority"), GPUFrameID(91), capabilities()).apply {
            commands.forEach(::record)
        }.close()

    private fun GPUTaskList.withClipPlans(plans: Map<Int, GPUClipExecutionPlan>): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet -> packet.withClipPlan(plans[packet.commandIdValue]) }
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                task.loadStore,
                task.samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                packets,
                packets.associate { packet ->
                    packet.packetId to requireNotNull(task.batchEligibilityByPacketId[packet.packetId])
                },
                task.sampleContinuationKey,
                task.compositeMembership,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUDrawPacket.withClipPlan(plan: GPUClipExecutionPlan?): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, plan, diagnostics,
    )

    private fun stencilPlan() = GPUClipExecutionPlan.StencilCoverage(
        contentKey = "clip.shared.stencil",
        bounds = targetBounds,
        sampleCount = 1,
        atomicGroup = GPUClipAtomicGroupID("atomic.clip.shared.stencil"),
        orderingToken = GPUClipOrderingToken("token.clip.shared.stencil"),
        producer = GPUClipStencilProducerPlan(
            geometry = GPUClipExecutionGeometry.Rect(GPUClipBounds(1f, 1f, 12f, 12f)),
            scissor = targetBounds,
            fillRule = GPUClipFillRule.Winding,
            reference = 1u,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = GPUClipStencilOperation.Replace,
            backPassOperation = GPUClipStencilOperation.Replace,
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        ),
        consumer = GPUClipStencilConsumerPlan(
            scissor = targetBounds,
            reference = 1u,
            compare = GPUClipStencilCompare.Equal,
        ),
    )

    private fun maskPlan(
        differenceAntiAlias: Boolean = true,
        contentKey: String = "clip.shared.mask",
    ) = GPUClipExecutionPlan.CoverageMask(
        contentKey = contentKey,
        bounds = targetBounds,
        sampleCount = 1,
        depthStencilRequired = false,
        orderingToken = GPUClipOrderingToken("token.clip.shared.mask"),
        producers = listOf(
            GPUClipMaskProducerPlan(
                0,
                GPUClipExecutionGeometry.Rect(GPUClipBounds(0f, 0f, 16f, 16f)),
                GPUClipMaskCombine.Intersect,
                true,
            ),
            GPUClipMaskProducerPlan(
                1,
                GPUClipExecutionGeometry.Rect(GPUClipBounds(4f, 4f, 12f, 12f)),
                GPUClipMaskCombine.Difference,
                differenceAntiAlias,
            ),
        ),
        consumer = GPUClipMaskConsumerPlan(),
    )

    private fun command(
        commandId: Int,
        paintOrder: Int,
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        clipPlan: GPUClipCoveragePlan = GPUClipCoveragePlan.NoClip,
    ) = command(
        commandId,
        paintOrder,
        GPUClipFacts(
            kind = when (clipPlan) {
                GPUClipCoveragePlan.NoClip -> GPUClipKind.WideOpen
                is GPUClipCoveragePlan.Scissor -> GPUClipKind.DeviceRect
                is GPUClipCoveragePlan.Mask,
                is GPUClipCoveragePlan.Refused,
                -> GPUClipKind.ComplexStack
            },
            bounds = GPUBounds(0f, 0f, 16f, 16f),
            coveragePlan = clipPlan,
        ),
        provenance,
    )

    private fun command(
        commandId: Int,
        paintOrder: Int,
        clip: GPUClipFacts,
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
    ) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 8f, 8f),
        target = targetFacts,
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        clip = clip,
        paintOrder = paintOrder,
        source = GPUCommandSource("unit-test", "fillRect", provenance),
    )

    private fun scissor() = GPUClipCoveragePlan.Scissor(GPUClipBounds(0f, 0f, 16f, 16f))

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit-test", "supported", true, "core-authority"),
            GPUCapabilityFact("first_slice.scissor.native", "unit-test", "supported", true, "core-authority"),
        ),
        snapshotId = "core-authority",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )

    private companion object {
        val targetBounds = GPUPixelBounds(0, 0, 16, 16)
        val targetFacts = GPUTargetFacts(16, 16, "rgba8unorm")
    }
}
