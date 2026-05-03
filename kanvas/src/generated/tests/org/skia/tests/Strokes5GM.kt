package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Strokes5GM : public skiagm::GM {
 * public:
 *     Strokes5GM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("zero_control_stroke"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint p;
 *         p.setColor(SK_ColorRED);
 *         p.setAntiAlias(true);
 *         p.setStyle(SkPaint::kStroke_Style);
 *         p.setStrokeWidth(40);
 *         p.setStrokeCap(SkPaint::kButt_Cap);
 *
 *         SkPath path = SkPathBuilder()
 *                       .moveTo(157.474f,111.753f)
 *                       .cubicTo(128.5f,111.5f,35.5f,29.5f,35.5f,29.5f)
 *                       .detach();
 *         canvas->drawPath(path, p);
 *         path = SkPathBuilder()
 *                .moveTo(250, 50)
 *                .quadTo(280, 80, 280, 80)
 *                .detach();
 *         canvas->drawPath(path, p);
 *         path = SkPathBuilder()
 *                .moveTo(150, 50)
 *                .conicTo(180, 80, 180, 80, 0.707f)
 *                .detach();
 *         canvas->drawPath(path, p);
 *
 *         path = SkPathBuilder()
 *                .moveTo(157.474f,311.753f)
 *                .cubicTo(157.474f,311.753f,85.5f,229.5f,35.5f,229.5f)
 *                .detach();
 *         canvas->drawPath(path, p);
 *         path = SkPathBuilder()
 *                .moveTo(280, 250)
 *                .quadTo(280, 250, 310, 280)
 *                .detach();
 *         canvas->drawPath(path, p);
 *         path = SkPathBuilder()
 *                .moveTo(180, 250)
 *                .conicTo(180, 250, 210, 280, 0.707f)
 *                .detach();
 *         canvas->drawPath(path, p);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class Strokes5GM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("zero_control_stroke"); }
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
   *         SkPaint p;
   *         p.setColor(SK_ColorRED);
   *         p.setAntiAlias(true);
   *         p.setStyle(SkPaint::kStroke_Style);
   *         p.setStrokeWidth(40);
   *         p.setStrokeCap(SkPaint::kButt_Cap);
   *
   *         SkPath path = SkPathBuilder()
   *                       .moveTo(157.474f,111.753f)
   *                       .cubicTo(128.5f,111.5f,35.5f,29.5f,35.5f,29.5f)
   *                       .detach();
   *         canvas->drawPath(path, p);
   *         path = SkPathBuilder()
   *                .moveTo(250, 50)
   *                .quadTo(280, 80, 280, 80)
   *                .detach();
   *         canvas->drawPath(path, p);
   *         path = SkPathBuilder()
   *                .moveTo(150, 50)
   *                .conicTo(180, 80, 180, 80, 0.707f)
   *                .detach();
   *         canvas->drawPath(path, p);
   *
   *         path = SkPathBuilder()
   *                .moveTo(157.474f,311.753f)
   *                .cubicTo(157.474f,311.753f,85.5f,229.5f,35.5f,229.5f)
   *                .detach();
   *         canvas->drawPath(path, p);
   *         path = SkPathBuilder()
   *                .moveTo(280, 250)
   *                .quadTo(280, 250, 310, 280)
   *                .detach();
   *         canvas->drawPath(path, p);
   *         path = SkPathBuilder()
   *                .moveTo(180, 250)
   *                .conicTo(180, 250, 210, 280, 0.707f)
   *                .detach();
   *         canvas->drawPath(path, p);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
