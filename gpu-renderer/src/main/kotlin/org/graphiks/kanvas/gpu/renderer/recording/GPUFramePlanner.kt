package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcher
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatcherRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef

/** Pure deterministic linearizer between finalized recordings and resource preflight. */
object GPUFramePlanner {
    fun plan(taskList: GPUTaskList): GPUFramePlan {
        validate(taskList)?.let { return taskList.atomicallyRefused(it) }

        val orderedTasks = stableTopologicalOrder(taskList)
            ?: return taskList.atomicallyRefused(
                diagnostic("invalid.frame_plan.task_cycle", "GPUTaskList dependencies contain a cycle"),
            )

        validateOutput(orderedTasks)?.let { return taskList.atomicallyRefused(it) }
        validateTargetTransitions(orderedTasks)?.let { return taskList.atomicallyRefused(it) }

        val compositeScopes = validateCompositeScopes(taskList, orderedTasks)
        if (compositeScopes is CompositeScopeValidation.Invalid) {
            return taskList.atomicallyRefused(compositeScopes.diagnostic)
        }
        compositeScopes as CompositeScopeValidation.Valid

        val destinationSchedule = buildDestinationSchedule(
            taskList = taskList,
            orderedTasks = orderedTasks,
            consumedChildIds = compositeScopes.consumedChildIds,
        )
        if (destinationSchedule is DestinationScheduleValidation.Invalid) {
            return taskList.atomicallyRefused(destinationSchedule.diagnostic)
        }
        destinationSchedule as DestinationScheduleValidation.Valid

        validateBlendPlans(taskList, destinationSchedule.consumerPacketIds)?.let {
            return taskList.atomicallyRefused(it)
        }

        val linearization = linearize(
            taskList = taskList,
            orderedTasks = orderedTasks,
            consumedChildIds = compositeScopes.consumedChildIds,
            orderedTaskIndex = orderedTasks.withIndex().associate { it.value.taskId to it.index },
            destinationSchedule = destinationSchedule,
        )
        if (linearization is Linearization.Refused) {
            return taskList.atomicallyRefused(linearization.diagnostic)
        }
        linearization as Linearization.Planned

        return GPUFramePlan(
            frameId = taskList.frameId,
            capabilitySeal = taskList.capabilitySeal,
            recordingSeals = taskList.recordingSeals.sortedBy(GPURecordingSeal::insertionOrder),
            steps = linearization.steps,
            memoryBudget = taskList.memoryBudget,
            diagnostics = taskList.diagnostics + linearization.diagnostics,
            dependencies = taskList.dependencies,
            phaseOrder = taskList.phaseOrder,
            elidedNoOpDraws = orderedTasks.elidedNoOpDraws(),
        )
    }

    private fun validate(taskList: GPUTaskList): GPUDiagnostic? {
        if (taskList.capabilitySeal.frameId != taskList.frameId ||
            taskList.recordingSeals.any { it.capabilitySealHash != taskList.capabilitySeal.sealHash }
        ) {
            return diagnostic(
                "invalid.frame_plan.capability_seal",
                "Frame and recording capability seals must be congruent",
            )
        }

        val sealsByRecording = taskList.recordingSeals.groupBy(GPURecordingSeal::recordingId)
        if (sealsByRecording.any { (_, seals) -> seals.size != 1 }) {
            return diagnostic("invalid.frame_plan.duplicate_recording_seal", "Each recording needs one seal")
        }
        if (taskList.recordingSeals.map(GPURecordingSeal::insertionOrder).distinct().size !=
            taskList.recordingSeals.size
        ) {
            return diagnostic(
                "invalid.frame_plan.duplicate_recording_insertion",
                "Recording insertion orders must be unique",
            )
        }
        if (taskList.recordingSeals.map(GPURecordingSeal::compatibilityKeyHash).distinct().size > 1) {
            return diagnostic(
                "recording.compatibility_key_mismatch",
                "Recording seals have incompatible compatibility keys",
            )
        }
        if (taskList.recordingSeals.any { it.replayKeyHash != taskList.expectedReplayKeyHash }) {
            return diagnostic(
                "replay.compatibility_key_mismatch",
                "A recording seal does not match the requested replay key",
            )
        }

        val tasksById = taskList.tasks.groupBy(GPUTask::taskId)
        if (tasksById.any { (_, tasks) -> tasks.size != 1 }) {
            return diagnostic("invalid.frame_plan.duplicate_task_id", "Frame task identifiers must be unique")
        }
        if (taskList.tasks.any { it.recordingId !in sealsByRecording }) {
            return diagnostic("invalid.frame_plan.unsealed_recording", "Every task must be sealed")
        }
        if (taskList.tasks.any { it.phase !in taskList.phaseOrder }) {
            return diagnostic("invalid.frame_plan.missing_phase", "Every task phase must occur in phaseOrder")
        }
        taskList.tasks.firstOrNull { it.phase != it.expectedPhase() }?.let {
            return diagnostic("invalid.frame_plan.task_phase", "Task ${it.taskId.value} has the wrong phase")
        }

        val taskIds = tasksById.keys
        if (taskList.dependencies.any { it.fromTaskId !in taskIds || it.toTaskId !in taskIds }) {
            return diagnostic(
                "invalid.frame_plan.missing_task_dependency",
                "A dependency references a task outside the finalized list",
            )
        }
        if (taskList.dependencies.any { it.fromTaskId == it.toTaskId }) {
            return diagnostic("invalid.frame_plan.self_dependency", "A task cannot depend on itself")
        }

        val renderTasks = taskList.tasks.filterIsInstance<GPUTask.Render>()
        val packetIds = renderTasks.flatMap(GPUTask.Render::drawPackets).map(GPUDrawPacket::packetId)
        if (packetIds.distinct().size != packetIds.size) {
            return diagnostic("invalid.frame_plan.duplicate_draw_packet", "Draw packet IDs must be unique")
        }
        renderTasks.forEach { task ->
            if (task.drawPackets.map(GPUDrawPacket::passId).distinct().size != 1) {
                return diagnostic(
                    "invalid.frame_plan.render_packet_pass",
                    "Render task ${task.taskId.value} mixes pass identities",
                )
            }
            if (task.drawPackets.map(GPUDrawPacket::targetStateHash).distinct().size != 1) {
                return diagnostic(
                    "invalid.frame_plan.render_packet_target",
                    "Render task ${task.taskId.value} mixes target states",
                )
            }
            if (task.drawPackets.any { !it.role.isRenderEncodable() || it.computePipelineKey != null }) {
                return diagnostic(
                    "invalid.frame_plan.render_packet_role",
                    "Render task ${task.taskId.value} contains a non-render packet role",
                )
            }
        }

        return taskList.tasks.filterIsInstance<GPUTask.Refused>()
            .firstOrNull { it.scope == GPURefusalScope.AtomicFrameFailure }
            ?.diagnostic
    }

