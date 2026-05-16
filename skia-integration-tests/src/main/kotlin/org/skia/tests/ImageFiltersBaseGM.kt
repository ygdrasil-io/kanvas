package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of upstream Skia's `gm/imagefiltersbase.cpp::ImageFiltersBaseGM`
 * (registered as `imagefiltersbase`).
 *
 * Cartesian product of 8 *draws* × 6 *filters* :
 *
 *  - draws : `drawPaint`, `drawLine`, `drawRect`, `drawPath` (circle),
 *            `drawText`, `drawBitmap`, `drawPatch`, `drawAtlas` ;
 *  - filters : `nullptr`, `Offset(0,0)` (identity), `Empty()`,
 *              `ColorFilter(red SrcIn)`, `Blur(12, 0.29)`,
 *              `DropShadow(10, 5, 3, 3, BLUE)`.
 *
 * Each cell is wrapped in a 1-pixel red rectangle so the bounds of
 * the filtered draw remain visible.
 */
public class ImageFiltersBaseGM : GM() {

    private var atlas: SkImage? = null

    override fun getName(): String = "imagefiltersbase"
    override fun getISize(): SkISize = SkISize.Make(700, 500)

    override fun onOnceBeforeDraw() {
        atlas = createAtlasImage()
    }

    private fun drawFrame(canvas: SkCanvas, r: SkRect) {
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            color = SK_ColorRED
        }
        canvas.drawRect(r, paint)
    }

    private fun drawPaintCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            imageFilter = imf
            color = SK_ColorGREEN
        }
        canvas.save()
        canvas.clipRect(r)
        canvas.drawPaint(paint)
        canvas.restore()
    }

    private fun drawLineCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            imageFilter = imf
            style = SkPaint.Style.kStroke_Style
            strokeWidth = r.width() / 10f
        }
        canvas.drawLine(r.left, r.top, r.right, r.bottom, paint)
    }

    private fun drawRectCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            color = SK_ColorYELLOW
            imageFilter = imf
        }
        val rr = SkRect.MakeLTRB(r.left, r.top, r.right, r.bottom)
        rr.inset(r.width() / 10f, r.height() / 10f)
        canvas.drawRect(rr, paint)
    }

    private fun drawPathCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            color = SK_ColorMAGENTA
            imageFilter = imf
            isAntiAlias = true
        }
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 2f / 5f, paint)
    }

    private fun drawTextCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply {
            imageFilter = imf
            color = SK_ColorCYAN
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), r.height() / 2f)
        SkTextUtils.DrawString(
            canvas, "Text", r.centerX(), r.centerY(), font, paint,
            SkTextUtils.Align.kCenter_Align,
        )
    }

    private fun drawBitmapCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply { imageFilter = imf }
        val bounds = r.roundOut()
        val bm = SkBitmap.Make(bounds.width(), bounds.height())
        bm.eraseColor(SK_ColorTRANSPARENT)
        // Render the path (no filter) into the bitmap, then composite
        // the bitmap to the target with the filter applied.
        val bmCanvas = SkSurface.MakeRasterDirect(bm).canvas
        drawPathCell(bmCanvas, r, null)
        canvas.drawImage(bm.asImage(), 0f, 0f, SkSamplingOptions.Default, paint)
    }

    private fun drawPatchCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val paint = SkPaint().apply { imageFilter = imf }
        val cubics = arrayOf(
            // top
            SkPoint(100f, 100f), SkPoint(150f, 50f), SkPoint(250f, 150f), SkPoint(300f, 100f),
            // right
            SkPoint(250f, 150f), SkPoint(350f, 250f),
            // bottom
            SkPoint(300f, 300f), SkPoint(250f, 250f), SkPoint(150f, 350f), SkPoint(100f, 300f),
            // left
            SkPoint(50f, 250f), SkPoint(150f, 150f),
        )
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN)
        canvas.save()
        canvas.translate(-r.left, -r.top)
        canvas.scale(r.width() / 400f, r.height() / 400f)
        canvas.drawPatch(cubics, colors, null, SkBlendMode.kDst, paint)
        canvas.restore()
    }

    private fun drawAtlasCell(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val a = atlas ?: return
        val rad = Math.toRadians(15.0)
        val xform = SkRSXform.Make(cos(rad).toFloat(), sin(rad).toFloat(), r.width() * 0.15f, 0f)
        val paint = SkPaint().apply {
            imageFilter = imf
            isAntiAlias = true
        }
        canvas.drawAtlas(
            image = a,
            xform = arrayOf(xform),
            src = arrayOf(r),
            colors = null,
            blendMode = SkBlendMode.kSrc,
            sampling = SkSamplingOptions.Default,
            cullRect = null,
            paint = paint,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (atlas == null) atlas = createAtlasImage()

        val drawProcs: List<(SkCanvas, SkRect, SkImageFilter?) -> Unit> = listOf(
            ::drawPaintCell, ::drawLineCell, ::drawRectCell, ::drawPathCell,
            ::drawTextCell, ::drawBitmapCell, ::drawPatchCell, ::drawAtlasCell,
        )

        val cf = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
        val filters: List<SkImageFilter?> = listOf(
            null,
            SkImageFilters.Offset(0f, 0f, null), // "identity"
            SkImageFilters.Empty(),
            SkImageFilters.ColorFilter(cf, null),
            SkImageFilters.Blur(12f, 0.29f, null),
            SkImageFilters.DropShadow(10f, 5f, 3f, 3f, SK_ColorBLUE, null),
        )

        val r = SkRect.MakeWH(64f, 64f)
        val margin = 16f
        val dx = r.width() + margin
        val dy = r.height() + margin

        c.translate(margin, margin)
        for (i in drawProcs.indices) {
            c.save()
            for (j in filters.indices) {
                drawProcs[i](c, r, filters[j])
                drawFrame(c, r)
                c.translate(0f, dy)
            }
            c.restore()
            c.translate(dx, 0f)
        }
    }

    private fun createAtlasImage(): SkImage {
        val w = 64; val h = 64
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val cv = surface.canvas
        val paint = SkPaint().apply { color = 0xFF808080.toInt() } // SK_ColorGRAY
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), h * 0.4f)
        SkTextUtils.DrawString(
            cv, "Atlas", w * 0.5f, h * 0.5f, font, paint,
            SkTextUtils.Align.kCenter_Align,
        )
        return surface.makeImageSnapshot()
    }
}
