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
        expected.imageDraws().forEach { image ->
            // The native path never synthesizes an addressing rule or hardware sampler: its only
            // image resource is the sealed textureLoad source authenticated by the plan table.
            require(image.execution.upload.widthI32 > 0 && image.execution.upload.heightI32 > 0 &&
                image.execution.numericAuthority.graph.sampling == image.execution.sampling &&
                image.execution.numericAuthority.graph.tileModes == image.execution.tileModes) {
                W5eImagePlanDiagnostics.InvalidContract
            }
            val cubic = image.execution.sampling as? ImageSamplingPlanV1.Cubic
            require(cubic == null || cubic.bF32.isFinite() && cubic.bF32 in 0f..1f &&
                cubic.cF32.isFinite() && cubic.cF32 in 0f..1f) { W5eImagePlanDiagnostics.CubicParameters }
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
