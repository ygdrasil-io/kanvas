package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.plan.*

/**
 * Versioned material partition alongside the exact historical geometry allocation seal.
 * Raw buffers are completion-owned and live through the complete frame; nothing is allocated
 * until this combined inventory has passed the configured aggregate budget.
 */
internal fun GPUFramePlan.w5aMaterialAllocationsV2(): List<GPUFrameMemoryAllocation> =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .filter { it.materialSourcePartitionV3() != null }
        .distinctBy { requireNotNull(it.materialSourcePartitionV3()).stage.canonicalIdentity }
        .map { packet -> requireNotNull(packet.materialSourcePartitionV3()).let { source ->
            GPUFrameMemoryAllocation(
                label = "w5a.source-v2.${packet.packetId.value}",
                category = GPUFrameMemoryCategory.ReusableScratch,
                bytes = source.stage.uniformByteCountI64,
                resourceKind = GPUFrameMemoryResourceKind.Buffer,
                extent = null,
                firstPassIndex = 0,
                lastPassIndexExclusive = steps.size.coerceAtLeast(1),
            )
        } }

private fun GPUFramePlan.w5eImageAllocationsV3(limits: GPULimits): List<GPUFrameMemoryAllocation> {
    val stages=steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .mapNotNull { it.materialSourcePartitionV3()?.stage }
    val seen=java.util.IdentityHashMap<org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest,Unit>()
    val requests=stages.flatMap { stage -> listOfNotNull(stage.imageV3?.cacheRequest) +
        stage.composedProof?.runtimeResources.orEmpty().mapNotNull { it.imageUpload?.cacheRequest } +
        stage.composedProof?.composedImageResources.orEmpty().map { image ->
            require(requireNotNull(stage.composedProof).authenticatesComposedImage(image.resource,image.upload))
            image.upload.cacheRequest
    } }.filter { seen.put(it,Unit) == null }
    // One issued request per captured frame owner, even across source versions.
    // Equal canonical contents from different owners keep separate reservations.
    return requests.flatMapIndexed { index,request ->
            val prefix = "w5e.frame-owner.$index"
            val alignmentI64 = lcmW5eAlignmentI64(256L, limits.copyBytesPerRowAlignment)
            val logicalRowI64 = Math.multiplyExact(request.widthI32.toLong(), request.format.bytesPerPixelI32.toLong())
            val rowI64 = Math.addExact(logicalRowI64, (alignmentI64 - logicalRowI64 % alignmentI64) % alignmentI64)
            listOf(
                GPUFrameMemoryAllocation("$prefix.image-v3.${request.canonicalPhysicalIdentity}", GPUFrameMemoryCategory.ReusableScratch,
                    request.byteSizeI64, GPUFrameMemoryResourceKind.Texture2D, GPUPixelBounds(0, 0, request.widthI32, request.heightI32),
                    0, steps.size.coerceAtLeast(1)),
                GPUFrameMemoryAllocation("$prefix.upload-v3.${request.canonicalPhysicalIdentity}", GPUFrameMemoryCategory.ReusableScratch,
                    Math.multiplyExact(rowI64, request.heightI32.toLong()), GPUFrameMemoryResourceKind.Buffer, null,
                    0, steps.size.coerceAtLeast(1)),
            )
        }
}

internal fun GPUFramePlan.w5aCombinedMemoryBudgetV2(limits: GPULimits): GPUFrameMemoryBudgetPlan =
    GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(
        allocations = memoryBudget.allocations + w5aMaterialAllocationsV2() + w5eImageAllocationsV3(limits) +
            w5eChildStopAllocationsV3() + w5gDeclaredStopAllocationsV5() + w5gNoiseAllocationsV1() + w5hRuntimeAllocationsV1() +
            w5hPreparedGeometryAllocationsV6(),
        configuredAggregateBudgetBytes = memoryBudget.configuredAggregateBudgetBytes,
        deviceLimits = limits,
    ))

/** Physical native bytes absent from the legacy logical Vertices inventory. */
internal fun GPUFramePlan.w5hPreparedGeometryAllocationsV6(): List<GPUFrameMemoryAllocation> {
    val vertices = steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .mapNotNull { packet -> (packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices)
            ?.takeIf { it.material.commonSource != null }?.also { require(packet.materialSourcePartitionV3() != null) } }
    if (vertices.isEmpty()) return emptyList()
    val paddingI64 = vertices.distinctBy { it.artifact.key }.fold(0L) { bytes, semantic ->
        val indexBytesI64 = semantic.artifact.indexBytesForUpload()?.size?.toLong()
        Math.addExact(bytes, indexBytesI64?.let {
            Math.subtractExact(org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesIndexBufferBytesI64(it), it)
        } ?: 0L)
    }
    val bytesI64 = Math.addExact(paddingI64,
        org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesDrawUniformBytesI64(vertices.size))
    return listOf(GPUFrameMemoryAllocation("w5h.prepared.vertices-native-geometry", GPUFrameMemoryCategory.ReusableScratch,
        bytesI64, GPUFrameMemoryResourceKind.Buffer, null, 0, steps.size.coerceAtLeast(1)))
}

