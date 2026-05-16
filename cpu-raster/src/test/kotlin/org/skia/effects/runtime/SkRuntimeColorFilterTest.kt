package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkColor4f
import org.skia.foundation.SkColorFilter
import org.skia.math.SkPoint
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * D2.2 verification suite for [SkRuntimeColorFilter].
 *
 * Direct unit tests : we build the binding via
 * [SkRuntimeEffect.makeColorFilter] and call `filterColor4f`.
 * End-to-end pipeline integration via paint.colorFilter is
 * already covered by the existing `SkColorFilter` raster tests
 * — D2.2 only tests the binding layer.
 */
class SkRuntimeColorFilterTest {

    @AfterEach fun cleanup() { SkRuntimeEffectDispatch.clearForTest() }

    private val identitySksl = """
        half4 main(vec4 c) { return c; }
    """.trimIndent()

    @Test
    fun `makeColorFilter returns null for a shader-shaped SkSL`() {
        val sksl = "half4 main(vec2 p) { return vec4(0); }"
        SkRuntimeEffectDispatch.register(sksl) { stubIdentity() }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        assertNull(effect.makeColorFilter(null), "shader effect can't make a color filter")
    }

    @Test
    fun `makeColorFilter rejects wrong-type children`() {
        val sksl = """
            uniform shader child;
            half4 main(vec4 c) { return c; }
        """.trimIndent()
        // SkSL declares a SHADER child but we requested a color filter
        // — the makeColorFilter call must either reject the children
        // wiring (via the require-check) or return null cleanly.
        SkRuntimeEffectDispatch.register(sksl) { stubIdentity() }
        val effect = SkRuntimeEffect.MakeForColorFilter(sksl).effect!!
        var threw = false
        try {
            effect.makeColorFilter(null, arrayOf<SkColorFilter?>(null))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw,
            "must reject a colorFilter binding with shader-typed child slot")
    }

    @Test
    fun `RuntimeColorFilter forwards srcColor unchanged for an identity impl`() {
        SkRuntimeEffectDispatch.register(identitySksl) { stubIdentity() }
        val effect = SkRuntimeEffect.MakeForColorFilter(identitySksl).effect!!
        val cf = effect.makeColorFilter(null)!!
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0.8f)
        val out = cf.filterColor4f(src)
        // Identity impl returns src unchanged.
        assertEquals(src.fR, out.fR, 1e-5f)
        assertEquals(src.fG, out.fG, 1e-5f)
        assertEquals(src.fB, out.fB, 1e-5f)
        assertEquals(src.fA, out.fA, 1e-5f)
    }

    @Test
    fun `RuntimeColorFilter applies an invert-RGB transformation`() {
        // Impl that returns `vec4(1 - c.r, 1 - c.g, 1 - c.b, c.a)`.
        SkRuntimeEffectDispatch.register(identitySksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
                override val children: List<SkRuntimeEffect.Child> = emptyList()
                override val flags: Int = 0
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f {
                    val s = srcColor!!
                    return SkColor4f(1f - s.fR, 1f - s.fG, 1f - s.fB, s.fA)
                }
            }
        }
        val effect = SkRuntimeEffect.MakeForColorFilter(identitySksl).effect!!
        val cf = effect.makeColorFilter(null)!!
        val src = SkColor4f(1f, 0f, 0f, 1f)  // red
        val out = cf.filterColor4f(src)
        assertEquals(0f, out.fR, 1e-5f)
        assertEquals(1f, out.fG, 1e-5f)
        assertEquals(1f, out.fB, 1e-5f)
        assertEquals(1f, out.fA, 1e-5f)
    }

    @Test
    fun `isAlphaUnchanged reads the kAlphaUnchanged_Flag flag`() {
        // Two impls : same SkSL, but we re-register between calls
        // because flag = 0 vs flag = kAlphaUnchanged_Flag are distinct.
        SkRuntimeEffectDispatch.register(identitySksl) {
            object : SkRuntimeImpl {
                override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
                override val children: List<SkRuntimeEffect.Child> = emptyList()
                override val flags: Int = SkRuntimeEffect.kAlphaUnchanged_Flag
                override fun shade(
                    coords: SkPoint?,
                    srcColor: SkColor4f?,
                    dstColor: SkColor4f?,
                    uniforms: ByteBuffer,
                    children: Array<ChildResolver>,
                ): SkColor4f = srcColor!!
            }
        }
        val effect = SkRuntimeEffect.MakeForColorFilter(identitySksl).effect!!
        val cf = effect.makeColorFilter(null)!!
        assertTrue(cf.isAlphaUnchanged(),
            "isAlphaUnchanged() should reflect the impl's kAlphaUnchanged_Flag")
    }

    private fun stubIdentity() = object : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = 0
        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = srcColor ?: SkColor4f.kBlack
    }
}
