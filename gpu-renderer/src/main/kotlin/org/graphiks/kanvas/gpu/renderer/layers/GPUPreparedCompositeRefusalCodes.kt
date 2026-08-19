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
    const val LAYER_GATE_MISSING = "unsupported.composite.layer.gate_missing"
    const val OPERATION = "unsupported.composite.operation"
    const val PAINT = "unsupported.composite.paint"
    const val CLIP = "unsupported.composite.clip"
    const val PREFLIGHT = "unsupported.composite.preflight"

    val ALL: Set<String> = setOf(
        PICTURE_CYCLE,
        PICTURE_BUDGET,
        LAYER_UNBALANCED,
        LAYER_BOUNDS,
        LAYER_BUDGET,
        LAYER_GATE_MISSING,
        OPERATION,
        PAINT,
        CLIP,
        PREFLIGHT,
    )
}
