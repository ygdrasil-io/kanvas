package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SpriteBitmapGM : public skiagm::GM {
 * public:
 *     SpriteBitmapGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("spritebitmap"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkBitmap bm;
 *         make_bm(&bm);
 *
 *         int dx = 10;
 *         int dy = 10;
 *
 *         SkScalar sigma = 8;
 *         sk_sp<SkImageFilter> filter(SkImageFilters::Blur(sigma, sigma, nullptr));
 *
 *         draw_1_bitmap(canvas, bm, false, dx, dy, nullptr);
 *         dy += bm.height() + 20;
 *         draw_1_bitmap(canvas, bm, false, dx, dy, filter);
 *         dy += bm.height() + 20;
 *         draw_1_bitmap(canvas, bm, true, dx, dy, nullptr);
 *         dy += bm.height() + 20;
 *         draw_1_bitmap(canvas, bm, true, dx, dy, filter);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class SpriteBitmapGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("spritebitmap"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkBitmap bm;
   *         make_bm(&bm);
   *
   *         int dx = 10;
   *         int dy = 10;
   *
   *         SkScalar sigma = 8;
   *         sk_sp<SkImageFilter> filter(SkImageFilters::Blur(sigma, sigma, nullptr));
   *
   *         draw_1_bitmap(canvas, bm, false, dx, dy, nullptr);
   *         dy += bm.height() + 20;
   *         draw_1_bitmap(canvas, bm, false, dx, dy, filter);
   *         dy += bm.height() + 20;
   *         draw_1_bitmap(canvas, bm, true, dx, dy, nullptr);
   *         dy += bm.height() + 20;
   *         draw_1_bitmap(canvas, bm, true, dx, dy, filter);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
