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
    /** Requested OpenType design coordinates that produced this blob. */
    val variationCoordinates: Map<String, Float> = emptyMap(),
) {
    /** Conservative ink bounds computed from the available glyph outlines. */
    fun computeBounds(typeface: Typeface): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (run in glyphRuns) {
            for (i in run.positions.indices) {
                val pos = run.positions[i]
                val glyphId = run.glyphs[i].toInt()
                val pathBounds = typeface
                    .getGlyphPath(glyphId, run.fontSize, variationCoordinates)
                    ?.computeBounds()
                val font = Font(typeface, size = run.fontSize)
                val metrics = font.getMetrics()
                val ascent = metrics?.ascent ?: -run.fontSize * 0.8f
                val descent = metrics?.descent ?: run.fontSize * 0.2f
                val left = pathBounds?.left?.plus(pos.x) ?: pos.x
                val right = pathBounds?.right?.plus(pos.x) ?: pos.x + typeface.getAdvance(
                    glyphId,
                    run.fontSize,
                    variationCoordinates,
                )
                val top = pathBounds?.top?.plus(pos.y) ?: pos.y + ascent
                val bottom = pathBounds?.bottom?.plus(pos.y) ?: pos.y + descent
                if (left < minX) minX = left
                if (right > maxX) maxX = right
                if (top < minY) minY = top
                if (bottom > maxY) maxY = bottom
            }
        }
        return if (minX.isFinite()) Rect(minX, minY, maxX, maxY) else Rect.EMPTY
    }
}
