package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::rgbw_sweep_gradient` (`DEF_SIMPLE_GM`,
 * 100 × 100). Draws one full-revolution sweep with hardstops at each
 * quarter — alternating between full-saturated colour and white.
 *
 * Stops:
 *   - White from t=0 to t=.25
 *   - Blue from t=.25 to t=.5
 *   - Red from t=.5 to t=.75
 *   - Green from t=.75 to t=1
 *
 * The four hardstops divide the disc into four 90° sectors going
 * clockwise starting at the +X axis: white (right) → blue (down) →
 * red (left) → green (up). Useful as the simplest possible sanity check
 * for the sweep gradient angle convention.
 */
public class RgbwSweepGradientGM : GM() {

    override fun getName(): String = "rgbw_sweep_gradient"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val colors = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),  // White, White
            0xFF0000FF.toInt(), 0xFF0000FF.toInt(),  // Blue, Blue
            0xFFFF0000.toInt(), 0xFFFF0000.toInt(),  // Red, Red
            0xFF00FF00.toInt(), 0xFF00FF00.toInt(),  // Green, Green
        )
        val positions = floatArrayOf(0f, 0.25f, 0.25f, 0.50f, 0.50f, 0.75f, 0.75f, 1f)

        val shader = SkSweepGradient.Make(
            center = SkPoint(SIZE / 2f, SIZE / 2f),
            colors = colors,
            positions = positions,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply { this.shader = shader }
        c.drawRect(SkRect.MakeWH(SIZE, SIZE), paint)
    }

    private companion object {
        const val SIZE: Float = 100f
    }
}
