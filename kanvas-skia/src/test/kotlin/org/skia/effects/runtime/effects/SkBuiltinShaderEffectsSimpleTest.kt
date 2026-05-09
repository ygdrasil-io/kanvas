package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkData
import org.skia.foundation.SkShader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * D2.4.b cluster A verification suite for [SkBuiltinShaderEffectsSimple].
 *
 * **Behaviour under test** :
 *  - Each registered SkSL → impl mapping is wired correctly :
 *    `MakeForShader(theSkSL).effect.makeShader(...)` returns a
 *    non-null shader for every built-in.
 *  - The math of each impl reproduces the SkSL formula at known
 *    sample points — SimpleRT's encoded-coord output, SpiralRT's
 *    polar lerp, and LinearGradientRT's encoded-vs-linear branch.
 *  - Uniforms pass through the binding into the impl's
 *    [java.nio.ByteBuffer] view at the offsets declared by the
 *    parser (validates the std140-ish alignment for the spiral
 *    case which mixes scalar, float2 and float4).
 *
 * **Sampling protocol** : tests build the effect via
 * [SkRuntimeEffect.MakeForShader] and call
 * [SkShader.sampleAtLocal] (which bypasses the canvasCtm chain
 * and feeds the local-space point straight into the impl). The
 * returned [org.skia.foundation.SkColor] (8-bit ARGB) is decoded
 * via [SkColor4f.FromColor], so the tolerance accounts for the
 * 1/255 quantisation step.
 */
class SkBuiltinShaderEffectsSimpleTest {

