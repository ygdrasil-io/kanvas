package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::gradients_interesting`
 * (`DEF_SIMPLE_GM`, 640 × 1300).
 *
 * Exercises the special-case Ganesh (GPU) gradient effects: 6 colour/position
 * configurations × 3 tile modes in a grid. Each cell is a 200 × 200 rect
 * with a short linear gradient (from size/3 to 2×size/3) so that all tile
 * modes produce visible repetitions.
 *
 * Configs:
 *  - `{colors2, {}}` — kTwo_ColorType
 *  - `{colors3, {}}` — kThree_ColorType (simple)
 *  - `{colors3, softRight}` — kThree_ColorType (tricky, pos ~0.999)
 *  - `{colors3, hardLeft}` — kHardStopLeftEdged_ColorType (pos 0,0,1)
 *  - `{colors3, hardRight}` — kHardStopRightEdged_ColorType (pos 0,1,1)
 *  - `{colors4, hardCenter}` — kSingleHardStop_ColorType (pos 0,.5,.5,1)
 *
 * Tile modes: kClamp, kRepeat, kMirror
 *
 * C++ original:
 * ```cpp
 * static constexpr SkScalar size = 200;
 * static const SkPoint pts[] = { { size/3, size/3 }, { size*2/3, size*2/3} };
 * for (const auto& cfg : configs) {
 *   for (auto mode : modes) {
 *     SkGradient grad = {{cfg.colors, cfg.pos, mode}, {}};
 *     p.setShader(SkShaders::LinearGradient(pts, grad));
 *     canvas->drawRect(SkRect::MakeWH(size, size), p);
 *     canvas->translate(size * 1.1f, 0);
 *   }
 *   canvas->translate(0, size * 1.1f);
 * }
 * ```
 *
 * Note: the `SkGradient` struct in upstream carries an `Interpolation`
 * member. We use default sRGB interpolation (the `{}` initialiser in C++),
 * which maps directly to the standard [SkLinearGradient.Make] call.
 */
public class GradientsInterestingGM : GM() {

    override fun getName(): String = "gradients_interesting"
    override fun getISize(): SkISize = SkISize.Make(640, 1300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val colors2 = intArrayOf(SK_ColorRED, SK_ColorBLUE)
        val colors3 = intArrayOf(SK_ColorRED, SK_ColorYELLOW, SK_ColorBLUE)
        val colors4 = intArrayOf(SK_ColorRED, SK_ColorYELLOW, SK_ColorYELLOW, SK_ColorBLUE)

        // softRight: Based on Android launcher "clipping"
        val softRight  = floatArrayOf(0f, 0.999f, 1f)
        val hardLeft   = floatArrayOf(0f, 0f,     1f)
        val hardRight  = floatArrayOf(0f, 1f,     1f)
        val hardCenter = floatArrayOf(0f, 0.5f, 0.5f, 1f)

        data class Config(val colors: IntArray, val pos: FloatArray?)

        val configs = listOf(
            Config(colors2, null),       // kTwo_ColorType
            Config(colors3, null),       // kThree_ColorType (simple)
            Config(colors3, softRight),  // kThree_ColorType (tricky)
            Config(colors3, hardLeft),   // kHardStopLeftEdged_ColorType
            Config(colors3, hardRight),  // kHardStopRightEdged_ColorType
            Config(colors4, hardCenter), // kSingleHardStop_ColorType
        )

        val modes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)

        val size = 200f
        val p0 = SkPoint(size / 3f, size / 3f)
        val p1 = SkPoint(size * 2f / 3f, size * 2f / 3f)

        val paint = SkPaint()
        for (cfg in configs) {
            c.save()
            for (mode in modes) {
                paint.shader = SkLinearGradient.Make(p0, p1, cfg.colors, cfg.pos, mode)
                c.drawRect(SkRect.MakeWH(size, size), paint)
                c.translate(size * 1.1f, 0f)
            }
            c.restore()
            c.translate(0f, size * 1.1f)
        }
    }
}
