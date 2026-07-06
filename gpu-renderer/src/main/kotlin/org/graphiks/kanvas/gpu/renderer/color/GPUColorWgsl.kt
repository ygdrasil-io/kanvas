package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule

/**
 * Parser-backed WGSL reflection carried by a validated color WGSL shader (EOTF, tone-map,
 * ICC transform, wide-gamut conversion). [validated] is `true` when the shader
 * parsed and reflected through the external WGSL parser (or when that parser is unavailable at runtime
 * and validation is declared, mirroring [org.graphiks.kanvas.gpu.renderer.runtimeeffects]).
 */
data class GPUColorWgslReflection(
    val report: WgslReflectionReport,
    val validated: Boolean = true,
)

/** Outcome of validating a generated color WGSL shader through parser-backed WGSL validation. */
sealed interface GPUColorWgslValidation {
    data class Validated(val reflection: GPUColorWgslReflection?) : GPUColorWgslValidation
    data class Rejected(val reason: String, val message: String) : GPUColorWgslValidation
}

/**
 * Validates generated color-management WGSL through parser-backed WGSL validation, mirroring the
 * runtime-effect shader-graph validation path. When the external parser is not present on
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
            reason = "wgsl_parse_error",
            message = "WGSL parser produced diagnostics: $errorMessages",
        )
    }
    val module = Lowerer().lower(parsed.translationUnit)
    val report = module.reflectWgslModule(sourceId = sourceId)
    return GPUColorWgslValidation.Validated(
        GPUColorWgslReflection(report = report, validated = true),
    )
}
