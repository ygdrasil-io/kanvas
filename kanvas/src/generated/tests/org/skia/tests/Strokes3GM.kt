package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class Strokes3GM : public skiagm::GM {
 *     static SkPath make0(const SkRect& bounds, SkString* title) {
 *         title->set("CW CW");
 *         return SkPathBuilder()
 *                .addRect(bounds, SkPathDirection::kCW)
 *                .addRect(inset(bounds), SkPathDirection::kCW)
 *                .detach();
 *     }
 *
 *     static SkPath make1(const SkRect& bounds, SkString* title) {
 *         title->set("CW CCW");
 *         return SkPathBuilder()
 *                .addRect(bounds, SkPathDirection::kCW)
 *                .addRect(inset(bounds), SkPathDirection::kCCW)
 *                .detach();
 *     }
 *
 *     static SkPath make2(const SkRect& bounds, SkString* title) {
 *         title->set("CW CW");
 *         return SkPathBuilder()
 *                .addOval(bounds, SkPathDirection::kCW)
 *                .addOval(inset(bounds), SkPathDirection::kCW)
 *                .detach();
 *     }
 *
 *     static SkPath make3(const SkRect& bounds, SkString* title) {
 *         title->set("CW CCW");
 *         return SkPathBuilder()
 *                .addOval(bounds, SkPathDirection::kCW)
 *                .addOval(inset(bounds), SkPathDirection::kCCW)
 *                .detach();
 *     }
 *
 *     static SkPath make4(const SkRect& bounds, SkString* title) {
 *         title->set("CW CW");
 *
 *         SkRect r = bounds;
 *         r.inset(bounds.width() / 10, -bounds.height() / 10);
 *         return SkPathBuilder()
 *                .addRect(bounds, SkPathDirection::kCW)
 *                .addOval(r, SkPathDirection::kCW)
 *                .detach();
 *     }
 *
 *     static SkPath make5(const SkRect& bounds, SkString* title) {
 *         title->set("CW CCW");
 *
 *         SkRect r = bounds;
 *         r.inset(bounds.width() / 10, -bounds.height() / 10);
 *         return SkPathBuilder()
 *                .addRect(bounds, SkPathDirection::kCW)
 *                .addOval(r, SkPathDirection::kCCW)
 *                .detach();
 *     }
 *
 * public:
 *     Strokes3GM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("strokes3"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1500, 1500); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint origPaint;
 *         origPaint.setAntiAlias(true);
 *         origPaint.setStyle(SkPaint::kStroke_Style);
 *         SkPaint fillPaint(origPaint);
 *         fillPaint.setColor(SK_ColorRED);
 *         SkPaint strokePaint(origPaint);
 *         strokePaint.setColor(ToolUtils::color_to_565(0xFF4444FF));
 *
 *         SkPath (*procs[])(const SkRect&, SkString*) = {
 *             make0, make1, make2, make3, make4, make5
 *         };
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(80));
 *
 *         SkRect bounds = SkRect::MakeWH(SkIntToScalar(50), SkIntToScalar(50));
 *         SkScalar dx = bounds.width() * 4/3;
 *         SkScalar dy = bounds.height() * 5;
 *
 *         for (size_t i = 0; i < std::size(procs); ++i) {
 *             SkString str;
 *             SkPath orig = procs[i](bounds, &str);
 *
 *             canvas->save();
 *             for (int j = 0; j < 13; ++j) {
 *                 strokePaint.setStrokeWidth(SK_Scalar1 * j * j);
 *                 canvas->drawPath(orig, strokePaint);
 *                 canvas->drawPath(orig, origPaint);
 *                 SkPath fill = skpathutils::FillPathWithPaint(orig, strokePaint);
 *                 canvas->drawPath(fill, fillPaint);
 *                 canvas->translate(dx + strokePaint.getStrokeWidth(), 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, dy);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class Strokes3GM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokes3"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1500, 1500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint origPaint;
   *         origPaint.setAntiAlias(true);
   *         origPaint.setStyle(SkPaint::kStroke_Style);
   *         SkPaint fillPaint(origPaint);
   *         fillPaint.setColor(SK_ColorRED);
   *         SkPaint strokePaint(origPaint);
   *         strokePaint.setColor(ToolUtils::color_to_565(0xFF4444FF));
   *
   *         SkPath (*procs[])(const SkRect&, SkString*) = {
   *             make0, make1, make2, make3, make4, make5
   *         };
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(80));
   *
   *         SkRect bounds = SkRect::MakeWH(SkIntToScalar(50), SkIntToScalar(50));
   *         SkScalar dx = bounds.width() * 4/3;
   *         SkScalar dy = bounds.height() * 5;
   *
   *         for (size_t i = 0; i < std::size(procs); ++i) {
   *             SkString str;
   *             SkPath orig = procs[i](bounds, &str);
   *
   *             canvas->save();
   *             for (int j = 0; j < 13; ++j) {
   *                 strokePaint.setStrokeWidth(SK_Scalar1 * j * j);
   *                 canvas->drawPath(orig, strokePaint);
   *                 canvas->drawPath(orig, origPaint);
   *                 SkPath fill = skpathutils::FillPathWithPaint(orig, strokePaint);
   *                 canvas->drawPath(fill, fillPaint);
   *                 canvas->translate(dx + strokePaint.getStrokeWidth(), 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, dy);
   *         }
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
     * static SkPath make0(const SkRect& bounds, SkString* title) {
     *         title->set("CW CW");
     *         return SkPathBuilder()
     *                .addRect(bounds, SkPathDirection::kCW)
     *                .addRect(inset(bounds), SkPathDirection::kCW)
     *                .detach();
     *     }
     * ```
     */
    private fun make0(bounds: SkRect, title: String?): SkPath {
      TODO("Implement make0")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath make1(const SkRect& bounds, SkString* title) {
     *         title->set("CW CCW");
     *         return SkPathBuilder()
     *                .addRect(bounds, SkPathDirection::kCW)
     *                .addRect(inset(bounds), SkPathDirection::kCCW)
     *                .detach();
     *     }
     * ```
     */
    private fun make1(bounds: SkRect, title: String?): SkPath {
      TODO("Implement make1")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath make2(const SkRect& bounds, SkString* title) {
     *         title->set("CW CW");
     *         return SkPathBuilder()
     *                .addOval(bounds, SkPathDirection::kCW)
     *                .addOval(inset(bounds), SkPathDirection::kCW)
     *                .detach();
     *     }
     * ```
     */
    private fun make2(bounds: SkRect, title: String?): SkPath {
      TODO("Implement make2")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath make3(const SkRect& bounds, SkString* title) {
     *         title->set("CW CCW");
     *         return SkPathBuilder()
     *                .addOval(bounds, SkPathDirection::kCW)
     *                .addOval(inset(bounds), SkPathDirection::kCCW)
     *                .detach();
     *     }
     * ```
     */
    private fun make3(bounds: SkRect, title: String?): SkPath {
      TODO("Implement make3")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath make4(const SkRect& bounds, SkString* title) {
     *         title->set("CW CW");
     *
     *         SkRect r = bounds;
     *         r.inset(bounds.width() / 10, -bounds.height() / 10);
     *         return SkPathBuilder()
     *                .addRect(bounds, SkPathDirection::kCW)
     *                .addOval(r, SkPathDirection::kCW)
     *                .detach();
     *     }
     * ```
     */
    private fun make4(bounds: SkRect, title: String?): SkPath {
      TODO("Implement make4")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPath make5(const SkRect& bounds, SkString* title) {
     *         title->set("CW CCW");
     *
     *         SkRect r = bounds;
     *         r.inset(bounds.width() / 10, -bounds.height() / 10);
     *         return SkPathBuilder()
     *                .addRect(bounds, SkPathDirection::kCW)
     *                .addOval(r, SkPathDirection::kCCW)
     *                .detach();
     *     }
     * ```
     */
    private fun make5(bounds: SkRect, title: String?): SkPath {
      TODO("Implement make5")
    }
  }
}
