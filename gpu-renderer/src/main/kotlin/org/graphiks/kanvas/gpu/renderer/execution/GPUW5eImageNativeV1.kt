package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupEntry
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

/** Native W5e sees only the sealed frame, physical cache request, and device generation. */
internal object GPUW5eImageNativeV1 {
    fun validate(frame: GPUFramePlan): Boolean {
        val packets = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        val expected = packets.any { it.w5bFinalFrameWitnessV3?.graph?.capabilityId == W5eImagePlanCompiler.CONSTRUCTION_CAPABILITY_ID }
        if (!expected) { require(packets.none { it.w5eImageFrameWitnessV1 != null }); return false }
        val witness = requireNotNull(packets.firstOrNull()?.w5eImageFrameWitnessV1) { W5eImagePlanDiagnostics.InvalidContract }
        require(witness.validates(frame) && packets.all { it.materialSourcePartitionV3()?.stage?.imageV3 != null }) {
            W5eImagePlanDiagnostics.InvalidContract
        }
        return true
    }
    fun acquire(cache: GPUW5eDecodedImageSessionCache, request: PlanCacheResourceRequest,
        generationI64: Long): GPUW5eDecodedImageSessionCache.Lease {
        require(cache.deviceGenerationI64 == generationI64) { "stale.material.image.device-generation" }
        return cache.acquire(request).also { require(it.generationI64 == generationI64) }
    }
    fun binding(lease: GPUW5eDecodedImageSessionCache.Lease, bindingU32: UInt): BindGroupEntry =
        BindGroupEntry(binding = bindingU32, resource = lease.view)
}
