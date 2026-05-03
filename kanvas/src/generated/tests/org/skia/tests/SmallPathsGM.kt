package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SmallPathsGM : public skiagm::GM {
 *     SkPath  fPath[N];
 *     SkScalar fDY[N];
 * protected:
 *     void onOnceBeforeDraw() override {
 *         for (size_t i = 0; i < N; i++) {
 *             auto [path, dy] = gProcs[i]();
 *             fPath[i] = path;
 *             fDY[i]   = dy;
 *         }
 *     }
 *
 *     SkString getName() const override { return SkString("smallpaths"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 512); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         // first column: filled paths
 *         canvas->save();
 *         for (size_t i = 0; i < N; i++) {
 *             canvas->drawPath(fPath[i], paint);
 *             canvas->translate(gXTranslate[i], fDY[i]);
 *         }
 *         canvas->restore();
 *         canvas->translate(SkIntToScalar(120), SkIntToScalar(0));
 *
 *         // second column: stroked paths
 *         canvas->save();
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeCap(SkPaint::kButt_Cap);
 *         for (size_t i = 0; i < N; i++) {
 *             paint.setStrokeWidth(gWidths[i]);
 *             paint.setStrokeMiter(gMiters[i]);
 *             canvas->drawPath(fPath[i], paint);
 *             canvas->translate(gXTranslate[i], fDY[i]);
 *         }
 *         canvas->restore();
 *         canvas->translate(SkIntToScalar(120), SkIntToScalar(0));
 *
 *         // third column: stroked paths with different widths
 *         canvas->save();
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeCap(SkPaint::kButt_Cap);
 *         for (size_t i = 0; i < N; i++) {
 *             paint.setStrokeWidth(gWidths[i] + 2.0f);
 *             paint.setStrokeMiter(gMiters[i]);
 *             canvas->drawPath(fPath[i], paint);
 *             canvas->translate(gXTranslate[i], fDY[i]);
 *         }
 *         canvas->restore();
 *         canvas->translate(SkIntToScalar(120), SkIntToScalar(0));
 *
 *         // fourth column: stroked and filled paths
 *         paint.setStyle(SkPaint::kStrokeAndFill_Style);
 *         paint.setStrokeCap(SkPaint::kButt_Cap);
 *         for (size_t i = 0; i < N; i++) {
 *             paint.setStrokeWidth(gWidths[i]);
 *             paint.setStrokeMiter(gMiters[i]);
 *             canvas->drawPath(fPath[i], paint);
 *             canvas->translate(gXTranslate[i], fDY[i]);
 *         }
 *
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class SmallPathsGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPath  fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fDY
   * ```
   */
  private var fDY: SkScalar = TODO("Initialize fDY")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (size_t i = 0; i < N; i++) {
   *             auto [path, dy] = gProcs[i]();
   *             fPath[i] = path;
   *             fDY[i]   = dy;
   *         }
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("smallpaths"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 512); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         // first column: filled paths
   *         canvas->save();
   *         for (size_t i = 0; i < N; i++) {
   *             canvas->drawPath(fPath[i], paint);
   *             canvas->translate(gXTranslate[i], fDY[i]);
   *         }
   *         canvas->restore();
   *         canvas->translate(SkIntToScalar(120), SkIntToScalar(0));
   *
   *         // second column: stroked paths
   *         canvas->save();
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeCap(SkPaint::kButt_Cap);
   *         for (size_t i = 0; i < N; i++) {
   *             paint.setStrokeWidth(gWidths[i]);
   *             paint.setStrokeMiter(gMiters[i]);
   *             canvas->drawPath(fPath[i], paint);
   *             canvas->translate(gXTranslate[i], fDY[i]);
   *         }
   *         canvas->restore();
   *         canvas->translate(SkIntToScalar(120), SkIntToScalar(0));
   *
   *         // third column: stroked paths with different widths
   *         canvas->save();
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeCap(SkPaint::kButt_Cap);
   *         for (size_t i = 0; i < N; i++) {
   *             paint.setStrokeWidth(gWidths[i] + 2.0f);
   *             paint.setStrokeMiter(gMiters[i]);
   *             canvas->drawPath(fPath[i], paint);
   *             canvas->translate(gXTranslate[i], fDY[i]);
   *         }
   *         canvas->restore();
   *         canvas->translate(SkIntToScalar(120), SkIntToScalar(0));
   *
   *         // fourth column: stroked and filled paths
   *         paint.setStyle(SkPaint::kStrokeAndFill_Style);
   *         paint.setStrokeCap(SkPaint::kButt_Cap);
   *         for (size_t i = 0; i < N; i++) {
   *             paint.setStrokeWidth(gWidths[i]);
   *             paint.setStrokeMiter(gMiters[i]);
   *             canvas->drawPath(fPath[i], paint);
   *             canvas->translate(gXTranslate[i], fDY[i]);
   *         }
   *
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
