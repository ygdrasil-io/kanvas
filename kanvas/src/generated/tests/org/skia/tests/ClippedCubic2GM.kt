package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ClippedCubic2GM : public skiagm::GM {
 *     SkString getName() const override { return SkString("clippedcubic2"); }
 *
 *     SkISize getISize() override { return {1240, 390}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->save();
 *         canvas->translate(-2, 120);
 *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 80, 150));
 *         canvas->translate(0, 170);
 *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 80, 100));
 *         canvas->translate(0, 170);
 *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 30, 150));
 *         canvas->translate(0, 170);
 *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 10, 150));
 *         canvas->restore();
 *         canvas->save();
 *         canvas->translate(20, -2);
 *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 150, 80));
 *         canvas->translate(170, 0);
 *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 100, 80));
 *         canvas->translate(170, 0);
 *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 150, 30));
 *         canvas->translate(170, 0);
 *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 150, 10));
 *         canvas->restore();
 *     }
 *
 *     void drawOne(SkCanvas* canvas, const SkPath& path, const SkRect& clip) {
 *         SkPaint framePaint, fillPaint;
 *         framePaint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawRect(clip, framePaint);
 *         canvas->drawPath(path, framePaint);
 *         canvas->save();
 *         canvas->clipRect(clip);
 *         canvas->drawPath(path, fillPaint);
 *         canvas->restore();
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         SkPathBuilder builder;
 *         builder.moveTo(69.7030518991886f, 0);
 *         builder.cubicTo( 69.7030518991886f, 21.831149999999997f,
 *                 58.08369508178456f, 43.66448333333333f, 34.8449814469765f, 65.5f);
 *         builder.cubicTo( 11.608591683531916f, 87.33115f, -0.010765133872116195f, 109.16448333333332f,
 *                 -0.013089005235602302f, 131);
 *         builder.close();
 *         fPath = builder.detach();
 *
 *         SkMatrix matrix;
 *         matrix.reset();
 *         matrix.setScaleX(0);
 *         matrix.setScaleY(0);
 *         matrix.setSkewX(1);
 *         matrix.setSkewY(1);
 *         fFlipped = fPath.makeTransform(matrix);
 *     }
 *
 *     SkPath fPath;
 *     SkPath fFlipped;
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ClippedCubic2GM : GM() {
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
   * SkPath fFlipped
   * ```
   */
  private var fFlipped: SkPath = TODO("Initialize fFlipped")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("clippedcubic2"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1240, 390}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->save();
   *         canvas->translate(-2, 120);
   *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 80, 150));
   *         canvas->translate(0, 170);
   *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 80, 100));
   *         canvas->translate(0, 170);
   *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 30, 150));
   *         canvas->translate(0, 170);
   *         drawOne(canvas, fPath, SkRect::MakeLTRB(0, 0, 10, 150));
   *         canvas->restore();
   *         canvas->save();
   *         canvas->translate(20, -2);
   *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 150, 80));
   *         canvas->translate(170, 0);
   *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 100, 80));
   *         canvas->translate(170, 0);
   *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 150, 30));
   *         canvas->translate(170, 0);
   *         drawOne(canvas, fFlipped, SkRect::MakeLTRB(0, 0, 150, 10));
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawOne(SkCanvas* canvas, const SkPath& path, const SkRect& clip) {
   *         SkPaint framePaint, fillPaint;
   *         framePaint.setStyle(SkPaint::kStroke_Style);
   *         canvas->drawRect(clip, framePaint);
   *         canvas->drawPath(path, framePaint);
   *         canvas->save();
   *         canvas->clipRect(clip);
   *         canvas->drawPath(path, fillPaint);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawOne(
    canvas: SkCanvas?,
    path: SkPath,
    clip: SkRect,
  ) {
    TODO("Implement drawOne")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         SkPathBuilder builder;
   *         builder.moveTo(69.7030518991886f, 0);
   *         builder.cubicTo( 69.7030518991886f, 21.831149999999997f,
   *                 58.08369508178456f, 43.66448333333333f, 34.8449814469765f, 65.5f);
   *         builder.cubicTo( 11.608591683531916f, 87.33115f, -0.010765133872116195f, 109.16448333333332f,
   *                 -0.013089005235602302f, 131);
   *         builder.close();
   *         fPath = builder.detach();
   *
   *         SkMatrix matrix;
   *         matrix.reset();
   *         matrix.setScaleX(0);
   *         matrix.setScaleY(0);
   *         matrix.setSkewX(1);
   *         matrix.setSkewY(1);
   *         fFlipped = fPath.makeTransform(matrix);
   *     }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }
}
