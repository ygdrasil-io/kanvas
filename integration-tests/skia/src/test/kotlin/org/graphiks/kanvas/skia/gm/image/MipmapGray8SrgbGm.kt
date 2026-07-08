package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/mipmap.cpp` — gray8 sRGB variant.
 * Tests mipmap rendering with a gray8 gradient image at decreasing scales.
 * @see https://github.com/google/skia/blob/main/gm/mipmap.cpp
 */
class MipmapGray8SrgbGm : SkiaGm {
    override val name = "mipmap_gray8_srgb"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 260
    override val height = 230

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val limg = makeG8Gradient()
        val simg = makeG8Gradient()

        canvas.translate(10f, 10f)
        showMipsOnly(canvas, limg)
        canvas.translate(0f, limg.height.toFloat() / 2f + 10f)
        showMipsOnly(canvas, simg)
    }

    private fun makeG8Gradient(): Image {
        val n = 100
        val pixels = ByteArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                pixels[y * n + x] = (255.0f * ((x + y) / (2.0f * (n - 1)))).toInt().toByte()
            }
        }
        return Image.fromPixels(n, n, pixels, ColorType.GRAY_8)
    }

    private fun showMipsOnly(canvas: GmCanvas, img: Image) {
        var w = img.width.toFloat() / 2f
        var h = img.height.toFloat() / 2f
        var x = 0f
        while (w > 5f) {
            val dst = Rect.fromXYWH(x, 0f, w, h)
            canvas.drawImageRect(img, /*src=*/ dst, /*dst=*/ dst)
            x += w + 10f
            w /= 2f
            h /= 2f
        }
    }
}
