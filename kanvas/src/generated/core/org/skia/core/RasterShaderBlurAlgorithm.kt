package org.skia.core

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class RasterShaderBlurAlgorithm : public SkShaderBlurAlgorithm {
 * public:
 *     sk_sp<SkDevice> makeDevice(const SkImageInfo& imageInfo) const override {
 *         // This Device will only be used to draw blurs, so use default SkSurfaceProps. The pixel
 *         // geometry and font configuration do not matter. This is not a GPU surface, so DMSAA and
 *         // the kAlwaysDither surface property are also irrelevant.
 *         return SkBitmapDevice::Create(imageInfo, SkSurfaceProps{});
 *     }
 * }
 * ```
 */
public open class RasterShaderBlurAlgorithm : SkShaderBlurAlgorithm() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDevice> makeDevice(const SkImageInfo& imageInfo) const override {
   *         // This Device will only be used to draw blurs, so use default SkSurfaceProps. The pixel
   *         // geometry and font configuration do not matter. This is not a GPU surface, so DMSAA and
   *         // the kAlwaysDither surface property are also irrelevant.
   *         return SkBitmapDevice::Create(imageInfo, SkSurfaceProps{});
   *     }
   * ```
   */
  public override fun makeDevice(imageInfo: SkImageInfo): SkSp<SkDevice> {
    TODO("Implement makeDevice")
  }
}
