package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class AnimatedGifGM : public skiagm::GM {
 * private:
 *     std::unique_ptr<SkCodec>        fCodec;
 *     int                             fFrame;
 *     double                          fNextUpdate;
 *     int                             fTotalFrames;
 *     std::vector<SkCodec::FrameInfo> fFrameInfos;
 *     std::vector<SkBitmap>           fFrames;
 *
 *     void drawFrame(SkCanvas* canvas, int frameIndex) {
 *         // FIXME: Create from an Image/ImageGenerator?
 *         if (frameIndex >= (int) fFrames.size()) {
 *             fFrames.resize(frameIndex + 1);
 *         }
 *         SkBitmap& bm = fFrames[frameIndex];
 *         if (!bm.getPixels()) {
 *             const SkImageInfo info = fCodec->getInfo().makeColorType(kN32_SkColorType);
 *             bm.allocPixels(info);
 *
 *             SkCodec::Options opts;
 *             opts.fFrameIndex = frameIndex;
 *             const int requiredFrame = fFrameInfos[frameIndex].fRequiredFrame;
 *             if (requiredFrame != SkCodec::kNoFrame) {
 *                 SkASSERT(requiredFrame >= 0
 *                          && static_cast<size_t>(requiredFrame) < fFrames.size());
 *                 SkBitmap& requiredBitmap = fFrames[requiredFrame];
 *                 // For simplicity, do not try to cache old frames
 *                 if (requiredBitmap.getPixels() &&
 *                     ToolUtils::copy_to(&bm, requiredBitmap.colorType(), requiredBitmap)) {
 *                     opts.fPriorFrame = requiredFrame;
 *                 }
 *             }
 *
 *             if (SkCodec::kSuccess != fCodec->getPixels(info, bm.getPixels(),
 *                                                        bm.rowBytes(), &opts)) {
 *                 SkDebugf("Could not getPixels for frame %i: %s", frameIndex, FLAGS_animatedGif[0]);
 *                 return;
 *             }
 *         }
 *
 *         canvas->drawImage(bm.asImage(), 0, 0);
 *     }
 *
 * public:
 *     AnimatedGifGM()
 *     : fFrame(0)
 *     , fNextUpdate (-1)
 *     , fTotalFrames (-1) {}
 *
 * private:
 *     SkString getName() const override { return SkString("animatedGif"); }
 *
 *     SkISize getISize() override {
 *         if (this->initCodec()) {
 *             SkISize dim = fCodec->getInfo().dimensions();
 *             // Wide enough to display all the frames.
 *             dim.fWidth *= fTotalFrames;
 *             // Tall enough to show the row of frames plus an animating version.
 *             dim.fHeight *= 2;
 *             return dim;
 *         }
 *         return SkISize::Make(640, 480);
 *     }
 *
 *     bool initCodec() {
 *         if (fCodec) {
 *             return true;
 *         }
 *         if (FLAGS_animatedGif.isEmpty()) {
 *             SkDebugf("Nothing specified for --animatedGif!");
 *             return false;
 *         }
 *
 *         std::unique_ptr<SkStream> stream(GetResourceAsStream(FLAGS_animatedGif[0]));
 *         if (!stream) {
 *             return false;
 *         }
 *
 *         fCodec = SkCodec::MakeFromStream(std::move(stream));
 *         if (!fCodec) {
 *             return false;
 *         }
 *
 *         fFrame = 0;
 *         fFrameInfos = fCodec->getFrameInfo();
 *         fTotalFrames = fFrameInfos.size();
 *         return true;
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!this->initCodec()) {
 *             errorMsg->printf("Could not create codec from %s", FLAGS_animatedGif[0]);
 *             return DrawResult::kFail;
 *         }
 *
 *         canvas->save();
 *         for (int frameIndex = 0; frameIndex < fTotalFrames; frameIndex++) {
 *             this->drawFrame(canvas, frameIndex);
 *             canvas->translate(SkIntToScalar(fCodec->getInfo().width()), 0);
 *         }
 *         canvas->restore();
 *
 *         SkAutoCanvasRestore acr(canvas, true);
 *         canvas->translate(0, SkIntToScalar(fCodec->getInfo().height()));
 *         this->drawFrame(canvas, fFrame);
 *         return DrawResult::kOk;
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         if (!fCodec || fTotalFrames == 1) {
 *             return false;
 *         }
 *
 *         double secs = TimeUtils::NanosToMSec(nanos) * .1;
 *         if (fNextUpdate < double(0)) {
 *             // This is a sentinel that we have not done any updates yet.
 *             // I'm assuming this gets called *after* onOnceBeforeDraw, so our first frame should
 *             // already have been retrieved.
 *             SkASSERT(fFrame == 0);
 *             fNextUpdate = secs + fFrameInfos[fFrame].fDuration;
 *
 *             return true;
 *         }
 *
 *         if (secs < fNextUpdate) {
 *             return true;
 *         }
 *
 *         while (secs >= fNextUpdate) {
 *             // Retrieve the next frame.
 *             fFrame++;
 *             if (fFrame == fTotalFrames) {
 *                 fFrame = 0;
 *             }
 *
 *             // Note that we loop here. This is not safe if we need to draw the intermediate frame
 *             // in order to draw correctly.
 *             fNextUpdate += fFrameInfos[fFrame].fDuration;
 *         }
 *
 *         return true;
 *     }
 * }
 * ```
 */
public open class AnimatedGifGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkCodec>        fCodec
   * ```
   */
  private var fCodec: Int = TODO("Initialize fCodec")

  /**
   * C++ original:
   * ```cpp
   * int                             fFrame
   * ```
   */
  private var fFrame: Int = TODO("Initialize fFrame")

  /**
   * C++ original:
   * ```cpp
   * double                          fNextUpdate
   * ```
   */
  private var fNextUpdate: Double = TODO("Initialize fNextUpdate")

  /**
   * C++ original:
   * ```cpp
   * int                             fTotalFrames
   * ```
   */
  private var fTotalFrames: Int = TODO("Initialize fTotalFrames")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkCodec::FrameInfo> fFrameInfos
   * ```
   */
  private var fFrameInfos: Int = TODO("Initialize fFrameInfos")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkBitmap>           fFrames
   * ```
   */
  private var fFrames: Int = TODO("Initialize fFrames")

  /**
   * C++ original:
   * ```cpp
   * void drawFrame(SkCanvas* canvas, int frameIndex) {
   *         // FIXME: Create from an Image/ImageGenerator?
   *         if (frameIndex >= (int) fFrames.size()) {
   *             fFrames.resize(frameIndex + 1);
   *         }
   *         SkBitmap& bm = fFrames[frameIndex];
   *         if (!bm.getPixels()) {
   *             const SkImageInfo info = fCodec->getInfo().makeColorType(kN32_SkColorType);
   *             bm.allocPixels(info);
   *
   *             SkCodec::Options opts;
   *             opts.fFrameIndex = frameIndex;
   *             const int requiredFrame = fFrameInfos[frameIndex].fRequiredFrame;
   *             if (requiredFrame != SkCodec::kNoFrame) {
   *                 SkASSERT(requiredFrame >= 0
   *                          && static_cast<size_t>(requiredFrame) < fFrames.size());
   *                 SkBitmap& requiredBitmap = fFrames[requiredFrame];
   *                 // For simplicity, do not try to cache old frames
   *                 if (requiredBitmap.getPixels() &&
   *                     ToolUtils::copy_to(&bm, requiredBitmap.colorType(), requiredBitmap)) {
   *                     opts.fPriorFrame = requiredFrame;
   *                 }
   *             }
   *
   *             if (SkCodec::kSuccess != fCodec->getPixels(info, bm.getPixels(),
   *                                                        bm.rowBytes(), &opts)) {
   *                 SkDebugf("Could not getPixels for frame %i: %s", frameIndex, FLAGS_animatedGif[0]);
   *                 return;
   *             }
   *         }
   *
   *         canvas->drawImage(bm.asImage(), 0, 0);
   *     }
   * ```
   */
  private fun drawFrame(canvas: SkCanvas?, frameIndex: Int) {
    TODO("Implement drawFrame")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("animatedGif"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         if (this->initCodec()) {
   *             SkISize dim = fCodec->getInfo().dimensions();
   *             // Wide enough to display all the frames.
   *             dim.fWidth *= fTotalFrames;
   *             // Tall enough to show the row of frames plus an animating version.
   *             dim.fHeight *= 2;
   *             return dim;
   *         }
   *         return SkISize::Make(640, 480);
   *     }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool initCodec() {
   *         if (fCodec) {
   *             return true;
   *         }
   *         if (FLAGS_animatedGif.isEmpty()) {
   *             SkDebugf("Nothing specified for --animatedGif!");
   *             return false;
   *         }
   *
   *         std::unique_ptr<SkStream> stream(GetResourceAsStream(FLAGS_animatedGif[0]));
   *         if (!stream) {
   *             return false;
   *         }
   *
   *         fCodec = SkCodec::MakeFromStream(std::move(stream));
   *         if (!fCodec) {
   *             return false;
   *         }
   *
   *         fFrame = 0;
   *         fFrameInfos = fCodec->getFrameInfo();
   *         fTotalFrames = fFrameInfos.size();
   *         return true;
   *     }
   * ```
   */
  private fun initCodec(): Boolean {
    TODO("Implement initCodec")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (!this->initCodec()) {
   *             errorMsg->printf("Could not create codec from %s", FLAGS_animatedGif[0]);
   *             return DrawResult::kFail;
   *         }
   *
   *         canvas->save();
   *         for (int frameIndex = 0; frameIndex < fTotalFrames; frameIndex++) {
   *             this->drawFrame(canvas, frameIndex);
   *             canvas->translate(SkIntToScalar(fCodec->getInfo().width()), 0);
   *         }
   *         canvas->restore();
   *
   *         SkAutoCanvasRestore acr(canvas, true);
   *         canvas->translate(0, SkIntToScalar(fCodec->getInfo().height()));
   *         this->drawFrame(canvas, fFrame);
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         if (!fCodec || fTotalFrames == 1) {
   *             return false;
   *         }
   *
   *         double secs = TimeUtils::NanosToMSec(nanos) * .1;
   *         if (fNextUpdate < double(0)) {
   *             // This is a sentinel that we have not done any updates yet.
   *             // I'm assuming this gets called *after* onOnceBeforeDraw, so our first frame should
   *             // already have been retrieved.
   *             SkASSERT(fFrame == 0);
   *             fNextUpdate = secs + fFrameInfos[fFrame].fDuration;
   *
   *             return true;
   *         }
   *
   *         if (secs < fNextUpdate) {
   *             return true;
   *         }
   *
   *         while (secs >= fNextUpdate) {
   *             // Retrieve the next frame.
   *             fFrame++;
   *             if (fFrame == fTotalFrames) {
   *                 fFrame = 0;
   *             }
   *
   *             // Note that we loop here. This is not safe if we need to draw the intermediate frame
   *             // in order to draw correctly.
   *             fNextUpdate += fFrameInfos[fFrame].fDuration;
   *         }
   *
   *         return true;
   *     }
   * ```
   */
  public override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
