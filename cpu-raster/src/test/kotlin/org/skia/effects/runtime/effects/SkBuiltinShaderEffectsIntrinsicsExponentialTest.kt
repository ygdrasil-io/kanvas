package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.math.SkColor4f
import kotlin.math.abs

/**
 * D2.4.c.2 verification suite for
 * [SkBuiltinShaderEffectsIntrinsicsExponential]. Same shape as
 * `SkBuiltinShaderEffectsIntrinsicsTrigTest` but spot-checks the
 * 10 exponential intrinsics. Each test pins one math identity at
 * a single sample point ; uniforms are chosen so the expected
 * output lands cleanly in `[0, 1]` (no 8-bit channel clamp drift).
 */
class SkBuiltinShaderEffectsIntrinsicsExponentialTest {

    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsIntrinsicsExponential.registerAll()
    }

    private fun shadeAt(
        fn: String,
        xScale: Float, xBias: Float, yScale: Float, yBias: Float,
        lx: Float, ly: Float,
    ): SkColor4f {
        val sksl = SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d(fn, requireES3 = false)
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect
            ?: error("MakeForShader returned null for : $fn")
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("xScale").set(xScale)
        builder.uniform("xBias").set(xBias)
        builder.uniform("yScale").set(yScale)
        builder.uniform("yBias").set(yBias)
        val shader = builder.makeShader()
            ?: error("makeShader returned null for : $fn")
        return SkColor4f.FromColor(shader.sampleAtLocal(lx, ly))
    }

    private fun assertNearly(
        expected: Float, actual: Float,
        tol: Float = 2f / 255f, msg: String = "",
    ) {
        assertTrue(
            abs(expected - actual) < tol,
            "$msg : expected=$expected actual=$actual diff=${abs(expected - actual)} tol=$tol",
        )
    }

    @Test
    fun `every exponential hash resolves to a non-null effect`() {
        for (entry in SkBuiltinShaderEffectsIntrinsicsExponential.EXPONENTIAL_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d(entry.fn, requireES3 = false)
            val result = SkRuntimeEffect.MakeForShader(sksl)
            assertNotNull(result.effect, "MakeForShader returned null for ${entry.fn} ; error=${result.errorText}")
        }
    }

    @Test
    fun `pow(x, 3) at x=2 returns 8`() {
        // y = 2^3 = 8 ; yScale = 1/16 ⇒ output = 0.5.
        val out = shadeAt("pow(x, 3)",
            xScale = 0f, xBias = 2f,
            yScale = 1f / 16f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "pow(2,3)/16 = 0.5")
    }

    @Test
    fun `pow(0_9, x) at x=0 returns 1`() {
        // 0.9^0 = 1.
        val out = shadeAt("pow(0.9, x)",
            xScale = 0f, xBias = 0f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(1f, out.fR, msg = "0.9^0 = 1")
    }

    @Test
    fun `exp(0) returns 1`() {
        val out = shadeAt("exp(x)",
            xScale = 0f, xBias = 0f,
            yScale = 1f, yBias = 0f,
            lx = 0.25f, ly = 0.25f)
        assertNearly(1f, out.fR, msg = "exp(0) = 1")
    }

    @Test
    fun `log(e) returns 1`() {
        // log(e) = 1 ; SkSL log = natural log.
        val e = kotlin.math.E.toFloat()
        val out = shadeAt("log(x)",
            xScale = 0f, xBias = e,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(1f, out.fR, msg = "log(e) = 1")
    }

    @Test
    fun `exp2(3) returns 8`() {
        // 2^3 = 8 ; yScale = 1/16 ⇒ 0.5.
        val out = shadeAt("exp2(x)",
            xScale = 0f, xBias = 3f,
            yScale = 1f / 16f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "exp2(3)/16 = 0.5")
    }

    @Test
    fun `log2(8) returns 3`() {
        // log2(8) = 3 ; yScale = 1/6 ⇒ 0.5.
        val out = shadeAt("log2(x)",
            xScale = 0f, xBias = 8f,
            yScale = 1f / 6f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "log2(8)/6 = 0.5")
    }

    @Test
    fun `sqrt(16) returns 4`() {
        // sqrt(16) = 4 ; yScale = 1/8 ⇒ 0.5.
        val out = shadeAt("sqrt(x)",
            xScale = 0f, xBias = 16f,
            yScale = 1f / 8f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "sqrt(16)/8 = 0.5")
    }

    @Test
    fun `inversesqrt(4) returns 0_5`() {
        // 1 / sqrt(4) = 1 / 2 = 0.5.
        val out = shadeAt("inversesqrt(x)",
            xScale = 0f, xBias = 4f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "1/sqrt(4) = 0.5")
    }
}
