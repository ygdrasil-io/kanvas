package org.skia.tests

import org.skia.foundation.SkPaint

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersText_CF : public ImageFiltersTextBaseGM {
 * public:
 *     ImageFiltersText_CF() : ImageFiltersTextBaseGM("color") {}
 *
 *     void installFilter(SkPaint* paint) override {
 *         paint->setColorFilter(SkColorFilters::Blend(SK_ColorBLUE, SkBlendMode::kSrcIn));
 *     }
 * }
 * ```
 */
public open class ImageFiltersTextCF public constructor() : ImageFiltersTextBaseGM(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void installFilter(SkPaint* paint) override {
   *         paint->setColorFilter(SkColorFilters::Blend(SK_ColorBLUE, SkBlendMode::kSrcIn));
   *     }
   * ```
   */
  public override fun installFilter(paint: SkPaint?) {
    TODO("Implement installFilter")
  }
}
