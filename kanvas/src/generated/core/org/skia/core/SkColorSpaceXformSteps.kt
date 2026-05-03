package org.skia.core

import kotlin.Boolean
import kotlin.FloatArray
import kotlin.UInt
import org.skia.skcms.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * struct SkColorSpaceXformSteps {
 *
 *     struct Flags {
 *         bool unpremul         = false;
 *         bool linearize        = false;
 *         bool src_ootf         = false;
 *         bool gamut_transform  = false;
 *         bool dst_ootf         = false;
 *         bool encode           = false;
 *         bool premul           = false;
 *
 *         constexpr uint32_t mask() const {
 *             return (unpremul        ?  1 : 0)
 *                  | (linearize       ?  2 : 0)
 *                  | (src_ootf        ? 32 : 0)
 *                  | (gamut_transform ?  4 : 0)
 *                  | (dst_ootf        ? 64 : 0)
 *                  | (encode          ?  8 : 0)
 *                  | (premul          ? 16 : 0);
 *         }
 *     };
 *
 *     SkColorSpaceXformSteps() {}
 *     SkColorSpaceXformSteps(const SkColorSpace* src, SkAlphaType srcAT,
 *                            const SkColorSpace* dst, SkAlphaType dstAT);
 *
 *     template <typename S, typename D>
 *     SkColorSpaceXformSteps(const S& src, const D& dst)
 *         : SkColorSpaceXformSteps(src.colorSpace(), src.alphaType(),
 *                                  dst.colorSpace(), dst.alphaType()) {}
 *
 *     void apply(float rgba[4]) const;
 *     void apply(SkRasterPipeline*) const;
 *
 *     Flags fFlags;
 *
 *     skcms_TransferFunction fSrcTF,     // Apply for linearize.
 *                            fDstTFInv;  // Apply for encode.
 *     float fSrcToDstMatrix[9];          // Apply this 3x3 *column*-major matrix for gamut_transform.
 *     float fSrcOotf[4];                 // Apply ootf with these r,g,b coefficients and gamma before
 *                                        // gamut_transform.
 *     float fDstOotf[4];                 // Apply ootf with these r,g,b coefficients and gamma after
 *                                        // gamut_transform.
 * }
 * ```
 */
public data class SkColorSpaceXformSteps<S, D> public constructor(
  /**
   * C++ original:
   * ```cpp
   * Flags fFlags
   * ```
   */
  public var fFlags: Flags,
  /**
   * C++ original:
   * ```cpp
   * skcms_TransferFunction fSrcTF
   * ```
   */
  public var fSrcTF: SkcmsTransferFunction,
  /**
   * C++ original:
   * ```cpp
   * skcms_TransferFunction fSrcTF,     // Apply for linearize.
   *                            fDstTFInv
   * ```
   */
  public var fDstTFInv: SkcmsTransferFunction,
  /**
   * C++ original:
   * ```cpp
   * float fSrcToDstMatrix[9]
   * ```
   */
  public var fSrcToDstMatrix: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float fSrcOotf[4]
   * ```
   */
  public var fSrcOotf: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float fDstOotf[4]
   * ```
   */
  public var fDstOotf: FloatArray,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkColorSpaceXformSteps::apply(float* rgba) const {
   *     if (this->fFlags.unpremul) {
   *         // I don't know why isfinite(x) stopped working on the Chromecast bots...
   *         auto is_finite = [](float x) { return x*0 == 0; };
   *
   *         float invA = sk_ieee_float_divide(1.0f, rgba[3]);
   *         invA = is_finite(invA) ? invA : 0;
   *         rgba[0] *= invA;
   *         rgba[1] *= invA;
   *         rgba[2] *= invA;
   *     }
   *     if (this->fFlags.linearize) {
   *         rgba[0] = skcms_TransferFunction_eval(&fSrcTF, rgba[0]);
   *         rgba[1] = skcms_TransferFunction_eval(&fSrcTF, rgba[1]);
   *         rgba[2] = skcms_TransferFunction_eval(&fSrcTF, rgba[2]);
   *     }
   *     if (this->fFlags.src_ootf) {
   *         const float Y = fSrcOotf[0] * rgba[0] +
   *                         fSrcOotf[1] * rgba[1] +
   *                         fSrcOotf[2] * rgba[2];
   *         const float Y_to_gamma_minus_1 = std::pow(Y, fSrcOotf[3]);
   *         rgba[0] *= Y_to_gamma_minus_1;
   *         rgba[1] *= Y_to_gamma_minus_1;
   *         rgba[2] *= Y_to_gamma_minus_1;
   *     }
   *     if (this->fFlags.gamut_transform) {
   *         float temp[3] = { rgba[0], rgba[1], rgba[2] };
   *         for (int i = 0; i < 3; ++i) {
   *             rgba[i] = fSrcToDstMatrix[    i] * temp[0] +
   *                       fSrcToDstMatrix[3 + i] * temp[1] +
   *                       fSrcToDstMatrix[6 + i] * temp[2];
   *         }
   *     }
   *     if (this->fFlags.dst_ootf) {
   *         const float Y = fDstOotf[0] * rgba[0] +
   *                         fDstOotf[1] * rgba[1] +
   *                         fDstOotf[2] * rgba[2];
   *         const float Y_to_gamma_minus_1 = std::pow(Y, fDstOotf[3]);
   *         rgba[0] *= Y_to_gamma_minus_1;
   *         rgba[1] *= Y_to_gamma_minus_1;
   *         rgba[2] *= Y_to_gamma_minus_1;
   *     }
   *     if (this->fFlags.encode) {
   *         rgba[0] = skcms_TransferFunction_eval(&fDstTFInv, rgba[0]);
   *         rgba[1] = skcms_TransferFunction_eval(&fDstTFInv, rgba[1]);
   *         rgba[2] = skcms_TransferFunction_eval(&fDstTFInv, rgba[2]);
   *     }
   *     if (this->fFlags.premul) {
   *         rgba[0] *= rgba[3];
   *         rgba[1] *= rgba[3];
   *         rgba[2] *= rgba[3];
   *     }
   * }
   * ```
   */
  public fun apply(rgba: FloatArray) {
    TODO("Implement apply")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpaceXformSteps::apply(SkRasterPipeline* p) const {
   *     if (this->fFlags.unpremul)        { p->append(SkRasterPipelineOp::unpremul); }
   *     if (this->fFlags.linearize)       { p->appendTransferFunction(fSrcTF); }
   *     if (this->fFlags.src_ootf)        { p->append(SkRasterPipelineOp::ootf, fSrcOotf); }
   *     if (this->fFlags.gamut_transform) { p->append(SkRasterPipelineOp::matrix_3x3, &fSrcToDstMatrix); }
   *     if (this->fFlags.dst_ootf)        { p->append(SkRasterPipelineOp::ootf, fDstOotf); }
   *     if (this->fFlags.encode)          { p->appendTransferFunction(fDstTFInv); }
   *     if (this->fFlags.premul)          { p->append(SkRasterPipelineOp::premul); }
   * }
   * ```
   */
  public fun apply(p: SkRasterPipeline?) {
    TODO("Implement apply")
  }

  public data class Flags public constructor(
    public var unpremul: Boolean,
    public var linearize: Boolean,
    public var srcOotf: Boolean,
    public var gamutTransform: Boolean,
    public var dstOotf: Boolean,
    public var encode: Boolean,
    public var premul: Boolean,
  ) {
    public fun mask(): UInt {
      TODO("Implement mask")
    }
  }
}
