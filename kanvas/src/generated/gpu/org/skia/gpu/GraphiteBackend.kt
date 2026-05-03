package org.skia.gpu

import org.skia.core.Algorithm
import org.skia.core.Backend
import org.skia.core.SkBlurEngine
import org.skia.core.SkDevice
import org.skia.core.SkShaderBlurAlgorithm
import org.skia.core.SkSpecialImage
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class GraphiteBackend :
 *         public Backend,
 *         private SkShaderBlurAlgorithm,
 *         private SkBlurEngine {
 * public:
 *
 *     GraphiteBackend(skgpu::graphite::Recorder* recorder,
 *                     const SkSurfaceProps& surfaceProps,
 *                     SkColorType colorType)
 *             : Backend(SkImageFilterCache::Create(SkImageFilterCache::kDefaultTransientSize),
 *                       surfaceProps, colorType)
 *             , fRecorder(recorder) {}
 *
 *     // Backend
 *     sk_sp<SkDevice> makeDevice(SkISize size,
 *                                sk_sp<SkColorSpace> colorSpace,
 *                                const SkSurfaceProps* props) const override {
 *         SkImageInfo imageInfo = SkImageInfo::Make(size,
 *                                                   this->colorType(),
 *                                                   kPremul_SkAlphaType,
 *                                                   std::move(colorSpace));
 *         return skgpu::graphite::Device::Make(fRecorder,
 *                                              imageInfo,
 *                                              skgpu::Budgeted::kYes,
 *                                              skgpu::Mipmapped::kNo,
 *                                              SkBackingFit::kApprox,
 *                                              props ? *props : this->surfaceProps(),
 *                                              skgpu::graphite::LoadOp::kDiscard,
 *                                              "ImageFilterResult");
 *     }
 *
 *     sk_sp<SkSpecialImage> makeImage(const SkIRect& subset, sk_sp<SkImage> image) const override {
 *         return SkSpecialImages::MakeGraphite(fRecorder, subset, image, this->surfaceProps());
 *     }
 *
 *     sk_sp<SkImage> getCachedBitmap(const SkBitmap& data) const override {
 *         auto proxy = skgpu::graphite::RecorderPriv::CreateCachedProxy(fRecorder, data,
 *                                                                       "ImageFilterCachedBitmap");
 *         if (!proxy) {
 *             return nullptr;
 *         }
 *
 *         const SkColorInfo& colorInfo = data.info().colorInfo();
 *         skgpu::Swizzle swizzle = fRecorder->priv().caps()->getReadSwizzle(colorInfo.colorType(),
 *                                                                           proxy->textureInfo());
 *         return sk_make_sp<skgpu::graphite::Image>(
 *                 skgpu::graphite::TextureProxyView(std::move(proxy), swizzle),
 *                 colorInfo);
 *     }
 *
 *     const SkBlurEngine* getBlurEngine() const override { return this; }
 *
 *     // SkBlurEngine
 *     const SkBlurEngine::Algorithm* findAlgorithm(SkSize sigma,
 *                                                  SkColorType colorType) const override {
 *         // The runtime effect blurs handle all tilemodes and color types
 *         return this;
 *     }
 *
 *     // SkShaderBlurAlgorithm
 *     sk_sp<SkDevice> makeDevice(const SkImageInfo& imageInfo) const override {
 *         return skgpu::graphite::Device::Make(fRecorder,
 *                                              imageInfo,
 *                                              skgpu::Budgeted::kYes,
 *                                              skgpu::Mipmapped::kNo,
 *                                              SkBackingFit::kApprox,
 *                                              this->surfaceProps(),
 *                                              skgpu::graphite::LoadOp::kDiscard,
 *                                              "EvalBlurTexture");
 *     }
 *
 * private:
 *     skgpu::graphite::Recorder* fRecorder;
 * }
 * ```
 */
public open class GraphiteBackend public constructor(
  recorder: Recorder?,
  surfaceProps: SkSurfaceProps,
  colorType: SkColorType,
) : Backend(TODO(), TODO(), TODO()),
    SkShaderBlurAlgorithm,
    SkBlurEngine {
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder? = TODO("Initialize fRecorder")

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
   *         return skgpu::graphite::Device::Make(fRecorder,
   *                                              imageInfo,
   *                                              skgpu::Budgeted::kYes,
   *                                              skgpu::Mipmapped::kNo,
   *                                              SkBackingFit::kApprox,
   *                                              props ? *props : this->surfaceProps(),
   *                                              skgpu::graphite::LoadOp::kDiscard,
   *                                              "ImageFilterResult");
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
   *         return SkSpecialImages::MakeGraphite(fRecorder, subset, image, this->surfaceProps());
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
   *         auto proxy = skgpu::graphite::RecorderPriv::CreateCachedProxy(fRecorder, data,
   *                                                                       "ImageFilterCachedBitmap");
   *         if (!proxy) {
   *             return nullptr;
   *         }
   *
   *         const SkColorInfo& colorInfo = data.info().colorInfo();
   *         skgpu::Swizzle swizzle = fRecorder->priv().caps()->getReadSwizzle(colorInfo.colorType(),
   *                                                                           proxy->textureInfo());
   *         return sk_make_sp<skgpu::graphite::Image>(
   *                 skgpu::graphite::TextureProxyView(std::move(proxy), swizzle),
   *                 colorInfo);
   *     }
   * ```
   */
  public override fun getCachedBitmap(`data`: SkBitmap): SkSp<SkImage> {
    TODO("Implement getCachedBitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBlurEngine* getBlurEngine() const override { return this; }
   * ```
   */
  public override fun getBlurEngine(): SkBlurEngine {
    TODO("Implement getBlurEngine")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBlurEngine::Algorithm* findAlgorithm(SkSize sigma,
   *                                                  SkColorType colorType) const override {
   *         // The runtime effect blurs handle all tilemodes and color types
   *         return this;
   *     }
   * ```
   */
  public override fun findAlgorithm(sigma: SkSize, colorType: SkColorType): Algorithm {
    TODO("Implement findAlgorithm")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDevice> makeDevice(const SkImageInfo& imageInfo) const override {
   *         return skgpu::graphite::Device::Make(fRecorder,
   *                                              imageInfo,
   *                                              skgpu::Budgeted::kYes,
   *                                              skgpu::Mipmapped::kNo,
   *                                              SkBackingFit::kApprox,
   *                                              this->surfaceProps(),
   *                                              skgpu::graphite::LoadOp::kDiscard,
   *                                              "EvalBlurTexture");
   *     }
   * ```
   */
  public override fun makeDevice(imageInfo: SkImageInfo): SkSp<SkDevice> {
    TODO("Implement makeDevice")
  }
}
