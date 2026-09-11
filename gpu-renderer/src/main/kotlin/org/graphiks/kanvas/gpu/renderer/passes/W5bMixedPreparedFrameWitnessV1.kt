package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.destination.preparedDestinationBounds

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.*
import org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.materials.w5aMaterialAllocationsV2
import org.graphiks.kanvas.gpu.renderer.execution.*
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan

/**
 * Complete prepared-frame sibling, deliberately separate from the graph-only W5b witness.
 * Uploads, binding plans and native core payloads are retained, not translated into graph draws.
 */
internal class W5bMixedPreparedFrameWitnessV1 private constructor(
    val timeline: W5bMixedFramePlanV1,
    private val frame: GPUFramePlan,
    private val admittedPackets: List<GPUDrawPacket>,
    val coreCopies: List<GPUFrameStep.CopyDestinationStep>,
) {
    enum class RefusalReason(val code: String) {
        TextBindingCapability("unsupported.w5b.mixed-text-binding-capability"),
        UniformBinding("resource-limit.w5b.mixed-native-uniform-binding"),
        NativeBuffer("resource-limit.w5b.mixed-native-buffer"),
        PhysicalBudget("resource-limit.w5b.mixed-physical-budget"),
    }
    class Refusal internal constructor(val reason: RefusalReason) : IllegalArgumentException(reason.code)

    private val frameHash = frame.stableHash()
    private data class NativeInventory(val coreSizing: GPUCorePrimitiveRenderRunSizingV1?)
    private var sealedNativeInventory: NativeInventory? = null

    fun nativeInventory(actual: GPUFramePlan): GPUCorePrimitiveRenderRunSizingV1? {
        require(validates(actual))
        return requireNotNull(sealedNativeInventory).coreSizing
    }

    /** Only authenticated native routes can determine physical packing and pool capacities. */
    fun sealNativeInventory(actual: GPUFramePlan, routes: GPUCorePrimitiveNativeScopeFrameRouteSeal,
        limits: org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits) {
        require(validates(actual))
        require(timeline.capabilities.copyBytesPerRowAlignment.toLong() == limits.copyBytesPerRowAlignment)
        actual.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { it.preparedTextBindingsByPacketId.values }.forEach { binding ->
                admit(RefusalReason.TextBindingCapability, limits.maxSampledTexturesPerShaderStageI32 != null && limits.maxSamplersPerShaderStageI32 != null &&
                    limits.maxDynamicUniformBuffersPerPipelineLayout != null)
                val plan = binding.compositeProgram.bindingPlan
                val material = plan.materialFragment
                val bindings = buildList {
                    add(plan.drawUniformGroup to plan.drawUniformBinding)
                    material.uniformBinding?.let { add(it.group to it.binding) }
                    material.sampledBindings.forEach { add(it.textureGroup to it.textureBinding); add(it.samplerGroup to it.samplerBinding) }
                    add(plan.atlasTextureGroup to plan.atlasTextureBinding)
                    add(plan.atlasSamplerGroup to plan.atlasSamplerBinding)
                    plan.coverageMaskTextureGroup?.let { add(it to requireNotNull(plan.coverageMaskTextureBinding)) }
                    plan.destinationTextureGroup?.let { add(it to requireNotNull(plan.destinationTextureBinding)) }
                    plan.destinationSamplerGroup?.let { add(it to requireNotNull(plan.destinationSamplerBinding)) }
                }
                val texturesI64 = Math.addExact(material.sampledBindings.size.toLong(),
                    1L + (if (plan.coverageMaskTextureGroup != null) 1L else 0L) + (if (plan.destinationTextureGroup != null) 1L else 0L))
                val samplersI64 = Math.addExact(material.sampledBindings.size.toLong(),
                    1L + (if (plan.destinationSamplerGroup != null) 1L else 0L))
                val uniformsI64 = if (material.uniformBinding == null) 1L else 2L
                admit(RefusalReason.TextBindingCapability, bindings.maxOf { it.first }.toLong() < requireNotNull(limits.maxBindGroupsI32).toLong() &&
                    bindings.groupBy { it.first }.values.all { entries ->
                        entries.size <= requireNotNull(limits.maxBindingsPerBindGroupI32) &&
                            entries.all { it.second < limits.maxBindingsPerBindGroupI32 }
                    } && texturesI64 <= requireNotNull(limits.maxSampledTexturesPerShaderStageI32).toLong() &&
                    samplersI64 <= requireNotNull(limits.maxSamplersPerShaderStageI32).toLong() &&
                    uniformsI64 <= requireNotNull(limits.maxUniformBuffersPerShaderStageI32).toLong() &&
                    uniformsI64 <= requireNotNull(limits.maxDynamicUniformBuffersPerPipelineLayout) &&
                    maxOf(80L, material.uniformBinding?.minBindingSizeBytes?.toLong() ?: 0L) <=
                    requireNotNull(limits.maxUniformBufferBindingSizeBytesI64))
            }
        val coreRenders = actual.steps.withIndex().filter { (_, step) -> step is GPUFrameStep.RenderPassStep &&
            step.drawPackets.any { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive } }
        val nativeRoutes = coreRenders.map { indexed ->
            val render = indexed.value as GPUFrameStep.RenderPassStep
            require(render.drawPackets.all { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive })
            routes.retainedFor(indexed.index, render.drawPackets.map { it.packetId }) as GPUCorePrimitiveNativeScopeRouteSeal.Routes
        }
        val sizing = if (nativeRoutes.isEmpty()) {
            require(coreRenders.isEmpty() && coreCopies.isEmpty() &&
                admittedPackets.none { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive } &&
                actual.steps.indices.none(routes::hasRouteForStep))
            null
        } else corePrimitiveRenderRunSizingV1(nativeRoutes, limits.minUniformBufferOffsetAlignment)
        admit(RefusalReason.UniformBinding, nativeRoutes.flatMap { it.uniformPlan.slots }.all {
            it.payloadBytes <= requireNotNull(limits.maxUniformBufferBindingSizeBytesI64)
        })
        val capacities = sizing?.capacities?.let { listOf(it.vertexBytes, it.indexBytes, it.uniformBytes) }.orEmpty()
        admit(RefusalReason.NativeBuffer, capacities.all { it <= requireNotNull(limits.maxBufferSize) })
        val bufferRefs = coreRenders.flatMap { (it.value as GPUFrameStep.RenderPassStep).resourceUses }
            .filter { it.role in setOf(GPUFrameResourceRole.VertexData, GPUFrameResourceRole.IndexData, GPUFrameResourceRole.UniformData) }
            .map { it.resource }.toSet()
        val preparations = actual.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().single().requests
        val replaced = preparations.filter { it.resource in bufferRefs }
        require((sizing == null) == coreRenders.isEmpty())
        if (sizing == null) require(capacities.isEmpty() && bufferRefs.isEmpty() && replaced.isEmpty())
        require(replaced.size == bufferRefs.size)
        val replacedLabels = replaced.map { it.diagnosticLabel }.toSet()
        require(replacedLabels.size == replaced.size && replaced.all { preparation ->
            actual.memoryBudget.allocations.single { it.label == preparation.diagnosticLabel }.let {
                it.bytes == preparation.byteSize && it.resourceKind == GPUFrameMemoryResourceKind.Buffer
            }
        })
        val physicalBytesI64 = (actual.memoryBudget.allocations.filterNot { it.label in replacedLabels }.map { it.bytes } +
            actual.w5aMaterialAllocationsV2().map { it.bytes } + capacities).fold(0L, Math::addExact)
        admit(RefusalReason.PhysicalBudget, physicalBytesI64 <= timeline.budget.maxFrameLocalBytes)
        val inventory = NativeInventory(sizing)
        require(sealedNativeInventory == null || sealedNativeInventory == inventory)
        sealedNativeInventory = inventory
    }
    class NativeProjection private constructor(
        val copyScopeKeys: List<GPUPreparedNativeScopeKey>,
        val packetIds: List<GPUDrawPacketID>,
    ) {
        companion object {
            fun issue(witness: W5bMixedPreparedFrameWitnessV1, actual: GPUFramePlan,
                scopes: List<GPUPreparedNativeScopeKey>, runs: List<GPUCorePrimitiveRenderRunPlan>): NativeProjection {
                require(witness.validates(actual))
                val coreRenders = actual.steps.withIndex().filter { (_, step) ->
                    step is GPUFrameStep.RenderPassStep && step.drawPackets.any { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }
                }
                require(runs.size == coreRenders.size)
                runs.zip(coreRenders).forEach { (run, indexed) ->
                    val render = indexed.value as GPUFrameStep.RenderPassStep
                    require(run.exactScopeKey.sourceStepIndex == indexed.index &&
                        (run.routeSeal as GPUCorePrimitiveNativeScopeRouteSeal.Routes).flattenedPacketIds == render.drawPackets.map { it.packetId })
                }
                val ids = coreRenders.flatMap { (it.value as GPUFrameStep.RenderPassStep).drawPackets.map { packet -> packet.packetId } }
                require(ids.distinct().size == ids.size)
                val copies = witness.coreCopies.map { copy -> scopes.single { it.sourceStepIndex == witness.copyStepIndex(actual, copy) } }
                require(copies.all { it.operationKind == GPUEncoderOperationKind.CopyDestination })
                return NativeProjection(immutableList(copies), immutableList(ids))
            }
        }
    }
    fun owns(packet: GPUDrawPacket): Boolean = admittedPackets.any { it === packet }
    fun destinationVersion(packet: GPUDrawPacket): Long {
        require(owns(packet) && validates(frame))
        return timeline.draws.single { it.commandIndexI32 == packet.commandIdValue }.versionBefore.valueI64
    }

    /** Only the child dependency checker sees this projection; execution keeps every step. */
    fun preparedDependencyProjection(actual: GPUFramePlan): GPUFramePlan {
        require(validates(actual))
        val coreCopyTasks = coreCopies.flatMap { it.sourceTaskIds }.toSet()
        return GPUFramePlan(actual.frameId, actual.capabilitySeal, actual.recordingSeals,
            actual.steps.filterNot { it is GPUFrameStep.CopyDestinationStep && it.sourceTaskIds.any(coreCopyTasks::contains) }, actual.memoryBudget, actual.diagnostics,
            actual.dependencies.filterNot { it.fromTaskId in coreCopyTasks || it.toTaskId in coreCopyTasks },
            actual.phaseOrder, actual.elidedNoOpDraws, actual.atomicallyRefused)
    }
    fun copyStepIndex(actual: GPUFramePlan, copy: GPUFrameStep.CopyDestinationStep): Int {
        require(validates(actual) && coreCopies.any { it === copy })
        return actual.steps.indexOfFirst { it is GPUFrameStep.CopyDestinationStep &&
            it.sourceTaskIds == copy.sourceTaskIds && it.consumers == copy.consumers && it.snapshot == copy.snapshot }
            .also { require(it >= 0) }
    }

    /** Complete envelope validation precedes every native projection. */
    fun validates(actual: GPUFramePlan): Boolean =
        actual.capabilitySeal === frame.capabilitySeal && actual.stableHash() == frameHash &&
            actual.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
                .let { packets -> packets.size == admittedPackets.size &&
                    packets.zip(admittedPackets).all { (a, b) -> a === b && a.w5bMixedFrameWitnessV1 === this } }

    companion object {
        private fun admit(reason: RefusalReason, condition: Boolean) {
            if (!condition) throw Refusal(reason)
        }

        fun issue(timeline: W5bMixedFramePlanV1, taskList: GPUTaskList,
            nativeCorePackets: List<GPUDrawPacket>, nativeCoreDestinationTasks: List<GPUTask.DestinationSnapshots>,
            nativeCorePreparations: List<GPUResourcePreparationRequest>, nativeCoreAllocations: List<GPUFrameMemoryAllocation>,
            synthesizedSceneClearCommandIdI32: Int? = null): W5bMixedPreparedFrameWitnessV1 {
            val frame = GPUFramePlanner.plan(taskList)
            require(!frame.atomicallyRefused && frame.diagnostics.none { it.isTerminal } &&
                frame.phaseOrder == GPUTaskPhase.entries && frame.memoryBudget.diagnostic == null) {
                "invalid.w5b.mixed-frame-envelope"
            }
            val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            val packets = renders.flatMap { it.drawPackets }
            val core = packets.filter { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive }
            require(core.size == nativeCorePackets.size && core.zip(nativeCorePackets).all { (a, b) -> a === b }) {
                "invalid.w5b.mixed-native-projection"
            }
            if (core.isEmpty()) require(nativeCoreDestinationTasks.isEmpty() && nativeCorePreparations.isEmpty() &&
                nativeCoreAllocations.isEmpty()) { "invalid.w5b.mixed-empty-core-resources" }
            require(renders.isNotEmpty() && renders.all { it.target.value == timeline.targetId.value &&
                it.samplePlan == GPUSamplePlan.SingleSampleFrame && it.sampleContinuation == null &&
                it.loadStore.storePlan == GPUStorePlan.Store && it.loadStore.clearColorLabel == null } &&
                renders.map { it.loadStore.loadOp } == listOf("clear") + List(renders.size - 1) { "load" }) {
                "invalid.w5b.mixed-target-timeline"
            }
            require(frame.steps.all { it is GPUFrameStep.PrepareResourcesStep || it is GPUFrameStep.UploadResourceStep ||
                it is GPUFrameStep.RenderPassStep || it is GPUFrameStep.CopyDestinationStep || it is GPUFrameStep.ReadbackCopyStep })
            val preparations = frame.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().single().requests
            require(preparations.map { it.resource }.distinct().size == preparations.size)
            val target = preparations.single { it.resource.value == timeline.targetId.value }
            val descriptor = target.descriptor as GPUFrameTextureDescriptor
            require(descriptor.format == org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat.RGBA8UnormSrgb && descriptor.sampleCount == 1)
            val survivors = timeline.draws.filter { it.blend != BlendPlan.NoOpV1 }
            val initialization = synthesizedSceneClearCommandIdI32?.let { id ->
                require(id == 0)
                packets.first().also { packet ->
                    require(isW5bPreparedSceneInitialization(packet, packet.semanticPayload, descriptor.logicalBounds) &&
                        renders.first().drawPackets == listOf(packet)) { "invalid.w5b.mixed-initial-clear" }
                }
            }
            val colors = packets.filter { it.role != GPUDrawPacketRole.PathStencilProducer && it !== initialization }
            // This authority starts at the mapped frame, not the original Surface operation list.
            if (timeline.draws.isEmpty()) require(
                initialization != null && packets == listOf(initialization) && renders.size == 1 &&
                frame.steps.none { it is GPUFrameStep.UploadResourceStep || it is GPUFrameStep.CopyDestinationStep } &&
                nativeCoreDestinationTasks.isEmpty()) { "invalid.w5b.mixed-zero-survivor-domain" }
            require(colors.map { it.commandIdValue } == survivors.map { it.commandIndexI32 }) {
                "invalid.w5b.mixed-color-order"
            }
            colors.zip(survivors).forEach { (packet, draw) ->
                require(packet.blendPlan == W5bBlendPlanLowerer.lower(draw.blend) && packet.diagnostics.isEmpty()) {
                    "invalid.w5b.mixed-final-blend"
                }
                val source = when (val semantic = packet.semanticPayload) {
                    is GPUDrawSemanticPayload.CorePrimitive -> {
                        val source = requireNotNull((semantic.material as? GPUCorePrimitiveMaterialPayload.SolidColor)?.w5aAuthority)
                        require(source.validates(packet.commandIdValue))
                        source.sourcePlanTable to source.ref
                    }
                    is GPUDrawSemanticPayload.TextA8 -> requireNotNull(semantic.materialPlanProvenance).let { it.sourcePlanTable to it.ref }
                    is GPUDrawSemanticPayload.Vertices -> requireNotNull(semantic.materialPlanProvenance).let { it.sourcePlanTable to it.ref }
                    else -> error("unsupported.w5b.mixed-source")
                }
                require(source.first === draw.sourceTable && source.second == draw.sourceRef)
                val reads = renders.single { packet in it.drawPackets }.resourceUses
                    .filter { it.role == GPUFrameResourceRole.DestinationSnapshot }
                val destination = draw.blend as? BlendPlan.DestinationReadV1
                require(if (destination == null) reads.isEmpty() else reads.size == 1 && reads.single().let {
                    it.resource.value == destination.snapshotResource?.value && !it.write &&
                        it.usage == GPUFrameResourceUsage.TextureBinding && it.lifetime == GPUFrameResourceLifetime.FrameLocal
                }) { "invalid.w5b.mixed-destination-use" }
            }
            packets.filter { it.role == GPUDrawPacketRole.PathStencilProducer }.forEach { producer ->
                require(producer.w5aSourceStageV2 == null && producer.corePrimitivePreparedAuthority
                    ?.structuralPipelineKey?.blend == GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone)
                val i = packets.indexOf(producer)
                require(packets.getOrNull(i + 1)?.let { it.commandIdValue == producer.commandIdValue &&
                    it.role == GPUDrawPacketRole.PathStencilCover } == true)
            }
            val copies = frame.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
            val destinationDraws = survivors.filter { it.blend is BlendPlan.DestinationReadV1 }
            require(copies.size == destinationDraws.size)
            copies.zip(destinationDraws).forEach { (copy, draw) ->
                val blend = draw.blend as BlendPlan.DestinationReadV1
                val consumer = copy.consumers.single()
                val packet = colors.single { it.commandIdValue == draw.commandIndexI32 }
                require(copy.source.value == timeline.targetId.value && copy.snapshot.value == blend.snapshotResource?.value &&
                    consumer.packetId == packet.packetId && consumer.commandId.value == draw.commandIndexI32)
                val copyIndex = frame.steps.indexOf(copy)
                val consumerIndex = frame.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep && packet in it.drawPackets }
                val consumerRender = frame.steps[consumerIndex] as GPUFrameStep.RenderPassStep
                require(consumer.renderTaskId in consumerRender.sourceTaskIds &&
                    copy.sourceKey == org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey(
                        org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity(consumerRender.target.value),
                        packet.resourceGeneration, frame.capabilitySeal.deviceGeneration, descriptor.format,
                        org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation.LinearPremul,
                        consumerRender.sampleContinuation?.key, null, blend.requiredDestinationVersion)) { "invalid.w5b.mixed-copy-source" }
                val tightBytesPerRowI64 = Math.multiplyExact(copy.logicalBounds.width.toLong(), 4L)
                val alignmentI64 = timeline.capabilities.copyBytesPerRowAlignment.toLong()
                val remainderI64 = tightBytesPerRowI64 % alignmentI64
                val paddedBytesPerRowI64 = if (remainderI64 == 0L) tightBytesPerRowI64 else
                    Math.addExact(tightBytesPerRowI64, alignmentI64 - remainderI64)
                val copiedBytesI64 = Math.multiplyExact(paddedBytesPerRowI64, copy.logicalBounds.height.toLong())
                require(copy.copyLayout == GPUTextureCopyLayout(paddedBytesPerRowI64, copy.logicalBounds.height)) {
                    "invalid.w5b.mixed-copy-layout"
                }
                if (packet in core) {
                    require(copy.logicalBounds == requireNotNull(packet.semanticPayload)
                        .preparedDestinationBounds(descriptor.logicalBounds)) { "invalid.w5b.mixed-copy-bounds" }
                    val originalTask = nativeCoreDestinationTasks.single { it.taskId in copy.sourceTaskIds }
                    val original = originalTask.payload.operations.filterIsInstance<GPUDestinationSnapshotOperation.TextureCopy>()
                        .single { operation -> operation.consumers.any { it.packetId == packet.packetId } }
                    val originalGroup = originalTask.payload.grouping.groups[original.groupIndex]
                    require(copy.sourceKey == originalGroup.key && copy.source == original.source &&
                        copy.snapshot == original.snapshot && copy.logicalBounds == original.logicalBounds &&
                        copy.logicalBounds == originalGroup.logicalBounds && copy.copyLayout == original.copyLayout &&
                        copiedBytesI64 == originalGroup.copiedBytes && original.sourceIntermediate == null &&
                        original.consumers.single().let { child -> child.packetId == consumer.packetId &&
                            child.commandId == consumer.commandId && child.groupingCommandId == consumer.groupingCommandId }) {
                        "invalid.w5b.mixed-child-copy"
                    }
                }
                require(copyIndex in 1 until consumerIndex)
                if (initialization != null) require(frame.steps.indexOf(renders.first()) < copyIndex)
                val precedingColors = frame.steps.take(copyIndex).filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .flatMap { it.drawPackets }.count { it.role != GPUDrawPacketRole.PathStencilProducer && it !== initialization }.toLong()
                require(precedingColors == blend.requiredDestinationVersion.valueI64 &&
                    frame.steps.subList(copyIndex + 1, consumerIndex).filterIsInstance<GPUFrameStep.RenderPassStep>()
                        .flatMap { it.drawPackets }.all { it.role == GPUDrawPacketRole.PathStencilProducer })
                val snapshot = preparations.single { it.resource == copy.snapshot }
                val snapshotDescriptor = snapshot.descriptor as GPUFrameTextureDescriptor
                val capacity = snapshotDescriptor.logicalBounds
                require(snapshot.role == GPUFrameResourceRole.DestinationSnapshot &&
                    snapshot.lifetime == GPUFrameResourceLifetime.FrameLocal &&
                    snapshotDescriptor.format == descriptor.format && snapshotDescriptor.sampleCount == 1 &&
                    copy.logicalBounds.left >= descriptor.logicalBounds.left && copy.logicalBounds.top >= descriptor.logicalBounds.top &&
                    copy.logicalBounds.right <= descriptor.logicalBounds.right && copy.logicalBounds.bottom <= descriptor.logicalBounds.bottom &&
                    copy.logicalBounds.width <= capacity.width && copy.logicalBounds.height <= capacity.height &&
                    snapshot.byteSize == Math.multiplyExact(Math.multiplyExact(capacity.width.toLong(), capacity.height.toLong()), 4L) &&
                    snapshot.usages == setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding))
                require(frame.memoryBudget.allocations.single { it.label == snapshot.diagnosticLabel }.let { allocation ->
                    allocation.category == GPUFrameMemoryCategory.DestinationSnapshot && allocation.bytes == snapshot.byteSize &&
                        allocation.resourceKind == GPUFrameMemoryResourceKind.Texture2D && allocation.extent == capacity
                })
            }
            val physicalI64 = (listOf(frame.memoryBudget.targetResidentBytes, frame.memoryBudget.peakFrameTransientBytes) +
                frame.w5aMaterialAllocationsV2().map { it.bytes }).fold(0L, Math::addExact)
            admit(RefusalReason.PhysicalBudget, physicalI64 <= timeline.budget.maxFrameLocalBytes)
            val coreIds = core.map { it.packetId }.toSet()
            require(nativeCoreDestinationTasks.flatMap { it.payload.operations }.size ==
                copies.count { it.consumers.single().packetId in coreIds }) { "invalid.w5b.mixed-child-copy-count" }
            require(copies.filter { it.consumers.single().packetId in coreIds }.map { it.snapshot }.distinct().size <= 1)
            val witness = W5bMixedPreparedFrameWitnessV1(timeline, frame, immutableList(packets),
                immutableList(copies.filter { it.consumers.single().packetId in coreIds }))
            packets.forEach { it.attachW5bMixedFrameWitnessV1(witness) }
            require(witness.validates(frame))
            return witness
        }
    }
}

