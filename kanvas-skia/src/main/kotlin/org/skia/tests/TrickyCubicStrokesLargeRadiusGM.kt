package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/trickycubicstrokes.cpp::trickycubicstrokes_largeradius`
 * (DEF_SIMPLE_GM, 128 × 256).
 *
 * Two cubic paths with `strokeWidth = 200` exposing cusp-circle artifacts
 * at large radii. Cubic 0 is a straight line, cubic 1 has slight
 * curvature (`shift = 210`, `dy = 5`). Drawn at `scale(0.5, 0.5) +
 * translate(-125, 0)`. (b/433057370.)
 */
public class TrickyCubicStrokesLargeRadiusGM : GM() {

    override fun getName(): String = "trickycubicstrokes_largeradius"
    override fun getISize(): SkISize = SkISize.Make(128, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val b = SkPathBuilder()
        for (y in 0..1) {
            val shift = 210f * y
            val dy = 5f * y
            b.moveTo(159.429f, 149.808f + shift)
                .cubicTo(
                    232.5f, 149.808f + dy + shift,
                    232.5f, 149.808f + dy + shift,
                    305.572f, 149.808f + shift,
                )
        }
        b.setFillType(SkPathFillType.kWinding)
        val s = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 200f
            isAntiAlias = true
        }
        c.scale(0.5f, 0.5f)
        c.translate(-125f, 0f)
        c.drawPath(b.detach(), s)
    }
}
