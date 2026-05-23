package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

/**
 * Port of Skia's `gm/gradients.cpp::gradients_alpha_many_stops`
 * (`DEF_SIMPLE_GM`, 100 × 100).
 *
 * From upstream comment: "From https://issues.chromium.org/issues/401546700,
 * this encounters Graphite's storage buffer option for storing gradient
 * buffers AND uses colors that emphasize premul vs. unpremul handling of
 * the color data."
 *
 * Renders a 13-stop linear gradient that fades a dark gray from fully
 * opaque to fully transparent, drawn vertically over a 50%-gray background.
 * All stops share the same RGB value (34/255 ≈ 0.133) but have decreasing
 * alpha values.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(gradients_alpha_many_stops, canvas, 100, 100) {
 *     static const SkPoint kPts[] = {{0.f, 0.f}, {0.f, 100.f}};
 *     static const float kPos[] = {0.f, 0.19f, 0.34f, 0.47f, 0.565f, 0.65f,
 *                                  0.73f, 0.802f, 0.861f, 0.91f, 0.952f, 0.982f, 1.f};
 *     static constexpr float kG = 34 / 255.f;
 *     static const SkColor4f kColors[] = {
 *         {kG, kG, kG, 1.f},    {kG, kG, kG, 0.738f},
 *         {kG, kG, kG, 0.541f}, {kG, kG, kG, 0.382f},
 *         ...
 *         {kG, kG, kG, 0.f}};
 *     canvas->clear(SkColor4f{0.5f, 0.5f, 0.5f, 1.f});
 *     SkPaint paint;
 *     paint.setShader(SkShaders::LinearGradient(kPts, {{kColors, kPos, SkTileMode::kClamp}, {}}));
 *     canvas->drawPaint(paint);
 * }
 * ```
 */
public class GradientsAlphaManyStopsGM : GM() {

    override fun getName(): String = "gradients_alpha_many_stops"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Clear to 50% gray (upstream: canvas->clear(SkColor4f{0.5f, 0.5f, 0.5f, 1.f}))
        c.clear(SkColorSetARGB(0xFF, 128, 128, 128))

        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(0f, 100f)

        val positions = floatArrayOf(
            0f, 0.19f, 0.34f, 0.47f, 0.565f, 0.65f,
            0.73f, 0.802f, 0.861f, 0.91f, 0.952f, 0.982f, 1f,
        )

        // kG = 34 / 255.f ≈ 0.133 → byte value 34
        val kGByte = 34
        val alphas = intArrayOf(
            255,  // 1.000
            188,  // 0.738 × 255 ≈ 188
            138,  // 0.541 × 255 ≈ 138
             97,  // 0.382 × 255 ≈  97
             71,  // 0.278 × 255 ≈  71
             49,  // 0.194 × 255 ≈  49
             32,  // 0.126 × 255 ≈  32
             19,  // 0.075 × 255 ≈  19
             11,  // 0.042 × 255 ≈  11
              5,  // 0.021 × 255 ≈   5
              2,  // 0.008 × 255 ≈   2
              1,  // 0.002 × 255 ≈   1
              0,  // 0.000
        )

        val colors = IntArray(positions.size) { i ->
            SkColorSetARGB(alphas[i], kGByte, kGByte, kGByte)
        }

        val paint = SkPaint()
        paint.shader = SkLinearGradient.Make(p0, p1, colors, positions, SkTileMode.kClamp)
        c.drawPaint(paint)
    }
}
