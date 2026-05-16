package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BitmapShaderGM : public GM {
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         this->setBGColor(SK_ColorGRAY);
 *         fImage = draw_bm();
 *         fMask = draw_mask();
 *     }
 *
 *     SkString getName() const override { return SkString("bitmapshaders"); }
 *
 *     SkISize getISize() override { return SkISize::Make(150, 100); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *
 *         for (int i = 0; i < 2; i++) {
 *             SkMatrix s;
 *             s.reset();
 *             if (1 == i) {
 *                 s.setScale(1.5f, 1.5f);
 *                 s.postTranslate(2, 2);
 *             }
 *
 *             canvas->save();
 *             paint.setShader(fImage->makeShader(SkSamplingOptions(), s));
 *
 *             // draw the shader with a bitmap mask
 *             canvas->drawImage(fMask, 0, 0,  SkSamplingOptions(), &paint);
 *             // no blue circle expected (the bitmap shader's coordinates are aligned to CTM still)
 *             canvas->drawImage(fMask, 30, 0, SkSamplingOptions(), &paint);
 *
 *             canvas->translate(0, 25);
 *
 *             canvas->drawCircle(10, 10, 10, paint);
 *             canvas->drawCircle(40, 10, 10, paint); // no blue circle expected
 *
 *             canvas->translate(0, 25);
 *
 *             // clear the shader, colorized by a solid color with a bitmap mask
 *             paint.setShader(nullptr);
 *             paint.setColor(SK_ColorGREEN);
 *             canvas->drawImage(fMask, 0, 0,  SkSamplingOptions(), &paint);
 *             canvas->drawImage(fMask, 30, 0, SkSamplingOptions(), &paint);
 *
 *             canvas->translate(0, 25);
 *
 *             paint.setShader(fMask->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                               SkSamplingOptions(), s));
 *             paint.setColor(SK_ColorRED);
 *
 *             // draw the mask using the shader and a color
 *             canvas->drawRect(SkRect::MakeXYWH(0, 0, 20, 20), paint);
 *             canvas->drawRect(SkRect::MakeXYWH(30, 0, 20, 20), paint);
 *             canvas->restore();
 *             canvas->translate(60, 0);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage> fImage, fMask;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BitmapShaderGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage, fMask
   * ```
   */
  private var fMask: SkSp<SkImage> = TODO("Initialize fMask")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         this->setBGColor(SK_ColorGRAY);
   *         fImage = draw_bm();
   *         fMask = draw_mask();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bitmapshaders"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(150, 100); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *
   *         for (int i = 0; i < 2; i++) {
   *             SkMatrix s;
   *             s.reset();
   *             if (1 == i) {
   *                 s.setScale(1.5f, 1.5f);
   *                 s.postTranslate(2, 2);
   *             }
   *
   *             canvas->save();
   *             paint.setShader(fImage->makeShader(SkSamplingOptions(), s));
   *
   *             // draw the shader with a bitmap mask
   *             canvas->drawImage(fMask, 0, 0,  SkSamplingOptions(), &paint);
   *             // no blue circle expected (the bitmap shader's coordinates are aligned to CTM still)
   *             canvas->drawImage(fMask, 30, 0, SkSamplingOptions(), &paint);
   *
   *             canvas->translate(0, 25);
   *
   *             canvas->drawCircle(10, 10, 10, paint);
   *             canvas->drawCircle(40, 10, 10, paint); // no blue circle expected
   *
   *             canvas->translate(0, 25);
   *
   *             // clear the shader, colorized by a solid color with a bitmap mask
   *             paint.setShader(nullptr);
   *             paint.setColor(SK_ColorGREEN);
   *             canvas->drawImage(fMask, 0, 0,  SkSamplingOptions(), &paint);
   *             canvas->drawImage(fMask, 30, 0, SkSamplingOptions(), &paint);
   *
   *             canvas->translate(0, 25);
   *
   *             paint.setShader(fMask->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
   *                                               SkSamplingOptions(), s));
   *             paint.setColor(SK_ColorRED);
   *
   *             // draw the mask using the shader and a color
   *             canvas->drawRect(SkRect::MakeXYWH(0, 0, 20, 20), paint);
   *             canvas->drawRect(SkRect::MakeXYWH(30, 0, 20, 20), paint);
   *             canvas->restore();
   *             canvas->translate(60, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
