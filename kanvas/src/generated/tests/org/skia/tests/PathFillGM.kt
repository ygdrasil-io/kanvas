package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class PathFillGM : public skiagm::GM {
 *     SkPath  fPath[N];
 *     SkScalar fDY[N];
 *     SkPath  fInfoPath;
 *     SkPath  fAccessibilityPath;
 *     SkPath  fVisualizerPath;
 * protected:
 *     void onOnceBeforeDraw() override {
 *         for (size_t i = 0; i < N; i++) {
 *             auto [path, dy] = gProcs[i]();
 *             fPath[i] = path;
 *             fDY[i] = dy;
 *         }
 *
 *         fInfoPath = make_info();
 *         fAccessibilityPath = make_accessibility();
 *         fVisualizerPath = make_visualizer();
 *     }
 *
 *     SkString getName() const override { return SkString("pathfill"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         for (size_t i = 0; i < N; i++) {
 *             canvas->drawPath(fPath[i], paint);
 *             canvas->translate(SkIntToScalar(0), fDY[i]);
 *         }
 *
 *         canvas->save();
 *         canvas->scale(0.300000011920929f, 0.300000011920929f);
 *         canvas->translate(50, 50);
 *         canvas->drawPath(fInfoPath, paint);
 *         canvas->restore();
 *
 *         canvas->scale(2, 2);
 *         canvas->translate(5, 15);
 *         canvas->drawPath(fAccessibilityPath, paint);
 *
 *         canvas->scale(0.5f, 0.5f);
 *         canvas->translate(5, 50);
 *         canvas->drawPath(fVisualizerPath, paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class PathFillGM : GM() {
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
   * SkPath  fInfoPath
   * ```
   */
  private var fInfoPath: SkPath = TODO("Initialize fInfoPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath  fAccessibilityPath
   * ```
   */
  private var fAccessibilityPath: SkPath = TODO("Initialize fAccessibilityPath")

  /**
   * C++ original:
   * ```cpp
   * SkPath  fVisualizerPath
   * ```
   */
  private var fVisualizerPath: SkPath = TODO("Initialize fVisualizerPath")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         for (size_t i = 0; i < N; i++) {
   *             auto [path, dy] = gProcs[i]();
   *             fPath[i] = path;
   *             fDY[i] = dy;
   *         }
   *
   *         fInfoPath = make_info();
   *         fAccessibilityPath = make_accessibility();
   *         fVisualizerPath = make_visualizer();
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pathfill"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
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
   *         for (size_t i = 0; i < N; i++) {
   *             canvas->drawPath(fPath[i], paint);
   *             canvas->translate(SkIntToScalar(0), fDY[i]);
   *         }
   *
   *         canvas->save();
   *         canvas->scale(0.300000011920929f, 0.300000011920929f);
   *         canvas->translate(50, 50);
   *         canvas->drawPath(fInfoPath, paint);
   *         canvas->restore();
   *
   *         canvas->scale(2, 2);
   *         canvas->translate(5, 15);
   *         canvas->drawPath(fAccessibilityPath, paint);
   *
   *         canvas->scale(0.5f, 0.5f);
   *         canvas->translate(5, 50);
   *         canvas->drawPath(fVisualizerPath, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
