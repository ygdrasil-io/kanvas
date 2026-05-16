package org.skia.foundation

import org.skia.math.SkcmsTransferFunction

/**
 * Bit-compatible alias for `skcms_TransferFunction` exposed under the
 * public `SkColorSpace*` namespace, mirroring how upstream Skia's
 * public C++ API exposes `skcms_TransferFunction` as the parameter
 * type for [SkColorSpace.MakeRGB].
 *
 * Skia keeps `skcms_TransferFunction` itself in `skcms.h`, but the
 * `SkColorSpace::MakeRGB(const skcms_TransferFunction&, const skcms_Matrix3x3&)`
 * factory's parameter is the same 7-float record. We provide a
 * mirrored data class here so that the [SkColorSpace] factory surface
 * is fully self-contained (a caller writing GM code never needs to
 * reach into the `org.skia.skcms` package just to invoke the
 * canonical "sRGB transfer / Rec.709 primaries" pattern).
 *
 * Conversions to/from [SkcmsTransferFunction] are bit-identical
 * (same float order : `g, a, b, c, d, e, f`).
 *
 * For an sRGBish transfer function, encoded → linear evaluates as:
 * ```
 *   y = (a*x + b)^g + e   for x >= d
 *   y = c*x + f           for x <  d
 * ```
 *
 * The standard sRGB constants are
 * `g = 2.4, a = 1/1.055, b = 0.055/1.055, c = 1/12.92, d = 0.04045,
 * e = 0, f = 0`.
 */
public data class SkColorSpaceTransferFn(
    public val g: Float,
    public val a: Float,
    public val b: Float,
    public val c: Float,
    public val d: Float,
    public val e: Float,
    public val f: Float,
) {
    /** Convert to the internal `skcms_TransferFunction` record. */
    public fun toSkcms(): SkcmsTransferFunction =
        SkcmsTransferFunction(g, a, b, c, d, e, f)

    public companion object {
        /** Standard sRGB transfer function (`g=2.4`, ICC-spec coefficients). */
        public val kSRGB: SkColorSpaceTransferFn = SkColorSpaceTransferFn(
            g = 2.4f,
            a = 1f / 1.055f,
            b = 0.055f / 1.055f,
            c = 1f / 12.92f,
            d = 0.04045f,
            e = 0f,
            f = 0f,
        )

        /** Linear (identity) transfer function : `y = x`. */
        public val kLinear: SkColorSpaceTransferFn =
            SkColorSpaceTransferFn(1f, 1f, 0f, 0f, 0f, 0f, 0f)

        /** Convert from the internal `skcms_TransferFunction` record. */
        public fun fromSkcms(tf: SkcmsTransferFunction): SkColorSpaceTransferFn =
            SkColorSpaceTransferFn(tf.g, tf.a, tf.b, tf.c, tf.d, tf.e, tf.f)
    }
}
