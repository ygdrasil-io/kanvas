package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorMAGENTA
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/imagefilterscropped.cpp::ImageFiltersCroppedGM`
 * (name `imagefilterscropped`, 400 × 960).
 *
 * For each of 4 draw functions (`drawBitmap`, `drawPath`, `drawPaint`,
 * `drawText`) walks a 14-entry filter table. Each filter has a crop rect
 * — either the standard `(10, 10, 44, 44)` rect or a "bogus" rect
 * outside the draw bounds — so the crop's interaction with the rendered
 * primitive is the GM's main thing.
 *
 * **Adaptations** — same as [ImageFiltersCropExpandGM] : non-Blur
 * filters in `:kanvas-skia` don't expose the trailing cropRect arg, so
 * we wrap each filter's output with [SkImageFilters.Crop] at the same
 * rect via the local `cropped` helper.
 */
public class ImageFiltersCroppedGM : GM() {

    override fun getName(): String = "imagefilterscropped"
    override fun getISize(): SkISize = SkISize.Make(400, 960)

    private lateinit var fCheckerboard: SkImage

    override fun onOnceBeforeDraw() {
        fCheckerboard = makeCheckerboard()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val drawProc: Array<(SkCanvas, SkRect, SkImageFilter?) -> Unit> = arrayOf(
            ::drawBitmap, ::drawPath, ::drawPaint, ::drawText,
        )

        val cf = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kSrcIn)
        val cropRect = SkIRect.MakeXYWH(10, 10, 44, 44)
        val bogusRect = SkIRect.MakeXYWH(-100, -100, 10, 10)
        val cropF = SkRect.Make(cropRect)
        val bogusF = SkRect.Make(bogusRect)

        val offset: SkImageFilter = SkImageFilters.Offset(-10f, -10f, null)
        val cfOffset: SkImageFilter = SkImageFilters.ColorFilter(cf, offset)

        // Outer erode on a different axis from the inner erode — the
        // inner halves intentionally have **no** crop rect (else the
        // inner crop would clip away the data needed by the outer pass).
        val erodeX: SkImageFilter = SkImageFilters.Erode(8, 0, null)
        val erodeY: SkImageFilter = SkImageFilters.Erode(0, 8, null)

        val filters: Array<SkImageFilter?> = arrayOf(
            null,
            cropped(SkImageFilters.ColorFilter(cf, null), cropF),
            SkImageFilters.Blur(0f, 0f, SkTileMode.kDecal, null, cropRect),
            SkImageFilters.Blur(1f, 1f, SkTileMode.kDecal, null, cropRect),
            SkImageFilters.Blur(8f, 0f, SkTileMode.kDecal, null, cropRect),
            SkImageFilters.Blur(0f, 8f, SkTileMode.kDecal, null, cropRect),
            SkImageFilters.Blur(8f, 8f, SkTileMode.kDecal, null, cropRect),
            cropped(SkImageFilters.Erode(1, 1, null), cropF),
            cropped(SkImageFilters.Erode(8, 0, erodeY), cropF),
            cropped(SkImageFilters.Erode(0, 8, erodeX), cropF),
            cropped(SkImageFilters.Erode(8, 8, null), cropF),
            cropped(SkImageFilters.Merge(null, cfOffset), cropF),
            SkImageFilters.Blur(8f, 8f, SkTileMode.kDecal, null, bogusRect),
            cropped(SkImageFilters.ColorFilter(cf, null), bogusF),
        )

        val r = SkRect.MakeWH(64f, 64f)
        val margin = 16f
        val dx = r.width() + margin
        val dy = r.height() + margin

        c.translate(margin, margin)
        for (j in drawProc.indices) {
            c.save()
            for (i in filters.indices) {
                c.drawImage(fCheckerboard, 0f, 0f)
                drawProc[j](c, r, filters[i])
                c.translate(0f, dy)
            }
            c.restore()
            c.translate(dx, 0f)
        }
    }

    /** Wrap [filter] with a [SkImageFilters.Crop] at [rect]. */
    private fun cropped(filter: SkImageFilter, rect: SkRect): SkImageFilter =
        SkImageFilters.Crop(rect, filter)

    private fun drawPaint(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            imageFilter = imf
            color = SK_ColorBLACK
        }
        canvas.save()
        canvas.clipRect(r)
        canvas.drawPaint(paint)
        canvas.restore()
    }

    private fun drawPath(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            color = SK_ColorMAGENTA
            imageFilter = imf
            isAntiAlias = true
        }
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 2f / 5f, paint)
    }

    private fun drawText(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            imageFilter = imf
            color = SK_ColorGREEN
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), r.height() / 2f)
        SkTextUtils.DrawString(
            canvas, "Text", r.centerX(), r.centerY(), font, paint, SkTextUtils.Align.kCenter_Align,
        )
    }

    private fun drawBitmap(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val bounds = r.roundOut()
        val surface = SkSurface.MakeRaster(
            SkImageInfo.MakeN32Premul(bounds.width(), bounds.height()),
        )
        // Render the path (no filter) into the off-screen surface, then
        // composite the surface onto the target canvas with the filter
        // applied via the destination paint — exactly upstream's flow.
        drawPath(surface.canvas, r, null)
        val paint = SkPaint().apply { imageFilter = imf }
        surface.draw(canvas, 0f, 0f, paint)
    }

    private fun makeCheckerboard(): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(80, 80))
        val canvas = surface.canvas
        val darkPaint = SkPaint().apply { color = 0xFF404040.toInt() }
        val lightPaint = SkPaint().apply { color = 0xFFA0A0A0.toInt() }
        var y = 0
        while (y < 80) {
            var x = 0
            while (x < 80) {
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

}
