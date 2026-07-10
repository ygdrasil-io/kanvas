package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/texteffects.cpp::fancyblobunderline`
 * (DEF_SIMPLE_GM, 1480 × 1380).
 * Stress-tests TextBlob underline rendering across three font families
 * (sans-serif, serif, monospace) and five sizes (100–20).
 * Each blob has three runs (allocRun, allocRunPosH, allocRunPos)
 * over the same source string, then a stroked underline is drawn.
 * @see https://github.com/google/skia/blob/main/gm/texteffects.cpp
 */
class TextEffectsGm : SkiaGm {
    override val name = "fancyblobunderline"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 85.0
    override val width = 1480
    override val height = 1380

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)
        val test = "aAjJgGyY_|{-(~[,]qQ}pP}zZ"
        val blobOffsetX = 10f
        val blobOffsetY = 80f

        var canvasY = 0f
        for (familyIdx in 0 until 3) {
            for (textSize in floatArrayOf(100f, 80f, 60f, 40f, 20f)) {
                val font = Font(typeface, size = textSize)
                val uWidth = textSize / 15f
                val blobPaint = paint.copy()

                val blob = makeFancyBlob(font, test)
                canvas.drawTextBlob(blob, blobOffsetX, blobOffsetY + canvasY, blobPaint)

                val uPos = uWidth
                val start = blobOffsetX
                val end = blobOffsetX + font.measureText(test)
                val underline = Path {
                    moveTo(start, uPos)
                    lineTo(end, uPos)
                }
                canvas.drawPath(
                    underline,
                    paint.copy(style = PaintStyle.STROKE, strokeWidth = uWidth),
                )

                canvasY += textSize * 1.3f
            }
            canvasY += 60f
        }
    }

    private fun makeFancyBlob(font: Font, text: String): TextBlob {
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        for (cp in text.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point.ZERO)
        }

        val glyphCount = glyphIds.size
        val widths = FloatArray(glyphCount) { i ->
            typeface.getAdvance(glyphIds[i].toInt(), font.size)
        }

        val allGlyphIds = mutableListOf<UShort>()
        val allPositions = mutableListOf<Point>()
        var glyphIndex = 0
        var advance = 0f

        val defaultRunLen = glyphCount / 3
        for (i in 0 until defaultRunLen) {
            allGlyphIds.add(glyphIds[glyphIndex + i])
            allPositions.add(Point(advance, 0f))
        }
        for (i in 0 until defaultRunLen) {
            advance += widths[glyphIndex++]
        }

        val horizontalRunLen = glyphCount / 3
        for (i in 0 until horizontalRunLen) {
            allGlyphIds.add(glyphIds[glyphIndex + i])
            allPositions.add(Point(advance, 0f))
        }
        for (i in 0 until horizontalRunLen) {
            advance += widths[glyphIndex++]
        }

        val fullRunLen = glyphCount - glyphIndex
        for (i in 0 until fullRunLen) {
            allGlyphIds.add(glyphIds[glyphIndex + i])
            allPositions.add(Point(advance, 0f))
        }

        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(allGlyphIds, allPositions)),
            typeface = typeface,
            fontSize = font.size,
        )
    }
}
