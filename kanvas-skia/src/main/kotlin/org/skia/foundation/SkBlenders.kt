package org.skia.foundation

/**
 * Stable factory functions for [SkBlender]. Mirrors Skia's
 * [`SkBlenders`](https://github.com/google/skia/blob/main/include/effects/SkBlenders.h)
 * — a thin namespace over the canonical custom blenders Skia
 * ships out of the box (currently just [Arithmetic]).
 *
 * Subsequent D2 slices add factories for runtime-effect-built
 * blenders ; for now the static list matches upstream's stable
 * surface.
 */
public object SkBlenders {

    /**
     * Arithmetic blender :
     *
     * ```
     * out_rgba = saturate(k1·src·dst + k2·src + k3·dst + k4)
     * if (enforcePremul) out_rgb = min(out_rgb, out_a)
     * ```
     *
     * Mirrors upstream's
     * [`SkBlenders::Arithmetic(k1, k2, k3, k4, enforcePremul)`](https://github.com/google/skia/blob/main/src/effects/SkBlenders.cpp)
     * exactly :
     *
     *  - Input checks : non-finite coefficients return `null`
     *    (matches upstream's `SkIsFinite(k1, k2, k3, k4)` early-out).
     *  - **Mode short-circuits** for tuples that match a stock
     *    [SkBlendMode] within `SK_ScalarNearlyEqual` tolerance
     *    (`(0, 1, 0, 0) → kSrc`, `(0, 0, 1, 0) → kDst`,
     *    `(0, 0, 0, 0) → kClear`) — the returned blender is a
     *    [SkBlendModeBlender] in those cases, so the rasterizer
     *    routes through the legacy fast paths and doesn't pay the
     *    [SkColor4f] round-trip.
     *  - Otherwise the returned blender is a fresh
     *    [SkArithmeticBlender] carrying the four coefficients +
     *    the premul flag.
     *
     * Pixel contract : both [src] and [dst] arrive at [SkArithmeticBlender.blend]
     * **unpremultiplied**, the formula multiplies, then [enforcePremul]
     * caps `out_rgb ≤ out_a` (premul invariant) when set. Exactly
     * what upstream's `sk_arithmetic_blend` does on the SkSL side.
     *
     * @param k1 multiplicative `src · dst` coefficient.
     * @param k2 `src` coefficient.
     * @param k3 `dst` coefficient.
     * @param k4 constant offset.
     * @param enforcePremul when `true`, clamp `out_rgb` so it never
     *   exceeds `out_a` — keeps the result a valid premul colour.
     *   When `false`, only the per-channel `[0, 1]` saturation is
     *   applied (the output may be non-premul, e.g. with
     *   `out_rgb > out_a`).
     * @return a blender matching the formula, or `null` if any
     *   coefficient is non-finite.
     */
    public fun Arithmetic(
        k1: Float,
        k2: Float,
        k3: Float,
        k4: Float,
        enforcePremul: Boolean,
    ): SkBlender? {
        if (!k1.isFinite() || !k2.isFinite() || !k3.isFinite() || !k4.isFinite()) {
            return null
        }
        // Mode short-circuits — match upstream's `table[]` of
        // (k1, k2, k3, k4) tuples that collapse to a stock blend
        // mode within nearly-equal tolerance. Lets the rasterizer
        // skip the SkColor4f round-trip entirely for these
        // common cases.
        val table = arrayOf(
            floatArrayOf(0f, 1f, 0f, 0f) to SkBlendMode.kSrc,
            floatArrayOf(0f, 0f, 1f, 0f) to SkBlendMode.kDst,
            floatArrayOf(0f, 0f, 0f, 0f) to SkBlendMode.kClear,
        )
        for ((tuple, mode) in table) {
            if (nearlyEqual(k1, tuple[0]) &&
                nearlyEqual(k2, tuple[1]) &&
                nearlyEqual(k3, tuple[2]) &&
                nearlyEqual(k4, tuple[3])
            ) {
                return SkBlender.Mode(mode)
            }
        }
        return SkArithmeticBlender(k1, k2, k3, k4, enforcePremul)
    }

    /**
     * Skia's `SK_ScalarNearlyEqual` tolerance — `1/4096`,
     * empirically chosen to absorb FP error on identity-like
     * coefficient inputs without false-positives on intentionally-
     * close values. Exposed as a top-level constant so unit tests
     * can pin the threshold.
     */
    internal const val NEARLY_EQUAL_TOL: Float = 1f / 4096f

    private fun nearlyEqual(a: Float, b: Float): Boolean =
        kotlin.math.abs(a - b) <= NEARLY_EQUAL_TOL
}

/**
 * Concrete arithmetic blender. Created exclusively via
 * [SkBlenders.Arithmetic] (the constructor is `internal`).
 *
 * The math runs in [SkColor4f] linear-light is [enforcePremul]
 * gates the post-saturate `out_rgb ≤ out_a` clamp, matching
 * upstream's `sk_arithmetic_blend(src, dst, k, pmClamp)` where
 * `pmClamp = enforcePremul ? 0 : 1` selects between
 * `min(out_rgb, out_a)` (premul invariant) and `min(out_rgb,
 * 1)` (already-saturated, non-premul OK).
 */
public class SkArithmeticBlender internal constructor(
    public val k1: Float,
    public val k2: Float,
    public val k3: Float,
    public val k4: Float,
    public val enforcePremul: Boolean,
) : SkBlender() {

    override fun blend(src: SkColor4f, dst: SkColor4f): SkColor4f {
        // `out = saturate(k1·src·dst + k2·src + k3·dst + k4)` per channel.
        val outR = saturate(k1 * src.fR * dst.fR + k2 * src.fR + k3 * dst.fR + k4)
        val outG = saturate(k1 * src.fG * dst.fG + k2 * src.fG + k3 * dst.fG + k4)
        val outB = saturate(k1 * src.fB * dst.fB + k2 * src.fB + k3 * dst.fB + k4)
        val outA = saturate(k1 * src.fA * dst.fA + k2 * src.fA + k3 * dst.fA + k4)
        // pmClamp = enforcePremul ? 0 : 1 ; out_rgb = min(out_rgb,
        // max(out_a, pmClamp)) — when enforcePremul, this becomes
        // min(out_rgb, out_a) ; when not, min(out_rgb, max(out_a, 1)) =
        // out_rgb (already ≤ 1 from saturate).
        val cap = if (enforcePremul) outA else 1f
        return SkColor4f(
            kotlin.math.min(outR, cap),
            kotlin.math.min(outG, cap),
            kotlin.math.min(outB, cap),
            outA,
        )
    }

    private fun saturate(v: Float): Float = v.coerceIn(0f, 1f)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkArithmeticBlender) return false
        return k1 == other.k1 && k2 == other.k2 && k3 == other.k3 && k4 == other.k4 &&
            enforcePremul == other.enforcePremul
    }

    override fun hashCode(): Int {
        var result = k1.hashCode()
        result = 31 * result + k2.hashCode()
        result = 31 * result + k3.hashCode()
        result = 31 * result + k4.hashCode()
        result = 31 * result + enforcePremul.hashCode()
        return result
    }

    override fun toString(): String =
        "SkArithmeticBlender(k1=$k1, k2=$k2, k3=$k3, k4=$k4, premul=$enforcePremul)"
}
