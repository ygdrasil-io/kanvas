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

private fun GPUFramePlan.w5eImageAllocationsV3(limits: GPULimits): List<GPUFrameMemoryAllocation> =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .mapNotNull { it.materialSourcePartitionV3()?.stage?.imageV3?.cacheRequest }
        .distinctBy { it.canonicalPhysicalIdentity }.flatMap { request ->
            val alignmentI64 = maxOf(256L, limits.copyBytesPerRowAlignment)
            require(alignmentI64 % 256L == 0L && alignmentI64 % limits.copyBytesPerRowAlignment == 0L)
            val logicalRowI64 = Math.multiplyExact(request.widthI32.toLong(), request.format.bytesPerPixelI32.toLong())
            val rowI64 = Math.addExact(logicalRowI64, (alignmentI64 - logicalRowI64 % alignmentI64) % alignmentI64)
            listOf(
                GPUFrameMemoryAllocation("w5e.image-v3.${request.canonicalPhysicalIdentity}", GPUFrameMemoryCategory.ReusableScratch,
                    request.byteSizeI64, GPUFrameMemoryResourceKind.Texture2D, GPUPixelBounds(0, 0, request.widthI32, request.heightI32),
                    0, steps.size.coerceAtLeast(1)),
                GPUFrameMemoryAllocation("w5e.upload-v3.${request.canonicalPhysicalIdentity}", GPUFrameMemoryCategory.ReusableScratch,
                    Math.multiplyExact(rowI64, request.heightI32.toLong()), GPUFrameMemoryResourceKind.Buffer, null,
                    0, steps.size.coerceAtLeast(1)),
            )
        }

internal fun GPUFramePlan.w5aCombinedMemoryBudgetV2(limits: GPULimits): GPUFrameMemoryBudgetPlan =
    GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(
        allocations = memoryBudget.allocations + w5aMaterialAllocationsV2() + w5eImageAllocationsV3(limits),
        configuredAggregateBudgetBytes = memoryBudget.configuredAggregateBudgetBytes,
        deviceLimits = limits,
    ))
