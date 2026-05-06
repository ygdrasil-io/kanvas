package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/blurs.cpp::blur2rectsnonninepatch` (700 × 500).
 *
 * Two rects with **same winding** (outer + inner both CW = filled
 * area is the disjoint union, not a ring) under a kNormal σ=4.3 blur.
 * The path is drawn three times :
 *  - at the canonical position,
 *  - translated `dx + 0.25` for a sub-pixel phase shift,
 *  - and translated `(-30, -150)` so the path's bbox extends partly
 *    outside the canvas, exercising the clip-path culling.
 *
 * Differs from [Blur2RectsGM] in that the inner rect's winding is also
 * `kCW`, so the union of the two rects (not the ring) is what gets
 * blurred — this hits a different code path in upstream's nine-patch
 * cache (which only kicks in for ring-shaped paths).
 */
public class Blur2RectsNonNinepatchGM : GM() {

    override fun getName(): String = "blur2rectsnonninepatch"
    override fun getISize(): SkISize = SkISize.Make(700, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 4.3f)
        }

        val outer = SkRect.MakeXYWH(10f, 110f, 100f, 100f)
        val inner = SkRect.MakeXYWH(50f, 150f, 10f, 10f)
        val path = SkPathBuilder()
            .addRect(outer, SkPathDirection.kCW)
            .addRect(inner, SkPathDirection.kCW)
            .detach()
        c.drawPath(path, paint)

        val dx = kotlin.math.round(path.computeBounds().width()) + 40f + 0.25f
        c.translate(dx, 0f)
        c.drawPath(path, paint)

        c.translate(-dx, 0f)
        c.translate(-30f, -150f)
        c.drawPath(path, paint)
    }
}
