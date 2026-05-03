package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ManyCirclesGM : public GM {
 *     // This GM attempts to flood Ganesh with more circles than will fit in a single index buffer
 *     // Stresses crbug.com/688582.
 * public:
 *     ManyCirclesGM() {
 *         this->setBGColor(0xFFFFFFFF);
 *     }
 *
 * protected:
 *     static const int kWidth = 800;
 *     static const int kHeight = 600;
 *
 *     SkString getName() const override { return SkString("manycircles"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRandom rand(1);
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         int total = 10000;
 *         while (total--) {
 *             SkScalar x = rand.nextF() * kWidth - 100;
 *             SkScalar y = rand.nextF() * kHeight - 100;
 *             SkScalar w = rand.nextF() * 200;
 *             SkRect circle = SkRect::MakeXYWH(x, y, w, w);
 *             paint.setColor(gen_color(&rand));
 *             canvas->drawOval(circle, paint);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ManyCirclesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("manycircles"); }
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
   *         SkRandom rand(1);
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         int total = 10000;
   *         while (total--) {
   *             SkScalar x = rand.nextF() * kWidth - 100;
   *             SkScalar y = rand.nextF() * kHeight - 100;
   *             SkScalar w = rand.nextF() * 200;
   *             SkRect circle = SkRect::MakeXYWH(x, y, w, w);
   *             paint.setColor(gen_color(&rand));
   *             canvas->drawOval(circle, paint);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    protected val kWidth: Int = TODO("Initialize kWidth")

    protected val kHeight: Int = TODO("Initialize kHeight")
  }
}
