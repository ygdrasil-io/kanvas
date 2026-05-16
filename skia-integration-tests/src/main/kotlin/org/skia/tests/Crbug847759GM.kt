package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/crbug_847759.cpp::crbug_847759` (DEF_SIMPLE_GM,
 * 500 × 500).
 *
 * Closed squashed-oval-like path : 4 cubic Béziers chained tip-to-tip
 * with previously-vertical tangents at the left and right tips. The
 * upstream `AAHairlinePathRenderer` emitted slightly non-vertical quads
 * when converting cubics, leaving missed AA pixels just outside each
 * tip. AA hairline (`strokeWidth = 0`) stress with `strokeMiter = 1.5`
 * (very tight) — the path is rendered at its natural position via a
 * `translate(-80, -330)` offset.
 */
public class Crbug847759GM : GM() {

    override fun getName(): String = "crbug_847759"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(97f, 374.5f)
            .cubicTo(97f, 359.8644528f, 155.8745488f, 348f, 228.5f, 348f)
            .cubicTo(301.1254512f, 348f, 360f, 359.8644528f, 360f, 374.5f)
            .cubicTo(360f, 389.1355472f, 301.1254512f, 401f, 228.5f, 401f)
            .cubicTo(155.8745488f, 401f, 97f, 389.1355472f, 97f, 374.5f)
            .close()
            .detach()
        val paint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = 0f
            strokeMiter = 1.5f
            style = SkPaint.Style.kStroke_Style
        }
        c.translate(-80f, -330f)
        c.drawPath(path, paint)
    }
}
