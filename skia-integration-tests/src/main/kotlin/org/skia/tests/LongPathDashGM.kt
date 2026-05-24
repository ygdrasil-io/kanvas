package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(longpathdash, …)` (612 × 612).
 *
 * Builds a long multi-contour path by weaving short radial segments across
 * a spiral of increasing radii, then strokes the whole thing with a
 * `{1, 1}` dash pattern at stroke-width 1. Exercises the dash path-effect
 * over a path with ~ 1 000 000 short line segments — the historical
 * performance regression target.
 *
 * Reference image: `longpathdash.png`, 612 × 612, default white BG.
 */
public class LongPathDashGM : GM() {

    override fun getName(): String = "longpathdash"
    override fun getISize(): SkISize = SkISize.Make(612, 612)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val lines = SkPathBuilder()
        var x = 32
        while (x < 256) {
            var a = 0.0
            while (a < Math.PI * 2) {
                val pts0x = 256 + sin(a).toFloat() * x
                val pts0y = 256 + cos(a).toFloat() * x
                val pts1x = 256 + sin(a + Math.PI / 3).toFloat() * (x + 64)
                val pts1y = 256 + cos(a + Math.PI / 3).toFloat() * (x + 64)
                lines.moveTo(pts0x, pts0y)
                var i = 0f
                while (i < 1f) {
                    lines.lineTo(
                        pts0x * (1f - i) + pts1x * i,
                        pts0y * (1f - i) + pts1y * i,
                    )
                    i += 0.05f
                }
                a += 0.03141592
            }
            x += 16
        }

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
            pathEffect = SkDashPathEffect.Make(floatArrayOf(1f, 1f), 0f)
        }

        c.translate(50f, 50f)
        c.drawPath(lines.detach(), paint)
    }
}
