package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ExternalImageFilter final : public ImageFilter {
 * public:
 *     ~ExternalImageFilter() override;
 *
 *     static sk_sp<ExternalImageFilter> Make() {
 *         return sk_sp<ExternalImageFilter>(new ExternalImageFilter());
 *     }
 *
 *     SG_ATTRIBUTE(ImageFilter, sk_sp<SkImageFilter>, fImageFilter)
 *
 * private:
 *     ExternalImageFilter();
 *
 *     sk_sp<SkImageFilter> onRevalidateFilter() override { return fImageFilter; }
 *
 *     sk_sp<SkImageFilter> fImageFilter;
 * }
 * ```
 */
public class ExternalImageFilter public constructor() : ImageFilter() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> fImageFilter
   * ```
   */
  private var fImageFilter: Int = TODO("Initialize fImageFilter")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(ImageFilter, sk_sp<SkImageFilter>, fImageFilter)
   * ```
   */
  public fun sgATTRIBUTE(param0: ImageFilter, param1: SkSp<SkImageFilter>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> onRevalidateFilter() override { return fImageFilter; }
   * ```
   */
  public override fun onRevalidateFilter(): Int {
    TODO("Implement onRevalidateFilter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<ExternalImageFilter> Make() {
     *         return sk_sp<ExternalImageFilter>(new ExternalImageFilter());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
