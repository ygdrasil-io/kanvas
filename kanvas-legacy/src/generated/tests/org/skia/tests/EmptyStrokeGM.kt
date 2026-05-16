package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EmptyStrokeGM : public GM {
 *     SkString getName() const override { return SkString("emptystroke"); }
 *
 *     SkISize getISize() override { return {200, 240}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static constexpr SkPath (*kProcs[])() = {
 *             make_path_move,             // expect red red red
 *             make_path_move_close,       // expect black black black
 *             make_path_move_line,        // expect black black black
 *             make_path_move_mix,         // expect red black black,
 *         };
 *
 *         SkPaint strokePaint;
 *         strokePaint.setStyle(SkPaint::kStroke_Style);
 *         strokePaint.setStrokeWidth(21);
 *         strokePaint.setStrokeCap(SkPaint::kSquare_Cap);
 *
 *         SkPaint dotPaint;
 *         dotPaint.setColor(SK_ColorRED);
 *         strokePaint.setStyle(SkPaint::kStroke_Style);
 *         dotPaint.setStrokeWidth(7);
 *
 *         for (auto proc : kProcs) {
 *             canvas->drawPoints(SkCanvas::kPoints_PointMode, kPts, dotPaint);
 *             canvas->drawPath(proc(), strokePaint);
 *             canvas->translate(0, 40);
 *         }
 *     }
 * }
 * ```
 */
public open class EmptyStrokeGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("emptystroke"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {200, 240}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         static constexpr SkPath (*kProcs[])() = {
   *             make_path_move,             // expect red red red
   *             make_path_move_close,       // expect black black black
   *             make_path_move_line,        // expect black black black
   *             make_path_move_mix,         // expect red black black,
   *         };
   *
   *         SkPaint strokePaint;
   *         strokePaint.setStyle(SkPaint::kStroke_Style);
   *         strokePaint.setStrokeWidth(21);
   *         strokePaint.setStrokeCap(SkPaint::kSquare_Cap);
   *
   *         SkPaint dotPaint;
   *         dotPaint.setColor(SK_ColorRED);
   *         strokePaint.setStyle(SkPaint::kStroke_Style);
   *         dotPaint.setStrokeWidth(7);
   *
   *         for (auto proc : kProcs) {
   *             canvas->drawPoints(SkCanvas::kPoints_PointMode, kPts, dotPaint);
   *             canvas->drawPath(proc(), strokePaint);
   *             canvas->translate(0, 40);
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
