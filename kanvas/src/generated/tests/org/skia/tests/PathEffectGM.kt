package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PathEffectGM : public GM {
 * public:
 *     PathEffectGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("patheffect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 600); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *
 *         SkPath path = SkPath::Polygon({{
 *             {20, 20},
 *             {70, 120},
 *             {120, 30},
 *             {170, 80},
 *             {240, 50},
 *         }}, false);
 *
 *         canvas->save();
 *         for (size_t i = 0; i < std::size(gPE); i++) {
 *             gPE[i](&paint);
 *             canvas->drawPath(path, paint);
 *             canvas->translate(0, 75);
 *         }
 *         canvas->restore();
 *
 *         SkRect r = { 0, 0, 250, 120 };
 *         path = SkPathBuilder().addOval(r, SkPathDirection::kCW)
 *                               .addRect(r.makeInset(50, 50), SkPathDirection::kCCW)
 *                               .detach();
 *
 *         canvas->translate(320, 20);
 *         for (size_t i = 0; i < std::size(gPE2); i++) {
 *             gPE2[i](&paint);
 *             canvas->drawPath(path, paint);
 *             canvas->translate(0, 160);
 *         }
 *
 *         const SkIRect rect = SkIRect::MakeXYWH(20, 20, 60, 60);
 *         for (size_t i = 0; i < std::size(gPE); i++) {
 *             SkPaint p;
 *             p.setAntiAlias(true);
 *             p.setStyle(SkPaint::kFill_Style);
 *             gPE[i](&p);
 *             canvas->drawIRect(rect, p);
 *             canvas->translate(75, 0);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PathEffectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("patheffect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 600); }
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
   *         paint.setStyle(SkPaint::kStroke_Style);
   *
   *         SkPath path = SkPath::Polygon({{
   *             {20, 20},
   *             {70, 120},
   *             {120, 30},
   *             {170, 80},
   *             {240, 50},
   *         }}, false);
   *
   *         canvas->save();
   *         for (size_t i = 0; i < std::size(gPE); i++) {
   *             gPE[i](&paint);
   *             canvas->drawPath(path, paint);
   *             canvas->translate(0, 75);
   *         }
   *         canvas->restore();
   *
   *         SkRect r = { 0, 0, 250, 120 };
   *         path = SkPathBuilder().addOval(r, SkPathDirection::kCW)
   *                               .addRect(r.makeInset(50, 50), SkPathDirection::kCCW)
   *                               .detach();
   *
   *         canvas->translate(320, 20);
   *         for (size_t i = 0; i < std::size(gPE2); i++) {
   *             gPE2[i](&paint);
   *             canvas->drawPath(path, paint);
   *             canvas->translate(0, 160);
   *         }
   *
   *         const SkIRect rect = SkIRect::MakeXYWH(20, 20, 60, 60);
   *         for (size_t i = 0; i < std::size(gPE); i++) {
   *             SkPaint p;
   *             p.setAntiAlias(true);
   *             p.setStyle(SkPaint::kFill_Style);
   *             gPE[i](&p);
   *             canvas->drawIRect(rect, p);
   *             canvas->translate(75, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
