package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ClippedCubicGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("clippedcubic"); }
 *
 *     SkISize getISize() override { return {1240, 390}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPath path = SkPathBuilder()
 *                       .moveTo(0, 0)
 *                       .cubicTo(140, 150, 40, 10, 170, 150)
 *                       .detach();
 *
 *         SkPaint paint;
 *         SkRect bounds = path.getBounds();
 *
 *         for (SkScalar dy = -1; dy <= 1; dy += 1) {
 *             canvas->save();
 *             for (SkScalar dx = -1; dx <= 1; dx += 1) {
 *                 canvas->save();
 *                 canvas->clipRect(bounds);
 *                 canvas->translate(dx, dy);
 *                 canvas->drawPath(path, paint);
 *                 canvas->restore();
 *
 *                 canvas->translate(bounds.width(), 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, bounds.height());
 *         }
 *     }
 * }
 * ```
 */
public open class ClippedCubicGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("clippedcubic"); }
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
   *         SkPath path = SkPathBuilder()
   *                       .moveTo(0, 0)
   *                       .cubicTo(140, 150, 40, 10, 170, 150)
   *                       .detach();
   *
   *         SkPaint paint;
   *         SkRect bounds = path.getBounds();
   *
   *         for (SkScalar dy = -1; dy <= 1; dy += 1) {
   *             canvas->save();
   *             for (SkScalar dx = -1; dx <= 1; dx += 1) {
   *                 canvas->save();
   *                 canvas->clipRect(bounds);
   *                 canvas->translate(dx, dy);
   *                 canvas->drawPath(path, paint);
   *                 canvas->restore();
   *
   *                 canvas->translate(bounds.width(), 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, bounds.height());
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
