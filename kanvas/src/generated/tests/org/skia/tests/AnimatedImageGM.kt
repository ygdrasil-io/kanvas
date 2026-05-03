package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnimatedImageGM : public skiagm::GM {
 *     const char*   fPath;
 *     const char*   fName;
 *     const int     fStep;
 *     const SkIRect fCropRect;
 *     SkISize       fSize;
 *     int           fTranslate;
 *     sk_sp<SkData> fData;
 *
 *     static const int kMaxFrames = 2;
 *
 *     void init() {
 *         if (!fData) {
 *             fData = GetResourceAsData(fPath);
 *             auto codec = SkCodec::MakeFromData(fData);
 *             auto dimensions = codec->dimensions();
 *
 *             fTranslate = std::max(dimensions.width(), dimensions.height()) // may be rotated
 *                          * 1.25f    // will be scaled up
 *                          + 2;       // padding
 *
 *             fSize = { fTranslate * kMaxFrames
 *                                  * 2    // crop and no-crop
 *                                  * 2,   // post-process and no post-process
 *                       fTranslate * 4    // 4 scales
 *                                  * 2 }; // makePictureSnapshot and getCurrentFrame
 *         }
 *     }
 * public:
 *     AnimatedImageGM(const char* path, const char* name, int step, SkIRect cropRect)
 *         : fPath(path)
 *         , fName(name)
 *         , fStep(step)
 *         , fCropRect(cropRect)
 *         , fSize{0, 0}
 *         , fTranslate(0)
 *     {}
 *     ~AnimatedImageGM() override = default;
 *
 *     SkString getName() const override { return SkStringPrintf("%s_animated_image", fName); }
 *
 *     SkISize getISize() override {
 *         this->init();
 *         return fSize;
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         this->init();
 *         for (bool usePic : { true, false }) {
 *             auto drawProc = [canvas, usePic](const sk_sp<SkAnimatedImage>& animatedImage) {
 *                 if (usePic) {
 *                     sk_sp<SkPicture> pic = animatedImage->makePictureSnapshot();
 *                     canvas->drawPicture(pic);
 *                 } else {
 *                     auto image = animatedImage->getCurrentFrame();
 *                     canvas->drawImage(image, 0, 0);
 *                 }
 *             };
 *             for (float scale : { 1.25f, 1.0f, .75f, .5f }) {
 *                 canvas->save();
 *                 for (bool doCrop : { false, true }) {
 *                     for (bool doPostProcess : { false, true }) {
 *                         auto codec = SkCodec::MakeFromData(fData);
 *                         const auto origin = codec->getOrigin();
 *                         auto androidCodec = SkAndroidCodec::MakeFromCodec(std::move(codec));
 *                         auto info = androidCodec->getInfo();
 *                         const auto unscaledSize = SkEncodedOriginSwapsWidthHeight(origin)
 *                                 ? SkISize{ info.height(), info.width() } :  info.dimensions();
 *
 *                         SkISize scaledSize = { SkScalarFloorToInt(unscaledSize.width()  * scale) ,
 *                                                SkScalarFloorToInt(unscaledSize.height() * scale) };
 *                         info = info.makeDimensions(scaledSize);
 *
 *                         auto cropRect = SkIRect::MakeSize(scaledSize);
 *                         if (doCrop) {
 *                             auto matrix = SkMatrix::RectToRectOrIdentity(SkRect::Make(unscaledSize),
 *                                                                          SkRect::Make(scaledSize));
 *                             matrix.preConcat(SkEncodedOriginToMatrix(origin,
 *                                     unscaledSize.width(), unscaledSize.height()));
 *                             SkRect cropRectFloat = SkRect::Make(fCropRect);
 *                             matrix.mapRect(&cropRectFloat);
 *                             cropRectFloat.roundOut(&cropRect);
 *                         }
 *
 *                         sk_sp<SkPicture> postProcessor = doPostProcess
 *                                 ? post_processor(SkRect::Make(cropRect.size())) : nullptr;
 *                         auto animatedImage = SkAnimatedImage::Make(std::move(androidCodec),
 *                                 info, cropRect, std::move(postProcessor));
 *                         animatedImage->setRepetitionCount(0);
 *
 *                         for (int frame = 0; frame < kMaxFrames; frame++) {
 *                             {
 *                                 SkAutoCanvasRestore acr(canvas, doCrop);
 *                                 if (doCrop) {
 *                                     canvas->translate(cropRect.left(), cropRect.top());
 *                                 }
 *                                 drawProc(animatedImage);
 *                             }
 *
 *                             canvas->translate(fTranslate, 0);
 *                             const auto duration = animatedImage->currentFrameDuration();
 *                             if (duration == SkAnimatedImage::kFinished) {
 *                                 break;
 *                             }
 *                             for (int i = 0; i < fStep; i++) {
 *                                 animatedImage->decodeNextFrame();
 *                             }
 *                         }
 *                     }
 *                 }
 *                 canvas->restore();
 *                 canvas->translate(0, fTranslate);
 *             }
 *         }
 *     }
 * }
 * ```
 */
public open class AnimatedImageGM public constructor(
  path: String?,
  name: String?,
  step: Int,
  cropRect: SkIRect,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const char*   fPath
   * ```
   */
  private val fPath: String? = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * const char*   fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * const int     fStep
   * ```
   */
  private val fStep: Int = TODO("Initialize fStep")

  /**
   * C++ original:
   * ```cpp
   * const SkIRect fCropRect
   * ```
   */
  private val fCropRect: SkIRect = TODO("Initialize fCropRect")

  /**
   * C++ original:
   * ```cpp
   * SkISize       fSize
   * ```
   */
  private var fSize: SkISize = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * int           fTranslate
   * ```
   */
  private var fTranslate: Int = TODO("Initialize fTranslate")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fData
   * ```
   */
  private var fData: SkSp<SkData> = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * void init() {
   *         if (!fData) {
   *             fData = GetResourceAsData(fPath);
   *             auto codec = SkCodec::MakeFromData(fData);
   *             auto dimensions = codec->dimensions();
   *
   *             fTranslate = std::max(dimensions.width(), dimensions.height()) // may be rotated
   *                          * 1.25f    // will be scaled up
   *                          + 2;       // padding
   *
   *             fSize = { fTranslate * kMaxFrames
   *                                  * 2    // crop and no-crop
   *                                  * 2,   // post-process and no post-process
   *                       fTranslate * 4    // 4 scales
   *                                  * 2 }; // makePictureSnapshot and getCurrentFrame
   *         }
   *     }
   * ```
   */
  private fun `init`() {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkStringPrintf("%s_animated_image", fName); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         this->init();
   *         return fSize;
   *     }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         this->init();
   *         for (bool usePic : { true, false }) {
   *             auto drawProc = [canvas, usePic](const sk_sp<SkAnimatedImage>& animatedImage) {
   *                 if (usePic) {
   *                     sk_sp<SkPicture> pic = animatedImage->makePictureSnapshot();
   *                     canvas->drawPicture(pic);
   *                 } else {
   *                     auto image = animatedImage->getCurrentFrame();
   *                     canvas->drawImage(image, 0, 0);
   *                 }
   *             };
   *             for (float scale : { 1.25f, 1.0f, .75f, .5f }) {
   *                 canvas->save();
   *                 for (bool doCrop : { false, true }) {
   *                     for (bool doPostProcess : { false, true }) {
   *                         auto codec = SkCodec::MakeFromData(fData);
   *                         const auto origin = codec->getOrigin();
   *                         auto androidCodec = SkAndroidCodec::MakeFromCodec(std::move(codec));
   *                         auto info = androidCodec->getInfo();
   *                         const auto unscaledSize = SkEncodedOriginSwapsWidthHeight(origin)
   *                                 ? SkISize{ info.height(), info.width() } :  info.dimensions();
   *
   *                         SkISize scaledSize = { SkScalarFloorToInt(unscaledSize.width()  * scale) ,
   *                                                SkScalarFloorToInt(unscaledSize.height() * scale) };
   *                         info = info.makeDimensions(scaledSize);
   *
   *                         auto cropRect = SkIRect::MakeSize(scaledSize);
   *                         if (doCrop) {
   *                             auto matrix = SkMatrix::RectToRectOrIdentity(SkRect::Make(unscaledSize),
   *                                                                          SkRect::Make(scaledSize));
   *                             matrix.preConcat(SkEncodedOriginToMatrix(origin,
   *                                     unscaledSize.width(), unscaledSize.height()));
   *                             SkRect cropRectFloat = SkRect::Make(fCropRect);
   *                             matrix.mapRect(&cropRectFloat);
   *                             cropRectFloat.roundOut(&cropRect);
   *                         }
   *
   *                         sk_sp<SkPicture> postProcessor = doPostProcess
   *                                 ? post_processor(SkRect::Make(cropRect.size())) : nullptr;
   *                         auto animatedImage = SkAnimatedImage::Make(std::move(androidCodec),
   *                                 info, cropRect, std::move(postProcessor));
   *                         animatedImage->setRepetitionCount(0);
   *
   *                         for (int frame = 0; frame < kMaxFrames; frame++) {
   *                             {
   *                                 SkAutoCanvasRestore acr(canvas, doCrop);
   *                                 if (doCrop) {
   *                                     canvas->translate(cropRect.left(), cropRect.top());
   *                                 }
   *                                 drawProc(animatedImage);
   *                             }
   *
   *                             canvas->translate(fTranslate, 0);
   *                             const auto duration = animatedImage->currentFrameDuration();
   *                             if (duration == SkAnimatedImage::kFinished) {
   *                                 break;
   *                             }
   *                             for (int i = 0; i < fStep; i++) {
   *                                 animatedImage->decodeNextFrame();
   *                             }
   *                         }
   *                     }
   *                 }
   *                 canvas->restore();
   *                 canvas->translate(0, fTranslate);
   *             }
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kMaxFrames: Int = TODO("Initialize kMaxFrames")
  }
}
