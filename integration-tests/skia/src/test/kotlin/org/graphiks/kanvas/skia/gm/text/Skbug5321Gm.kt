package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Point

class Skbug5321Gm : SkiaGm {
    override val name = "skbug_5321"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 30f, antiAlias = false)
        val text = "x\u0300y"
        var x = 20f
        var y = 45f

        canvas.drawString(text, x, y, font, Paint())

        val metrics = font.getMetrics()
        val lineSpacing = if (metrics != null) metrics.descent - metrics.ascent + metrics.leading else 36f
        y += lineSpacing

        val codepoints = text.codePoints().toArray()
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = x
        for (cp in codepoints) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(cursorX, y))
            cursorX += typeface.getAdvance(gid, 30f)
        }

        val blob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions, fontSize = 30f)),
            typeface = typeface,
            fontSize = 30f,
        )
        canvas.drawTextBlob(blob, 0f, 0f, Paint())
    }
}
