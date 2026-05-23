package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::gradients_dup_color_stops`
 * (`DEF_SIMPLE_GM`, 704 × 564).
 *
 * Exercises duplicate colour stops — at the start (`pos=0`), at the end
 * (`pos=1`), and in the middle (`pos=0.5`). Upstream comment: "At the time
 * of this writing, only Linear correctly deals with duplicates at the ends,
 * and then only correctly on CPU backend."
 *
 * The test draws a 5-column × 4-row grid (SIZE=121px cells):
 *  - Rows = gradient type: Linear | Radial | Conical | Sweep
 *  - Columns = GradRun variant (see [GradRun] data class)
 *
 * C++ shape:
 * ```cpp
 * #define SIZE 121
 * struct GradRun { SkColor4f fColors[4]; SkScalar fPos[4]; size_t fCount; };
 * const SkColor4f preColor  = SkColors::kRed;
 * const SkColor4f postColor = SkColors::kBlue;
 * const SkColor4f color0    = SkColors::kBlack;
 * const SkColor4f color1    = SkColors::kGreen;
 * // badColor fills unused array slots — should never appear
 * const SkColor4f badColor  = SkColor4f::FromColor(0xFF3388BB);
 *
 * const GradRun runs[] = {
 *   { {color0, color1, bad, bad}, {0, 1, -1, -1}, 2 },
 *   { {preColor, color0, color1, bad}, {0, 0, 1, -1}, 3 },
 *   { {color0, color1, postColor, bad}, {0, 1, 1, -1}, 3 },
 *   { {preColor, color0, color1, postColor}, {0, 0, 1, 1}, 4 },
 *   { {color0, color0, color1, color1}, {0, 0.5f, 0.5f, 1}, 4 },
 * };
 * ```
 */
public class GradientsDupColorStopsGM : GM() {

    override fun getName(): String = "gradients_dup_color_stops"
    override fun getISize(): SkISize = SkISize.Make(704, 564)

    private data class GradRun(val colors: IntArray, val positions: FloatArray)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val preColor  = SK_ColorRED
        val postColor = SK_ColorBLUE
        val color0    = SK_ColorBLACK
        val color1    = SK_ColorGREEN

        val runs = listOf(
            // 2 stops: black → green, no dup
            GradRun(intArrayOf(color0, color1), floatArrayOf(0f, 1f)),
            // 3 stops: dup at start: red | black → green
            GradRun(intArrayOf(preColor, color0, color1), floatArrayOf(0f, 0f, 1f)),
            // 3 stops: dup at end: black → green | blue
            GradRun(intArrayOf(color0, color1, postColor), floatArrayOf(0f, 1f, 1f)),
            // 4 stops: dup at both ends: red | black → green | blue
            GradRun(intArrayOf(preColor, color0, color1, postColor), floatArrayOf(0f, 0f, 1f, 1f)),
            // 4 stops: hard stop in middle: black ‥ hardstop ‥ green
            GradRun(intArrayOf(color0, color0, color1, color1), floatArrayOf(0f, 0.5f, 0.5f, 1f)),
        )

        val factories: List<(GradRun, SkTileMode) -> SkShader?> = listOf(
            ::makeLinear,
            ::makeRadial,
            ::makeConical,
            ::makeSweep,
        )

        val rect = SkRect.MakeWH(SIZE.toFloat(), SIZE.toFloat())
        val dx = SIZE + 20f
        val dy = SIZE + 20f
        val mode = SkTileMode.kClamp

        val paint = SkPaint()
        c.translate(10f, 10f - dy)

        for (factory in factories) {
            c.translate(0f, dy)
            c.save()
            for (run in runs) {
                paint.shader = factory(run, mode)
                c.drawRect(rect, paint)
                c.translate(dx, 0f)
            }
            c.restore()
        }
    }

    private companion object {
        const val SIZE = 121

        fun makeLinear(run: GradRun, mode: SkTileMode): SkShader {
            val p0 = SkPoint(30f, 30f)
            val p1 = SkPoint(SIZE - 30f, SIZE - 30f)
            return SkLinearGradient.Make(p0, p1, run.colors, run.positions, mode)
        }

        fun makeRadial(run: GradRun, mode: SkTileMode): SkShader {
            val half = SIZE * 0.5f
            return SkRadialGradient.Make(SkPoint(half, half), half - 10f, run.colors, run.positions, mode)
        }

        fun makeConical(run: GradRun, mode: SkTileMode): SkShader? {
            val half = SIZE * 0.5f
            val center = SkPoint(half, half)
            return SkConicalGradient.Make(center, 20f, center, half - 10f, run.colors, run.positions, mode)
        }

        fun makeSweep(run: GradRun, mode: SkTileMode): SkShader {
            val half = SIZE * 0.5f
            return SkSweepGradient.Make(SkPoint(half, half), run.colors, run.positions, mode)
        }
    }
}
