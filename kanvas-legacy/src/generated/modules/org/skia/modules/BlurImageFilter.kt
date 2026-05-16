package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.math.SkVector
import undefined.Sigma

/**
 * C++ original:
 * ```cpp
 * class BlurImageFilter final : public ImageFilter {
 * public:
 *     ~BlurImageFilter() override;
 *
 *     static sk_sp<BlurImageFilter> Make();
 *
 *     SG_ATTRIBUTE(Sigma   , SkVector  , fSigma   )
 *     SG_ATTRIBUTE(TileMode, SkTileMode, fTileMode)
 *
 * protected:
 *     sk_sp<SkImageFilter> onRevalidateFilter() override;
 *
 * private:
 *     explicit BlurImageFilter();
 *
 *     SkVector   fSigma    = { 0, 0 };
 *     SkTileMode fTileMode = SkTileMode::kDecal;
 *
 *     using INHERITED = ImageFilter;
 * }
 * ```
 */
public class BlurImageFilter public constructor() : ImageFilter() {
  /**
   * C++ original:
   * ```cpp
   * SkVector   fSigma
   * ```
   */
  private var fSigma: Int = TODO("Initialize fSigma")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode fTileMode
   * ```
   */
  private var fTileMode: Int = TODO("Initialize fTileMode")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Sigma   , SkVector  , fSigma   )
   * ```
   */
  public override fun sgATTRIBUTE(param0: Sigma, param1: SkVector): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> BlurImageFilter::onRevalidateFilter() {
   *     // Tile modes other than kDecal require an explicit crop rect.
   *     SkASSERT(fTileMode == SkTileMode::kDecal || this->getCropRect().has_value());
   *     return SkImageFilters::Blur(fSigma.x(), fSigma.y(), fTileMode, nullptr, this->getCropRect());
   * }
   * ```
   */
  public override fun onRevalidateFilter(): SkSp<SkImageFilter> {
    TODO("Implement onRevalidateFilter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<BlurImageFilter> BlurImageFilter::Make() {
     *     return sk_sp<BlurImageFilter>(new BlurImageFilter());
     * }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
