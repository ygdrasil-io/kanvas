package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ArcOfZorroGM : public GM {
 * public:
 *     ArcOfZorroGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("arcofzorro"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1000, 1000); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRandom rand;
 *
 *         SkRect rect = SkRect::MakeXYWH(10, 10, 200, 200);
 *
 *         SkPaint p;
 *
 *         p.setStyle(SkPaint::kStroke_Style);
 *         p.setStrokeWidth(35);
 *         int xOffset = 0, yOffset = 0;
 *         int direction = 0;
 *
 *         for (float arc = 134.0f; arc < 136.0f; arc += 0.01f) {
 *             SkColor color = rand.nextU();
 *             color |= 0xff000000;
 *             p.setColor(color);
 *
 *             canvas->save();
 *             canvas->translate(SkIntToScalar(xOffset), SkIntToScalar(yOffset));
 *             canvas->drawArc(rect, 0, arc, false, p);
 *             canvas->restore();
 *
 *             switch (direction) {
 *             case 0:
 *                 xOffset += 10;
 *                 if (xOffset >= 700) {
 *                     direction = 1;
 *                 }
 *                 break;
 *             case 1:
 *                 xOffset -= 10;
 *                 yOffset += 10;
 *                 if (xOffset < 50) {
 *                     direction = 2;
 *                 }
 *                 break;
 *             case 2:
 *                 xOffset += 10;
 *                 break;
 *             }
 *         }
 *
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ArcOfZorroGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("arcofzorro"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1000, 1000); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkRandom rand;
   *
   *         SkRect rect = SkRect::MakeXYWH(10, 10, 200, 200);
   *
   *         SkPaint p;
   *
   *         p.setStyle(SkPaint::kStroke_Style);
   *         p.setStrokeWidth(35);
   *         int xOffset = 0, yOffset = 0;
   *         int direction = 0;
   *
   *         for (float arc = 134.0f; arc < 136.0f; arc += 0.01f) {
   *             SkColor color = rand.nextU();
   *             color |= 0xff000000;
   *             p.setColor(color);
   *
   *             canvas->save();
   *             canvas->translate(SkIntToScalar(xOffset), SkIntToScalar(yOffset));
   *             canvas->drawArc(rect, 0, arc, false, p);
   *             canvas->restore();
   *
   *             switch (direction) {
   *             case 0:
   *                 xOffset += 10;
   *                 if (xOffset >= 700) {
   *                     direction = 1;
   *                 }
   *                 break;
   *             case 1:
   *                 xOffset -= 10;
   *                 yOffset += 10;
   *                 if (xOffset < 50) {
   *                     direction = 2;
   *                 }
   *                 break;
   *             case 2:
   *                 xOffset += 10;
   *                 break;
   *             }
   *         }
   *
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
