package org.skia.core

import org.skia.foundation.SkColorSpace
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import org.skia.skcms.SkcmsTFType
import org.skia.skcms.SkcmsTransferFunction
import org.skia.skcms.classify
import org.skia.skcms.skcmsMatrix3x3Concat
import org.skia.skcms.skcmsTransferFunctionEval
import org.skia.skcms.skcmsTransferFunctionInvert
import kotlin.math.pow

/**
 * Bit-compatible port of `SkColorSpaceXformSteps`. Build once per draw,
 * call [apply] per pixel.
 *
 * Phase A added the four standard constructor optimizations Skia ships:
 * 1. Opaque-output hint (`dstAT == kOpaque → dstAT = srcAT`).
 * 2. Early-return when `src == dst` and alpha types match.
 * 3. `linearize` / `encode` skipped when the corresponding TF is already
 *    linear (saves 6 `pow` calls per pixel in Linear↔Linear gamut transforms).
 * 4. `linearize+encode` cancellation when only the gamut changes and TFs
 *    are identical, and `unpremul+premul` cancellation when no non-linear
 *    op runs between them.
 *
 * Phase I of `MIGRATION_PLAN_COLORSPACE_PORT.md` adds HDR support:
 * - PQ src installs the standard PQish parametric on `srcTF` and scales
 *   linear values by `10000 / srcTrfn.a` to bring them onto the dst's
 *   HDR-reference-white scale. Mirror `SkColorSpaceXformSteps.cpp:74-81`.
 * - HLG src installs an HLGish-with-K=1/12 on `srcTF` and scales by
 *   `srcTrfn.b / srcTrfn.a` (peak/ref-white). When `srcTrfn.c != 1` the
 *   system gamma triggers the OOTF: `Y` is the per-channel luminance
 *   weights to Rec.2020, `gamma-1` lives in `fSrcOotf[3]`. Mirror
 *   `SkColorSpaceXformSteps.cpp:82-92`.
 * - Symmetric handling for PQ / HLG dst.
 * - Extra optimisation: when src and dst OOTFs cancel (no gamut transform
 *   and the gammas are reciprocal), we drop both. Mirror `:154-163`.
 */
