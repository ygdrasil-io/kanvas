package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
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
 * Port of Skia's `gm/gradient_dirty_laundry.cpp::GradientsGM`
 * (`getName() = "gradient_dirty_laundry"`).
 *
 * Regression GM for the gradient sampler — replays the same 40-stop
 * RGBWB sequence through linear, radial and sweep gradients, each
 * filling a 100×100 square. Originally added as a "dirty laundry"
 * marker for stops that the Skia gradient sampler rendered with
 * banding ; the long stop sequence stresses the sampler's per-stop
 * lerp arithmetic.
 *
 * C++ original:
 * ```cpp
 * class GradientsGM : public GM {
 * public:
 *     GradientsGM() { this->setBGColor(0xFFDDDDDD); }
 *     SkString getName() const override { return SkString("gradient_dirty_laundry"); }
 *     SkISize getISize() override { return SkISize::Make(640, 615); }
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPoint pts[2] = { { 0, 0 }, { 100, 100 } };
 *         SkTileMode tm = SkTileMode::kClamp;
 *         SkRect r = { 0, 0, 100, 100 };
 *         SkPaint paint; paint.setAntiAlias(true);
 *         canvas->translate(20, 20);
 *         for (size_t i = 0; i < std::size(gGradData); i++) {
 *             canvas->save();
 *             for (size_t j = 0; j < std::size(gGradMakers); j++) {
 *                 paint.setShader(gGradMakers[j](pts, gGradData[i], tm));
 *                 canvas->drawRect(r, paint);
 *                 canvas->translate(0, 120);
 *             }
 *             canvas->restore();
 *             canvas->translate(120, 0);
 *         }
 *     }
 * };
 * ```
 */
public class GradientDirtyLaundryGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String = "gradient_dirty_laundry"
    override fun getISize(): SkISize = SkISize.Make(640, 615)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 100f))
        val tm = SkTileMode.kClamp
        val r = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val paint = SkPaint().apply { isAntiAlias = true }

        // 40 entries — 8 repetitions of the 5-colour pattern.
        val basePattern = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorWHITE, SK_ColorBLACK)
        val colors = IntArray(40) { basePattern[it % basePattern.size] }

        val makers: List<(SkPoint, SkPoint, IntArray, SkTileMode) -> SkShader?> = listOf(
            { p0, p1, cs, t -> SkLinearGradient.Make(p0, p1, cs, null, t) },
            { p0, p1, cs, t ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                SkRadialGradient.Make(SkPoint(cx, cy), cx, cs, null, t)
            },
            { p0, p1, cs, t ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                SkSweepGradient.Make(SkPoint(cx, cy), cs, null, t)
            },
        )

        c.translate(20f, 20f)
        // Only one GradData entry — the loop over `i` runs once.
        c.save()
        for (j in makers.indices) {
            paint.shader = makers[j](pts[0], pts[1], colors, tm)
            c.drawRect(r, paint)
            c.translate(0f, 120f)
        }
        c.restore()
    }
}
