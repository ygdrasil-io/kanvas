package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ManyRRectsGM : public GM {
 *     // This GM attempts to flood Ganesh with more rrects than will fit in a single index buffer
 *     // Stresses crbug.com/684112
 * public:
 *     ManyRRectsGM() {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("manyrrects"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 300); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRandom rand(1);
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setColor(SK_ColorBLUE);
 *         int total = 7000;
 *
 *         // Rectangle positioning variables
 *         int x = 0;
 *         int y = 0;
 *         const int kXLimit = 700;
 *         const int kYIncrement = 5;
 *         const int kXIncrement = 5;
 *
 *         SkRect rect = SkRect::MakeLTRB(0, 0, 4, 4);
 *         SkRRect rrect = SkRRect::MakeRectXY(rect, 1, 1);
 *         while (total--) {
 *             canvas->save();
 *             canvas->translate(x, y);
 *             canvas->drawRRect(rrect, paint);
 *             x += kXIncrement;
 *             if (x > kXLimit) {
 *                 x = 0;
 *                 y += kYIncrement;
 *             }
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ManyRRectsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("manyrrects"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 300); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRandom rand(1);
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setColor(SK_ColorBLUE);
   *         int total = 7000;
   *
   *         // Rectangle positioning variables
   *         int x = 0;
   *         int y = 0;
   *         const int kXLimit = 700;
   *         const int kYIncrement = 5;
   *         const int kXIncrement = 5;
   *
   *         SkRect rect = SkRect::MakeLTRB(0, 0, 4, 4);
   *         SkRRect rrect = SkRRect::MakeRectXY(rect, 1, 1);
   *         while (total--) {
   *             canvas->save();
   *             canvas->translate(x, y);
   *             canvas->drawRRect(rrect, paint);
   *             x += kXIncrement;
   *             if (x > kXLimit) {
   *                 x = 0;
   *                 y += kYIncrement;
   *             }
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