public class SkColorSpaceXformSteps(
    src: SkColorSpace,
    srcAT: SkAlphaType,
    dst: SkColorSpace,
    dstAT: SkAlphaType,
) {
    public val flags: Flags
    public val srcTF: SkcmsTransferFunction
    public val dstTFInv: SkcmsTransferFunction
    /**
     * Column-major 3x3 (per upstream comment in SkColorSpaceXformSteps.h).
     * Slot 0..2 is the first column, 3..5 second, 6..8 third — same layout
     * as `apply()` consumes.
     */
    public val srcToDstMatrix: FloatArray
    /**
     * Phase I OOTF coefficients.
     * - `[0..2]`: per-channel Y luminance weights of the src colorspace
     *   in the *dst* (Rec.2020-linear-relative) gamut.
     * - `[3]`: HLG system gamma minus 1 (i.e. `srcTrfn.c - 1`).
     * Empty FloatArray(4) when `flags.srcOotf` is false.
     */
    public val fSrcOotf: FloatArray
    /** Same shape as [fSrcOotf]; `[3]` is `1/dstTrfn.c - 1`. */
    public val fDstOotf: FloatArray

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
        var srcOotfFlag = false
        var dstOotfFlag = false
        var packedMatrix: FloatArray = IDENTITY_MATRIX
        var resolvedSrcTF: SkcmsTransferFunction = SkNamedTransferFn.kLinear
        var resolvedDstTFInv: SkcmsTransferFunction = SkNamedTransferFn.kLinear
        val srcOotfBuf = FloatArray(4)
        val dstOotfBuf = FloatArray(4)

        // Opt 2: early-return when src and dst color spaces and alpha types
        // are identical. Leaves all flags `false` (identity pipeline).
        if (src.hash() != dst.hash() || srcAT != effectiveDstAT) {
            val srcTrfn = src.transferFn
            val dstTrfn = dst.transferFn
            val srcTfType = classify(srcTrfn)
            val dstTfType = classify(dstTrfn)

            // Linear-domain scale factor: PQ/HLG carry HDR luminance metadata
            // in the TF struct; equalising src and dst luminance is part of
            // the gamut step.
            var scaleFactor = 1f

            // Source TF.
            when (srcTfType) {
                SkcmsTFType.PQ -> {
                    // PQ is anchored on a 10,000-nit peak; scale linear values
                    // down to the src HDR ref-white (srcTrfn.a, in nits).
                    scaleFactor *= 10000f / srcTrfn.a
                    resolvedSrcTF = K_PQISH_STANDARD
                    linearize = true
                }
                SkcmsTFType.HLG -> {
                    // HLG carries peak (b) and ref-white (a). Scale the linear
                    // section down accordingly. The HLGish standard is K=1/12.
                    scaleFactor *= srcTrfn.b / srcTrfn.a
                    resolvedSrcTF = K_HLGISH_STANDARD.copy(f = 1f / 12f - 1f)
                    linearize = true
                    if (srcTrfn.c != 1f) {
                        srcOotfFlag = true
                        srcOotfBuf[3] = srcTrfn.c - 1f
                        setOotfY(src, srcOotfBuf)
                    }
                }
                else -> {
                    linearize = srcTrfn != SkNamedTransferFn.kLinear
                    if (linearize) resolvedSrcTF = srcTrfn
                }
            }

            // Destination TF inverse.
            when (dstTfType) {
                SkcmsTFType.PQ -> {
                    scaleFactor /= 10000f / dstTrfn.a
                    encode = true
                    resolvedDstTFInv = skcmsTransferFunctionInvert(K_PQISH_STANDARD)!!
                }
                SkcmsTFType.HLG -> {
                    scaleFactor /= dstTrfn.b / dstTrfn.a
                    encode = true
                    val dstHlgish = K_HLGISH_STANDARD.copy(f = 1f / 12f - 1f)
                    resolvedDstTFInv = skcmsTransferFunctionInvert(dstHlgish)!!
                    if (dstTrfn.c != 1f) {
                        dstOotfFlag = true
                        // Inverse OOTF gamma: `1/c - 1` (so eval Y^((1/c)-1)
                        // before encode reverses the src `Y^(c-1)` after linearize).
                        dstOotfBuf[3] = 1f / dstTrfn.c - 1f
                        setOotfY(dst, dstOotfBuf)
                    }
                }
                else -> {
                    encode = dstTrfn != SkNamedTransferFn.kLinear
                    if (encode) resolvedDstTFInv = dst.invTransferFn
                }
            }

            unpremul = srcAT == SkAlphaType.kPremul
            // gamut_transform is true when the gamuts differ OR when an HDR
            // luminance scale factor is in play; either way the matrix step
            // is the only place we get to apply scaleFactor.
            gamutTransform = src.toXYZD50Hash != dst.toXYZD50Hash || scaleFactor != 1f
            premul = srcAT != SkAlphaType.kOpaque && effectiveDstAT == SkAlphaType.kPremul

            if (gamutTransform) {
                // src → dst matrix (in row-major terms): dst.fromXYZD50 * src.toXYZD50.
                // Then transposed to column-major for the apply() consumer
                // and scaled by scaleFactor.
                val rowMajor = skcmsMatrix3x3Concat(dst.fromXYZD50, src.toXYZD50)
                packedMatrix = floatArrayOf(
                    rowMajor.vals[0][0] * scaleFactor, rowMajor.vals[1][0] * scaleFactor, rowMajor.vals[2][0] * scaleFactor,
                    rowMajor.vals[0][1] * scaleFactor, rowMajor.vals[1][1] * scaleFactor, rowMajor.vals[2][1] * scaleFactor,
                    rowMajor.vals[0][2] * scaleFactor, rowMajor.vals[1][2] * scaleFactor, rowMajor.vals[2][2] * scaleFactor,
                )
            }

            // Phase I cancel-OOTF: when both src and dst OOTFs are active and
            // there's no gamut step, the Y vectors are identical (same coefs).
            // If the gammas are reciprocal (`(γ_src)·(γ_dst) == 1`, i.e.
            // `(γ_src - 1 + 1)·(γ_dst - 1 + 1) == 1`), the two `Y^k` operations
            // exactly cancel → drop both. Mirror `SkColorSpaceXformSteps.cpp:154-163`.
            if (srcOotfFlag && !gamutTransform && dstOotfFlag) {
                if ((srcOotfBuf[3] + 1f) * (dstOotfBuf[3] + 1f) == 1f) {
                    srcOotfFlag = false
                    dstOotfFlag = false
                }
            }

            // Opt 4a: linearize+encode same TF and no gamut/ootf → both cancel.
            // The inv pin in skcmsTransferFunctionInvert guarantees
            // `inv(eval(1)) == 1`, so the round-trip is bit-stable but costs
            // 6 `pow` calls per pixel.
            //
            // Skipped for PQ/HLG because srcTF/dstTFInv are *not* the original
            // PQ/HLG TFs (they're the standard PQish/HLGish parametrics) — the
            // hashes wouldn't reflect that.
            if (linearize && encode && !gamutTransform && !srcOotfFlag && !dstOotfFlag &&
                srcTfType != SkcmsTFType.PQ && srcTfType != SkcmsTFType.HLG &&
                dstTfType != SkcmsTFType.PQ && dstTfType != SkcmsTFType.HLG &&
                src.transferFnHash == dst.transferFnHash) {
                linearize = false
                encode = false
            }

            // Opt 4b: unpremul+premul with no non-linear op between → both
            // cancel. Without this, a c/=a; c*=a round-trip drifts float by
            // up to 1 ulp on partial alphas.
            if (unpremul && premul && !linearize && !encode && !srcOotfFlag && !dstOotfFlag) {
                unpremul = false
                premul = false
            }
        }

        flags = Flags(unpremul, linearize, srcOotfFlag, gamutTransform, dstOotfFlag, encode, premul)
        srcTF = resolvedSrcTF
        dstTFInv = resolvedDstTFInv
        srcToDstMatrix = packedMatrix
        fSrcOotf = if (srcOotfFlag) srcOotfBuf else EMPTY_OOTF
        fDstOotf = if (dstOotfFlag) dstOotfBuf else EMPTY_OOTF
    }

    /**
     * Apply the pipeline in place. `rgba[3]` (alpha) is preserved through
     * unpremul/premul and never touched by linearize/encode/gamut/ootf.
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
        if (flags.srcOotf) {
            // Y = sum(src.gamma_minus_1 weights · rgb), then scale RGB by
            // Y^gamma_minus_1. Reference: BT.2100 HLG OOTF.
            val Y = fSrcOotf[0] * rgba[0] + fSrcOotf[1] * rgba[1] + fSrcOotf[2] * rgba[2]
            val k = Y.toDouble().pow(fSrcOotf[3].toDouble()).toFloat()
            rgba[0] *= k; rgba[1] *= k; rgba[2] *= k
        }
        if (flags.gamutTransform) {
            val r = rgba[0]; val g = rgba[1]; val b = rgba[2]
            // m is column-major: rgba[i] = m[0+i]*r + m[3+i]*g + m[6+i]*b.
            rgba[0] = srcToDstMatrix[0] * r + srcToDstMatrix[3] * g + srcToDstMatrix[6] * b
            rgba[1] = srcToDstMatrix[1] * r + srcToDstMatrix[4] * g + srcToDstMatrix[7] * b
            rgba[2] = srcToDstMatrix[2] * r + srcToDstMatrix[5] * g + srcToDstMatrix[8] * b
        }
        if (flags.dstOotf) {
            val Y = fDstOotf[0] * rgba[0] + fDstOotf[1] * rgba[1] + fDstOotf[2] * rgba[2]
            val k = Y.toDouble().pow(fDstOotf[3].toDouble()).toFloat()
            rgba[0] *= k; rgba[1] *= k; rgba[2] *= k
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
        val srcOotf: Boolean = false,
        val gamutTransform: Boolean = false,
        val dstOotf: Boolean = false,
        val encode: Boolean = false,
        val premul: Boolean = false,
    ) {
        /** True when this is the no-op pipeline. */
        public val isIdentity: Boolean
            get() = !unpremul && !linearize && !srcOotf && !gamutTransform &&
                !dstOotf && !encode && !premul
    }

    public companion object {
        private val IDENTITY_MATRIX: FloatArray =
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        private val EMPTY_OOTF: FloatArray = FloatArray(4)

        // BT.2100 PQish standard parameters. Mirror `SkColorSpaceXformSteps.cpp:69-71`.
        // tfKindMarker(SkcmsTFType.PQish) = -2.
        internal val K_PQISH_STANDARD: SkcmsTransferFunction = SkcmsTransferFunction(
            g = -2f,
            a = -107f / 128f, b = 1f, c = 32f / 2523f,
            d = 2413f / 128f, e = -2392f / 128f, f = 8192f / 1305f,
        )

        // BT.2100 HLGish standard parameters (R, G, a, b, c, K-1=0 → K=1).
        // Mirror `SkColorSpaceXformSteps.cpp:71-72`. tfKindMarker(HLGish) = -3.
        internal val K_HLGISH_STANDARD: SkcmsTransferFunction = SkcmsTransferFunction(
            g = -3f,
            a = 2f, b = 2f, c = 1f / 0.17883277f,
            d = 0.28466892f, e = 0.55991073f, f = 0f,
        )

        // Rec.2020 luminance coefficients (BT.2100 Rec.2020 white point).
        private val Y_REC2020: FloatArray = floatArrayOf(0.262700f, 0.678000f, 0.059300f)

        // Cached (Rec.2020 linear) colorspace used as the OOTF anchor space.
        private val rec2020Linear: SkColorSpace by lazy {
            SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)!!
        }

        /**
         * Compute Y luminance coefficients for `cs` in the Rec.2020-linear
         * gamut, used by the HLG OOTF. Mirror `set_ootf_Y` upstream
         * (`SkColorSpaceXformSteps.cpp:27-39`). Writes into `out[0..2]`;
         * the caller is responsible for `out[3]` (`gamma_minus_1`).
         */
        private fun setOotfY(cs: SkColorSpace, out: FloatArray) {
            // m takes cs RGB to Rec.2020-linear RGB.
            val m = skcmsMatrix3x3Concat(rec2020Linear.fromXYZD50, cs.toXYZD50)
            // out[i] = sum_j m[j][i] * Y_rec2020[j].
            for (i in 0 until 3) {
                var sum = 0f
                for (j in 0 until 3) {
                    sum += m.vals[j][i] * Y_REC2020[j]
                }
                out[i] = sum
            }
        }
    }
}

public enum class SkAlphaType {
    kUnknown,
    kOpaque,
    kPremul,
    kUnpremul,
}
