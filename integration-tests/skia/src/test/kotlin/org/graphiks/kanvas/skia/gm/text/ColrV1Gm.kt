package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.EmojiTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/colrv1.cpp` — default `gradient_stops_repeat` category.
 * Iterates 4 codepoints at 4 text sizes (12, 18, 30, 120 pt) in
 * 4 colours (black, green, red, blue) with line-breaking.
 * @see https://github.com/google/skia/blob/main/gm/colrv1.cpp
 */
class ColrV1Gm : SkiaGm {
    override val name = "colrv1_gradient_stops_repeat"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    private val codepoints = intArrayOf(0xf0100, 0xf0101, 0xf0102, 0xf0103)
    private val textSizes = floatArrayOf(12f, 18f, 30f, 120f)
    private val colors = listOf(Color.BLACK, Color.GREEN, Color.RED, Color.BLUE)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.translate(200f, 20f)

        val tf = EmojiTypeface.createOrFallback(EmojiTypeface.Format.COLRv0, ByteArray(0))
        val font = Font(tf, antiAlias = false, subpixel = true)

        var colorIdx = 0
        var y = 0f

        for (textSize in textSizes) {
            val f = font.copy(size = textSize)
            val metrics = f.getMetrics()
            val yShift = if (metrics != null) {
                -(metrics.ascent + metrics.descent + metrics.leading) * 1.2f
            } else {
                textSize * 1.5f
            }
            y += yShift
            val paint = Paint(color = colors[colorIdx])
            var x = 0f

            for (cp in codepoints) {
                val cpStr = String(Character.toChars(cp))
                val glyphAdvance = f.measureText(cpStr)
                if (0f < x && 1000f < x + glyphAdvance) {
                    y += yShift
                    x = 0f
                }
                canvas.drawSimpleText(cpStr, x, y, f, paint)
                x += glyphAdvance * 1.05f
            }
            colorIdx++
        }
    }
}
