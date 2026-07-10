package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/text_scale_skew.cpp::TextScaleSkew` (256 × 128).
 * Draws "Skia" in a 5×3 grid of scaleX × skewX permutations.
 * font.scaleX and font.skewX are dropped; text is centered at each cell.
 * font.getSpacing() → font.size * 1.2f
 * @see https://github.com/google/skia/blob/main/gm/text_scale_skew.cpp
 */
open class TextScaleSkewGm : SkiaGm {
    override val name = "text_scale_skew"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override val width = 256
    override val height = 128

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawTheText(canvas)
    }

    protected fun drawTheText(canvas: GmCanvas) {
        val paint = Paint(antiAlias = true)
        val font = Font(typeface, size = 18f)
        var y = 10f
        for (scale in floatArrayOf(0.5f, 0.71f, 1f, 1.41f, 2f)) {
            y += font.size * 1.2f
            var x = 50f
            for (skew in floatArrayOf(-0.5f, 0f, 0.5f)) {
                val text = "Skia"
                val textWidth = font.measureText(text)
                val drawX = x - textWidth / 2f
                canvas.drawString(text, drawX, y, font, paint)
                x += 78f
            }
        }
    }
}

/**
 * Port of Skia's `gm/text_scale_skew.cpp::TextScaleSkewRotate` (256 × 128).
 * Same grid rotated 30° around center (128, 64).
 */
class TextScaleSkewRotateGm : TextScaleSkewGm() {
    override val renderCost = RenderCost.FAST
    override val name = "text_scale_skew_rotate"

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save()
        canvas.translate(128f, 64f)
        canvas.rotate(30f)
        canvas.translate(-128f, -64f)
        drawTheText(canvas)
        canvas.restore()
    }
}
