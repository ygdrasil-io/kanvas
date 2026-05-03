package org.skia.core

import org.skia.foundation.SkColorSpace
import org.skia.skcms.SkNamedTransferFn
import org.skia.skcms.SkcmsTransferFunction
import org.skia.skcms.skcmsMatrix3x3Concat
import org.skia.skcms.skcmsTransferFunctionEval

/**
 * Bit-compatible port of `SkColorSpaceXformSteps`. Phase 3 supports only
 * sRGBish transfer functions and no OOTF (HDR is out of scope, deferred to
 * MIGRATION_PLAN_COLORSPACE_PORT.md Phase I).
 *
 * Build once per draw, call [apply] per pixel. Matches upstream `apply(float*)`
 * exactly except for the omitted OOTF branches.
 *
 * Phase A of the port plan adds the four constructor optimizations Skia ships:
 * 1. Opaque-output hint (`dstAT == kOpaque → dstAT = srcAT`).
 * 2. Early-return when `src == dst` and alpha types match.
 * 3. `linearize` / `encode` skipped when the corresponding TF is already
 *    linear (saves 6 `pow` calls per pixel in Linear↔Linear gamut transforms).
 * 4. `linearize+encode` cancellation when only the gamut changes and TFs are
 *    identical, and `unpremul+premul` cancellation when no non-linear op
 *    runs between them (avoids an `a, /a, *a` round-trip that drifts float).
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
        // Opt 1: opaque outputs are treated as the same alpha type as the
        // source input. Upstream comment: "we'd really like to have a good
        // way of explaining why we think this is useful." (SkColorSpaceXformSteps.cpp:45-47)
        val effectiveDstAT = if (dstAT == SkAlphaType.kOpaque) srcAT else dstAT

        var unpremul = false
        var linearize = false
        var gamutTransform = false
        var encode = false
        var premul = false
        var packedMatrix: FloatArray = IDENTITY_MATRIX

        // Opt 2: early-return when src and dst color spaces and alpha types
        // are identical. Leaves all flags `false` (identity pipeline).
        if (src.hash() != dst.hash() || srcAT != effectiveDstAT) {
            gamutTransform = src.toXYZD50Hash != dst.toXYZD50Hash

            // Opt 3: linearize is only useful when src TF is non-linear;
            // encode is only useful when dst TF is non-linear. Saves 3
            // identity TF evals per pixel each in Linear↔Linear cases.
            linearize = src.transferFn != SkNamedTransferFn.kLinear
            encode = dst.transferFn != SkNamedTransferFn.kLinear

            unpremul = srcAT == SkAlphaType.kPremul
            premul = srcAT != SkAlphaType.kOpaque && effectiveDstAT == SkAlphaType.kPremul

            // Opt 4a: linearize+encode same TF and no gamut → both cancel.
            // Mathematically the round-trip is identity (the inv pin in
            // skcmsTransferFunctionInvert guarantees `inv(eval(1)) == 1`),
            // but it costs 6 `pow` calls per pixel.
            if (linearize && encode && !gamutTransform &&
                src.transferFnHash == dst.transferFnHash) {
                linearize = false
                encode = false
            }

            // Opt 4b: unpremul+premul with no non-linear op between → both
            // cancel. Without this, a c/=a; c*=a round-trip drifts float
            // by up to 1 ulp on partial alphas.
            if (unpremul && premul && !linearize && !encode) {
                unpremul = false
                premul = false
            }

            if (gamutTransform) {
                // src → dst matrix (in row-major terms): dst.fromXYZD50 * src.toXYZD50.
                // Then transposed to column-major for the apply() consumer.
                val rowMajor = skcmsMatrix3x3Concat(dst.fromXYZD50, src.toXYZD50)
                packedMatrix = floatArrayOf(
                    rowMajor.vals[0][0], rowMajor.vals[1][0], rowMajor.vals[2][0],
                    rowMajor.vals[0][1], rowMajor.vals[1][1], rowMajor.vals[2][1],
                    rowMajor.vals[0][2], rowMajor.vals[1][2], rowMajor.vals[2][2],
                )
            }
        }

        flags = Flags(unpremul, linearize, gamutTransform, encode, premul)
        srcToDstMatrix = packedMatrix
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

    public companion object {
        private val IDENTITY_MATRIX: FloatArray =
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}

public enum class SkAlphaType {
    kUnknown,
    kOpaque,
    kPremul,
    kUnpremul,
}
