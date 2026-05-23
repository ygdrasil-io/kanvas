package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkPaint
import kotlin.math.sin

/**
 * Port of Skia's `gm/runtimeshader.cpp::SpiralRT` (`spiral_rt` GM,
 * 512 × 512, benchmark + animation).
 *
 * Renders a polar-coordinate conic spiral between two `layout(color)`
 * uniforms (`in_colors0` = red, `in_colors1` = green). Upstream animates
 * `rad_scale` via `sin(secs * 0.5 + 2.0) / 5`; here it is frozen at
 * `sin(2.0f) / 5 ≈ 0.0727` (the static frame with time = 0 seconds).
 *
 * **SkSL source** : [SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL] (already
 * registered in the dispatch table — impl is [SkBuiltinShaderEffectsSimple.SpiralRTImpl]).
 *
 * C++ original: `gm/runtimeshader.cpp:192-225`.
 */
public class SpiralRTGM : GM() {

    init {
        // Trigger object-init so the impl is registered before MakeForShader.
        SkBuiltinShaderEffectsSimple
    }

    override fun getName(): String = "spiral_rt"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    private val effect: SkRuntimeEffect by lazy {
        val res = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL)
        requireNotNull(res.effect) { "SpiralRTGM SkSL failed: ${res.errorText}" }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val builder = SkRuntimeEffectBuilder(effect)

        // Freeze animation at t=0: sin(0*0.5+2.0)/5
        builder.uniform("rad_scale").set(sin(2.0f) / 5f)
        builder.uniform("in_center").set(floatArrayOf(256f, 256f))
        builder.uniform("in_colors0").set(SkColor4f(1f, 0f, 0f, 1f))
        builder.uniform("in_colors1").set(SkColor4f(0f, 1f, 0f, 1f))

        val paint = SkPaint()
        paint.shader = builder.makeShader()
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 512f, 512f), paint)
    }
}
