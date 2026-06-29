package org.graphiks.kanvas.gpu.renderer.text

enum class GPUFallbackReason {
    MissingGlyph,
    UnsupportedScript,
    ColorFontPreference,
}

data class GPUFallbackGlyphPlan(
    val glyphIndex: Int,
    val originalFont: GPUFontKey,
    val fallbackFont: GPUFontKey,
    val reason: GPUFallbackReason,
)

data class GPUFallbackSubrun(
    val fontKey: GPUFontKey,
    val glyphRange: IntRange,
    val atlasPage: GPUAtlasPageRef,
)

data class GPUFallbackBatchPolicy(
    val subruns: List<GPUFallbackSubrun>,
    val maxFallbackDepth: Int,
) {
    companion object {
        fun splitSubruns(
            glyphs: List<GPUFallbackGlyphPlan>,
            maxFallbackDepth: Int,
        ): List<GPUFallbackSubrun> {
            if (glyphs.isEmpty() || maxFallbackDepth <= 0) return emptyList()
            val subruns = mutableListOf<GPUFallbackSubrun>()
            var currentFont: GPUFontKey = glyphs.first().fallbackFont
            var startIdx = glyphs.first().glyphIndex
            for (i in 1 until glyphs.size) {
                val glyph = glyphs[i]
                if (glyph.fallbackFont != currentFont) {
                    subruns.add(
                        GPUFallbackSubrun(
                            fontKey = currentFont,
                            glyphRange = startIdx..glyphs[i - 1].glyphIndex,
                            atlasPage = GPUAtlasPageRef(currentFont.value),
                        ),
                    )
                    currentFont = glyph.fallbackFont
                    startIdx = glyph.glyphIndex
                }
            }
            subruns.add(
                GPUFallbackSubrun(
                    fontKey = currentFont,
                    glyphRange = startIdx..glyphs.last().glyphIndex,
                    atlasPage = GPUAtlasPageRef(currentFont.value),
                ),
            )
            return subruns
        }

        fun fallbackExhaustedDiagnostic(
            glyphIndex: Int,
            fontIdentity: String,
        ): GPUTextDiagnostic = GPUTextDiagnostic(
            code = GPUTextDiagnosticCodes.FALLBACK_EXHAUSTED,
            message = "Fallback chain exhausted for glyph $glyphIndex (font: $fontIdentity)",
            terminal = true,
        )
    }
}
