package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Dashing5GM : public skiagm::GM {
 * public:
 *     Dashing5GM(bool doAA) : fDoAA(doAA) {}
 *
 * private:
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString(fDoAA ? "dashing5_aa" : "dashing5_bw"); }
 *
 *     SkISize getISize() override { return {400, 200}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr int kOn = 4;
 *         constexpr int kOff = 4;
 *         constexpr int kIntervalLength = kOn + kOff;
 *
 *         constexpr SkColor gColors[kIntervalLength] = {
 *             SK_ColorRED,
 *             SK_ColorGREEN,
 *             SK_ColorBLUE,
 *             SK_ColorCYAN,
 *             SK_ColorMAGENTA,
 *             SK_ColorYELLOW,
 *             SK_ColorGRAY,
 *             SK_ColorDKGRAY
 *         };
 *
 *         SkPaint paint;
 *         paint.setStroke(true);
 *
 *         paint.setAntiAlias(fDoAA);
 *
 *         SkMatrix rot;
 *         rot.setRotate(90);
 *         SkASSERT(rot.rectStaysRect());
 *
 *         canvas->concat(rot);
 *
 *         int sign;       // used to toggle the direction of the lines
 *         int phase = 0;
 *
 *         for (int x = 0; x < 200; x += 10) {
 *             paint.setStrokeWidth(SkIntToScalar(phase+1));
 *             paint.setColor(gColors[phase]);
 *             sign = (x % 20) ? 1 : -1;
 *             drawline(canvas, kOn, kOff, paint,
 *                      SkIntToScalar(x), -sign * SkIntToScalar(10003),
 *                      SkIntToScalar(phase),
 *                      SkIntToScalar(x),  sign * SkIntToScalar(10003));
 *             phase = (phase + 1) % kIntervalLength;
 *         }
 *
 *         for (int y = -400; y < 0; y += 10) {
 *             paint.setStrokeWidth(SkIntToScalar(phase+1));
 *             paint.setColor(gColors[phase]);
 *             sign = (y % 20) ? 1 : -1;
 *             drawline(canvas, kOn, kOff, paint,
 *                      -sign * SkIntToScalar(10003), SkIntToScalar(y),
 *                      SkIntToScalar(phase),
 *                       sign * SkIntToScalar(10003), SkIntToScalar(y));
 *             phase = (phase + 1) % kIntervalLength;
 *         }
 *     }
 *
 * private:
 *     bool fDoAA;
 * }
 * ```
 */
public open class Dashing5GM public constructor(
  doAA: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fDoAA
   * ```
   */
  private var fDoAA: Boolean = TODO("Initialize fDoAA")

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  public override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString(fDoAA ? "dashing5_aa" : "dashing5_bw"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {400, 200}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr int kOn = 4;
   *         constexpr int kOff = 4;
   *         constexpr int kIntervalLength = kOn + kOff;
   *
   *         constexpr SkColor gColors[kIntervalLength] = {
   *             SK_ColorRED,
   *             SK_ColorGREEN,
   *             SK_ColorBLUE,
   *             SK_ColorCYAN,
   *             SK_ColorMAGENTA,
   *             SK_ColorYELLOW,
   *             SK_ColorGRAY,
   *             SK_ColorDKGRAY
   *         };
   *
   *         SkPaint paint;
   *         paint.setStroke(true);
   *
   *         paint.setAntiAlias(fDoAA);
   *
   *         SkMatrix rot;
   *         rot.setRotate(90);
   *         SkASSERT(rot.rectStaysRect());
   *
   *         canvas->concat(rot);
   *
   *         int sign;       // used to toggle the direction of the lines
   *         int phase = 0;
   *
   *         for (int x = 0; x < 200; x += 10) {
   *             paint.setStrokeWidth(SkIntToScalar(phase+1));
   *             paint.setColor(gColors[phase]);
   *             sign = (x % 20) ? 1 : -1;
   *             drawline(canvas, kOn, kOff, paint,
   *                      SkIntToScalar(x), -sign * SkIntToScalar(10003),
   *                      SkIntToScalar(phase),
   *                      SkIntToScalar(x),  sign * SkIntToScalar(10003));
   *             phase = (phase + 1) % kIntervalLength;
   *         }
   *
   *         for (int y = -400; y < 0; y += 10) {
   *             paint.setStrokeWidth(SkIntToScalar(phase+1));
   *             paint.setColor(gColors[phase]);
   *             sign = (y % 20) ? 1 : -1;
   *             drawline(canvas, kOn, kOff, paint,
   *                      -sign * SkIntToScalar(10003), SkIntToScalar(y),
   *                      SkIntToScalar(phase),
   *                       sign * SkIntToScalar(10003), SkIntToScalar(y));
   *             phase = (phase + 1) % kIntervalLength;
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
