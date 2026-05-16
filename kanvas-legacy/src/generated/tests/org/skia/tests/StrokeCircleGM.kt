package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class StrokeCircleGM : public skiagm::GM {
 * public:
 *     StrokeCircleGM() : fRotate(0) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("strokecircle"); }
 *
 *     SkISize getISize() override { return SkISize::Make(520, 520); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(20, 20);
 *         canvas->translate(13, 13);
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStroke(true);
 *         paint.setStrokeWidth(SK_Scalar1 / 2);
 *
 *         const SkScalar delta = paint.getStrokeWidth() * 3 / 2;
 *         SkRect r = SkRect::MakeXYWH(-12, -12, 24, 24);
 *         SkRandom rand;
 *
 *         SkScalar sign = 1;
 *         while (r.width() > paint.getStrokeWidth() * 2) {
 *             SkAutoCanvasRestore acr(canvas, true);
 *             canvas->rotate(fRotate * sign);
 *
 *             paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
 *             canvas->drawOval(r, paint);
 *             r.inset(delta, delta);
 *             sign = -sign;
 *         }
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fRotate = TimeUtils::Scaled(1e-9 * nanos, 60, 360);
 *         return true;
 *     }
 *
 * private:
 *     SkScalar fRotate;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class StrokeCircleGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRotate
   * ```
   */
  private var fRotate: SkScalar = TODO("Initialize fRotate")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokecircle"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(520, 520); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->scale(20, 20);
   *         canvas->translate(13, 13);
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStroke(true);
   *         paint.setStrokeWidth(SK_Scalar1 / 2);
   *
   *         const SkScalar delta = paint.getStrokeWidth() * 3 / 2;
   *         SkRect r = SkRect::MakeXYWH(-12, -12, 24, 24);
   *         SkRandom rand;
   *
   *         SkScalar sign = 1;
   *         while (r.width() > paint.getStrokeWidth() * 2) {
   *             SkAutoCanvasRestore acr(canvas, true);
   *             canvas->rotate(fRotate * sign);
   *
   *             paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
   *             canvas->drawOval(r, paint);
   *             r.inset(delta, delta);
   *             sign = -sign;
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
   * bool onAnimate(double nanos) override {
   *         fRotate = TimeUtils::Scaled(1e-9 * nanos, 60, 360);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
