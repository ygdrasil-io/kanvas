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
 * class AddArcGM : public skiagm::GM {
 * public:
 *     AddArcGM() : fRotate(0) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("addarc"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1040, 1040); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(20, 20);
 *
 *         SkRect r = SkRect::MakeWH(1000, 1000);
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStroke(true);
 *         paint.setStrokeWidth(15);
 *
 *         const SkScalar inset = paint.getStrokeWidth() + 4;
 *         const SkScalar sweepAngle = 345;
 *         SkRandom rand;
 *
 *         SkScalar sign = 1;
 *         while (r.width() > paint.getStrokeWidth() * 3) {
 *             paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
 *             SkScalar startAngle = rand.nextUScalar1() * 360;
 *
 *             SkScalar speed = SkScalarSqrt(16 / r.width()) * 0.5f;
 *             startAngle += fRotate * 360 * speed * sign;
 *
 *             SkPathBuilder path;
 *             path.addArc(r, startAngle, sweepAngle);
 *             canvas->drawPath(path.detach().setIsVolatile(true), paint);
 *
 *             r.inset(inset, inset);
 *             sign = -sign;
 *         }
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         fRotate = TimeUtils::Scaled(1e-9 * nanos, 1, 360);
 *         return true;
 *     }
 *
 * private:
 *     SkScalar fRotate;
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class AddArcGM public constructor() : GM() {
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
   * SkString getName() const override { return SkString("addarc"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1040, 1040); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(20, 20);
   *
   *         SkRect r = SkRect::MakeWH(1000, 1000);
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStroke(true);
   *         paint.setStrokeWidth(15);
   *
   *         const SkScalar inset = paint.getStrokeWidth() + 4;
   *         const SkScalar sweepAngle = 345;
   *         SkRandom rand;
   *
   *         SkScalar sign = 1;
   *         while (r.width() > paint.getStrokeWidth() * 3) {
   *             paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
   *             SkScalar startAngle = rand.nextUScalar1() * 360;
   *
   *             SkScalar speed = SkScalarSqrt(16 / r.width()) * 0.5f;
   *             startAngle += fRotate * 360 * speed * sign;
   *
   *             SkPathBuilder path;
   *             path.addArc(r, startAngle, sweepAngle);
   *             canvas->drawPath(path.detach().setIsVolatile(true), paint);
   *
   *             r.inset(inset, inset);
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
   *         fRotate = TimeUtils::Scaled(1e-9 * nanos, 1, 360);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
