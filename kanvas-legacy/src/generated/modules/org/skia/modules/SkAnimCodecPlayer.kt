package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SkAnimCodecPlayer {
 * public:
 *     explicit SkAnimCodecPlayer(std::unique_ptr<SkCodec> codec);
 *     ~SkAnimCodecPlayer();
 *
 *     /**
 *      *  Returns the current frame of the animation. This defaults to the first frame for
 *      *  animated codecs (i.e. msec = 0). Calling this multiple times (without calling seek())
 *      *  will always return the same image object (or null if there was an error).
 *      */
 *     sk_sp<SkImage> getFrame();
 *
 *     /**
 *      *  Return the size of the image(s) that will be returned by getFrame().
 *      */
 *     SkISize dimensions() const;
 *
 *     /**
 *      *  Returns the total duration of the animation in milliseconds. Returns 0 for a single-frame
 *      *  image.
 *      */
 *     uint32_t duration() const { return fTotalDuration; }
 *
 *     /**
 *      *  Finds the closest frame associated with the time code (in milliseconds) and sets that
 *      *  to be the current frame (call getFrame() to retrieve that image).
 *      *  Returns true iff this call to seek() changed the "current frame" for the animation.
 *      *  Thus if seek() returns false, then getFrame() will return the same image as it did
 *      *  before this call to seek().
 *      */
 *     bool seek(uint32_t msec);
 *
 *
 * private:
 *     std::unique_ptr<SkCodec>        fCodec;
 *     SkImageInfo                     fImageInfo;
 *     std::vector<SkCodec::FrameInfo> fFrameInfos;
 *     std::vector<sk_sp<SkImage> >    fImages;
 *     int                             fCurrIndex = 0;
 *     uint32_t                        fTotalDuration;
 *
 *     sk_sp<SkImage> getFrameAt(int index);
 * }
 * ```
 */
public data class SkAnimCodecPlayer public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkCodec>        fCodec
   * ```
   */
  private var fCodec: Int,
  /**
   * C++ original:
   * ```cpp
   * SkImageInfo                     fImageInfo
   * ```
   */
  private var fImageInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkCodec::FrameInfo> fFrameInfos
   * ```
   */
  private var fFrameInfos: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkImage> >    fImages
   * ```
   */
  private var fImages: Int,
  /**
   * C++ original:
   * ```cpp
   * int                             fCurrIndex = 0
   * ```
   */
  private var fCurrIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t                        fTotalDuration
   * ```
   */
  private var fTotalDuration: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkAnimCodecPlayer::getFrame() {
   *     SkASSERT(fTotalDuration > 0 || fImages.size() == 1);
   *
   *     return fTotalDuration > 0
   *         ? this->getFrameAt(fCurrIndex)
   *         : fImages.front();
   * }
   * ```
   */
  public fun getFrame(): Int {
    TODO("Implement getFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize SkAnimCodecPlayer::dimensions() const {
   *     if (!fCodec) {
   *         auto image = fImages.front();
   *         return image ? image->dimensions() : SkISize::MakeEmpty();
   *     }
   *     if (SkEncodedOriginSwapsWidthHeight(fCodec->getOrigin())) {
   *         return { fImageInfo.height(), fImageInfo.width() };
   *     }
   *     return { fImageInfo.width(), fImageInfo.height() };
   * }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t duration() const { return fTotalDuration; }
   * ```
   */
  public fun duration(): Int {
    TODO("Implement duration")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnimCodecPlayer::seek(uint32_t msec) {
   *     if (!fTotalDuration) {
   *         return false;
   *     }
   *
   *     msec %= fTotalDuration;
   *
   *     auto lower = std::lower_bound(fFrameInfos.begin(), fFrameInfos.end(), msec,
   *                                   [](const SkCodec::FrameInfo& info, uint32_t msec) {
   *                                       return (uint32_t)info.fDuration <= msec;
   *                                   });
   *     int prevIndex = fCurrIndex;
   *     fCurrIndex = lower - fFrameInfos.begin();
   *     return fCurrIndex != prevIndex;
   * }
   * ```
   */
  public fun seek(msec: UInt): Boolean {
    TODO("Implement seek")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkAnimCodecPlayer::getFrameAt(int index) {
   *     SkASSERT((unsigned)index < fFrameInfos.size());
   *
   *     if (fImages[index]) {
   *         return fImages[index];
   *     }
   *
   *     size_t rb = fImageInfo.minRowBytes();
   *     size_t size = fImageInfo.computeByteSize(rb);
   *     auto data = SkData::MakeUninitialized(size);
   *
   *     SkCodec::Options opts;
   *     opts.fFrameIndex = index;
   *
   *     const auto origin = fCodec->getOrigin();
   *     const auto orientedDims = this->dimensions();
   *     const auto originMatrix = SkEncodedOriginToMatrix(origin, orientedDims.width(),
   *                                                               orientedDims.height());
   *
   *     SkPaint paint;
   *     paint.setBlendMode(SkBlendMode::kSrc);
   *
   *     auto imageInfo = fImageInfo;
   *     if (fFrameInfos[index].fAlphaType != kOpaque_SkAlphaType && imageInfo.isOpaque()) {
   *         imageInfo = imageInfo.makeAlphaType(kPremul_SkAlphaType);
   *     }
   *     const int requiredFrame = fFrameInfos[index].fRequiredFrame;
   *     if (requiredFrame != SkCodec::kNoFrame && fImages[requiredFrame]) {
   *         auto requiredImage = fImages[requiredFrame];
   *         auto canvas = SkCanvas::MakeRasterDirect(imageInfo, data->writable_data(), rb);
   *         if (origin != kDefault_SkEncodedOrigin) {
   *             // The required frame is stored after applying the origin. Undo that,
   *             // because the codec decodes prior to applying the origin.
   *             // FIXME: Another approach would be to decode the frame's delta on top
   *             // of transparent black, and then draw that through the origin matrix
   *             // onto the required frame. To do that, SkCodec needs to expose the
   *             // rectangle of the delta and the blend mode, so we can handle
   *             // kRestoreBGColor frames and Blend::kSrc.
   *             canvas->concat(*originMatrix.invert());
   *         }
   *         canvas->drawImage(requiredImage, 0, 0, SkSamplingOptions(), &paint);
   *         opts.fPriorFrame = requiredFrame;
   *     }
   *
   *     if (SkCodec::kSuccess != fCodec->getPixels(imageInfo, data->writable_data(), rb, &opts)) {
   *         return nullptr;
   *     }
   *
   *     auto image = SkImages::RasterFromData(imageInfo, std::move(data), rb);
   *     if (origin != kDefault_SkEncodedOrigin) {
   *         imageInfo = imageInfo.makeDimensions(orientedDims);
   *         rb = imageInfo.minRowBytes();
   *         size = imageInfo.computeByteSize(rb);
   *         data = SkData::MakeUninitialized(size);
   *         auto canvas = SkCanvas::MakeRasterDirect(imageInfo, data->writable_data(), rb);
   *         canvas->concat(originMatrix);
   *         canvas->drawImage(image, 0, 0, SkSamplingOptions(), &paint);
   *         image = SkImages::RasterFromData(imageInfo, std::move(data), rb);
   *     }
   *     return fImages[index] = image;
   * }
   * ```
   */
  private fun getFrameAt(index: Int): Int {
    TODO("Implement getFrameAt")
  }
}
