package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDrawable
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct MyDrawable : public SkDrawable {
 *     SkRect onGetBounds() override { return SkRect::MakeWH(50, 100);  }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPath path = SkPathBuilder().moveTo(10, 10)
 *                                      .conicTo(10, 90, 50, 90, 0.9f)
 *                                      .detach();
 *
 *        SkPaint paint;
 *        paint.setColor(SK_ColorBLUE);
 *        canvas->drawRect(path.getBounds(), paint);
 *
 *        paint.setAntiAlias(true);
 *        paint.setColor(SK_ColorWHITE);
 *        canvas->drawPath(path, paint);
 *     }
 * }
 * ```
 */
public open class MyDrawable : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * SkRect onGetBounds() override { return SkRect::MakeWH(50, 100);  }
   * ```
   */
  public override fun onGetBounds(): SkRect {
    TODO("Implement onGetBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPath path = SkPathBuilder().moveTo(10, 10)
   *                                      .conicTo(10, 90, 50, 90, 0.9f)
   *                                      .detach();
   *
   *        SkPaint paint;
   *        paint.setColor(SK_ColorBLUE);
   *        canvas->drawRect(path.getBounds(), paint);
   *
   *        paint.setAntiAlias(true);
   *        paint.setColor(SK_ColorWHITE);
   *        canvas->drawPath(path, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
