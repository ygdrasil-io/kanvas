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
 * class ImagePictGM : public skiagm::GM {
 *     sk_sp<SkPicture> fPicture;
 *     sk_sp<SkImage>   fImage0;
 *     sk_sp<SkImage>   fImage1;
 * public:
 *     ImagePictGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("image-picture"); }
 *
 *     SkISize getISize() override { return SkISize::Make(850, 450); }
 *
 *     void onOnceBeforeDraw() override {
 *         const SkRect bounds = SkRect::MakeXYWH(100, 100, 100, 100);
 *         SkPictureRecorder recorder;
 *         draw_something(recorder.beginRecording(bounds), bounds);
 *         fPicture = recorder.finishRecordingAsPicture();
 *
 *         // extract enough just for the oval.
 *         const SkISize size = SkISize::Make(100, 100);
 *         auto srgbColorSpace = SkColorSpace::MakeSRGB();
 *
 *         SkMatrix matrix;
 *         matrix.setTranslate(-100, -100);
 *         fImage0 = SkImages::DeferredFromPicture(
 *                 fPicture, size, &matrix, nullptr, SkImages::BitDepth::kU8, srgbColorSpace);
 *         matrix.postTranslate(-50, -50);
 *         matrix.postRotate(45);
 *         matrix.postTranslate(50, 50);
 *         fImage1 = SkImages::DeferredFromPicture(
 *                 fPicture, size, &matrix, nullptr, SkImages::BitDepth::kU8, srgbColorSpace);
 *     }
 *
 *     void drawSet(SkCanvas* canvas) const {
 *         SkMatrix matrix = SkMatrix::Translate(-100, -100);
 *         canvas->drawPicture(fPicture, &matrix, nullptr);
 *         canvas->drawImage(fImage0.get(), 150, 0);
 *         canvas->drawImage(fImage1.get(), 300, 0);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(20, 20);
 *
 *         this->drawSet(canvas);
 *
 *         canvas->save();
 *         canvas->translate(0, 130);
 *         canvas->scale(0.25f, 0.25f);
 *         this->drawSet(canvas);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(0, 200);
 *         canvas->scale(2, 2);
 *         this->drawSet(canvas);
 *         canvas->restore();
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ImagePictGM public constructor() : GM() {
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
   * sk_sp<SkImage>   fImage0
   * ```
   */
  private var fImage0: SkSp<SkImage> = TODO("Initialize fImage0")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>   fImage1
   * ```
   */
  private var fImage1: SkSp<SkImage> = TODO("Initialize fImage1")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("image-picture"); }
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
   *         const SkRect bounds = SkRect::MakeXYWH(100, 100, 100, 100);
   *         SkPictureRecorder recorder;
   *         draw_something(recorder.beginRecording(bounds), bounds);
   *         fPicture = recorder.finishRecordingAsPicture();
   *
   *         // extract enough just for the oval.
   *         const SkISize size = SkISize::Make(100, 100);
   *         auto srgbColorSpace = SkColorSpace::MakeSRGB();
   *
   *         SkMatrix matrix;
   *         matrix.setTranslate(-100, -100);
   *         fImage0 = SkImages::DeferredFromPicture(
   *                 fPicture, size, &matrix, nullptr, SkImages::BitDepth::kU8, srgbColorSpace);
   *         matrix.postTranslate(-50, -50);
   *         matrix.postRotate(45);
   *         matrix.postTranslate(50, 50);
   *         fImage1 = SkImages::DeferredFromPicture(
   *                 fPicture, size, &matrix, nullptr, SkImages::BitDepth::kU8, srgbColorSpace);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawSet(SkCanvas* canvas) const {
   *         SkMatrix matrix = SkMatrix::Translate(-100, -100);
   *         canvas->drawPicture(fPicture, &matrix, nullptr);
   *         canvas->drawImage(fImage0.get(), 150, 0);
   *         canvas->drawImage(fImage1.get(), 300, 0);
   *     }
   * ```
   */
  protected fun drawSet(canvas: SkCanvas?) {
    TODO("Implement drawSet")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(20, 20);
   *
   *         this->drawSet(canvas);
   *
   *         canvas->save();
   *         canvas->translate(0, 130);
   *         canvas->scale(0.25f, 0.25f);
   *         this->drawSet(canvas);
   *         canvas->restore();
   *
   *         canvas->save();
   *         canvas->translate(0, 200);
   *         canvas->scale(2, 2);
   *         this->drawSet(canvas);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
