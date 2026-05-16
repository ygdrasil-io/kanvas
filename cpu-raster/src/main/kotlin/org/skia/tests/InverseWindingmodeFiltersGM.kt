package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/inversepaths.cpp::inverse_windingmode_filters`
 * (256 × 100).
 *
 * Probes interaction between blur mask filter and inverse-fill path
 * types under tight clipRects. The path is `(rect ∪ rect) ∪ (rect ∪
 * −rect)` with mixed CW/CCW windings ; rendered through 4 fill types
 * (`kWinding`, `kEvenOdd`, `kInverseWinding`, `kInverseEvenOdd`)
 * each clipped to a 51×99 strip and overlaid with a red 1-px
 * stroked frame around the clip.
 *
 * Validates that the blur-mask path-mask pipeline correctly handles
 * inverse-fill rasterization within a tight clip.
 */
public class InverseWindingmodeFiltersGM : GM() {

    override fun getName(): String = "inverse_windingmode_filters"
    override fun getISize(): SkISize = SkISize.Make(256, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val path = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), SkPathDirection.kCW)
            .addRect(SkRect.MakeLTRB(20f, 20f, 40f, 40f), SkPathDirection.kCW)
            .addRect(SkRect.MakeLTRB(10f, 60f, 30f, 80f), SkPathDirection.kCW)
            .addRect(SkRect.MakeLTRB(20f, 70f, 40f, 90f), SkPathDirection.kCCW)
            .detach()

        val strokePaint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        val clipRect = SkRect.MakeLTRB(0f, 0f, 51f, 99f)
        c.drawPath(path, strokePaint)

        val fillPaint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1f)
        }

        for (fillType in arrayOf(
            SkPathFillType.kWinding,
            SkPathFillType.kEvenOdd,
            SkPathFillType.kInverseWinding,
            SkPathFillType.kInverseEvenOdd,
        )) {
            c.translate(51f, 0f)
            c.save()
            c.clipRect(clipRect)
            c.drawPath(path.makeFillType(fillType), fillPaint)
            c.restore()
            val clipPaint = SkPaint().apply {
                color = SK_ColorRED
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 1f
            }
            c.drawRect(clipRect, clipPaint)
        }
    }
}
