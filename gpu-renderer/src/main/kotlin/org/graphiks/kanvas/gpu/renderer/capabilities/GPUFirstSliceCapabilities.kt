package org.graphiks.kanvas.gpu.renderer.capabilities

/** Canonical handle-free names for the first native clip and path-fill slice. */
object GPUFirstSliceCapabilityName {
    const val SCISSOR_NATIVE = "first_slice.scissor.native"
    const val BOUNDED_CLIP_NATIVE = "first_slice.bounded_clip.native"
    const val PATH_FILL_STENCIL_COVER = "first_slice.path_fill.stencil_cover"
}

/** Builds a validity-affecting fact for one explicitly supported GPU capability. */
fun supportedGPUCapabilityFact(
    name: String,
    source: String,
    evidenceLabel: String,
): GPUCapabilityFact {
    require(name.isNotBlank()) { "name must not be blank" }
    require(source.isNotBlank()) { "source must not be blank" }
    require(evidenceLabel.isNotBlank()) { "evidenceLabel must not be blank" }
    return GPUCapabilityFact(
        name = name,
        source = source,
        value = "supported",
        affectsValidity = true,
        evidenceLabel = evidenceLabel,
    )
}
