package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkData
import org.skia.foundation.SkShader
import org.skia.math.SkPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * D2.4.b cluster C verification suite for
 * [SkBuiltinShaderEffectsColorCube] — the 2 colour-cube LUT
 * runtime effects ([SkBuiltinShaderEffectsColorCube.ColorCubeRTImpl]
 * and [SkBuiltinShaderEffectsColorCube.ColorCubeColorFilterRTImpl]).
 *
 * **Behaviour under test** :
 *  - Both impls register their SkSL → impl mapping correctly :
 *    `MakeForShader(COLOR_CUBE_RT_SKSL).effect` and
 *    `MakeForColorFilter(COLOR_CUBE_CF_RT_SKSL).effect` resolve
 *    to non-null effects with the expected reflection.
 *  - The shader impl ([ColorCubeRTImpl]), wired up with a
 *    constant-colour LUT child shader, returns the LUT's
 *    constant verbatim — the lerp collapses when both
 *    `color_cube.eval(coords1)` and `color_cube.eval(coords2)`
 *    return the same constant.
 *  - The premul round-trip is alpha-preserving : feed an opaque
 *    `(0.5, 0.5, 0.5, 1)` colour through, expect the lerped
 *    output back (also `(0.5, 0.5, 0.5, 1)` for a constant
 *    half-grey LUT).
 *  - The shader and color-filter forms produce the **same**
 *    output for the same input + LUT, since the math is
 *    identical (only the entry point differs). The
 *    color-filter form is exercised by directly invoking its
 *    [SkRuntimeImpl.shade] — the binding layer's
 *    [SkRuntimeEffect.makeColorFilter] currently rejects shader
 *    children of color-filter effects ; the shader-as-cf-child
 *    integration lands in the parent worktree.
 *  - Auto-registration : after a
 *    [SkRuntimeEffectDispatch.clearForTest], a fresh
 *    `MakeForShader(COLOR_CUBE_RT_SKSL)` call re-resolves the
 *    impl (the dispatch helper repopulates builtins on first
 *    lookup).
 */
class SkBuiltinShaderEffectsColorCubeTest {

