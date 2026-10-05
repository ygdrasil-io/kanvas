package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.materials.declaredNoiseSlabV1
import org.graphiks.kanvas.gpu.renderer.materials.NOISE_TABLE_ALLOCATION_LABEL_V1
import org.graphiks.kanvas.render.ir.*

/** Exact native packet partitions, issued only after all constituent lowerers succeeded. */
internal class W5aCompositeFrameAuthorityV1 private constructor(
    val sessionIdentity: String,
    private val taskList: GPUTaskList,
    lanes: List<Lane>,
) {
    class Lane(val planId: String, val capabilityId: String, renders: List<GPUTask.Render>, val standaloneTaskList: GPUTaskList) {
        val renders = renders.toList()
        val packets = renders.flatMap { it.drawPackets }
    }
    val lanes = lanes.toList()
    val readbackRequestId: String = taskList.tasks.filterIsInstance<GPUTask.Readback>().single().request.requestId.value
    fun owns(packet: GPUDrawPacket): Boolean = lanes.any { lane -> lane.packets.any { it === packet } }
    fun validates(frame: GPUFramePlan, renders: List<GPUFrameStep.RenderPassStep>): Boolean =
        frame.frameId == taskList.frameId && frame.capabilitySeal == taskList.capabilitySeal &&
            frame.memoryBudget == taskList.memoryBudget && frame.dependencies == taskList.dependencies &&
            frame.recordingSeals == taskList.recordingSeals && frame.phaseOrder == taskList.phaseOrder &&
            !frame.atomicallyRefused && frame.diagnostics.isEmpty() && frame.elidedNoOpDraws.isEmpty() &&
            frame.steps.size == taskList.tasks.size &&
            (frame.steps.firstOrNull() as? GPUFrameStep.PrepareResourcesStep)?.let { actual ->
                val expected = taskList.tasks.first() as GPUTask.PrepareResources
                actual.requests == expected.requests && actual.sourceTaskIds == listOf(expected.taskId)
            } == true &&
            (frame.steps.lastOrNull() as? GPUFrameStep.ReadbackCopyStep)?.let { actual ->
                val expected = taskList.tasks.last() as GPUTask.Readback
                actual.source == expected.source && actual.staging == expected.staging &&
                    actual.request == expected.request && actual.sourceTaskIds == listOf(expected.taskId)
            } == true &&
            renders.size == lanes.sumOf { it.renders.size } &&
            renders.zip(lanes.flatMap { it.renders }).all { (actual, expected) ->
                actual.target == expected.target && actual.loadStore == expected.loadStore &&
                    actual.sourceTaskIds == listOf(expected.taskId) && actual.samplePlan == expected.samplePlan &&
                    actual.resourceUses == expected.resourceUses && actual.depthStencilLoadStore == expected.depthStencilLoadStore &&
                    actual.sampleContinuation == null && expected.sampleContinuationKey == null &&
                    actual.preparedImageBindingsByPacketId == expected.preparedImageBindingsByPacketId &&
                    actual.preparedTextBindingsByPacketId == expected.preparedTextBindingsByPacketId &&
                    actual.drawPackets.size == expected.drawPackets.size &&
                    actual.drawPackets.zip(expected.drawPackets).all { (a, b) -> a === b && a.w5aCompositeFrameAuthority === this }
            }
    companion object {
        fun issue(sessionIdentity: String, taskList: GPUTaskList, lanes: List<Lane>): W5aCompositeFrameAuthorityV1 {
            require(lanes.size >= 2 && lanes.all { it.packets.isNotEmpty() })
            val packets = lanes.flatMap { it.packets }
            require(packets.map { it.packetId }.distinct().size == packets.size)
            return W5aCompositeFrameAuthorityV1(sessionIdentity, taskList, lanes)
        }
    }
}

