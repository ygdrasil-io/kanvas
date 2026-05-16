package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ScaledStrokesGM : public skiagm::GM {
 * public:
 *     ScaledStrokesGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("scaledstrokes"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 320); }
 *
 *     static void draw_path(SkScalar size, SkCanvas* canvas, SkPaint paint) {
 *         SkScalar c = 0.551915024494f * size;
 *         SkPathBuilder path;
 *         path.moveTo(0.0f, size);
 *         path.cubicTo(c, size, size, c, size, 0.0f);
 *         path.cubicTo(size, -c, c, -size, 0.0f, -size);
 *         path.cubicTo(-c, -size, -size, -c, -size, 0.0f);
 *         path.cubicTo(-size, c, -c, size, 0.0f, size);
 *         canvas->drawPath(path.detach(), paint);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         SkPath path;
 *         paint.setStyle(SkPaint::Style::kStroke_Style);
 *         canvas->translate(5.0f, 5.0f);
 *         const SkScalar size = 60.0f;
 *         for (int i = 0; i < 2; i++) {
 *             paint.setAntiAlias(i == 1);
 *             for (int j = 0; j < 4; j++) {
 *                 SkScalar scale = 4.0f - j;
 *                 paint.setStrokeWidth(4.0f / scale);
 *                 canvas->save();
 *                 canvas->translate(size / 2.0f, size / 2.0f);
 *                 canvas->scale(scale, scale);
 *                 draw_path(size / 2.0f / scale, canvas, paint);
 *                 canvas->restore();
 *
 *                 canvas->save();
 *                 canvas->translate(size / 2.0f, 80.0f + size / 2.0f);
 *                 canvas->scale(scale, scale);
 *                 canvas->drawCircle(0.0f, 0.0f, size / 2.0f / scale, paint);
 *                 canvas->restore();
 *
 *                 canvas->save();
 *                 canvas->translate(0.0f, 160.0f);
 *                 canvas->scale(scale, scale);
 *                 canvas->drawRect(SkRect::MakeXYWH(0.0f, 0.0f, size / scale, size / scale), paint);
 *                 canvas->restore();
 *
 *                 canvas->save();
 *                 canvas->translate(0.0f, 240.0f);
 *                 canvas->scale(scale, scale);
 *                 canvas->drawLine(0.0f, 0.0f, size / scale, size / scale, paint);
 *                 canvas->restore();
 *
 *                 canvas->translate(80.0f, 0.0f);
 *             }
 *         }
 *
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ScaledStrokesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("scaledstrokes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 320); }
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
   *         SkPath path;
   *         paint.setStyle(SkPaint::Style::kStroke_Style);
   *         canvas->translate(5.0f, 5.0f);
   *         const SkScalar size = 60.0f;
   *         for (int i = 0; i < 2; i++) {
   *             paint.setAntiAlias(i == 1);
   *             for (int j = 0; j < 4; j++) {
   *                 SkScalar scale = 4.0f - j;
   *                 paint.setStrokeWidth(4.0f / scale);
   *                 canvas->save();
   *                 canvas->translate(size / 2.0f, size / 2.0f);
   *                 canvas->scale(scale, scale);
   *                 draw_path(size / 2.0f / scale, canvas, paint);
   *                 canvas->restore();
   *
   *                 canvas->save();
   *                 canvas->translate(size / 2.0f, 80.0f + size / 2.0f);
   *                 canvas->scale(scale, scale);
   *                 canvas->drawCircle(0.0f, 0.0f, size / 2.0f / scale, paint);
   *                 canvas->restore();
   *
   *                 canvas->save();
   *                 canvas->translate(0.0f, 160.0f);
   *                 canvas->scale(scale, scale);
   *                 canvas->drawRect(SkRect::MakeXYWH(0.0f, 0.0f, size / scale, size / scale), paint);
   *                 canvas->restore();
   *
   *                 canvas->save();
   *                 canvas->translate(0.0f, 240.0f);
   *                 canvas->scale(scale, scale);
   *                 canvas->drawLine(0.0f, 0.0f, size / scale, size / scale, paint);
   *                 canvas->restore();
   *
   *                 canvas->translate(80.0f, 0.0f);
   *             }
   *         }
   *
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
     * static void draw_path(SkScalar size, SkCanvas* canvas, SkPaint paint) {
     *         SkScalar c = 0.551915024494f * size;
     *         SkPathBuilder path;
     *         path.moveTo(0.0f, size);
     *         path.cubicTo(c, size, size, c, size, 0.0f);
     *         path.cubicTo(size, -c, c, -size, 0.0f, -size);
     *         path.cubicTo(-c, -size, -size, -c, -size, 0.0f);
     *         path.cubicTo(-size, c, -c, size, 0.0f, size);
     *         canvas->drawPath(path.detach(), paint);
     *     }
     * ```
     */
    protected fun drawPath(
      size: SkScalar,
      canvas: SkCanvas?,
      paint: SkPaint,
    ) {
      TODO("Implement drawPath")
    }
  }
}
