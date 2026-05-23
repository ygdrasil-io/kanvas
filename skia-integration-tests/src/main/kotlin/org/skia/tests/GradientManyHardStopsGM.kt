package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::gradient_many_hard_stops` (`DEF_SIMPLE_GM`,
 * 500 × 500).
 *
 * Draws a diagonal linear gradient with 300 hard-stop pairs cycling
 * through Red | Green | Blue. Positions are calculated as
 * `pos[i] = (2.0f * (i / 2)) / kStopCount`, causing each colour to
 * occupy exactly two adjacent stops at the same value (a true hard
 * stop). This stresses the gradient evaluator's hard-stop detection
 * logic on both CPU and GPU.
 *
 * C++ original (`draw_many_hard_stops`):
 * ```cpp
 * const unsigned kStopCount = 300;
 * const SkPoint pts[] = {{50, 50}, {450, 450}};
 * SkColor4f colors[kStopCount];
 * SkScalar pos[kStopCount];
 * for (unsigned i = 0; i < kStopCount; i++) {
 *     switch (i % 6) {
 *         case 0: colors[i] = SkColors::kRed;   break;
 *         case 1: colors[i] = SkColors::kGreen; break;
 *         case 2: colors[i] = SkColors::kGreen; break;
 *         case 3: colors[i] = SkColors::kBlue;  break;
 *         case 4: colors[i] = SkColors::kBlue;  break;
 *         case 5: colors[i] = SkColors::kRed;   break;
 *     }
 *     pos[i] = (2.0f * (i / 2)) / kStopCount;
 * }
 * p.setShader(SkShaders::LinearGradient(pts, {{colors, pos, SkTileMode::kClamp}, {}}));
 * canvas->drawRect(SkRect::MakeXYWH(0, 0, 500, 500), p);
 * ```
 */
public class GradientManyHardStopsGM : GM() {

    override fun getName(): String = "gradient_many_hard_stops"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kStopCount = 300
        val colors = IntArray(kStopCount) { i ->
            when (i % 6) {
                0 -> 0xFFFF0000.toInt() // Red
                1 -> 0xFF00FF00.toInt() // Green
                2 -> 0xFF00FF00.toInt() // Green
                3 -> 0xFF0000FF.toInt() // Blue
                4 -> 0xFF0000FF.toInt() // Blue
                else -> 0xFFFF0000.toInt() // Red
            }
        }
        val pos = FloatArray(kStopCount) { i ->
            (2.0f * (i / 2)) / kStopCount
        }

        val p0 = SkPoint(50f, 50f)
        val p1 = SkPoint(450f, 450f)

        val paint = SkPaint()
        paint.shader = SkLinearGradient.Make(p0, p1, colors, pos, SkTileMode.kClamp)
        c.drawRect(SkRect.MakeXYWH(0f, 0f, 500f, 500f), paint)
    }
}
