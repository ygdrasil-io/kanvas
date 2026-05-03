package org.skia.core

import kotlin.Boolean
import kotlin.FloatArray
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkColor
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkSp
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SK_API SkColorFilter : public SkFlattenable {
 * public:
 *     /** If the filter can be represented by a source color plus Mode, this
 *      *  returns true, and sets (if not NULL) the color and mode appropriately.
 *      *  If not, this returns false and ignores the parameters.
 *      */
 *     bool asAColorMode(SkColor* color, SkBlendMode* mode) const;
 *
 *     /** If the filter can be represented by a 5x4 matrix, this
 *      *  returns true, and sets the matrix appropriately.
 *      *  If not, this returns false and ignores the parameter.
 *      */
 *     bool asAColorMatrix(float matrix[20]) const;
 *
 *     // Returns true if the filter is guaranteed to never change the alpha of a color it filters.
 *     bool isAlphaUnchanged() const;
 *
 *     /**
 *      * Converts the src color (in src colorspace), into the dst colorspace,
 *      * then applies this filter to it, returning the filtered color in the dst colorspace.
 *      */
 *     SkColor4f filterColor4f(const SkColor4f& srcColor, SkColorSpace* srcCS,
 *                             SkColorSpace* dstCS) const;
 *
 *     /** Construct a colorfilter whose effect is to first apply the inner filter and then apply
 *      *  this filter, applied to the output of the inner filter.
 *      *
 *      *  result = this(inner(...))
 *      */
 *     sk_sp<SkColorFilter> makeComposed(sk_sp<SkColorFilter> inner) const;
 *
 *     /** Return a colorfilter that will compute this filter in a specific color space. By default all
 *      *  filters operate in the destination (surface) color space. This allows filters like Blend and
 *      *  Matrix, or runtime color filters to perform their math in a known space.
 *      */
 *     sk_sp<SkColorFilter> makeWithWorkingColorSpace(sk_sp<SkColorSpace>) const;
 *
 *     static sk_sp<SkColorFilter> Deserialize(const void* data, size_t size,
 *                                             const SkDeserialProcs* procs = nullptr);
 *
 * private:
 *     SkColorFilter() = default;
 *     friend class SkColorFilterBase;
 *
 *     using INHERITED = SkFlattenable;
 * }
 * ```
 */
public open class SkColorFilter public constructor() : SkFlattenable() {
  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilter::asAColorMode(SkColor* color, SkBlendMode* mode) const {
   *     return as_CFB(this)->onAsAColorMode(color, mode);
   * }
   * ```
   */
  public fun asAColorMode(color: SkColor?, mode: SkBlendMode?): Boolean {
    TODO("Implement asAColorMode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilter::asAColorMatrix(float matrix[20]) const {
   *     return as_CFB(this)->onAsAColorMatrix(matrix);
   * }
   * ```
   */
  public fun asAColorMatrix(matrix: FloatArray): Boolean {
    TODO("Implement asAColorMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilter::isAlphaUnchanged() const {
   *     return as_CFB(this)->onIsAlphaUnchanged();
   * }
   * ```
   */
  public fun isAlphaUnchanged(): Boolean {
    TODO("Implement isAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor4f SkColorFilter::filterColor4f(const SkColor4f& origSrcColor, SkColorSpace* srcCS,
   *                                        SkColorSpace* dstCS) const {
   *     SkPMColor4f color = { origSrcColor.fR, origSrcColor.fG, origSrcColor.fB, origSrcColor.fA };
   *     SkColorSpaceXformSteps(srcCS, kUnpremul_SkAlphaType,
   *                            dstCS, kPremul_SkAlphaType).apply(color.vec());
   *
   *     // SkColor4f will assert if we allow alpha outside [0,1]. (SkSL color filters might do this).
   *     return as_CFB(this)->onFilterColor4f(color, dstCS).pinAlpha().unpremul();
   * }
   * ```
   */
  public fun filterColor4f(
    srcColor: SkColor4f,
    srcCS: SkColorSpace?,
    dstCS: SkColorSpace?,
  ): Int {
    TODO("Implement filterColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilter::makeComposed(sk_sp<SkColorFilter> inner) const {
   *     if (!inner) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     return sk_sp<SkColorFilter>(new SkComposeColorFilter(sk_ref_sp(this), std::move(inner)));
   * }
   * ```
   */
  public fun makeComposed(`inner`: SkSp<SkColorFilter>): Int {
    TODO("Implement makeComposed")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilter::makeWithWorkingColorSpace(
   *         sk_sp<SkColorSpace> workingSpace) const {
   *     SkColorFilter* base = const_cast<SkColorFilter*>(this);
   *     if (!workingSpace) {
   *         return sk_ref_sp(base);
   *     }
   *
   *     skcms_TransferFunction tf;
   *     skcms_Matrix3x3 toXYZ;
   *     workingSpace->transferFn(&tf);
   *     workingSpace->toXYZD50(&toXYZ);
   *     const SkAlphaType* kOriginalAlphaType = nullptr;
   *     return SkColorFilterPriv::WithWorkingFormat(sk_ref_sp(base), &tf, &toXYZ, kOriginalAlphaType);
   * }
   * ```
   */
  public fun makeWithWorkingColorSpace(workingSpace: SkSp<SkColorSpace>): Int {
    TODO("Implement makeWithWorkingColorSpace")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilter::Deserialize(const void* data, size_t size,
     *                                                 const SkDeserialProcs* procs) {
     *     return sk_sp<SkColorFilter>(static_cast<SkColorFilter*>(
     *                                 SkFlattenable::Deserialize(
     *                                 kSkColorFilter_Type, data, size, procs).release()));
     * }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }
  }
}
