package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

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
    val legacy=steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .mapNotNull { it.materialSourcePartitionV3()?.stage?.imageV3?.cacheRequest }
        .distinctBy { it.canonicalPhysicalIdentity }
    val seen=java.util.IdentityHashMap<org.graphiks.kanvas.gpu.plan.PlanCacheResourceRequest,Unit>()
    val composed=stages.flatMap { stage -> stage.composedProof?.composedImageResources.orEmpty().map { image ->
        require(requireNotNull(stage.composedProof).authenticatesComposedImage(image.resource,image.upload))
        image.upload.cacheRequest
    } }.filter { seen.put(it,Unit) == null }
    return (legacy.map { it to "w5e" } + composed.mapIndexed { index,request -> request to "w5g.$index" }).flatMap { (request,prefix) ->
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
            w5eChildStopAllocationsV3() + w5gDeclaredStopAllocationsV5(),
        configuredAggregateBudgetBytes = memoryBudget.configuredAggregateBudgetBytes,
        deviceLimits = limits,
    ))

/** Marks an existing graph-owned allocation, without charging that slab twice. */
internal fun org.graphiks.kanvas.gpu.plan.RenderGraph.composedStopAllocationLabelV5(sessionIdentity: String): String? {
    val table=materialPlanTableOrNull() ?: return null
    if(table.entries().none { it.bindings is org.graphiks.kanvas.gpu.plan.ComposedMaterialBindingV5 }) return null
    return table.gradientStopSlab?.let { "$sessionIdentity.gradient-stops" }
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
            requireNotNull(stage.composedLayout).resources.filter { it.buffer != null }.forEach { resource ->
                require(requireNotNull(stage.composedProof).authenticatesComposedStorage(resource,slab))
            }
        }
        // Every consumer, including an ordinary gradient sibling, retains this
        // exact frame-local object. Value equality is not an ownership proof.
        val consumers=packets.filter { it.materialSourcePartitionV3()?.stage?.gradientStopSlab != null }
        require(consumers.all { it.materialSourcePartitionV3()?.stage?.gradientStopSlab === slab })
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
                val session="w3.session.${scratch.deviceGeneration}.${scratch.targetBounds.width}x${scratch.targetBounds.height}.rgba8unorm-srgb"
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
