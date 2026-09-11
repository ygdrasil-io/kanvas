package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.snapshotForFramePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Unforgeable renderer token for the sealed initial clear, never a synthetic draw. */
class W5bInitialClearV3 private constructor(
    internal val witness: W5bPreparedFrameWitnessV3?,
    internal val clearOnly: W5bClearOnlyFrameWitnessV3?,
) {
    internal constructor(witness: W5bPreparedFrameWitnessV3) : this(witness, null)
    internal constructor(witness: W5bClearOnlyFrameWitnessV3) : this(null, witness)
    internal val graph: RenderGraph get() = witness?.graph ?: requireNotNull(clearOnly).graph
    internal fun matches(target: GPUFrameTargetRef,
        loadStore: GPULoadStorePlan, sample: GPUSamplePlan): Boolean {
        val first = graph.passes().filterIsInstance<PlanPass.RenderPass>().firstOrNull() ?: return false
        return first.draws().isEmpty() && first.destinationVersionAfter?.valueI64 == 0L &&
            first.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent &&
            first.store == org.graphiks.kanvas.gpu.plan.AttachmentStorePlan.Store &&
            target == (witness?.scratch?.target ?: clearOnly?.target) && loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            sample == GPUSamplePlan.SingleSampleFrame
    }
}

/** Output initialization only: no material, geometry scratch, pipeline or destination snapshot. */
internal class W5bClearOnlyFrameWitnessV3(
    val graph: RenderGraph,
    val target: GPUFrameTargetRef,
    val staging: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef,
    private val capabilitySeal: GPUFrameCapabilitySeal,
    private val recordingSeal: GPURecordingSeal,
    memoryBudget: GPUFrameMemoryBudgetPlan,
    targetPreparation: GPUResourcePreparationRequest,
    stagingPreparation: GPUResourcePreparationRequest,
    private val readbackRequest: GPUFrameReadbackRequest,
) {
    val capabilitySealHash: String get() = capabilitySeal.sealHash
    val prepareTaskId = GPUTaskID("task.w5b.${graph.id.value}.prepare")
    val clearTaskId = GPUTaskID("task.w5b.${graph.id.value}.initial-clear")
    val readbackTaskId = GPUTaskID("task.w5b.${graph.id.value}.readback")
    val dependencies = immutableList(listOf(prepareTaskId, clearTaskId, readbackTaskId).zipWithNext { before, after ->
        GPUTaskDependency(before, after, "w5b-clear-order", GPUTaskUseToken("${before.value}->${after.value}"), "w5b-clear-order")
    })
    private val preparations = immutableList(listOf(targetPreparation, stagingPreparation))
    private val memory = memoryBudget.snapshotForFramePlan()

    init {
        require(graph.visualCommandCount == 0 && graph.materialPlanTableOrNull() == null)
        require(graph.passes().size == 2 && graph.resources().size == 2)
        val clear = graph.passes().first() as PlanPass.RenderPass
        require(clear.draws().isEmpty() && clear.destinationVersionAfter?.valueI64 == 0L)
        require(clear.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent)
        require(clear.store == org.graphiks.kanvas.gpu.plan.AttachmentStorePlan.Store)
        require(graph.passes().last() is PlanPass.ReadbackPass)
        require(targetPreparation.resource == target && stagingPreparation.resource == staging)
        require(recordingSeal.capabilitySealHash == capabilitySealHash)
    }

    /** Exact post-surface-split frame: no filtered-away steps or additional authorities. */
    fun validates(frame: GPUFramePlan): Boolean {
        if (frame.steps.size != 3 || frame.capabilitySeal !== capabilitySeal || frame.frameId != capabilitySeal.frameId ||
            frame.recordingSeals != listOf(recordingSeal) || frame.memoryBudget != memory ||
            frame.dependencies != dependencies || frame.phaseOrder != GPUTaskPhase.entries ||
            frame.diagnostics.isNotEmpty() || frame.elidedNoOpDraws.isNotEmpty() || frame.atomicallyRefused) return false
        val prepare = frame.steps[0] as? GPUFrameStep.PrepareResourcesStep ?: return false
        val clear = frame.steps[1] as? GPUFrameStep.RenderPassStep ?: return false
        val readback = frame.steps[2] as? GPUFrameStep.ReadbackCopyStep ?: return false
        return prepare.sourceTaskIds == listOf(prepareTaskId) && prepare.requests == preparations &&
            clear.sourceTaskIds == listOf(clearTaskId) && clear.w5bInitialClearV3?.clearOnly === this &&
            clear.drawPackets.isEmpty() && clear.target == target && clear.loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            clear.samplePlan == GPUSamplePlan.SingleSampleFrame && clear.resourceUses.isEmpty() && clear.batches.isEmpty() &&
            clear.frameProvenanceByPacketId.isEmpty() && clear.preparedImageBindingsByPacketId.isEmpty() &&
            clear.preparedTextBindingsByPacketId.isEmpty() && clear.sampleContinuation == null &&
            clear.depthStencilLoadStore == null && clear.w4eMaskContinuation == null && clear.w4eSceneContinuation == null &&
            readback.sourceTaskIds == listOf(readbackTaskId) && readback.request == readbackRequest &&
            readback.source == target && readback.staging == staging &&
            readback.request.requestId.value == "w3.${graph.id.value}.readback"
    }
}

