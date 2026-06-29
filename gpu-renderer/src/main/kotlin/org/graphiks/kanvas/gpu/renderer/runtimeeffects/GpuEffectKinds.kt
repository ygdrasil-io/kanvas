package org.graphiks.kanvas.gpu.renderer.runtimeeffects

enum class GPURuntimeEffectKind {
    Material,
    ColorFilter,
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
        GPURuntimeEffectKind.ColorFilter -> setOf("color_filter_pipeline", "color_transform")
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
        wgslModule: Any,
    ): GPURuntimeEffectKindResult
}

object KanvasRuntimeEffectKindValidator : GPURuntimeEffectKindValidator {
    override fun validate(
        effect: GPURuntimeEffectDescriptor,
        wgslModule: Any,
    ): GPURuntimeEffectKindResult {
        val kind = effect.routeContract.acceptedPlacements.primaryKind()
        return if (kind != null) {
            GPURuntimeEffectKindResult.Accepted
        } else {
            GPURuntimeEffectKindResult.Refused(
                diagnosticCode = "unsupported.runtime_effect.kind_not_registered",
                reason = "No primary kind derived from accepted placements: ${effect.routeContract.acceptedPlacements}",
            )
        }
    }
}

private fun Set<GPURuntimeEffectRoutePlacement>.primaryKind(): GPURuntimeEffectKind? =
    firstNotNullOfOrNull { placement -> placement.toKind() }

private fun GPURuntimeEffectRoutePlacement.toKind(): GPURuntimeEffectKind? = when (this) {
    GPURuntimeEffectRoutePlacement.MaterialSource -> GPURuntimeEffectKind.Material
    GPURuntimeEffectRoutePlacement.MaterialColorFilter -> GPURuntimeEffectKind.ColorFilter
    GPURuntimeEffectRoutePlacement.MaterialBlender -> GPURuntimeEffectKind.Blender
    GPURuntimeEffectRoutePlacement.ClipShader -> GPURuntimeEffectKind.ClipShader
    GPURuntimeEffectRoutePlacement.FilterComputeNode -> GPURuntimeEffectKind.Compute
    GPURuntimeEffectRoutePlacement.FilterRenderNode -> null
    GPURuntimeEffectRoutePlacement.PrimitiveBlender -> null
    GPURuntimeEffectRoutePlacement.CPUReferenceOnly -> null
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
