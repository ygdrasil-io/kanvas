package org.skia.effects

import kotlin.Boolean
import kotlin.FloatArray
import kotlin.Int
import org.skia.core.SkPMColor4f
import org.skia.core.SkStageRec
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.skcms.SkcmsMatrix3x3
import org.skia.skcms.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * class SkWorkingFormatColorFilter final : public SkColorFilterBase {
 * public:
 *     SkWorkingFormatColorFilter(sk_sp<SkColorFilter> child,
 *                                const skcms_TransferFunction* tf,
 *                                const skcms_Matrix3x3* gamut,
 *                                const SkAlphaType* at);
 *
 *     sk_sp<SkColorSpace> workingFormat(const sk_sp<SkColorSpace>& dstCS, SkAlphaType* outAT) const;
 *
 *     SkColorFilterBase::Type type() const override {
 *         return SkColorFilterBase::Type::kWorkingFormat;
 *     }
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     SkPMColor4f onFilterColor4f(const SkPMColor4f& origColor,
 *                                 SkColorSpace* rawDstCS) const override;
 *
 *     bool onIsAlphaUnchanged() const override;
 *
 *     sk_sp<SkColorFilter> child() const { return fChild; }
 *
 * private:
 *     friend void ::SkRegisterWorkingFormatColorFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkWorkingFormatColorFilter)
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     // We implement these so that callers can get this information, even after a filter is wrapped.
 *     // That's important for Android, where *all* color filters work in sRGB. If the working format
 *     // is ever important to a caller, we'll need to expand/alter the API. See: b/360020740
 *     bool onAsAColorMode(SkColor*, SkBlendMode*) const override;
 *     bool onAsAColorMatrix(float[20]) const override;
 *
 *     sk_sp<SkColorFilter> fChild;
 *     SkWorkingFormatCalculator fWorkingFormatCalculator;
 * }
 * ```
 */
public class SkWorkingFormatColorFilter public constructor(
  child: SkSp<SkColorFilter>,
  tf: SkcmsTransferFunction?,
  gamut: SkcmsMatrix3x3?,
  at: SkAlphaType?,
) : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> fChild
   * ```
   */
  private var fChild: Int = TODO("Initialize fChild")

  /**
   * C++ original:
   * ```cpp
   * SkWorkingFormatCalculator fWorkingFormatCalculator
   * ```
   */
  private var fWorkingFormatCalculator: SkWorkingFormatCalculator =
      TODO("Initialize fWorkingFormatCalculator")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkWorkingFormatColorFilter::workingFormat(const sk_sp<SkColorSpace>& dstCS,
   *                                                               SkAlphaType* outAT) const {
   *     return fWorkingFormatCalculator.workingFormat(dstCS, outAT);
   * }
   * ```
   */
  public fun workingFormat(dstCS: SkSp<SkColorSpace>, outAT: SkAlphaType?): Int {
    TODO("Implement workingFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override {
   *         return SkColorFilterBase::Type::kWorkingFormat;
   *     }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWorkingFormatColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     sk_sp<SkColorSpace> dstCS = sk_ref_sp(rec.fDstCS);
   *
   *     if (!dstCS) {
   *         dstCS = SkColorSpace::MakeSRGB();
   *     }
   *
   *     SkAlphaType workingAT;
   *     sk_sp<SkColorSpace> workingCS = this->workingFormat(dstCS, &workingAT);
   *
   *     SkColorInfo dst = {rec.fDstColorType, kPremul_SkAlphaType, dstCS},
   *                 working = {rec.fDstColorType, workingAT, workingCS};
   *
   *     const auto* dstToWorking = rec.fAlloc->make<SkColorSpaceXformSteps>(dst, working);
   *     const auto* workingToDst = rec.fAlloc->make<SkColorSpaceXformSteps>(working, dst);
   *
   *     // The paint color is in the destination color space, so *should* be coverted to working space.
   *     // That's not necessary, though:
   *     //   - Tinting alpha-only image shaders is the only effect that uses paint-color
   *     //   - Alpha-only image shaders can't be reached from color-filters without SkSL
   *     //   - SkSL disables paint-color tinting of alpha-only image shaders
   *
   *     SkStageRec workingRec = {rec.fPipeline,
   *                              rec.fAlloc,
   *                              rec.fDstColorType,
   *                              workingCS.get(),
   *                              rec.fPaintColor,
   *                              rec.fSurfaceProps,
   *                              rec.fDstBounds};
   *
   *     dstToWorking->apply(rec.fPipeline);
   *     if (!as_CFB(fChild)->appendStages(workingRec, shaderIsOpaque)) {
   *         return false;
   *     }
   *     workingToDst->apply(rec.fPipeline);
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPMColor4f SkWorkingFormatColorFilter::onFilterColor4f(const SkPMColor4f& origColor,
   *                                                         SkColorSpace* rawDstCS) const {
   *     sk_sp<SkColorSpace> dstCS = sk_ref_sp(rawDstCS);
   *     if (!dstCS) {
   *         dstCS = SkColorSpace::MakeSRGB();
   *     }
   *
   *     SkAlphaType workingAT;
   *     sk_sp<SkColorSpace> workingCS = this->workingFormat(dstCS, &workingAT);
   *
   *     SkColorInfo dst = {kUnknown_SkColorType, kPremul_SkAlphaType, dstCS},
   *                 working = {kUnknown_SkColorType, workingAT, workingCS};
   *
   *     SkPMColor4f color = origColor;
   *     SkColorSpaceXformSteps{dst, working}.apply(color.vec());
   *     color = as_CFB(fChild)->onFilterColor4f(color, working.colorSpace());
   *     SkColorSpaceXformSteps{working, dst}.apply(color.vec());
   *     return color;
   * }
   * ```
   */
  public override fun onFilterColor4f(origColor: SkPMColor4f, rawDstCS: SkColorSpace?): Int {
    TODO("Implement onFilterColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWorkingFormatColorFilter::onIsAlphaUnchanged() const { return fChild->isAlphaUnchanged(); }
   * ```
   */
  public override fun onIsAlphaUnchanged(): Boolean {
    TODO("Implement onIsAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> child() const { return fChild; }
   * ```
   */
  public fun child(): Int {
    TODO("Implement child")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWorkingFormatColorFilter::onAsAColorMode(SkColor* color, SkBlendMode* mode) const {
   *     return fChild->asAColorMode(color, mode);
   * }
   * ```
   */
  public override fun onAsAColorMode(color: SkColor?, mode: SkBlendMode?): Boolean {
    TODO("Implement onAsAColorMode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWorkingFormatColorFilter::onAsAColorMatrix(float matrix[20]) const {
   *     return fChild->asAColorMatrix(matrix);
   * }
   * ```
   */
  public override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
    TODO("Implement onAsAColorMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWorkingFormatColorFilter::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fChild.get());
   *     fWorkingFormatCalculator.flatten(buffer);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkWorkingFormatColorFilter::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkColorFilter> child = buffer.readColorFilter();
   *     bool useDstTF = buffer.readBool(), useDstGamut = buffer.readBool(),
   *          useDstAT = buffer.readBool();
   *
   *     skcms_TransferFunction tf;
   *     skcms_Matrix3x3 gamut;
   *     SkAlphaType at;
   *
   *     if (!useDstTF) {
   *         buffer.readScalarArray({&tf.g, sizeof(skcms_TransferFunction) / sizeof(SkScalar)});
   *     }
   *     if (!useDstGamut) {
   *         buffer.readScalarArray({&gamut.vals[0][0], sizeof(skcms_Matrix3x3) / sizeof(SkScalar)});
   *     }
   *     if (!useDstAT) {
   *         at = buffer.read32LE(kLastEnum_SkAlphaType);
   *     }
   *
   *     return SkColorFilterPriv::WithWorkingFormat(std::move(child),
   *                                                 useDstTF ? nullptr : &tf,
   *                                                 useDstGamut ? nullptr : &gamut,
   *                                                 useDstAT ? nullptr : &at);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
