package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

/**
 * Port of Skia's `gm/gradients.cpp::RadialGradient2GM`
 * (`radial_gradient2` / `radial_gradient2_nodither`, 800 x 400).
 *
 * Reproduces b/7671058 -- two columns of three stacked circles. Each
 * column uses a different `Interpolation::InPremul` flag (left =
 * `kNo`, right = `kYes`). The three circles per column are :
 *  - a sweep gradient through 7 colours,
 *  - a radial gradient with `colors2` = `(opaque black, transparent)`,
 *  - a radial gradient with `colors1` = `(opaque white, transparent)`.
 *
 * All three share `cx = 200, cy = 200, radius = 150` and `kClamp`
 * tile mode ; each column is offset by 400 px.
 *
 * `:kanvas-skia` does not currently model the `InPremul` flag at the
 * gradient layer, so both columns render identically. Both reference
 * PNGs (`radial_gradient2.png`, `radial_gradient2_nodither.png`) are
 * compared against the same Kotlin output; the floor is captured by
 * the ratchet.
 *
 * C++ original :
 * ```cpp
 * SkISize getISize() override { return {800, 400}; }
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint1, paint2, paint3;
 *     const SkColor4f sweep_colors[] =
 *         { {1,0,0,1}, {1,1,0,1}, {0,1,0,1}, {0,1,1,1}, {0,0,1,1}, {1,0,1,1}, {1,0,0,1} };
 *     const SkColor4f colors1[] = { {1,1,1,1}, {0,0,0,0} };
 *     const SkColor4f colors2[] = { {0,0,0,1}, {0,0,0,0} };
 *     const SkScalar cx = 200, cy = 200, radius = 150;
 *     // two iterations through (premul on / off) ...
 *     canvas->drawCircle(cx, cy, radius, paint1);
 *     canvas->drawCircle(cx, cy, radius, paint3);
 *     canvas->drawCircle(cx, cy, radius, paint2);
 *     canvas->translate(400, 0);
 * }
 * ```
 */
public class RadialGradient2GM(
    private val dither: Boolean = true,
) : GM() {

    override fun getName(): String =
        if (dither) "radial_gradient2" else "radial_gradient2_nodither"

    override fun getISize(): SkISize = SkISize.Make(800, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val sweepColors = intArrayOf(
            SkColor4f(1f, 0f, 0f, 1f).toSkColor(),
            SkColor4f(1f, 1f, 0f, 1f).toSkColor(),
            SkColor4f(0f, 1f, 0f, 1f).toSkColor(),
            SkColor4f(0f, 1f, 1f, 1f).toSkColor(),
            SkColor4f(0f, 0f, 1f, 1f).toSkColor(),
            SkColor4f(1f, 0f, 1f, 1f).toSkColor(),
            SkColor4f(1f, 0f, 0f, 1f).toSkColor(),
        )
        val colors1 = intArrayOf(
            SkColor4f(1f, 1f, 1f, 1f).toSkColor(),
            SkColor4f(0f, 0f, 0f, 0f).toSkColor(),
        )
        val colors2 = intArrayOf(
            SkColor4f(0f, 0f, 0f, 1f).toSkColor(),
            SkColor4f(0f, 0f, 0f, 0f).toSkColor(),
        )

        val cx = 200f
        val cy = 200f
        val radius = 150f
        val center = SkPoint(cx, cy)
        val tm = SkTileMode.kClamp

        // Two columns -- in upstream the only difference is the
        // Interpolation::InPremul flag. We don't yet model that, so
        // both columns render identically. We still iterate twice to
        // match the destination layout (the right column is just a
        // 400-px translation).
        for (iteration in 0 until 2) {
            val paint1 = SkPaint().apply {
                shader = SkSweepGradient.Make(center, sweepColors, null, tm)
                isDither = dither
            }
            val paint2 = SkPaint().apply {
                shader = SkRadialGradient.Make(center, radius, colors1, null, tm)
                isDither = dither
            }
            val paint3 = SkPaint().apply {
                shader = SkRadialGradient.Make(center, radius, colors2, null, tm)
                isDither = dither
            }

            c.drawCircle(cx, cy, radius, paint1)
            c.drawCircle(cx, cy, radius, paint3)
            c.drawCircle(cx, cy, radius, paint2)

            c.translate(400f, 0f)
        }
    }
}
