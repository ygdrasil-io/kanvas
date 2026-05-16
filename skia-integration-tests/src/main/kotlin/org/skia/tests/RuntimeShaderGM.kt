package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/runtimeshader.cpp::SimpleRT` (`runtime_shader` GM,
 * 512 × 256, also a benchmark).
 *
 * Builds a [SkRuntimeEffect] from the upstream SkSL :
 *
 * ```glsl
 * uniform half4 gColor;
 * half4 main(float2 p) {
 *     return half4(p*(1.0/255), gColor.b, 1);
 * }
 * ```
 *
 * Sets `gColor = (1, 0, 0, 1)` (so blue channel = 0 → output blue is
 * always zero, and red channel comes from `p.y` not the uniform), then
 * draws a 256 × 256 rect with the shader rotated 90° around `(128, 128)`
 * via a local matrix.
 *
 * The upstream SkSL is hand-ported into [SkBuiltinShaderEffectsSimple]'s
 * dispatch registry, so [SkRuntimeEffect.MakeForShader] resolves it
 * without an SkSL parser.
 *
 * Reference image: `runtime_shader.png`, 512 × 256.
 */
public class RuntimeShaderGM : GM() {

    override fun getName(): String = "runtime_shader"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    private val sksl: String = """
        uniform half4 gColor;

        half4 main(float2 p) {
            return half4(p*(1.0/255), gColor.b, 1);
        }
    """

    private val effect: SkRuntimeEffect by lazy {
        val res = SkRuntimeEffect.MakeForShader(sksl)
        requireNotNull(res.effect) { "RuntimeShaderGM SkSL failed to compile : ${res.errorText}" }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val builder = SkRuntimeEffectBuilder(effect)
        val localM = SkMatrix.MakeRotate(90f, 128f, 128f)
        builder.uniform("gColor").set(SkColor4f(1f, 0f, 0f, 1f))

        val p = SkPaint()
        p.shader = builder.makeShader(localM)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), p)
    }
}
