package org.skia.tests

import org.skia.foundation.SkPaint

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersText_IF : public ImageFiltersTextBaseGM {
 * public:
 *     ImageFiltersText_IF() : ImageFiltersTextBaseGM("image") {}
 *
 *     void installFilter(SkPaint* paint) override {
 *         paint->setImageFilter(SkImageFilters::Blur(1.5f, 1.5f, nullptr));
 *     }
 * }
 * ```
 */
public open class ImageFiltersTextIF public constructor() : ImageFiltersTextBaseGM(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void installFilter(SkPaint* paint) override {
   *         paint->setImageFilter(SkImageFilters::Blur(1.5f, 1.5f, nullptr));
   *     }
   * ```
   */
  public override fun installFilter(paint: SkPaint?) {
    TODO("Implement installFilter")
  }
}
