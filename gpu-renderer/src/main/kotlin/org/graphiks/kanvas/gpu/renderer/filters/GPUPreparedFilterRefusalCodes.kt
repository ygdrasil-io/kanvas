package org.graphiks.kanvas.gpu.renderer.filters

/**
 * Canonical authority for prepared filter refusal codes.
 * Every refusal code is a stable string constant with no duplicate values.
 */
object GPUPreparedFilterRefusalCodes {
    const val GRAPH_CYCLE = "unsupported.filter.graph.cycle"
    const val GRAPH_BUDGET = "unsupported.filter.graph.budget"
    const val PARAMETER_NON_FINITE = "unsupported.filter.parameter.non_finite"
    const val BOUNDS_OVERFLOW = "unsupported.filter.bounds.overflow"
    const val INTERMEDIATE_BUDGET = "unsupported.filter.intermediate.budget"
    const val NATIVE_CAPABILITY = "unsupported.filter.native.capability"
    const val RUNTIME_EFFECT_DESCRIPTOR = "unsupported.filter.runtime_effect.descriptor"
    const val RUNTIME_EFFECT_WGSL_NOT_AVAILABLE = "unsupported.filter.runtime_effect.wgsl_not_available"
    const val RUNTIME_EFFECT_ABI = "unsupported.filter.runtime_effect.abi"
    const val RUNTIME_EFFECT_CHILD = "unsupported.filter.runtime_effect.child"

    val ALL: Set<String> = setOf(
        GRAPH_CYCLE,
        GRAPH_BUDGET,
        PARAMETER_NON_FINITE,
        BOUNDS_OVERFLOW,
        INTERMEDIATE_BUDGET,
        NATIVE_CAPABILITY,
        RUNTIME_EFFECT_DESCRIPTOR,
        RUNTIME_EFFECT_WGSL_NOT_AVAILABLE,
        RUNTIME_EFFECT_ABI,
        RUNTIME_EFFECT_CHILD,
    )
}