private fun GPUFramePlan.w5hRuntimeAllocationsV1(): List<GPUFrameMemoryAllocation> {
    val references=steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .flatMap { it.materialSourcePartitionV3()?.stage?.composedProof?.runtimeResources.orEmpty() }
    require(references.size <= PlanCacheResourceRequest.MAX_RUNTIME_LEASES_I32)
    val requests=references.map { it.cacheRequest }.filter { it !is PlanCacheResourceRequest.Texture }.distinct()
    require(requests.size <= PlanCacheResourceRequest.MAX_RUNTIME_ENTRIES_I32 &&
        requests.fold(0L) { bytes,request -> Math.addExact(bytes,request.byteSizeI64) } <= PlanCacheResourceRequest.MAX_RUNTIME_BYTES_I64)
    // Samplers have zero payload bytes but consume the entry/lease reservations above.
    return requests.filterIsInstance<PlanCacheResourceRequest.Storage>().mapIndexed { index,request ->
        GPUFrameMemoryAllocation("w5h.runtime-owner.$index",GPUFrameMemoryCategory.ReusableScratch,request.byteSizeI64,
            GPUFrameMemoryResourceKind.Buffer,null,0,steps.size.coerceAtLeast(1))
    }
}

/** Marks an existing graph-owned allocation, without charging that slab twice. */
internal fun org.graphiks.kanvas.gpu.plan.RenderGraph.composedStopAllocationLabelV5(sessionIdentity: String): String? {
    val table=materialPlanTableOrNull() ?: return null
    if(table.entries().none { it.bindings is org.graphiks.kanvas.gpu.plan.ComposedMaterialBindingV5 }) return null
    return table.gradientStopSlab?.let { "$sessionIdentity.gradient-stops" }
}

internal const val NOISE_TABLE_ALLOCATION_LABEL_V1 = "w5g.noise-v1.tables"

/** R33's actual graph row, authenticated against the same issued source proof and slab. */
internal fun RenderGraph.declaredNoiseSlabV1(): NoiseTableSlabV1? {
    val rows=resources().filter { it.role == PlanResourceRole.NoiseTableData }
    if(rows.isEmpty()) return null
    require(rows.size == 1)
    val table=requireNotNull(materialPlanTableOrNull())
    val proofs=table.entries().mapIndexedNotNull { index,entry ->
        val binding=entry.bindings as? ComposedMaterialBindingV5 ?: return@mapIndexedNotNull null
        binding.sourceProof.takeIf { it.noiseTableSlab != null }?.also { proof ->
            require(proof.authenticates(table,MaterialPlanRef(index),SourceCoordinatesV4.None))
            val resource=requireNotNull(proof.composedBindingLayout).resources.single {
                it.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.NOISE_U32 }
            require(proof.authenticatesComposedNoise(resource,requireNotNull(proof.noiseTableSlab)))
        }
    }
    val slab=proofs.map { requireNotNull(it.noiseTableSlab) }.distinct().single()
    require(rows.single().let { it.kind == PlanResourceKind.Buffer && it.byteSize == slab.byteCountI64 &&
        it.format == null && it.copyExtent() == null && it.ordinal == 0 && it.sampleCountI32 == 1 &&
        it.usages() == setOf(PlanResourceUsage.StorageRead,PlanResourceUsage.CopyDestination) &&
        it.lifetime == PlanResourceLifetime.FrameLocal && it.firstPassIndex == 0 &&
        it.lastPassIndexExclusive == passes().size })
    return slab
}

internal fun RenderGraph.noiseAllocationLabelV1(): String =
    requireNotNull(declaredNoiseSlabV1()).let { NOISE_TABLE_ALLOCATION_LABEL_V1 }

