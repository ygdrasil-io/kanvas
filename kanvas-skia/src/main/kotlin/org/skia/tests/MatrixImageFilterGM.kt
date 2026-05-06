package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/matriximagefilter.cpp::matriximagefilter`
 * (420 × 100).
 *
 * Draws a 64×64 dark/light checkerboard inside two side-by-side
 * `saveLayer(bounds, paint{imageFilter=MatrixTransform(skew(0.5, 0.2),
 * sampling)})` panels — one with default (nearest) sampling, one with
 * `kLinear`. Validates the Phase 7d.2 `SkImageFilters::MatrixTransform`
 * affine pipeline.
 */
public class MatrixImageFilterGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "matriximagefilter"
    override fun getISize(): SkISize = SkISize.Make(420, 100)

    private fun makeCheckerboard(): SkBitmap {
        val bm = SkBitmap(64, 64).also { it.eraseColor(0) }
        val canvas = SkCanvas(bm)
        val dark = SkPaint().apply { color = 0xFF404040.toInt() }
        val light = SkPaint().apply { color = 0xFFA0A0A0.toInt() }
        var y = 0
        while (y < 64) {
            var x = 0
            while (x < 64) {
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                canvas.drawRect(SkRect.MakeXYWH(0f, 0f, 16f, 16f), dark)
                canvas.drawRect(SkRect.MakeXYWH(16f, 0f, 16f, 16f), light)
                canvas.drawRect(SkRect.MakeXYWH(0f, 16f, 16f, 16f), light)
                canvas.drawRect(SkRect.MakeXYWH(16f, 16f, 16f, 16f), dark)
                canvas.restore()
                x += 32
            }
            y += 32
        }
        return bm
    }

    private fun draw(c: SkCanvas, rect: SkRect, bm: SkBitmap, m: SkMatrix, sampling: SkSamplingOptions) {
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.MatrixTransform(m, sampling, null)
        }
        c.saveLayer(rect, paint)
        c.drawImage(bm.asImage(), 0f, 0f, sampling)
        c.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val matrix = SkMatrix.MakeSkew(0.5f, 0.2f)
        val checker = makeCheckerboard()
        val srcRect = SkRect.MakeWH(96f, 96f)
        val margin = 10f

        c.translate(margin, margin)
        draw(c, srcRect, checker, matrix, SkSamplingOptions.Default)

        c.translate(srcRect.width() + margin, 0f)
        draw(c, srcRect, checker, matrix, SkSamplingOptions(SkFilterMode.kLinear))
    }
}
