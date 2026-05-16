package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's [`gm/nearesthalfpixelimage.cpp`](https://github.com/google/skia/blob/main/gm/nearesthalfpixelimage.cpp)
 * `nearest_half_pixel_image` GM (264 × 235).
 *
 * Tests drawing 2-pixel-wide / -tall images at half-pixel offsets in
 * device space with `SkFilterMode.kNearest`. Both `drawImage` and
 * `drawRect` with an image shader are tested, with positive and
 * negative scale.
 */
public class NearestHalfPixelImageGM : GM() {

    override fun getName(): String = "nearest_half_pixel_image"
    override fun getISize(): SkISize = SkISize.Make(264, 235)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Build the four 2-px source images: RGBA 2×1 / 1×2 + Alpha 2×1 / 1×2.
        val redARGB = 0xFFFF0000.toInt()
        val blueARGB = 0xFF0000FF.toInt()

        val rgbaX = make2x1(redARGB, blueARGB)
        val rgbaY = make1x2(redARGB, blueARGB)
        val alphaX = makeAlpha2x1(0xFF.toByte(), 0xAA.toByte())
        val alphaY = makeAlpha1x2(0xFF.toByte(), 0xAA.toByte())

        data class ImagePair(val imageX: SkImage, val imageY: SkImage)
        val images = arrayOf(ImagePair(rgbaX, rgbaY), ImagePair(alphaX, alphaY))

        // Draw offscreen at 80×80, then blit-zoom 8x onto the main canvas.
        val surf = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(80, 80))
        val sc = surf.canvas
        sc.clear(SK_ColorWHITE)

        val kOffAxisScale = 4f

        fun draw(image: SkImage, shader: Boolean, doX: Boolean, mirror: Boolean, alpha: Int) {
            sc.save()
            try {
                val paint = SkPaint().apply { this.alpha = alpha }
                if (shader) {
                    paint.shader = image.makeShader(
                        SkSamplingOptions(SkFilterMode.kNearest),
                    )
                }
                if (doX) {
                    sc.scale(if (mirror) -1f else 1f, kOffAxisScale)
                    sc.translate(if (mirror) -2.5f else 0.5f, 0f)
                } else {
                    sc.scale(kOffAxisScale, if (mirror) -1f else 1f)
                    sc.translate(0f, if (mirror) -2.5f else 0.5f)
                }
                if (shader) {
                    sc.drawRect(
                        SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()),
                        paint,
                    )
                } else {
                    sc.drawImage(
                        image,
                        0f,
                        0f,
                        SkSamplingOptions(SkFilterMode.kNearest),
                        paint,
                    )
                }
            } finally {
                sc.restore()
            }
        }

        for (shader in arrayOf(false, true)) {
            for (alpha in intArrayOf(0xFF, 0x70)) {
                sc.save()
                for (i in images) {
                    for (mirror in arrayOf(false, true)) {
                        draw(i.imageX, shader, doX = true, mirror, alpha)
                        sc.save()
                        sc.translate(4f, 0f)
                        draw(i.imageY, shader, doX = false, mirror, alpha)
                        sc.restore()
                        sc.translate(0f, kOffAxisScale * 2f)
                    }
                }
                sc.restore()
                sc.translate(kOffAxisScale * 2f, 0f)
            }
        }

        c.scale(8f, 8f)
        c.drawImage(surf.makeImageSnapshot(), 0f, 0f)
    }

    private fun make2x1(c0: Int, c1: Int): SkImage {
        val bm = SkBitmap(2, 1)
        bm.setPixel(0, 0, c0)
        bm.setPixel(1, 0, c1)
        return bm.asImage()
    }

    private fun make1x2(c0: Int, c1: Int): SkImage {
        val bm = SkBitmap(1, 2)
        bm.setPixel(0, 0, c0)
        bm.setPixel(0, 1, c1)
        return bm.asImage()
    }

    private fun makeAlpha2x1(a0: Byte, a1: Byte): SkImage {
        val info = SkImageInfo.MakeA8(2, 1)
        val bm = SkBitmap.allocPixels(info)
        bm.setPixel(0, 0, SkColorSetARGB(a0.toInt() and 0xFF, 0, 0, 0))
        bm.setPixel(1, 0, SkColorSetARGB(a1.toInt() and 0xFF, 0, 0, 0))
        return bm.asImage()
    }

    private fun makeAlpha1x2(a0: Byte, a1: Byte): SkImage {
        val info = SkImageInfo.MakeA8(1, 2)
        val bm = SkBitmap.allocPixels(info)
        bm.setPixel(0, 0, SkColorSetARGB(a0.toInt() and 0xFF, 0, 0, 0))
        bm.setPixel(0, 1, SkColorSetARGB(a1.toInt() and 0xFF, 0, 0, 0))
        return bm.asImage()
    }
}
