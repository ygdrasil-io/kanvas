package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.destination.CopyAsDrawMaterialization
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcher
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope

/** Pure deterministic linearizer between finalized recordings and resource preflight. */
object GPUFramePlanner {
    fun plan(taskList: GPUTaskList): GPUFramePlan {
        val validationFailure = validate(taskList)
        if (validationFailure != null) return taskList.atomicallyRefused(validationFailure)

        val orderedTasks = stableTopologicalOrder(taskList)
            ?: return taskList.atomicallyRefused(
                diagnostic(
                    code = "invalid.frame_plan.task_cycle",
                    message = "GPUTaskList dependencies contain a cycle",
                ),
            )

        val compositeScopes = validateCompositeScopes(taskList, orderedTasks)
        if (compositeScopes is CompositeScopeValidation.Invalid) {
            return taskList.atomicallyRefused(compositeScopes.diagnostic)
        }
        compositeScopes as CompositeScopeValidation.Valid

        val linearization = linearize(
            orderedTasks = orderedTasks,
            consumedChildIds = compositeScopes.consumedChildIds,
            orderedTaskIndex = orderedTasks.withIndex().associate { it.value.taskId to it.index },
        )
        if (linearization is Linearization.Refused) {
            return taskList.atomicallyRefused(linearization.diagnostic)
        }
        linearization as Linearization.Planned

        return GPUFramePlan(
            frameId = taskList.frameId,
            recordingSeals = taskList.recordingSeals.sortedBy(GPURecordingSeal::insertionOrder),
            steps = linearization.steps,
            memoryBudget = taskList.memoryBudget,
            diagnostics = taskList.diagnostics + linearization.diagnostics,
        )
    }

    private fun validate(taskList: GPUTaskList): GPUDiagnostic? {
        val sealsByRecording = taskList.recordingSeals.groupBy(GPURecordingSeal::recordingId)
        if (sealsByRecording.any { (_, seals) -> seals.size != 1 }) {
            return diagnostic(
                code = "invalid.frame_plan.duplicate_recording_seal",
                message = "Each recording must have exactly one seal",
            )
        }
        if (taskList.recordingSeals.map(GPURecordingSeal::insertionOrder).distinct().size !=
            taskList.recordingSeals.size
        ) {
            return diagnostic(
                code = "invalid.frame_plan.duplicate_recording_insertion",
                message = "Recording seal insertion orders must be unique",
            )
        }
        if (taskList.recordingSeals.map(GPURecordingSeal::compatibilityKeyHash).distinct().size > 1) {
            return diagnostic(
                code = "recording.compatibility_key_mismatch",
                message = "Recording seals have incompatible compatibility keys",
            )
        }
        if (taskList.recordingSeals.any { seal ->
                seal.replayKeyHash != taskList.expectedReplayKeyHash
            }
        ) {
            return diagnostic(
                code = "replay.compatibility_key_mismatch",
                message = "A recording seal does not match the requested replay key",
            )
        }

        val tasksById = taskList.tasks.groupBy(GPUTask::taskId)
        if (tasksById.any { (_, tasks) -> tasks.size != 1 }) {
            return diagnostic(
                code = "invalid.frame_plan.duplicate_task_id",
                message = "Task identifiers must be unique within one frame",
            )
        }
        if (taskList.tasks.any { task -> task.recordingId !in sealsByRecording }) {
            return diagnostic(
                code = "invalid.frame_plan.unsealed_recording",
                message = "Every task must belong to a sealed recording",
            )
        }
        if (taskList.tasks.any { task -> task.phase !in taskList.phaseOrder }) {
            return diagnostic(
                code = "invalid.frame_plan.missing_phase",
                message = "Every task phase must occur in phaseOrder",
            )
        }
        val invalidPhaseTask = taskList.tasks.firstOrNull { task -> task.phase != task.expectedPhase() }
        if (invalidPhaseTask != null) {
            return diagnostic(
                code = "invalid.frame_plan.task_phase",
                message = "Task ${invalidPhaseTask.taskId.value} has the wrong phase",
            )
        }

        val taskIds = tasksById.keys
        if (taskList.dependencies.any { dependency ->
                dependency.fromTaskId !in taskIds || dependency.toTaskId !in taskIds
            }
        ) {
            return diagnostic(
                code = "invalid.frame_plan.missing_task_dependency",
                message = "A dependency references a task outside the finalized task list",
            )
        }
        if (taskList.dependencies.any { it.fromTaskId == it.toTaskId }) {
            return diagnostic(
                code = "invalid.frame_plan.self_dependency",
                message = "A task cannot depend on itself",
            )
        }

        val packetIds = taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .map { packet -> packet.packetId }
        if (packetIds.distinct().size != packetIds.size) {
            return diagnostic(
                code = "invalid.frame_plan.duplicate_draw_packet",
                message = "Draw packet identifiers must be unique within one frame",
            )
        }

        val atomicRefusal = taskList.tasks
            .filterIsInstance<GPUTask.Refused>()
            .firstOrNull { task -> task.scope == GPURefusalScope.AtomicFrameFailure }
        return atomicRefusal?.diagnostic
    }

