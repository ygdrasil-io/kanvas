package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SimpleRectGM : public skiagm::GM {
 * public:
 *     SimpleRectGM() {}
 *
 * protected:
 *     SkString getName() const override {
 *         SkString name;
 *         name.printf("simplerect");
 *         return name;
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 800); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(1, 1);    // want to exercise non-identity ctm performance
 *
 *         const SkScalar min = -20;
 *         const SkScalar max = 800;
 *         const SkScalar size = 20;
 *
 *         SkRandom rand;
 *         SkPaint paint;
 *         for (int i = 0; i < 10000; i++) {
 *             paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
 *             SkScalar x = rand.nextRangeScalar(min, max);
 *             SkScalar y = rand.nextRangeScalar(min, max);
 *             SkScalar w = rand.nextRangeScalar(0, size);
 *             SkScalar h = rand.nextRangeScalar(0, size);
 *             canvas->drawRect(SkRect::MakeXYWH(x, y, w, h), paint);
 *         }
 *     }
 *
 *     bool onAnimate(double nanos) override { return true; }
 *
 * private:
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class SimpleRectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         SkString name;
   *         name.printf("simplerect");
   *         return name;
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 800); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(1, 1);    // want to exercise non-identity ctm performance
   *
   *         const SkScalar min = -20;
   *         const SkScalar max = 800;
   *         const SkScalar size = 20;
   *
   *         SkRandom rand;
   *         SkPaint paint;
   *         for (int i = 0; i < 10000; i++) {
   *             paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
   *             SkScalar x = rand.nextRangeScalar(min, max);
   *             SkScalar y = rand.nextRangeScalar(min, max);
   *             SkScalar w = rand.nextRangeScalar(0, size);
   *             SkScalar h = rand.nextRangeScalar(0, size);
   *             canvas->drawRect(SkRect::MakeXYWH(x, y, w, h), paint);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override { return true; }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
