package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

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
    val fontSize: Float = 12f,
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
) {
    /**
     * Approximate bounding box computed from glyph positions and font metrics.
     * Uses font-level ascent/descent as a proxy for per-glyph ink bounds,
     * matching the approach Skia uses in [TextBlob::bounds].
     */
    fun computeBounds(typeface: Typeface): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val font = Font(typeface, size = fontSize)
        val metrics = font.getMetrics()
        val ascent = metrics?.ascent ?: fontSize * 0.8f
        val descent = metrics?.descent ?: -(fontSize * 0.2f)
        for (run in glyphRuns) {
            for (i in run.positions.indices) {
                val pos = run.positions[i]
                val advance = typeface.getAdvance(run.glyphs[i].toInt(), fontSize)
                val top = pos.y - ascent
                val bottom = pos.y - descent
                val left = pos.x
                val right = pos.x + advance
                if (left < minX) minX = left
                if (right > maxX) maxX = right
                if (top < minY) minY = top
                if (bottom > maxY) maxY = bottom
            }
        }
        return Rect(minX, minY, maxX, maxY)
    }
}
