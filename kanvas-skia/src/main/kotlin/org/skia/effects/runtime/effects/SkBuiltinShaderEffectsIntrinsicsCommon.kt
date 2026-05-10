package org.skia.effects.runtime.effects

import org.skia.effects.runtime.SkRuntimeEffectDispatch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * D2.4.c.3 — Hand-ported common intrinsic runtime effects.
 *
 * Maps the 31 unary intrinsic plots that
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_common)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
 * lays out across a 6×7 grid : `abs` / `sign` / `floor` / `ceil` /
 * `fract` / `mod` (3 forms) / `min` / `max` (6 forms) / `clamp` (3
 * forms) / `saturate` / `mix` (3 forms) / `step` (3 forms) /
 * `smoothstep` (3 forms) / `floor(p)` / `ceil(p)` componentwise.
 *
 * **Reuses** the [SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * skeleton — same uniform layout, same coord remap. Each unique
 * SkSL string registers a fresh impl with the matching math.
 *
 * **Why so many "same math" entries** : upstream registers
 * separate SkSL strings for `mod(x, 2)`, `mod(p, v2).x`, etc.
 * even though the result at any sample point is identical (the
 * SkSL `p.x` token equals the `x` token after the template's
 * remap). The hash dispatch is per-string ; we register each
 * canonical string with the corresponding lambda. Repeated math
 * doesn't hurt — class instances are cheap.
 *
 * **`p.y` is the only "different value" axis** : the row-7 entries
 * `floor(p).y` / `ceil(p).y` consume
 * [SkBuiltinShaderEffectsIntrinsicsTrig.IntrinsicContext.py],
 * which is `(1 - p_in.x) * xScale + xBias` — distinct from `x`.
 *
 * **GMs unblocked** :
 *  - [`RuntimeIntrinsicsCommonGM`](../../../../tests/RuntimeIntrinsicsCommonGM.kt) :
 *    the `runtime_intrinsics_common` `DEF_SIMPLE_GM`.
 */
public object SkBuiltinShaderEffectsIntrinsicsCommon {

    /** Idempotent registry population — see Phase D2.4.c.1 KDoc. */
    public fun registerAll() {
        for (entry in COMMON_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsTrig
                .makeUnarySksl1d(entry.fn, requireES3 = false)
            SkRuntimeEffectDispatch.register(sksl) {
                SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl(entry.eval)
            }
        }
    }

    internal class Entry(
        val fn: String,
        val eval: (SkBuiltinShaderEffectsIntrinsicsTrig.IntrinsicContext) -> Float,
    )

    /** GLSL `mod(x, y)` — defined as `x - y * floor(x/y)`. Differs
     *  from Kotlin `x % y` (which is C's `fmod` — sign of result
     *  follows numerator, not denominator). For `mod(1, -2)`
     *  GLSL returns -1 ; `1 % -2` returns 1. */
    private fun glslMod(x: Float, y: Float): Float = x - y * floor(x / y)

    /** GLSL `mix(a, b, t)` — linear interp `a*(1-t) + b*t`. */
    private fun glslMix(a: Float, b: Float, t: Float): Float = a * (1f - t) + b * t

    /** GLSL `step(edge, x)` — 0 if x < edge else 1. */
    private fun glslStep(edge: Float, x: Float): Float = if (x < edge) 0f else 1f

    /** GLSL `smoothstep(edge0, edge1, x)` — Hermite interpolation. */
    private fun glslSmoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Skia / HLSL `saturate(x)` — `clamp(x, 0, 1)`. */
    private fun saturate(x: Float): Float = x.coerceIn(0f, 1f)

    internal val COMMON_ENTRIES: List<Entry> = listOf(
        // Row 1
        Entry("abs(x)") { ctx -> abs(ctx.x) },
        Entry("sign(x)") { ctx -> sign(ctx.x) },

        // Row 2 — floor / ceil / fract / mod (3 forms)
        Entry("floor(x)") { ctx -> floor(ctx.x) },
        Entry("ceil(x)") { ctx -> ceil(ctx.x) },
        Entry("fract(x)") { ctx -> ctx.x - floor(ctx.x) },
        Entry("mod(x, 2)") { ctx -> glslMod(ctx.x, 2f) },
        Entry("mod(p, -2).x") { ctx -> glslMod(ctx.px, -2f) },
        Entry("mod(p, v2).x") { ctx -> glslMod(ctx.px, 2f) },

        // Row 3 — min / max (3 forms each, all reduce to scalar
        // because p.x == x and v1.x = 1)
        Entry("min(x, 1)") { ctx -> min(ctx.x, 1f) },
        Entry("min(p, 1).x") { ctx -> min(ctx.px, 1f) },
        Entry("min(p, v1).x") { ctx -> min(ctx.px, 1f) },
        Entry("max(x, 1)") { ctx -> max(ctx.x, 1f) },
        Entry("max(p, 1).x") { ctx -> max(ctx.px, 1f) },
        Entry("max(p, v1).x") { ctx -> max(ctx.px, 1f) },

        // Row 4 — clamp (3 forms) + saturate
        Entry("clamp(x, 1, 2)") { ctx -> ctx.x.coerceIn(1f, 2f) },
        Entry("clamp(p, 1, 2).x") { ctx -> ctx.px.coerceIn(1f, 2f) },
        Entry("clamp(p, v1, v2).x") { ctx -> ctx.px.coerceIn(1f, 2f) },
        Entry("saturate(x)") { ctx -> saturate(ctx.x) },

        // Row 5 — mix (3 forms)
        Entry("mix(1, 2, x)") { ctx -> glslMix(1f, 2f, ctx.x) },
        Entry("mix(v1, v2, x).x") { ctx -> glslMix(1f, 2f, ctx.x) },
        Entry("mix(v1, v2, p).x") { ctx -> glslMix(1f, 2f, ctx.px) },

        // Row 6 — step (3 forms) + smoothstep (3 forms)
        Entry("step(1, x)") { ctx -> glslStep(1f, ctx.x) },
        Entry("step(1, p).x") { ctx -> glslStep(1f, ctx.px) },
        Entry("step(v1, p).x") { ctx -> glslStep(1f, ctx.px) },
        Entry("smoothstep(1, 2, x)") { ctx -> glslSmoothstep(1f, 2f, ctx.x) },
        Entry("smoothstep(1, 2, p).x") { ctx -> glslSmoothstep(1f, 2f, ctx.px) },
        Entry("smoothstep(v1, v2, p).x") { ctx -> glslSmoothstep(1f, 2f, ctx.px) },

        // Row 7 — floor / ceil componentwise on p ; .x and .y
        // sample different values (px = remap of lx, py = remap
        // of 1 - lx)
        Entry("floor(p).x") { ctx -> floor(ctx.px) },
        Entry("ceil(p).x") { ctx -> ceil(ctx.px) },
        Entry("floor(p).y") { ctx -> floor(ctx.py) },
        Entry("ceil(p).y") { ctx -> ceil(ctx.py) },
    )

    init { registerAll() }
}
