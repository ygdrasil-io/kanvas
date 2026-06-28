package org.graphiks.kanvas.gpu.renderer.text

enum class GpuFallbackReason {
    MissingGlyph,
    UnsupportedScript,
    ColorFontPreference,
}

data class GpuFallbackGlyphPlan(
    val glyphIndex: Int,
    val originalFont: String,
    val fallbackFont: String,
    val reason: GpuFallbackReason,
)

data class GpuFallbackSubrun(
    val fontKey: String,
    val glyphRange: IntRange,
    val atlasPage: String,
)

data class GpuFallbackBatchPolicy(
    val subruns: List<GpuFallbackSubrun>,
    val maxFallbackDepth: Int,
) {
    companion object {
        fun splitSubruns(
            glyphs: List<GpuFallbackGlyphPlan>,
            maxFallbackDepth: Int,
        ): List<GpuFallbackSubrun> {
            if (glyphs.isEmpty() || maxFallbackDepth <= 0) return emptyList()
            val subruns = mutableListOf<GpuFallbackSubrun>()
            var currentFont: String = glyphs.first().fallbackFont
            var startIdx = glyphs.first().glyphIndex
            for (i in 1 until glyphs.size) {
                val glyph = glyphs[i]
                if (glyph.fallbackFont != currentFont) {
                    subruns.add(GpuFallbackSubrun(currentFont, startIdx..glyphs[i - 1].glyphIndex, currentFont))
                    currentFont = glyph.fallbackFont
                    startIdx = glyph.glyphIndex
                }
            }
            subruns.add(GpuFallbackSubrun(currentFont, startIdx..glyphs.last().glyphIndex, currentFont))
            return subruns
        }
    }
}