    /**
     * Re-register on every test entry. The `object`'s `init { … }`
     * fires once per JVM, but a sibling test (or one of ours that
     * calls `clearForTest`) wipes the dispatch table — without
     * `@BeforeEach` we'd hit "SkSL not registered" on the next
     * `MakeForShader`. Idempotent : `registerAll` overwrites with
     * the same factories.
     */
    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsColorCube.registerAll()
    }

    // No `@AfterEach cleanup` — we rely on the auto-registration
    // path to repopulate the dispatch table on each MakeFor* call.

    // Standard cube parameters (kSize = 16, matching the upstream
    // GM `gm/runtimeshader.cpp` lines 320 / 403).
    private val rgScale = 15f / 16f      // (kSize - 1) / kSize
    private val rgBias = 0.5f / 16f      // 0.5f / kSize
    private val bScale = 15f             // kSize - 1
    private val invSize = 1f / 16f       // 1f / kSize

    /** Pack 4 floats into the standard 16-byte uniform layout. */
    private fun uniforms(rg: Float, bias: Float, b: Float, inv: Float): SkData {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
        buf.putFloat(rg)
        buf.putFloat(bias)
        buf.putFloat(b)
        buf.putFloat(inv)
        return SkData.MakeWithCopy(buf.array())
    }

    /**
     * Stub shader impl that returns a constant SkColor4f at every
     * sample point. Same pattern as
     * [SkRuntimeShaderTest.constantColorImpl] — registered against
     * a single-byte SkSL placeholder so we can wrap it via
     * [SkRuntimeEffect.makeShader].
     */
    private fun constantColorImpl(c: SkColor4f) = object : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag
        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = c
    }

    /** SkSL stubs for the constant-colour LUT and source shaders. */
    private val constantLutSksl = """
        half4 main(float2 p) { return half4(0); }
    """.trimIndent()
    private val constantSrcSksl = """
        half4 main(float2 p) { return half4(0); }
    """.trimIndent()
    private val constantSrcSksl2 = """
        half4 main(float2 p) { return half4(1); }
    """.trimIndent()

    private fun assertNearly(
        expected: SkColor4f,
        actual: SkColor4f,
        tol: Float = 1e-4f,
        msg: String = "",
    ) {
        val drift = floatArrayOf(
            abs(expected.fR - actual.fR),
            abs(expected.fG - actual.fG),
            abs(expected.fB - actual.fB),
            abs(expected.fA - actual.fA),
        )
        assertTrue(drift.all { it < tol },
            "$msg : expected=$expected, actual=$actual, drift=${drift.toList()}")
    }

    // ─── Effect resolution + reflection ──────────────────────────────

    @Test
    fun `ColorCubeRT MakeForShader resolves to non-null effect with 4 uniforms and 2 children`() {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsColorCube.COLOR_CUBE_RT_SKSL,
        ).effect
        assertNotNull(effect, "MakeForShader returned null for COLOR_CUBE_RT_SKSL")
        assertEquals(4, effect!!.uniforms().size,
            "ColorCubeRT must declare 4 uniforms (rg_scale, rg_bias, b_scale, inv_size)")
        assertEquals(2, effect.children().size,
            "ColorCubeRT must declare 2 children (child, color_cube)")
        assertEquals(16, effect.uniformSize,
            "uniform block must be 16 bytes (4 × float)")
        assertTrue(effect.allowShader(), "must allow shader use")
    }

    @Test
    fun `ColorCubeColorFilterRT MakeForColorFilter resolves with 4 uniforms and 1 child`() {
        val effect = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinShaderEffectsColorCube.COLOR_CUBE_CF_RT_SKSL,
        ).effect
        assertNotNull(effect, "MakeForColorFilter returned null for COLOR_CUBE_CF_RT_SKSL")
        assertEquals(4, effect!!.uniforms().size,
            "ColorCubeColorFilterRT must declare 4 uniforms")
        assertEquals(1, effect.children().size,
            "ColorCubeColorFilterRT must declare 1 child (color_cube)")
        assertEquals(16, effect.uniformSize)
        assertTrue(effect.allowColorFilter(), "must allow color-filter use")
    }

    // ─── ColorCubeRTImpl (shader form) — end-to-end via makeShader ───

    @Test
    fun `ColorCubeRT with constant half-grey LUT returns half-grey for opaque input`() {
        // Source colour = (0.5, 0.5, 0.5, 1).
        // LUT returns constant (0.5, 0.5, 0.5, 1) at every sample.
        // Math reduces to : lerp(half-grey, half-grey, frac) =
        // half-grey ; re-premul leaves it unchanged at α = 1.
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsColorCube.COLOR_CUBE_RT_SKSL,
        ).effect!!

        val srcShader = makeConstantShader(SkColor4f(0.5f, 0.5f, 0.5f, 1f), constantSrcSksl)
        val lutShader = makeConstantShader(SkColor4f(0.5f, 0.5f, 0.5f, 1f), constantLutSksl)
        val u = uniforms(rgScale, rgBias, bScale, invSize)

        val shader = effect.makeShader(u, arrayOf<SkShader?>(srcShader, lutShader))!!
        val packed = shader.sampleAtLocal(8f, 8f)
        val out = SkColor4f.FromColor(packed)

        // Tolerance 1e-2 — the output passes through the
        // 8-bit packed-byte SkColor on the way back out (each
        // channel is rounded to the nearest 1/255).
        assertNearly(SkColor4f(0.5f, 0.5f, 0.5f, 1f), out, tol = 1e-2f)
    }

    @Test
    fun `ColorCubeRT with constant white LUT returns white for any opaque input`() {
        // Even with a non-zero source colour, a constant LUT
        // overrides everything : output should match the LUT.
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsColorCube.COLOR_CUBE_RT_SKSL,
        ).effect!!

        val srcShader = makeConstantShader(SkColor4f(0.3f, 0.7f, 0.2f, 1f), constantSrcSksl)
        val lutShader = makeConstantShader(SkColor4f(1f, 1f, 1f, 1f), constantSrcSksl2)
        val u = uniforms(rgScale, rgBias, bScale, invSize)

        val shader = effect.makeShader(u, arrayOf<SkShader?>(srcShader, lutShader))!!
        val packed = shader.sampleAtLocal(0f, 0f)
        val out = SkColor4f.FromColor(packed)

        assertNearly(SkColor4f(1f, 1f, 1f, 1f), out, tol = 1e-2f)
    }

    // ─── ColorCubeColorFilterRTImpl — direct shade() invocation ──────

    @Test
    fun `ColorCubeColorFilterRT shade with constant half-grey LUT returns half-grey for opaque half-grey input`() {
        val src = SkColor4f(0.5f, 0.5f, 0.5f, 1f)
        val out = invokeColorFilterImpl(
            srcColor = src,
            lut = ChildResolver.Shader { _ -> SkColor4f(0.5f, 0.5f, 0.5f, 1f) },
        )
        assertNearly(src, out, tol = 1e-5f)
    }

    @Test
    fun `ColorCubeColorFilterRT shade with constant LUT returns LUT colour regardless of input`() {
        val lutColour = SkColor4f(0.2f, 0.4f, 0.8f, 1f)
        val cases = listOf(
            SkColor4f(0f, 0f, 0f, 1f),
            SkColor4f(1f, 0f, 0f, 1f),
            SkColor4f(0.7f, 0.3f, 0.1f, 1f),
            SkColor4f(0.5f, 0.5f, 0.5f, 1f),
        )
        for (src in cases) {
            val out = invokeColorFilterImpl(
                srcColor = src,
                lut = ChildResolver.Shader { _ -> lutColour },
            )
            // Constant LUT → output equals LUT colour after the
            // unpremul → cube → re-premul round-trip (α = 1 → no
            // change).
            assertNearly(lutColour, out, tol = 1e-5f,
                msg = "constant LUT must override input $src")
        }
    }

    // ─── Premul round-trip alpha = 0 edge case ───────────────────────

    @Test
    fun `ColorCubeColorFilterRT shade with alpha-zero input falls through unpremul guard`() {
        // unpremul((0,0,0,0)) is defined as (0,0,0,0) per
        // [SkColor4f.unpremul]. After the cube lookup over a
        // constant LUT, the lerp returns the LUT colour, then
        // re-premul multiplies by alpha — so a constant LUT with
        // α = 0 should round-trip to (0,0,0,0).
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 0f)
        val out = invokeColorFilterImpl(
            srcColor = src,
            lut = ChildResolver.Shader { _ -> SkColor4f(0.5f, 0.5f, 0.5f, 0f) },
        )
        // LUT alpha = 0 → re-premul yields (0, 0, 0, 0).
        assertNearly(SkColor4f(0f, 0f, 0f, 0f), out, tol = 1e-5f)
    }

    // ─── Shader vs color-filter parity — same maths, same output ─────

    @Test
    fun `ColorCubeRT and ColorCubeColorFilterRT produce identical output for the same input and LUT`() {
        // Drive both impls directly via their shade() functions
        // with hand-built ChildResolvers, so we compare apples to
        // apples (no 8-bit pack/unpack on the way out).
        val src = SkColor4f(0.7f, 0.3f, 0.1f, 1f)
        val lutColour = SkColor4f(0.2f, 0.6f, 0.4f, 1f)

        val u = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).apply {
            putFloat(rgScale); putFloat(rgBias); putFloat(bScale); putFloat(invSize)
            position(0)
        }

        val lutResolver = ChildResolver.Shader { _ -> lutColour }
        val srcResolver = ChildResolver.Shader { _ -> src }

        // Shader form : src comes from child[0], LUT from child[1].
        val outShader = SkBuiltinShaderEffectsColorCube.ColorCubeRTImpl.shade(
            coords = SkPoint(0f, 0f),
            srcColor = null,
            dstColor = null,
            uniforms = u.duplicate().order(ByteOrder.nativeOrder()),
            children = arrayOf(srcResolver, lutResolver),
        )
        // Color-filter form : src comes from srcColor, LUT from child[0].
        val outFilter = SkBuiltinShaderEffectsColorCube.ColorCubeColorFilterRTImpl.shade(
            coords = null,
            srcColor = src,
            dstColor = null,
            uniforms = u.duplicate().order(ByteOrder.nativeOrder()),
            children = arrayOf(lutResolver),
        )

        assertEquals(outShader.fR, outFilter.fR, 1e-6f, "R must match")
        assertEquals(outShader.fG, outFilter.fG, 1e-6f, "G must match")
        assertEquals(outShader.fB, outFilter.fB, 1e-6f, "B must match")
        assertEquals(outShader.fA, outFilter.fA, 1e-6f, "A must match")
    }

    // ─── Auto-registration after clearForTest ─────────────────────────

    @Test
    fun `registerAll repopulates after clearForTest for ColorCubeRT`() {
        SkRuntimeEffectDispatch.clearForTest()
        // Manually trigger re-registration. D2.4.a's
        // ensureBuiltinsLoaded() does this automatically at every
        // MakeForXxx call ; it merges later, so for now we exercise
        // the explicit registerAll() path.
        SkBuiltinShaderEffectsColorCube.registerAll()
        val r = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsColorCube.COLOR_CUBE_RT_SKSL,
        )
        assertNotNull(r.effect,
            "registerAll must repopulate after clearForTest")
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /** Build a [SkShader] that returns [c] at every sample point. */
    private fun makeConstantShader(c: SkColor4f, sksl: String): SkShader {
        SkRuntimeEffectDispatch.register(sksl) { constantColorImpl(c) }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        return effect.makeShader(uniforms = null)!!
    }

    /**
     * Directly invoke
     * [SkBuiltinShaderEffectsColorCube.ColorCubeColorFilterRTImpl.shade]
     * with the standard cube uniforms and the supplied LUT
     * resolver. Bypasses
     * [SkRuntimeEffect.makeColorFilter] which doesn't yet
     * accept shader children of color-filter effects (integration
     * pending in the parent worktree).
     */
    private fun invokeColorFilterImpl(
        srcColor: SkColor4f,
        lut: ChildResolver.Shader,
    ): SkColor4f {
        val u = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).apply {
            putFloat(rgScale); putFloat(rgBias); putFloat(bScale); putFloat(invSize)
            position(0)
        }
        return SkBuiltinShaderEffectsColorCube.ColorCubeColorFilterRTImpl.shade(
            coords = null,
            srcColor = srcColor,
            dstColor = null,
            uniforms = u,
            children = arrayOf(lut),
        )
    }
}
