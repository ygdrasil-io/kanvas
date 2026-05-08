package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorMAGENTA
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/dropshadowimagefilter.cpp` :
 * `DEF_SIMPLE_GM(dropshadowimagefilter, canvas, 400, 656)`.
 *
 * 4-column × 8-row grid : 4 draw recipes (`drawPaint`, `drawCircle`,
 * `drawText`, `drawBitmap`) × 8 drop-shadow filters covering
 * various dx/dy/sigmaX/sigmaY/color permutations + composed-with-
 * color-filter + DropShadowOnly variant.
 *
 * **Adaptations** :
 *  - Our `SkImageFilters.DropShadow` factory takes only
 *    `(dx, dy, sigmaX, sigmaY, color, input)` ; upstream's later
 *    overloads accept `colorSpace` and `cropRect` parameters that
 *    we don't expose. The 2 affected filter slots (slot 5 and 6)
 *    fall back to the simple variant — visible drift on those
 *    rows.
 *  - The `draw_text` recipe is replaced by a `drawCircle` because
 *    `SkTextUtils.DrawString` doesn't have a direct equivalent in
 *    our codebase. The text column will diverge from upstream.
 */
public class DropShadowImageFilterGM : GM() {

    override fun getName(): String = "dropshadowimagefilter"
    override fun getISize(): SkISize = SkISize.Make(400, 656)

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
            color = SK_ColorGREEN
            imageFilter = imf
            isAntiAlias = true
        }
        canvas.save()
        canvas.clipRect(r)
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 3f, paint)
        canvas.restore()
    }

    private fun drawTextSubstitute(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        // Upstream draws "Text" centred in `r`. We substitute a
        // smaller filled rectangle to keep the column populated.
        val paint = SkPaint().apply {
            color = SK_ColorGREEN
            imageFilter = imf
            isAntiAlias = true
        }
        canvas.save()
        canvas.clipRect(r)
        val textRect = SkRect.MakeXYWH(
            r.centerX() - r.width() / 4f,
            r.centerY() - r.height() / 6f,
            r.width() / 2f,
            r.height() / 3f,
        )
        canvas.drawRect(textRect, paint)
        canvas.restore()
    }

    private fun drawBitmap(canvas: SkCanvas, r: SkRect, imf: SkImageFilter?) {
        val bounds = r.roundOut()
        val surf = SkSurface.MakeRasterN32Premul(bounds.width(), bounds.height())
        drawPath(surf.canvas, r, null)
        val paint = SkPaint().apply { imageFilter = imf }
        canvas.save()
        canvas.clipRect(r)
        surf.draw(canvas, 0f, 0f, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val drawProcs: List<(SkCanvas, SkRect, SkImageFilter?) -> Unit> = listOf(
            ::drawBitmap, ::drawPath, ::drawPaint, ::drawTextSubstitute,
        )

        val cf = SkColorFilters.Blend(SK_ColorMAGENTA, SkBlendMode.kSrcIn)
        val cfif = SkImageFilters.ColorFilter(cf, null)

        val filters: List<SkImageFilter?> = listOf(
            null,
            SkImageFilters.DropShadow(7f, 0f, 0f, 3f, SK_ColorBLUE, null),
            SkImageFilters.DropShadow(0f, 7f, 3f, 0f, SK_ColorBLUE, null),
            SkImageFilters.DropShadow(7f, 7f, 3f, 3f, SK_ColorBLUE, null),
            SkImageFilters.DropShadow(7f, 7f, 3f, 3f, SK_ColorBLUE, cfif),
            // Slot 5 — would use colorSpace + green tint. We use a
            // simple DropShadow with green as colour approximation.
            SkImageFilters.DropShadow(
                7f, 7f, 3f, 3f, 0xFF008000.toInt(), null,
            ),
            // Slot 6 — would use bogus cropRect. Falls back to identity DropShadow.
            SkImageFilters.DropShadow(7f, 7f, 3f, 3f, SK_ColorBLUE, null),
            SkImageFilters.DropShadowOnly(7f, 7f, 3f, 3f, SK_ColorBLUE, null),
        )

        val r = SkRect.MakeWH(64f, 64f)
        val margin = 16f
        val dx = r.width() + margin
        val dy = r.height() + margin

        c.translate(margin, margin)
        for (j in drawProcs.indices) {
            c.save()
            for (i in filters.indices) {
                drawProcs[j](c, r, filters[i])
                c.translate(0f, dy)
            }
            c.restore()
            c.translate(dx, 0f)
        }
    }
}
