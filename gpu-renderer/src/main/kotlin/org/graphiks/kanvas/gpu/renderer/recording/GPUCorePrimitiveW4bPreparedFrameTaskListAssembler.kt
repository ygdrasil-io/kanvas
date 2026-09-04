package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanPassId
import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchAdjacency
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.W4bSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Complete W4b graph facts sealed by Task 4 and closed into a native frame envelope here. */
internal data class GPUCorePrimitiveW4bPreparedFrameRequest(
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
    val copyBytesPerRowAlignment: Long,
    val readbackBytesPerRow: Long,
    val scratch: W4bSessionScratchV1,
)

/**
 * Closes only the already-sealed W4b analytic-RRect lane. V/I/U remain one pooled native lease;
 * only target and output-owned staging are ordinary preparation requests.
 */
internal class GPUCorePrimitiveW4bPreparedFrameTaskListAssembler {
    fun buildPreplanned(request: GPUCorePrimitiveW4bPreparedFrameRequest): GPUCorePrimitivePreparedFrameResult {
        val render = request.baseTaskList.tasks.singleOrNull() as? GPUTask.Render
            ?: return refused("A preplanned W4b frame requires exactly one render-only base task.")
        if (!hasExactW4bEnvelope(request, render)) return refused("Preplanned W4b frame facts are inconsistent.")
        val sealedPackets = sealPackets(request, render.drawPackets)
            ?: return refused("W4b analytic Uniform80 packet sealing failed.")
        val prefix = "task.w4b.${request.planId.value}"
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
                GPUTaskUseToken("w4b.${request.planId.value}.prepare-to-render"),
                "w4b-plan-resource-availability",
            ),
            GPUTaskDependency(
                renderId,
                readbackId,
                "plan-pass-dependency",
                GPUTaskUseToken(
                    "w4b.${request.planId.value}.${request.renderPassId.value}-to-${request.readbackPassId.value}",
                ),
                "w4b-plan-render-before-readback",
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
        request: GPUCorePrimitiveW4bPreparedFrameRequest,
        packets: List<GPUDrawPacket>,
    ): List<GPUDrawPacket>? {
        val scratch = request.scratch
        val payloads = packets.mapIndexed { index, packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return null
            val authority = GPUCorePrimitivePreparedSemanticAuthority.capture(semantic)
            val bytes = when (val result = buildCorePrimitiveAnalyticShapeUniform(semantic, authority)) {
                is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted -> result.bytes
                is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused -> return null
            }
            if (bytes.size.toLong() != W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES) return null
            val slot = scratch.uniformPlan.slots.getOrNull(index) ?: return null
            if (slot.slotLabel != "analytic-shape-draw-${packet.commandIdValue}" ||
                slot.payloadBytes != W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES ||
                slot.allocatedBytes != scratch.uniformStrideBytes ||
                slot.alignedOffset != index.toLong() * scratch.uniformStrideBytes
            ) return null
            W4bUniformPayload(packet, authority, bytes, index)
        }
        val slabPayloads = payloads.map { payload ->
            GPUUniformSlabPayload("analytic-shape-draw-${payload.packet.commandIdValue}", payload.bytes)
        }
        if (!scratch.uniformPlan.hasExactPayloads(
                W4bSessionScratchV1.SOURCE_LABEL,
                request.baseTaskList.capabilitySeal.deviceGeneration.value,
                scratch.uniformStrideBytes,
                slabPayloads,
            )
        ) return null
        return payloads.map { payload ->
            val packet = payload.packet
            val publicPipelineKey = packet.renderPipelineKey ?: return null
            val seal = try {
                GPUCorePrimitiveAnalyticShapeUniformSeal(
                    plan = scratch.uniformPlan,
                    slotIndex = payload.index,
                    commandId = packet.commandIdValue,
                    packetId = packet.packetId,
                    semanticAuthority = payload.semanticAuthority,
                    renderScissor = (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive).scissorBounds,
                    structuralPipelineKey = scratch.structuralPipelineKey,
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
                    GPUCorePrimitivePreparedPacketAuthority.plannedW4b(
                        structuralPipelineKey = scratch.structuralPipelineKey,
                        renderPipelineKey = publicPipelineKey,
                        scratch = scratch,
                    ).copy(analyticShapeUniformSeal = seal),
                )
            } catch (_: IllegalArgumentException) {
                return null
            }
            packet
        }
    }

