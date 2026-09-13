package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupEntry
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

/** Native W5e sees only the sealed frame, physical cache request, and device generation. */
internal object GPUW5eImageNativeV1 {
    fun validate(frame: GPUFramePlan): Boolean {
        val packets = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        val expected = frame.w5eConstructionV1
        if (expected == null) {
            require(frame.w5ePreparedFrameV1 == null && packets.none { it.w5eImageFrameWitnessV1 != null })
            return false
        }
        val witness = requireNotNull(frame.w5ePreparedFrameV1) { W5eImagePlanDiagnostics.InvalidContract }
        require(witness.bridge === expected && witness.validates(frame)) {
            W5eImagePlanDiagnostics.InvalidContract
        }
        return expected.imageDraws().isNotEmpty()
    }
    fun acquire(cache: GPUW5eDecodedImageSessionCache, request: PlanCacheResourceRequest,
        generationI64: Long): GPUW5eDecodedImageSessionCache.Lease {
        require(cache.deviceGenerationI64 == generationI64) { "stale.material.image.device-generation" }
        return cache.acquire(request).also { require(it.generationI64 == generationI64) }
    }
    fun binding(lease: GPUW5eDecodedImageSessionCache.Lease, bindingU32: UInt): BindGroupEntry =
        BindGroupEntry(binding = bindingU32, resource = lease.view)
}
