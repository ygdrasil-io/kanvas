package org.skia.foundation

import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import org.skia.skcms.SkcmsMatrix3x3
import org.skia.skcms.SkcmsTFType
import org.skia.skcms.SkcmsTransferFunction
import org.skia.skcms.classify
import org.skia.skcms.skcmsTransferFunctionInvert

/**
 * Bit-compatible port of the chunk of `SkColorSpace` that GM rendering needs:
 * an immutable bundle of (transfer function, toXYZD50 matrix) plus a hash
 * pair for fast equality.
 *
 * Phase 2 leaves out:
 *  - ICC parsing (`Make(profile)`) — Phase 6.
 *  - serialize/deserialize — Phase 7.
 *  - `MakeCICP`, `makeColorSpin` — not needed.
 *  - `gamutTransformTo`, `invTransferFn` exposed externally — handled inside
 *    `SkColorSpaceXformSteps` instead.
 */
public class SkColorSpace private constructor(
    public val transferFn: SkcmsTransferFunction,
    public val toXYZD50: SkcmsMatrix3x3,
) {
    public val transferFnHash: Int = hashFloats(
        transferFn.g, transferFn.a, transferFn.b, transferFn.c,
        transferFn.d, transferFn.e, transferFn.f,
    )
    public val toXYZD50Hash: Int = run {
        val xs = FloatArray(9)
        for (r in 0 until 3) for (c in 0 until 3) xs[r * 3 + c] = toXYZD50.vals[r][c]
        hashFloats(*xs)
    }

    /**
     * Lazy inverse fields, computed once. We don't fail if the matrix or TF
     * is non-invertible — we fall back to sRGB to mirror upstream behavior
     * (`computeLazyDstFields` in `SkColorSpace.cpp`).
     */
    private val lazyDst: LazyDst by lazy { computeLazyDst() }

    public val fromXYZD50: SkcmsMatrix3x3 get() = lazyDst.fromXYZD50
    public val invTransferFn: SkcmsTransferFunction get() = lazyDst.invTransferFn

    public fun gammaCloseToSRGB(): Boolean = transferFn == SkNamedTransferFn.kSRGB
    public fun gammaIsLinear(): Boolean = transferFn == SkNamedTransferFn.kLinear
    public fun isSRGB(): Boolean = this === sRGBSingleton

    public fun hash(): Long =
        (transferFnHash.toLong() shl 32) or (toXYZD50Hash.toLong() and 0xFFFFFFFFL)

    private fun computeLazyDst(): LazyDst {
        val invMat = org.skia.skcms.skcmsMatrix3x3Invert(toXYZD50)
            ?: org.skia.skcms.skcmsMatrix3x3Invert(SkNamedGamut.kSRGB)!!
        val invTf = skcmsTransferFunctionInvert(transferFn)
            ?: skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)!!
        return LazyDst(invMat, invTf)
    }

    private data class LazyDst(
        val fromXYZD50: SkcmsMatrix3x3,
        val invTransferFn: SkcmsTransferFunction,
    )

    public companion object {
        public fun makeSRGB(): SkColorSpace = sRGBSingleton

        public fun makeSRGBLinear(): SkColorSpace = sRGBLinearSingleton

        /**
         * `MakeRGB(tf, mat)`. Returns `null` if `tf` is not a valid sRGBish
         * transfer function. Snaps to the sRGB or sRGBLinear singleton when
         * the parameters match (so `Equals` becomes pointer-equality for
         * common cases — same as upstream).
         */
        public fun makeRGB(
            transferFn: SkcmsTransferFunction,
            toXYZ: SkcmsMatrix3x3,
        ): SkColorSpace? {
            if (classify(transferFn) != SkcmsTFType.sRGBish) return null
            if (transferFn == SkNamedTransferFn.kSRGB &&
                toXYZ == SkNamedGamut.kSRGB) return sRGBSingleton
            if (transferFn == SkNamedTransferFn.kLinear &&
                toXYZ == SkNamedGamut.kSRGB) return sRGBLinearSingleton
            return SkColorSpace(transferFn, toXYZ)
        }

        public fun equals(a: SkColorSpace?, b: SkColorSpace?): Boolean {
            if (a === b) return true
            if (a == null || b == null) return false
            return a.hash() == b.hash()
        }

        private fun hashFloats(vararg xs: Float): Int {
            // Match the C++ `SkOpts::hash_fn` only loosely — what we need is
            // stability and low collision rate, not bit-equality with skia.
            var h = 0x811C9DC5.toInt()
            for (x in xs) {
                h = (h xor x.toRawBits()) * 0x01000193
            }
            return h
        }

        private val sRGBSingleton: SkColorSpace =
            SkColorSpace(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)

        private val sRGBLinearSingleton: SkColorSpace =
            SkColorSpace(SkNamedTransferFn.kLinear, SkNamedGamut.kSRGB)
    }
}
