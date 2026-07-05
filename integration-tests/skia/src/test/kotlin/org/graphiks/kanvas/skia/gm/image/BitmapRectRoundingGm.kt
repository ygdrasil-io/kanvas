package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmaprect.cpp::BitmapRectRounding`.
 * Probes drawImageRect precision under sub-pixel CTM scale.
 * @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
class BitmapRectRoundingGm : SkiaGm {
    override val name = "bitmaprect_rounding"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val pixels = ByteArray(10 * 10 * 4)
        for (i in pixels.indices step 4) {
            pixels[i] = 0x00.toByte()
            pixels[i + 1] = 0x00.toByte()
            pixels[i + 2] = 0xFF.toByte()
            pixels[i + 3] = 0xFF.toByte()
        }
        val image = Image.fromPixels(10, 10, pixels, ColorType.RGBA_8888, "bitmap")

        val r = Rect.fromXYWH(1f, 1f, 110f, 114f)
        canvas.scale(0.9f, 0.9f)

        canvas.drawRect(r, Paint(color = Color.RED))
        canvas.drawImageRect(
            image,
            Rect.fromXYWH(0f, 0f, 10f, 10f),
            r,
        )
    }
}
