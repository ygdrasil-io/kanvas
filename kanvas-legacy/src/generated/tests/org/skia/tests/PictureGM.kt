package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PictureGM : public skiagm::GM {
 * public:
 *     PictureGM()
 *         : fPicture(nullptr)
 *     {}
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *          fPicture = make_picture();
 *     }
 *
 *     SkString getName() const override { return SkString("pictures"); }
 *
 *     SkISize getISize() override { return SkISize::Make(450, 120); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(10, 10);
 *
 *         SkMatrix matrix;
 *         SkPaint paint;
 *
 *         canvas->drawPicture(fPicture);
 *
 *         matrix.setTranslate(110, 0);
 *         canvas->drawPicture(fPicture, &matrix, nullptr);
 *
 *         matrix.postTranslate(110, 0);
 *         canvas->drawPicture(fPicture, &matrix, &paint);
 *
 *         paint.setAlphaf(0.5f);
 *         matrix.postTranslate(110, 0);
 *         canvas->drawPicture(fPicture, &matrix, &paint);
 *     }
 *
 * private:
 *     sk_sp<SkPicture> fPicture;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class PictureGM public constructor() : GM() {
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
   * void onOnceBeforeDraw() override {
   *          fPicture = make_picture();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pictures"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(450, 120); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(10, 10);
   *
   *         SkMatrix matrix;
   *         SkPaint paint;
   *
   *         canvas->drawPicture(fPicture);
   *
   *         matrix.setTranslate(110, 0);
   *         canvas->drawPicture(fPicture, &matrix, nullptr);
   *
   *         matrix.postTranslate(110, 0);
   *         canvas->drawPicture(fPicture, &matrix, &paint);
   *
   *         paint.setAlphaf(0.5f);
   *         matrix.postTranslate(110, 0);
   *         canvas->drawPicture(fPicture, &matrix, &paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
