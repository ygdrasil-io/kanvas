package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class FootageAnimator final : public Animator {
 * public:
 *     FootageAnimator(sk_sp<ImageAsset> asset,
 *                     sk_sp<sksg::Image> image_node,
 *                     sk_sp<sksg::Matrix<SkMatrix>> image_transform_node,
 *                     const SkISize& asset_size,
 *                     float time_bias, float time_scale)
 *         : fAsset(std::move(asset))
 *         , fImageNode(std::move(image_node))
 *         , fImageTransformNode(std::move(image_transform_node))
 *         , fAssetSize(asset_size)
 *         , fTimeBias(time_bias)
 *         , fTimeScale(time_scale)
 *         , fIsMultiframe(fAsset->isMultiFrame()) {}
 *
 *     StateChanged onSeek(float t) override {
 *         if (!fIsMultiframe && fImageNode->getImage()) {
 *             // Single frame already resolved.
 *             return false;
 *         }
 *
 *         auto frame_data = fAsset->getFrameData((t + fTimeBias) * fTimeScale);
 *         const auto m = image_matrix(frame_data, fAssetSize);
 *         if (frame_data.image    != fImageNode->getImage() ||
 *             frame_data.sampling != fImageNode->getSamplingOptions() ||
 *             m                   != fImageTransformNode->getMatrix()) {
 *
 *             fImageNode->setImage(std::move(frame_data.image));
 *             fImageNode->setSamplingOptions(frame_data.sampling);
 *             fImageTransformNode->setMatrix(m);
 *             return true;
 *         }
 *
 *         return false;
 *     }
 *
 * private:
 *     const sk_sp<ImageAsset>             fAsset;
 *     const sk_sp<sksg::Image>            fImageNode;
 *     const sk_sp<sksg::Matrix<SkMatrix>> fImageTransformNode;
 *     const SkISize                       fAssetSize;
 *     const float                         fTimeBias,
 *                                         fTimeScale;
 *     const bool                          fIsMultiframe;
 * }
 * ```
 */
public class FootageAnimator public constructor(
  asset: SkSp<ImageAsset>,
  imageNode: SkSp<Image>,
  imageTransformNode: SkSp<Matrix<SkMatrix>>,
  assetSize: SkISize,
  timeBias: Float,
  timeScale: Float,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<ImageAsset>             fAsset
   * ```
   */
  private val fAsset: SkSp<ImageAsset> = TODO("Initialize fAsset")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Image>            fImageNode
   * ```
   */
  private val fImageNode: SkSp<Image> = TODO("Initialize fImageNode")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::Matrix<SkMatrix>> fImageTransformNode
   * ```
   */
  private val fImageTransformNode: SkSp<Matrix<SkMatrix>> = TODO("Initialize fImageTransformNode")

  /**
   * C++ original:
   * ```cpp
   * const SkISize                       fAssetSize
   * ```
   */
  private val fAssetSize: SkISize = TODO("Initialize fAssetSize")

  /**
   * C++ original:
   * ```cpp
   * const float                         fTimeBias
   * ```
   */
  private val fTimeBias: Float = TODO("Initialize fTimeBias")

  /**
   * C++ original:
   * ```cpp
   * const float                         fTimeBias,
   *                                         fTimeScale
   * ```
   */
  private val fTimeScale: Float = TODO("Initialize fTimeScale")

  /**
   * C++ original:
   * ```cpp
   * const bool                          fIsMultiframe
   * ```
   */
  private val fIsMultiframe: Boolean = TODO("Initialize fIsMultiframe")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         if (!fIsMultiframe && fImageNode->getImage()) {
   *             // Single frame already resolved.
   *             return false;
   *         }
   *
   *         auto frame_data = fAsset->getFrameData((t + fTimeBias) * fTimeScale);
   *         const auto m = image_matrix(frame_data, fAssetSize);
   *         if (frame_data.image    != fImageNode->getImage() ||
   *             frame_data.sampling != fImageNode->getSamplingOptions() ||
   *             m                   != fImageTransformNode->getMatrix()) {
   *
   *             fImageNode->setImage(std::move(frame_data.image));
   *             fImageNode->setSamplingOptions(frame_data.sampling);
   *             fImageTransformNode->setMatrix(m);
   *             return true;
   *         }
   *
   *         return false;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
