package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API ImageAsset : public SkRefCnt {
 * public:
 *     /**
 *      * Returns true if the image asset is animated.
 *      */
 *     virtual bool isMultiFrame() = 0;
 *
 *     /**
 *      * DEPRECATED: override getFrameData() instead.
 *      *
 *      * Returns the SkImage for a given frame.
 *      *
 *      * If the image asset is static, getFrame() is only called once, at animation load time.
 *      * Otherwise, this gets invoked every time the animation time is adjusted (on every seek).
 *      *
 *      * Embedders should cache and serve the same SkImage whenever possible, for efficiency.
 *      *
 *      * @param t   Frame time code, in seconds, relative to the image layer timeline origin
 *      *            (in-point).
 *      */
 *     virtual sk_sp<SkImage> getFrame(float t);
 *
 *     // Describes how the frame image is to be scaled to the animation-declared asset size.
 *     enum class SizeFit {
 *         // See SkMatrix::ScaleToFit
 *         kFill   = SkMatrix::kFill_ScaleToFit,
 *         kStart  = SkMatrix::kStart_ScaleToFit,
 *         kCenter = SkMatrix::kCenter_ScaleToFit,
 *         kEnd    = SkMatrix::kEnd_ScaleToFit,
 *
 *         // No scaling.
 *         kNone,
 *     };
 *
 *     struct FrameData {
 *         // SkImage payload.
 *         sk_sp<SkImage>    image;
 *         // Resampling parameters.
 *         SkSamplingOptions sampling;
 *         // Additional image transform to be applied before AE scaling rules.
 *         SkMatrix          matrix = SkMatrix::I();
 *         // Strategy for image size -> AE asset size scaling.
 *         SizeFit           scaling = SizeFit::kCenter;
 *     };
 *
 *     /**
 *      * Returns the payload for a given frame.
 *      *
 *      * If the image asset is static, getFrameData() is only called once, at animation load time.
 *      * Otherwise, this gets invoked every time the animation time is adjusted (on every seek).
 *      *
 *      * Embedders should cache and serve the same SkImage whenever possible, for efficiency.
 *      *
 *      * @param t   Frame time code, in seconds, relative to the image layer timeline origin
 *      *            (in-point).
 *      */
 *     virtual FrameData getFrameData(float t);
 * }
 * ```
 */
public abstract class ImageAsset : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual bool isMultiFrame() = 0
   * ```
   */
  public abstract fun isMultiFrame(): Boolean

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> ImageAsset::getFrame(float t) {
   *     return nullptr;
   * }
   * ```
   */
  public open fun getFrame(t: Float): Int {
    TODO("Implement getFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * ImageAsset::FrameData ImageAsset::getFrameData(float t) {
   *     // legacy behavior
   *     return {
   *         this->getFrame(t),
   *         SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNearest),
   *         SkMatrix::I(),
   *         SizeFit::kCenter,
   *     };
   * }
   * ```
   */
  public open fun getFrameData(t: Float): FrameData {
    TODO("Implement getFrameData")
  }

  public data class FrameData public constructor(
    public var image: Int,
    public var sampling: Int,
    public var matrix: Int,
    public var scaling: undefined.SizeFit,
  )

  public enum class SizeFit {
    kFill,
    kStart,
    kCenter,
    kEnd,
    kNone,
  }
}

public typealias MultiFrameImageAssetINHERITED = ImageAsset
