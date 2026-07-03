package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point

/**
 * A contiguous run of glyphs from a single font, together with their
 * positioned [points][positions].
 *
 * @property glyphs    glyph identifier for every code-point in the run.
 * @property positions device-space position (x, y) for each glyph.
 */
data class KanvasGlyphRun(
    val glyphs: List<UShort>,
    val positions: List<Point>,
)

/**
 * The result of shaping a piece of text: one or more [glyph runs][KanvasGlyphRun]
 * and the typeface / size used to produce them.
 *
 * @property glyphRuns  ordered list of shaped glyph runs.
 * @property typeface   the [KanvasTypeface] the text was shaped with, if known.
 * @property fontSize   the font size in design-space units (default 12).
 */
data class TextBlob(
    val glyphRuns: List<KanvasGlyphRun>,
    val typeface: Typeface? = null,
    val fontSize: Float = 12f,
)
