package org.skia.gpu

import kotlin.Boolean
import org.skia.core.SkMaskFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.math.SkIPoint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SDFMaskFilterImpl : public SkMaskFilterBase {
 * public:
 *     SDFMaskFilterImpl();
 *
 *     // overrides from SkMaskFilterBase
 *     //  This method is not exported to java.
 *     SkMask::Format getFormat() const override;
 *     //  This method is not exported to java.
 *     bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix&,
 *                     SkIPoint* margin) const override;
 *     SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kSDF; }
 *     void computeFastBounds(const SkRect&, SkRect*) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SDFMaskFilterImpl)
 * }
 * ```
 */
public open class SDFMaskFilterImpl public constructor() : SkMaskFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * SkMask::Format SDFMaskFilterImpl::getFormat() const {
   *     return SkMask::kSDF_Format;
   * }
   * ```
   */
  public override fun getFormat(): SkMask.Format {
    TODO("Implement getFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SDFMaskFilterImpl::filterMask(SkMaskBuilder* dst, const SkMask& src,
   *                                    const SkMatrix& matrix, SkIPoint* margin) const {
   *     if (src.fFormat != SkMask::kA8_Format
   *         && src.fFormat != SkMask::kBW_Format
   *         && src.fFormat != SkMask::kLCD16_Format) {
   *         return false;
   *     }
   *
   *     *dst = SkMaskBuilder::PrepareDestination(SK_DistanceFieldPad, SK_DistanceFieldPad, src);
   *     dst->format() = SkMask::kSDF_Format;
   *
   *     if (margin) {
   *         margin->set(SK_DistanceFieldPad, SK_DistanceFieldPad);
   *     }
   *
   *     if (src.fImage == nullptr) {
   *         return true;
   *     }
   *     if (dst->fImage == nullptr) {
   *         dst->bounds().setEmpty();
   *         return false;
   *     }
   *
   *     if (src.fFormat == SkMask::kA8_Format) {
   *         return SkGenerateDistanceFieldFromA8Image(dst->image(), src.fImage,
   *                                                   src.fBounds.width(), src.fBounds.height(),
   *                                                   src.fRowBytes);
   *     } else if (src.fFormat == SkMask::kLCD16_Format) {
   *         return SkGenerateDistanceFieldFromLCD16Mask(dst->image(), src.fImage,
   *                                                      src.fBounds.width(), src.fBounds.height(),
   *                                                      src.fRowBytes);
   *     } else {
   *         return SkGenerateDistanceFieldFromBWImage(dst->image(), src.fImage,
   *                                                   src.fBounds.width(), src.fBounds.height(),
   *                                                   src.fRowBytes);
   *     }
   * }
   * ```
   */
  public override fun filterMask(
    dst: SkMaskBuilder?,
    src: SkMask,
    matrix: SkMatrix,
    margin: SkIPoint?,
  ): Boolean {
    TODO("Implement filterMask")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kSDF; }
   * ```
   */
  public override fun type(): SkMaskFilterBase.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * void SDFMaskFilterImpl::computeFastBounds(const SkRect& src,
   *                                             SkRect* dst) const {
   *     dst->setLTRB(src.fLeft  - SK_DistanceFieldPad, src.fTop    - SK_DistanceFieldPad,
   *                  src.fRight + SK_DistanceFieldPad, src.fBottom + SK_DistanceFieldPad);
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect, dst: SkRect?) {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SDFMaskFilterImpl::CreateProc(SkReadBuffer& buffer) {
   *     return SDFMaskFilter::Make();
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
