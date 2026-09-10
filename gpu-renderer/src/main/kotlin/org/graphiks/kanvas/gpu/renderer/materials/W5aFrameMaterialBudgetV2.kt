package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.*

/**
 * Versioned material partition alongside the exact historical geometry allocation seal.
 * Raw buffers are completion-owned and live through the complete frame; nothing is allocated
 * until this combined inventory has passed the configured aggregate budget.
 */
internal fun GPUFramePlan.w5aMaterialAllocationsV2(): List<GPUFrameMemoryAllocation> =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .filter { it.w5aSourceStageV2 != null }
        .distinctBy { requireNotNull(it.w5aSourceStageV2).stage.canonicalIdentity }
        .map { packet -> requireNotNull(packet.w5aSourceStageV2).let { source ->
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

internal fun GPUFramePlan.w5aCombinedMemoryBudgetV2(limits: GPULimits): GPUFrameMemoryBudgetPlan =
    GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(
        allocations = memoryBudget.allocations + w5aMaterialAllocationsV2(),
        configuredAggregateBudgetBytes = memoryBudget.configuredAggregateBudgetBytes,
        deviceLimits = limits,
    ))
