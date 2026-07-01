package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurs.cpp::BlurBigSigma` (1024 x 1024).
 * Draws a large rect with image-filter blur sigma=500 on both axes.
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class BlurBigSigmaGm : SkiaGm {
    override val name = "BlurBigSigma"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            imageFilter = ImageFilter.Blur(500f, 500f),
        )
        canvas.drawRect(Rect(0f, 0f, 700f, 800f), paint)
    }
}
