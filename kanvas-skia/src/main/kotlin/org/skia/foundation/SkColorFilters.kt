package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkColorFilters`](https://github.com/google/skia/blob/main/include/core/SkColorFilter.h)
 * factory namespace — the canonical set of [SkColorFilter] builders.
 *
 * Phase 7a (Group A foundation) ships :
 *  - [Matrix] — 4 × 5 affine colour matrix (RGBA in, RGBA + bias out).
 *  - [Table] — independent per-channel 256-entry LUT for ARGB.
 *  - [Compose] — `outer ∘ inner`.
 *  - [Lerp] — `lerp(weight, dst, src)`.
 *  - [Blend] — apply [SkBlendMode] with a fixed `colour` as src and the
 *    pixel as dst — covers e.g. tinting via `SkBlendMode.kModulate`.
 *
 * Phase 7a does **not** ship `Lighting`, `HSLAMatrix`,
 * `LinearToSRGBGamma`, or `SRGBToLinearGamma` — they're trivial
 * follow-ups in subsequent slices.
 */
public object SkColorFilters {

    /**
     * Mirrors Skia's `SkColorFilters::Matrix(const float[20], Clamp)`.
     *
     * The 20-element row-major matrix encodes a 4 × 5 affine map :
     *
     * ```
     *   |R'|   | r11 r12 r13 r14 r15 |   | R |
     *   |G'| = | r21 r22 r23 r24 r25 | * | G |
     *   |B'|   | r31 r32 r33 r34 r35 |   | B |
     *   |A'|   | r41 r42 r43 r44 r45 |   | A |
     *                                   | 1 |
     * ```
     *
     * with R, G, B, A non-premultiplied in `[0, 1]`. The 5th column
     * is an additive bias applied after the linear mix. The result is
     * not clamped here ; the device clamps before the per-pixel
     * blend (Skia's "Clamp" parameter is therefore implicit in our
     * pipeline — we always clamp on the storage edge).
     *
     * @throws IllegalArgumentException if [matrix].size != 20.
     */
    public fun Matrix(matrix: FloatArray): SkColorFilter {
        require(matrix.size == 20) { "Matrix expects 20 floats, got ${matrix.size}" }
        return SkMatrixColorFilter(matrix.copyOf())
    }

    /**
     * Mirrors Skia's `SkColorFilters::TableARGB(...)`. Each of A / R /
     * G / B is mapped through its own 256-entry LUT after the input
     * is quantised to 8 bits per channel. Pass `null` for any channel
     * to keep it identity (passes through unchanged).
     *
     * @throws IllegalArgumentException if any non-null table doesn't
     * have exactly 256 entries.
     */
    public fun TableARGB(
        a: ByteArray? = null,
        r: ByteArray? = null,
        g: ByteArray? = null,
        b: ByteArray? = null,
    ): SkColorFilter {
        fun checkOrIdentity(t: ByteArray?, name: String): ByteArray {
            if (t == null) return identityTable
            require(t.size == 256) { "$name table must have 256 entries, got ${t.size}" }
            return t.copyOf()
        }
        return SkTableColorFilter(
            checkOrIdentity(a, "alpha"),
            checkOrIdentity(r, "red"),
            checkOrIdentity(g, "green"),
            checkOrIdentity(b, "blue"),
        )
    }

    /**
     * Mirrors Skia's `SkColorFilters::Table(uint8_t[256])` — same
     * 256-entry LUT applied to all four channels.
     */
    public fun Table(table: ByteArray): SkColorFilter {
        require(table.size == 256) { "Table must have 256 entries, got ${table.size}" }
        val copy = table.copyOf()
        return SkTableColorFilter(copy, copy, copy, copy)
    }

    /**
     * Mirrors Skia's `SkColorFilters::Compose(outer, inner)`. Returns
     * a filter that applies [inner] first, then [outer] to the
     * result : `Compose(o, i).filter(c) == o.filter(i.filter(c))`.
     */
    public fun Compose(outer: SkColorFilter, inner: SkColorFilter): SkColorFilter =
        SkComposeColorFilter(outer, inner)

    /**
     * Mirrors Skia's `SkColorFilters::Lerp(t, dst, src)`. Linearly
     * interpolates between [dst].filter(c) (at `t = 0`) and
     * [src].filter(c) (at `t = 1`). [t] is clamped to `[0, 1]`.
     */
    public fun Lerp(t: Float, dst: SkColorFilter, src: SkColorFilter): SkColorFilter =
        SkLerpColorFilter(t.coerceIn(0f, 1f), dst, src)

    /**
     * Mirrors Skia's `SkColorFilters::Blend(SkColor, SkBlendMode)` —
     * the resulting filter blends `colour` (as the *src*) with the
     * input pixel (as the *dst*) under [mode]. Useful for tints
     * (`Blend(0xFF66AAFF, kModulate)`) and dst-replacement
     * (`Blend(c, kSrcIn)` keeps the pixel's alpha but uses `c.rgb`).
     */
    public fun Blend(colour: SkColor, mode: SkBlendMode): SkColorFilter =
        SkBlendColorFilter(colour, mode)

    /** Lazy identity LUT shared by [TableARGB] for null channels. */
    private val identityTable: ByteArray = ByteArray(256) { it.toByte() }
}

// -- Internal concrete implementations --------------------------------------

/**
 * 4 × 5 affine colour matrix. Operates on non-premul `[0, 1]` floats ;
 * out-of-range outputs are passed through and clamped by the device.
 */
internal class SkMatrixColorFilter(private val m: FloatArray) : SkColorFilter() {
    init { check(m.size == 20) }

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val r = src.fR; val g = src.fG; val b = src.fB; val a = src.fA
        val outR = m[0]  * r + m[1]  * g + m[2]  * b + m[3]  * a + m[4]
        val outG = m[5]  * r + m[6]  * g + m[7]  * b + m[8]  * a + m[9]
        val outB = m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]
        val outA = m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19]
        return SkColorFloats(outR, outG, outB, outA)
    }

    override fun isAlphaUnchanged(): Boolean {
        // Alpha row leaves channels untouched and adds no bias when
        // [m15..m18] == [0, 0, 0, 1] and m19 == 0.
        return m[15] == 0f && m[16] == 0f && m[17] == 0f && m[18] == 1f && m[19] == 0f
    }
}

/**
 * Per-channel 256-entry LUT. Each channel is quantised to 8 bits via
 * round-half-up, indexed into its lookup table, decoded back to float.
 * Mirrors the upstream "TableARGB" semantics (pre-decode unpremul,
 * apply, repremul handled by the device).
 */
internal class SkTableColorFilter(
    private val tA: ByteArray,
    private val tR: ByteArray,
    private val tG: ByteArray,
    private val tB: ByteArray,
) : SkColorFilter() {
    init {
        check(tA.size == 256 && tR.size == 256 && tG.size == 256 && tB.size == 256)
    }

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val ai = quantise(src.fA)
        val ri = quantise(src.fR)
        val gi = quantise(src.fG)
        val bi = quantise(src.fB)
        return SkColorFloats(
            (tR[ri].toInt() and 0xFF) / 255f,
            (tG[gi].toInt() and 0xFF) / 255f,
            (tB[bi].toInt() and 0xFF) / 255f,
            (tA[ai].toInt() and 0xFF) / 255f,
        )
    }

    private fun quantise(v: Float): Int =
        (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
}

/** `outer.filter(inner.filter(src))`. */
internal class SkComposeColorFilter(
    private val outer: SkColorFilter,
    private val inner: SkColorFilter,
) : SkColorFilter() {
    override fun filterColor4f(src: SkColor4f): SkColor4f =
        outer.filterColor4f(inner.filterColor4f(src))

    override fun isAlphaUnchanged(): Boolean =
        outer.isAlphaUnchanged() && inner.isAlphaUnchanged()
}

/** `lerp(t, dst.filter(src), src_filter.filter(src))`. */
internal class SkLerpColorFilter(
    private val t: Float,
    private val dst: SkColorFilter,
    private val srcFilter: SkColorFilter,
) : SkColorFilter() {
    init { check(t in 0f..1f) }

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val a = dst.filterColor4f(src)
        val b = srcFilter.filterColor4f(src)
        val u = 1f - t
        return SkColorFloats(
            a.fR * u + b.fR * t,
            a.fG * u + b.fG * t,
            a.fB * u + b.fB * t,
            a.fA * u + b.fA * t,
        )
    }
}

/**
 * Apply [mode] with the fixed [colour] as src and the input pixel as
 * dst. Equivalent to a single-pixel blend evaluated through the
 * existing [SkBlendMode] dispatch. Operates in non-premul → premul →
 * blend → unpremul space so the output composes correctly.
 */
internal class SkBlendColorFilter(
    private val colour: SkColor,
    private val mode: SkBlendMode,
) : SkColorFilter() {
    private val src4f: SkColor4f = SkColor4f(
        SkColorGetR(colour) / 255f,
        SkColorGetG(colour) / 255f,
        SkColorGetB(colour) / 255f,
        SkColorGetA(colour) / 255f,
    )

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        // Premul both sides.
        val sa = src4f.fA
        val sr = src4f.fR * sa
        val sg = src4f.fG * sa
        val sb = src4f.fB * sa
        val da = src.fA
        val dr = src.fR * da
        val dg = src.fG * da
        val db = src.fB * da
        val out = blendPremul(sr, sg, sb, sa, dr, dg, db, da, mode)
        // Unpremul.
        val oa = out[3]
        if (oa <= 0f) return SkColorFloats(0f, 0f, 0f, 0f)
        val invA = 1f / oa
        return SkColorFloats(out[0] * invA, out[1] * invA, out[2] * invA, oa)
    }
}

/**
 * Construct an [SkColor4f] from raw floats without going through the
 * SkColor4f packed-Int constructor (which would clamp + quantise).
 * Skia's SkColor4f keeps channels as raw floats — out-of-range values
 * are preserved, the device clamps on storage.
 */
internal fun SkColorFloats(r: Float, g: Float, b: Float, a: Float): SkColor4f =
    SkColor4f(r, g, b, a)

/**
 * Premul-float blend over all 29 [SkBlendMode] values, mirroring the
 * device's [org.skia.core.SkBitmapDevice]'s `blendF16PremulMode` core
 * but as a pure function (no bitmap I/O). Used by [SkBlendColorFilter]
 * and the colorFilter unit tests.
 *
 * Inputs are premultiplied floats in `[0, 1]` ; output is a 4-element
 * premul tuple. Intermediate values may exceed `[0, 1]` (kPlus, etc.)
 * — the caller is responsible for clamping before storage.
 */
internal fun blendPremul(
    sr: Float, sg: Float, sb: Float, sa: Float,
    dr: Float, dg: Float, db: Float, da: Float,
    mode: SkBlendMode,
): FloatArray {
    val out = FloatArray(4)
    when (mode) {
        SkBlendMode.kClear -> { /* zeros */ }
        SkBlendMode.kSrc -> { out[0] = sr; out[1] = sg; out[2] = sb; out[3] = sa }
        SkBlendMode.kDst -> { out[0] = dr; out[1] = dg; out[2] = db; out[3] = da }
        SkBlendMode.kSrcOver -> {
            val k = 1f - sa
            out[0] = sr + dr * k; out[1] = sg + dg * k
            out[2] = sb + db * k; out[3] = sa + da * k
        }
        SkBlendMode.kDstOver -> {
            val k = 1f - da
            out[0] = dr + sr * k; out[1] = dg + sg * k
            out[2] = db + sb * k; out[3] = da + sa * k
        }
        SkBlendMode.kSrcIn -> {
            out[0] = sr * da; out[1] = sg * da; out[2] = sb * da; out[3] = sa * da
        }
        SkBlendMode.kDstIn -> {
            out[0] = dr * sa; out[1] = dg * sa; out[2] = db * sa; out[3] = da * sa
        }
        SkBlendMode.kSrcOut -> {
            val k = 1f - da
            out[0] = sr * k; out[1] = sg * k; out[2] = sb * k; out[3] = sa * k
        }
        SkBlendMode.kDstOut -> {
            val k = 1f - sa
            out[0] = dr * k; out[1] = dg * k; out[2] = db * k; out[3] = da * k
        }
        SkBlendMode.kSrcATop -> {
            val k = 1f - sa
            out[0] = sr * da + dr * k
            out[1] = sg * da + dg * k
            out[2] = sb * da + db * k
            out[3] = sa * da + da * k
        }
        SkBlendMode.kDstATop -> {
            val k = 1f - da
            out[0] = dr * sa + sr * k
            out[1] = dg * sa + sg * k
            out[2] = db * sa + sb * k
            out[3] = da * sa + sa * k
        }
        SkBlendMode.kXor -> {
            val ks = 1f - sa
            val kd = 1f - da
            out[0] = sr * kd + dr * ks
            out[1] = sg * kd + dg * ks
            out[2] = sb * kd + db * ks
            out[3] = sa * kd + da * ks
        }
        SkBlendMode.kPlus -> {
            out[0] = (sr + dr).coerceAtMost(1f)
            out[1] = (sg + dg).coerceAtMost(1f)
            out[2] = (sb + db).coerceAtMost(1f)
            out[3] = (sa + da).coerceAtMost(1f)
        }
        SkBlendMode.kModulate -> {
            out[0] = sr * dr; out[1] = sg * dg; out[2] = sb * db; out[3] = sa * da
        }
        SkBlendMode.kScreen -> {
            out[0] = sr + dr - sr * dr
            out[1] = sg + dg - sg * dg
            out[2] = sb + db - sb * db
            out[3] = sa + da - sa * da
        }
        // Separable + HSL [SkBlendMode]s are not yet supported as
        // SkBlendColorFilter inputs — they're rare in practice (tint
        // filters use kModulate / kSrcIn / kSrcOver) and the in-device
        // implementations live behind `internal` visibility in the
        // `org.skia.core` package, which `org.skia.foundation` can't
        // reach without a refactor. A follow-up slice will hoist the
        // pure-float blend dispatcher out of [org.skia.core.SkBitmapDevice]
        // and reuse it here.
        else -> throw UnsupportedOperationException(
            "SkBlendColorFilter does not support separable/HSL mode $mode yet — " +
                "use a SkColorMatrix or open a follow-up to hoist the float blend dispatcher.",
        )
    }
    return out
}
