package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BigTextGM : public skiagm::GM {
 * public:
 *     BigTextGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("bigtext"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 1500);
 *
 *         SkRect r;
 *         (void)font.measureText("/", 1, SkTextEncoding::kUTF8, &r);
 *         SkPoint pos = {
 *             this->width()/2 - r.centerX(),
 *             this->height()/2 - r.centerY()
 *         };
 *
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawSimpleText("/", 1, SkTextEncoding::kUTF8, pos.fX, pos.fY, font, paint);
 *
 *         paint.setColor(SK_ColorBLUE);
 *         canvas->drawSimpleText("\\", 1, SkTextEncoding::kUTF8, pos.fX, pos.fY, font, paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class BigTextGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("bigtext"); }
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
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         SkFont font(ToolUtils::DefaultPortableTypeface(), 1500);
   *
   *         SkRect r;
   *         (void)font.measureText("/", 1, SkTextEncoding::kUTF8, &r);
   *         SkPoint pos = {
   *             this->width()/2 - r.centerX(),
   *             this->height()/2 - r.centerY()
   *         };
   *
   *         paint.setColor(SK_ColorRED);
   *         canvas->drawSimpleText("/", 1, SkTextEncoding::kUTF8, pos.fX, pos.fY, font, paint);
   *
   *         paint.setColor(SK_ColorBLUE);
   *         canvas->drawSimpleText("\\", 1, SkTextEncoding::kUTF8, pos.fX, pos.fY, font, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