    private fun validateOutput(orderedTasks: List<GPUTask>): GPUDiagnostic? {
        val outputs = orderedTasks.filterIsInstance<GPUTask.Output>()
        if (outputs.size > 1) {
            return diagnostic("invalid.frame_plan.output_count", "A frame may contain at most one Output task")
        }
        val output = outputs.singleOrNull() ?: return null
        if (orderedTasks.lastOrNull()?.taskId != output.taskId) {
            return diagnostic(
                "invalid.frame_plan.output_not_terminal",
                "Output must be the final task after all encoder work and evidence",
            )
        }
        return null
    }

    private fun validateTargetTransitions(orderedTasks: List<GPUTask>): GPUDiagnostic? {
        data class Scope(
            val parent: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef,
            val child: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef,
            var composed: Boolean,
        )

        val stack = mutableListOf<Scope>()
        orderedTasks.forEach { task ->
            stack.lastOrNull()?.takeIf(Scope::composed)?.let { active ->
                val exactReturn = task is GPUTask.TargetTransition &&
                    task.transitionKind == GPUTargetTransitionKind.ReturnToParent &&
                    task.parent == active.parent && task.child == active.child
                if (!exactReturn) {
                    return invalidTransition(
                        "A composed child must return to its parent before any other work",
                    )
                }
            }
            when (task) {
                is GPUTask.TargetTransition -> {
                    if (task.parent == task.child) return invalidTransition("parent and child must differ")
                    when (task.transitionKind) {
                        GPUTargetTransitionKind.EnterChild -> {
                            if (stack.isNotEmpty() && stack.last().child != task.parent) {
                                return invalidTransition("nested child must enter from the active child")
                            }
                            stack += Scope(task.parent, task.child, composed = false)
                        }
                        GPUTargetTransitionKind.CompositeChild -> {
                            val active = stack.lastOrNull()
                                ?: return invalidTransition("CompositeChild requires an active child")
                            if (active.parent != task.parent || active.child != task.child || active.composed) {
                                return invalidTransition("CompositeChild must match the active uncomposed child")
                            }
                            active.composed = true
                        }
                        GPUTargetTransitionKind.ReturnToParent -> {
                            val active = stack.lastOrNull()
                                ?: return invalidTransition("ReturnToParent requires an active child")
                            if (active.parent != task.parent || active.child != task.child || !active.composed) {
                                return invalidTransition("ReturnToParent must close the composed active child")
                            }
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                }
                is GPUTask.Render -> if (stack.isNotEmpty() && task.target != stack.last().child) {
                    return invalidTransition("Render work must target the active child")
                }
                is GPUTask.Output -> if (stack.isNotEmpty()) {
                    return invalidTransition("Output cannot occur inside an open child target")
                }
                else -> Unit
            }

            if (task !is GPUTask.TargetTransition && task !is GPUTask.Output && stack.isNotEmpty()) {
                val activeChild = stack.last().child.value
                if (task.touchedTargetValues().any { target -> target != activeChild }) {
                    return invalidTransition(
                        "Work inside a child scope may touch only the active child target",
                    )
                }
            }
        }
        return if (stack.isEmpty()) null else invalidTransition("All child target scopes must close")
    }

    private fun GPUTask.touchedTargetValues(): List<String> = when (this) {
        is GPUTask.Render -> listOf(target.value)
        is GPUTask.PrepareResources -> requests.mapNotNull { request ->
            (request.resource as? GPUFrameTargetRef)?.value
        }
        is GPUTask.Compute -> listOf(target.value) + resourceUses.mapNotNull { use ->
            (use.resource as? GPUFrameTargetRef)?.value
        }
        is GPUTask.Copy -> listOfNotNull(
            (source as? GPUFrameTargetRef)?.value,
            (destination as? GPUFrameTargetRef)?.value,
        )
        is GPUTask.Upload -> listOfNotNull((destination as? GPUFrameTargetRef)?.value)
        is GPUTask.DestinationSnapshots ->
            payload.grouping.groups.map { group -> group.key.target.value } +
                payload.operations.mapNotNull { operation ->
                    (operation.source as? GPUFrameTargetRef)?.value
                }
        is GPUTask.Readback -> listOf(source.value)
        is GPUTask.Barrier,
        is GPUTask.TargetTransition,
        is GPUTask.Output,
        is GPUTask.Refused,
        -> emptyList()
    }

    private fun invalidTransition(message: String): GPUDiagnostic =
        diagnostic("invalid.frame_plan.target_transition", message)

    private fun stableTopologicalOrder(taskList: GPUTaskList): List<GPUTask>? {
        val taskById = taskList.tasks.associateBy(GPUTask::taskId)
        val insertionByRecording = taskList.recordingSeals.associate {
            it.recordingId to it.insertionOrder
        }
        val phaseIndex = taskList.phaseOrder.withIndex().associate { it.value to it.index }
        val originalIndex = taskList.tasks.withIndex().associate { it.value.taskId to it.index }
        val stableComparator = compareBy<GPUTask>(
            { phaseIndex.getValue(it.phase) },
            { insertionByRecording.getValue(it.recordingId) },
            { originalIndex.getValue(it.taskId) },
            { it.taskId.value },
        )

        val indegree = taskList.tasks.associate { it.taskId to 0 }.toMutableMap()
        val outgoing = taskList.tasks.associate { it.taskId to mutableListOf<GPUTaskID>() }
        taskList.dependencies.forEach { dependency ->
            outgoing.getValue(dependency.fromTaskId) += dependency.toTaskId
            indegree[dependency.toTaskId] = indegree.getValue(dependency.toTaskId) + 1
        }

        val ready = taskList.tasks.filter { indegree.getValue(it.taskId) == 0 }.toMutableList()
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
        val composites = taskList.tasks.filterIsInstance<GPUTask.Refused>()
            .filter { it.scope == GPURefusalScope.RefusedCompositeCommand }
        val renderMembers = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .filter { it.compositeMembership != null }
        val orderedIndex = orderedTasks.withIndex().associate { it.value.taskId to it.index }
        val owners = mutableMapOf<GPUTaskID, GPUTaskID>()
        val ownedScopes = mutableSetOf<GPUCompositeScopeID>()

        composites.forEach { composite ->
            val scopeId = composite.compositeScopeId
                ?: return invalidComposite("Composite refusal lacks a typed scope")
            if (!ownedScopes.add(scopeId)) return invalidComposite("Composite scope has multiple owners")
            val members = renderMembers.filter { it.compositeMembership?.scopeId == scopeId }
                .sortedBy { it.compositeMembership?.childOrdinal }
            if (members.isEmpty() || members.any { it.recordingId != composite.recordingId }) {
                return invalidComposite("Composite children must be draw tasks from the same recording")
            }
            val memberships = members.map { requireNotNull(it.compositeMembership) }
            if (memberships.map { it.parentCommandId }.any { it != composite.commandId } ||
                memberships.map { it.childOrdinal } != memberships.indices.toList() ||
                memberships.map { it.provenanceToken }.distinct().size != memberships.size ||
                composite.consumedChildTaskIds != members.map { it.taskId } ||
                composite.provenanceTokens != memberships.map { it.provenanceToken }
            ) {
                return invalidComposite("Composite membership and consumed provenance must match one-to-one")
            }
            members.forEach { child ->
                if (owners.put(child.taskId, composite.taskId) != null) {
                    return invalidComposite("A child cannot be consumed by multiple composites")
                }
                if (orderedIndex.getValue(child.taskId) >= orderedIndex.getValue(composite.taskId)) {
                    return CompositeScopeValidation.Invalid(
                        diagnostic("invalid.frame_plan.atomic_order", "Composite child must precede its parent"),
                    )
                }
            }
            val childIds = members.map { it.taskId }.toSet()
            val firstChildIndex = members.minOf { orderedIndex.getValue(it.taskId) }
            val compositeIndex = orderedIndex.getValue(composite.taskId)
            if (orderedTasks.subList(firstChildIndex, compositeIndex).any { it.taskId !in childIds }) {
                return CompositeScopeValidation.Invalid(
                    diagnostic("invalid.frame_plan.atomic_order", "Composite children must remain contiguous"),
                )
            }
        }

        if (renderMembers.any { member ->
                composites.none { it.compositeScopeId == member.compositeMembership?.scopeId }
            }
        ) {
            return invalidComposite("Composite child membership has no owning refusal")
        }

        taskList.dependencies.forEach { dependency ->
            val fromOwner = owners[dependency.fromTaskId]
            val toOwner = owners[dependency.toTaskId]
            val allowed = when {
                fromOwner == null -> true
                dependency.toTaskId == fromOwner -> true
                fromOwner == toOwner -> true
                else -> false
            }
            if (!allowed) {
                return CompositeScopeValidation.Invalid(
                    diagnostic("invalid.frame_plan.atomic_order", "Composite child dependency leaks outside scope"),
                )
            }
        }
        return CompositeScopeValidation.Valid(owners.keys)
    }

    private fun invalidComposite(message: String): CompositeScopeValidation.Invalid =
        CompositeScopeValidation.Invalid(diagnostic("invalid.frame_plan.composite_scope", message))

    private fun buildDestinationSchedule(
        taskList: GPUTaskList,
        orderedTasks: List<GPUTask>,
        consumedChildIds: Set<GPUTaskID>,
    ): DestinationScheduleValidation {
        val tasksById = taskList.tasks.associateBy(GPUTask::taskId)
        val renderTasks = taskList.tasks.filterIsInstance<GPUTask.Render>().associateBy(GPUTask::taskId)
        val orderedIndex = orderedTasks.withIndex().associate { it.value.taskId to it.index }
        val directDependencies = taskList.dependencies.map { it.fromTaskId to it.toTaskId }.toSet()
        val operationsBeforeTask = linkedMapOf<GPUTaskID, MutableList<ScheduledDestinationOperation>>()
        val destinationTaskIds = mutableSetOf<GPUTaskID>()
        val refusalSourceTaskByRefusedTask = mutableMapOf<GPUTaskID, GPUTaskID>()
        val consumerPacketIds = mutableSetOf<GPUDrawPacketID>()
        val snapshotIntervals = mutableMapOf<GPUFrameTextureRef, MutableList<IntRange>>()

        orderedTasks.filterIsInstance<GPUTask.DestinationSnapshots>().forEach { destination ->
            destinationTaskIds += destination.taskId
            val payload = destination.payload
            if (payload.grouping.groups.isEmpty() && payload.grouping.refusals.isEmpty()) {
                return invalidDestination("invalid.frame_plan.destination_group", "Destination task is empty")
            }
            if (payload.grouping.groups.any {
                    it.key.deviceGeneration != taskList.capabilitySeal.deviceGeneration
                }
            ) {
                return invalidDestination(
                    "invalid.frame_plan.capability_seal",
                    "Destination grouping device generation does not match the frame capability seal",
                )
            }

            payload.operations.forEach { operation ->
                val group = payload.grouping.groups[operation.groupIndex]
                val memberIds = group.members.map { it.commandId }
                val consumerIds = operation.consumers.map { it.groupingCommandId }
                if (memberIds.distinct().size != memberIds.size ||
                    consumerIds.distinct().size != consumerIds.size ||
                    memberIds != consumerIds ||
                    group.members.zipWithNext().any { (first, second) ->
                        first.accessIndex >= second.accessIndex
                    }
                ) {
                    return invalidDestination(
                        "invalid.frame_plan.destination_consumer_binding",
                        "Every Task 5 member must have one ordered consumer",
                    )
                }
                if (operation.consumers.isEmpty()) {
                    return invalidDestination(
                        "invalid.frame_plan.destination_consumer_binding",
                        "Destination snapshot operation has no consumer",
                    )
                }

                val consumerTasks = mutableListOf<GPUTask.Render>()
                val consumerPoints = mutableListOf<Pair<GPUTask.Render, Int>>()
                operation.consumers.forEach { consumer ->
                    val render = renderTasks[consumer.renderTaskId]
                        ?: return invalidDestination(
                            "invalid.frame_plan.destination_consumer_binding",
                            "Destination consumer render task is missing",
                        )
                    val packetIndex = render.drawPackets.indexOfFirst { it.packetId == consumer.packetId }
                    val packet = render.drawPackets.getOrNull(packetIndex)
                    if (render.recordingId != destination.recordingId ||
                        render.taskId in consumedChildIds ||
                        render.target.value != group.key.target.value ||
                        packet == null || packet.packetId != consumer.packetId ||
                        packet.commandIdValue != consumer.commandId.value ||
                        packet.blendPlan?.destinationReadRequirement !=
                        GPUBlendDestinationReadRequirement.DestinationTextureRequired ||
                        (destination.taskId to render.taskId) !in directDependencies ||
                        !consumerPacketIds.add(packet.packetId)
                    ) {
                        return invalidDestination(
                            "invalid.frame_plan.destination_consumer_binding",
                            "Destination consumer task, packet, command, or dependency is not exact",
                        )
                    }
                    consumerTasks += render
                    consumerPoints += render to packetIndex
                }

                val executionPoints = consumerPoints.map { (render, packetIndex) ->
                    orderedIndex.getValue(render.taskId) to packetIndex
                }
                if (executionPoints.zipWithNext().any { (first, second) ->
                        first.first > second.first ||
                            (first.first == second.first && first.second >= second.second)
                    }
                ) {
                    return invalidDestination(
                        "invalid.frame_plan.destination_consumer_binding",
                        "Destination consumers must preserve Task 5 access order",
                    )
                }

                if (operation is GPUDestinationSnapshotOperation.CopyAsDraw &&
                    taskList.capabilitySeal.copyAsDrawCapability?.available != true
                ) {
                    return invalidDestination(
                        "unsupported.destination_read.copy_unavailable",
                        "CopyAsDraw is absent from the canonical frame capability seal",
                    )
                }

                val consumerTaskIds = consumerTasks.map { it.taskId }.toSet()
                val firstConsumer = consumerTasks.minBy { orderedIndex.getValue(it.taskId) }
                val firstIndex = consumerTasks.minOf { orderedIndex.getValue(it.taskId) }
                val lastIndex = consumerTasks.maxOf { orderedIndex.getValue(it.taskId) }
                val lastConsumerPacketIndex = consumerPoints
                    .filter { (render) -> orderedIndex.getValue(render.taskId) == lastIndex }
                    .maxOf { (_, packetIndex) -> packetIndex }
                val operationConsumerPacketIds = operation.consumers.map { it.packetId }.toSet()
                val unsafeWrite = orderedTasks.subList(firstIndex, lastIndex + 1).any { candidate ->
                    when (candidate) {
                        is GPUTask.Render -> {
                            val candidateIndex = orderedIndex.getValue(candidate.taskId)
                            val packetsInSnapshotLifetime = if (candidateIndex == lastIndex) {
                                candidate.drawPackets.take(lastConsumerPacketIndex + 1)
                            } else {
                                candidate.drawPackets
                            }
                            packetsInSnapshotLifetime.any { packet ->
                                packet.packetId !in operationConsumerPacketIds &&
                                    (candidate.packetWrites(packet, operation.source) ||
                                        candidate.packetWrites(packet, operation.snapshot))
                            }
                        }
                        else -> candidate.taskId !in consumerTaskIds &&
                            (candidate.writes(operation.source) || candidate.writes(operation.snapshot))
                    }
                }
                if (unsafeWrite) {
                    return invalidDestination(
                        "invalid.frame_plan.destination_order",
                        "A source or snapshot write invalidates destination-read consumers",
                    )
                }
                val interval = firstIndex..lastIndex
                val intervals = snapshotIntervals.getOrPut(operation.snapshot, ::mutableListOf)
                if (intervals.any { existing ->
                        interval.first <= existing.last && existing.first <= interval.last
                    }
                ) {
                    return invalidDestination(
                        "invalid.frame_plan.destination_snapshot_alias",
                        "Destination snapshot aliases must have non-overlapping consumer lifetimes",
                    )
                }
                intervals += interval
                operationsBeforeTask.getOrPut(firstConsumer.taskId, ::mutableListOf) +=
                    ScheduledDestinationOperation(destination.taskId, group.key, operation)
            }

            val refusalIds = payload.grouping.refusals.map { it.commandId }
            val bindingIds = payload.refusalBindings.map { it.groupingCommandId }
            if (refusalIds.distinct().size != refusalIds.size || refusalIds != bindingIds) {
                return invalidDestination(
                    "invalid.frame_plan.destination_consumer_binding",
                    "Every Task 5 refusal must have one ordered refused-task binding",
                )
            }
            payload.grouping.refusals.zip(payload.refusalBindings).forEach { (refusal, binding) ->
                val refusedTask = tasksById[binding.refusedTaskId] as? GPUTask.Refused
                if (refusedTask == null || refusedTask.recordingId != destination.recordingId ||
                    refusedTask.scope != GPURefusalScope.RefusedLeafDrawStep ||
                    refusedTask.commandId != binding.commandId ||
                    refusedTask.diagnostic.code.value != refusal.code ||
                    (destination.taskId to refusedTask.taskId) !in directDependencies ||
                    refusalSourceTaskByRefusedTask.put(refusedTask.taskId, destination.taskId) != null
                ) {
                    return invalidDestination(
                        "invalid.frame_plan.destination_consumer_binding",
                        "Task 5 refusal does not match one exact refused task",
                    )
                }
            }
        }

        return DestinationScheduleValidation.Valid(
            operationsBeforeTask = operationsBeforeTask,
            destinationTaskIds = destinationTaskIds,
            refusalSourceTaskByRefusedTask = refusalSourceTaskByRefusedTask,
            consumerPacketIds = consumerPacketIds,
        )
    }

    private fun invalidDestination(code: String, message: String): DestinationScheduleValidation.Invalid =
        DestinationScheduleValidation.Invalid(diagnostic(code, message))

    private fun validateBlendPlans(
        taskList: GPUTaskList,
        destinationConsumerPacketIds: Set<GPUDrawPacketID>,
    ): GPUDiagnostic? {
        taskList.tasks.filterIsInstance<GPUTask.Render>().forEach { task ->
            task.drawPackets.forEach { packet ->
                when (val blend = requireNotNull(packet.blendPlan)) {
                    is GPUBlendPlan.UnsupportedBlend -> when (blend.refusalScope) {
                        GPURefusalScope.RefusedLeafDrawStep -> if (task.drawPackets.size != 1) {
                            return diagnostic(
                                "invalid.frame_plan.blend_refusal_scope",
                                "Leaf blend refusal must own one exact packet",
                            )
                        }
                        GPURefusalScope.RefusedCompositeCommand,
                        GPURefusalScope.AtomicFrameFailure,
                        -> return blend.diagnostic.toCanonicalDiagnostic()
                    }
                    else -> Unit
                }
                if (packet.blendPlan.destinationReadRequirement ==
                    GPUBlendDestinationReadRequirement.DestinationTextureRequired &&
                    packet.packetId !in destinationConsumerPacketIds
                ) {
                    return diagnostic(
                        "invalid.frame_plan.destination_read_unbound",
                        "Destination-reading packet has no exact Task 5 copy or refusal association",
                    )
                }
            }
        }
        return null
    }

    private fun linearize(
        taskList: GPUTaskList,
        orderedTasks: List<GPUTask>,
        consumedChildIds: Set<GPUTaskID>,
        orderedTaskIndex: Map<GPUTaskID, Int>,
        destinationSchedule: DestinationScheduleValidation.Valid,
    ): Linearization {
        val steps = mutableListOf<GPUFrameStep>()
        val diagnostics = mutableListOf<GPUDiagnostic>()
        val openedRenderSegments = mutableSetOf<RenderContinuationKey>()
        var index = 0
        while (index < orderedTasks.size) {
            val task = orderedTasks[index]
            if (task.taskId in consumedChildIds || task.taskId in destinationSchedule.destinationTaskIds) {
                index += 1
                continue
            }

            destinationSchedule.operationsBeforeTask[task.taskId]?.forEach { scheduled ->
                steps += scheduled.toStep(taskList.capabilitySeal)
            }

            if (task is GPUTask.Render) {
                val unsupported = task.drawPackets.singleOrNull()?.blendPlan as? GPUBlendPlan.UnsupportedBlend
                if (unsupported != null) {
                    val refusal = unsupported.diagnostic.toCanonicalDiagnostic()
                    steps += GPUFrameStep.RefusedLeafDrawStep(
                        commandId = org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(
                            task.drawPackets.single().commandIdValue,
                        ),
                        diagnostic = refusal,
                        sourceTaskIds = listOf(task.taskId),
                    )
                    diagnostics += refusal
                    index += 1
                    continue
                }
                if (task.drawPackets.all { it.blendPlan is GPUBlendPlan.NoOp }) {
                    index += 1
                    continue
                }

                val renderTasks = mutableListOf(task)
                var nextIndex = index + 1
                while (nextIndex < orderedTasks.size) {
                    val next = orderedTasks[nextIndex]
                    if (next.taskId in consumedChildIds) {
                        nextIndex += 1
                        continue
                    }
                    if (next is GPUTask.Render &&
                        next.drawPackets.all { it.blendPlan is GPUBlendPlan.NoOp }
                    ) {
                        nextIndex += 1
                        continue
                    }
                    if (next !is GPUTask.Render ||
                        destinationSchedule.operationsBeforeTask.containsKey(next.taskId) ||
                        next.drawPackets.any { it.blendPlan is GPUBlendPlan.NoOp ||
                            it.blendPlan is GPUBlendPlan.UnsupportedBlend } ||
                        !task.canShareProvisionalSegment(next)
                    ) break
                    renderTasks += next
                    nextIndex += 1
                }
                val continuationKey = task.continuationKey()
                val renderStep = batchRenderSegment(
                    tasks = renderTasks,
                    continuesStoredTarget = continuationKey in openedRenderSegments,
                )
                    ?: return Linearization.Refused(
                        diagnostic("invalid.frame_plan.render_batch", "Batching lost a draw packet"),
                    )
                steps += renderStep
                openedRenderSegments += continuationKey
                index = nextIndex
                continue
            }

            when (task) {
                is GPUTask.Render -> error("handled above")
                is GPUTask.PrepareResources -> steps += GPUFrameStep.PrepareResourcesStep(
                    task.requests,
                    listOf(task.taskId),
                )
                is GPUTask.Compute -> steps += GPUFrameStep.ComputePassStep(
                    task.target,
                    task.resourceUses,
                    task.dispatches,
                    listOf(task.taskId),
                )
                is GPUTask.Copy -> steps += GPUFrameStep.CopyResourceStep(
                    task.source,
                    task.destination,
                    task.regions,
                    listOf(task.taskId),
                )
                is GPUTask.Upload -> steps += GPUFrameStep.UploadResourceStep(
                    task.staging,
                    task.destination,
                    task.layout,
                    listOf(task.taskId),
                )
                is GPUTask.Barrier -> steps += GPUFrameStep.DependencyBarrierStep(
                    task.orderedUseTokens,
                    task.reasonCode,
                    listOf(task.taskId),
                )
                is GPUTask.DestinationSnapshots -> error("destination tasks are attached to consumers")
                is GPUTask.TargetTransition -> steps += GPUFrameStep.TargetTransitionStep(
                    task.parent,
                    task.child,
                    task.transitionKind,
                    listOf(task.taskId),
                )
                is GPUTask.Readback -> steps += GPUFrameStep.ReadbackCopyStep(
                    task.source,
                    task.staging,
                    task.request,
                    listOf(task.taskId),
                )
                is GPUTask.Output -> {
                    steps += GPUFrameStep.AcquireSurfaceOutput(task.descriptor, listOf(task.taskId))
                    steps += GPUFrameStep.SurfaceBlitRenderPassStep(
                        task.scene,
                        task.descriptor.output,
                        listOf(task.taskId),
                    )
                    steps += GPUFrameStep.PostSubmitPresentAction(task.descriptor.output, listOf(task.taskId))
                }
                is GPUTask.Refused -> when (task.scope) {
                    GPURefusalScope.RefusedLeafDrawStep -> {
                        val destinationSource = destinationSchedule.refusalSourceTaskByRefusedTask[task.taskId]
                        val sourceIds = listOfNotNull(destinationSource, task.taskId)
                            .sortedBy { orderedTaskIndex.getValue(it) }
                        steps += GPUFrameStep.RefusedLeafDrawStep(task.commandId, task.diagnostic, sourceIds)
                    }
                    GPURefusalScope.RefusedCompositeCommand -> {
                        val sourceIds = (task.consumedChildTaskIds + task.taskId)
                            .sortedBy { orderedTaskIndex.getValue(it) }
                        steps += GPUFrameStep.RefusedCompositeCommandStep(
                            task.commandId,
                            task.provenanceTokens,
                            task.diagnostic,
                            sourceIds,
                        )
                    }
                    GPURefusalScope.AtomicFrameFailure -> error("validated before linearization")
                }
            }
            index += 1
        }
        return Linearization.Planned(steps, diagnostics)
    }

    private fun batchRenderSegment(
        tasks: List<GPUTask.Render>,
        continuesStoredTarget: Boolean,
    ): GPUFrameStep.RenderPassStep? {
        val first = tasks.first()
        val packets = tasks.flatMap(GPUTask.Render::drawPackets)
            .filterNot { it.blendPlan is GPUBlendPlan.NoOp }
        val packetOwner = buildMap<GPUDrawPacketID, GPUTaskID> {
            tasks.forEach { task -> task.drawPackets.forEach { put(it.packetId, task.taskId) } }
        }
        val eligibility = buildMap {
            tasks.forEach { task -> putAll(task.batchEligibilityByPacketId) }
        }.filterKeys { it in packets.map(GPUDrawPacket::packetId) }
        val batchPlan = GPUPassBatcher().plan(
            GPUPassBatcherRequest(
                segmentKey = first.provisionalSegmentKey,
                packets = packets,
                eligibilityByPacketId = eligibility,
            ),
        )
        if (batchPlan.batches.flatMap { it.packets }.map { it.packetId } != packets.map { it.packetId }) {
            return null
        }
        val frameBatches = batchPlan.batches.map { batch ->
            GPUFrameRenderBatch(
                batchId = batch.batchId,
                kind = batch.kind,
                packets = batch.packets,
                sourceTaskIds = batch.packets.map { packetOwner.getValue(it.packetId) }.distinct(),
            )
        }
        return GPUFrameStep.RenderPassStep(
            target = first.target,
            loadStore = if (continuesStoredTarget) {
                first.loadStore.copy(loadOp = "load", clearColorLabel = null)
            } else {
                first.loadStore
            },
            samplePlan = first.samplePlan,
            drawPackets = packets,
            sourceTaskIds = packets.map { packetOwner.getValue(it.packetId) }.distinct(),
            batches = frameBatches,
        )
    }

    private fun ScheduledDestinationOperation.toStep(
        capabilitySeal: GPUFrameCapabilitySeal,
    ): GPUFrameStep = when (val value = operation) {
        is GPUDestinationSnapshotOperation.TextureCopy -> GPUFrameStep.CopyDestinationStep(
            source = value.source as GPUFrameTargetRef,
            sourceKey = sourceKey,
            snapshot = value.snapshot,
            logicalBounds = value.logicalBounds,
            copyLayout = value.copyLayout,
            consumers = value.consumers,
            sourceTaskIds = listOf(sourceTaskId),
        )
        is GPUDestinationSnapshotOperation.CopyAsDraw -> GPUFrameStep.CopyAsDrawMaterializationStep(
            source = value.source as GPUFrameTextureRef,
            sourceKey = sourceKey,
            sourceIntermediate = value.sourceIntermediate,
            snapshot = value.snapshot,
            logicalBounds = value.logicalBounds,
            capabilitySealHash = capabilitySeal.sealHash,
            consumers = value.consumers,
            sourceTaskIds = listOf(sourceTaskId),
        )
    }

    private fun GPUTask.Render.canShareProvisionalSegment(other: GPUTask.Render): Boolean =
        target == other.target &&
            loadStore == other.loadStore &&
            samplePlan == other.samplePlan &&
            provisionalSegmentKey == other.provisionalSegmentKey &&
            drawPackets.first().targetStateHash == other.drawPackets.first().targetStateHash

    private fun GPUTask.Render.continuationKey(): RenderContinuationKey = RenderContinuationKey(
        target = target,
        samplePlan = samplePlan,
        provisionalSegmentKey = provisionalSegmentKey,
    )

    private fun GPUTask.Render.packetWrites(
        packet: GPUDrawPacket,
        resource: GPUFrameResourceRef,
    ): Boolean = target == resource &&
        packet.blendPlan !is GPUBlendPlan.NoOp &&
        packet.blendPlan !is GPUBlendPlan.UnsupportedBlend

    private fun GPUTask.writes(resource: GPUFrameResourceRef): Boolean = when (this) {
        is GPUTask.Render -> target == resource && drawPackets.any { packet ->
            packet.blendPlan !is GPUBlendPlan.NoOp && packet.blendPlan !is GPUBlendPlan.UnsupportedBlend
        }
        is GPUTask.Compute -> target == resource || resourceUses.any { it.write && it.resource == resource }
        is GPUTask.Copy -> destination == resource
        is GPUTask.Upload -> destination == resource
        else -> false
    }

    private fun GPUDrawPacketRole.isRenderEncodable(): Boolean = when (this) {
        GPUDrawPacketRole.Shading,
        GPUDrawPacketRole.DepthOnly,
        GPUDrawPacketRole.StencilProducer,
        GPUDrawPacketRole.StencilConsumer,
        GPUDrawPacketRole.ClipProducer,
        GPUDrawPacketRole.Clear,
        GPUDrawPacketRole.Composite,
        -> true
        GPUDrawPacketRole.Discard,
        GPUDrawPacketRole.Copy,
        GPUDrawPacketRole.Upload,
        GPUDrawPacketRole.Compute,
        GPUDrawPacketRole.Readback,
        -> false
    }

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

    private fun GPUTaskList.atomicallyRefused(value: GPUDiagnostic): GPUFramePlan =
        GPUFramePlan(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals.sortedBy(GPURecordingSeal::insertionOrder),
            steps = emptyList(),
            memoryBudget = memoryBudget,
            diagnostics = diagnostics + value,
            dependencies = dependencies,
            phaseOrder = phaseOrder,
            elidedNoOpDraws = tasks.elidedNoOpDraws(),
            atomicallyRefused = true,
        )

    private fun List<GPUTask>.elidedNoOpDraws(): List<GPUFrameElidedNoOpDraw> =
        filterIsInstance<GPUTask.Render>().flatMap { task ->
            task.drawPackets.mapNotNull { packet ->
                val noOp = packet.blendPlan as? GPUBlendPlan.NoOp ?: return@mapNotNull null
                GPUFrameElidedNoOpDraw(
                    taskId = task.taskId,
                    packetId = packet.packetId,
                    commandId = org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(
                        packet.commandIdValue,
                    ),
                    mode = noOp.mode,
                    reason = noOp.reason,
                )
            }
        }

    private fun org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDiagnostic.toCanonicalDiagnostic():
        GPUDiagnostic = GPUDiagnostic(
            code = GPUDiagnosticCode(code),
            domain = GPUDiagnosticDomain.Passes,
            severity = if (terminal) GPUDiagnosticSeverity.Error else GPUDiagnosticSeverity.Warning,
            message = message,
            isTerminal = terminal,
        )

    private fun diagnostic(code: String, message: String): GPUDiagnostic = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Recording,
        severity = GPUDiagnosticSeverity.Error,
        message = message,
    )

    private sealed interface CompositeScopeValidation {
        data class Valid(val consumedChildIds: Set<GPUTaskID>) : CompositeScopeValidation
        data class Invalid(val diagnostic: GPUDiagnostic) : CompositeScopeValidation
    }

    private sealed interface DestinationScheduleValidation {
        data class Valid(
            val operationsBeforeTask: Map<GPUTaskID, List<ScheduledDestinationOperation>>,
            val destinationTaskIds: Set<GPUTaskID>,
            val refusalSourceTaskByRefusedTask: Map<GPUTaskID, GPUTaskID>,
            val consumerPacketIds: Set<GPUDrawPacketID>,
        ) : DestinationScheduleValidation

        data class Invalid(val diagnostic: GPUDiagnostic) : DestinationScheduleValidation
    }

    private data class ScheduledDestinationOperation(
        val sourceTaskId: GPUTaskID,
        val sourceKey: GPUDestinationSnapshotGroupKey,
        val operation: GPUDestinationSnapshotOperation,
    )

    private data class RenderContinuationKey(
        val target: GPUFrameTargetRef,
        val samplePlan: org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan,
        val provisionalSegmentKey: GPUProvisionalRenderSegmentKey,
    )

    private sealed interface Linearization {
        data class Planned(
            val steps: List<GPUFrameStep>,
            val diagnostics: List<GPUDiagnostic>,
        ) : Linearization

        data class Refused(val diagnostic: GPUDiagnostic) : Linearization
    }
}
