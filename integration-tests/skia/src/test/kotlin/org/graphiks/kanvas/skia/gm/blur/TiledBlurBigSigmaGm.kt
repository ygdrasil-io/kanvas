package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM(TiledBlurBigSigma, …)`.
 * Regression for crbug.com/1500021 — tiled blur with large sigma (206).
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class TiledBlurBigSigmaGm : SkiaGm {
    override val name = "TiledBlurBigSigma"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 768

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kTileWidth = 342
        val kTileHeight = 256

        for (y in 0 until 3) {
            for (x in 0 until 3) {
                canvas.save()

                canvas.clipRect(Rect(
                    (x * kTileWidth).toFloat(),
                    (y * kTileHeight).toFloat(),
                    ((x + 1) * kTileWidth).toFloat(),
                    ((y + 1) * kTileHeight).toFloat(),
                ))

                val inner = ImageFilter.ColorFilter(ColorFilter.Blend(Color.BLACK, BlendMode.SRC), null)
                val blur = ImageFilter.Blur(206f, 206f, tileMode = TileMode.CLAMP, input = inner)

                val p = Paint(imageFilter = blur)

                canvas.clipRect(Rect(0f, 0f, 1970f, 1223f))
                canvas.saveLayer(null, p)

                val fill = Paint(color = Color.BLUE)
                canvas.drawCircle(600f, 150f, 350f, fill)

                canvas.restore()
                canvas.restore()
            }
        }
    }
}
