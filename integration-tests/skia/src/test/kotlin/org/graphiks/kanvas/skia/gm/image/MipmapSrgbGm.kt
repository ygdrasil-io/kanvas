package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/mipmap.cpp` — sRGB variant.
 * Tests mipmap rendering with a checkerboard image at decreasing scales.
 * @see https://github.com/google/skia/blob/main/gm/mipmap.cpp
 */
class MipmapSrgbGm : SkiaGm {
    override val name = "mipmap_srgb"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 260
    override val height = 230

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val limg = makeCheckerImage()
        val simg = makeCheckerImage()

        canvas.translate(10f, 10f)
        showMips(canvas, limg)
        canvas.translate(0f, limg.height.toFloat() + 10f)
        showMips(canvas, simg)
    }

    private fun makeCheckerImage(): Image {
        val n = 100
        val pixels = ByteArray(n * n * 4)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val i = (y * n + x) * 4
                val v = if (((x xor y) and 1) != 0) 0xFF else 0x00
                pixels[i] = v.toByte()
                pixels[i + 1] = v.toByte()
                pixels[i + 2] = v.toByte()
                pixels[i + 3] = 0xFF.toByte()
            }
        }
        return Image.fromPixels(n, n, pixels, ColorType.RGBA_8888)
    }

    private fun showMips(canvas: GmCanvas, img: Image) {
        var w = img.width.toFloat()
        var h = img.height.toFloat()
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
