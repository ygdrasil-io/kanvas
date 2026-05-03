package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageShaderGM : public skiagm::GM {
 *     sk_sp<SkPicture> fPicture;
 *
 * public:
 *     ImageShaderGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("image-shader"); }
 *
 *     SkISize getISize() override { return SkISize::Make(850, 450); }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkRect bounds = SkRect::MakeWH(100, 100);
 *         SkPictureRecorder recorder;
 *         draw_something(recorder.beginRecording(bounds), bounds);
 *         fPicture = recorder.finishRecordingAsPicture();
 *     }
 *
 *     void testImage(SkCanvas* canvas, SkImage* image) {
 *         SkAutoCanvasRestore acr(canvas, true);
 *
 *         canvas->drawImage(image, 0, 0);
 *         canvas->translate(0, 120);
 *
 *         const SkTileMode tile = SkTileMode::kRepeat;
 *         const SkMatrix localM = SkMatrix::Translate(-50, -50);
 *         SkPaint paint;
 *         paint.setShader(image->makeShader(tile, tile, SkSamplingOptions(), &localM));
 *         paint.setAntiAlias(true);
 *         canvas->drawCircle(50, 50, 50, paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(20, 20);
 *
 *         const SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
 *
 *         for (size_t i = 0; i < std::size(gProcs); ++i) {
 *             sk_sp<SkImage> image(gProcs[i](canvas->recordingContext(), fPicture.get(), info));
 *             if (image) {
 *                 this->testImage(canvas, image.get());
 *             }
 *             canvas->translate(120, 0);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ImageShaderGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("image-shader"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(850, 450); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const SkRect bounds = SkRect::MakeWH(100, 100);
   *         SkPictureRecorder recorder;
   *         draw_something(recorder.beginRecording(bounds), bounds);
   *         fPicture = recorder.finishRecordingAsPicture();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void testImage(SkCanvas* canvas, SkImage* image) {
   *         SkAutoCanvasRestore acr(canvas, true);
   *
   *         canvas->drawImage(image, 0, 0);
   *         canvas->translate(0, 120);
   *
   *         const SkTileMode tile = SkTileMode::kRepeat;
   *         const SkMatrix localM = SkMatrix::Translate(-50, -50);
   *         SkPaint paint;
   *         paint.setShader(image->makeShader(tile, tile, SkSamplingOptions(), &localM));
   *         paint.setAntiAlias(true);
   *         canvas->drawCircle(50, 50, 50, paint);
   *     }
   * ```
   */
  protected fun testImage(canvas: SkCanvas?, image: SkImage?) {
    TODO("Implement testImage")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(20, 20);
   *
   *         const SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
   *
   *         for (size_t i = 0; i < std::size(gProcs); ++i) {
   *             sk_sp<SkImage> image(gProcs[i](canvas->recordingContext(), fPicture.get(), info));
   *             if (image) {
   *                 this->testImage(canvas, image.get());
   *             }
   *             canvas->translate(120, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