    private fun stableTopologicalOrder(taskList: GPUTaskList): List<GPUTask>? {
        val taskById = taskList.tasks.associateBy(GPUTask::taskId)
        val insertionByRecording = taskList.recordingSeals.associate {
            seal -> seal.recordingId to seal.insertionOrder
        }
        val phaseIndex = taskList.phaseOrder.withIndex().associate { it.value to it.index }
        val originalIndex = taskList.tasks.withIndex().associate { it.value.taskId to it.index }
        val stableComparator = compareBy<GPUTask>(
            { task -> phaseIndex.getValue(task.phase) },
            { task -> insertionByRecording.getValue(task.recordingId) },
            { task -> originalIndex.getValue(task.taskId) },
            { task -> task.taskId.value },
        )

        val indegree = taskList.tasks.associate { task -> task.taskId to 0 }.toMutableMap()
        val outgoing = taskList.tasks.associate { task -> task.taskId to mutableListOf<GPUTaskID>() }
        taskList.dependencies.forEach { dependency ->
            outgoing.getValue(dependency.fromTaskId) += dependency.toTaskId
            indegree[dependency.toTaskId] = indegree.getValue(dependency.toTaskId) + 1
        }

        val ready = taskList.tasks.filter { task -> indegree.getValue(task.taskId) == 0 }.toMutableList()
        val ordered = mutableListOf<GPUTask>()
        while (ready.isNotEmpty()) {
            ready.sortWith(stableComparator)
            val task = ready.removeAt(0)
            ordered += task
            outgoing.getValue(task.taskId).forEach { nextTaskId ->
                val nextIndegree = indegree.getValue(nextTaskId) - 1
                indegree[nextTaskId] = nextIndegree
                if (nextIndegree == 0) ready += taskById.getValue(nextTaskId)
            }
        }
        return ordered.takeIf { it.size == taskList.tasks.size }
    }

    private fun validateCompositeScopes(
        taskList: GPUTaskList,
        orderedTasks: List<GPUTask>,
    ): CompositeScopeValidation {
        val taskIds = taskList.tasks.map(GPUTask::taskId).toSet()
        val orderedIndex = orderedTasks.withIndex().associate { it.value.taskId to it.index }
        val owners = mutableMapOf<GPUTaskID, GPUTaskID>()

        taskList.tasks.filterIsInstance<GPUTask.Refused>()
            .filter { task -> task.scope == GPURefusalScope.RefusedCompositeCommand }
            .forEach { composite ->
                if (composite.consumedChildTaskIds.isEmpty() ||
                    composite.consumedChildTaskIds.distinct().size != composite.consumedChildTaskIds.size ||
                    composite.consumedChildTaskIds.any { child -> child !in taskIds || child == composite.taskId }
                ) {
                    return CompositeScopeValidation.Invalid(
                        diagnostic(
                            code = "invalid.frame_plan.composite_provenance",
                            message = "Composite refusal child provenance is incomplete or invalid",
                        ),
                    )
                }
                composite.consumedChildTaskIds.forEach { child ->
                    if (owners.put(child, composite.taskId) != null) {
                        return CompositeScopeValidation.Invalid(
                            diagnostic(
                                code = "invalid.frame_plan.composite_provenance",
                                message = "A child task cannot be consumed by multiple composite refusals",
                            ),
                        )
                    }
                    if (orderedIndex.getValue(child) >= orderedIndex.getValue(composite.taskId)) {
                        return CompositeScopeValidation.Invalid(
                            diagnostic(
                                code = "invalid.frame_plan.atomic_order",
                                message = "Composite refusal cannot consume a child ordered after itself",
                            ),
                        )
                    }
                }
                val childIds = composite.consumedChildTaskIds.toSet()
                val firstChildIndex = composite.consumedChildTaskIds.minOf { child ->
                    orderedIndex.getValue(child)
                }
                val compositeIndex = orderedIndex.getValue(composite.taskId)
                if (orderedTasks.subList(firstChildIndex, compositeIndex).any { task ->
                        task.taskId !in childIds
                    }
                ) {
                    return CompositeScopeValidation.Invalid(
                        diagnostic(
                            code = "invalid.frame_plan.atomic_order",
                            message = "Composite refusal children do not form one contiguous atomic scope",
                        ),
                    )
                }
            }

        taskList.dependencies.forEach { dependency ->
            val fromOwner = owners[dependency.fromTaskId]
            val toOwner = owners[dependency.toTaskId]
            val allowed = when {
                fromOwner == null && toOwner == null -> true
                fromOwner != null && dependency.toTaskId == fromOwner -> true
                fromOwner != null && fromOwner == toOwner -> true
                else -> false
            }
            if (!allowed) {
                return CompositeScopeValidation.Invalid(
                    diagnostic(
                        code = "invalid.frame_plan.atomic_order",
                        message = "Composite refusal child ordering leaks outside its atomic scope",
                    ),
                )
            }
        }
        return CompositeScopeValidation.Valid(owners.keys)
    }

