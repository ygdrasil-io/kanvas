package org.graphiks.kanvas.text

import org.graphiks.kanvas.geometry.Path

/**
 * A reference to a typeface resource accessible via [resourcePath].
 *
 * @property resourcePath  location of the font file (e.g. a bundled asset or
 *                         filesystem path).
 */
data class KanvasTypeface(val resourcePath: String) : Typeface {
    override val fontName: String get() = resourcePath
    /** Stub — real glyph resolution comes from [FontTypeface] which wraps GlyphScaler. */
    override fun glyphIdForCodepoint(codepoint: Int): Int = 0
    /** Stub — real advance comes from [FontTypeface]. */
    override fun getAdvance(glyphId: Int, fontSize: Float): Float = 0f
    /** Stub — real paths come from [FontTypeface]. */
    override fun getGlyphPath(glyphId: Int, fontSize: Float): Path? = null
}
