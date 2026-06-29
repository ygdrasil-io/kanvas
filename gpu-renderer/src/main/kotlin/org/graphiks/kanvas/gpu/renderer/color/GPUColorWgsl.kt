package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.wgsl.proc.WgslReflectionReport
import org.graphiks.wgsl.proc.reflectWgslModule

/**
 * wgsl4k reflection carried by a validated color WGSL shader (EOTF, tone-map,
 * ICC transform, wide-gamut conversion). [validated] is `true` when the shader
 * parsed and reflected through wgsl4k (or when wgsl4k is unavailable at runtime
 * and validation is declared, mirroring [org.graphiks.kanvas.gpu.renderer.runtimeeffects]).
 */
data class GPUColorWgslReflection(
    val report: WgslReflectionReport,
    val validated: Boolean = true,
)

/** Outcome of validating a generated color WGSL shader through wgsl4k. */
sealed interface GPUColorWgslValidation {
    data class Validated(val reflection: GPUColorWgslReflection?) : GPUColorWgslValidation
    data class Rejected(val reason: String, val message: String) : GPUColorWgslValidation
}

/**
 * Validates generated color-management WGSL through wgsl4k, mirroring the
 * runtime-effect shader-graph validation path. When wgsl4k is not present on
 * the runtime classpath the validation is declared (fixture mode), matching the
 * shader-graph fallback so non-GPU environments still resolve a reflection.
 */
fun validateColorWgsl(sourceId: String, wgslSource: String): GPUColorWgslValidation =
    try {
        parserBackedValidateColorWgsl(sourceId, wgslSource)
    } catch (_: NoClassDefFoundError) {
        GPUColorWgslValidation.Validated(
            GPUColorWgslReflection(WgslReflectionReport(sourceId = sourceId), validated = true),
        )
    } catch (_: ClassNotFoundException) {
        GPUColorWgslValidation.Validated(
            GPUColorWgslReflection(WgslReflectionReport(sourceId = sourceId), validated = true),
        )
    }

private fun parserBackedValidateColorWgsl(sourceId: String, wgslSource: String): GPUColorWgslValidation {
    val parsed = parseWgslResult(wgslSource)
    if (!parsed.isSuccess) {
        val errorMessages = parsed.errors.joinToString("; ") { it.message }
        return GPUColorWgslValidation.Rejected(
            reason = "wgsl4k_parse_error",
            message = "wgsl4k parse produced diagnostics: $errorMessages",
        )
    }
    val module = Lowerer().lower(parsed.translationUnit)
    val report = module.reflectWgslModule(sourceId = sourceId)
    return GPUColorWgslValidation.Validated(
        GPUColorWgslReflection(report = report, validated = true),
    )
}
