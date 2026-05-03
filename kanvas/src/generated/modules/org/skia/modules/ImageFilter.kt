package org.skia.modules

import kotlin.Int
import org.skia.effects.SkImageFilters
import org.skia.math.SkMatrix
import undefined.CropRect

/**
 * C++ original:
 * ```cpp
 * class ImageFilter : public Node {
 * public:
 *     ~ImageFilter() override;
 *
 *     const sk_sp<SkImageFilter>& getFilter() const {
 *         SkASSERT(!this->hasInval());
 *         return fFilter;
 *     }
 *
 *     SG_ATTRIBUTE(CropRect, SkImageFilters::CropRect, fCropRect)
 *
 * protected:
 *     ImageFilter();
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) final;
 *
 *     virtual sk_sp<SkImageFilter> onRevalidateFilter() = 0;
 *
 * private:
 *     sk_sp<SkImageFilter>     fFilter;
 *     SkImageFilters::CropRect fCropRect = std::nullopt;
 *
 *     using INHERITED = Node;
 * }
 * ```
 */
public abstract class ImageFilter public constructor() : Node(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter>     fFilter
   * ```
   */
  private var fFilter: Int = TODO("Initialize fFilter")

  /**
   * C++ original:
   * ```cpp
   * SkImageFilters::CropRect fCropRect
   * ```
   */
  private var fCropRect: Int = TODO("Initialize fCropRect")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkImageFilter>& getFilter() const {
   *         SkASSERT(!this->hasInval());
   *         return fFilter;
   *     }
   * ```
   */
  public fun getFilter(): Int {
    TODO("Implement getFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(CropRect, SkImageFilters::CropRect, fCropRect)
   * ```
   */
  public fun sgATTRIBUTE(param0: CropRect, param1: SkImageFilters.CropRect): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect ImageFilter::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     SkASSERT(this->hasInval());
   *
   *     fFilter = this->onRevalidateFilter();
   *     return SkRect::MakeEmpty();
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImageFilter> onRevalidateFilter() = 0
   * ```
   */
  protected abstract fun onRevalidateFilter(): Int
}

public typealias DropShadowImageFilterINHERITED = ImageFilter

public typealias BlurImageFilterINHERITED = ImageFilter
