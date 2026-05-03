package org.skia.foundation

import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import org.skia.skcms.SkcmsMatrix3x3
import org.skia.skcms.SkcmsTFType
import org.skia.skcms.SkcmsTransferFunction
import org.skia.skcms.classify
import org.skia.skcms.skcmsMatrix3x3Concat
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

    /**
     * Returns a colorspace with the same gamut as this one but a linear
     * gamma. Mirror of upstream
     * [SkColorSpace.cpp:270-275](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkColorSpace.cpp).
     */
    public fun makeLinearGamma(): SkColorSpace {
        if (gammaIsLinear()) return this
        return makeRGB(SkNamedTransferFn.kLinear, toXYZD50)!!
    }

    /**
     * Returns a colorspace with the same gamut as this one but the sRGB
     * transfer function. Mirror of upstream `SkColorSpace.cpp:277-282`.
     */
    public fun makeSRGBGamma(): SkColorSpace {
        if (gammaCloseToSRGB()) return this
        return makeRGB(SkNamedTransferFn.kSRGB, toXYZD50)!!
    }

    /**
     * Returns a colorspace with the same TF but with the primary colors
     * rotated (RGB → GBR when applied to a source). Used by Skia for
     * testing — three applications return the original. Mirror of
     * `SkColorSpace.cpp:284-294`.
     */
    public fun makeColorSpin(): SkColorSpace {
        val spin = SkcmsMatrix3x3.of(
            0f, 0f, 1f,
            1f, 0f, 0f,
            0f, 1f, 0f,
        )
        val spun = skcmsMatrix3x3Concat(toXYZD50, spin)
        return SkColorSpace(transferFn, spun)
    }

    /**
     * Serialize this colorspace to a fresh byte buffer. Same wire format
     * as upstream `SkColorSpace::serialize` (`SkColorSpace.cpp:441-445`):
     * `[4-byte ColorSpaceHeader][7 floats TF][9 floats matrix]`, total
     * 68 bytes, little-endian.
     */
    public fun serialize(): ByteArray {
        val out = ByteArray(SERIALIZED_SIZE)
        writeToMemory(out)
        return out
    }

    /**
     * Write the serialized representation to [memory]. Mirror of
     * `SkColorSpace::writeToMemory` (`SkColorSpace.cpp:427-439`). Returns
     * the number of bytes written (always 68). Pass `null` to query the
     * required size without writing.
     */
    public fun writeToMemory(memory: ByteArray?): Int {
        if (memory == null) return SERIALIZED_SIZE
        require(memory.size >= SERIALIZED_SIZE) {
            "serialize buffer too small: ${memory.size} < $SERIALIZED_SIZE"
        }

        // ColorSpaceHeader: version=1, 3 reserved bytes (0).
        memory[0] = SERIALIZED_VERSION
        memory[1] = 0
        memory[2] = 0
        memory[3] = 0

        // 7 floats TF, little-endian.
        var off = 4
        for (f in floatArrayOf(transferFn.g, transferFn.a, transferFn.b,
            transferFn.c, transferFn.d, transferFn.e, transferFn.f)) {
            writeFloatLE(memory, off, f); off += 4
        }
        // 9 floats matrix, row-major, little-endian.
        for (r in 0 until 3) for (c in 0 until 3) {
            writeFloatLE(memory, off, toXYZD50.vals[r][c]); off += 4
        }

        return SERIALIZED_SIZE
    }

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
         * Create an `SkColorSpace` from CICP code points (ITU-T H.273
         * tables 2 and 3). Returns `null` if either id is not in the
         * supported tables, or if `MakeRGB` rejects the resulting
         * combination. Mirrors upstream
         * `SkColorSpace::MakeCICP` (`SkColorSpace.cpp:161-174`).
         */
        public fun makeCICP(
            colorPrimaries: SkNamedPrimaries.CicpId,
            transferCharacteristics: SkNamedTransferFn.CicpId,
        ): SkColorSpace? {
            val trfn = SkNamedTransferFn.getCicp(transferCharacteristics) ?: return null
            val toXYZD50 = SkNamedPrimaries.getCicp(colorPrimaries) ?: return null
            return makeRGB(trfn, toXYZD50)
        }

        /**
         * `MakeRGB(tf, mat)`. Returns `null` if `tf` is not a valid sRGBish
         * transfer function. Snaps quasi-standard inputs to the matching
         * `SkNamedTransferFn::k*` so `gammaCloseToSRGB()` (memcmp-style
         * exact compare) and pointer-equality stay correct even for inputs
         * that arrive from an ICC parser with s15Fixed16 truncation noise.
         *
         * Mirrors upstream
         * [SkColorSpace.cpp:136-159](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkColorSpace.cpp).
         */
        public fun makeRGB(
            transferFn: SkcmsTransferFunction,
            toXYZ: SkcmsMatrix3x3,
        ): SkColorSpace? {
            if (classify(transferFn) != SkcmsTFType.sRGBish) return null

            // Quasi-sRGB → snap to kSRGB (and the kSRGB singleton if the
            // gamut also matches kSRGB).
            var tf = transferFn
            if (isAlmostSRGB(tf)) {
                if (xyzAlmostEqual(toXYZ, SkNamedGamut.kSRGB)) return sRGBSingleton
                tf = SkNamedTransferFn.kSRGB
            } else if (isAlmost2Dot2(tf)) {
                tf = SkNamedTransferFn.k2Dot2
            } else if (isAlmostLinear(tf)) {
                if (xyzAlmostEqual(toXYZ, SkNamedGamut.kSRGB)) return sRGBLinearSingleton
                tf = SkNamedTransferFn.kLinear
            }

            return SkColorSpace(tf, toXYZ)
        }

        public fun equals(a: SkColorSpace?, b: SkColorSpace?): Boolean {
            if (a === b) return true
            if (a == null || b == null) return false
            return a.hash() == b.hash()
        }

        /**
         * Deserialize a colorspace from a buffer produced by [serialize].
         * Returns `null` if the buffer is malformed (wrong version, too
         * short) or if the resulting (TF, gamut) pair is rejected by
         * [makeRGB]. Mirror of `SkColorSpace::Deserialize`
         * (`SkColorSpace.cpp:447-470`).
         */
        public fun deserialize(data: ByteArray, length: Int = data.size): SkColorSpace? {
            if (length < SERIALIZED_SIZE) return null
            if (data[0] != SERIALIZED_VERSION) return null

            var off = 4
            val tf = SkcmsTransferFunction(
                g = readFloatLE(data, off + 0),
                a = readFloatLE(data, off + 4),
                b = readFloatLE(data, off + 8),
                c = readFloatLE(data, off + 12),
                d = readFloatLE(data, off + 16),
                e = readFloatLE(data, off + 20),
                f = readFloatLE(data, off + 24),
            )
            off += 28

            val mat = Array(3) { FloatArray(3) }
            for (r in 0 until 3) for (c in 0 until 3) {
                mat[r][c] = readFloatLE(data, off); off += 4
            }
            return makeRGB(tf, SkcmsMatrix3x3(mat))
        }

        /** Wire-format size in bytes: 4-byte header + 7+9 little-endian floats. */
        public const val SERIALIZED_SIZE: Int = 4 + 16 * 4

        /** Wire-format version byte. Currently `1`; older versions are rejected. */
        public const val SERIALIZED_VERSION: Byte = 1

        private fun writeFloatLE(buf: ByteArray, off: Int, value: Float) {
            val bits = value.toRawBits()
            buf[off] = (bits and 0xFF).toByte()
            buf[off + 1] = ((bits ushr 8) and 0xFF).toByte()
            buf[off + 2] = ((bits ushr 16) and 0xFF).toByte()
            buf[off + 3] = ((bits ushr 24) and 0xFF).toByte()
        }

        private fun readFloatLE(buf: ByteArray, off: Int): Float {
            val bits = (buf[off].toInt() and 0xFF) or
                ((buf[off + 1].toInt() and 0xFF) shl 8) or
                ((buf[off + 2].toInt() and 0xFF) shl 16) or
                ((buf[off + 3].toInt() and 0xFF) shl 24)
            return Float.fromBits(bits)
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
