package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.codec.SkCodec
import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class MultiFrameImageAsset final : public ImageAsset {
 * public:
 *     // Clients must call SkCodec::Register() to load the required decoding image codecs before
 *     // calling Make. For example:
 *     //     SkCodec::Register(SkPngDecoder::Decoder());
 *     static sk_sp<MultiFrameImageAsset> Make(sk_sp<SkData>,
 *                                             ImageDecodeStrategy = ImageDecodeStrategy::kLazyDecode);
 *     // If the client has already decoded the data, they can use this constructor.
 *     static sk_sp<MultiFrameImageAsset> Make(std::unique_ptr<SkCodec>,
 *                                             ImageDecodeStrategy = ImageDecodeStrategy::kLazyDecode);
 *
 *     bool isMultiFrame() override;
 *
 *     // Animation duration, in ms.
 *     float duration() const;
 *
 *     sk_sp<SkImage> getFrame(float t) override;
 *
 * private:
 *     explicit MultiFrameImageAsset(std::unique_ptr<SkAnimCodecPlayer>, ImageDecodeStrategy);
 *
 *     sk_sp<SkImage> generateFrame(float t);
 *
 *     std::unique_ptr<SkAnimCodecPlayer> fPlayer;
 *     sk_sp<SkImage>                     fCachedFrame;
 *     ImageDecodeStrategy fStrategy;
 *
 *     using INHERITED = ImageAsset;
 * }
 * ```
 */
public class MultiFrameImageAsset public constructor(
  player: SkAnimCodecPlayer?,
  strat: ImageDecodeStrategy,
) : ImageAsset() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAnimCodecPlayer> fPlayer
   * ```
   */
  private var fPlayer: SkAnimCodecPlayer? = TODO("Initialize fPlayer")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>                     fCachedFrame
   * ```
   */
  private var fCachedFrame: Int = TODO("Initialize fCachedFrame")

  /**
   * C++ original:
   * ```cpp
   * ImageDecodeStrategy fStrategy
   * ```
   */
  private var fStrategy: ImageDecodeStrategy = TODO("Initialize fStrategy")

  /**
   * C++ original:
   * ```cpp
   * bool MultiFrameImageAsset::isMultiFrame() { return fPlayer->duration() > 0; }
   * ```
   */
  public override fun isMultiFrame(): Boolean {
    TODO("Implement isMultiFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * float MultiFrameImageAsset::duration() const { return fPlayer->duration(); }
   * ```
   */
  public fun duration(): Float {
    TODO("Implement duration")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> MultiFrameImageAsset::getFrame(float t) {
   *     // For static images we can reuse the cached frame
   *     // (which includes the optional pre-decode step).
   *     if (!fCachedFrame || this->isMultiFrame()) {
   *         fCachedFrame = this->generateFrame(t);
   *     }
   *
   *     return fCachedFrame;
   * }
   * ```
   */
  public override fun getFrame(t: Float): Int {
    TODO("Implement getFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> MultiFrameImageAsset::generateFrame(float t) {
   *     auto decode = [](sk_sp<SkImage> image) {
   *         SkASSERT(image->isLazyGenerated());
   *
   *         static constexpr size_t kMaxArea = 2048 * 2048;
   *         const auto image_area = SkToSizeT(image->width() * image->height());
   *
   *         if (image_area > kMaxArea) {
   *             // When the image is too large, decode and scale down to a reasonable size.
   *             const auto scale = std::sqrt(static_cast<float>(kMaxArea) / image_area);
   *             const auto info  = SkImageInfo::MakeN32Premul(scale * image->width(),
   *                                                           scale * image->height());
   *             SkBitmap bm;
   *             if (bm.tryAllocPixels(info, info.minRowBytes()) &&
   *                     image->scalePixels(bm.pixmap(),
   *                                        SkSamplingOptions(SkFilterMode::kLinear,
   *                                                          SkMipmapMode::kNearest),
   *                                        SkImage::kDisallow_CachingHint)) {
   *                 image = bm.asImage();
   *             }
   *         } else {
   *             // When the image size is OK, just force-decode.
   *             image = image->makeRasterImage(nullptr);
   *         }
   *
   *         return image;
   *     };
   *
   *     fPlayer->seek(static_cast<uint32_t>(t * 1000));
   *     auto frame = fPlayer->getFrame();
   *
   *     if (fStrategy == ImageDecodeStrategy::kPreDecode && frame && frame->isLazyGenerated()) {
   *         // The multi-frame decoder should never return lazy images.
   *         SkASSERT(!this->isMultiFrame());
   *         frame = decode(std::move(frame));
   *     }
   *
   *     return frame;
   * }
   * ```
   */
  private fun generateFrame(t: Float): Int {
    TODO("Implement generateFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MultiFrameImageAsset> MultiFrameImageAsset::Make(sk_sp<SkData> data, ImageDecodeStrategy strat) {
   *     if (auto codec = SkCodec::MakeFromData(std::move(data))) {
   *         return sk_sp<MultiFrameImageAsset>(new MultiFrameImageAsset(
   *                 std::make_unique<SkAnimCodecPlayer>(std::move(codec)), strat));
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun make(`data`: SkSp<SkData>, strat: ImageDecodeStrategy): SkSp<MultiFrameImageAsset> {
    TODO("Implement make")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<MultiFrameImageAsset> Make(sk_sp<SkData>,
     *                                             ImageDecodeStrategy = ImageDecodeStrategy::kLazyDecode)
     * ```
     */
    public fun make(param0: SkSp<SkData>, param1: ImageDecodeStrategy = TODO()): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<MultiFrameImageAsset> Make(std::unique_ptr<SkCodec>,
     *                                             ImageDecodeStrategy = ImageDecodeStrategy::kLazyDecode)
     * ```
     */
    public fun make(param0: SkCodec?, param1: ImageDecodeStrategy = TODO()): Int {
      TODO("Implement make")
    }
  }
}
