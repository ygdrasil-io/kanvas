package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.wgsl.parser.parseWgslResult

data class RuntimeEffectWgslValidation(val accepted: Boolean, val diagnostic: String? = null)

/**
 * Concrete runtime-effect registry wired to registered Kotlin/WGSL descriptors.
 * Provides lookups for all registered runtime effects.
 */
class KanvasRuntimeEffectRegistry : GPURuntimeEffectRegistry {
    private val descriptors: Map<GPURuntimeEffectID, GPURuntimeEffectDescriptor> = mapOf(
        SimpleRTDescriptor.effectId to SimpleRTDescriptor.createDescriptor(),
        LinearGradientRTDescriptor.effectId to LinearGradientRTDescriptor.createDescriptor(),
        SpiralRTDescriptor.effectId to SpiralRTDescriptor.createDescriptor(),
        ColorFilterLumaToAlphaDescriptor.effectId to ColorFilterLumaToAlphaDescriptor.createDescriptor(),
        ColorFilterNoopDescriptor.effectId to ColorFilterNoopDescriptor.createDescriptor(),
        ColorFilterTernaryDescriptor.effectId to ColorFilterTernaryDescriptor.createDescriptor(),
        ColorFilterIfsDescriptor.effectId to ColorFilterIfsDescriptor.createDescriptor(),
        ColorFilterEarlyReturnDescriptor.effectId to ColorFilterEarlyReturnDescriptor.createDescriptor(),
        IntrinsicsCommonDescriptor.effectId to IntrinsicsCommonDescriptor.createDescriptor(),
        IntrinsicsTrigDescriptor.effectId to IntrinsicsTrigDescriptor.createDescriptor(),
        IntrinsicsExponentialDescriptor.effectId to IntrinsicsExponentialDescriptor.createDescriptor(),
        IntrinsicsGeometricDescriptor.effectId to IntrinsicsGeometricDescriptor.createDescriptor(),
        IntrinsicsMatrixDescriptor.effectId to IntrinsicsMatrixDescriptor.createDescriptor(),
        IntrinsicsRelationalDescriptor.effectId to IntrinsicsRelationalDescriptor.createDescriptor(),
        RuntimeFunctionsDescriptor.effectId to RuntimeFunctionsDescriptor.createDescriptor(),
        RippleDescriptor.effectId to RippleDescriptor.createDescriptor(),
        ArithmodeDescriptor.effectId to ArithmodeDescriptor.createDescriptor(),
        LumaFilterDescriptor.effectId to LumaFilterDescriptor.createDescriptor(),
        GChannelSplatDescriptor.effectId to GChannelSplatDescriptor.createDescriptor(),
        ComposeColorFilterDescriptor.effectId to ComposeColorFilterDescriptor.createDescriptor(),
    )

    override fun lookup(id: GPURuntimeEffectID): GPURuntimeEffectDescriptor? = descriptors[id]

    fun snapshot(
        registryVersion: String = "runtime-registry-v1",
        generation: Long = 1L,
        provenance: String = "kanvas-registered-descriptors",
    ): GPURuntimeEffectRegistrySnapshot = GPURuntimeEffectRegistrySnapshot(
        registryVersion, generation, descriptors.values.toList(), provenance,
    )

    fun validateWgsl(id: GPURuntimeEffectID): RuntimeEffectWgslValidation {
        val source = lookup(id)?.wgslSource
            ?: return RuntimeEffectWgslValidation(false, "unsupported.runtime_effect.unregistered_descriptor")
        return try {
            val parsed = parseWgslResult(source)
            if (parsed.isSuccess) RuntimeEffectWgslValidation(true)
            else RuntimeEffectWgslValidation(false, "unsupported.runtime_effect.wgsl_reflection")
        } catch (_: NoClassDefFoundError) {
            RuntimeEffectWgslValidation(false, "unsupported.runtime_effect.wgsl_parser_unavailable")
        }
    }
}
