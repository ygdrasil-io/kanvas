package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/texteffects.cpp::textblob_intercepts` (940 × 800).
 * Renders "Hyjay {worlp}." in three positioning modes and draws a thick
 * horizontal underline with gaps at glyph descender crossings.
 * getIntercepts is approximated via bbox-based gap computation.
 * @see https://github.com/google/skia/blob/main/gm/texteffects.cpp
 */
class TextBlobInterceptsGm : SkiaGm {
    override val name = "textblob_intercepts"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 85.0
    override val width = 940
    override val height = 800

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "Hyjay {worlp}."
        val font = Font(typeface, size = 100f)

        val glyphIds = mutableListOf<UShort>()
        for (cp in text.codePoints()) {
            glyphIds.add(typeface.glyphIdForCodepoint(cp).toUShort())
        }

        val b0 = makeTextBlob(font, glyphIds)

        canvas.translate(20f, 120f)
        drawBlobAdorned(canvas, b0, font)

        for (spacing in floatArrayOf(0f, 20f)) {
            val b1 = makePosHBlob(font, glyphIds, spacing)
            val b2 = makePosBlob(font, glyphIds, spacing)
            canvas.translate(0f, 150f)
            drawBlobAdorned(canvas, b1, font)
            canvas.translate(0f, 150f)
            drawBlobAdorned(canvas, b2, font)
        }
    }

    private fun makeTextBlob(font: Font, glyphIds: List<UShort>): TextBlob {
        val positions = mutableListOf<Point>()
        var x = 0f
        for (gid in glyphIds) {
            positions.add(Point(x, 0f))
            x += typeface.getAdvance(gid.toInt(), font.size)
        }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
            typeface = typeface,
            fontSize = font.size,
        )
    }

    private fun makePosHBlob(font: Font, glyphIds: List<UShort>, spacing: Float): TextBlob {
        val xpos = mutableListOf<Float>()
        var x = 0f
        for (gid in glyphIds) {
            xpos.add(x)
            x += typeface.getAdvance(gid.toInt(), font.size)
        }
        for (i in 1 until glyphIds.size) {
            xpos[i] = xpos[i] + spacing * i
        }
        val positions = xpos.map { Point(it, 0f) }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
            typeface = typeface,
            fontSize = font.size,
        )
    }

    private fun makePosBlob(font: Font, glyphIds: List<UShort>, spacing: Float): TextBlob {
        val positions = mutableListOf<Point>()
        var x = 0f
        for (gid in glyphIds) {
            positions.add(Point(x, 0f))
            x += typeface.getAdvance(gid.toInt(), font.size)
        }
        for (i in 1 until glyphIds.size) {
            positions[i] = Point(positions[i].x + spacing * i, 0f)
        }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
            typeface = typeface,
            fontSize = font.size,
        )
    }

    private fun drawBlobAdorned(canvas: GmCanvas, blob: TextBlob, font: Font) {
        val paint = Paint()
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        val ymin = 8f
        val ymax = 16f
        val blobBounds = computeGlyphBounds(blob)
        if (blobBounds == null) return

        val halfH = (ymax - ymin) / 2f
        val halo = halfH * 1.5f

        // Compute gaps where glyphs intersect the band [ymin, ymax]
        val gaps = mutableListOf<Pair<Float, Float>>()
        for (run in blob.glyphRuns) {
            for (pos in run.positions) {
                val glyphLeft = pos.x
                val glyphRight = pos.x + font.size * 0.5f
                // Check if glyph overlaps the band vertically
                if (pos.y - font.size * 1.2f <= ymax && pos.y >= 0f) {
                    // Glyph overlaps — create a gap
                    val gapStart = glyphLeft - halo
                    val gapEnd = glyphRight + halo
                    gaps.add(Pair(gapStart, gapEnd))
                }
            }
        }

        val yCenter = (ymin + ymax) / 2f
        val endX = 900f

        // Merge overlapping gaps
        gaps.sortBy { it.first }
        val merged = mutableListOf<Pair<Float, Float>>()
        for (gap in gaps) {
            if (merged.isEmpty()) {
                merged.add(gap)
            } else {
                val last = merged.last()
                if (gap.first <= last.second) {
                    merged[merged.size - 1] = Pair(last.first, maxOf(last.second, gap.second))
                } else {
                    merged.add(gap)
                }
            }
        }

        // Draw underline with gaps using drawLine segments
        val linePaint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = ymax - ymin,
            antiAlias = true,
        )

        // Draw segments between gaps
        if (merged.isEmpty()) {
            canvas.drawLine(0f, yCenter, endX, yCenter, linePaint)
        } else {
            // Before first gap
            if (merged[0].first > 0f) {
                canvas.drawLine(0f, yCenter, merged[0].first, yCenter, linePaint)
            }
            // Between gaps
            for (i in 0 until merged.size - 1) {
                canvas.drawLine(merged[i].second, yCenter, merged[i + 1].first, yCenter, linePaint)
            }
            // After last gap
            if (merged.last().second < endX) {
                canvas.drawLine(merged.last().second, yCenter, endX, yCenter, linePaint)
            }
        }
    }

    private fun computeGlyphBounds(blob: TextBlob): Rect? {
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var hasGlyphs = false
        for (run in blob.glyphRuns) {
            for (pos in run.positions) {
                minX = minOf(minX, pos.x)
                maxX = maxOf(maxX, pos.x)
                hasGlyphs = true
            }
        }
        if (!hasGlyphs) return null
        return Rect(minX, 0f, maxX, blob.fontSize * 1.2f)
    }
}
