package org.skia.core

import org.skia.foundation.SkColorSpace
import org.skia.skcms.SkcmsTransferFunction
import org.skia.skcms.skcmsMatrix3x3Concat
import org.skia.skcms.skcmsTransferFunctionEval

/**
 * Bit-compatible port of `SkColorSpaceXformSteps`. Phase 3 supports only
 * sRGBish transfer functions and no OOTF (HDR is out of scope).
 *
 * Build once per draw, call [apply] per pixel. Matches upstream `apply(float*)`
 * exactly except for the omitted OOTF branches.
 */
public class SkColorSpaceXformSteps(
    src: SkColorSpace,
    srcAT: SkAlphaType,
    dst: SkColorSpace,
    dstAT: SkAlphaType,
) {
    public val flags: Flags
    public val srcTF: SkcmsTransferFunction = src.transferFn
    public val dstTFInv: SkcmsTransferFunction = dst.invTransferFn
    /**
     * Column-major 3x3 (per upstream comment in SkColorSpaceXformSteps.h).
     * Slot 0..2 is the first column, 3..5 second, 6..8 third — same layout
     * as `apply()` consumes.
     */
    public val srcToDstMatrix: FloatArray

    init {
        val gamutTransform = src.toXYZD50Hash != dst.toXYZD50Hash
        val transferDiffer = src.transferFnHash != dst.transferFnHash
        val needLinearizeEncode = gamutTransform || transferDiffer

        // Match upstream SkColorSpaceXformSteps: unpremul/premul depend only
        // on the alpha types, independent of color work. They cancel out
        // automatically when both run (premul → unpremul → premul, identity
        // modulo float precision).
        flags = Flags(
            unpremul = (srcAT == SkAlphaType.kPremul),
            linearize = needLinearizeEncode,
            gamutTransform = gamutTransform,
            encode = needLinearizeEncode,
            premul = (dstAT == SkAlphaType.kPremul),
        )

        // src → dst matrix (in row-major terms): dst.fromXYZD50 * src.toXYZD50
        // Then transposed to column-major for the apply() consumer.
        srcToDstMatrix = if (gamutTransform) {
            val rowMajor = skcmsMatrix3x3Concat(dst.fromXYZD50, src.toXYZD50)
            // Pack as column-major:
            //   column 0 = (m[0][0], m[1][0], m[2][0])
            //   column 1 = (m[0][1], m[1][1], m[2][1])
            //   column 2 = (m[0][2], m[1][2], m[2][2])
            floatArrayOf(
                rowMajor.vals[0][0], rowMajor.vals[1][0], rowMajor.vals[2][0],
                rowMajor.vals[0][1], rowMajor.vals[1][1], rowMajor.vals[2][1],
                rowMajor.vals[0][2], rowMajor.vals[1][2], rowMajor.vals[2][2],
            )
        } else {
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }
    }

    /**
     * Apply the pipeline in place. `rgba[3]` (alpha) is preserved through
     * unpremul/premul and never touched by linearize/encode/gamut.
     */
    public fun apply(rgba: FloatArray) {
        if (flags.unpremul) {
            val a = rgba[3]
            val invA = if (a == 0f) 0f else 1f / a
            rgba[0] *= invA
            rgba[1] *= invA
            rgba[2] *= invA
        }
        if (flags.linearize) {
            rgba[0] = skcmsTransferFunctionEval(srcTF, rgba[0])
            rgba[1] = skcmsTransferFunctionEval(srcTF, rgba[1])
            rgba[2] = skcmsTransferFunctionEval(srcTF, rgba[2])
        }
        if (flags.gamutTransform) {
            val r = rgba[0]; val g = rgba[1]; val b = rgba[2]
            // m is column-major: rgba[i] = m[0+i]*r + m[3+i]*g + m[6+i]*b.
            rgba[0] = srcToDstMatrix[0] * r + srcToDstMatrix[3] * g + srcToDstMatrix[6] * b
            rgba[1] = srcToDstMatrix[1] * r + srcToDstMatrix[4] * g + srcToDstMatrix[7] * b
            rgba[2] = srcToDstMatrix[2] * r + srcToDstMatrix[5] * g + srcToDstMatrix[8] * b
        }
        if (flags.encode) {
            rgba[0] = skcmsTransferFunctionEval(dstTFInv, rgba[0])
            rgba[1] = skcmsTransferFunctionEval(dstTFInv, rgba[1])
            rgba[2] = skcmsTransferFunctionEval(dstTFInv, rgba[2])
        }
        if (flags.premul) {
            val a = rgba[3]
            rgba[0] *= a
            rgba[1] *= a
            rgba[2] *= a
        }
    }

    public data class Flags(
        val unpremul: Boolean = false,
        val linearize: Boolean = false,
        val gamutTransform: Boolean = false,
        val encode: Boolean = false,
        val premul: Boolean = false,
    ) {
        /** True when this is the no-op pipeline. */
        public val isIdentity: Boolean
            get() = !unpremul && !linearize && !gamutTransform && !encode && !premul
    }
}

public enum class SkAlphaType {
    kUnknown,
    kOpaque,
    kPremul,
    kUnpremul,
}
