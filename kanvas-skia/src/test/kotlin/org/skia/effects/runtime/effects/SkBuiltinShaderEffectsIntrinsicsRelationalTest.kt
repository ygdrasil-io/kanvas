package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.foundation.SkColor4f

/**
 * D2.4.c.6 verification suite for
 * [SkBuiltinShaderEffectsIntrinsicsRelational].
 *
 * Verifies :
 *  1. Each of the 18 SkSL hashes resolves to a non-null effect.
 *  2. Per-cell colour output matches the expected `(cmpX, cmpY)`
 *    truth values at known sample points (one per quadrant of
 *    interest).
 *  3. The float and int variants produce identical outputs at
 *    integer-valued sample points.
 */
class SkBuiltinShaderEffectsIntrinsicsRelationalTest {

    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsIntrinsicsRelational.registerAll()
    }

    private fun shadeAt(
        type: String, fn: String,
        v1: IntArray = intArrayOf(-2, -2),
        lx: Float, ly: Float,
    ): SkColor4f {
        val sksl = SkBuiltinShaderEffectsIntrinsicsRelational.makeBvecSksl(type, fn)
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect
            ?: error("MakeForShader returned null for : $fn")
        val builder = SkRuntimeEffectBuilder(effect)
        if (type == "int") {
            builder.uniform("v1").set(v1)
        } else {
            builder.uniform("v1").set(floatArrayOf(v1[0].toFloat(), v1[1].toFloat()))
        }
        val shader = builder.makeShader()
            ?: error("makeShader returned null for : $fn")
        return SkColor4f.FromColor(shader.sampleAtLocal(lx, ly))
    }

    @Test
    fun `every relational hash resolves`() {
        for (entry in SkBuiltinShaderEffectsIntrinsicsRelational.RELATIONAL_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsRelational.makeBvecSksl(entry.type, entry.fn)
            assertNotNull(SkRuntimeEffect.MakeForShader(sksl).effect,
                "MakeForShader returned null for ${entry.type} : ${entry.fn}")
        }
    }

    @Test
    fun `lessThan at p=(-3, -3) gives true,true`() {
        // p = (-3, -3) lands in lx, ly < 0.33 quadrant — both red and green.
        val out = shadeAt("float", "lessThan(p, v1)", lx = 0.1f, ly = 0.1f)
        assertEquals(1f, out.fR, 0.01f)
        assertEquals(1f, out.fG, 0.01f)
    }

    @Test
    fun `lessThan at p=(-2, -2) gives false,false`() {
        // p = (-2, -2) — middle bin (lx, ly ∈ [0.33, 0.66)).
        val out = shadeAt("float", "lessThan(p, v1)", lx = 0.5f, ly = 0.5f)
        assertEquals(0f, out.fR, 0.01f)
        assertEquals(0f, out.fG, 0.01f)
    }

    @Test
    fun `equal at p=(-2, -2) gives true,true`() {
        // p = v1 = (-2, -2) → equal everywhere.
        val out = shadeAt("float", "equal(p, v1)", lx = 0.5f, ly = 0.5f)
        assertEquals(1f, out.fR, 0.01f)
        assertEquals(1f, out.fG, 0.01f)
    }

    @Test
    fun `equal at p=(-1, -3) gives false,false`() {
        val out = shadeAt("float", "equal(p, v1)", lx = 0.9f, ly = 0.1f)
        assertEquals(0f, out.fR, 0.01f)
        assertEquals(0f, out.fG, 0.01f)
    }

    @Test
    fun `int variant matches float variant at integer sample points`() {
        val outFloat = shadeAt("float", "lessThan(p, v1)", lx = 0.1f, ly = 0.5f)
        val outInt = shadeAt("int", "lessThan(int2(p), v1)", lx = 0.1f, ly = 0.5f)
        assertEquals(outFloat.fR, outInt.fR, 0.01f, "R mismatch float vs int variant")
        assertEquals(outFloat.fG, outInt.fG, 0.01f, "G mismatch float vs int variant")
    }

    @Test
    fun `any(equal) at p=(-1, -2) gives true,true (broadcast)`() {
        // p.x = -1 ≠ -2 ; p.y = -2 == -2 ⇒ any = true. Broadcast :
        // both R and G should be 1.
        val out = shadeAt("float", "bool2(any(equal(p, v1)))", lx = 0.9f, ly = 0.5f)
        assertEquals(1f, out.fR, 0.01f, "any.x broadcast")
        assertEquals(1f, out.fG, 0.01f, "any.y broadcast")
    }

    @Test
    fun `all(equal) at p=(-1, -2) gives false,false`() {
        // p.x ≠ -2 ⇒ all = false. Broadcast both 0.
        val out = shadeAt("float", "bool2(all(equal(p, v1)))", lx = 0.9f, ly = 0.5f)
        assertEquals(0f, out.fR, 0.01f)
        assertEquals(0f, out.fG, 0.01f)
    }

    @Test
    fun `not(equal) is the inverse of equal`() {
        for ((lx, ly) in listOf(0.1f to 0.1f, 0.5f to 0.5f, 0.9f to 0.5f)) {
            val outE = shadeAt("float", "equal(p, v1)", lx = lx, ly = ly)
            val outN = shadeAt("float", "not(equal(p, v1))", lx = lx, ly = ly)
            assertEquals(1f - outE.fR, outN.fR, 0.01f, "R inversion at ($lx, $ly)")
            assertEquals(1f - outE.fG, outN.fG, 0.01f, "G inversion at ($lx, $ly)")
        }
    }
}
