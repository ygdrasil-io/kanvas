package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class StringArtGM : public skiagm::GM {
 * public:
 *     StringArtGM() : fNumSteps(kMaxNumSteps) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("stringart"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar angle = kAngle*SK_ScalarPI + SkScalarHalf(SK_ScalarPI);
 *         SkScalar size = SkIntToScalar(std::min(kWidth, kHeight));
 *         SkPoint center = SkPoint::Make(SkScalarHalf(kWidth), SkScalarHalf(kHeight));
 *         SkScalar length = 5;
 *         SkScalar step = angle;
 *
 *         SkPathBuilder builder;
 *         builder.moveTo(center);
 *
 *         for (int i = 0; i < fNumSteps && length < (SkScalarHalf(size) - 10.f); ++i) {
 *             SkPoint rp = SkPoint::Make(length*SkScalarCos(step) + center.fX,
 *                                        length*SkScalarSin(step) + center.fY);
 *             builder.lineTo(rp);
 *             length += angle / SkScalarHalf(SK_ScalarPI);
 *             step += angle;
 *         }
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setColor(ToolUtils::color_to_565(0xFF007700));
 *
 *         canvas->drawPath(builder.detach(), paint);
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         constexpr SkScalar kDesiredDurationSecs = 3.0f;
 *
 *         // Make the animation ping-pong back and forth but start in the fully drawn state
 *         SkScalar fraction = 1.0f - TimeUtils::Scaled(1e-9 * nanos, 2.0f/kDesiredDurationSecs, 2.0f);
 *         if (fraction <= 0.0f) {
 *             fraction = -fraction;
 *         }
 *
 *         SkASSERT(fraction >= 0.0f && fraction <= 1.0f);
 *
 *         fNumSteps = (int) (fraction * kMaxNumSteps);
 *         return true;
 *     }
 *
 * private:
 *     int fNumSteps;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class StringArtGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * int fNumSteps
   * ```
   */
  private var fNumSteps: Int = TODO("Initialize fNumSteps")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("stringart"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkScalar angle = kAngle*SK_ScalarPI + SkScalarHalf(SK_ScalarPI);
   *         SkScalar size = SkIntToScalar(std::min(kWidth, kHeight));
   *         SkPoint center = SkPoint::Make(SkScalarHalf(kWidth), SkScalarHalf(kHeight));
   *         SkScalar length = 5;
   *         SkScalar step = angle;
   *
   *         SkPathBuilder builder;
   *         builder.moveTo(center);
   *
   *         for (int i = 0; i < fNumSteps && length < (SkScalarHalf(size) - 10.f); ++i) {
   *             SkPoint rp = SkPoint::Make(length*SkScalarCos(step) + center.fX,
   *                                        length*SkScalarSin(step) + center.fY);
   *             builder.lineTo(rp);
   *             length += angle / SkScalarHalf(SK_ScalarPI);
   *             step += angle;
   *         }
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setColor(ToolUtils::color_to_565(0xFF007700));
   *
   *         canvas->drawPath(builder.detach(), paint);
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
   *         constexpr SkScalar kDesiredDurationSecs = 3.0f;
   *
   *         // Make the animation ping-pong back and forth but start in the fully drawn state
   *         SkScalar fraction = 1.0f - TimeUtils::Scaled(1e-9 * nanos, 2.0f/kDesiredDurationSecs, 2.0f);
   *         if (fraction <= 0.0f) {
   *             fraction = -fraction;
   *         }
   *
   *         SkASSERT(fraction >= 0.0f && fraction <= 1.0f);
   *
   *         fNumSteps = (int) (fraction * kMaxNumSteps);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
