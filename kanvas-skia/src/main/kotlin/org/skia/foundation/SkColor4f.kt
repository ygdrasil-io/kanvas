package org.skia.foundation

/**
 * Floating-point, **non-premultiplied** colour with sRGB-byte interop.
 *
 * Mirrors Skia's `SkColor4f` (= `SkRGBA4f<kUnpremul>`). Each channel is a
 * float in `[0, 1]` for in-gamut colours; values outside that range are
 * permitted (e.g. for HDR / wide-gamut workflows) but [toSkColor] clamps
 * to byte range when encoding back to a packed sRGB byte colour.
 *
 * **Colour-management scope.** This class is the storage / conversion
 * primitive only. The colour-management pipeline (gamut transforms,
 * profile-aware encode / decode) lives outside foundation and is being
 * built in a parallel branch. [FromColor] / [toSkColor] currently treat
 * the packed `SkColor` byte channels as straight sRGB-byte to/from a
 * normalised float, which is the contract Skia gives when no
 * `SkColorSpace` is supplied.
 */
public data class SkColor4f(
    public var fR: Float,
    public var fG: Float,
    public var fB: Float,
    public var fA: Float,
) {
    public fun vec(): FloatArray = floatArrayOf(fR, fG, fB, fA)

    /**
     * Returns true iff every channel is in `[0, 1]` after rounding to
     * 8 bits — i.e. the colour can be encoded to [SkColor] without
     * lossy clamping beyond the 8-bit quantisation.
     */
    public fun fitsInBytes(): Boolean =
        fA in 0f..1f && fR in 0f..1f && fG in 0f..1f && fB in 0f..1f

    /**
     * Encode to a packed ARGB8888 [SkColor]. Channels outside `[0, 1]`
     * are clamped. Mirrors Skia's `SkColor4f::toSkColor`.
     */
    public fun toSkColor(): SkColor =
        SkColorSetARGB(
            channelToByte(fA),
            channelToByte(fR),
            channelToByte(fG),
            channelToByte(fB),
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

        /**
         * Decode an ARGB8888 [SkColor] into normalised floats. The
         * inverse of [toSkColor] up to 8-bit quantisation.
         */
        public fun FromColor(c: SkColor): SkColor4f = SkColor4f(
            fR = SkColorGetR(c) * INV_255,
            fG = SkColorGetG(c) * INV_255,
            fB = SkColorGetB(c) * INV_255,
            fA = SkColorGetA(c) * INV_255,
        )

        private const val INV_255: Float = 1f / 255f

        private fun channelToByte(v: Float): Int {
            // Round-half-to-even-ish via +0.5 floor: faithful to Skia's
            // `SkColor4f::toSkColor` which does `SkScalarRoundToInt(255*v)`.
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
