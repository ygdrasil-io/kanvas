package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColorChannel
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/imagefilterscropexpand.cpp::DEF_SIMPLE_GM(
 * imagefilterscropexpand, canvas, 730, 650)`.
 *
 * Walks a 9-filter row over 8 rows where the row's crop rect is the
 * original crop rect (`10, 10, 44, 44`) outset by `{-15, -10, -5, 0,
 * 5, 10, 15, 20}` pixels. Each filter shares a common no-op-cropped
 * input filter, ensuring the row's crop drives the filter's effective
 * bounds.
 *
 * **Adaptations** — `:kanvas-skia`'s [SkImageFilters] doesn't expose
 * the trailing `cropRect` argument on every filter factory (only
 * [SkImageFilters.Blur] currently). Upstream's `(filter, cropRect)`
 * pattern is reproduced by wrapping each filter's output with
 * [SkImageFilters.Crop] at the same crop rect — the
 * [croppedFilter] helper handles the wrapping (and is a no-op for
 * already-crop-aware Blur calls).
 */
public class ImageFiltersCropExpandGM : GM() {

    override fun getName(): String = "imagefilterscropexpand"
    override fun getISize(): SkISize = SkISize.Make(730, 650)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val cropRect = SkIRect.MakeXYWH(10, 10, 44, 44)

        val gradientCircle = makeGradientCircle(64, 64)
        val checkerboard = makeCheckerboard()

        val gradientCircleSource: SkImageFilter =
            SkImageFilters.Image(gradientCircle, SkSamplingOptions(SkFilterMode.kLinear))
        val noopCropped: SkImageFilter =
            SkImageFilters.Crop(SkRect.Make(cropRect), SkImageFilters.Offset(0f, 0f, null))

        // Per-channel matrix : saturate green and add 32/255 to alpha.
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 1f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 32f / 255f,
        )
        val cfAlphaTrans = SkColorFilters.Matrix(matrix)

        val margin = 12f
        val pointLocation = floatArrayOf(0f, 0f, 10f)
        val kd = 2f
        val surfaceScale = 1f

        c.translate(margin, margin)

        var outset = -15
        while (outset <= 20) {
            c.save()
            val bigRect = SkIRect.MakeLTRB(
                cropRect.left - outset,
                cropRect.top - outset,
                cropRect.right + outset,
                cropRect.bottom + outset,
            )
            val cropRectF = SkRect.Make(bigRect)

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.ColorFilter(cfAlphaTrans, noopCropped), cropRectF,
            ))

            draw(c, checkerboard, bigRect, SkImageFilters.Blur(
                0.3f, 0.3f, SkTileMode.kDecal, noopCropped, bigRect,
            ))

            draw(c, checkerboard, bigRect, SkImageFilters.Blur(
                8.0f, 8.0f, SkTileMode.kDecal, noopCropped, bigRect,
            ))

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.Dilate(2, 2, noopCropped), cropRectF,
            ))

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.Erode(2, 2, noopCropped), cropRectF,
            ))

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.DropShadow(10f, 10f, 3f, 3f, SK_ColorBLUE, noopCropped), cropRectF,
            ))

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.DisplacementMap(
                    SkColorChannel.kR, SkColorChannel.kR, 12f,
                    gradientCircleSource, noopCropped,
                ), cropRectF,
            ))

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.Offset(-8f, 16f, noopCropped), cropRectF,
            ))

            draw(c, checkerboard, bigRect, croppedFilter(
                SkImageFilters.PointLitDiffuse(
                    pointLocation, SK_ColorWHITE, surfaceScale, kd, noopCropped,
                ), cropRectF,
            ))

            c.restore()
            c.translate(0f, 80f)
            outset += 5
        }
    }

    /** Wrap [filter] with a [SkImageFilters.Crop] at [rect]. */
    private fun croppedFilter(filter: SkImageFilter, rect: SkRect): SkImageFilter =
        SkImageFilters.Crop(rect, filter)

    /**
     * Mirrors C++ `draw(canvas, image, layerRect, filter)` — saves a
     * layer (no bounds hint) with the filter applied via paint, draws
     * the source image at the origin, restores, then strokes a red
     * outline of `layerRect`. The C++ source uses a paint-only
     * `saveLayer(nullptr, &paint)` so the filter's crop rect itself
     * defines the effective output bounds.
     */
    private fun draw(canvas: SkCanvas, image: SkImage, layerRect: SkIRect, filter: SkImageFilter?) {
        val paint = SkPaint().apply { imageFilter = filter }
        canvas.saveLayer(null, paint)
        canvas.drawImage(image, 0f, 0f)
        canvas.restore()

        val strokePaint = SkPaint().apply {
            color = 0xFFFF0000.toInt()
            style = SkPaint.Style.kStroke_Style
        }
        canvas.drawRect(SkRect.Make(layerRect), strokePaint)

        canvas.translate(80f, 0f)
    }

    private fun makeCheckerboard(): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(64, 64))
        val canvas = surface.canvas
        canvas.clear(0xFFFF0000.toInt())
        val darkPaint = SkPaint().apply { color = 0xFF404040.toInt() }
        val lightPaint = SkPaint().apply { color = 0xFFA0A0A0.toInt() }
        var y = 8
        while (y < 48) {
            var x = 8
            while (x < 48) {
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                canvas.drawRect(SkRect.MakeXYWH(0f, 0f, 8f, 8f), darkPaint)
                canvas.drawRect(SkRect.MakeXYWH(8f, 0f, 8f, 8f), lightPaint)
                canvas.drawRect(SkRect.MakeXYWH(0f, 8f, 8f, 8f), lightPaint)
                canvas.drawRect(SkRect.MakeXYWH(8f, 8f, 8f, 8f), darkPaint)
                canvas.restore()
                x += 16
            }
            y += 16
        }
        return surface.makeImageSnapshot()
    }

    private fun makeGradientCircle(width: Int, height: Int): SkImage {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.8f
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(width, height))
        val canvas = surface.canvas
        canvas.clear(0x00000000)
        val paint = SkPaint().apply {
            shader = SkRadialGradient.Make(
                SkPoint(cx, cy), radius,
                intArrayOf(SK_ColorWHITE, SK_ColorBLACK),
                null,
                SkTileMode.kClamp,
            )
        }
        canvas.drawCircle(cx, cy, radius, paint)
        return surface.makeImageSnapshot()
    }

}
