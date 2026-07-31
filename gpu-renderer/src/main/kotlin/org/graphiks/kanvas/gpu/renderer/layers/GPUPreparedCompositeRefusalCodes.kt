package org.graphiks.kanvas.gpu.renderer.layers

/**
 * Canonical authority for prepared composite refusal codes.
 * Every refusal code is a stable string constant with no duplicate values.
 */
object GPUPreparedCompositeRefusalCodes {
    const val PICTURE_CYCLE = "unsupported.composite.picture.cycle"
    const val PICTURE_BUDGET = "unsupported.composite.picture.budget"
    const val LAYER_UNBALANCED = "unsupported.composite.layer.unbalanced"
    const val LAYER_BOUNDS = "unsupported.composite.layer.bounds"
    const val LAYER_BUDGET = "unsupported.composite.layer.budget"
    const val LAYER_DESTINATION_READ = "unsupported.composite.layer.destination_read"
    const val OPERATION = "unsupported.composite.operation"
    const val PAINT = "unsupported.composite.paint"
    const val CLIP = "unsupported.composite.clip"
    const val NATIVE_ALIAS = "unsupported.composite.native.alias"
    const val NATIVE_CAPABILITY = "unsupported.composite.native.capability"
    const val PREFLIGHT = "unsupported.composite.preflight"
    const val LAYER_GATE_MISSING = "unsupported.composite.layer_gate_missing"

    val ALL: Set<String> = setOf(
        PICTURE_CYCLE,
        PICTURE_BUDGET,
        LAYER_UNBALANCED,
        LAYER_BOUNDS,
        LAYER_BUDGET,
        OPERATION,
        PAINT,
        CLIP,
        NATIVE_ALIAS,
        NATIVE_CAPABILITY,
        PREFLIGHT,
        LAYER_GATE_MISSING,
    )
}
