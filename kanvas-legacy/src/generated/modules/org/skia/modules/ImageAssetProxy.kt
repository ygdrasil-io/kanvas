package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import org.skia.foundation.SkSp
import undefined.FrameData

/**
 * C++ original:
 * ```cpp
 * class skottie::SlotManager::ImageAssetProxy final : public skresources::ImageAsset {
 * public:
 *     explicit ImageAssetProxy(sk_sp<skresources::ImageAsset> asset)
 *     : fImageAsset(std::move(asset)) {}
 *
 *     // always returns true to force the FootageLayer to always redraw in case asset is swapped
 *     bool isMultiFrame() override { return true; }
 *
 *     FrameData getFrameData(float t) override {
 *         if (fImageAsset) {
 *             return fImageAsset->getFrameData(t);
 *         }
 *         return {nullptr , SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNearest),
 *             SkMatrix::I(), SizeFit::kCenter};
 *     }
 *
 *     void setImageAsset (sk_sp<skresources::ImageAsset> asset) {
 *         fImageAsset = std::move(asset);
 *     }
 *
 *     sk_sp<const skresources::ImageAsset> getImageAsset() const {
 *         return fImageAsset;
 *     }
 * private:
 *     sk_sp<skresources::ImageAsset> fImageAsset;
 * }
 * ```
 */
public open class ImageAssetProxy public constructor(
  asset: SkSp<ImageAsset>,
) : SlotManager.ImageAssetProxy(),
    Any,
    ImageAsset {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<skresources::ImageAsset> fImageAsset
   * ```
   */
  private var fImageAsset: SkSp<ImageAsset> = TODO("Initialize fImageAsset")

  /**
   * C++ original:
   * ```cpp
   * bool isMultiFrame() override { return true; }
   * ```
   */
  public override fun isMultiFrame(): Boolean {
    TODO("Implement isMultiFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * FrameData getFrameData(float t) override {
   *         if (fImageAsset) {
   *             return fImageAsset->getFrameData(t);
   *         }
   *         return {nullptr , SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNearest),
   *             SkMatrix::I(), SizeFit::kCenter};
   *     }
   * ```
   */
  public override fun getFrameData(t: Float): FrameData {
    TODO("Implement getFrameData")
  }

  /**
   * C++ original:
   * ```cpp
   * void setImageAsset (sk_sp<skresources::ImageAsset> asset) {
   *         fImageAsset = std::move(asset);
   *     }
   * ```
   */
  public fun setImageAsset(asset: SkSp<ImageAsset>) {
    TODO("Implement setImageAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const skresources::ImageAsset> getImageAsset() const {
   *         return fImageAsset;
   *     }
   * ```
   */
  public fun getImageAsset(): SkSp<ImageAsset> {
    TODO("Implement getImageAsset")
  }
}
