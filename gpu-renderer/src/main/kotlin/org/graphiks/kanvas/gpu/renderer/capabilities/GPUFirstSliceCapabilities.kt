package org.graphiks.kanvas.gpu.renderer.capabilities

/** Canonical handle-free names for the first native clip and path-fill slice. */
object GPUFirstSliceCapabilityName {
    const val FILL_DRRECT_NATIVE = "first_slice.fill_drrect.native"
    const val SCISSOR_NATIVE = "first_slice.scissor.native"
    const val BOUNDED_CLIP_NATIVE = "first_slice.bounded_clip.native"
    const val PATH_FILL_STENCIL_COVER = "first_slice.path_fill.stencil_cover"
    const val STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE =
        "first_slice.stroke_rect.linear_gradient_three_stop.native"
    const val STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE =
        "first_slice.stroke_rect.radial_gradient_two_stop.native"
    const val STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE =
        "first_slice.stroke_rect.sweep_gradient_two_stop.native"
    const val STROKE_RECT_RADIAL_GRADIENT_THREE_STOP_NATIVE =
        "first_slice.stroke_rect.radial_gradient_three_stop.native"
    const val STROKE_RECT_SWEEP_GRADIENT_THREE_STOP_NATIVE =
        "first_slice.stroke_rect.sweep_gradient_three_stop.native"
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
