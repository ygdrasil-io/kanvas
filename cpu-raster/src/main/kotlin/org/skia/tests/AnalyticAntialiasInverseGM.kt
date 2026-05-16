package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/aaa.cpp::analytic_antialias_inverse` (800 × 800).
 *
 * Single drawn path — a 30-radius circle at (100, 100) flipped to
 * `kInverseWinding`. The fill is the **complement** of the disc, so the
 * canvas is filled red everywhere except inside the circle.
 *
 * Probes the rasteriser's analytic-AA inverse-fill code path (large
 * filled background with a small AA hole).
 */
public class AnalyticAntialiasInverseGM : GM() {

    override fun getName(): String = "analytic_antialias_inverse"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
        }

        c.save()
        val path: SkPath = SkPath.Circle(100f, 100f, 30f).makeFillType(SkPathFillType.kInverseWinding)
        c.drawPath(path, p)
        c.restore()
    }
}