private fun GPUFramePlan.w5gNoiseAllocationsV1(): List<GPUFrameMemoryAllocation> {
    val stages=steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .mapNotNull { it.materialSourcePartitionV3()?.stage }.filter { it.noiseTableSlab != null }
    val slabs=stages.map { requireNotNull(it.noiseTableSlab) }.distinct()
    require(slabs.size <= 1)
    val preparedTable = preparedCommonTableV6()
    slabs.forEach { slab ->
        stages.forEach { stage ->
            require(stage.noiseTableSlab === slab)
            val resource=requireNotNull(stage.composedLayout).resources.single {
                it.buffer?.storageKind == org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.NOISE_U32 }
            require(requireNotNull(stage.composedProof).authenticatesComposedNoise(resource,slab))
        }
        if (preparedTable != null) {
            require(preparedTable.entries().any { (it.bindings as? ComposedMaterialBindingV5)?.sourceProof?.noiseTableSlab === slab })
            require(memoryBudget.allocations.none { it.label == NOISE_TABLE_ALLOCATION_LABEL_V1 })
            return listOf(GPUFrameMemoryAllocation(NOISE_TABLE_ALLOCATION_LABEL_V1, GPUFrameMemoryCategory.ReusableScratch,
                slab.byteCountI64, GPUFrameMemoryResourceKind.Buffer, null, 0, steps.size))
        }
        val allocation=memoryBudget.allocations.single { it.label == NOISE_TABLE_ALLOCATION_LABEL_V1 }
        val lastConsumer=steps.indexOfLast { step -> step is GPUFrameStep.RenderPassStep && step.drawPackets.any {
            it.materialSourcePartitionV3()?.stage?.noiseTableSlab === slab } }
        require(allocation.category == GPUFrameMemoryCategory.ReusableScratch && allocation.bytes == slab.byteCountI64 &&
            allocation.resourceKind == GPUFrameMemoryResourceKind.Buffer && allocation.extent == null &&
            allocation.firstPassIndex == 0 && allocation.lastPassIndexExclusive > lastConsumer)
    }
    if(slabs.isEmpty()) require(memoryBudget.allocations.none { it.label == NOISE_TABLE_ALLOCATION_LABEL_V1 })
    return emptyList() // The one authenticated declaration is already in the base memory budget.
}

private fun GPUFramePlan.w5gDeclaredStopAllocationsV5(): List<GPUFrameMemoryAllocation> {
    val renders=steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
    val packets=renders.flatMap { it.drawPackets }
    val stages=packets.mapNotNull { it.materialSourcePartitionV3()?.stage }
        .filter { it.composedLayout != null && it.gradientStopSlab != null }
    val slabs=stages.map { requireNotNull(it.gradientStopSlab) }.distinct()
    require(slabs.size <= 1)
    return slabs.mapNotNull { slab ->
        stages.forEach { stage ->
            require(stage.gradientStopSlab === slab)
            requireNotNull(stage.composedLayout).resources.filter {
                it.buffer?.storageKind == org.graphiks.kanvas.gpu.plan.ComposedBindingLayoutV1.StorageKind.GRADIENT_STOPS }.forEach { resource ->
                require(requireNotNull(stage.composedProof).authenticatesComposedStorage(resource,slab))
            }
        }
        // Every consumer, including an ordinary gradient sibling, retains this
        // exact frame-local object. Value equality is not an ownership proof.
        val consumers=packets.filter { it.materialSourcePartitionV3()?.stage?.gradientStopSlab != null }
        require(consumers.all { it.materialSourcePartitionV3()?.stage?.gradientStopSlab === slab })
        preparedCommonTableV6()?.let { table ->
            require(table.gradientStopSlab === slab)
            return@mapNotNull GPUFrameMemoryAllocation("w5h.prepared.gradient-stops", GPUFrameMemoryCategory.ReusableScratch,
                slab.byteSizeI64, GPUFrameMemoryResourceKind.Buffer, null, 0, steps.size)
        }
        val composite=consumers.mapNotNull { it.w5aCompositeFrameAuthority }.distinct().singleOrNull()
        val expected=if(composite != null) {
            require(composite.validates(this,renders) && consumers.all { composite.owns(it) && it.w5aCompositeFrameAuthority === composite })
            GPUFrameMemoryAllocation("${composite.sessionIdentity}.gradient-stops",GPUFrameMemoryCategory.ReusableScratch,
                slab.byteSizeI64,GPUFrameMemoryResourceKind.Buffer,null,0,steps.size)
        } else {
            val witness=consumers.mapNotNull { it.w5bFinalFrameWitnessV3 }.distinct().singleOrNull()
            if(witness != null) {
                require(witness.validates(this) && consumers.all { it.w5bFinalFrameWitnessV3 === witness })
                require(witness.graph.materialPlanTableOrNull()?.gradientStopSlab === slab)
                val scratch=witness.scratch
                require(scratch.deviceGeneration == capabilitySeal.deviceGeneration.value)
                val session=if (witness.graph.capabilityId == W5bCorePrimitiveGraph.CAPABILITY_ID)
                    scratch.target.value.removeSuffix(".target")
                else "w3.session.${scratch.deviceGeneration}.${scratch.targetBounds.width}x${scratch.targetBounds.height}.rgba8unorm-srgb"
                if(witness.graph.capabilityId in setOf(org.graphiks.kanvas.gpu.plan.W5bCorePrimitiveGraph.CAPABILITY_ID,
                    org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler.CAPABILITY_ID,
                    org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler.W5A_CAPABILITY_ID)) {
                    // GpuPlanTaskListLowerer retains the exact W3 allocation
                    // envelope for destination-aware core Rects as well.
                    GPUFrameMemoryAllocation("$session.gradient-stops",GPUFrameMemoryCategory.ReusableScratch,
                        slab.byteSizeI64,GPUFrameMemoryResourceKind.Buffer,null)
                } else {
                    val resource=witness.graph.resources().single { it.role == org.graphiks.kanvas.gpu.plan.PlanResourceRole.GradientStopData }
                    require(resource.byteSize == slab.byteSizeI64 && resource.kind == org.graphiks.kanvas.gpu.plan.PlanResourceKind.Buffer)
                    GPUFrameMemoryAllocation("$session.gradient-stops",GPUFrameMemoryCategory.ReusableScratch,resource.byteSize,
                        GPUFrameMemoryResourceKind.Buffer,resource.copyExtent()?.let { GPUPixelBounds(0,0,it.width,it.height) },
                        resource.firstPassIndex,resource.lastPassIndexExclusive)
                }
            } else {
                val scratch=consumers.map { requireNotNull(it.corePrimitivePreparedAuthority?.w3SessionScratch) }.distinct().single()
                val allPackets=renders.flatMap { it.drawPackets }.filter { it.corePrimitivePreparedAuthority?.w3SessionScratch === scratch }
                val readback=steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
                require(renders.all { it.target == scratch.target })
                require(scratch.matches(scratch.planId,capabilitySeal.sealHash,capabilitySeal.deviceGeneration.value,
                    readback.source,readback.staging,scratch.targetBounds,allPackets))
                val target=steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().flatMap { it.requests }
                    .single { it.resource == scratch.target }
                require((target.descriptor as GPUFrameTextureDescriptor).logicalBounds == scratch.targetBounds)
                val session="w3.session.${scratch.deviceGeneration}.${scratch.targetBounds.width}x${scratch.targetBounds.height}.rgba8unorm-srgb"
                GPUFrameMemoryAllocation("$session.gradient-stops",GPUFrameMemoryCategory.ReusableScratch,
                    slab.byteSizeI64,GPUFrameMemoryResourceKind.Buffer,null)
            }
        }
        // Current admitted gradient lanes declare the allocation in their sealed
        // construction graph. Do not silently invent a missing declaration.
        require(memoryBudget.allocations.count { it.label == expected.label } == 1 && expected in memoryBudget.allocations)
        null
    }
}