/** Exact existing initialization geometry/material/blend, shared before and after native lowering. */
internal fun isW5bPreparedSceneInitialization(
    packet: GPUDrawPacket,
    payload: GPUDrawSemanticPayload?,
    targetBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
): Boolean {
    val semantic = payload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
    val material = semantic.material as? GPUCorePrimitiveMaterialPayload.SolidColor ?: return false
    val blend = packet.blendPlan as? GPUBlendPlan.FixedFunctionBlend ?: return false
    return packet.commandIdValue == 0 && packet.originalPaintOrder == 0 &&
        packet.role == GPUDrawPacketRole.Shading && packet.w5aSourceStageV2 == null &&
        semantic.geometry is GPUCorePrimitiveGeometry.Rect && semantic.sourceFamily == GPUCorePrimitiveSourceFamily.Rect &&
        semantic.rectGeometryAuthority?.isIdentityFullTarget(targetBounds) == true &&
        semantic.targetBounds == targetBounds && semantic.scissorBounds == targetBounds &&
        packet.clipExecutionPlan == GPUClipExecutionPlan.NoClip &&
        semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor &&
        material.w5aAuthority == null && material.premultipliedRgba.all { it == 0f } &&
        blend.state.color == org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent("one", "zero", "add") &&
        blend.state.alpha == blend.state.color && blend.state.writeMask == "rgba" &&
        blend.sourceCoverageEncoding == GPUSourceCoverageEncoding.None
}
