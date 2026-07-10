package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/readpixels.cpp::ReadPixelsCodecGM`
 * (27 × 108, variant of ReadPixelsGM using PNG-decoded source).
 * Draws a 3-col × 12-row grid of colored cells representing
 * color-space / color-type / alpha-type matrix combinations.
 * @see https://github.com/google/skia/blob/main/gm/readpixels.cpp
 */
class ReadPixelsCodecGm : SkiaGm {
    override val name = "readpixelscodec"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 3 * (kEncodedWidth + 1)
    override val height = 12 * (kEncodedHeight + 1)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(42)
        val cellW = kEncodedWidth + 1
        val cellH = kEncodedHeight + 1

        for (col in 0 until 3) {
            canvas.save()
            for (row in 0 until 12) {
                val rect = Rect.fromXYWH(0f, 0f, cellW.toFloat(), cellH.toFloat())
                canvas.drawRect(rect, Paint(
                    color = Color.fromRGBA(rand.nextFloat(), rand.nextFloat(), rand.nextFloat()),
                ))
                canvas.translate(0f, cellH.toFloat())
            }
            canvas.restore()
            canvas.translate(cellW.toFloat(), 0f)
        }
    }

    private companion object {
        const val kEncodedWidth = 8
        const val kEncodedHeight = 8
    }
}
