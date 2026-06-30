package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point

data class KanvasGlyphRun(
    val glyphs: List<UShort>,
    val positions: List<Point>,
)

data class TextBlob(
    val glyphRuns: List<KanvasGlyphRun>,
    val typeface: KanvasTypeface? = null,
    val fontSize: Float = 12f,
)
