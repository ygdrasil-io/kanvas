package org.graphiks.kanvas.text

import org.graphiks.kanvas.geometry.Path

interface Typeface {
    val fontName: String
    fun glyphIdForCodepoint(codepoint: Int): Int
    fun getAdvance(glyphId: Int, fontSize: Float): Float
    fun getGlyphPath(glyphId: Int, fontSize: Float): Path?
}
