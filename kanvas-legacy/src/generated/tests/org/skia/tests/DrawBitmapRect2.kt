package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawBitmapRect2 : public skiagm::GM {
 *     bool fUseIRect;
 * public:
 *     DrawBitmapRect2(bool useIRect) : fUseIRect(useIRect) {
 *     }
 *
 * protected:
 *     SkString getName() const override {
 *         SkString str;
 *         str.printf("bitmaprect_%s", fUseIRect ? "i" : "s");
 *         return str;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(0xFFCCCCCC);
 *
 *         const SkIRect src[] = {
 *             { 0, 0, 32, 32 },
 *             { 0, 0, 80, 80 },
 *             { 32, 32, 96, 96 },
 *             { -32, -32, 32, 32, }
 *         };
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         auto sampling = SkSamplingOptions();
 *
 *         auto image = make_image(canvas);
 *
 *         SkRect dstR = { 0, 200, 128, 380 };
 *
 *         canvas->translate(16, 40);
 *         for (size_t i = 0; i < std::size(src); i++) {
 *             SkRect srcR;
 *             srcR.set(src[i]);
 *
 *             canvas->drawImage(image, 0, 0, sampling, &paint);
 *             if (!fUseIRect) {
 *                 canvas->drawImageRect(image.get(), srcR, dstR, sampling, &paint,
 *                                       SkCanvas::kStrict_SrcRectConstraint);
 *             } else {
 *                 canvas->drawImageRect(image.get(), SkRect::Make(src[i]), dstR, sampling, &paint,
 *                                       SkCanvas::kStrict_SrcRectConstraint);
 *             }
 *
 *             canvas->drawRect(dstR, paint);
 *             canvas->drawRect(srcR, paint);
 *
 *             canvas->translate(160, 0);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawBitmapRect2 public constructor(
  useIRect: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fUseIRect
   * ```
   */
  private var fUseIRect: Boolean = TODO("Initialize fUseIRect")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString str;
   *         str.printf("bitmaprect_%s", fUseIRect ? "i" : "s");
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
   *         canvas->drawColor(0xFFCCCCCC);
   *
   *         const SkIRect src[] = {
   *             { 0, 0, 32, 32 },
   *             { 0, 0, 80, 80 },
   *             { 32, 32, 96, 96 },
   *             { -32, -32, 32, 32, }
   *         };
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         auto sampling = SkSamplingOptions();
   *
   *         auto image = make_image(canvas);
   *
   *         SkRect dstR = { 0, 200, 128, 380 };
   *
   *         canvas->translate(16, 40);
   *         for (size_t i = 0; i < std::size(src); i++) {
   *             SkRect srcR;
   *             srcR.set(src[i]);
   *
   *             canvas->drawImage(image, 0, 0, sampling, &paint);
   *             if (!fUseIRect) {
   *                 canvas->drawImageRect(image.get(), srcR, dstR, sampling, &paint,
   *                                       SkCanvas::kStrict_SrcRectConstraint);
   *             } else {
   *                 canvas->drawImageRect(image.get(), SkRect::Make(src[i]), dstR, sampling, &paint,
   *                                       SkCanvas::kStrict_SrcRectConstraint);
   *             }
   *
   *             canvas->drawRect(dstR, paint);
   *             canvas->drawRect(srcR, paint);
   *
   *             canvas->translate(160, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
