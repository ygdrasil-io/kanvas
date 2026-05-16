package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.graphiks.math.SkColor4f
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * D2.4.c.4 verification suite for
 * [SkBuiltinShaderEffectsIntrinsicsGeometric]. Spot-checks the
 * GLSL geometric-function family at known sample points.
 *
 * **Sampling note** — each entry takes the local-space `(lx, ly)`
 * + four uniforms `(xScale, xBias, yScale, yBias)`. The template's
 * remap produces `p.x = lx*xScale + xBias` and `p.y = (1 - lx)*xScale + xBias`,
 * so to test the `p.y`-dependent entries (e.g. `length(p)`) we
 * pick `lx` and uniforms that put `p.x` and `p.y` at known values.
 */
class SkBuiltinShaderEffectsIntrinsicsGeometricTest {

    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsIntrinsicsGeometric.registerAll()
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
    fun `every geometric hash resolves to a non-null effect`() {
        for (entry in SkBuiltinShaderEffectsIntrinsicsGeometric.GEOMETRIC_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d(entry.fn, requireES3 = false)
            val result = SkRuntimeEffect.MakeForShader(sksl)
            assertNotNull(result.effect, "MakeForShader returned null for ${entry.fn} ; error=${result.errorText}")
        }
    }

    @Test
    fun `length(x) at x=-3 returns 3`() {
        val out = shadeAt("length(x)",
            xScale = 0f, xBias = -3f,
            yScale = 1f / 6f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "length(-3) = 3 → /6 = 0.5")
    }

    @Test
    fun `length(p) at lx=0_5 returns sqrt(0_5)`() {
        // xScale = 1, xBias = 0 ⇒ p.x = lx = 0.5, p.y = 1 - lx = 0.5.
        // length(p) = sqrt(0.25 + 0.25) = sqrt(0.5) ≈ 0.707.
        val out = shadeAt("length(p)",
            xScale = 1f, xBias = 0f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(sqrt(0.5f), out.fR, msg = "length(p) at p=(0.5, 0.5)")
    }

    @Test
    fun `dot(x, 2) at x=0_25 returns 0_5`() {
        val out = shadeAt("dot(x, 2)",
            xScale = 0f, xBias = 0.25f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "dot(0.25, 2) = 0.5")
    }

    @Test
    fun `cross_x at p=(0_5, 0_5) returns -0_75`() {
        // .x = px*py - 1 = 0.5*0.5 - 1 = -0.75 ; remap : 0.5 + (-0.75)*0.5 = 0.125.
        val out = shadeAt("cross(p.xy1, p.y1x).x",
            xScale = 1f, xBias = 0f,
            yScale = 0.5f, yBias = 0.5f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.125f, out.fR, msg = "cross.x at (0.5, 0.5) = -0.75 → 0.5 + (-0.75)*0.5 = 0.125")
    }

    @Test
    fun `normalize(x) at x=-3 returns -1`() {
        // sign(-3) = -1 ; yScale = 0.5, yBias = 0.5 ⇒ -1 * 0.5 + 0.5 = 0.
        val out = shadeAt("normalize(x)",
            xScale = 0f, xBias = -3f,
            yScale = 0.5f, yBias = 0.5f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0f, out.fR, msg = "sign(-3)*0.5+0.5 = 0")
    }

    @Test
    fun `faceforward at px=-1 returns 1`() {
        // px = -1 < 0 → returns 1 (the N).
        val out = shadeAt("faceforward(v1, p.x0, v1.x0).x",
            xScale = 0f, xBias = -1f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(1f, out.fR, msg = "faceforward at px=-1 returns 1")
    }

    @Test
    fun `faceforward at px=1 returns -1`() {
        // px = 1 ≥ 0 → returns -1 ; yScale = 0.5, yBias = 0.5 ⇒ 0.
        val out = shadeAt("faceforward(v1, p.x0, v1.x0).x",
            xScale = 0f, xBias = 1f,
            yScale = 0.5f, yBias = 0.5f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0f, out.fR, msg = "faceforward at px=1 returns -1")
    }

    @Test
    fun `reflect_x equals px`() {
        // reflect(p.x1, v1.0x).x = p.x. Use xScale = 0, xBias = 0.5
        // ⇒ px = 0.5 ; yScale = 1, yBias = 0 ⇒ output = 0.5.
        val out = shadeAt("reflect(p.x1, v1.0x).x",
            xScale = 0f, xBias = 0.5f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0.5f, out.fR, msg = "reflect.x = px = 0.5")
    }

    @Test
    fun `refract_x at eta=2 returns 0 (total internal reflection)`() {
        // |eta| = 2 > 1 ⇒ k < 0 ⇒ refract = (0, 0). Output : 0.
        val out = shadeAt("refract(v1.x0, v1.0x, x).x",
            xScale = 0f, xBias = 2f,
            yScale = 1f, yBias = 0f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(0f, out.fR, msg = "refract.x at |eta|>1 = 0")
    }

    @Test
    fun `refract_y at eta=0_5 returns -sqrt(0_75)`() {
        // eta = 0.5, k = 1 - 0.25 = 0.75. .y = -sqrt(0.75).
        // yScale = 0.5, yBias = 0.5 ⇒ output = (-sqrt(0.75)) * 0.5 + 0.5 ≈ 0.067.
        val expected = -sqrt(0.75f) * 0.5f + 0.5f
        val out = shadeAt("refract(v1.x0, v1.0x, x).y",
            xScale = 0f, xBias = 0.5f,
            yScale = 0.5f, yBias = 0.5f,
            lx = 0.5f, ly = 0.5f)
        assertNearly(expected, out.fR, msg = "refract.y at eta=0.5 = -sqrt(0.75)")
    }
}
