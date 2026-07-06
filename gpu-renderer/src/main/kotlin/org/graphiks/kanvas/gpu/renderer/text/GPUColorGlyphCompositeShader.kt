package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.wgsl.colorGlyphCompositeWgsl

/**
 * A parser-backed COLRv0 composite shader plan: the generated WGSL source and
 * its reflection. Renderer-neutral - no GPU handle, no pixels.
 */
data class GPUColorGlyphCompositePlan(
    val wgslSource: String,
    val wgslReflection: GPUColorWgslReflection?,
)

/** Outcome of building the COLRv0 composite shader plan. */
sealed interface GPUColorGlyphCompositeShaderResult {
    /** Shader generated and validated through parser-backed WGSL validation. */
    data class Ready(val plan: GPUColorGlyphCompositePlan) : GPUColorGlyphCompositeShaderResult

    /** Parser-backed WGSL validation rejected the generated shader. */
    data class Rejected(val reason: String, val message: String) : GPUColorGlyphCompositeShaderResult
}

/**
 * Generates and validates the COLRv0 composite shader through registered WGSL validation, mirroring
 * the color-management WGSL validation path in `GPUColorWgsl`.
 */
fun buildColorGlyphCompositeShader(
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
