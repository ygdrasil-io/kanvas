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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import kotlin.random.Random

/**
 * Port of Skia's `gm/getpostextpath.cpp` (DEF_SIMPLE_GM, 480 × 780).
 * Draws "Ham bur ge fons" as filled text and as red stroked outlines
 * reconstructed from per-glyph paths, both with default positioning
 * and with random y-jitter per glyph.
 * @see https://github.com/google/skia/blob/main/gm/getpostextpath.cpp
 */
class GetPosTextPathGm : SkiaGm {
    override val name = "getpostextpath"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 90.0
    override val width = 480
    override val height = 780

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "Ham bur ge fons"
        val font = Font(typeface, size = 48f)
        canvas.translate(10f, 64f)

        val paint = Paint(antiAlias = true)

        canvas.drawSimpleText(text, 0f, 0f, font, paint)
        val path = getTextPath(font, text, positions = null)
        strokePath(canvas, path)

        val glyphs = textToGlyphs(font, text)
        val count = glyphs.size
        val widths = FloatArray(count) { i -> typeface.getAdvance(glyphs[i], font.size) }
        val pos = Array(count) { Point.ZERO }

        val rng = Random(42)
        var cx = 20f
        val cy = 100f
        for (i in 0 until count) {
            pos[i] = Point(cx, cy + rng.nextFloat() * 24f - 12f)
            cx += widths[i]
        }

        canvas.translate(0f, 64f)

        val blob = run {
            val glyphIds = mutableListOf<UShort>()
            val positions = mutableListOf<Point>()
            for (i in 0 until count) {
                glyphIds.add(glyphs[i].toUShort())
                positions.add(pos[i])
            }
            TextBlob(
                glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
                typeface = typeface,
                fontSize = font.size,
            )
        }
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        val pathPos = getTextPath(font, text, positions = pos)
        strokePath(canvas, pathPos)
    }

    private fun strokePath(canvas: GmCanvas, path: Path) {
        canvas.drawPath(
            path,
            Paint(antiAlias = true, color = Color.RED, style = PaintStyle.STROKE),
        )
    }

    private fun getTextPath(font: Font, text: String, positions: Array<Point>?): Path {
        val path = Path { }
        var advance = 0f
        for (i in text.indices) {
            val cp = text[i].code
            val gid = typeface.glyphIdForCodepoint(cp)
            if (gid == 0) continue
            val glyphPath = typeface.getGlyphPath(gid, font.size)
            if (glyphPath != null) {
                val dx: Float
                val dy: Float
                if (positions != null) {
                    dx = positions[i].x
                    dy = positions[i].y
                } else {
                    dx = advance
                    dy = 0f
                }
                path.addPath(glyphPath.transform(Matrix33.translate(dx, dy)))
            }
            if (positions == null) {
                advance += typeface.getAdvance(gid, font.size)
            }
        }
        return path
    }

    private fun textToGlyphs(font: Font, text: String): List<Int> {
        val glyphs = mutableListOf<Int>()
        for (cp in text.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphs.add(gid)
        }
        return glyphs
    }
}
