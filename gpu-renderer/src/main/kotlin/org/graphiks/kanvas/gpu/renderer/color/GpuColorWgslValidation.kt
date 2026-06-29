package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.wgsl.proc.reflectWgslModule

/**
 * Local wgsl4k validation evidence for generated color-management WGSL.
 *
 * The reflection report produced by wgsl4k is consumed transiently and reduced
 * to this Kanvas-owned summary so plans never expose the `compileOnly`
 * `org.graphiks.wgsl.proc` report type at runtime. `validated` is `false` only
 * when wgsl4k is unavailable on the classpath; a syntactically or semantically
 * invalid module produces a [GpuColorWgslValidation.Rejected] instead.
 */
data class GpuColorWgslReflection(
    val sourceId: String,
    val validated: Boolean,
    val reflectionSource: String,
    val entryPointCount: Int,
    val unsupportedFeatures: List<String> = emptyList(),
)

/** Result of validating a generated color WGSL snippet through wgsl4k. */
sealed interface GpuColorWgslValidation {
    /** wgsl4k parsed, lowered, and reflected the source (or was unavailable). */
    data class Validated(val reflection: GpuColorWgslReflection) : GpuColorWgslValidation

    /** wgsl4k reported the generated WGSL as invalid. */
    data class Rejected(val reason: String, val message: String) : GpuColorWgslValidation
}

/**
 * Validates generated color WGSL by parsing, lowering, and reflecting it
 * through wgsl4k. When wgsl4k is absent from the runtime classpath the source
 * is treated as accepted-but-unvalidated so reflection availability never
 * blocks a color route; only an actual wgsl4k validation failure is rejected.
 */
fun validateColorWgsl(sourceId: String, wgslSource: String): GpuColorWgslValidation =
    try {
        val parsed = parseWgslResult(wgslSource)
        if (!parsed.isSuccess) {
            GpuColorWgslValidation.Rejected(
                reason = "wgsl4k.validation.syntax_error",
                message = parsed.errors.joinToString("; ") { it.message },
            )
        } else {
            val report = Lowerer().lower(parsed.translationUnit).reflectWgslModule(sourceId = sourceId)
            if (!report.validation.success) {
                GpuColorWgslValidation.Rejected(
                    reason = report.validation.diagnostics.firstOrNull()?.reason
                        ?: "wgsl4k.validation.failed",
                    message = report.validation.diagnostics.joinToString("; ") { it.message }
                        .ifBlank { "wgsl4k validation failed" },
                )
            } else {
                GpuColorWgslValidation.Validated(
                    GpuColorWgslReflection(
                        sourceId = report.sourceId,
                        validated = true,
                        reflectionSource = "wgsl4k-parsed",
                        entryPointCount = report.entryPoints.size,
                        unsupportedFeatures = report.unsupportedFeatures,
                    ),
                )
            }
        }
    } catch (_: NoClassDefFoundError) {
        GpuColorWgslValidation.Validated(unavailableReflection(sourceId))
    } catch (_: ClassNotFoundException) {
        GpuColorWgslValidation.Validated(unavailableReflection(sourceId))
    }

private fun unavailableReflection(sourceId: String): GpuColorWgslReflection =
    GpuColorWgslReflection(
        sourceId = sourceId,
        validated = false,
        reflectionSource = "wgsl4k-unavailable",
        entryPointCount = 0,
    )
