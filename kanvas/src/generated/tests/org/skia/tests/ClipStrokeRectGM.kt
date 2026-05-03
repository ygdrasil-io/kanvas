package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ClipStrokeRectGM : public skiagm::GM {
 * public:
 *     ClipStrokeRectGM() {
 *
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("clip_strokerect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(200, 400); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint p;
 *         p.setColor(SK_ColorRED);
 *         p.setAntiAlias(true);
 *         p.setStyle(SkPaint::kStroke_Style);
 *         p.setStrokeWidth(22);
 *
 *         SkRect r = SkRect::MakeXYWH(20, 20, 100, 100);
 *         // setting the height of this to 19 causes failure
 *         SkRect rect = SkRect::MakeXYWH(20, 0, 100, 20);
 *
 *         canvas->save();
 *         canvas->clipRect(rect, true);
 *         canvas->drawRect(r, p);
 *         canvas->restore();
 *
 *         p.setColor(SK_ColorBLUE);
 *         p.setStrokeWidth(2);
 *         canvas->drawRect(rect, p);
 *
 *         p.setColor(SK_ColorRED);
 *         p.setAntiAlias(true);
 *         p.setStyle(SkPaint::kStroke_Style);
 *         p.setStrokeWidth(22);
 *
 *         SkRect r2 = SkRect::MakeXYWH(20, 140, 100, 100);
 *         // setting the height of this to 19 causes failure
 *         SkRect rect2 = SkRect::MakeXYWH(20, 120, 100, 19);
 *
 *         canvas->save();
 *         canvas->clipRect(rect2, true);
 *         canvas->drawRect(r2, p);
 *         canvas->restore();
 *
 *         p.setColor(SK_ColorBLUE);
 *         p.setStrokeWidth(2);
 *         canvas->drawRect(rect2, p);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ClipStrokeRectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("clip_strokerect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(200, 400); }
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
   *         p.setStrokeWidth(22);
   *
   *         SkRect r = SkRect::MakeXYWH(20, 20, 100, 100);
   *         // setting the height of this to 19 causes failure
   *         SkRect rect = SkRect::MakeXYWH(20, 0, 100, 20);
   *
   *         canvas->save();
   *         canvas->clipRect(rect, true);
   *         canvas->drawRect(r, p);
   *         canvas->restore();
   *
   *         p.setColor(SK_ColorBLUE);
   *         p.setStrokeWidth(2);
   *         canvas->drawRect(rect, p);
   *
   *         p.setColor(SK_ColorRED);
   *         p.setAntiAlias(true);
   *         p.setStyle(SkPaint::kStroke_Style);
   *         p.setStrokeWidth(22);
   *
   *         SkRect r2 = SkRect::MakeXYWH(20, 140, 100, 100);
   *         // setting the height of this to 19 causes failure
   *         SkRect rect2 = SkRect::MakeXYWH(20, 120, 100, 19);
   *
   *         canvas->save();
   *         canvas->clipRect(rect2, true);
   *         canvas->drawRect(r2, p);
   *         canvas->restore();
   *
   *         p.setColor(SK_ColorBLUE);
   *         p.setStrokeWidth(2);
   *         canvas->drawRect(rect2, p);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
