package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/imageblurrepeatmode.cpp::ImageBlurRepeatModeGM`
 * (name `imageblurrepeatmode`, 850 × 920).
 *
 * Three colourful banded images (X-only, Y-only, X+Y) are drawn through
 * Gaussian blurs at sigma `{0.6, 3.0, 8.0, 20.0}`. Each blur uses
 * `kRepeat` tile mode with the image's own bounds as the crop rect, so
 * samples outside the image wrap around. Each row corresponds to one
 * sigma value and each column to one of the three images.
 */
public class ImageBlurRepeatModeGM : GM() {

    init {
        setBGColor(0xFFCCCCCC.toInt())
    }

    override fun getName(): String = "imageblurrepeatmode"
    override fun getISize(): SkISize = SkISize.Make(850, 920)

    private lateinit var fImages: Array<SkImage>

    override fun onOnceBeforeDraw() {
        fImages = arrayOf(makeImage(1), makeImage(2), makeImage(3))
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = fImages
        c.translate(0f, 30f)
        for (sigma in SIGMAS) {
            c.save()

            var filter: SkImageFilter? = SkImageFilters.Blur(
                sigma, 0f, SkTileMode.kRepeat, null, image[0].iBounds(),
            )
            drawImage(c, image[0], filter)
            c.translate(image[0].width.toFloat() + 20f, 0f)

            filter = SkImageFilters.Blur(
                0f, sigma, SkTileMode.kRepeat, null, image[1].iBounds(),
            )
            drawImage(c, image[1], filter)
            c.translate(image[1].width.toFloat() + 20f, 0f)

            filter = SkImageFilters.Blur(
                sigma, sigma, SkTileMode.kRepeat, null, image[2].iBounds(),
            )
            drawImage(c, image[2], filter)
            c.translate(image[2].width.toFloat() + 20f, 0f)

            c.restore()
            c.translate(0f, image[0].height.toFloat() + 20f)
        }
    }

    /**
     * Mirrors C++ `draw_image(canvas, image, filter)` — saves the
     * canvas, translates by `(30, 0)`, clips to the image bounds,
     * draws the image with the supplied [filter] applied via paint,
     * then restores.
     */
    private fun drawImage(canvas: SkCanvas, image: SkImage, filter: SkImageFilter?) {
        val sc = canvas.save()
        val paint = SkPaint().apply { imageFilter = filter }
        canvas.translate(30f, 0f)
        canvas.clipRect(SkRect.Make(image.iBounds()))
        canvas.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restoreToCount(sc)
    }

    /**
     * Mirrors C++ `make_image(canvas, direction)` — paints a 250 × 200
     * banded image. `direction & 1` flips on the X bars ; `direction & 2`
     * flips on the Y bars. When both bits are set, every bar is
     * 50 % alpha so the X/Y stripes blend visually.
     */
    private fun makeImage(direction: Int): SkImage {
        val info = SkImageInfo.MakeN32Premul(250, 200)
        val surface = SkSurface.MakeRaster(info)
        val canvas = surface.canvas
        val paint = SkPaint().apply { isAntiAlias = true }

        val colors = intArrayOf(
            SK_ColorRED, SK_ColorBLUE, SK_ColorGREEN, SK_ColorYELLOW, SK_ColorBLACK,
        )
        val width = 25
        val xDir = (direction and 0x1) == 1
        val yDir = (direction and 0x2) == 2

        if (xDir) {
            var x = 0
            while (x < info.width) {
                paint.color = colors[(x / width) % 5]
                if (yDir) paint.alphaf = 0.5f
                canvas.drawRect(SkRect.MakeXYWH(x.toFloat(), 0f, width.toFloat(), info.height.toFloat()), paint)
                x += width
            }
        }
        if (yDir) {
            paint.alphaf = 1.0f
            var y = 0
            while (y < info.height) {
                paint.color = colors[(y / width) % 5]
                if (xDir) paint.alphaf = 0.5f
                canvas.drawRect(SkRect.MakeXYWH(0f, y.toFloat(), info.width.toFloat(), width.toFloat()), paint)
                y += width
            }
        }
        return surface.makeImageSnapshot()
    }

    private fun SkImage.iBounds(): SkIRect = SkIRect.MakeWH(width, height)

    private companion object {
        private val SIGMAS: FloatArray = floatArrayOf(0.6f, 3.0f, 8.0f, 20.0f)
    }
}
