package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces

/**
 * Port of Skia's `gm/runtimeshader.cpp::LinearGradientRT` (`linear_gradient_rt`
 * GM, 266 × 143).
 *
 * Draws two horizontal colour-gradient strips that compare:
 *  - **Top half of each strip** (`p.y < 32`): simple `mix` in encoded sRGB
 *    space — the midpoint of the gradient is darker than expected.
 *  - **Bottom half of each strip** (`p.y ≥ 32`): `mix` done in *linear* sRGB
 *    via `toLinearSrgb` / `fromLinearSrgb` intrinsics — the midpoint should
 *    be perceptually neutral (correctly linear).
 *
 * Upstream renders to two separate offscreen surfaces with different colour
 * spaces (null = no-op, then sRGB) so the intrinsics show a difference only on
 * the sRGB surface. Kanvas-skia applies the `toLinearSrgb` / `fromLinearSrgb`
 * conversion unconditionally, so both strips are drawn the same way — the bottom
 * half always shows the linear-corrected gradient.
 *
 * **SkSL impl** : [SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL] /
 * [SkBuiltinShaderEffectsSimple.LinearGradientRTImpl].
 *
 * C++ original: `gm/runtimeshader.cpp:592-644`.
 */
public class LinearGradientRTGM : GM() {

    init {
        SkBuiltinShaderEffectsSimple
    }

    override fun getName(): String = "linear_gradient_rt"
    // Upstream: {256 + 10, 128 + 15} = {266, 143}
    override fun getISize(): SkISize = SkISize.Make(266, 143)

    private val effect: SkRuntimeEffect by lazy {
        val res = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL,
        )
        requireNotNull(res.effect) { "LinearGradientRTGM SkSL failed: ${res.errorText}" }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val builder = SkRuntimeEffectBuilder(effect)
        // Colors from upstream: (0.75, 0.25, 0.0) → (0.0, 0.75, 0.25)
        builder.uniform("in_colors0").set(SkColor4f(0.75f, 0.25f, 0.0f, 1.0f))
        builder.uniform("in_colors1").set(SkColor4f(0.0f, 0.75f, 0.25f, 1.0f))

        val paint = SkPaint()
        paint.shader = builder.makeShader()

        c.save()
        c.clear(SK_ColorWHITE)
        c.translate(5f, 5f)

        // Draw two gradient strips one below the other (64-px height each + 5 gap).
        // The upstream draws to two different surfaces (null CS then sRGB) — we
        // draw twice to the same canvas using offscreen raster surfaces to match
        // the strip layout.
        val info = SkImageInfo.MakeN32Premul(256, 64)
        for (i in 0..1) {
            val surface = c.makeSurface(info) ?: SkSurfaces.Raster(info)!!
            surface.canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 64f), paint)
            c.drawImage(
                surface.makeImageSnapshot(),
                0f,
                0f,
                SkSamplingOptions.Default,
                null,
            )
            c.translate(0f, 64f + 5f)
        }

        c.restore()
    }
}