/** The mixed witness retains the exact common table and occurrence-to-command join. */
private fun GPUFramePlan.preparedCommonTableV6(): MaterialPlanTable? {
    val packets = steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
    val sourcePackets = packets.filter { it.materialSourcePartitionV3() != null }
    val witness = sourcePackets.mapNotNull { it.w5bMixedFrameWitnessV1 }.distinct().singleOrNull() ?: return null
    require(witness.validates(this))
    val table = witness.timeline.draws.map { it.sourceTable }.distinct().singleOrNull() ?: return null
    if (table.entries().none { it.bindings is ComposedMaterialBindingV5 }) return null
    sourcePackets.forEach { packet ->
        val draw = witness.timeline.draws.single { it.commandIndexI32 == packet.commandIdValue }
        require(draw.sourceTable === table)
        val binding = table.entry(draw.sourceRef).bindings as ComposedMaterialBindingV5
        require(packet.materialSourcePartitionV3()?.stage?.composedProof === binding.sourceProof &&
            binding.sourceProof.authenticates(table, draw.sourceRef, SourceCoordinatesV4.None))
    }
    return table
}

/** The W5e construction graph is neutral; its image children's shared slab is owned here. */
private fun GPUFramePlan.w5eChildStopAllocationsV3(): List<GPUFrameMemoryAllocation> =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .mapNotNull { it.materialSourcePartitionV3()?.stage?.takeIf { stage -> stage.imageV3 != null }?.gradientStopSlab }
        .distinctBy { it.canonicalIdentity }.filterNot { slab ->
            steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }.any {
                it.materialSourcePartitionV3()?.stage?.let { stage -> stage.composedLayout != null && stage.gradientStopSlab === slab } == true
            }
        }.map { slab ->
            GPUFrameMemoryAllocation("w5e.child-stops-v3.${slab.canonicalIdentity}", GPUFrameMemoryCategory.ReusableScratch,
                slab.byteSizeI64, GPUFrameMemoryResourceKind.Buffer, null, 0, steps.size.coerceAtLeast(1))
        }

private fun lcmW5eAlignmentI64(leftI64: Long, rightI64: Long): Long {
    require(leftI64 > 0L && rightI64 > 0L)
    var left = leftI64
    var right = rightI64
    while (right != 0L) { val remainder = left % right; left = right; right = remainder }
    return Math.multiplyExact(leftI64 / left, rightI64)
}
