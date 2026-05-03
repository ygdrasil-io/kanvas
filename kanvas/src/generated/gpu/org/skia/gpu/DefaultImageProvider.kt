package org.skia.gpu

import org.skia.foundation.SkImage
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class DefaultImageProvider final : public ImageProvider {
 * public:
 *     static sk_sp<DefaultImageProvider> Make() { return sk_sp(new DefaultImageProvider); }
 *
 *     sk_sp<SkImage> findOrCreate(Recorder* recorder,
 *                                 const SkImage* image,
 *                                 SkImage::RequiredProperties) override {
 *         SkASSERT(!as_IB(image)->isGraphiteBacked());
 *
 *         return nullptr;
 *     }
 *
 * private:
 *     DefaultImageProvider() {}
 * }
 * ```
 */
public class DefaultImageProvider public constructor() : ImageProvider() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> findOrCreate(Recorder* recorder,
   *                                 const SkImage* image,
   *                                 SkImage::RequiredProperties) override {
   *         SkASSERT(!as_IB(image)->isGraphiteBacked());
   *
   *         return nullptr;
   *     }
   * ```
   */
  public override fun findOrCreate(
    recorder: Recorder?,
    image: SkImage?,
    param2: SkImage.RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement findOrCreate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<DefaultImageProvider> Make() { return sk_sp(new DefaultImageProvider); }
     * ```
     */
    public fun make(): SkSp<DefaultImageProvider> {
      TODO("Implement make")
    }
  }
}
