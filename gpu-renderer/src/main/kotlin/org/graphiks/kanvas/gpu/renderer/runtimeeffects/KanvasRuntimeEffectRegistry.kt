package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/**
 * Concrete runtime-effect registry wired to registered Kotlin/WGSL descriptors.
 * Provides lookups for simple_rt, linear_gradient_rt, and spiral_rt effects.
 */
class KanvasRuntimeEffectRegistry : GPURuntimeEffectRegistry {
    private val descriptors: Map<GPURuntimeEffectID, GPURuntimeEffectDescriptor> = mapOf(
        SimpleRTDescriptor.effectId to SimpleRTDescriptor.createDescriptor(),
        LinearGradientRTDescriptor.effectId to LinearGradientRTDescriptor.createDescriptor(),
        SpiralRTDescriptor.effectId to SpiralRTDescriptor.createDescriptor(),
    )

    override fun lookup(id: GPURuntimeEffectID): GPURuntimeEffectDescriptor? = descriptors[id]
}
