package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanPassId
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest

/** Exact W4c graph facts ready to be closed into the prepared-frame task envelope. */
internal data class GPUCorePrimitiveW4cPreparedFrameRequest(
    val planId: PlanId,
    val baseTaskList: GPUTaskList,
    val target: GPUFrameTargetRef,
    val targetPreparation: GPUResourcePreparationRequest,
    val staging: GPUFrameBufferRef,
    val stagingPreparation: GPUResourcePreparationRequest,
    val readbackRequest: GPUFrameReadbackRequest,
    val memoryBudget: GPUFrameMemoryBudgetPlan,
    val renderPassIds: List<PlanPassId>,
    val readbackPassId: PlanPassId,
    val scratch: W4cSessionScratchV1,
    val compositeWitness: GPUW5aCompositeLaneWitnessV1? = null,
)

/**
 * Closes only the sealed W4c path-fill lane into its linear task list.
 *
 * The ordinary preparation requests deliberately contain only target and staging. The shared
 * V/I/U and optional D24S8 lease remains authority carried by the W4c scratch for Task 6.
 */
internal class GPUCorePrimitiveW4cPreparedFrameTaskListAssembler {
    fun buildPreplanned(request: GPUCorePrimitiveW4cPreparedFrameRequest): GPUCorePrimitivePreparedFrameResult {
        val renders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        if (!hasExactEnvelope(request, renders)) return refused("Preplanned W4c frame facts are inconsistent.")
        val prefix = "task.w4c.${request.planId.value}"
        val prepareId = GPUTaskID("$prefix.prepare")
        val renderIds = request.renderPassIds.map { passId ->
            GPUTaskID("$prefix.render.${passId.value}")
        }
        val readbackId = GPUTaskID("$prefix.readback.${request.readbackPassId.value}")
        val preparedRenders = renders.zip(renderIds).map { (render, taskId) ->
            GPUTask.Render(
                taskId = taskId,
                recordingId = render.recordingId,
                phase = GPUTaskPhase.Render,
                target = render.target,
                loadStore = render.loadStore,
                samplePlan = render.samplePlan,
                resourceUses = render.resourceUses,
                provisionalSegmentKey = render.provisionalSegmentKey,
                drawPackets = render.drawPackets,
                batchEligibilityByPacketId = render.batchEligibilityByPacketId,
                sampleContinuationKey = render.sampleContinuationKey,
                compositeMembership = render.compositeMembership,
                depthStencilLoadStore = render.depthStencilLoadStore,
                preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
                preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
            )
        }
        val tasks = buildList {
            add(
                GPUTask.PrepareResources(
                    prepareId,
                    renders.first().recordingId,
                    GPUTaskPhase.Prepare,
                    listOf(request.targetPreparation, request.stagingPreparation),
                ),
            )
            addAll(preparedRenders)
            add(
                GPUTask.Readback(
                    readbackId,
                    renders.first().recordingId,
                    GPUTaskPhase.Readback,
                    request.target,
                    request.staging,
                    request.readbackRequest,
                ),
            )
        }
        val dependencies = buildList {
            add(
                GPUTaskDependency(
                    prepareId,
                    renderIds.first(),
                    "resource-prepare",
                    GPUTaskUseToken("w4c.${request.planId.value}.prepare-to-${request.renderPassIds.first().value}"),
                    "w4c-plan-resource-availability",
                ),
            )
            renderIds.zipWithNext().forEachIndexed { index, (from, to) ->
                val previous = preparedRenders[index].drawPackets.single()
                val next = preparedRenders[index + 1].drawPackets.single()
                val atomicGroupId = if (
                    previous.role == GPUDrawPacketRole.PathStencilProducer &&
                    next.role == GPUDrawPacketRole.PathStencilCover &&
                    previous.commandIdValue == next.commandIdValue
                ) {
                    GPUTaskAtomicGroupID("w4c:${previous.commandIdValue}")
                } else {
                    null
                }
                add(
                    GPUTaskDependency(
                        from,
                        to,
                        "plan-pass-dependency",
                        GPUTaskUseToken(
                            "w4c.${request.planId.value}.${request.renderPassIds[index].value}-to-" +
                                request.renderPassIds[index + 1].value,
                        ),
                        "w4c-plan-pass-order",
                        atomicGroupId,
                    ),
                )
            }
            add(
                GPUTaskDependency(
                    renderIds.last(),
                    readbackId,
                    "plan-pass-dependency",
                    GPUTaskUseToken(
                        "w4c.${request.planId.value}.${request.renderPassIds.last().value}-to-" +
                            request.readbackPassId.value,
                    ),
                    "w4c-plan-render-before-readback",
                ),
            )
        }
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

    private fun hasExactEnvelope(
        request: GPUCorePrimitiveW4cPreparedFrameRequest,
        renders: List<GPUTask.Render>,
    ): Boolean {
        if (request.compositeWitness?.let { witness ->
                witness.planId != request.planId.value ||
                    witness.packetIds != renders.flatMap(GPUTask.Render::drawPackets).map(GPUDrawPacket::packetId) ||
                    request.target.value != "${witness.sessionIdentity}.target" || request.staging.value != "${witness.sessionIdentity}.staging"
            } == true) return false
        val preparationRoles = listOf(request.targetPreparation.role, request.stagingPreparation.role)
        return request.baseTaskList.diagnostics.none(GPUDiagnostic::isTerminal) &&
            request.baseTaskList.compositeCommands.isEmpty() &&
            request.baseTaskList.dependencies.isEmpty() &&
            request.baseTaskList.memoryBudget == request.memoryBudget &&
            request.baseTaskList.tasks.size == renders.size &&
            renders.size == request.renderPassIds.size && renders.isNotEmpty() &&
            preparationRoles == listOf(GPUFrameResourceRole.SceneTarget, GPUFrameResourceRole.ReadbackStaging) &&
            request.targetPreparation.resource == request.target &&
            request.stagingPreparation.resource == request.staging &&
            request.readbackRequest.requestId.value == "w4c.${request.planId.value}.readback" &&
            request.readbackRequest.outputColorInterpretation == GPUColorInterpretation.EncodedPremulSrgb &&
            request.scratch.matches(
                request.planId.value,
                request.baseTaskList.capabilitySeal.sealHash,
                request.baseTaskList.capabilitySeal.deviceGeneration.value,
                request.target,
                request.staging,
                request.scratch.targetBounds,
            ) &&
            renders.zip(request.renderPassIds).all { (render, passId) ->
                render.target == request.target &&
                    render.drawPackets.size == 1 &&
                    render.drawPackets.single().passId == passId.value &&
                    render.drawPackets.single().corePrimitivePreparedAuthority?.w4cSessionScratch === request.scratch
            }
    }

    private fun refused(message: String): GPUCorePrimitivePreparedFrameResult.Refused =
        GPUCorePrimitivePreparedFrameResult.Refused(
            GPUDiagnostic(
                GPUDiagnosticCode("w4c.lowering.incompatible_plan"),
                GPUDiagnosticDomain.Recording,
                GPUDiagnosticSeverity.Error,
                message,
            ),
        )
}
