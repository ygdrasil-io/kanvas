package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.math.SkVector
import undefined.Offset

/**
 * C++ original:
 * ```cpp
 * class DropShadowImageFilter final : public ImageFilter {
 * public:
 *     ~DropShadowImageFilter() override;
 *
 *     static sk_sp<DropShadowImageFilter> Make();
 *
 *     enum class Mode { kShadowAndForeground, kShadowOnly };
 *
 *     SG_ATTRIBUTE(Offset, SkVector, fOffset)
 *     SG_ATTRIBUTE(Sigma , SkVector, fSigma )
 *     SG_ATTRIBUTE(Color , SkColor , fColor )
 *     SG_ATTRIBUTE(Mode  , Mode    , fMode  )
 *
 * protected:
 *     sk_sp<SkImageFilter> onRevalidateFilter() override;
 *
 * private:
 *     explicit DropShadowImageFilter();
 *
 *     SkVector             fOffset = { 0, 0 },
 *                          fSigma  = { 0, 0 };
 *     SkColor              fColor  = SK_ColorBLACK;
 *     Mode                 fMode   = Mode::kShadowAndForeground;
 *
 *     using INHERITED = ImageFilter;
 * }
 * ```
 */
public class DropShadowImageFilter public constructor() : ImageFilter() {
  /**
   * C++ original:
   * ```cpp
   * SkVector             fOffset
   * ```
   */
  private var fOffset: Int = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * SkVector             fOffset = { 0, 0 },
   *                          fSigma
   * ```
   */
  private var fSigma: Int = TODO("Initialize fSigma")

  /**
   * C++ original:
   * ```cpp
   * SkColor              fColor
   * ```
   */
  private var fColor: Int = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * Mode                 fMode   = Mode::kShadowAndForeground
   * ```
   */
  private var fMode: Mode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Offset, SkVector, fOffset)
   * ```
   */
  public override fun sgATTRIBUTE(param0: Offset, param1: SkVector): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> DropShadowImageFilter::onRevalidateFilter() {
   *     if (fMode == Mode::kShadowOnly) {
   *         return SkImageFilters::DropShadowOnly(fOffset.x(), fOffset.y(), fSigma.x(), fSigma.y(),
   *                                               fColor, nullptr, this->getCropRect());
   *     } else {
   *         return SkImageFilters::DropShadow(fOffset.x(), fOffset.y(), fSigma.x(), fSigma.y(),
   *                                           fColor, nullptr, this->getCropRect());
   *     }
   * }
   * ```
   */
  public override fun onRevalidateFilter(): SkSp<SkImageFilter> {
    TODO("Implement onRevalidateFilter")
  }

  public enum class Mode {
    kShadowAndForeground,
    kShadowOnly,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<DropShadowImageFilter> DropShadowImageFilter::Make() {
     *     return sk_sp<DropShadowImageFilter>(new DropShadowImageFilter());
     * }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
