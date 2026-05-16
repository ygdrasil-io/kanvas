package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Strokes2GM : public skiagm::GM {
 *     SkPath fPath;
 * protected:
 *     void onOnceBeforeDraw() override {
 *         SkRandom rand;
 *         SkPathBuilder builder;
 *         builder.moveTo(0, 0);
 *         for (int i = 0; i < 13; i++) {
 *             SkScalar x = rand.nextUScalar1() * (W >> 1);
 *             SkScalar y = rand.nextUScalar1() * (H >> 1);
 *             builder.lineTo(x, y);
 *         }
 *         fPath = builder.detach();
 *     }
 *
 *     SkString getName() const override { return SkString("strokes_poly"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->drawColor(SK_ColorWHITE);
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(SkIntToScalar(9)/2);
 *
 *         for (int y = 0; y < 2; y++) {
 *             paint.setAntiAlias(!!y);
 *             SkAutoCanvasRestore acr(canvas, true);
 *             canvas->translate(0, SH * y);
 *             canvas->clipRect(SkRect::MakeLTRB(SkIntToScalar(2),
 *                                               SkIntToScalar(2),
 *                                               SW - SkIntToScalar(2),
 *                                               SH - SkIntToScalar(2)));
 *
 *             SkRandom rand;
 *             for (int i = 0; i < N/2; i++) {
 *                 SkRect r;
 *                 rnd_rect(&r, &paint, rand);
 *                 canvas->rotate(SkIntToScalar(15), SW/2, SH/2);
 *                 canvas->drawPath(fPath, paint);
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class Strokes2GM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPath fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkRandom rand;
   *         SkPathBuilder builder;
   *         builder.moveTo(0, 0);
   *         for (int i = 0; i < 13; i++) {
   *             SkScalar x = rand.nextUScalar1() * (W >> 1);
   *             SkScalar y = rand.nextUScalar1() * (H >> 1);
   *             builder.lineTo(x, y);
   *         }
   *         fPath = builder.detach();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokes_poly"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->drawColor(SK_ColorWHITE);
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(SkIntToScalar(9)/2);
   *
   *         for (int y = 0; y < 2; y++) {
   *             paint.setAntiAlias(!!y);
   *             SkAutoCanvasRestore acr(canvas, true);
   *             canvas->translate(0, SH * y);
   *             canvas->clipRect(SkRect::MakeLTRB(SkIntToScalar(2),
   *                                               SkIntToScalar(2),
   *                                               SW - SkIntToScalar(2),
   *                                               SH - SkIntToScalar(2)));
   *
   *             SkRandom rand;
   *             for (int i = 0; i < N/2; i++) {
   *                 SkRect r;
   *                 rnd_rect(&r, &paint, rand);
   *                 canvas->rotate(SkIntToScalar(15), SW/2, SH/2);
   *                 canvas->drawPath(fPath, paint);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