    private fun hasExactW4bEnvelope(
        request: GPUCorePrimitiveW4bPreparedFrameRequest,
        render: GPUTask.Render,
    ): Boolean {
        val targetDescriptor = request.targetPreparation.descriptor as? GPUFrameTextureDescriptor ?: return false
        val stagingDescriptor = request.stagingPreparation.descriptor as? GPUFrameBufferDescriptor ?: return false
        val scratch = request.scratch
        val targetRowBytes = try {
            Math.multiplyExact(request.targetBounds.width.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return false
        }
        val expectedReadbackRow = if (targetRowBytes % request.copyBytesPerRowAlignment == 0L) {
            targetRowBytes
        } else {
            targetRowBytes + request.copyBytesPerRowAlignment - targetRowBytes % request.copyBytesPerRowAlignment
        }
        val targetBytes = try {
            Math.multiplyExact(targetRowBytes, request.targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return false
        }
        val stagingBytes = try {
            Math.multiplyExact(expectedReadbackRow, request.targetBounds.height.toLong())
        } catch (_: ArithmeticException) {
            return false
        }
        return request.baseTaskList.diagnostics.none(GPUDiagnostic::isTerminal) &&
            request.baseTaskList.dependencies.isEmpty() && request.baseTaskList.compositeCommands.isEmpty() &&
            request.baseTaskList.memoryBudget == request.memoryBudget &&
            request.renderPassId.value == "MainRender:0" && request.readbackPassId.value == "Readback:0" &&
            request.targetBounds.left == 0 && request.targetBounds.top == 0 && !request.targetBounds.isEmpty &&
            request.copyBytesPerRowAlignment > 0L && request.readbackBytesPerRow == expectedReadbackRow &&
            render.target == request.target && render.loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            render.samplePlan == org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame &&
            render.resourceUses.isEmpty() && render.sampleContinuationKey == null && render.compositeMembership == null &&
            render.depthStencilLoadStore == null && render.preparedImageBindingsByPacketId.isEmpty() &&
            render.preparedTextBindingsByPacketId.isEmpty() &&
            render.drawPackets.size in 1..512 &&
            scratch.matches(
                request.planId.value,
                request.baseTaskList.capabilitySeal.sealHash,
                request.baseTaskList.capabilitySeal.deviceGeneration.value,
                request.target,
                request.staging,
                request.targetBounds,
                render.drawPackets,
            ) &&
            scratch.vertexResourceId == request.vertexResourceId && scratch.indexResourceId == request.indexResourceId &&
            scratch.uniformResourceId == request.uniformResourceId &&
            scratch.draws.map { it.packetId } == render.drawPackets.map(GPUDrawPacket::packetId) &&
            scratch.draws.map { it.commandId } == render.drawPackets.map(GPUDrawPacket::commandIdValue) &&
            render.drawPackets.withIndex().all { (index, packet) -> isExactW4bPacket(packet, scratch, index) } &&
            request.targetPreparation.resource == request.target &&
            request.targetPreparation.role == GPUFrameResourceRole.SceneTarget &&
            request.targetPreparation.usages == setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource) &&
            request.targetPreparation.lifetime == GPUFrameResourceLifetime.FrameLocal &&
            targetDescriptor.logicalBounds == request.targetBounds &&
            targetDescriptor.format == GPUColorFormat.RGBA8UnormSrgb && targetDescriptor.sampleCount == 1 &&
            request.targetPreparation.byteSize == targetBytes &&
            request.stagingPreparation.resource == request.staging &&
            request.stagingPreparation.role == GPUFrameResourceRole.ReadbackStaging &&
            request.stagingPreparation.usages == setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead) &&
            request.stagingPreparation.lifetime == GPUFrameResourceLifetime.FrameLocal &&
            stagingDescriptor.byteSize == stagingBytes && stagingDescriptor.alignmentBytes == request.copyBytesPerRowAlignment &&
            request.stagingPreparation.byteSize == stagingBytes &&
            request.readbackRequest.requestId.value == "w4b.${request.planId.value}.readback" &&
            request.readbackRequest.sourceBounds == request.targetBounds &&
            request.readbackRequest.pixelFormat == GPUReadbackPixelFormat.Rgba8Unorm &&
            request.readbackRequest.outputColorInterpretation == GPUColorInterpretation.EncodedPremulSrgb &&
            request.readbackRequest.bufferOffsetBytes == 0L
    }

