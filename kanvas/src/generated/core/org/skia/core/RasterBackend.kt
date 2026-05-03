package org.skia.core

import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RasterBackend : public Backend {
 * public:
 *
 *     RasterBackend(const SkSurfaceProps& surfaceProps, SkColorType colorType)
 *             : Backend(SkImageFilterCache::Get(), surfaceProps, colorType) {}
 *
 *     sk_sp<SkDevice> makeDevice(SkISize size,
 *                                sk_sp<SkColorSpace> colorSpace,
 *                                const SkSurfaceProps* props) const override {
 *         SkImageInfo imageInfo = SkImageInfo::Make(size,
 *                                                   this->colorType(),
 *                                                   kPremul_SkAlphaType,
 *                                                   std::move(colorSpace));
 *         return SkBitmapDevice::Create(imageInfo, props ? *props : this->surfaceProps());
 *     }
 *
 *     sk_sp<SkSpecialImage> makeImage(const SkIRect& subset, sk_sp<SkImage> image) const override {
 *         return SkSpecialImages::MakeFromRaster(subset, image, this->surfaceProps());
 *     }
 *
 *     sk_sp<SkImage> getCachedBitmap(const SkBitmap& data) const override {
 *         return SkImages::RasterFromBitmap(data);
 *     }
 *
 *     const SkBlurEngine* getBlurEngine() const override {
 *         return SkBlurEngine::GetRasterBlurEngine();
 *     }
 * }
 * ```
 */
public open class RasterBackend public constructor(
  surfaceProps: SkSurfaceProps,
  colorType: SkColorType,
) : Backend(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDevice> makeDevice(SkISize size,
   *                                sk_sp<SkColorSpace> colorSpace,
   *                                const SkSurfaceProps* props) const override {
   *         SkImageInfo imageInfo = SkImageInfo::Make(size,
   *                                                   this->colorType(),
   *                                                   kPremul_SkAlphaType,
   *                                                   std::move(colorSpace));
   *         return SkBitmapDevice::Create(imageInfo, props ? *props : this->surfaceProps());
   *     }
   * ```
   */
  public override fun makeDevice(
    size: SkISize,
    colorSpace: SkSp<SkColorSpace>,
    props: SkSurfaceProps?,
  ): SkSp<SkDevice> {
    TODO("Implement makeDevice")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> makeImage(const SkIRect& subset, sk_sp<SkImage> image) const override {
   *         return SkSpecialImages::MakeFromRaster(subset, image, this->surfaceProps());
   *     }
   * ```
   */
  public override fun makeImage(subset: SkIRect, image: SkSp<SkImage>): SkSp<SkSpecialImage> {
    TODO("Implement makeImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> getCachedBitmap(const SkBitmap& data) const override {
   *         return SkImages::RasterFromBitmap(data);
   *     }
   * ```
   */
  public override fun getCachedBitmap(`data`: SkBitmap): SkSp<SkImage> {
    TODO("Implement getCachedBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBlurEngine* getBlurEngine() const override {
   *         return SkBlurEngine::GetRasterBlurEngine();
   *     }
   * ```
   */
  public override fun getBlurEngine(): SkBlurEngine {
    TODO("Implement getBlurEngine")
  }
}