/** Joins validated lane envelopes; never compiles a material or reconstructs geometry. */
internal class W5aCompositeGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val composite = request.graph.w5aCompositePlanOrNull()
            ?: return invalid("Composite graph lacks its compiler authority")
        val session = "w5a.mixed.${request.deviceGeneration.value}.${request.graph.id.value}"
        val lowered = mutableListOf<GpuPlanLoweringResult.Lowered>()
        for ((ordinal, graph) in composite.lanes().withIndex()) {
            when (val result = GpuPlanTaskListLowerer().lower(request.copy(graph = graph,
                w5aCompositeSessionIdentity = session, w5aCompositeLaneOrdinal = ordinal))) {
                is GpuPlanLoweringResult.Lowered -> lowered += result
                else -> return result
            }
        }
        val first = lowered.first().taskList
        val prepare = first.tasks.filterIsInstance<GPUTask.PrepareResources>().single()
        val readback = lowered.last().taskList.tasks.filterIsInstance<GPUTask.Readback>().single()
        val lanes = lowered.mapIndexed { ordinal, result ->
            val graph = composite.lanes()[ordinal]
            val renders = result.taskList.tasks.filterIsInstance<GPUTask.Render>().mapIndexed { index, render ->
                GPUTask.Render(render.taskId, render.recordingId, render.phase, render.target,
                    if (ordinal > 0 && index == 0) GPULoadStorePlan("load", render.loadStore.storePlan) else render.loadStore,
                    render.samplePlan, render.resourceUses,
                    GPUProvisionalRenderSegmentKey("$session.lane.$ordinal.pass.$index"), render.drawPackets,
                    render.batchEligibilityByPacketId, render.sampleContinuationKey, render.compositeMembership,
                    render.depthStencilLoadStore, render.preparedImageBindingsByPacketId, render.preparedTextBindingsByPacketId)
            }
            W5aCompositeFrameAuthorityV1.Lane(graph.id.value, graph.capabilityId, renders, result.taskList)
        }
        val tasks = listOf(prepare) + lanes.flatMap { it.renders } + readback
        val rectAllocations = lanes.flatMapIndexed { ordinal, lane ->
            val scratch = lane.packets.first().corePrimitivePreparedAuthority?.w3SessionScratch
            val declared = composite.rectScratchBytesI64(ordinal)
            if (scratch == null) {
                require(declared.isEmpty())
                emptyList()
            } else {
                val actual = listOf(scratch.poolCapacities.vertexBytes, scratch.poolCapacities.indexBytes, scratch.poolCapacities.uniformBytes)
                require(actual == declared) { "Composite Rect scratch differs from its compiler reservations" }
                listOf("vertex", "index", "uniform").zip(actual).map { (role, bytes) ->
                    GPUFrameMemoryAllocation("$session.$role.lane.$ordinal", GPUFrameMemoryCategory.ReusableScratch,
                        bytes, GPUFrameMemoryResourceKind.Buffer, null)
                }
            }
        }
        var stopSlabCounted = false
        val noiseSlabs=composite.lanes().mapNotNull { it.declaredNoiseSlabV1() }.distinct()
        require(noiseSlabs.size <= 1)
        var noiseSlabCounted = false
        val allocations = (rectAllocations + lowered.flatMapIndexed { ordinal, result -> result.taskList.memoryBudget.allocations.mapNotNull { allocation ->
            if (allocation.category == GPUFrameMemoryCategory.CanonicalTarget || allocation.category == GPUFrameMemoryCategory.ReadbackStaging) {
                allocation.takeIf { ordinal == 0 }
            } else if (allocation.label == "$session.gradient-stops") {
                require(allocation.bytes == composite.materialTable.gradientStopSlab?.byteSizeI64)
                if (stopSlabCounted) null else allocation.also { stopSlabCounted = true }
            } else if (allocation.label == NOISE_TABLE_ALLOCATION_LABEL_V1) {
                val slab=requireNotNull(composite.lanes()[ordinal].declaredNoiseSlabV1())
                require(slab === noiseSlabs.single() && allocation.bytes == slab.byteCountI64 &&
                    allocation.category == GPUFrameMemoryCategory.ReusableScratch &&
                    allocation.resourceKind == GPUFrameMemoryResourceKind.Buffer && allocation.extent == null)
                if(noiseSlabCounted) null else allocation.also { noiseSlabCounted = true }
            } else allocation.copy(label = "${allocation.label}.lane.$ordinal")
        } }).map { it.copy(firstPassIndex = 0, lastPassIndexExclusive = tasks.size) }
        require(noiseSlabCounted == noiseSlabs.isNotEmpty())
        val budget = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            minOf(request.currentBudget.maxFrameLocalBytes, request.rendererAggregateMemoryBudgetBytes ?: Long.MAX_VALUE),
            requireNotNull(request.capabilities.limits)))
        if (budget.diagnostic != null || Math.addExact(budget.targetResidentBytes, budget.peakFrameTransientBytes) != composite.peakFrameLocalBytesI64)
            return invalid("Composite resource lifetime or aggregate memory budget is inconsistent")
        val originalDependencies = lowered.flatMap { it.taskList.dependencies }
        val dependencies = tasks.zipWithNext().mapIndexed { index, (before, after) ->
            originalDependencies.firstOrNull { it.fromTaskId == before.taskId && it.toTaskId == after.taskId }
                ?: GPUTaskDependency(before.taskId, after.taskId, "w5a-composite-order", GPUTaskUseToken("$session.edge.$index"), "w5a-native-lane-order")
        }
        val taskList = GPUTaskList(first.frameId, first.capabilitySeal, first.recordingSeals,
            first.expectedReplayKeyHash, tasks, dependencies, first.phaseOrder, budget)
        val authority = W5aCompositeFrameAuthorityV1.issue(session, taskList, lanes)
        lanes.flatMap { it.packets }.forEach { it.attachW5aCompositeFrameAuthority(authority) }
        GpuPlanLoweringResult.Lowered(taskList, readback.request.requestId.value)
    } catch (failure: IllegalArgumentException) {
        invalid(failure.message ?: "Invalid composite frame")
    } catch (_: ArithmeticException) {
        invalid("Composite resource accounting overflows I64")
    }

    private fun invalid(message: String): GpuPlanLoweringResult.InvalidPlan = GpuPlanLoweringResult.InvalidPlan(
        RenderDiagnostic(RenderDiagnosticCode("w5a.composite.invalid-plan"), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, message))
}
