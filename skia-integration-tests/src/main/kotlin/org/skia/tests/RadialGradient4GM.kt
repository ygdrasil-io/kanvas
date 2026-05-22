package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp::RadialGradient4GM`
 * (`radial_gradient4` / `radial_gradient4_nodither`, 500 x 500).
 *
 * 500x500 drawRect filled with a 5-stop radial gradient
 * (red, red, white, white, red) at positions `(0, .4, .4, .8, .8)`,
 * centre `(250, 250)`, radius 250. Repeated-position stops force a
 * hard band transition. Paint is antialiased.
 *
 * C++ original :
 * ```cpp
 * SkISize getISize() override { return {500, 500}; }
 * void onOnceBeforeDraw() override {
 *     const SkPoint center = { 250, 250 };
 *     const SkScalar kRadius = 250;
 *     const SkColor4f colors[] = {
 *         SkColors::kRed, SkColors::kRed, SkColors::kWhite,
 *         SkColors::kWhite, SkColors::kRed };
 *     const SkScalar pos[] = { 0, .4f, .4f, .8f, .8f };
 *     fShader = SkShaders::RadialGradient(center, kRadius,
 *                                         {{colors, pos, SkTileMode::kClamp}, {}});
 * }
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setDither(fDither);
 *     paint.setShader(fShader);
 *     canvas->drawRect(SkRect::MakeWH(500, 500), paint);
 * }
 * ```
 */
public class RadialGradient4GM(
    private val dither: Boolean = true,
) : GM() {

    private var shader: SkShader? = null

    override fun getName(): String =
        if (dither) "radial_gradient4" else "radial_gradient4_nodither"

    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onOnceBeforeDraw() {
        val center = SkPoint(250f, 250f)
        val kRadius = 250f
        val colors = intArrayOf(
            SkColor4f.kRed.toSkColor(),
            SkColor4f.kRed.toSkColor(),
            SkColor4f.kWhite.toSkColor(),
            SkColor4f.kWhite.toSkColor(),
            SkColor4f.kRed.toSkColor(),
        )
        val pos = floatArrayOf(0f, 0.4f, 0.4f, 0.8f, 0.8f)
        shader = SkRadialGradient.Make(
            center = center,
            radius = kRadius,
            colors = colors,
            positions = pos,
            tileMode = SkTileMode.kClamp,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            isDither = dither
            this.shader = this@RadialGradient4GM.shader
        }
        c.drawRect(SkRect.MakeWH(500f, 500f), paint)
    }
}
