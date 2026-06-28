package org.graphiks.kanvas.gpu.renderer.runtimeeffects

enum class GPURuntimeEffectKind {
    Material,
    Blender,
    ClipShader,
    Compute,
}

data class GPURuntimeEffectKindContract(
    val kind: GPURuntimeEffectKind,
    val entryPointSignature: String,
    val routePlacement: GPURuntimeEffectRoutePlacement,
) {
    val requiredCapabilities: Set<String> = when (kind) {
        GPURuntimeEffectKind.Material -> setOf("coords_to_unpremul")
        GPURuntimeEffectKind.Blender -> setOf("premultiplied_input", "premultiplied_output")
        GPURuntimeEffectKind.ClipShader -> setOf("coverage_float_output")
        GPURuntimeEffectKind.Compute -> setOf("storage_buffer_io", "compute_dispatch")
    }
}

sealed interface GPURuntimeEffectKindResult {
    data object Accepted : GPURuntimeEffectKindResult
    data class Refused(val diagnosticCode: String, val reason: String) : GPURuntimeEffectKindResult
}

interface GPURuntimeEffectKindValidator {
    fun validate(
        effect: GPURuntimeEffectDescriptor,
        wgslModule: Any = TODO("Wire GPURuntimeEffectKindValidator to wgsl4k parser"),
    ): GPURuntimeEffectKindResult = TODO("Wire GPURuntimeEffectKindValidator to wgsl4k parser")
}

object DefaultGPURuntimeEffectKindValidator {
    fun validate(
        kind: GPURuntimeEffectKind,
        acceptedKinds: Set<GPURuntimeEffectKind>,
    ): GPURuntimeEffectKindResult {
        if (kind !in acceptedKinds) {
            return GPURuntimeEffectKindResult.Refused(
                diagnosticCode = "unsupported.runtime_effect.kind_not_registered",
                reason = "Effect kind $kind is not registered",
            )
        }
        return GPURuntimeEffectKindResult.Accepted
    }
}
