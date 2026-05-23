package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(dashbigrects, …)` (256 × 256).
 *
 * Draws 11 concentric dashed rects at exponentially increasing sizes (from
 * 4 × `kOnOffInterval` up to 1 000 000 000 × `kOnOffInterval`). Each rect
 * is given a random 16-bit-565-quantised colour (matching upstream's
 * `ToolUtils::color_to_565` round-trip). The canvas is cleared to black.
 *
 * The extremely large rect dimensions stress the dash counter arithmetic
 * at IEEE float precision limits — each width/height is chosen as
 * `N * kOnOffInterval + kOnOffInterval/2` so the first and last dash
 * segments are always half-length, forcing the edge-clip logic.
 *
 * Reference image: `dashbigrects.png`, 256 × 256, black BG.
 */
public class DashBigRectsGM : GM() {

    override fun getName(): String = "dashbigrects"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val rand = SkRandom()

        val kHalfStrokeWidth = 8
        val kOnOffInterval = 2 * kHalfStrokeWidth

        c.clear(SK_ColorBLACK)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = (2 * kHalfStrokeWidth).toFloat()
            strokeCap = SkPaint.Cap.kButt_Cap
            pathEffect = SkDashPathEffect.Make(
                floatArrayOf(kOnOffInterval.toFloat(), kOnOffInterval.toFloat()), 0f
            )
        }

        val gWidthHeights = floatArrayOf(
            1_000_000_000f * kOnOffInterval + kOnOffInterval / 2f,
            1_000_000f    * kOnOffInterval + kOnOffInterval / 2f,
            1_000f        * kOnOffInterval + kOnOffInterval / 2f,
            100f          * kOnOffInterval + kOnOffInterval / 2f,
            10f           * kOnOffInterval + kOnOffInterval / 2f,
            9f            * kOnOffInterval + kOnOffInterval / 2f,
            8f            * kOnOffInterval + kOnOffInterval / 2f,
            7f            * kOnOffInterval + kOnOffInterval / 2f,
            6f            * kOnOffInterval + kOnOffInterval / 2f,
            5f            * kOnOffInterval + kOnOffInterval / 2f,
            4f            * kOnOffInterval + kOnOffInterval / 2f,
        )

        for (i in gWidthHeights.indices) {
            paint.color = ToolUtils.colorTo565(rand.nextU() or (0xFF shl 24))
            val offset = (2 * i * kHalfStrokeWidth + kHalfStrokeWidth).toFloat()
            c.drawRect(
                SkRect.MakeXYWH(offset, offset, gWidthHeights[i], gWidthHeights[i]),
                paint,
            )
        }
    }
}
