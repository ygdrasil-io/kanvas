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
 * D2.4.c.3 verification suite for
 * [SkBuiltinShaderEffectsIntrinsicsCommon]. Spot-checks the GLSL
 * common-function family math at known sample points :
 *  - `abs` / `sign` / `floor` / `ceil` / `fract`
 *  - `mod` (GLSL semantics, not C `%`)
 *  - `min` / `max` / `clamp` / `saturate`
 *  - `mix` / `step` / `smoothstep`
 *  - `floor(p).y` / `ceil(p).y` (the only entries that consume
 *    `p.y` rather than `x` / `p.x`)
 *
 * Entries that just permute the SkSL surface (e.g. `min(x, 1)` vs
 * `min(p, 1).x`) collapse to identical math at any sample point ;
 * we verify hash resolution for all 31 entries via the batch test
 * but only spot-check one variant of each redundant family.
 */
class SkBuiltinShaderEffectsIntrinsicsCommonTest {

    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsIntrinsicsCommon.registerAll()
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
    fun `every common hash resolves to a non-null effect`() {
        for (entry in SkBuiltinShaderEffectsIntrinsicsCommon.COMMON_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d(entry.fn, requireES3 = false)
            val result = SkRuntimeEffect.MakeForShader(sksl)
            assertNotNull(result.effect, "MakeForShader returned null for ${entry.fn} ; error=${result.errorText}")
        }
    }

    @Test
    fun `abs(-3) returns 3`() {
        // y = abs(-3) = 3 ; yScale = 1/6 ⇒ output = 0.5
        val out = shadeAt("abs(x)",
            xScale = 0f, xBias = -3f,
            yScale = 1f / 6f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "abs(-3)/6 = 0.5")
    }

    @Test
    fun `sign(-2) returns -1`() {
        // y = sign(-2) = -1 ; remap : yScale = 0.5, yBias = 0.5 ⇒ output = 0.
        val out = shadeAt("sign(x)",
            xScale = 0f, xBias = -2f,
            yScale = 0.5f, yBias = 0.5f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0f, out.fR, msg = "sign(-2)*0.5+0.5 = 0")
    }

    @Test
    fun `floor(2_7) returns 2`() {
        // y = floor(2.7) = 2 ; yScale = 1/4 ⇒ 0.5
        val out = shadeAt("floor(x)",
            xScale = 0f, xBias = 2.7f,
            yScale = 1f / 4f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "floor(2.7)/4 = 0.5")
    }

    @Test
    fun `ceil(2_3) returns 3`() {
        val out = shadeAt("ceil(x)",
            xScale = 0f, xBias = 2.3f,
            yScale = 1f / 6f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "ceil(2.3)/6 = 0.5")
    }

    @Test
    fun `fract(2_5) returns 0_5`() {
        val out = shadeAt("fract(x)",
            xScale = 0f, xBias = 2.5f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "fract(2.5) = 0.5")
    }

    @Test
    fun `mod(5, 2) returns 1`() {
        // GLSL mod(5, 2) = 5 - 2*floor(5/2) = 5 - 2*2 = 1.
        val out = shadeAt("mod(x, 2)",
            xScale = 0f, xBias = 5f,
            yScale = 0.5f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "mod(5, 2) = 1 → /2 = 0.5")
    }

    @Test
    fun `mod(p, -2)_x for x=1 returns -1 (GLSL semantics)`() {
        // GLSL mod(1, -2) = 1 - (-2)*floor(-0.5) = 1 - (-2)*(-1) = -1.
        // Output : -1 * 0.5 + 0.5 = 0.
        val out = shadeAt("mod(p, -2).x",
            xScale = 0f, xBias = 1f,
            yScale = 0.5f, yBias = 0.5f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0f, out.fR, msg = "GLSL mod(1, -2) = -1 → 0.5*(-1)+0.5 = 0")
    }

    @Test
    fun `min(0_5, 1) returns 0_5`() {
        val out = shadeAt("min(x, 1)",
            xScale = 0f, xBias = 0.5f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "min(0.5, 1) = 0.5")
    }

    @Test
    fun `max(2, 1) returns 2`() {
        val out = shadeAt("max(x, 1)",
            xScale = 0f, xBias = 2f,
            yScale = 0.25f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "max(2, 1)/4 = 0.5")
    }

    @Test
    fun `clamp(3, 1, 2) returns 2`() {
        val out = shadeAt("clamp(x, 1, 2)",
            xScale = 0f, xBias = 3f,
            yScale = 0.25f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "clamp(3, 1, 2)/4 = 0.5")
    }

    @Test
    fun `saturate(1_5) returns 1`() {
        val out = shadeAt("saturate(x)",
            xScale = 0f, xBias = 1.5f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(1f, out.fR, msg = "saturate(1.5) = 1")
    }

    @Test
    fun `mix(1, 2, 0_5) returns 1_5`() {
        // mix(1, 2, 0.5) = 1*0.5 + 2*0.5 = 1.5 ; /3 = 0.5.
        val out = shadeAt("mix(1, 2, x)",
            xScale = 0f, xBias = 0.5f,
            yScale = 1f / 3f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "mix(1, 2, 0.5)/3 = 0.5")
    }

    @Test
    fun `step(1, 0_5) returns 0`() {
        val out = shadeAt("step(1, x)",
            xScale = 0f, xBias = 0.5f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0f, out.fR, msg = "step(1, 0.5) = 0 (since 0.5 < 1)")
    }

    @Test
    fun `smoothstep(1, 2, 1_5) returns 0_5`() {
        // smoothstep(1, 2, 1.5) = Hermite at t=0.5 = 0.5*0.5*(3 - 1) = 0.5
        val out = shadeAt("smoothstep(1, 2, x)",
            xScale = 0f, xBias = 1.5f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "smoothstep(1, 2, 1.5) = 0.5")
    }

    @Test
    fun `floor(p)_y reads p_y not x`() {
        // For lx = 0, p.x = 0 * 4 + 0 = 0 ; p.y = (1 - 0) * 4 + 0 = 4.
        // floor(p).y = floor(4) = 4 ; yScale = 1/8 ⇒ 0.5.
        val out = shadeAt("floor(p).y",
            xScale = 4f, xBias = 0f,
            yScale = 1f / 8f, yBias = 0f,
            lx = 0f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "floor(p.y) = floor(4) = 4 → /8 = 0.5")
    }
}
