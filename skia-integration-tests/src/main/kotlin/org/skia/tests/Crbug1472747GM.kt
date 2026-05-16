package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/circulararcs.cpp::crbug_1472747` (DEF_SIMPLE_GM,
 * 400 × 400).
 *
 * Manually-stroked circle (inner + outer ovals) at `r = 31000` filled
 * with `kEvenOdd` to produce a thin ring. Each oval is decomposed via
 * two `arcTo(oval, 0, -180)` + `arcTo(oval, -180, -180)` half-arcs —
 * mirroring how Canvas2D emits a `2π` arc. Originally exposed a
 * pre-chopping bug in tessellation path renderers that lost the
 * non-default winding mode.
 */
public class Crbug1472747GM : GM() {

    override fun getName(): String = "crbug_1472747"
    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val radius = 31000f
        val cx = 0f
        val cy = radius + 10f

        val builder = SkPathBuilder()
        addCanvas2dCircleArcTo(cx, cy, radius, builder)         // inner
        addCanvas2dCircleArcTo(cx, cy, radius + 5f, builder)    // outer
        builder.setFillType(SkPathFillType.kEvenOdd)

        val fill = SkPaint().apply { isAntiAlias = true }
        c.drawPath(builder.detach(), fill)
    }

    private fun addCanvas2dCircleArcTo(cx: Float, cy: Float, radius: Float, builder: SkPathBuilder) {
        val oval = SkRect.MakeLTRB(cx - radius, cy - radius, cx + radius, cy + radius)
        builder.arcTo(oval, 0f, -180f, false)
        builder.arcTo(oval, -180f, -180f, false)
    }
}
