package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurs.cpp` (DEF_SIMPLE_GM BlurSmallSigma).
 * Tests image-filter blur identity guards at very small sigmas.
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class BlurSmallSigmaGm : SkiaGm {
    override val name = "BlurSmallSigma"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val leftPaint = Paint(
            imageFilter = ImageFilter.Blur(sigmaX = 16f, sigmaY = 1e-5f),
        )
        canvas.drawRect(Rect.fromLTRB(64f, 64f, 192f, 192f), leftPaint)

        val redPaint = Paint(color = Color.RED)
        val rect = Rect.fromLTRB(320f, 64f, 448f, 192f)
        canvas.drawRect(rect, redPaint)

        val blackPaint = Paint(
            color = Color.BLACK,
            imageFilter = ImageFilter.Blur(sigmaX = 1e-5f, sigmaY = 1e-5f),
        )
        canvas.drawRect(rect, blackPaint)
    }
}