/** Separate W5b execution authority; the W3 object is reused solely for exact V/I/U packing. */
internal class W5bPreparedFrameWitnessV3(
    val graph: RenderGraph,
    val scratch: W5bGeometryScratchV3,
    private val capabilitySeal: GPUFrameCapabilitySeal,
    private val recordingSeal: GPURecordingSeal,
    memoryBudget: GPUFrameMemoryBudgetPlan,
    targetPreparation: GPUResourcePreparationRequest,
    stagingPreparation: GPUResourcePreparationRequest,
    private val readbackRequest: GPUFrameReadbackRequest,
    packets: List<GPUDrawPacket>,
    val clipPrefixV4: org.graphiks.kanvas.gpu.renderer.planning.W4eClipGraphLowerer.ClipPrefixV4? = null,
    geometryLanes: List<W5bGeometryScratchV3> = listOf(scratch),
) {
    val geometryLanes = immutableList(geometryLanes)
    fun scratchFor(packet: GPUDrawPacket): W5bGeometryScratchV3 = this.geometryLanes.single { packet.packetId in it.packetIds }
    fun packetsFor(lane: W5bGeometryScratchV3): List<GPUDrawPacket> {
        require(this.geometryLanes.any { it === lane })
        return admittedPackets.filter { it.packetId in lane.packetIds }
    }
    private val admittedPackets = immutableList(packets)
    fun ownsGeometryProducer(packet: GPUDrawPacket): Boolean = admittedPackets.any { it === packet } &&
        packet.role == GPUDrawPacketRole.PathStencilProducer &&
        packet.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 === this &&
        packet.corePrimitivePreparedAuthority?.structuralPipelineKey?.blend == GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone &&
        graph.passes().filterIsInstance<PlanPass.StencilGeometryProducerV3>().any {
            it.commandIndexI32 == packet.commandIdValue && (it.id.value == packet.passId ||
                (scratchFor(packet) as? W5bGeometryScratchV3.General)?.ownsProducer(packet, it) == true)
        }
    private val memory = memoryBudget.snapshotForFramePlan()
    private val packetResourceGenerations = org.graphiks.kanvas.gpu.renderer.collections.immutableMap(
        packets.associate { it.packetId to it.resourceGeneration })
    val prepareTaskId = GPUTaskID("task.w5b.${graph.id.value}.prepare")
    val preparations = immutableList(listOfNotNull(targetPreparation, stagingPreparation,
        graph.resources().singleOrNull { it.role == org.graphiks.kanvas.gpu.plan.PlanResourceRole.DestinationSnapshot }?.let {
            val resource = org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef(scratch.target.value.removeSuffix(".target") + ".snapshot")
            GPUResourcePreparationRequest(resource,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor(scratch.targetBounds,
                    org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat.RGBA8UnormSrgb, 1),
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.DestinationSnapshot,
                setOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.CopyDestination,
                    org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding),
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, it.byteSize, resource.value)
        }) + clipPrefixV4?.preparations.orEmpty())
    fun taskId(pass: PlanPass): GPUTaskID = clipPrefixV4?.renders?.singleOrNull {
        it.drawPackets.single().w4ePreparedClipPass?.passId == pass.id.value
    }?.taskId ?: GPUTaskID("task.w5b.${graph.id.value}.${pass.id.value}")
    private val taskIds = immutableList(listOf(prepareTaskId) + graph.passes().map(::taskId))
    val dependencies = immutableList(taskIds.zipWithNext { before, after ->
        fun atomic(id: GPUTaskID) = clipPrefixV4?.renders?.singleOrNull { it.taskId == id }
            ?.drawPackets?.singleOrNull()?.w4ePreparedClipPass?.atomicGroupId ?: graph.passes().singleOrNull { taskId(it) == id }?.let {
                when (it) {
                    is PlanPass.StencilGeometryProducerV3 -> it.atomicGroup.value
                    is PlanPass.StencilCover -> it.atomicGroup.value
                    else -> null
                }
            }
        GPUTaskDependency(before, after, "w5b-version-order", GPUTaskUseToken("${before.value}->${after.value}"),
            "w5b-version-order", atomic(before)?.takeIf { it == atomic(after) }
                ?.let { org.graphiks.kanvas.gpu.renderer.recording.GPUTaskAtomicGroupID(it) })
    })
    // One immutable copy authority is consumed by both task emission and frame validation.
    // The following packet's sealed blend retains the destination version; the copy must
    // refer to precisely that packet/task, never merely to a consumer with the same count.
    private val copies = org.graphiks.kanvas.gpu.renderer.collections.immutableMap(
        graph.passes().mapIndexedNotNull { index, pass ->
            if (pass !is PlanPass.TextureCopy) return@mapIndexedNotNull null
            val consumer = graph.passes()[index + if (graph.passes()[index + 1] is PlanPass.StencilGeometryProducerV3) 2 else 1]
            val draw = when (consumer) {
                is PlanPass.RenderPass -> consumer.draws().single()
                is PlanPass.StencilCover -> consumer.draw
                else -> error("Destination copy is not followed by a color consumer")
            }
            val consumerTarget = when (consumer) {
                is PlanPass.RenderPass -> consumer.target
                is PlanPass.StencilCover -> consumer.target
                else -> error("Invalid destination consumer")
            }
            val blend = draw.blend as org.graphiks.kanvas.gpu.plan.BlendPlan.DestinationReadV1
            val packet = packets.single { it.commandIdValue == draw.commandIndex && it.role != GPUDrawPacketRole.PathStencilProducer }
            require(pass.source == consumerTarget && pass.destination == blend.snapshotResource &&
                pass.destinationVersion == blend.requiredDestinationVersion &&
                packet.blendPlan == org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer.lower(blend))
            pass.id to GPUFrameStep.CopyDestinationStep(
                source = scratch.target,
                sourceKey = org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey(
                    org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity(scratch.target.value),
                    packet.resourceGeneration, capabilitySeal.deviceGeneration,
                    org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat.RGBA8UnormSrgb,
                    org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation.LinearPremul, null, null),
                snapshot = org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef(
                    scratch.target.value.removeSuffix(".target") + ".snapshot"),
                logicalBounds = scratch.targetBounds,
                copyLayout = org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout(
                    (graph.passes().last() as PlanPass.ReadbackPass).bytesPerRow, scratch.targetBounds.height),
                consumers = listOf(org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef(
                    packet.commandIdValue.toString(), taskId(consumer), packet.packetId,
                    org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(packet.commandIdValue))),
                sourceTaskIds = listOf(taskId(pass)),
            )
        }.toMap())
    fun copyAuthority(pass: PlanPass.TextureCopy): GPUFrameStep.CopyDestinationStep = copies.getValue(pass.id)
    fun hasAtomicCopyBinding(copyTask: GPUTaskID, consumerTask: GPUTaskID, packet: GPUDrawPacket,
        actualDependencies: List<GPUTaskDependency>): Boolean {
        val copyIndex = graph.passes().indexOfFirst { it is PlanPass.TextureCopy && taskId(it) == copyTask }
        val copy = graph.passes().getOrNull(copyIndex) as? PlanPass.TextureCopy ?: return false
        val producer = graph.passes().getOrNull(copyIndex + 1) as? PlanPass.StencilGeometryProducerV3 ?: return false
        val cover = graph.passes().getOrNull(copyIndex + 2) as? PlanPass.StencilCover ?: return false
        return actualDependencies == dependencies && taskId(cover) == consumerTask && producer.atomicGroup == cover.atomicGroup &&
            packet.role == GPUDrawPacketRole.PathStencilCover && packet.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 === this &&
            copyAuthority(copy).consumers.single().let { it.renderTaskId == consumerTask && it.packetId == packet.packetId && it.commandId.value == packet.commandIdValue }
    }
    fun atomicCopyProducer(copyTask: GPUTaskID, consumerTask: GPUTaskID, packet: GPUDrawPacket,
        actualDependencies: List<GPUTaskDependency>): GPUTaskID? {
        if (!hasAtomicCopyBinding(copyTask, consumerTask, packet, actualDependencies)) return null
        val index = graph.passes().indexOfFirst { it is PlanPass.TextureCopy && taskId(it) == copyTask }
        return taskId(graph.passes()[index + 1])
    }

    private fun validatesCopy(actual: GPUFrameStep.CopyDestinationStep, pass: PlanPass.TextureCopy): Boolean {
        val expected = copyAuthority(pass)
        return actual.source == expected.source && actual.sourceKey == expected.sourceKey &&
            actual.snapshot == expected.snapshot && actual.logicalBounds == expected.logicalBounds &&
            actual.copyLayout == expected.copyLayout && actual.consumers == expected.consumers &&
            actual.sourceTaskIds == expected.sourceTaskIds
    }
    fun colorResourceUses(pass: PlanPass.RenderPass) = buildList {
        pass.draws().forEach { draw -> geometryLanes.filterIsInstance<W5bGeometryScratchV3.General>()
            .singleOrNull { draw.commandIndex in it.commandIds }?.let { addAll(it.geometryResourceUses(draw.commandIndex, false)) } }
        if (pass.draws().any { it.blend is org.graphiks.kanvas.gpu.plan.BlendPlan.DestinationReadV1 }) add(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef(scratch.target.value.removeSuffix(".target") + ".snapshot"),
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.DestinationSnapshot,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
        if (pass.draws().filterIsInstance<org.graphiks.kanvas.gpu.plan.W5bPointDraw>().any { it.clipOnly != null }) add(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(requireNotNull(clipPrefixV4).maskRef,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.ClipMask,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
    }
    fun depthStencilRef(id: org.graphiks.kanvas.gpu.plan.PlanResourceId) =
        org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef(scratch.target.value.removeSuffix(".target") + ".${id.value}.depth-stencil")
    fun stencilResourceUses(pass: PlanPass) = buildList {
        val depth = when (pass) {
            is PlanPass.StencilGeometryProducerV3 -> pass.depthStencil
            is PlanPass.StencilCover -> pass.depthStencil
            else -> error("Not a stencil pass")
        }
        val commandI32 = if (pass is PlanPass.StencilGeometryProducerV3) pass.commandIndexI32 else (pass as PlanPass.StencilCover).draw.commandIndex
        val general = geometryLanes.filterIsInstance<W5bGeometryScratchV3.General>().singleOrNull { commandI32 in it.commandIds }
        if (general != null) addAll(general.geometryResourceUses(commandI32, pass is PlanPass.StencilGeometryProducerV3))
        else add(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(depthStencilRef(depth),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.PathDepthStencil,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.RenderAttachment,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, true))
        if (pass is PlanPass.StencilCover && pass.draw.blend is org.graphiks.kanvas.gpu.plan.BlendPlan.DestinationReadV1)
            add(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef(scratch.target.value.removeSuffix(".target") + ".snapshot"),
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole.DestinationSnapshot,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding,
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.FrameLocal, false))
    }
    fun stencilLoadStore(pass: PlanPass) = org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
        if (pass is PlanPass.StencilGeometryProducerV3)
            org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear else org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Load,
        GPUStorePlan.Store,
        if (pass is PlanPass.StencilGeometryProducerV3) 0u else null,
    )
    init {
        require(graph.id.value == scratch.planId)
        require(graph.passes().any { it is PlanPass.TextureCopy } ||
            graph.capabilityId in setOf(org.graphiks.kanvas.gpu.plan.W5bCorePrimitiveGraph.CAPABILITY_ID,
                org.graphiks.kanvas.gpu.plan.W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID,
                org.graphiks.kanvas.gpu.plan.W4cPathFillPlanCompiler.W5B_CAPABILITY_ID,
                org.graphiks.kanvas.gpu.plan.W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID,
                org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID,
                org.graphiks.kanvas.gpu.plan.W4aAnalyticRectPlanCompiler.W5B_CAPABILITY_ID))
        require(this.geometryLanes.isNotEmpty() && this.geometryLanes.first() === scratch &&
            this.geometryLanes.all { it.planId == graph.id.value && it.target == scratch.target && it.staging == scratch.staging &&
                it.capabilitySealHash == scratch.capabilitySealHash && it.deviceGeneration == scratch.deviceGeneration &&
                it.fitsDeviceLimits(graph.capabilities.maxBufferSizeBytes,
                    graph.capabilities.maxDynamicUniformBuffersPerPipelineLayout.toLong()) })
        require(capabilitySeal.sealHash == scratch.capabilitySealHash && recordingSeal.capabilitySealHash == capabilitySeal.sealHash)
        require(targetPreparation.resource == scratch.target && stagingPreparation.resource == scratch.staging)
        require(memory.diagnostic == null && memory.targetResidentBytes + memory.peakFrameTransientBytes == graph.peakFrameLocalBytes)
    }

    fun validates(frame: GPUFramePlan): Boolean {
        val allRenders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val prefix = clipPrefixV4
        val prefixRenders = if (prefix == null) emptyList() else allRenders.take(prefix.renders.size)
        val prepare = frame.steps.firstOrNull() as? GPUFrameStep.PrepareResourcesStep ?: return false
        if (frame.capabilitySeal !== capabilitySeal || frame.frameId != capabilitySeal.frameId ||
            frame.recordingSeals != listOf(recordingSeal) || frame.memoryBudget != memory ||
            frame.phaseOrder != GPUTaskPhase.entries || frame.diagnostics.isNotEmpty() ||
            frame.elidedNoOpDraws.isNotEmpty() || frame.atomicallyRefused ||
            frame.steps.map { it.sourceTaskIds.singleOrNull() ?: return false } != taskIds ||
            frame.dependencies != dependencies || prepare.requests != preparations ||
            frame.steps.count { it is GPUFrameStep.PrepareResourcesStep } != 1 ||
                frame.steps.any { it !is GPUFrameStep.PrepareResourcesStep && it !is GPUFrameStep.RenderPassStep &&
                    it !is GPUFrameStep.CopyDestinationStep && it !is GPUFrameStep.ReadbackCopyStep } ||
            frame.steps.lastOrNull() !is GPUFrameStep.ReadbackCopyStep) return false
        if (prefix != null && (!prefix.authority.validatesRenderSteps(frame.frameId.value, frame.capabilitySeal.sealHash, prefixRenders) ||
            prefixRenders.map { it.sourceTaskIds.singleOrNull() } != prefix.renders.map { it.taskId } ||
            frame.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().flatMap { it.requests }
                .filter { request -> prefix.preparations.any { it.resource == request.resource } } != prefix.preparations)) return false
        val renders = allRenders.drop(prefixRenders.size)
        val planned = graph.passes().filter { it is PlanPass.RenderPass || it is PlanPass.StencilGeometryProducerV3 || it is PlanPass.StencilCover }
        if (renders.size != planned.size || frame.capabilitySeal.sealHash != scratch.capabilitySealHash) return false
        val operations = frame.steps.filter { it is GPUFrameStep.RenderPassStep ||
            it is GPUFrameStep.CopyDestinationStep || it is GPUFrameStep.ReadbackCopyStep }
        if (operations.size != graph.passes().size) return false
        if (operations.zip(graph.passes()).any { (actual, expected) -> when (expected) {
            is PlanPass.RenderPass -> actual !is GPUFrameStep.RenderPassStep || actual.target != scratch.target ||
                (if (expected.draws().isEmpty()) actual.w5bInitialClearV3?.witness !== this else actual.w5bInitialClearV3 != null) ||
                actual.samplePlan != GPUSamplePlan.SingleSampleFrame || actual.loadStore.loadOp !=
                (if (expected.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent) "clear" else "load") ||
                actual.loadStore.storePlan != GPUStorePlan.Store || actual.loadStore.clearColorLabel != null ||
                actual.resourceUses != colorResourceUses(expected) || actual.sampleContinuation != null ||
                actual.depthStencilLoadStore != null || actual.w4eMaskContinuation != null || actual.w4eSceneContinuation != null ||
                actual.preparedImageBindingsByPacketId.isNotEmpty() || actual.preparedTextBindingsByPacketId.isNotEmpty()
            is PlanPass.TextureCopy -> actual !is GPUFrameStep.CopyDestinationStep ||
                !validatesCopy(actual, expected)
            is PlanPass.StencilGeometryProducerV3, is PlanPass.StencilCover -> {
                val load = if (expected is PlanPass.StencilGeometryProducerV3) expected.load else (expected as PlanPass.StencilCover).load
                actual !is GPUFrameStep.RenderPassStep || actual.target != scratch.target || actual.w5bInitialClearV3 != null ||
                    actual.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                    actual.loadStore != GPULoadStorePlan(if (load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent) "clear" else "load", GPUStorePlan.Store) ||
                    actual.resourceUses != stencilResourceUses(expected) || actual.depthStencilLoadStore != stencilLoadStore(expected) ||
                    actual.sampleContinuation != null || actual.w4eMaskContinuation != null || actual.w4eSceneContinuation != null ||
                    actual.preparedImageBindingsByPacketId.isNotEmpty() || actual.preparedTextBindingsByPacketId.isNotEmpty()
            }
            is PlanPass.ReadbackPass -> actual !is GPUFrameStep.ReadbackCopyStep || actual.source != scratch.target ||
                actual.staging != scratch.staging || actual.request != readbackRequest
            is PlanPass.ClipMaskInitialize, is PlanPass.ClipMaskProducer, is PlanPass.ClipMaskFold ->
                prefix == null || actual !is GPUFrameStep.RenderPassStep || actual !in prefixRenders ||
                    actual.drawPackets.singleOrNull()?.w4ePreparedClipPass?.passId != expected.id.value
            else -> true
        } }) return false
        if (renders.zip(planned).any { (actual, sealed) ->
            val sealedDraws = when (sealed) {
                is PlanPass.RenderPass -> sealed.draws()
                is PlanPass.StencilCover -> listOf(sealed.draw)
                else -> emptyList()
            }
            val expectedCommands = if (sealed is PlanPass.StencilGeometryProducerV3) listOf(sealed.commandIndexI32) else sealedDraws.map { it.commandIndex }
            actual.drawPackets.map { it.commandIdValue } != expectedCommands ||
                actual.drawPackets.any { it.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 !== this } ||
                (sealed is PlanPass.StencilGeometryProducerV3 && actual.drawPackets.single().let { packet ->
                    packet.role != GPUDrawPacketRole.PathStencilProducer || packet.w5aSourceStageV2 != null ||
                        packet.blendPlan != org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan()
                }) ||
                actual.drawPackets.zip(sealedDraws).any { (packet, draw) ->
                    packet.blendPlan != org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer.lower(draw.blend) ||
                        packet.role != if (sealed is PlanPass.StencilCover) GPUDrawPacketRole.PathStencilCover else GPUDrawPacketRole.Shading
                } || actual.drawPackets.any { it.resourceGeneration != packetResourceGenerations[it.packetId] || it.diagnostics.isNotEmpty() }
        }) return false
        val packets = renders.flatMap { it.drawPackets }
        return geometryLanes.flatMap { it.packetIds } == packets.map { it.packetId } &&
            geometryLanes.flatMap { it.commandIds } == packets.map { it.commandIdValue } &&
            geometryLanes.all { it.hasExactUniformPayloads(graph.capabilities.minUniformBufferOffsetAlignment.toLong(), packetsFor(it)) }
    }
}
