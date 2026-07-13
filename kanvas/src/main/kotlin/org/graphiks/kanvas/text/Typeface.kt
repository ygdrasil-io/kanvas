package org.graphiks.kanvas.text

import org.graphiks.kanvas.geometry.Path

interface Typeface {
    val fontName: String
    /** Units per em used by this face's design space. */
    val unitsPerEm: Float get() = 1_000f
    fun glyphIdForCodepoint(codepoint: Int): Int
    fun getAdvance(glyphId: Int, fontSize: Float): Float
    /** Returns an advance at an optional OpenType variation position. */
    fun getAdvance(
        glyphId: Int,
        fontSize: Float,
        variationCoordinates: Map<String, Float>,
    ): Float = getAdvance(glyphId, fontSize)
    fun getGlyphPath(glyphId: Int, fontSize: Float): Path?
    /** Returns a glyph outline at an optional OpenType variation position. */
    fun getGlyphPath(
        glyphId: Int,
        fontSize: Float,
        variationCoordinates: Map<String, Float>,
    ): Path? = getGlyphPath(glyphId, fontSize)
}
