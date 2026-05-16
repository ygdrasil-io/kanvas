package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/crbug_996140.cpp::crbug_996140`
 * (DEF_SIMPLE_GM_BG, 300 × 300, BG = white).
 *
 * Reproduces a Canvas2D `arc()` rendering bug : a tiny circle at
 * `(19.221, 720 - 6.76)` with `radius = 0.0295275590551181` is drawn
 * after `scale(203.20, 203.20)` and translates that bring it into
 * device space. The reproduced path is two `arcTo(boundingBox, 0, 180)`
 * + `arcTo(boundingBox, 180, 180)` half-arcs (matches how Canvas
 * decomposes a 2π arc). Drawn stroked (blue, 1 px) then filled (red).
 */
public class Crbug996140GM : GM() {

    init { setBGColor(SK_ColorWHITE) }

    override fun getName(): String = "crbug_996140"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val cx = 19.221f
        val cy = 720f - 6.76f
        val radius = 0.0295275590551181f

        val s = 203.20f
        val tx = -14.55f
        val ty = -711.51f

        c.translate(-800f, -200f)
        c.scale(s, s)
        c.translate(tx, ty)

        val fill = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kFill_Style
            isAntiAlias = true
        }
        val stroke = SkPaint().apply {
            color = SK_ColorBLUE
            strokeWidth = 1f
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = true
        }

        val boundingBox = SkRect.MakeLTRB(cx - radius, cy - radius, cx + radius, cy + radius)
        val path = SkPathBuilder()
            .arcTo(boundingBox, 0f, 180f, false)
            .arcTo(boundingBox, 180f, 180f, false)
            .detach()

        c.drawPath(path, stroke)
        c.drawPath(path, fill)
    }
}
