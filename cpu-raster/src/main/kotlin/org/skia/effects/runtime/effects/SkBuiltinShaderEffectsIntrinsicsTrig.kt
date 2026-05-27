package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Hand-ported "trig" intrinsic runtime effects from Phase D2.4.c.1
 * — the 12 unary SkSL plot expressions that
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_trig)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
 * lays out across a 3×5 grid (radians / degrees / sin / cos / tan /
 * asin / acos / atan / 4× atan-with-constant). Each one is fed
 * through the shared
 * [`make_unary_sksl_1d`](#makeUnarySksl1d) skeleton so the hash
 * key changes per call but the surrounding plumbing (4 uniforms,
 * the `(p.x, 1 - p.x) * xScale + xBias` remap, the `y.xxx1`
 * output) is identical.
 *
 * **Strategy** — *one shared [SkRuntimeImpl] subclass*
 * ([UnaryIntrinsicImpl]) parameterised by a Kotlin lambda that
 * implements the per-pixel scalar math. Each registered SkSL hash
 * dispatches to a fresh [UnaryIntrinsicImpl] instance carrying the
 * matching lambda. This keeps the boilerplate down — only the
 * intrinsic body needs hand-translating, the uniform layout and
 * coordinate remap are shared.
 *
 * **Why hand-port rather than parse SkSL** : the project's
 * strategy is hand-port-per-shader-type (Kotlin for raster, WGSL
 * for GPU — see
 * [`MIGRATION_PLAN_GPU_WEBGPU.md`](../../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md)).
 * The runtime-intrinsics GMs deliberately exercise the SkSL
 * function-call surface ; we mirror each call with a Kotlin
 * `kotlin.math` equivalent. Floating-point semantics match GLSL
 * almost-everywhere (1-arg `atan(x)` → `kotlin.math.atan(x)` ;
 * 2-arg `atan(y, x)` → `kotlin.math.atan2(y, x)` ; same NaN /
 * Inf rules).
 *
 * **GMs unblocked** :
 *  - [`RuntimeIntrinsicsTrigGM`](../../../../tests/RuntimeIntrinsicsTrigGM.kt) :
 *    the `runtime_intrinsics_trig` `DEF_SIMPLE_GM` from
 *    `gm/runtimeintrinsics.cpp`. Future slices D2.4.c.2-6 share
 *    [makeUnarySksl1d] / [UnaryIntrinsicImpl] for the exponential
 *    / common / geometric clusters and add their own templates
 *    (matrix, relational) on top.
 */
public object SkBuiltinShaderEffectsIntrinsicsTrig {

    /**
     * Idempotent registry population. Called automatically on
     * first reference to this `object` (via the `init {}` block
     * at the bottom of this declaration — placed *after*
     * [TRIG_ENTRIES] so the property is fully initialised before
     * we iterate it), and re-called by [SkRuntimeEffect]'s
     * `ensureBuiltinsLoaded` helper after a
     * [SkRuntimeEffectDispatch.clearForTest].
     */
    public fun registerAll() {
        for (entry in TRIG_ENTRIES) {
            val sksl = makeUnarySksl1d(entry.fn, requireES3 = false)
            SkRuntimeEffectDispatch.registerBuiltinIfAbsent(sksl) {
                UnaryIntrinsicImpl(entry.eval)
            }
        }
    }

    // ─── Public SkSL accessor (used by the GM port) ──────────────────

    /**
     * Build the SkSL source for a unary intrinsic plot. Mirror of
     * [`make_unary_sksl_1d`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
     * — must produce the **exact same** canonical-normalised string
     * as upstream so the dispatch hash matches what the GM port
     * passes at draw time.
     *
     * The SkSL takes 4 `float` uniforms (`xScale`, `xBias`,
     * `yScale`, `yBias`) and remaps the input `p ∈ [0, 1]²` to
     * `(p.x, 1 - p.x) * xScale + xBias`, exposing :
     *
     *  - `x`  : `float`   — `p.x` after the remap.
     *  - `xi` : `int`     — `floor(x)`.
     *  - `p`  : `float2`  — full remapped point.
     *  - `pi` : `int2`    — `floor(p)` componentwise.
     *  - `v1` : `float2(1)` — convenience constant.
     *  - `v2` : `float2(2)` — convenience constant.
     *
     * The intrinsic expression `[fn]` (e.g. `"sin(x)"` or
     * `"atan(0.1, x)"`) replaces the `%s` placeholder in the
     * template. The result is cast to `float`, scaled, biased,
     * and broadcast as `(y, y, y, 1)`.
     *
     * @param fn the SkSL expression to plot — must produce a
     *   scalar that converts to `float` (most intrinsics do).
     *   Vector intrinsics like `mod(p, v2)` must be component-
     *   indexed (e.g. `"mod(p, v2).x"`).
     * @param requireES3 emits `#version 300` if true ; otherwise
     *   `#version 100`. The trig cluster always passes `false` ;
     *   the ES3-only cluster (D2.4.c future) passes `true`.
     */
    public fun makeUnarySksl1d(fn: String, requireES3: Boolean): String {
        val version = if (requireES3) "300" else "100"
        return "#version $version\n" +
            "uniform float xScale; uniform float xBias;" +
            "uniform float yScale; uniform float yBias;" +
            "half4 main(float2 p) {" +
            "    const float2 v1 = float2(1);" +
            "    const float2 v2 = float2(2);" +
            "    p = float2(p.x, 1 - p.x) * xScale + xBias;" +
            "    float x = p.x;" +
            "    int2  pi = int2(floor(p));" +
            "    int   xi = pi.x;" +
            "    float y = float($fn) * yScale + yBias;" +
            "    return y.xxx1;" +
            "}"
    }

    // ─── Trig entries — fn token + Kotlin math impl ──────────────────

    /**
     * Test cases consumed by [registerAll]. The `fn` token is what
     * upstream pastes into the SkSL template ; [eval] is the
     * matching Kotlin scalar computation. Order matches
     * upstream's `DEF_SIMPLE_GM(runtime_intrinsics_trig)` body for
     * easy diff against the GM port.
     */
    internal val TRIG_ENTRIES: List<Entry> = listOf(
        // Row 1
        Entry("radians(x)") { ctx -> radians(ctx.x) },
        Entry("degrees(x)") { ctx -> degrees(ctx.x) },
        // Row 2
        Entry("sin(x)") { ctx -> sin(ctx.x) },
        Entry("cos(x)") { ctx -> cos(ctx.x) },
        Entry("tan(x)") { ctx -> tan(ctx.x) },
        // Row 3
        Entry("asin(x)") { ctx -> asin(ctx.x) },
        Entry("acos(x)") { ctx -> acos(ctx.x) },
        Entry("atan(x)") { ctx -> atan(ctx.x) },
        // Row 4
        Entry("atan(0.1,  x)") { ctx -> atan2(0.1f, ctx.x) },
        Entry("atan(-0.1, x)") { ctx -> atan2(-0.1f, ctx.x) },
        // Row 5
        Entry("atan(x,  0.1)") { ctx -> atan2(ctx.x, 0.1f) },
        Entry("atan(x, -0.1)") { ctx -> atan2(ctx.x, -0.1f) },
    )

    private fun radians(degrees: Float): Float = (degrees * (PI / 180.0)).toFloat()
    private fun degrees(radians: Float): Float = (radians * (180.0 / PI)).toFloat()

    /**
     * Auto-registration `init` block. Placed **after** [TRIG_ENTRIES]
     * so the list is fully initialised when [registerAll] iterates
     * it. Kotlin object init blocks run in source order intermixed
     * with property initializers — moving `init` here is the canonical
     * fix for "I want this self-registration on object load".
     */
    init { registerAll() }

    // ─── Skeleton impl shared across every unary intrinsic ───────────

    /**
     * Per-pixel evaluation context fed to an [Entry.eval] lambda.
     * Mirrors the SkSL bindings exposed by [makeUnarySksl1d].
     */
    public class IntrinsicContext(
        public val x: Float,
        public val xi: Int,
        public val px: Float,
        public val py: Float,
        public val pix: Int,
        public val piy: Int,
    ) {
        public companion object {
            public val V1X: Float = 1f
            public val V1Y: Float = 1f
            public val V2X: Float = 2f
            public val V2Y: Float = 2f
        }
    }

    /** Pair of an SkSL function token and its Kotlin
     *  scalar implementation. */
    internal class Entry(
        val fn: String,
        val eval: (IntrinsicContext) -> Float,
    )

    /**
     * Generic [SkRuntimeImpl] for every `make_unary_sksl_1d(fn,
     * false)` registration. Lazily instantiated per dispatch hit ;
     * carries no per-instance state besides the math lambda.
     *
     * Uniform layout (matches the SkSL declaration order, std140-
     * ish alignment per [SkRuntimeEffect.Uniform.offset]) :
     *
     *  - `xScale` : `kFloat` at offset  0 — 4 bytes.
     *  - `xBias`  : `kFloat` at offset  4 — 4 bytes.
     *  - `yScale` : `kFloat` at offset  8 — 4 bytes.
     *  - `yBias`  : `kFloat` at offset 12 — 4 bytes.
     *  - Total   : 16 bytes.
     */
    public class UnaryIntrinsicImpl(
        private val math: (IntrinsicContext) -> Float,
    ) : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = UNIFORMS
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val pIn = coords ?: return SkColor4f.kBlack

            uniforms.position(0)
            val xScale = uniforms.float
            val xBias = uniforms.float
            val yScale = uniforms.float
            val yBias = uniforms.float

            // Remap p as the SkSL does : float2(p.x, 1 - p.x).
            // Note : that's `1 - p.x`, *not* `1 - p.y` — both
            // components of the remapped point are derived from
            // `p.x` alone (SkSL upstream copies p.x to the y
            // component "for intrinsics with a mix of scalar /
            // vector params" — see the comment block above
            // `make_unary_sksl_1d` upstream).
            val px = pIn.fX * xScale + xBias
            val py = (1f - pIn.fX) * xScale + xBias
            val x = px
            val pix = floor(px).toInt()
            val piy = floor(py).toInt()
            val xi = pix

            val ctx = IntrinsicContext(x = x, xi = xi, px = px, py = py, pix = pix, piy = piy)
            val rawY = math(ctx)
            val y = rawY * yScale + yBias
            // y.xxx1 — broadcast scalar across RGB, alpha = 1.
            return SkColor4f(fR = y, fG = y, fB = y, fA = 1f)
        }

        public companion object {
            internal val UNIFORMS: List<SkRuntimeEffect.Uniform> = listOf(
                SkRuntimeEffect.Uniform(
                    name = "xScale",
                    offset = 0,
                    type = SkRuntimeEffect.Uniform.Type.kFloat,
                    count = 1,
                    flags = 0,
                ),
                SkRuntimeEffect.Uniform(
                    name = "xBias",
                    offset = 4,
                    type = SkRuntimeEffect.Uniform.Type.kFloat,
                    count = 1,
                    flags = 0,
                ),
                SkRuntimeEffect.Uniform(
                    name = "yScale",
                    offset = 8,
                    type = SkRuntimeEffect.Uniform.Type.kFloat,
                    count = 1,
                    flags = 0,
                ),
                SkRuntimeEffect.Uniform(
                    name = "yBias",
                    offset = 12,
                    type = SkRuntimeEffect.Uniform.Type.kFloat,
                    count = 1,
                    flags = 0,
                ),
            )
        }
    }
}
