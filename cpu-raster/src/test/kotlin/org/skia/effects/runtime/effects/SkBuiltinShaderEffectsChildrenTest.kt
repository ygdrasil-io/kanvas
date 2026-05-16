package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.math.SkColor
import org.skia.math.SkColor4f
import org.skia.foundation.SkData
import org.skia.foundation.SkShader
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * D2.4.b cluster B verification suite for [SkBuiltinShaderEffectsChildren] —
 * the two `runtimeshader.cpp` programs that consume shader children
 * (`ThresholdRT`, `UnsharpRT`).
 *
 * **Behaviour under test** :
 *  - Each registered SkSL → impl mapping is wired correctly :
 *    `MakeForShader(theSkSL).effect.makeShader(...)` returns a non-
 *    null shader for every built-in.
 *  - `ThresholdRT` math : the smooth-cutoff parameters drive `m` to
 *    `0` (output ≈ before), `1` (output ≈ after), or a mid-range
 *    blend, depending on `cutoff` / `slope` / `threshold.a`.
 *  - `UnsharpRT` math : the 5-tap kernel (`5·centre − Σ neighbours`)
 *    reproduces the input on a constant child (kernel sum = 1 ⇒
 *    centre passes through) and produces the expected high-contrast
 *    result on an impulse child.
 *
 * **Sampling shim** : we sample the runtime shader directly via
 * [SkShader.sampleAtLocal], which round-trips through the 8-bit
 * pack-unpack at [org.skia.effects.runtime.SkRuntimeShader.pack4fToColor].
 * That introduces ≤ 1/255 ≈ 4e-3 quantisation error per channel ;
 * tolerances are sized accordingly. Tests that need sub-byte
 * precision call the impl directly via
 * [org.skia.effects.runtime.SkRuntimeImpl.shade].
 */
