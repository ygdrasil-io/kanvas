package org.skia.gpu

import kotlin.Boolean
import kotlin.ULong
import org.skia.core.SkSpecialImage
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SpecialImage final : public SkSpecialImage {
 * public:
 *     SpecialImage(const SkIRect& subset, sk_sp<SkImage> image, const SkSurfaceProps& props)
 *             : SkSpecialImage(subset, image->uniqueID(), image->imageInfo().colorInfo(), props)
 *             , fImage(std::move(image)) {
 *         SkASSERT(as_IB(fImage)->isGraphiteBacked());
 *     }
 *
 *     size_t getSize() const override {
 *         return fImage->textureSize();
 *     }
 *
 *     bool isGraphiteBacked() const override { return true; }
 *
 *     SkISize backingStoreDimensions() const override {
 *         return fImage->dimensions();
 *     }
 *
 *     sk_sp<SkSpecialImage> onMakeBackingStoreSubset(const SkIRect& subset) const override {
 *         SkASSERT(fImage->bounds().contains(subset));
 *         return sk_make_sp<skgpu::graphite::SpecialImage>(subset, fImage, this->props());
 *     }
 *
 *     sk_sp<SkImage> asImage() const override { return fImage; }
 *
 * private:
 *     // TODO(b/299474380): SkSpecialImage is intended to go away in favor of just using SkImages
 *     // and tracking the intended srcRect explicitly in skif::FilterResult. Since Graphite tracks
 *     // device-linked textures via Images, the graphite special image just wraps an image.
 *     sk_sp<SkImage> fImage;
 * }
 * ```
 */
public class SpecialImage public constructor(
  subset: SkIRect,
  image: SkSp<SkImage>,
  props: SkSurfaceProps,
) : SkSpecialImage(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * size_t getSize() const override {
   *         return fImage->textureSize();
   *     }
   * ```
   */
  public override fun getSize(): ULong {
    TODO("Implement getSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isGraphiteBacked() const override { return true; }
   * ```
   */
  public override fun isGraphiteBacked(): Boolean {
    TODO("Implement isGraphiteBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize backingStoreDimensions() const override {
   *         return fImage->dimensions();
   *     }
   * ```
   */
  public override fun backingStoreDimensions(): SkISize {
    TODO("Implement backingStoreDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> onMakeBackingStoreSubset(const SkIRect& subset) const override {
   *         SkASSERT(fImage->bounds().contains(subset));
   *         return sk_make_sp<skgpu::graphite::SpecialImage>(subset, fImage, this->props());
   *     }
   * ```
   */
  public override fun onMakeBackingStoreSubset(subset: SkIRect): SkSp<SkSpecialImage> {
    TODO("Implement onMakeBackingStoreSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> asImage() const override { return fImage; }
   * ```
   */
  public override fun asImage(): SkSp<SkImage> {
    TODO("Implement asImage")
  }
}
