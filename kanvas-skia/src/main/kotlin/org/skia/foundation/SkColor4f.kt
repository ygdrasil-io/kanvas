package org.skia.foundation

/**
 * Iso-aligned port of Skia's `SkColor4f` (= `SkRGBA4f<kUnpremul_SkAlphaType>`,
 * see [include/core/SkColor.h](https://github.com/google/skia/blob/main/include/core/SkColor.h)).
 *
 * Float-component ARGB color, **non-premultiplied** by default. Each
 * channel is in `[0, 1]` for in-gamut colours; values outside that
 * range are permitted (HDR / wide-gamut workflows) but [toSkColor]
 * clamps to byte range when encoding back to a packed sRGB byte colour.
 *
 * **Premul tag:** Skia uses a template parameter (`kAT`) to track
 * whether a `SkRGBA4f` is premultiplied or not. The Kotlin port uses
 * a single class — [premul] / [unpremul] return the converted form
 * but the type tag is the caller's responsibility.
 */
public data class SkColor4f(
    public var fR: Float,
    public var fG: Float,
    public var fB: Float,
    public var fA: Float,
) {
    /** Returns `[fR, fG, fB, fA]` as a fresh array. */
    public fun vec(): FloatArray = floatArrayOf(fR, fG, fB, fA)

    /** Same as [vec]. Mirrors Skia's `SkRGBA4f::array()`. */
    public fun array(): FloatArray = vec()

    /**
     * Indexed channel access: `0 = R`, `1 = G`, `2 = B`, `3 = A`.
     * Throws on out-of-range index (Skia asserts in debug).
     */
    public operator fun get(index: Int): Float = when (index) {
        0 -> fR; 1 -> fG; 2 -> fB; 3 -> fA
        else -> throw IndexOutOfBoundsException("SkColor4f index $index outside [0, 3]")
    }

    /** Returns `true` if alpha is exactly `1.0f`. */
    public fun isOpaque(): Boolean = fA == 1.0f

    /** Returns `true` iff every channel is in `[0, 1]` (no clamping needed for byte encode). */
    public fun fitsInBytes(): Boolean =
        fA in 0f..1f && fR in 0f..1f && fG in 0f..1f && fB in 0f..1f

    /** Component-wise scalar multiply: `(R*s, G*s, B*s, A*s)`. */
    public operator fun times(scale: Float): SkColor4f =
        SkColor4f(fR * scale, fG * scale, fB * scale, fA * scale)

    /** Component-wise multiply: `(R*o.R, G*o.G, B*o.B, A*o.A)`. */
    public operator fun times(other: SkColor4f): SkColor4f =
        SkColor4f(fR * other.fR, fG * other.fG, fB * other.fB, fA * other.fA)

    /** Returns a copy with `fA = 1`. */
    public fun makeOpaque(): SkColor4f = SkColor4f(fR, fG, fB, 1f)

    /** Returns a copy with alpha pinned to `[0, 1]`. */
    public fun pinAlpha(): SkColor4f = SkColor4f(fR, fG, fB, fA.coerceIn(0f, 1f))

    /** Returns a copy with alpha replaced by `a`. */
    public fun withAlpha(a: Float): SkColor4f = SkColor4f(fR, fG, fB, a)

    /** Returns a copy with alpha replaced by `a / 255`. */
    public fun withAlphaByte(a: Int): SkColor4f = SkColor4f(fR, fG, fB, (a and 0xFF) / 255f)

    /**
     * Premultiply RGB by alpha — call when treating `this` as unpremul.
     * The result is *also* a `SkColor4f` but represents a premul color
     * (Skia's `SkRGBA4f<kPremul>`); the caller tracks the tag.
     */
    public fun premul(): SkColor4f = SkColor4f(fR * fA, fG * fA, fB * fA, fA)

    /**
     * Unpremultiply RGB by alpha — call when treating `this` as premul.
     * If `fA == 0`, returns `(0, 0, 0, 0)` (matches Skia which would
     * otherwise divide by zero).
     */
    public fun unpremul(): SkColor4f {
        if (fA == 0f) return SkColor4f(0f, 0f, 0f, 0f)
        val inv = 1f / fA
        return SkColor4f(fR * inv, fG * inv, fB * inv, fA)
    }

    /**
     * Pack to a 32-bit RGBA-byte-order value (R in MSB, A in LSB) as
     * Skia's `toBytes_RGBA`. Different layout from [toSkColor], which
     * uses ARGB byte order.
     */
    public fun toBytes_RGBA(): Int =
        (channelToByte(fR) shl 24) or
            (channelToByte(fG) shl 16) or
            (channelToByte(fB) shl 8) or
            channelToByte(fA)

    /**
     * Encode to a packed ARGB8888 [SkColor]. Channels outside `[0, 1]`
     * are clamped. Mirrors Skia's `SkColor4f::toSkColor`.
     */
    public fun toSkColor(): SkColor =
        SkColorSetARGB(
            channelToByte(fA), channelToByte(fR),
            channelToByte(fG), channelToByte(fB),
        )

    public companion object {
        public val kTransparent: SkColor4f = SkColor4f(0f, 0f, 0f, 0f)
        public val kBlack: SkColor4f = SkColor4f(0f, 0f, 0f, 1f)
        public val kDkGray: SkColor4f = SkColor4f(0.25f, 0.25f, 0.25f, 1f)
        public val kGray: SkColor4f = SkColor4f(0.5f, 0.5f, 0.5f, 1f)
        public val kLtGray: SkColor4f = SkColor4f(0.75f, 0.75f, 0.75f, 1f)
        public val kWhite: SkColor4f = SkColor4f(1f, 1f, 1f, 1f)
        public val kRed: SkColor4f = SkColor4f(1f, 0f, 0f, 1f)
        public val kGreen: SkColor4f = SkColor4f(0f, 1f, 0f, 1f)
        public val kBlue: SkColor4f = SkColor4f(0f, 0f, 1f, 1f)
        public val kYellow: SkColor4f = SkColor4f(1f, 1f, 0f, 1f)
        public val kCyan: SkColor4f = SkColor4f(0f, 1f, 1f, 1f)
        public val kMagenta: SkColor4f = SkColor4f(1f, 0f, 1f, 1f)

        /** Decode an ARGB8888 [SkColor] into normalised floats. */
        public fun FromColor(c: SkColor): SkColor4f = SkColor4f(
            fR = SkColorGetR(c) * INV_255,
            fG = SkColorGetG(c) * INV_255,
            fB = SkColorGetB(c) * INV_255,
            fA = SkColorGetA(c) * INV_255,
        )

        /**
         * Decode an [SkPMColor] (premultiplied) — produces a premul
         * `SkColor4f`. Caller tracks the premul tag (Skia uses the C++
         * template parameter).
         */
        public fun FromPMColor(c: SkPMColor): SkColor4f = FromColor(c)

        /**
         * Decode a 32-bit RGBA-byte-order value (R in MSB, A in LSB).
         * Mirrors Skia's `FromBytes_RGBA`.
         */
        public fun FromBytes_RGBA(rgba: Int): SkColor4f = SkColor4f(
            fR = ((rgba ushr 24) and 0xFF) * INV_255,
            fG = ((rgba ushr 16) and 0xFF) * INV_255,
            fB = ((rgba ushr 8) and 0xFF) * INV_255,
            fA = (rgba and 0xFF) * INV_255,
        )

        private const val INV_255: Float = 1f / 255f

        private fun channelToByte(v: Float): Int {
            // Round-half-to-even-ish via +0.5 floor: faithful to Skia's
            // `SkScalarRoundToInt(255 * v)`.
            val clamped = when {
                v <= 0f -> 0f
                v >= 1f -> 1f
                else -> v
            }
            val rounded = (clamped * 255f + 0.5f).toInt()
            return if (rounded < 0) 0 else if (rounded > 255) 255 else rounded
        }
    }
}
