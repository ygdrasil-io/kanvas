package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Point

/** Tests typeface styles with kerning — draws text blobs with various
 *  font styles to verify kerning behaviour across typeface variants.
 */
class TypefaceStylesKerningGm : SkiaGm {
    override val name = "typefacestyles_kerning"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 30f)
        val text = "Type AWAY"
        val x = 10f
        val metrics = font.getMetrics()
        val dy = if (metrics != null) metrics.descent - metrics.ascent + metrics.leading else 36f
        var y = dy

        val paint = Paint()
        for (i in 0 until 4) {
            canvas.drawString(text, x, y, font, paint)
            val kernGlyphs = buildKernText(text, x + 240f, y, font)
            canvas.drawTextBlob(kernGlyphs, 0f, 0f, paint)
            y += dy
        }
    }

    private fun buildKernText(text: String, x: Float, y: Float, font: Font): TextBlob {
        val codepoints = text.codePoints().toArray()
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = x
        for (cp in codepoints) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(cursorX, y))
            cursorX += typeface.getAdvance(gid, font.size)
        }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions, fontSize = font.size)),
            typeface = typeface,
            fontSize = font.size,
        )
    }
}