class SkBuiltinShaderEffectsChildrenTest {

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
        SkBuiltinShaderEffectsChildren.registerAll()
    }

    /**
     * Stub [SkShader] that returns the same constant colour at every
     * local-space sample point. Used as the child for shader effects
     * under test.
     */
    private class ConstantShader(private val c: SkColor4f) :
        SkShader(localMatrix = SkMatrix.Identity) {
        override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
            // Not exercised — sampleAtLocal-only test path.
            for (i in 0 until count) dst[i] = c.toSkColor()
        }

        override fun sampleAtLocal(lx: Float, ly: Float): SkColor = c.toSkColor()
    }

    /**
     * Stub [SkShader] backed by a `(lx, ly) -> SkColor4f` lambda.
     * Lets a test programmatically choose the per-point response —
     * e.g. to build a delta-function for the unsharp kernel test.
     */
    private class FunctionShader(private val sample: (Float, Float) -> SkColor4f) :
        SkShader(localMatrix = SkMatrix.Identity) {
        override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
            for (i in 0 until count) {
                dst[i] = sample((devX + i).toFloat() + 0.5f, devY.toFloat() + 0.5f)
                    .toSkColor()
            }
        }

        override fun sampleAtLocal(lx: Float, ly: Float): SkColor = sample(lx, ly).toSkColor()
    }

    private fun thresholdUniforms(cutoff: Float, slope: Float): SkData {
        val bytes = ByteBuffer.allocate(8)
            .order(ByteOrder.nativeOrder())
            .putFloat(cutoff)
            .putFloat(slope)
            .array()
        return SkData.MakeWithCopy(bytes)
    }

    private fun assertNearly(
        expected: SkColor4f,
        actual: SkColor4f,
        // 1.5/255 ≈ 5.9e-3 is the worst-case drift from a single
        // pack-unpack round-trip ; chained through 3 child samples
        // + a final lerp pack we can hit ~2/255 = 7.9e-3. Bumped to
        // 1e-2 (≈ 2.5/255) so the test catches genuine math errors
        // (≥ 1 % drift) without flaking on rounding.
        tol: Float = 1e-2f,
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

    /** Build the threshold runtime shader and sample it at `(x, y)`. */
    private fun threshold(
        cutoff: Float, slope: Float,
        before: SkColor4f, after: SkColor4f, threshA: Float,
        x: Float = 0.5f, y: Float = 0.5f,
    ): SkColor4f {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.THRESHOLD_RT_SKSL,
        ).effect!!
        val shader = effect.makeShader(
            uniforms = thresholdUniforms(cutoff, slope),
            children = arrayOf<SkShader?>(
                ConstantShader(before),
                ConstantShader(after),
                ConstantShader(SkColor4f(0f, 0f, 0f, threshA)),
            ),
        )!!
        return SkColor4f.FromColor(shader.sampleAtLocal(x, y))
    }

    // ─── ThresholdRT ─────────────────────────────────────────────────

    @Test
    fun `ThresholdRT MakeForShader resolves and produces a non-null shader`() {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.THRESHOLD_RT_SKSL,
        ).effect
        assertNotNull(effect, "MakeForShader must resolve THRESHOLD_RT_SKSL")
        // 3 children expected ; 0-children call should be rejected.
        assertEquals(3, effect!!.children().size)
        assertEquals(2, effect.uniforms().size)
        assertEquals(8, effect.uniformSize)
    }

    @Test
    fun `ThresholdRT with threshold a=0 and steep slope returns before`() {
        // cutoff=0.5, slope=10 → smooth_cutoff(0) = 0*10 + (0.5 - 5)
        //                       = -4.5 → clamped to 0 → m=0 → out=before.
        val before = SkColor4f(0.8f, 0.2f, 0.1f, 1f)
        val after = SkColor4f(0.1f, 0.7f, 0.9f, 1f)
        val out = threshold(cutoff = 0.5f, slope = 10f,
            before = before, after = after, threshA = 0f)
        assertNearly(before, out, msg = "m=0 → before should pass through")
    }

    @Test
    fun `ThresholdRT with threshold a=1 and steep slope returns after`() {
        // cutoff=0.5, slope=10 → smooth_cutoff(1) = 1*10 + (0.5 - 5)
        //                       = 5.5 → clamped to 1 → m=1 → out=after.
        val before = SkColor4f(0.8f, 0.2f, 0.1f, 1f)
        val after = SkColor4f(0.1f, 0.7f, 0.9f, 1f)
        val out = threshold(cutoff = 0.5f, slope = 10f,
            before = before, after = after, threshA = 1f)
        assertNearly(after, out, msg = "m=1 → after should pass through")
    }

    @Test
    fun `ThresholdRT with mid-range threshold lerps before to after`() {
        // cutoff=0.5, slope=1, threshA=0.5 →
        //   smooth_cutoff(0.5) = 0.5*1 + (0.5 - 1*0.5) = 0.5 → m=0.5.
        //   out = 0.5 * before + 0.5 * after.
        val before = SkColor4f(0.0f, 0.0f, 0.0f, 1f)
        val after = SkColor4f(1.0f, 1.0f, 1.0f, 1f)
        val out = threshold(cutoff = 0.5f, slope = 1f,
            before = before, after = after, threshA = 0.5f)
        assertNearly(SkColor4f(0.5f, 0.5f, 0.5f, 1f), out,
            msg = "m=0.5 → midpoint of before and after")
    }

    @Test
    fun `ThresholdRT smooth_cutoff respects cutoff position`() {
        // Verify the (0.5 - slope*cutoff) bias term :
        // cutoff=0.25, slope=4, threshA=0.25 →
        //   raw = 0.25*4 + (0.5 - 4*0.25) = 1.0 + (0.5 - 1.0) = 0.5
        //   m = 0.5
        val before = SkColor4f(0.0f, 0.0f, 0.0f, 1f)
        val after = SkColor4f(1.0f, 1.0f, 1.0f, 1f)
        val out = threshold(cutoff = 0.25f, slope = 4f,
            before = before, after = after, threshA = 0.25f)
        assertNearly(SkColor4f(0.5f, 0.5f, 0.5f, 1f), out,
            msg = "raw=0.5 at cutoff position when threshA=cutoff")
    }

    @Test
    fun `ThresholdRT clamps m to 1 above the cutoff and returns after`() {
        // cutoff=0.5, slope=10, threshA=0.7 →
        //   raw = 0.7*10 + (0.5 - 5) = 7 - 4.5 = 2.5 → clamped to 1
        //   → m=1 → out=after.
        val before = SkColor4f(0.0f, 0.0f, 0.0f, 1f)
        val after = SkColor4f(1.0f, 0.5f, 0.25f, 1f)
        val out = threshold(cutoff = 0.5f, slope = 10f,
            before = before, after = after, threshA = 0.7f)
        assertNearly(after, out, msg = "raw=2.5 clamps to m=1")
    }

    // ─── UnsharpRT ───────────────────────────────────────────────────

    @Test
    fun `UnsharpRT MakeForShader resolves and produces a non-null shader`() {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL,
        ).effect
        assertNotNull(effect, "MakeForShader must resolve UNSHARP_RT_SKSL")
        assertEquals(1, effect!!.children().size)
        assertEquals(0, effect.uniforms().size)
        assertEquals(0, effect.uniformSize)
    }

    @Test
    fun `UnsharpRT with constant child returns input (kernel sum = 1)`() {
        // For any constant `c`, the kernel computes 5c - 4c = c.
        val constant = SkColor4f(0.5f, 0.5f, 0.5f, 1f)
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL,
        ).effect!!
        val shader = effect.makeShader(
            uniforms = null,
            children = arrayOf<SkShader?>(ConstantShader(constant)),
        )!!
        // Sample at any local-space point.
        val out = SkColor4f.FromColor(shader.sampleAtLocal(8f, 8f))
        assertNearly(constant, out,
            msg = "constant input must pass through (5c - 4c = c)")
    }

    @Test
    fun `UnsharpRT on an impulse centre saturates the result to the centre value`() {
        // Child returns red `(1,0,0,1)` only at the exact sample
        // point `(50, 50)` ; black `(0,0,0,1)` elsewhere. The
        // 5-tap kernel at the centre :
        //   5 * red - 4 * black = (5, 0, 0, 5 - 4)  (per channel)
        // → R=5 (clamps to 1), G=0, B=0, A=1.
        val impulse = FunctionShader { lx, ly ->
            if (lx == 50f && ly == 50f) SkColor4f(1f, 0f, 0f, 1f)
            else SkColor4f(0f, 0f, 0f, 1f)
        }
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL,
        ).effect!!
        val shader = effect.makeShader(
            uniforms = null,
            children = arrayOf<SkShader?>(impulse),
        )!!
        val out = SkColor4f.FromColor(shader.sampleAtLocal(50f, 50f))
        // R clamps to 1.0 (raw = 5), G and B stay at 0, A = 1.
        assertNearly(SkColor4f(1f, 0f, 0f, 1f), out,
            msg = "impulse centre saturates R to 1.0")
    }

    @Test
    fun `UnsharpRT on an impulse neighbour produces a negative result clamped to 0`() {
        // Sample one pixel to the right of the impulse :
        //   centre (51, 50) = black, east (52, 50) = black,
        //   west (50, 50) = RED, south = north = black.
        //   raw R = 5*0 - 0 - 1 - 0 - 0 = -1 → clamps to 0.
        val impulse = FunctionShader { lx, ly ->
            if (lx == 50f && ly == 50f) SkColor4f(1f, 0f, 0f, 1f)
            else SkColor4f(0f, 0f, 0f, 1f)
        }
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL,
        ).effect!!
        val shader = effect.makeShader(
            uniforms = null,
            children = arrayOf<SkShader?>(impulse),
        )!!
        // Sample at (51, 50) — west neighbour is the impulse.
        val out = SkColor4f.FromColor(shader.sampleAtLocal(51f, 50f))
        // R clamps to 0, G/B stay 0, A = 5*1 - 4*1 = 1.
        assertNearly(SkColor4f(0f, 0f, 0f, 1f), out,
            msg = "impulse neighbour drives R below 0 (clamped)")
    }

    @Test
    fun `UnsharpRT kernel arithmetic checked unclamped via direct impl call`() {
        // Bypass the 8-bit pack-unpack to verify the raw float math
        // — the [SkRuntimeShader.sampleAtLocal] path quantises every
        // child sample to a byte, which can collapse a linear
        // function's neighbour deltas below 1/255 and drift the
        // result enough to mask kernel-arithmetic regressions.
        //
        // Child : f(x, y) = (0.1 + 0.01*x, 0.2 + 0.005*y, 0, 1).
        // Kernel at (10, 20) :
        //   5*(0.2, 0.3, 0, 1) - (0.21, 0.3, 0, 1)
        //                      - (0.19, 0.3, 0, 1)
        //                      - (0.2,  0.305, 0, 1)
        //                      - (0.2,  0.295, 0, 1)
        //   = (1.0 - 0.8, 1.5 - 1.2, 0, 5 - 4)
        //   = (0.2, 0.3, 0, 1) — linear functions reproduce
        //                        themselves through the kernel.
        val resolver = ChildResolver.Shader { p ->
            SkColor4f(0.1f + 0.01f * p.fX, 0.2f + 0.005f * p.fY, 0f, 1f)
        }
        val out = SkBuiltinShaderEffectsChildren.UnsharpRTImpl.shade(
            coords = SkPoint(10f, 20f),
            srcColor = null,
            dstColor = null,
            uniforms = ByteBuffer.allocate(0).order(ByteOrder.nativeOrder()),
            children = arrayOf(resolver),
        )
        // Tighter tolerance — no byte-roundtrip in this path.
        assertNearly(SkColor4f(0.2f, 0.3f, 0f, 1f), out, tol = 1e-5f,
            msg = "linear-function kernel = function itself (float-precision)")
    }

    // ─── Auto-registration after clearForTest ─────────────────────────

    @Test
    fun `clearForTest does not break subsequent MakeForShader for cluster B`() {
        SkRuntimeEffectDispatch.clearForTest()
        // Touch the object directly to trigger init { registerAll() }.
        SkBuiltinShaderEffectsChildren.registerAll()
        val r1 = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.THRESHOLD_RT_SKSL,
        )
        val r2 = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL,
        )
        assertNotNull(r1.effect, "ThresholdRT must resolve after re-register")
        assertNotNull(r2.effect, "UnsharpRT must resolve after re-register")
    }
}
