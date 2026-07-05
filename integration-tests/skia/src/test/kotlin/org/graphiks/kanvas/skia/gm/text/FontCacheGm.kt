package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces

/**
 * Port of Skia's `gm/fontcache.cpp::FontCacheGM`.
 * Exercises glyph rendering with multiple sizes and subpixel offsets.
 * @see https://github.com/google/skia/blob/main/gm/fontcache.cpp
 */
class FontCacheGm : SkiaGm {
    override val name = "fontcache"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1280
    override val height = 1280

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val sizes = intArrayOf(8, 9, 10, 11, 12, 13, 18, 20, 25)
        val texts = arrayOf(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyz",
            "0123456789",
            "!@#\$%^&*()<>[]{}",
        )

        val kSize = 1280f
        val kSubPixelInc = 0.5f
        val paint = Paint()
        var x = 0f
        var y = 10f
        var subpixelX = 0f
        var subpixelY = 0f
        var offsetX = true

        while (true) {
            for (s in sizes) {
                val size = (2 * s).toFloat()
                val font = Font(typeface, size = size, subpixel = true)
                for (tfi in 0 until 6) {
                    for (text in texts) {
                        canvas.drawString(text, x + subpixelX, y + subpixelY, font, paint)
                        x = s.toFloat() + x + subpixelX + font.measureText(text)
                        x = kotlin.math.ceil(x)
                        if (x + 100f > kSize) {
                            x = 0f
                            y += kotlin.math.ceil(size + 3f)
                            if (y > kSize) return
                        }
                    }
                }
                if (offsetX) subpixelX += kSubPixelInc
                else subpixelY += kSubPixelInc
                offsetX = !offsetX
            }
        }
    }
}
