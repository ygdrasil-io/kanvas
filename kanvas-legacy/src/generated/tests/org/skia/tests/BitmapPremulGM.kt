package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BitmapPremulGM : public GM {
 * public:
 *     BitmapPremulGM() {
 *         this->setBGColor(SK_ColorWHITE);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("bitmap_premul"); }
 *
 *     SkISize getISize() override { return SkISize::Make(SLIDE_SIZE * 2, SLIDE_SIZE * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar slideSize = SkIntToScalar(SLIDE_SIZE);
 *         canvas->drawImage(make_argb8888_gradient(), 0, 0);
 *         canvas->drawImage(make_argb4444_gradient(), slideSize, 0);
 *         canvas->drawImage(make_argb8888_stripes(), 0, slideSize);
 *         canvas->drawImage(make_argb4444_stripes(), slideSize, slideSize);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BitmapPremulGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bitmap_premul"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(SLIDE_SIZE * 2, SLIDE_SIZE * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar slideSize = SkIntToScalar(SLIDE_SIZE);
   *         canvas->drawImage(make_argb8888_gradient(), 0, 0);
   *         canvas->drawImage(make_argb4444_gradient(), slideSize, 0);
   *         canvas->drawImage(make_argb8888_stripes(), 0, slideSize);
   *         canvas->drawImage(make_argb4444_stripes(), slideSize, slideSize);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
