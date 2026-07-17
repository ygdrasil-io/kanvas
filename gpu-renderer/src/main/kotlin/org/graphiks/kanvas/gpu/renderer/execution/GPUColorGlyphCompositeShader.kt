package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl

/** Parser-backed COLRv0 shader source ready for native pipeline materialization. */
internal data class GPUColorGlyphCompositePlan(
    val wgslSource: String,
    val wgslReflection: GPUColorWgslReflection?,
)

/** Outcome of preparing the native COLRv0 shader source. */
internal sealed interface GPUColorGlyphCompositeShaderResult {
    data class Ready(val plan: GPUColorGlyphCompositePlan) : GPUColorGlyphCompositeShaderResult

    data class Rejected(val reason: String, val message: String) : GPUColorGlyphCompositeShaderResult
}

/** Generates and parser-validates the fixed native COLRv0 composite shader. */
internal fun buildColorGlyphCompositeShader(
    maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS,
): GPUColorGlyphCompositeShaderResult {
    val wgsl = colorGlyphCompositeWgsl(maxLayers)
    return when (val validation = validateColorWgsl(sourceId = "text.colrv0.composite", wgslSource = wgsl)) {
        is GPUColorWgslValidation.Validated ->
            GPUColorGlyphCompositeShaderResult.Ready(
                GPUColorGlyphCompositePlan(wgslSource = wgsl, wgslReflection = validation.reflection),
            )
        is GPUColorWgslValidation.Rejected ->
            GPUColorGlyphCompositeShaderResult.Rejected(reason = validation.reason, message = validation.message)
    }
}
