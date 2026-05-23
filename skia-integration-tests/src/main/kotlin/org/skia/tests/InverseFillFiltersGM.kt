package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/inversepaths.cpp::inverse_fill_filters` (384 × 128).
 *
 * Renders 3 cells side-by-side, each 128×128 (clipped), showing an
 * inverse-winding circle (r=30) at (65, 65) under 3 filter states :
 *
 *  1. No filter — plain anti-aliased inverse fill.
 *  2. Image filter — `SkImageFilters::Blur(5, 5, nullptr)`.
 *  3. Mask filter — `SkMaskFilter::MakeBlur(kNormal, 5)`.
 *
 * Each cell is framed with a 1-px white stroke. Used to validate that
 * both blur filter paths handle inverse-fill rasterisation correctly.
 */
public class InverseFillFiltersGM : GM() {

    override fun getName(): String = "inverse_fill_filters"
    override fun getISize(): SkISize = SkISize.Make(384, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val draw: (SkPaint) -> Unit = { paint ->
            var path = SkPath.Circle(65f, 65f, 30f)
            path = path.makeFillType(SkPathFillType.kInverseWinding)

            c.save()
            c.clipRect(SkRect.MakeLTRB(0f, 0f, 128f, 128f))
            c.drawPath(path, paint)
            c.restore()

            val stroke = SkPaint().apply {
                style = SkPaint.Style.kStroke_Style
                color = SK_ColorWHITE
            }
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 128f, 128f), stroke)
        }

        val paint = SkPaint().apply {
            isAntiAlias = true
        }

        // Cell 1: no filter
        draw(paint)

        // Cell 2: image filter blur
        c.translate(128f, 0f)
        draw(SkPaint().apply {
            isAntiAlias = true
            imageFilter = SkImageFilters.Blur(5f, 5f, null)
        })

        // Cell 3: mask filter blur
        c.translate(128f, 0f)
        draw(SkPaint().apply {
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 5f)
        })
    }
}
