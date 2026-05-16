package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurs.cpp::blur2rects` (700 × 500).
 *
 * Two nested rects (outer CW + inner CCW for an even-odd ring), drawn
 * twice : once at the canonical position and once translated by a
 * fractional `dx = round(width) + 14.25` to exercise a different
 * pixel-grid phase. Both copies receive a `kNormal` blur of σ = 2.3.
 *
 * Originally a regression case for a "nine-patch" blur cache where
 * the same path drawn at a different sub-pixel phase produced subtly
 * different output ; the fix forced both phases through the same
 * pipeline.
 */
public class Blur2RectsGM : GM() {

    override fun getName(): String = "blur2rects"
    override fun getISize(): SkISize = SkISize.Make(700, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 2.3f)
        }

        val outer = SkRect.MakeXYWH(10.125f, 10.125f, 100.125f, 100f)
        val inner = SkRect.MakeXYWH(20.25f, 20.125f, 80f, 80f)
        val path = SkPathBuilder()
            .addRect(outer, SkPathDirection.kCW)
            .addRect(inner, SkPathDirection.kCCW)
            .detach()

        c.drawPath(path, paint)
        // Round to nearest integer + 14 + 0.25 for fractional phase.
        val dx = kotlin.math.round(path.computeBounds().width()) + 14f + 0.25f
        c.translate(dx, 0f)
        c.drawPath(path, paint)
    }
}
