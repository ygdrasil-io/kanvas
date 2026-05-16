package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class FilterGM : public skiagm::GM {
 *     void onOnceBeforeDraw() override {
 *         SkBitmap bm32, bm4444, bm565;
 *         make_bm(&bm32);
 *         ToolUtils::copy_to(&bm4444, kARGB_4444_SkColorType, bm32);
 *         ToolUtils::copy_to(&bm565, kRGB_565_SkColorType, bm32);
 *
 *         fImg32 = bm32.asImage();
 *         fImg4444 = bm4444.asImage();
 *         fImg565 = bm565.asImage();
 *     }
 *
 * public:
 *     sk_sp<SkImage> fImg32, fImg4444, fImg565;
 *
 *     FilterGM() {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("bitmapfilters"); }
 *
 *     SkISize getISize() override { return SkISize::Make(540, 250); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar x = SkIntToScalar(10);
 *         SkScalar y = SkIntToScalar(10);
 *
 *         canvas->translate(x, y);
 *         y = draw_row(canvas, fImg4444);
 *         canvas->translate(0, y);
 *         y = draw_row(canvas, fImg565);
 *         canvas->translate(0, y);
 *         draw_row(canvas, fImg32);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class FilterGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImg32
   * ```
   */
  public var fImg32: SkSp<SkImage> = TODO("Initialize fImg32")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImg32, fImg4444
   * ```
   */
  public var fImg4444: SkSp<SkImage> = TODO("Initialize fImg4444")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImg32, fImg4444, fImg565
   * ```
   */
  public var fImg565: SkSp<SkImage> = TODO("Initialize fImg565")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkBitmap bm32, bm4444, bm565;
   *         make_bm(&bm32);
   *         ToolUtils::copy_to(&bm4444, kARGB_4444_SkColorType, bm32);
   *         ToolUtils::copy_to(&bm565, kRGB_565_SkColorType, bm32);
   *
   *         fImg32 = bm32.asImage();
   *         fImg4444 = bm4444.asImage();
   *         fImg565 = bm565.asImage();
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bitmapfilters"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(540, 250); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar x = SkIntToScalar(10);
   *         SkScalar y = SkIntToScalar(10);
   *
   *         canvas->translate(x, y);
   *         y = draw_row(canvas, fImg4444);
   *         canvas->translate(0, y);
   *         y = draw_row(canvas, fImg565);
   *         canvas->translate(0, y);
   *         draw_row(canvas, fImg32);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
