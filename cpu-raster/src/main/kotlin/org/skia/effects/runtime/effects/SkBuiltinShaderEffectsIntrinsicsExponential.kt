package org.skia.effects.runtime.effects

import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * D2.4.c.2 — Hand-ported exponential intrinsic runtime effects.
 *
 * Maps the 10 unary intrinsics that
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_exponential)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp)
 * lays out across a 2×5 grid : `pow(x, 3)`, `pow(x, -3)`,
 * `pow(0.9, x)`, `pow(1.1, x)`, `exp(x)`, `log(x)`, `exp2(x)`,
 * `log2(x)`, `sqrt(x)`, `inversesqrt(x)`.
 *
 * **Reuses** the [SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * skeleton (Phase D2.4.c.1) — same uniform layout, same coord
 * remap, only the per-pixel math lambda changes. Registrations
 * are layered onto the same dispatch table — each SkSL hash is
 * unique because the function token differs.
 *
 * **Math notes** :
 *  - GLSL `log` is the natural log (base e). Kotlin's `kotlin.math.ln`
 *    matches.
 *  - GLSL `exp2(x)` = `2^x` ↔ Kotlin `2.0.pow(x.toDouble()).toFloat()`
 *    (no `kotlin.math.exp2`). For a more direct translation, we
 *    use `pow(2f, x)` to make the SkSL ↔ Kotlin symmetry visible.
 *  - GLSL `log2(x)` = log base 2. Kotlin's `kotlin.math.log2` matches.
 *  - GLSL `inversesqrt(x)` = `1 / sqrt(x)`. No direct Kotlin
 *    intrinsic ; we open-code it.
 *
 * **GMs unblocked** :
 *  - [`RuntimeIntrinsicsExponentialGM`](../../../../tests/RuntimeIntrinsicsExponentialGM.kt) :
 *    the `runtime_intrinsics_exponential` `DEF_SIMPLE_GM`.
 */
public object SkBuiltinShaderEffectsIntrinsicsExponential {

    /**
     * Idempotent registry population. Fires from
     * [SkRuntimeEffect.Companion.ensureBuiltinsLoaded] (and again on
     * first reference via the `init {}` block at the bottom of the
     * declaration). Calls
     * [SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d] to build
     * the canonical SkSL string for each entry, then dispatches to
     * [SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl] with
     * the matching scalar lambda.
     */
    public fun registerAll() {
        for (entry in EXPONENTIAL_ENTRIES) {
            val sksl = SkBuiltinShaderEffectsIntrinsicsTrig
                .makeUnarySksl1d(entry.fn, requireES3 = false)
            SkRuntimeEffectDispatch.registerBuiltinIfAbsent(sksl) {
                SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl(entry.eval)
            }
        }
    }

    /**
     * Pair of an SkSL function token and its Kotlin scalar
     * implementation. Mirrors the order of upstream's
     * `DEF_SIMPLE_GM(runtime_intrinsics_exponential)` for an easy
     * diff against the GM port.
     */
    internal class Entry(
        val fn: String,
        val eval: (SkBuiltinShaderEffectsIntrinsicsTrig.IntrinsicContext) -> Float,
    )

    internal val EXPONENTIAL_ENTRIES: List<Entry> = listOf(
        // Row 1 — pow with constant exponent.
        Entry("pow(x, 3)") { ctx -> ctx.x.pow(3f) },
        Entry("pow(x, -3)") { ctx -> ctx.x.pow(-3f) },
        // Row 2 — pow with constant base.
        Entry("pow(0.9, x)") { ctx -> 0.9f.pow(ctx.x) },
        Entry("pow(1.1, x)") { ctx -> 1.1f.pow(ctx.x) },
        // Row 3 — exp / log (natural).
        Entry("exp(x)") { ctx -> exp(ctx.x) },
        Entry("log(x)") { ctx -> ln(ctx.x) },
        // Row 4 — exp2 / log2.
        Entry("exp2(x)") { ctx -> 2f.pow(ctx.x) },
        Entry("log2(x)") { ctx -> log2(ctx.x) },
        // Row 5 — sqrt / inversesqrt.
        Entry("sqrt(x)") { ctx -> sqrt(ctx.x) },
        Entry("inversesqrt(x)") { ctx -> 1f / sqrt(ctx.x) },
    )

    init { registerAll() }
}
