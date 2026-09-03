package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanPassId
import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchAdjacency
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.W4aSessionScratchDrawV1
import org.graphiks.kanvas.gpu.renderer.passes.W4aSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Complete, already-planned W4a facts consumed by the dedicated analytic Uniform80 envelope. */
internal data class GPUCorePrimitiveW4aPreparedFrameRequest(
    val planId: PlanId,
    val baseTaskList: GPUTaskList,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val targetPreparation: GPUResourcePreparationRequest,
    val staging: GPUFrameBufferRef,
    val stagingPreparation: GPUResourcePreparationRequest,
    val readbackRequest: GPUFrameReadbackRequest,
    val memoryBudget: GPUFrameMemoryBudgetPlan,
    val renderPassId: PlanPassId,
    val readbackPassId: PlanPassId,
    val vertexResourceId: PlanResourceId,
    val indexResourceId: PlanResourceId,
    val uniformResourceId: PlanResourceId,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformStrideBytes: Long,
    val uniformUsefulBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val copyBytesPerRowAlignment: Long,
    val readbackBytesPerRow: Long,
    val maxBufferSize: Long,
    val maxDynamicUniformBuffersPerPipelineLayout: Long,
    val drawSnapshots: List<W4aSessionScratchDrawV1>,
)

/**
 * Closes the W4a RenderGraph into target preparation, its native ScalarAA render pass, and
 * readback. This is intentionally a sibling envelope: it accepts no scene, recognition, or
 * generic routing input and it preserves the graph-owned V/I/U reservation exactly once.
 */
internal class GPUCorePrimitiveW4aPreparedFrameTaskListAssembler {
    fun buildPreplanned(request: GPUCorePrimitiveW4aPreparedFrameRequest): GPUCorePrimitivePreparedFrameResult {
        val render = request.baseTaskList.tasks.singleOrNull() as? GPUTask.Render
            ?: return refused("A preplanned W4a frame requires exactly one render-only base task.")
        if (!hasExactW4aEnvelope(request, render)) {
            return refused("Preplanned W4a frame facts are inconsistent.")
        }

        val sealedPackets = sealPackets(request, render.drawPackets)
            ?: return refused("W4a analytic Uniform80 packet sealing failed.")
        val prefix = "task.w4a.${request.planId.value}"
        val prepareId = GPUTaskID("$prefix.prepare.${request.renderPassId.value}")
        val renderId = GPUTaskID("$prefix.render.${request.renderPassId.value}")
        val readbackId = GPUTaskID("$prefix.readback.${request.readbackPassId.value}")
        val preparedRender = GPUTask.Render(
            taskId = renderId,
            recordingId = render.recordingId,
            phase = GPUTaskPhase.Render,
            target = request.target,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            provisionalSegmentKey = render.provisionalSegmentKey,
            drawPackets = sealedPackets,
            batchEligibilityByPacketId = render.batchEligibilityByPacketId,
            sampleContinuationKey = render.sampleContinuationKey,
            compositeMembership = render.compositeMembership,
            depthStencilLoadStore = render.depthStencilLoadStore,
            preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
            preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
        )
        val tasks = listOf(
            GPUTask.PrepareResources(
                prepareId,
                render.recordingId,
                GPUTaskPhase.Prepare,
                listOf(request.targetPreparation, request.stagingPreparation),
            ),
            preparedRender,
            GPUTask.Readback(
                readbackId,
                render.recordingId,
                GPUTaskPhase.Readback,
                request.target,
                request.staging,
                request.readbackRequest,
            ),
        )
        val dependencies = listOf(
            GPUTaskDependency(
                prepareId,
                renderId,
                "resource-prepare",
                GPUTaskUseToken("w4a.${request.planId.value}.prepare-to-render"),
                "w4a-plan-resource-availability",
            ),
            GPUTaskDependency(
                renderId,
                readbackId,
                "plan-pass-dependency",
                GPUTaskUseToken(
                    "w4a.${request.planId.value}.${request.renderPassId.value}-to-${request.readbackPassId.value}",
                ),
                "w4a-plan-render-before-readback",
            ),
        )
        return GPUCorePrimitivePreparedFrameResult.Recorded(
            GPUTaskList(
                request.baseTaskList.frameId,
                request.baseTaskList.capabilitySeal,
                request.baseTaskList.recordingSeals,
                request.baseTaskList.expectedReplayKeyHash,
                tasks,
                dependencies,
                request.baseTaskList.phaseOrder,
                request.memoryBudget,
                request.baseTaskList.diagnostics,
            ),
        )
    }