    private fun linearize(
        orderedTasks: List<GPUTask>,
        consumedChildIds: Set<GPUTaskID>,
        orderedTaskIndex: Map<GPUTaskID, Int>,
    ): Linearization {
        val steps = mutableListOf<GPUFrameStep>()
        val diagnostics = mutableListOf<GPUDiagnostic>()
        var index = 0
        while (index < orderedTasks.size) {
            val task = orderedTasks[index]
            if (task.taskId in consumedChildIds) {
                index += 1
                continue
            }
            if (task is GPUTask.Render) {
                val renderTasks = mutableListOf(task)
                var nextIndex = index + 1
                while (nextIndex < orderedTasks.size) {
                    val next = orderedTasks[nextIndex]
                    if (next.taskId in consumedChildIds) {
                        nextIndex += 1
                        continue
                    }
                    if (next !is GPUTask.Render || !task.canShareProvisionalSegment(next)) break
                    renderTasks += next
                    nextIndex += 1
                }
                val renderSteps = batchRenderSegment(renderTasks)
                if (renderSteps == null) {
                    return Linearization.Refused(
                        diagnostic(
                            code = "invalid.frame_plan.render_batch",
                            message = "Pass-local batching did not preserve every draw packet",
                        ),
                    )
                }
                steps += renderSteps
                index = nextIndex
                continue
            }

            when (task) {
                is GPUTask.Render -> error("Render tasks are handled as provisional segments")
                is GPUTask.PrepareResources -> steps += GPUFrameStep.PrepareResourcesStep(
                    requests = task.requests,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.Compute -> steps += GPUFrameStep.ComputePassStep(
                    target = task.target,
                    resourceUses = task.resourceUses,
                    dispatches = task.dispatches,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.Copy -> steps += GPUFrameStep.CopyResourceStep(
                    source = task.source,
                    destination = task.destination,
                    regions = task.regions,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.Upload -> steps += GPUFrameStep.UploadResourceStep(
                    staging = task.staging,
                    destination = task.destination,
                    layout = task.layout,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.Barrier -> steps += GPUFrameStep.DependencyBarrierStep(
                    orderedUseTokens = task.orderedUseTokens,
                    reasonCode = task.reasonCode,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.DestinationSnapshots -> {
                    when (val result = destinationSnapshotSteps(task)) {
                        is DestinationSteps.Invalid -> return Linearization.Refused(result.diagnostic)
                        is DestinationSteps.Valid -> {
                            steps += result.steps
                            diagnostics += result.diagnostics
                        }
                    }
                }
                is GPUTask.TargetTransition -> steps += GPUFrameStep.TargetTransitionStep(
                    parent = task.parent,
                    child = task.child,
                    transitionKind = task.transitionKind,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.Readback -> steps += GPUFrameStep.ReadbackCopyStep(
                    source = task.source,
                    staging = task.staging,
                    request = task.request,
                    sourceTaskIds = listOf(task.taskId),
                )
                is GPUTask.Output -> {
                    steps += GPUFrameStep.AcquireSurfaceOutput(
                        descriptor = task.descriptor,
                        sourceTaskIds = listOf(task.taskId),
                    )
                    steps += GPUFrameStep.SurfaceBlitRenderPassStep(
                        scene = task.scene,
                        output = task.descriptor.output,
                        sourceTaskIds = listOf(task.taskId),
                    )
                    steps += GPUFrameStep.PostSubmitPresentAction(
                        output = task.descriptor.output,
                        sourceTaskIds = listOf(task.taskId),
                    )
                }
                is GPUTask.Refused -> when (task.scope) {
                    GPURefusalScope.RefusedLeafDrawStep -> steps += GPUFrameStep.RefusedLeafDrawStep(
                        commandId = task.commandId,
                        diagnostic = task.diagnostic,
                        sourceTaskIds = listOf(task.taskId),
                    )
                    GPURefusalScope.RefusedCompositeCommand -> {
                        val sourceTaskIds = (task.consumedChildTaskIds + task.taskId)
                            .distinct()
                            .sortedBy { sourceTaskId -> orderedTaskIndex.getValue(sourceTaskId) }
                        steps += GPUFrameStep.RefusedCompositeCommandStep(
                            commandId = task.commandId,
                            provenanceTokens = task.provenanceTokens,
                            diagnostic = task.diagnostic,
                            sourceTaskIds = sourceTaskIds,
                        )
                    }
                    GPURefusalScope.AtomicFrameFailure -> error("Atomic refusals are validated first")
                }
            }
            index += 1
        }
        return Linearization.Planned(steps, diagnostics)
    }

    private fun batchRenderSegment(tasks: List<GPUTask.Render>): List<GPUFrameStep.RenderPassStep>? {
        val first = tasks.first()
        val packets = tasks.flatMap(GPUTask.Render::drawPackets)
        val packetOwner = buildMap<GPUDrawPacketID, GPUTaskID> {
            tasks.forEach { task ->
                task.drawPackets.forEach { packet -> put(packet.packetId, task.taskId) }
            }
        }
        val eligibility = buildMap {
            tasks.forEach { task -> putAll(task.batchEligibilityByPacketId) }
        }
        val batchPlan = GPUPassBatcher().plan(
            GPUPassBatcherRequest(
                packetStream = GPUDrawPacketStream(
                    streamId = "frame.${first.taskId.value}",
                    passId = first.passId,
                    packets = packets,
                ),
                eligibilityByPacketId = eligibility,
            ),
        )
        if (batchPlan.batches.flatMap { batch -> batch.packets }.map { it.packetId } !=
            packets.map { it.packetId }
        ) {
            return null
        }
        return batchPlan.batches.map { batch ->
            GPUFrameStep.RenderPassStep(
                target = first.target,
                loadStore = first.loadStore,
                samplePlan = first.samplePlan,
                drawPackets = batch.packets,
                sourceTaskIds = batch.packets.map { packet -> packetOwner.getValue(packet.packetId) }.distinct(),
            )
        }
    }

    private fun destinationSnapshotSteps(task: GPUTask.DestinationSnapshots): DestinationSteps {
        val payload = task.payload
        val bindingsByGroupingId = payload.commandBindings.groupBy {
            binding -> binding.groupingCommandId
        }
        val groupingCommandIds = payload.grouping.groups.flatMap { group ->
            group.members.map { member -> member.commandId }
        } + payload.grouping.refusals.map { refusal -> refusal.commandId }
        if (groupingCommandIds.distinct().size != groupingCommandIds.size ||
            payload.commandBindings.map { binding -> binding.groupingCommandId }.toSet() != groupingCommandIds.toSet() ||
            payload.commandBindings.map { binding -> binding.commandId }.distinct().size != payload.commandBindings.size ||
            groupingCommandIds.any { commandId -> bindingsByGroupingId[commandId]?.size != 1 }
        ) {
            return DestinationSteps.Invalid(
                diagnostic(
                    code = "invalid.frame_plan.destination_command_binding",
                    message = "Destination grouping command IDs require one exact typed binding",
                ),
            )
        }

        val steps = mutableListOf<GPUFrameStep>()
        val diagnostics = mutableListOf<GPUDiagnostic>()
        payload.grouping.materializations.forEach { materialization ->
            val snapshot = payload.snapshotsByGroupIndex[materialization.groupIndex]
                ?: return DestinationSteps.Invalid(
                    diagnostic(
                        code = "invalid.frame_plan.destination_snapshot_resource",
                        message = "Destination snapshot materialization has no typed texture resource",
                    ),
                )
            when (materialization) {
                is GPUDestinationSnapshotMaterialization.TextureCopy -> {
                    val layout = payload.copyLayoutsByGroupIndex[materialization.groupIndex]
                        ?: return DestinationSteps.Invalid(
                            diagnostic(
                                code = "invalid.frame_plan.destination_copy_layout",
                                message = "Destination texture copy has no typed copy layout",
                            ),
                        )
                    steps += GPUFrameStep.CopyDestinationStep(
                        source = payload.source,
                        snapshot = snapshot,
                        logicalBounds = materialization.logicalBounds,
                        copyLayout = layout,
                        sourceTaskIds = listOf(task.taskId),
                    )
                }
                is CopyAsDrawMaterialization -> {
                    if (payload.copyAsDrawCapability?.available != true) {
                        val group = payload.grouping.groups.getOrNull(materialization.groupIndex)
                            ?: return DestinationSteps.Invalid(
                                diagnostic(
                                    code = "invalid.frame_plan.destination_group",
                                    message = "Copy-as-draw materialization references an unknown group",
                                ),
                            )
                        group.members.forEach { member ->
                            val binding = bindingsByGroupingId.getValue(member.commandId).single()
                            val refusal = diagnostic(
                                code = "unsupported.destination_read.copy_unavailable",
                                message = "Copy-as-draw materialization implementation is unavailable",
                            )
                            steps += GPUFrameStep.RefusedLeafDrawStep(
                                commandId = binding.commandId,
                                diagnostic = refusal,
                                sourceTaskIds = listOf(task.taskId),
                            )
                            diagnostics += refusal
                        }
                    } else {
                        steps += GPUFrameStep.CopyAsDrawMaterializationStep(
                            source = payload.source,
                            snapshot = snapshot,
                            logicalBounds = materialization.logicalBounds,
                            sourceTaskIds = listOf(task.taskId),
                        )
                    }
                }
            }
        }
        payload.grouping.refusals.forEach { refusal ->
            val binding = bindingsByGroupingId.getValue(refusal.commandId).single()
            val canonical = diagnostic(code = refusal.code, message = refusal.code, facts = refusal.facts)
            steps += GPUFrameStep.RefusedLeafDrawStep(
                commandId = binding.commandId,
                diagnostic = canonical,
                sourceTaskIds = listOf(task.taskId),
            )
            diagnostics += canonical
        }
        return DestinationSteps.Valid(steps, diagnostics)
    }

    private fun GPUTask.Render.canShareProvisionalSegment(other: GPUTask.Render): Boolean =
        target == other.target &&
            loadStore == other.loadStore &&
            samplePlan == other.samplePlan &&
            passId == other.passId

    private fun GPUTask.expectedPhase(): GPUTaskPhase = when (this) {
        is GPUTask.PrepareResources -> GPUTaskPhase.Prepare
        is GPUTask.Upload -> GPUTaskPhase.Upload
        is GPUTask.Compute -> GPUTaskPhase.Compute
        is GPUTask.Render -> GPUTaskPhase.Render
        is GPUTask.Copy, is GPUTask.DestinationSnapshots -> GPUTaskPhase.Copy
        is GPUTask.Barrier, is GPUTask.TargetTransition -> GPUTaskPhase.Transition
        is GPUTask.Readback -> GPUTaskPhase.Readback
        is GPUTask.Output -> GPUTaskPhase.Output
        is GPUTask.Refused -> GPUTaskPhase.Refusal
    }

    private fun GPUTaskList.atomicallyRefused(diagnostic: GPUDiagnostic): GPUFramePlan =
        GPUFramePlan(
            frameId = frameId,
            recordingSeals = recordingSeals.sortedBy(GPURecordingSeal::insertionOrder),
            steps = emptyList(),
            memoryBudget = memoryBudget,
            diagnostics = diagnostics + diagnostic,
            atomicallyRefused = true,
        )

    private fun diagnostic(
        code: String,
        message: String,
        facts: Map<String, String> = emptyMap(),
    ): GPUDiagnostic = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Recording,
        severity = GPUDiagnosticSeverity.Error,
        message = message,
        facts = facts,
    )

    private sealed interface CompositeScopeValidation {
        data class Valid(val consumedChildIds: Set<GPUTaskID>) : CompositeScopeValidation
        data class Invalid(val diagnostic: GPUDiagnostic) : CompositeScopeValidation
    }

    private sealed interface Linearization {
        data class Planned(
            val steps: List<GPUFrameStep>,
            val diagnostics: List<GPUDiagnostic>,
        ) : Linearization

        data class Refused(val diagnostic: GPUDiagnostic) : Linearization
    }

    private sealed interface DestinationSteps {
        data class Valid(
            val steps: List<GPUFrameStep>,
            val diagnostics: List<GPUDiagnostic>,
        ) : DestinationSteps

        data class Invalid(val diagnostic: GPUDiagnostic) : DestinationSteps
    }
}
