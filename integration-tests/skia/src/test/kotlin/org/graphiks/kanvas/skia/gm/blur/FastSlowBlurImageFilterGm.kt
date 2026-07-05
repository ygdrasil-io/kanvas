package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefilters.cpp` (fast_slow_blurimagefilter).
 * Compares blur with tight clip (fast path) vs looser clip (slower path).
 * @see https://github.com/google/skia/blob/main/gm/imagefilters.cpp
 */
class FastSlowBlurImageFilterGm : SkiaGm {
    override val name = "fast_slow_blurimagefilter"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 620
    override val height = 260

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeImage()
        val r = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())

        canvas.translate(10f, 10f)
        var sigma = 8f
        while (sigma <= 128f) {
            val paint = Paint(imageFilter = ImageFilter.Blur(sigma, sigma))

            canvas.save()
            for (outset in 0..1) {
                canvas.save()
                val clipRect = Rect(
                    r.left - outset, r.top - outset,
                    r.right + outset, r.bottom + outset,
                )
                canvas.clipRect(clipRect)
                canvas.drawImage(image, r, paint)
                canvas.restore()
                canvas.translate(0f, r.height + 20f)
            }
            canvas.restore()
            canvas.translate(r.width + 20f, 0f)

            sigma *= 2f
        }
    }

    private fun makeImage(): Image {
        val surface = Surface(100, 100)
        surface.canvas {
            drawRect(Rect(25f, 25f, 75f, 75f), Paint(color = Color.BLACK))
        }
        return surface.makeImageSnapshot()
    }
}
