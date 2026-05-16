package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.gpu.ganesh.SkAlphaType

/**
 * C++ original:
 * ```cpp
 * class SkWorkingFormatCalculator {
 * public:
 *     SkWorkingFormatCalculator(const skcms_TransferFunction* tf,
 *                               const skcms_Matrix3x3* gamut,
 *                               const SkAlphaType* at);
 *
 *     sk_sp<SkColorSpace> workingFormat(const sk_sp<SkColorSpace>& dstCS, SkAlphaType* outAT) const;
 *
 *     void flatten(SkWriteBuffer& buffer) const;
 *
 * private:
 *     skcms_TransferFunction fTF;
 *     bool fUseDstTF = true;
 *     skcms_Matrix3x3 fGamut;
 *     bool fUseDstGamut = true;
 *     SkAlphaType fAT;
 *     bool fUseDstAT = true;
 * }
 * ```
 */
public data class SkWorkingFormatCalculator public constructor(
  /**
   * C++ original:
   * ```cpp
   * skcms_TransferFunction fTF
   * ```
   */
  private var fTF: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fUseDstTF = true
   * ```
   */
  private var fUseDstTF: Boolean,
  /**
   * C++ original:
   * ```cpp
   * skcms_Matrix3x3 fGamut
   * ```
   */
  private var fGamut: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fUseDstGamut = true
   * ```
   */
  private var fUseDstGamut: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkAlphaType fAT
   * ```
   */
  private var fAT: SkAlphaType,
  /**
   * C++ original:
   * ```cpp
   * bool fUseDstAT = true
   * ```
   */
  private var fUseDstAT: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkWorkingFormatCalculator::workingFormat(const sk_sp<SkColorSpace>& dstCS,
   *                                                              SkAlphaType* outAT) const {
   *     skcms_TransferFunction tf;
   *     skcms_Matrix3x3 gamut;
   *
   *     if (fUseDstTF) {
   *         SkAssertResult(dstCS->isNumericalTransferFn(&tf));
   *     } else {
   *         tf = fTF;
   *     }
   *     if (fUseDstGamut) {
   *         SkAssertResult(dstCS->toXYZD50(&gamut));
   *     } else {
   *         gamut = fGamut;
   *     }
   *     *outAT = fUseDstAT ? kPremul_SkAlphaType : fAT;
   *
   *     return SkColorSpace::MakeRGB(tf, gamut);
   * }
   * ```
   */
  public fun workingFormat(dstCS: SkSp<SkColorSpace>, outAT: SkAlphaType?): Int {
    TODO("Implement workingFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWorkingFormatCalculator::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeBool(fUseDstTF);
   *     buffer.writeBool(fUseDstGamut);
   *     buffer.writeBool(fUseDstAT);
   *     if (!fUseDstTF) {
   *         buffer.writeScalarArray({&fTF.g, sizeof(skcms_TransferFunction) / sizeof(SkScalar)});
   *     }
   *     if (!fUseDstGamut) {
   *         buffer.writeScalarArray({&fGamut.vals[0][0], sizeof(skcms_Matrix3x3) / sizeof(SkScalar)});
   *     }
   *     if (!fUseDstAT) {
   *         buffer.writeInt(fAT);
   *     }
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }
}