    private fun isExactW4bPacket(
        packet: GPUDrawPacket,
        scratch: W4bSessionScratchV1,
        index: Int,
    ): Boolean {
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
        val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.RRect ?: return false
        val draw = scratch.draws.getOrNull(index) ?: return false
        val device = draw.copyDeviceShape()
        val clip = packet.clipExecutionPlan ?: return false
        val expectedClip = if (draw.copyScissorBounds() == scratch.targetBounds) {
            org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.NoClip
        } else {
            org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.ScissorOnly(draw.copyScissorBounds())
        }
        return packet.packetId == GPUDrawPacketID("packet.w4b.${packet.commandIdValue}") &&
            packet.passId == "pass.w4b.main" && packet.layerId == "root" &&
            packet.bindingListId == "binding.w4b.${packet.commandIdValue}" &&
            packet.insertionReasonCode == "w4b-analytic-rrect" &&
            packet.originalPaintOrder == index && packet.sortKey == index.toLong() &&
            packet.sortKeyPreimage == "paint-order:$index" &&
            packet.renderStepId.value == CORE_PRIMITIVE_RENDER_STEP_IDENTITY && packet.renderStepVersion == 1 &&
            packet.role == GPUDrawPacketRole.Shading && packet.resourceGeneration == PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION &&
            packet.blendPlan?.canonicalIdentity() == canonicalSolidRectSrcOverBlendPlan().canonicalIdentity() &&
            packet.clipExecutionPlan == expectedClip && semantic.clipExecutionPlanIdentity == expectedClip.canonicalIdentity() &&
            semantic.sourceFamily == GPUCorePrimitiveSourceFamily.RRect &&
            semantic.rectGeometryAuthority == null && semantic.rrectGeometryAuthority != null &&
            semantic.material is GPUCorePrimitiveMaterialPayload.SolidColor &&
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA &&
            semantic.targetBounds == scratch.targetBounds && semantic.scissorBounds == draw.copyScissorBounds() &&
            geometry.left == device.rect.left && geometry.top == device.rect.top &&
            geometry.right == device.rect.right && geometry.bottom == device.rect.bottom &&
            geometry.radii == listOf(
                device.topLeft.x, device.topLeft.y, device.topRight.x, device.topRight.y,
                device.bottomRight.x, device.bottomRight.y, device.bottomLeft.x, device.bottomLeft.y,
            )
    }

    private fun refused(message: String): GPUCorePrimitivePreparedFrameResult.Refused =
        GPUCorePrimitivePreparedFrameResult.Refused(
            GPUDiagnostic(
                GPUDiagnosticCode("w4b.lowering.incompatible_plan"),
                GPUDiagnosticDomain.Recording,
                GPUDiagnosticSeverity.Error,
                message,
            ),
        )

    private data class W4bUniformPayload(
        val packet: GPUDrawPacket,
        val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
        val bytes: ByteArray,
        val index: Int,
    )
}
