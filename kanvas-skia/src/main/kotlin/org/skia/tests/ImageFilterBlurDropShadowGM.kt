package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Bespoke validation GM for [SkImageFilters.Blur] / DropShadow /
 * MatrixTransform — Phase 7d.2.
 *
 * 4 cells :
 *  1. Raw source.
 *  2. Blur(sigma=2).
 *  3. DropShadow(dx=8, dy=8, sigma=3, black 0x80).
 *  4. MatrixTransform(scale 1.5, kLinear).
 */
public class ImageFilterBlurDropShadowGM : GM() {

    override fun getName(): String = "image_filter_blur_drop_shadow"
    override fun getISize(): SkISize = SkISize.Make(360, 100)

    private val sourceImage: SkImage = run {
        val bm = SkBitmap(64, 64).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 56f, 56f), SkPaint(SK_ColorRED))
        canvas.drawRect(SkRect.MakeLTRB(20f, 28f, 44f, 36f), SkPaint(SK_ColorBLACK))
        bm.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sampling = SkSamplingOptions.Default

        // Cell 1 : raw.
        c.drawImage(sourceImage, 10f, 10f, sampling, null)

        // Cell 2 : Blur.
        val blurPaint = SkPaint().apply {
            imageFilter = SkImageFilters.Blur(2f)
        }
        c.drawImage(sourceImage, 100f, 10f, sampling, blurPaint)

        // Cell 3 : DropShadow.
        val shadowPaint = SkPaint().apply {
            imageFilter = SkImageFilters.DropShadow(
                dx = 8f, dy = 8f,
                sigmaX = 3f, sigmaY = 3f,
                color = SkColorSetARGB(0x80, 0, 0, 0),
            )
        }
        c.drawImage(sourceImage, 190f, 10f, sampling, shadowPaint)

        // Cell 4 : MatrixTransform scale 1.5.
        val scalePaint = SkPaint().apply {
            imageFilter = SkImageFilters.MatrixTransform(
                matrix = SkMatrix.MakeScale(1.5f, 1.5f),
                sampling = SkSamplingOptions.Default,
            )
        }
        c.drawImage(sourceImage, 280f, 10f, sampling, scalePaint)
    }
}
