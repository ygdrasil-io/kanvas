package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/crbug_1257515.cpp::crbug_1257515` (DEF_SIMPLE_GM,
 * 1139 × 400).
 *
 * Two long polylines stroked under a `translate + scale(2,2)` :
 *  1. Red, 12-vertex, `strokeWidth = 2`, `kRound_Cap` + `kRound_Join`.
 *  2. Blue (47, 136, 255), 10-vertex, `strokeWidth = 3`, `kButt_Cap` +
 *     `kBevel_Join` + `strokeMiter = 10`.
 *
 * Originally a regression test for an iOS/Chromium SVG rendering bug
 * where polyline strokes under non-1 scale produced disconnected
 * segments.
 */
public class Crbug1257515GM : GM() {

    override fun getName(): String = "crbug_1257515"
    override fun getISize(): SkISize = SkISize.Make(1139, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            color = SK_ColorRED
            strokeWidth = 2f
            style = SkPaint.Style.kStroke_Style
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeJoin = SkPaint.Join.kRound_Join
            isAntiAlias = true
        }

        // First path — red polyline.
        val red = SkPathBuilder()
            .moveTo(45.125f, 102.53701800000002f)
            .lineTo(135.375f, 162.666156f)
            .lineTo(225.625f, 116.622276f)
            .lineTo(315.875f, 121.52087700000001f)
            .lineTo(406.125f, 134.632899f)
            .lineTo(496.375f, 192.317736f)
            .lineTo(586.625f, 138.82944899999998f)
            .lineTo(676.875f, 234.212031f)
            .lineTo(767.125f, 207.082926f)
            .lineTo(857.375f, 128.083857f)
            .lineTo(947.625f, 127.95689999999999f)
            .lineTo(1037.875f, 113.956785f)
            .detach()
        c.save()
        c.translate(-50f, -200f)
        c.scale(2f, 2f)
        c.drawPath(red, p)
        c.restore()

        // Second path — blue polyline.
        val blue = SkPathBuilder()
            .moveTo(128.5307f, 587.5728f)
            .lineTo(232.4748f, 617.037f)
            .lineTo(335.4189f, 624.8472f)
            .lineTo(438.3631f, 630.5933f)
            .lineTo(541.3073f, 625.1138f)
            .lineTo(644.2513f, 626.8717f)
            .lineTo(747.1955f, 629.9542f)
            .lineTo(850.1396f, 629.6956f)
            .lineTo(953.0838f, 616.4909f)
            .lineTo(1056.028f, 613.8181f)
            .detach()
        p.color = SkColorSetARGB(255, 47, 136, 255)
        p.strokeWidth = 3f
        p.strokeCap = SkPaint.Cap.kButt_Cap
        p.strokeJoin = SkPaint.Join.kBevel_Join
        p.strokeMiter = 10f

        c.save()
        c.translate(-300f, -900f)
        c.scale(2f, 2f)
        c.drawPath(blue, p)
        c.restore()
    }
}
