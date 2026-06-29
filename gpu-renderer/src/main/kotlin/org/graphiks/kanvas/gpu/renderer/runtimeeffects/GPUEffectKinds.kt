package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.wgsl.proc.reflectWgslModule

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
            ?: return GPURuntimeEffectKindResult.Refused(
                diagnosticCode = "unsupported.runtime_effect.kind_not_registered",
                reason = "No primary kind derived from accepted placements: ${effect.routeContract.acceptedPlacements}",
            )
        if (wgslModule is String) {
            val stageResult = validateEntryPointStage(kind, wgslModule)
            if (stageResult is GPURuntimeEffectKindResult.Refused) {
                return stageResult
            }
        }
        return GPURuntimeEffectKindResult.Accepted
    }

    /**
     * Reflects [wgslSource] via wgsl4k and returns the first entry point stage
     * (`vertex`, `fragment`, or `compute`), or null when the source cannot be
     * parsed/lowered or wgsl4k is unavailable on the classpath.
     */
    fun entryPointStage(wgslSource: String): String? =
        try {
            val parsed = parseWgslResult(wgslSource)
            if (!parsed.isSuccess) {
                null
            } else {
                Lowerer().lower(parsed.translationUnit)
                    .reflectWgslModule(sourceId = "runtime-effect-kind-validation")
                    .entryPoints
                    .firstOrNull()
                    ?.stage
            }
        } catch (_: NoClassDefFoundError) {
            null
        } catch (_: ClassNotFoundException) {
            null
        }

    /**
     * Validates that the WGSL [wgslSource] entry point stage matches the stage
     * required by [kind]. Returns [GPURuntimeEffectKindResult.Accepted] when the
     * stage cannot be determined so reflection availability never blocks a kind.
     */
    fun validateEntryPointStage(
        kind: GPURuntimeEffectKind,
        wgslSource: String,
    ): GPURuntimeEffectKindResult {
        val stage = entryPointStage(wgslSource) ?: return GPURuntimeEffectKindResult.Accepted
        val expectedStage = kind.expectedEntryPointStage()
        return if (stage == expectedStage) {
            GPURuntimeEffectKindResult.Accepted
        } else {
            GPURuntimeEffectKindResult.Refused(
                diagnosticCode = "unsupported.runtime_effect.entry_point_stage_mismatch",
                reason = "WGSL entry point stage '$stage' does not match expected '$expectedStage' for kind $kind",
            )
        }
    }
}

/** Maps each runtime-effect kind to the WGSL entry point stage it requires. */
private fun GPURuntimeEffectKind.expectedEntryPointStage(): String = when (this) {
    GPURuntimeEffectKind.Compute -> "compute"
    GPURuntimeEffectKind.Material,
    GPURuntimeEffectKind.ColorFilter,
    GPURuntimeEffectKind.Blender,
    GPURuntimeEffectKind.ClipShader,
    -> "fragment"
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
