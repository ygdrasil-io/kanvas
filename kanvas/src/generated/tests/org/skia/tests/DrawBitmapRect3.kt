package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawBitmapRect3 : public skiagm::GM {
 * public:
 *     DrawBitmapRect3() {
 *         this->setBGColor(SK_ColorBLACK);
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("3x3bitmaprect");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkBitmap bitmap;
 *         make_3x3_bitmap(&bitmap);
 *
 *         SkRect srcR = { 0.5f, 0.5f, 2.5f, 2.5f };
 *         SkRect dstR = { 100, 100, 300, 200 };
 *
 *         canvas->drawImageRect(ToolUtils::MakeTextureImage(canvas, bitmap.asImage()),
 *                               srcR, dstR, SkSamplingOptions(),
 *                               nullptr, SkCanvas::kStrict_SrcRectConstraint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawBitmapRect3 public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("3x3bitmaprect");
   *         return str;
   *     }
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
   *
   *         SkBitmap bitmap;
   *         make_3x3_bitmap(&bitmap);
   *
   *         SkRect srcR = { 0.5f, 0.5f, 2.5f, 2.5f };
   *         SkRect dstR = { 100, 100, 300, 200 };
   *
   *         canvas->drawImageRect(ToolUtils::MakeTextureImage(canvas, bitmap.asImage()),
   *                               srcR, dstR, SkSamplingOptions(),
   *                               nullptr, SkCanvas::kStrict_SrcRectConstraint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