    private fun sealPackets(
        request: GPUCorePrimitiveW4aPreparedFrameRequest,
        packets: List<GPUDrawPacket>,
    ): List<GPUDrawPacket>? {
        val sealedPayloads = packets.mapIndexed { index, packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return null
            val authority = GPUCorePrimitivePreparedSemanticAuthority.capture(semantic)
            val bytes = when (val built = buildCorePrimitiveAnalyticShapeUniform(semantic, authority)) {
                is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted -> built.bytes
                is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused -> return null
            }
            if (bytes.size.toLong() != W4aSessionScratchV1.UNIFORM_PAYLOAD_BYTES) return null
            W4aUniformPayload(packet, semantic, authority, bytes, index)
        }
        val slabPayloads = sealedPayloads.map { payload ->
            GPUUniformSlabPayload("analytic-shape-draw-${payload.packet.commandIdValue}", payload.bytes)
        }
        val uniformPlan = when (val planned = GPUUniformSlabPlanner.plan(
            sourceLabel = W4aSessionScratchV1.SOURCE_LABEL,
            deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
            alignmentBytes = request.uniformStrideBytes,
            uploadBudgetBytes = request.uniformCapacityBytes,
            payloads = slabPayloads,
            maxBufferSize = request.maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = request.maxDynamicUniformBuffersPerPipelineLayout,
        )) {
            is GPUUniformSlabPlanningResult.Accepted -> planned.plan
            is GPUUniformSlabPlanningResult.Refused -> return null
        }
        if (uniformPlan.totalBytes != request.uniformUsefulBytes ||
            uniformPlan.alignmentBytes != request.uniformStrideBytes ||
            !uniformPlan.hasExactPayloads(
                W4aSessionScratchV1.SOURCE_LABEL,
                request.baseTaskList.capabilitySeal.deviceGeneration.value,
                request.uniformStrideBytes,
                slabPayloads,
            )
        ) return null

        val first = sealedPayloads.firstOrNull() ?: return null
        val structuralKey = structuralKey(first.packet, first.semantic) ?: return null
        if (sealedPayloads.any { payload -> structuralKey(payload.packet, payload.semantic) != structuralKey }) {
            return null
        }
        val poolCapacities = corePrimitiveFramePoolCapacitiesOrNull(
            request.vertexUsefulBytes,
            request.indexUsefulBytes,
            request.uniformUsefulBytes,
        ) ?: return null
        val scratch = try {
            W4aSessionScratchV1(
                planId = request.planId.value,
                capabilitySealHash = request.baseTaskList.capabilitySeal.sealHash,
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                target = request.target,
                staging = request.staging,
                targetBounds = request.targetBounds,
                vertexResourceId = request.vertexResourceId,
                indexResourceId = request.indexResourceId,
                uniformResourceId = request.uniformResourceId,
                packetIds = packets.map(GPUDrawPacket::packetId),
                commandIds = packets.map(GPUDrawPacket::commandIdValue),
                draws = request.drawSnapshots,
                structuralPipelineKey = structuralKey,
                uniformPlan = uniformPlan,
                uniformStrideBytes = request.uniformStrideBytes,
                vertexUsefulBytes = request.vertexUsefulBytes,
                indexUsefulBytes = request.indexUsefulBytes,
                uniformUsefulBytes = request.uniformUsefulBytes,
                vertexCapacityBytes = request.vertexCapacityBytes,
                indexCapacityBytes = request.indexCapacityBytes,
                uniformCapacityBytes = request.uniformCapacityBytes,
                poolCapacities = poolCapacities,
                maxBufferSize = request.maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout = request.maxDynamicUniformBuffersPerPipelineLayout,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }

        return sealedPayloads.map { payload ->
            val packet = payload.packet
            val publicPipelineKey = packet.renderPipelineKey ?: return null
            val seal = try {
                GPUCorePrimitiveAnalyticShapeUniformSeal(
                    plan = uniformPlan,
                    slotIndex = payload.index,
                    commandId = packet.commandIdValue,
                    packetId = packet.packetId,
                    semanticAuthority = payload.semanticAuthority,
                    renderScissor = payload.semantic.scissorBounds,
                    structuralPipelineKey = structuralKey,
                    renderPipelineKey = publicPipelineKey,
                    bindingLayoutHash = CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH,
                    resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                    payloadBytes = payload.bytes,
                )
            } catch (_: IllegalArgumentException) {
                return null
            }
            try {
                packet.attachCorePrimitivePreparedAuthority(
                    GPUCorePrimitivePreparedPacketAuthority(
                        structuralPipelineKey = structuralKey,
                        renderPipelineKey = publicPipelineKey,
                        uniformSlabSeal = null,
                        analyticShapeUniformSeal = seal,
                        w4aSessionScratch = scratch,
                    ),
                )
            } catch (_: IllegalArgumentException) {
                return null
            }
            packet
        }
    }

    private fun hasExactW4aEnvelope(
        request: GPUCorePrimitiveW4aPreparedFrameRequest,
        render: GPUTask.Render,
    ): Boolean {
        val targetDescriptor = request.targetPreparation.descriptor as? GPUFrameTextureDescriptor ?: return false
        val stagingDescriptor = request.stagingPreparation.descriptor as? GPUFrameBufferDescriptor ?: return false
        val expectedIdentity = w4aSessionIdentity(request)
        val expectedAllocations = listOf(
            GPUFrameMemoryAllocation(
                "$expectedIdentity.target",
                GPUFrameMemoryCategory.CanonicalTarget,
                request.targetPreparation.byteSize,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
            GPUFrameMemoryAllocation(
                "$expectedIdentity.staging",
                GPUFrameMemoryCategory.ReadbackStaging,
                request.stagingPreparation.byteSize,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$expectedIdentity.vertex",
                GPUFrameMemoryCategory.ReusableScratch,
                request.vertexCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$expectedIdentity.index",
                GPUFrameMemoryCategory.ReusableScratch,
                request.indexCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$expectedIdentity.uniform",
                GPUFrameMemoryCategory.ReusableScratch,
                request.uniformCapacityBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
        )
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val transientBytes = try {
            Math.addExact(
                Math.addExact(request.stagingPreparation.byteSize, request.vertexCapacityBytes),
                Math.addExact(request.indexCapacityBytes, request.uniformCapacityBytes),
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val scratchBytes = try {
            Math.addExact(
                request.vertexCapacityBytes,
                Math.addExact(request.indexCapacityBytes, request.uniformCapacityBytes),
            )
        } catch (_: ArithmeticException) {
            return false
        }
        val expectedReadbackBytes = try {
            Math.multiplyExact(request.readbackBytesPerRow, request.targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return false
        }
        val targetRowBytes = try {
            Math.multiplyExact(request.targetBounds.width.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return false
        }
        val expectedReadbackRow = if (
            request.copyBytesPerRowAlignment <= 0L ||
            targetRowBytes % request.copyBytesPerRowAlignment == 0L
        ) {
            targetRowBytes
        } else {
            try {
                Math.addExact(
                    targetRowBytes,
                    request.copyBytesPerRowAlignment - targetRowBytes % request.copyBytesPerRowAlignment,
                )
            } catch (_: ArithmeticException) {
                return false
            }
        }
        return request.baseTaskList.diagnostics.none(GPUDiagnostic::isTerminal) &&
            request.baseTaskList.dependencies.isEmpty() &&
            request.baseTaskList.compositeCommands.isEmpty() &&
            request.baseTaskList.memoryBudget == request.memoryBudget &&
            request.renderPassId.value == "MainRender:0" &&
            request.readbackPassId.value == "Readback:0" &&
            request.targetBounds.left == 0 && request.targetBounds.top == 0 && !request.targetBounds.isEmpty &&
            request.maxBufferSize > 0L && request.maxDynamicUniformBuffersPerPipelineLayout >= 1L &&
            request.vertexUsefulBytes == render.drawPackets.size.toLong() * VERTEX_BYTES_PER_DRAW &&
            request.indexUsefulBytes == render.drawPackets.size.toLong() * INDEX_BYTES_PER_DRAW &&
            request.uniformUsefulBytes == render.drawPackets.size.toLong() * request.uniformStrideBytes &&
            request.uniformStrideBytes > 0L &&
            request.vertexCapacityBytes >= request.vertexUsefulBytes &&
            request.indexCapacityBytes >= request.indexUsefulBytes &&
            request.uniformCapacityBytes >= request.uniformUsefulBytes &&
            request.copyBytesPerRowAlignment > 0L &&
            request.readbackBytesPerRow == expectedReadbackRow &&
            listOf(request.vertexCapacityBytes, request.indexCapacityBytes, request.uniformCapacityBytes)
                .all { bytes -> bytes <= request.maxBufferSize } &&
            request.drawSnapshots.size == render.drawPackets.size &&
            request.drawSnapshots.map(W4aSessionScratchDrawV1::packetId) == render.drawPackets.map(GPUDrawPacket::packetId) &&
            request.drawSnapshots.map(W4aSessionScratchDrawV1::commandId) == render.drawPackets.map(GPUDrawPacket::commandIdValue) &&
            request.drawSnapshots.zip(render.drawPackets).all { (snapshot, packet) ->
                snapshotMatchesPacket(snapshot, packet)
            } &&
            render.target == request.target &&
            render.loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            render.samplePlan == org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame &&
            render.resourceUses.isEmpty() &&
            render.sampleContinuationKey == null && render.compositeMembership == null &&
            render.depthStencilLoadStore == null &&
            render.preparedImageBindingsByPacketId.isEmpty() && render.preparedTextBindingsByPacketId.isEmpty() &&
            render.drawPackets.size in 1..512 &&
            render.drawPackets.map(GPUDrawPacket::packetId).distinct().size == render.drawPackets.size &&
            render.drawPackets.zipWithNext().all { (first, second) -> first.commandIdValue < second.commandIdValue } &&
            render.drawPackets.withIndex().all { (paintOrder, packet) ->
                packet.originalPaintOrder == paintOrder && packet.sortKey == paintOrder.toLong()
            } &&
            render.batchEligibilityByPacketId.keys == render.drawPackets.map(GPUDrawPacket::packetId).toSet() &&
            render.batchEligibilityByPacketId.values.all { eligibility ->
                eligibility.kind == GPUPassBatchKind.SolidFill &&
                    eligibility.adjacency == GPUPassBatchAdjacency.Compatible &&
                    eligibility.queueGuard.requiredRetainedRefs.isEmpty() &&
                    eligibility.queueGuard.retainedRefs.isEmpty()
            } &&
            render.drawPackets.all { packet -> isExactW4aPacket(packet, request.targetBounds) } &&
            request.targetPreparation.resource == request.target &&
            request.targetPreparation.role == GPUFrameResourceRole.SceneTarget &&
            request.targetPreparation.usages == setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) &&
            request.targetPreparation.lifetime == GPUFrameResourceLifetime.FrameLocal &&
            request.targetPreparation.diagnosticLabel == "$expectedIdentity.target" &&
            targetDescriptor.logicalBounds == request.targetBounds &&
            targetDescriptor.format == GPUColorFormat.RGBA8UnormSrgb && targetDescriptor.sampleCount == 1 &&
            request.targetPreparation.byteSize == targetBytes &&
            request.stagingPreparation.resource == request.staging &&
            request.stagingPreparation.role == GPUFrameResourceRole.ReadbackStaging &&
            request.stagingPreparation.usages == setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.MapRead,
            ) &&
            request.stagingPreparation.lifetime == GPUFrameResourceLifetime.FrameLocal &&
            request.stagingPreparation.diagnosticLabel == "$expectedIdentity.staging" &&
            stagingDescriptor.byteSize == request.stagingPreparation.byteSize &&
            stagingDescriptor.alignmentBytes == request.copyBytesPerRowAlignment &&
            request.stagingPreparation.byteSize == expectedReadbackBytes &&
            request.readbackRequest.requestId.value == "w4a.${request.planId.value}.readback" &&
            request.readbackRequest.sourceBounds == request.targetBounds &&
            request.readbackRequest.pixelFormat == GPUReadbackPixelFormat.Rgba8Unorm &&
            request.readbackRequest.outputColorInterpretation == GPUColorInterpretation.EncodedPremulSrgb &&
            request.readbackRequest.bufferOffsetBytes == 0L &&
            request.memoryBudget.diagnostic == null &&
            request.memoryBudget.configuredAggregateBudgetBytes > 0L &&
            request.memoryBudget.categoryTotals.keys == GPUFrameMemoryCategory.entries.toSet() &&
            request.memoryBudget.targetResidentBytes == targetBytes &&
            request.memoryBudget.peakFrameTransientBytes == transientBytes &&
            request.memoryBudget.categoryTotals[GPUFrameMemoryCategory.CanonicalTarget] == targetBytes &&
            request.memoryBudget.categoryTotals[GPUFrameMemoryCategory.ReadbackStaging] == request.stagingPreparation.byteSize &&
            request.memoryBudget.categoryTotals[GPUFrameMemoryCategory.ReusableScratch] ==
            scratchBytes &&
            request.memoryBudget.categoryTotals.filterKeys { category ->
                category !in setOf(
                    GPUFrameMemoryCategory.CanonicalTarget,
                    GPUFrameMemoryCategory.ReadbackStaging,
                    GPUFrameMemoryCategory.ReusableScratch,
                )
            }.values.all { bytes -> bytes == 0L } &&
            request.memoryBudget.allocations == expectedAllocations
    }

    private fun snapshotMatchesPacket(snapshot: W4aSessionScratchDrawV1, packet: GPUDrawPacket): Boolean {
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
        val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.Rect ?: return false
        val device = snapshot.copyDeviceBounds()
        return device.left == geometry.left && device.top == geometry.top &&
            device.right == geometry.right && device.bottom == geometry.bottom &&
            snapshot.copyScissorBounds() == semantic.scissorBounds
    }

    private fun isExactW4aPacket(packet: GPUDrawPacket, targetBounds: GPUPixelBounds): Boolean {
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
        val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.Rect ?: return false
        val clip = packet.clipExecutionPlan ?: return false
        val blend = packet.blendPlan ?: return false
        val expectedStructural = corePrimitiveRenderPipelineStructuralKey(
            semantic,
            clip,
            blend,
            sampleCount = 1,
            colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
        )
        return packet.packetId == GPUDrawPacketID("packet.w4a.${packet.commandIdValue}") &&
            packet.analysisRecordId == semantic.analysisRecordId &&
            packet.passId == "pass.w4a.main" &&
            packet.layerId == "root" &&
            packet.bindingListId == "binding.w4a.${packet.commandIdValue}" &&
            packet.insertionReasonCode == "w4a-analytic-rect" &&
            packet.sortKey == packet.originalPaintOrder.toLong() &&
            packet.sortKeyPreimage == "paint-order:${packet.originalPaintOrder}" &&
            packet.renderStepId.value == CORE_PRIMITIVE_RENDER_STEP_IDENTITY &&
            packet.renderStepVersion == 1 &&
            packet.role == GPUDrawPacketRole.Shading &&
            blend.canonicalIdentity() == canonicalSolidRectSrcOverBlendPlan().canonicalIdentity() &&
            packet.renderPipelineKey == expectedStructural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY) &&
            packet.computePipelineKey == null &&
            packet.bindingLayoutHash == CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH &&
            packet.uniformSlot == semantic.payloadRef.uniformSlot &&
            packet.resourceSlot == null &&
            packet.vertexSourceLabel == CORE_PRIMITIVE_VERTEX_SOURCE_LABEL &&
            packet.scissorBoundsHash == corePrimitiveScissorAuthority(semantic.scissorBounds) &&
            packet.targetStateHash == corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb) &&
            packet.resourceGeneration == PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION &&
            packet.frameProvenance == GPUFrameProvenance.None &&
            packet.diagnostics.isEmpty() &&
            packet.clipProducerAuthority == null &&
            packet.corePrimitivePreparedAuthority == null &&
            semantic.sourceFamily == GPUCorePrimitiveSourceFamily.Rect &&
            semantic.rectRouteAuthority == GPUCorePrimitiveRectRouteAuthority.RectAxisAligned &&
            semantic.rectGeometryAuthority != null && semantic.rrectGeometryAuthority == null &&
            semantic.material is GPUCorePrimitiveMaterialPayload.SolidColor &&
            semantic.targetBounds == targetBounds &&
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA &&
            semantic.blendPlanIdentity == canonicalSolidRectSrcOverBlendPlan().canonicalIdentity() &&
            semantic.analysisRecordId == "analysis.fill_rect.${packet.commandIdValue}" &&
            semantic.analysisCommandFamily == "FillRect" &&
            semantic.payloadRef.commandIdValue == packet.commandIdValue &&
            semantic.frameProvenance == GPUFrameProvenance.None &&
            geometry.left < geometry.right && geometry.top < geometry.bottom &&
            semantic.hasStructuralIntegrity() && semantic.hasCanonicalHashIntegrity() &&
            when (val coverage = packet.clipCoveragePlan) {
                GPUClipCoveragePlan.NoClip ->
                    semantic.clipCoveragePlan == GPUClipCoveragePlan.NoClip &&
                        semantic.scissorBounds == targetBounds &&
                        clip == GPUClipExecutionPlan.NoClip &&
                        semantic.clipExecutionPlanIdentity == GPUClipExecutionPlan.NoClip.canonicalIdentity()
                is GPUClipCoveragePlan.Scissor -> {
                    val execution = clip as? GPUClipExecutionPlan.ScissorOnly ?: return false
                    semantic.clipCoveragePlan == coverage &&
                        semantic.scissorBounds == execution.scissor &&
                        coverage.bounds.left == execution.scissor.left.toFloat() &&
                        coverage.bounds.top == execution.scissor.top.toFloat() &&
                        coverage.bounds.right == execution.scissor.right.toFloat() &&
                        coverage.bounds.bottom == execution.scissor.bottom.toFloat() &&
                        semantic.clipExecutionPlanIdentity == execution.canonicalIdentity()
                }
                else -> false
            }
    }

    private fun structuralKey(
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
    ): GPUCorePrimitiveRenderPipelineStructuralKey? {
        val clip = packet.clipExecutionPlan ?: return null
        val blend = packet.blendPlan ?: return null
        val key = corePrimitiveRenderPipelineStructuralKey(
            semantic,
            clip,
            blend,
            sampleCount = 1,
            colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
        )
        return key.takeIf { candidate ->
            packet.renderPipelineKey == candidate.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        }
    }

    private fun w4aSessionIdentity(request: GPUCorePrimitiveW4aPreparedFrameRequest): String =
        "w4a.session.${request.baseTaskList.capabilitySeal.deviceGeneration.value}." +
            "${request.targetBounds.width}x${request.targetBounds.height}.rgba8unorm-srgb"

    private fun refused(message: String): GPUCorePrimitivePreparedFrameResult.Refused =
        GPUCorePrimitivePreparedFrameResult.Refused(
            GPUDiagnostic(
                GPUDiagnosticCode("w4a.lowering.incompatible_plan"),
                GPUDiagnosticDomain.Recording,
                GPUDiagnosticSeverity.Error,
                message,
            ),
        )

    private data class W4aUniformPayload(
        val packet: GPUDrawPacket,
        val semantic: GPUDrawSemanticPayload.CorePrimitive,
        val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
        val bytes: ByteArray,
        val index: Int,
    )

    private companion object {
        const val VERTEX_BYTES_PER_DRAW: Long = 32L
        const val INDEX_BYTES_PER_DRAW: Long = 24L
    }
}