    /**
     * Re-register on every test entry. The `object`'s `init { … }`
     * block fires once per JVM process, so a sibling test that
     * calls [SkRuntimeEffectDispatch.clearForTest] (e.g. our own
     * `clearForTest does not break subsequent MakeForShader` case)
     * will leave the table empty for the next test in run order.
     * D2.4.a wires `SkRuntimeEffect.makeFor` to call
     * `ensureBuiltinsLoaded()` automatically — until that lands,
     * tests do it explicitly.
     */
    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsSimple.registerAll()
    }

    /** Reads the supplied byte array as a uniforms blob and
     *  invokes the shader at a single local-space point. */
    private fun shadeAt(sksl: String, uniforms: ByteArray, x: Float, y: Float): SkColor4f {
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect
            ?: error("MakeForShader returned null for : $sksl")
        val shader = effect.makeShader(
            uniforms = if (uniforms.isEmpty()) null else SkData.MakeWithCopy(uniforms),
            children = emptyArray<SkShader?>(),
        ) ?: error("makeShader returned null")
        return SkColor4f.FromColor(shader.sampleAtLocal(x, y))
    }

    private fun assertNearly(
        expected: SkColor4f,
        actual: SkColor4f,
        tol: Float = 1.5f / 255f,
        msg: String = "",
    ) {
        val drift = floatArrayOf(
            abs(expected.fR - actual.fR),
            abs(expected.fG - actual.fG),
            abs(expected.fB - actual.fB),
            abs(expected.fA - actual.fA),
        )
        assertTrue(drift.all { it < tol },
            "$msg : expected=$expected, actual=$actual, drift=${drift.toList()}, tol=$tol")
    }

    /** Pack [floats] into a little-endian byte array suitable for
     *  [SkData.MakeWithCopy]. The runtime impl reads through a
     *  `ByteOrder.nativeOrder()` buffer ; on x86_64 / arm64 macOS
     *  + Linux that's little-endian, which matches the JVM. */
    private fun bytesOf(vararg floats: Float): ByteArray {
        val bb = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.nativeOrder())
        for (f in floats) bb.putFloat(f)
        return bb.array()
    }

    // ─── SimpleRT (gProg) ────────────────────────────────────────────

    @Test
    fun `SimpleRT factory returns a non-null shader`() {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL,
        ).effect
        assertNotNull(effect, "MakeForShader returned null Result.effect")
        val shader = effect!!.makeShader(
            uniforms = SkData.MakeWithCopy(bytesOf(0f, 0f, 0f, 1f)),
            children = emptyArray<SkShader?>(),
        )
        assertNotNull(shader, "makeShader returned null")
    }

    @Test
    fun `SimpleRT outputs (p_x over 255, p_y over 255, gColor_b, 1)`() {
        // gColor = (1, 0, 0.7, 1) — only .b should leak into the output.
        val u = bytesOf(1f, 0f, 0.7f, 1f)
        val out = shadeAt(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL, u, 128f, 64f)
        assertNearly(SkColor4f(128f / 255f, 64f / 255f, 0.7f, 1f), out)
    }

    @Test
    fun `SimpleRT ignores gColor_r and gColor_g`() {
        // Two uniforms with very different R / G but same B → outputs match.
        val a = bytesOf(0.0f, 0.0f, 0.4f, 1f)
        val b = bytesOf(1.0f, 1.0f, 0.4f, 0f) // diff R, G, A — only B is read.
        val outA = shadeAt(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL, a, 50f, 100f)
        val outB = shadeAt(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL, b, 50f, 100f)
        assertNearly(outA, outB,
            msg = "outputs should match — only gColor.b is sampled")
    }

    @Test
    fun `SimpleRT at the origin returns (0, 0, gColor_b, 1)`() {
        val u = bytesOf(0f, 0f, 0.5f, 1f)
        val out = shadeAt(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL, u, 0f, 0f)
        assertNearly(SkColor4f(0f, 0f, 0.5f, 1f), out)
    }

    // ─── SpiralRT (gSpiralSkSL) ──────────────────────────────────────

    @Test
    fun `SpiralRT factory returns a non-null shader`() {
        // 48 bytes of zero-uniforms.
        val u = ByteArray(48)
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL,
        ).effect
        assertNotNull(effect)
        val shader = effect!!.makeShader(
            uniforms = SkData.MakeWithCopy(u),
            children = emptyArray<SkShader?>(),
        )
        assertNotNull(shader)
    }

    @Test
    fun `SpiralRT at center returns 50_50 mix when rad_scale = 0`() {
        // rad_scale = 0 → t depends only on angle. At the center,
        // pp = (0, 0), atan(0/0) = NaN, but ppX = 0 makes a divide-by-
        // zero — pick a tiny offset to avoid the singularity. Use
        // pp = (1, 0) → angle = atan(0) = 0, t = (0 + π/2) / π = 0.5.
        // Then radius * rad_scale = 0 → t stays 0.5 → fract(0.5) = 0.5.
        // Output : (c0 + c1) / 2.
        val center = floatArrayOf(0f, 0f)
        val c0 = floatArrayOf(1f, 0f, 0f, 1f) // red
        val c1 = floatArrayOf(0f, 0f, 1f, 1f) // blue
        // Layout : float rad_scale @0, float2 in_center @8, float4 c0 @16, float4 c1 @32.
        val bb = ByteBuffer.allocate(48).order(ByteOrder.nativeOrder())
        bb.putFloat(0f) // rad_scale
        bb.putFloat(0f) // padding @4..7
        bb.putFloat(center[0]).putFloat(center[1]) // in_center @8..15
        bb.putFloat(c0[0]).putFloat(c0[1]).putFloat(c0[2]).putFloat(c0[3]) // @16
        bb.putFloat(c1[0]).putFloat(c1[1]).putFloat(c1[2]).putFloat(c1[3]) // @32

        val out = shadeAt(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL, bb.array(), 1f, 0f)
        // (c0 + c1) / 2 = (0.5, 0, 0.5, 1).
        assertNearly(SkColor4f(0.5f, 0f, 0.5f, 1f), out)
    }

    @Test
    fun `SpiralRT matches reference Kotlin recompute at multiple points`() {
        val radScale = 0.05f
        val center = floatArrayOf(50f, 50f)
        val c0 = floatArrayOf(0.2f, 0.4f, 0.8f, 1f)
        val c1 = floatArrayOf(1.0f, 0.6f, 0.2f, 1f)
        val bb = ByteBuffer.allocate(48).order(ByteOrder.nativeOrder())
        bb.putFloat(radScale)
        bb.putFloat(0f) // padding for alignment
        bb.putFloat(center[0]).putFloat(center[1])
        bb.putFloat(c0[0]).putFloat(c0[1]).putFloat(c0[2]).putFloat(c0[3])
        bb.putFloat(c1[0]).putFloat(c1[1]).putFloat(c1[2]).putFloat(c1[3])
        val u = bb.array()

        val cases = listOf(
            10f to 30f,
            70f to 80f,
            100f to 100f,
            25f to 75f,
        )
        for ((x, y) in cases) {
            val out = shadeAt(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL, u, x, y)
            // Reference recompute, mirroring the SkSL math verbatim.
            val ppX = x - center[0]
            val ppY = y - center[1]
            val r0 = sqrt(ppX * ppX + ppY * ppY)
            val r = sqrt(r0)
            val ang = atan(ppY / ppX)
            var t = (ang + (3.1415926f / 2f)) / 3.1415926f
            t += r * radScale
            t -= floor(t)
            val one = 1f - t
            val ref = SkColor4f(
                fR = c0[0] * one + c1[0] * t,
                fG = c0[1] * one + c1[1] * t,
                fB = c0[2] * one + c1[2] * t,
                fA = c0[3] * one + c1[3] * t,
            )
            assertNearly(ref, out, msg = "SpiralRT mismatch at ($x, $y)")
        }
    }

    @Test
    fun `SpiralRT reflects uniform changes`() {
        // Same coords, two different uniform sets → different outputs.
        val u1 = run {
            val bb = ByteBuffer.allocate(48).order(ByteOrder.nativeOrder())
            bb.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f)
                .putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f)
                .putFloat(0f).putFloat(1f).putFloat(0f).putFloat(1f)
            bb.array()
        }
        val u2 = run {
            val bb = ByteBuffer.allocate(48).order(ByteOrder.nativeOrder())
            bb.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f)
                .putFloat(0f).putFloat(0f).putFloat(1f).putFloat(1f)
                .putFloat(1f).putFloat(1f).putFloat(0f).putFloat(1f)
            bb.array()
        }
        val a = shadeAt(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL, u1, 5f, 3f)
        val b = shadeAt(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL, u2, 5f, 3f)
        // Outputs must differ (different colour pairs at the same t).
        val same = abs(a.fR - b.fR) < 1e-3f &&
            abs(a.fG - b.fG) < 1e-3f &&
            abs(a.fB - b.fB) < 1e-3f
        assertTrue(!same, "different uniforms must yield different outputs")
    }

    // ─── LinearGradientRT (gLinearGradientSkSL) ──────────────────────

    @Test
    fun `LinearGradientRT factory returns a non-null shader`() {
        val u = ByteArray(32)
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL,
        ).effect
        assertNotNull(effect)
        val shader = effect!!.makeShader(
            uniforms = SkData.MakeWithCopy(u),
            children = emptyArray<SkShader?>(),
        )
        assertNotNull(shader)
    }

    @Test
    fun `LinearGradientRT top half lerps in encoded sRGB`() {
        // p.y < 32 → encoded-space mix.
        val c0 = floatArrayOf(0f, 0f, 0f, 1f)
        val c1 = floatArrayOf(1f, 1f, 1f, 1f)
        val u = bytesOf(c0[0], c0[1], c0[2], c0[3], c1[0], c1[1], c1[2], c1[3])
        // p.x = 128 → t = 0.5 ; p.y = 16 → top branch.
        val out = shadeAt(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL, u, 128f, 16f)
        // Encoded mix of black & white = (0.5, 0.5, 0.5, 1).
        assertNearly(SkColor4f(0.5f, 0.5f, 0.5f, 1f), out)
    }

    @Test
    fun `LinearGradientRT bottom half lerps in linear sRGB and overrides alpha to 1`() {
        // Same black → white but in the bottom branch (p.y >= 32).
        // mid-grey in linear sRGB = 0.5 → encoded ≈ 0.7354 (sRGB OETF).
        val c0 = floatArrayOf(0f, 0f, 0f, 0.5f) // alpha 0.5
        val c1 = floatArrayOf(1f, 1f, 1f, 0.5f) // alpha 0.5
        val u = bytesOf(c0[0], c0[1], c0[2], c0[3], c1[0], c1[1], c1[2], c1[3])
        val out = shadeAt(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL, u, 128f, 64f)
        // Expected RGB after linear-space mix and re-encode :
        //   linear(0) = 0, linear(1) = 1 → mix(0, 1, 0.5) = 0.5.
        //   encode(0.5) ≈ 1.055 * 0.5^(1/2.4) - 0.055 ≈ 0.7353569
        // Alpha is forced to 1 (from `.rgb1` swizzle), not 0.5.
        assertNearly(SkColor4f(0.7353569f, 0.7353569f, 0.7353569f, 1f), out, tol = 2.5f / 255f)
    }

    @Test
    fun `LinearGradientRT branch boundary is at p_y = 32`() {
        // Black → red. At t = 0.5 the top branch gives (0.5, 0, 0, 1)
        // while the bottom branch gives (encode(0.5), 0, 0, 1) ≈ (0.735, 0, 0, 1).
        val u = bytesOf(0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f)
        val top = shadeAt(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL, u, 128f, 31.9f)
        val bot = shadeAt(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL, u, 128f, 32.0f)
        assertNearly(SkColor4f(0.5f, 0f, 0f, 1f), top, msg = "p.y < 32 must take encoded branch")
        // Bottom branch should differ noticeably from the top.
        val deltaR = bot.fR - top.fR
        assertTrue(deltaR > 0.1f,
            "bottom branch encode ≈ 0.735 must differ from top's 0.5 ; got ${bot.fR}")
    }

    @Test
    fun `LinearGradientRT t = 0 returns in_colors0 in both branches`() {
        // p.x = 0 → t = 0 → mix(a, b, 0) = a. Both branches should
        // recover c0 (modulo the .rgb1 alpha override in the bottom).
        val c0 = floatArrayOf(0.4f, 0.6f, 0.2f, 0.8f)
        val c1 = floatArrayOf(0.9f, 0.1f, 0.5f, 0.3f)
        val u = bytesOf(c0[0], c0[1], c0[2], c0[3], c1[0], c1[1], c1[2], c1[3])
        val top = shadeAt(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL, u, 0f, 16f)
        val bot = shadeAt(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL, u, 0f, 64f)
        assertNearly(SkColor4f(c0[0], c0[1], c0[2], c0[3]), top,
            msg = "top branch at t=0 must equal in_colors0")
        // Bottom : RGB equal (transferFn(transferFn^-1(c)) = c), alpha forced to 1.
        assertNearly(SkColor4f(c0[0], c0[1], c0[2], 1f), bot, tol = 2.5f / 255f,
            msg = "bottom branch at t=0 must equal in_colors0.rgb1")
    }

    // ─── Auto-registration after clearForTest ─────────────────────────

    @Test
    fun `clearForTest does not break subsequent MakeForShader`() {
        SkRuntimeEffectDispatch.clearForTest()
        // Touch the object so its `init { registerAll() }` re-runs the
        // registration. A real client calls
        // SkRuntimeEffect.makeFor(...) which calls
        // ensureBuiltinsLoaded() ; that hook is wired in D2.4.a.
        SkBuiltinShaderEffectsSimple.registerAll()
        val r = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL)
        assertNotNull(r.effect, "registerAll must repopulate after clearForTest")
        // And the other two too.
        assertNotNull(
            SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL).effect,
        )
        assertNotNull(
            SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL).effect,
        )
    }

    @Test
    fun `signature parser produces 48-byte uniform block for SpiralRT`() {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL,
        ).effect!!
        assertEquals(48, effect.uniformSize,
            "SpiralRT uniform block must be 48 bytes total")
    }

    @Test
    fun `signature parser produces 32-byte uniform block for LinearGradientRT`() {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL,
        ).effect!!
        assertEquals(32, effect.uniformSize,
            "LinearGradientRT uniform block must be 32 bytes total")
    }
}
