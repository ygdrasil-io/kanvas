package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/annotated_text.cpp` (512 × 512).
 * Draws "Click this link!" twice — once upright and once rotated 90°
 * inside a clipped area. SkAnnotateRectWithURL is a no-op on raster.
 * saveLayer is approximated with save/restore.
 * @see https://github.com/google/skia/blob/main/gm/annotated_text.cpp
 */
class AnnotatedTextGm : SkiaGm {
    override val name = "annotated_text"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 1f, g = 1f, b = 1f)
        canvas.save()
        canvas.clipRect(Rect.fromLTRB(64f, 64f, 320f, 320f))
        canvas.drawColor(r = 0xEE / 255f, g = 0xEE / 255f, b = 0xEE / 255f)

        val font = Font(typeface, size = 40f)
        val text = "Click this link!"
        drawAnnotatedText(canvas, text, 200f, 80f, font)

        canvas.save()
        canvas.rotate(90f)
        drawAnnotatedText(canvas, text, 150f, -55f, font)
        canvas.restore()
        canvas.restore()
    }

    private fun drawAnnotatedText(canvas: GmCanvas, text: String, x: Float, y: Float, font: Font) {
        val textWidth = font.measureText(text)
        val bounds = Rect.fromLTRB(x, y - font.size * 0.8f, x + textWidth, y + font.size * 0.2f)
        val shade = Paint(color = Color.fromRGBA(0x80 / 255f, 0x34 / 255f, 0x61 / 255f, 0x80 / 255f))
        canvas.drawRect(bounds, shade)
        canvas.drawString(text, x, y, font, Paint())
    }
}
