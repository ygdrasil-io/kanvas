package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point

/**
 * Bridge from the font module's shaping output to Kanvas public API types.
 *
 * Converts [ShapedGlyphRun] (from :font) into [KanvasGlyphRun] by computing
 * per-glyph positions from cluster advances and casting glyph IDs to UShort.
 */
object TextBridge {
    /**
     * Convert a shaped glyph run to a Kanvas glyph run.
     *
     * @param run the shaped run from the font module
     * @param startX initial X offset for the first glyph
     * @param startY initial Y offset for the first glyph
     */
    fun toKanvasGlyphRun(run: ShapedGlyphRun, startX: Float = 0f, startY: Float = 0f): KanvasGlyphRun {
        val glyphs = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()

        var cursorX = startX
        var cursorY = startY

        for (cluster in run.clusters) {
            val glyphCount = cluster.glyphRange.last - cluster.glyphRange.first + 1
            for (i in 0 until glyphCount) {
                val gid = run.glyphIds.getOrNull(cluster.glyphRange.first + i) ?: 0
                glyphs.add(gid.toUShort())
                positions.add(Point(cursorX + cluster.offsetX + cluster.advanceX * i, cursorY + cluster.offsetY))
            }
            cursorX += cluster.advanceX
        }

        return KanvasGlyphRun(glyphs, positions)
    }

    /**
     * Convert a full shaping result into a [TextBlob].
     * Each [ShapedGlyphRun] becomes a [KanvasGlyphRun] in the blob.
     */
    fun toTextBlob(result: ShapingResult, typeface: KanvasTypeface? = null): TextBlob {
        val fontSize = result.glyphRuns.firstOrNull()?.fontSize ?: 12f
        val runs = result.glyphRuns.map { toKanvasGlyphRun(it) }
        return TextBlob(glyphRuns = runs, typeface = typeface, fontSize = fontSize)
    }
}

/**
 * Minimal shaped glyph run type mirroring the font module's ShapedGlyphRun.
 * The real import would be `org.graphiks.kanvas.text.shaping.ShapedGlyphRun`
 * from :font — this is a bridge-compatible subset.
 */
data class ShapedGlyphRun(
    val glyphIds: List<Int>,
    val clusters: List<GlyphCluster>,
    val advanceX: Float = 0f,
    val advanceY: Float = 0f,
    val fontSize: Float = 12f,
)

data class GlyphCluster(
    val textRange: IntRange,
    val glyphRange: IntRange,
    val advanceX: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

data class ShapingResult(
    val glyphRuns: List<ShapedGlyphRun> = emptyList(),
)
