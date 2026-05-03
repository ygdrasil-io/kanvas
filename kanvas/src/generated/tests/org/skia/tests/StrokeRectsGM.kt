package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkRandom
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class StrokeRectsGM : public GM {
 * public:
 *     StrokeRectsGM(bool rotated) : fRotated(rotated) {}
 *
 * protected:
 *     SkString getName() const override {
 *         if (fRotated) {
 *             return SkString("strokerects_rotated");
 *         } else {
 *             return SkString("strokerects");
 *         }
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(W * 2, H * 2); }
 *
 *     static void rnd_rect(SkRect* r, SkRandom& rand) {
 *         SkScalar x = rand.nextUScalar1() * W;
 *         SkScalar y = rand.nextUScalar1() * H;
 *         SkScalar w = rand.nextUScalar1() * (W >> 2);
 *         SkScalar h = rand.nextUScalar1() * (H >> 2);
 *         SkScalar hoffset = rand.nextSScalar1();
 *         SkScalar woffset = rand.nextSScalar1();
 *
 *         r->setXYWH(x, y, w, h);
 *         r->offset(-w/2 + woffset, -h/2 + hoffset);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         if (fRotated) {
 *             canvas->rotate(45.f, SW, SH);
 *         }
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *
 *         for (int y = 0; y < 2; y++) {
 *             paint.setAntiAlias(!!y);
 *             for (int x = 0; x < 2; x++) {
 *                 paint.setStrokeWidth(x * SkIntToScalar(3));
 *
 *                 SkAutoCanvasRestore acr(canvas, true);
 *                 canvas->translate(SW * x, SH * y);
 *                 canvas->clipRect(SkRect::MakeLTRB(
 *                         SkIntToScalar(2), SkIntToScalar(2)
 *                         , SW - SkIntToScalar(2), SH - SkIntToScalar(2)
 *                 ));
 *
 *                 SkRandom rand;
 *                 for (int i = 0; i < N; i++) {
 *                     SkRect r;
 *                     rnd_rect(&r, rand);
 *                     canvas->drawRect(r, paint);
 *                 }
 *             }
 *         }
 *     }
 *
 * private:
 *     bool fRotated;
 * }
 * ```
 */
public open class StrokeRectsGM public constructor(
  rotated: Boolean,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * bool fRotated
   * ```
   */
  private var fRotated: Boolean = TODO("Initialize fRotated")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         if (fRotated) {
   *             return SkString("strokerects_rotated");
   *         } else {
   *             return SkString("strokerects");
   *         }
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W * 2, H * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         if (fRotated) {
   *             canvas->rotate(45.f, SW, SH);
   *         }
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *
   *         for (int y = 0; y < 2; y++) {
   *             paint.setAntiAlias(!!y);
   *             for (int x = 0; x < 2; x++) {
   *                 paint.setStrokeWidth(x * SkIntToScalar(3));
   *
   *                 SkAutoCanvasRestore acr(canvas, true);
   *                 canvas->translate(SW * x, SH * y);
   *                 canvas->clipRect(SkRect::MakeLTRB(
   *                         SkIntToScalar(2), SkIntToScalar(2)
   *                         , SW - SkIntToScalar(2), SH - SkIntToScalar(2)
   *                 ));
   *
   *                 SkRandom rand;
   *                 for (int i = 0; i < N; i++) {
   *                     SkRect r;
   *                     rnd_rect(&r, rand);
   *                     canvas->drawRect(r, paint);
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void rnd_rect(SkRect* r, SkRandom& rand) {
     *         SkScalar x = rand.nextUScalar1() * W;
     *         SkScalar y = rand.nextUScalar1() * H;
     *         SkScalar w = rand.nextUScalar1() * (W >> 2);
     *         SkScalar h = rand.nextUScalar1() * (H >> 2);
     *         SkScalar hoffset = rand.nextSScalar1();
     *         SkScalar woffset = rand.nextSScalar1();
     *
     *         r->setXYWH(x, y, w, h);
     *         r->offset(-w/2 + woffset, -h/2 + hoffset);
     *     }
     * ```
     */
    protected fun rndRect(r: SkRect?, rand: SkRandom) {
      TODO("Implement rndRect")
    }
  }
}
