package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorMatrix
import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import kotlin.math.pow

/**
 * Mirrors Skia's
 * [`SkColorFilters`](https://github.com/google/skia/blob/main/include/core/SkColorFilter.h)
 * factory namespace — the canonical set of [SkColorFilter] builders.
 *
 * Ships (Phase 7a foundation + R1-B follow-up):
 *  - [Matrix] — 4 × 5 affine colour matrix (RGBA in, RGBA + bias out).
 *  - [Table] — independent per-channel 256-entry LUT for ARGB.
 *  - [Compose] — `outer ∘ inner`.
 *  - [Lerp] — `lerp(weight, dst, src)` (non-null and nullable overloads).
 *  - [Blend] — apply [SkBlendMode] with a fixed `colour` as src and the
 *    pixel as dst — covers e.g. tinting via `SkBlendMode.kModulate`.
 *  - [Lighting] — `c * mul + add`, RGB-only (alpha untouched).
 *  - [LinearToSRGBGamma] / [SRGBToLinearGamma] — IEC 61966-2-1 transfer
 *    function and its inverse, applied per RGB channel (alpha is left
 *    alone). Useful for hand-rolled linear-space compositing where the
 *    raster surface is still 8-bit sRGB-encoded.
 *
 * R1-B does **not** ship `HSLAMatrix` — deferred to a later slice when
 * we need it for the upstream colour-matrix GMs.
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
     * Mirrors Skia's `SkColorFilters::Matrix(const SkColorMatrix&)`
     * overload. Delegates to the 20-float form via
     * [SkColorMatrix.getRowMajor], so the resulting filter is bit-
     * identical to the one produced from
     * `Matrix(matrix.toFloatArray())`.
     */
    public fun Matrix(matrix: SkColorMatrix): SkColorFilter {
        val rowMajor = FloatArray(20)
        matrix.getRowMajor(rowMajor)
        return Matrix(rowMajor)
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

    /**
     * Mirrors Skia's
     * `SkColorFilters::Blend(const SkColor4f&, sk_sp<SkColorSpace>, SkBlendMode)` —
     * the F32 + colour-space overload. The [colour] is expressed in the
     * given [colorSpace] (or sRGB if `null`); upstream converts it to the
     * destination colour space before blending.
     *
     * kanvas-skia's colour-filter pipeline evaluates filters in sRGB
     * working space, so the fixed [colour] is transformed from
     * [colorSpace] (or sRGB for `null`) to sRGB when the filter is built.
     */
    public fun Blend(colour: SkColor4f, colorSpace: SkColorSpace?, mode: SkBlendMode): SkColorFilter =
        SkBlendColor4fFilter(colour, colorSpace, mode)

    /**
     * Mirrors Skia's `SkColorFilters::Lighting(SkColor mul, SkColor add)`.
     * Multiplies each RGB channel of the input by the corresponding byte
     * from [mul] (treated as `[0, 1]` after `/ 255`), then adds the byte
     * from [add] (same scaling). Alpha is **untouched** — the upstream
     * implementation explicitly ignores the alpha channel of both args.
     *
     * Equivalent to `Matrix(diag(mulR, mulG, mulB, 1) + bias(addR, addG, addB, 0))`.
     * The output is not clamped here — the device clamps before storage.
     */
    public fun Lighting(mul: SkColor, add: SkColor): SkColorFilter {
        val mr = SkColorGetR(mul) / 255f
        val mg = SkColorGetG(mul) / 255f
        val mb = SkColorGetB(mul) / 255f
        val ar = SkColorGetR(add) / 255f
        val ag = SkColorGetG(add) / 255f
        val ab = SkColorGetB(add) / 255f
        return Matrix(
            floatArrayOf(
                mr, 0f, 0f, 0f, ar,
                0f, mg, 0f, 0f, ag,
                0f, 0f, mb, 0f, ab,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    /**
     * Mirrors Skia's `SkColorFilters::LinearToSRGBGamma`. Applies the
     * IEC 61966-2-1 sRGB encoding curve to each RGB channel — i.e.
     * treats the input channel as linear-light intensity and encodes it
     * to sRGB-display-referred. Alpha is passed through unchanged
     * (this is a per-channel non-linear function, not a matrix).
     *
     * Implementation is a dedicated [SkSRGBGammaColorFilter] subclass
     * — a matrix can't express the non-linear segment cleanly.
     */
    public fun LinearToSRGBGamma(): SkColorFilter = SkSRGBGammaColorFilter(toSRGB = true)

    /**
     * Mirrors Skia's `SkColorFilters::SRGBToLinearGamma` — inverse of
     * [LinearToSRGBGamma]. Treats each RGB channel as sRGB-encoded and
     * decodes it back to linear light.
     */
    public fun SRGBToLinearGamma(): SkColorFilter = SkSRGBGammaColorFilter(toSRGB = false)

    /**
     * Nullable overload of [Lerp] that mirrors Skia's upstream
     * `SkColorFilters::Lerp(float, sk_sp<SkColorFilter>, sk_sp<SkColorFilter>)`
     * exactly. Per upstream's `SkColorFilter.cpp` :
     *
     * ```
     *  sk_sp<SkColorFilter> SkColorFilters::Lerp(float t,
     *      sk_sp<SkColorFilter> cf0, sk_sp<SkColorFilter> cf1) {
     *      if (!cf0 && !cf1) return nullptr;
     *      ...
     *  }
     * ```
     *
     * A `nullptr` child in the upstream code path means **"use the
     * unfiltered input"** (true pass-through), **NOT** "apply the
     * identity colour filter". The distinction matters because :
     *  - `lerp(t, null, src)` should evaluate to
     *    `lerp(t, input, src.filter(input))` — i.e. blend the *raw*
     *    pixel with the filtered version.
     *  - Substituting `kIdentity` for `null` is *also* correct for the
     *    pure-Skia identity filter (which by definition returns its
     *    input unchanged), but it conflates two semantically different
     *    things and breaks symmetry with the upstream `nullptr` test
     *    inside `SkBlendModeColorFilter` and other consumers that
     *    inspect the children.
     *
     * Behaviour after this refactor :
     *  - both `null` ⇒ returns `null` (no-op).
     *  - [t] is `NaN`           ⇒ returns `null` (Skia rejects NaN).
     *  - `t <= 0`               ⇒ returns [dst] verbatim (may be `null`
     *                              ⇒ pass-through on the dst side).
     *  - `t >= 1`               ⇒ returns [src] verbatim (same).
     *  - both non-null          ⇒ existing [SkLerpColorFilter] (2-arg).
     *  - exactly one `null`     ⇒ new [SkPassThroughLerpFilter] that
     *    blends the input with the filtered side.
     *
     * `@JvmName` keeps this overload distinguishable from the non-null
     * variant at the bytecode level (both share the same erased Kotlin
     * signature otherwise — same JVM mangling rule as Kotlin's stdlib
     * nullable overloads).
     */
    @JvmName("LerpNullable")
    public fun Lerp(t: Float, dst: SkColorFilter?, src: SkColorFilter?): SkColorFilter? {
        if (dst == null && src == null) return null
        if (t.isNaN()) return null
        if (dst === src) return dst
        if (t <= 0f) return dst
        if (t >= 1f) return src
        if (dst != null && src != null) return SkLerpColorFilter(t, dst, src)
        // Exactly one of dst / src is null. Build a pass-through lerp
        // that treats the null side as the *unfiltered* input.
        return SkPassThroughLerpFilter(t, dstSide = dst, srcSide = src)
    }

    /** Lazy identity LUT shared by [TableARGB] for null channels. */
    private val identityTable: ByteArray = ByteArray(256) { it.toByte() }
}

// -- Internal concrete implementations --------------------------------------

/**
 * Identity colour filter — returns its input unchanged. Kept as an
 * internal utility for tests and future consumers ; the nullable
 * [SkColorFilters.Lerp] overload (R-suivi.1) no longer routes `null`
 * children through this filter — see [SkPassThroughLerpFilter] for
 * the true pass-through path.
 */
internal object SkIdentityColorFilter : SkColorFilter() {
    override fun filterColor4f(src: SkColor4f): SkColor4f = src
    override fun isAlphaUnchanged(): Boolean = true
}

/**
 * Linear blend `lerp(t, A, B)` where one of A / B is the *unfiltered*
 * input (true upstream pass-through for a `nullptr` filter side) and
 * the other is `child.filterColor4f(input)`.
 *
 * Exactly one of [dstSide] / [srcSide] is non-null — the [SkColorFilters.Lerp]
 * factory guarantees this. The non-null side is named after its role
 * in the lerp formula : at `t == 0` the dst-side dominates, at `t == 1`
 * the src-side dominates.
 *
 * Mirrors Skia's `SkBlendModeColorFilter::Make(SkBlendMode::kSrcOver, ...)`
 * trick for pass-through children — the runtime effect that backs
 * `Lerp` upstream evaluates a `null` child to the original colour,
 * which is exactly what this filter does.
 */
internal class SkPassThroughLerpFilter(
    private val t: Float,
    private val dstSide: SkColorFilter?,
    private val srcSide: SkColorFilter?,
) : SkColorFilter() {
    init {
        check(t in 0f..1f) { "t must be in [0, 1], got $t" }
        check((dstSide == null) != (srcSide == null)) {
            "exactly one of dstSide / srcSide must be null"
        }
    }

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val a = dstSide?.filterColor4f(src) ?: src
        val b = srcSide?.filterColor4f(src) ?: src
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
 * Per-channel IEC 61966-2-1 sRGB transfer function (or its inverse).
 *
 * The forward direction (`toSRGB = true`) maps linear light `L ∈ [0, 1]`
 * to sRGB-encoded `E ∈ [0, 1]` :
 *
 * ```
 *   E = 12.92 * L                       (L <= 0.0031308)
 *   E = 1.055 * L^(1/2.4) - 0.055       (L  > 0.0031308)
 * ```
 *
 * The reverse direction (`toSRGB = false`) is the standard inverse :
 *
 * ```
 *   L = E / 12.92                       (E <= 0.04045)
 *   L = ((E + 0.055) / 1.055)^2.4       (E  > 0.04045)
 * ```
 *
 * Out-of-range inputs are passed through (`< 0` or `> 1`) — Skia
 * clamps at the storage boundary, not here.
 */
internal class SkSRGBGammaColorFilter(private val toSRGB: Boolean) : SkColorFilter() {

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val fn: (Float) -> Float = if (toSRGB) ::linearToSrgb else ::srgbToLinear
        return SkColor4f(fn(src.fR), fn(src.fG), fn(src.fB), src.fA)
    }

    override fun isAlphaUnchanged(): Boolean = true

    private companion object {
        fun linearToSrgb(c: Float): Float {
            if (c.isNaN()) return c
            if (c <= 0f) return c
            if (c >= 1f) return c
            return if (c <= 0.0031308f) 12.92f * c
            else 1.055f * c.pow(1f / 2.4f) - 0.055f
        }

        fun srgbToLinear(c: Float): Float {
            if (c.isNaN()) return c
            if (c <= 0f) return c
            if (c >= 1f) return c
            return if (c <= 0.04045f) c / 12.92f
            else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }
}


/**
 * 4 × 5 affine colour matrix. Operates on non-premul `[0, 1]` floats ;
 * out-of-range outputs are passed through and clamped by the device.
 */
internal class SkMatrixColorFilter(private val m: FloatArray) : SkColorFilter() {
    init { check(m.size == 20) }

    /**
     * Phase G-saveLayer-colorFilter -- read-only handle on the raw
     * 20-float matrix for [asMatrixFilter] extraction. Callers must
     * `copyOf()` before mutating ; the field itself is shared with the
     * filter's per-pixel evaluator.
     */
    internal val exposedMatrix: FloatArray get() = m

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

    /**
     * Phase G-saveLayer-colorFilter -- read-only handles on the
     * constructor args for [asBlendModeFilter] extraction. Both are
     * immutable scalars / enums so direct exposure is safe.
     */
    internal val exposedColour: SkColor get() = colour
    internal val exposedMode: SkBlendMode get() = mode

    private val src4f: SkColor4f = SkColor4f(
        SkColorGetR(colour) / 255f,
        SkColorGetG(colour) / 255f,
        SkColorGetB(colour) / 255f,
        SkColorGetA(colour) / 255f,
    )

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        return blendColor4f(src4f, src, mode)
    }
}

/**
 * F32 variant of [SkBlendColorFilter]. The fixed colour is transformed from
 * its declared colour space into the filter working space once at creation
 * time, preserving float precision instead of round-tripping through 8-bit
 * [SkColor].
 */
internal class SkBlendColor4fFilter(
    colour: SkColor4f,
    colorSpace: SkColorSpace?,
    private val mode: SkBlendMode,
) : SkColorFilter() {
    private val src4f: SkColor4f = transformBlendColorToSrgb(colour, colorSpace)

    override fun filterColor4f(src: SkColor4f): SkColor4f =
        blendColor4f(src4f, src, mode)
}

private fun transformBlendColorToSrgb(colour: SkColor4f, colorSpace: SkColorSpace?): SkColor4f {
    val srcCS = colorSpace ?: SkColorSpace.makeSRGB()
    val rgba = floatArrayOf(colour.fR, colour.fG, colour.fB, colour.fA)
    SkColorSpaceXformSteps(
        src = srcCS,
        srcAT = SkAlphaType.kUnpremul,
        dst = SkColorSpace.makeSRGB(),
        dstAT = SkAlphaType.kUnpremul,
    ).apply(rgba)
    return SkColorFloats(rgba[0], rgba[1], rgba[2], rgba[3])
}

private fun blendColor4f(src4f: SkColor4f, dst4f: SkColor4f, mode: SkBlendMode): SkColor4f {
    val sa = src4f.fA
    val sr = src4f.fR * sa
    val sg = src4f.fG * sa
    val sb = src4f.fB * sa
    val da = dst4f.fA
    val dr = dst4f.fR * da
    val dg = dst4f.fG * da
    val db = dst4f.fB * da
    val out = blendPremul(sr, sg, sb, sa, dr, dg, db, da, mode)
    val oa = out[3]
    if (oa <= 0f) return SkColorFloats(0f, 0f, 0f, 0f)
    val invA = 1f / oa
    return SkColorFloats(out[0] * invA, out[1] * invA, out[2] * invA, oa)
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

// -- Backend-facing extractors ----------------------------------------------
//
// Phase G-saveLayer-colorFilter -- a small public read-only API that lets
// non-foundation backends (e.g. :gpu-raster) detect the common
// [SkColorFilter] shapes without depending on the `internal` concrete
// classes. The variants in scope are the two SkColorFilters that the
// WebGPU layer-composite shader supports : Blend (constant colour applied
// with a [SkBlendMode] to each pixel) and Matrix (4x5 affine RGBA in /
// RGBA out). All other variants return `null` -- backends fall back to
// the no-filter composite path for the unsupported cases (or to CPU
// rasterisation upstream of the dispatch).

/**
 * Read-only descriptor of an [SkColorFilters.Blend] filter -- the constant
 * source colour + the blend mode that combines it with the input pixel.
 * Returned by [SkColorFilter.asBlendModeFilter] when (and only when) the
 * receiver is a `Blend` filter.
 */
public data class SkBlendModeFilterParams(
    /** The constant colour used as the *src* of the per-pixel blend. */
    public val colour: SkColor,
    /** The blend mode applied with [colour] as src and the pixel as dst. */
    public val mode: SkBlendMode,
)

/**
 * Read-only descriptor of an [SkColorFilters.Matrix] filter -- the 20
 * row-major floats of the 4 x 5 affine colour matrix. Returned by
 * [SkColorFilter.asMatrixFilter] when (and only when) the receiver is a
 * `Matrix` filter. The returned array is a defensive copy.
 *
 * Layout : row-major, same as [SkColorFilters.Matrix] :
 *
 * ```
 *   row 0 : r11 r12 r13 r14 r15   (R coefficients + bias)
 *   row 1 : r21 r22 r23 r24 r25
 *   row 2 : r31 r32 r33 r34 r35
 *   row 3 : r41 r42 r43 r44 r45
 * ```
 */
public data class SkMatrixFilterParams(
    /** 20 row-major floats : 4 rows of (R, G, B, A coefficients + bias). */
    public val matrix: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        other is SkMatrixFilterParams && matrix.contentEquals(other.matrix)
    override fun hashCode(): Int = matrix.contentHashCode()
}

/**
 * Extract the parameters of an [SkColorFilters.Blend] filter, or `null`
 * if the receiver is any other [SkColorFilter] variant (matrix, table,
 * compose, lerp, sRGB gamma, working-CS wrapper, ...).
 *
 * Backends that can express the per-pixel `Blend(colour, mode)` in their
 * fragment-side pipeline (e.g. the WebGPU layer composite) use this to
 * decide whether to fold the filter in or fall back to a no-filter
 * composite path.
 */
public fun SkColorFilter.asBlendModeFilter(): SkBlendModeFilterParams? {
    val f = this as? SkBlendColorFilter ?: return null
    return SkBlendModeFilterParams(colour = f.exposedColour, mode = f.exposedMode)
}

/**
 * Extract the row-major 4 x 5 matrix of an [SkColorFilters.Matrix]
 * filter, or `null` if the receiver is any other [SkColorFilter]
 * variant. The returned [FloatArray] is a defensive copy -- mutating it
 * does not affect the underlying filter.
 */
public fun SkColorFilter.asMatrixFilter(): SkMatrixFilterParams? {
    val f = this as? SkMatrixColorFilter ?: return null
    return SkMatrixFilterParams(matrix = f.exposedMatrix.copyOf())
}
