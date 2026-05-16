package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ThinStrokedRectsGM : public GM {
 * public:
 *     ThinStrokedRectsGM() {
 *         this->setBGColor(0xFF000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("thinstrokedrects"); }
 *
 *     SkISize getISize() override { return SkISize::Make(240, 320); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPaint paint;
 *         paint.setColor(SK_ColorWHITE);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setAntiAlias(true);
 *
 *         constexpr SkRect rect = { 0, 0, 10, 10 };
 *         constexpr SkRect rect2 = { 0, 0, 20, 20 };
 *
 *         constexpr SkScalar gStrokeWidths[] = {
 *             4, 2, 1, 0.5f, 0.25f, 0.125f, 0
 *         };
 *
 *         canvas->translate(5, 5);
 *         for (int i = 0; i < 8; ++i) {
 *             canvas->save();
 *             canvas->translate(i*0.125f, i*30.0f);
 *             for (size_t j = 0; j < std::size(gStrokeWidths); ++j) {
 *                 paint.setStrokeWidth(gStrokeWidths[j]);
 *                 canvas->drawRect(rect, paint);
 *                 canvas->translate(15, 0);
 *             }
 *             canvas->restore();
 *         }
 *
 *         // Draw a second time in red with a scale
 *         paint.setColor(SK_ColorRED);
 *         canvas->translate(0, 15);
 *         for (int i = 0; i < 8; ++i) {
 *             canvas->save();
 *             canvas->translate(i*0.125f, i*30.0f);
 *             canvas->scale(0.5f, 0.5f);
 *             for (size_t j = 0; j < std::size(gStrokeWidths); ++j) {
 *                 paint.setStrokeWidth(2.0f * gStrokeWidths[j]);
 *                 canvas->drawRect(rect2, paint);
 *                 canvas->translate(30, 0);
 *             }
 *             canvas->restore();
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ThinStrokedRectsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("thinstrokedrects"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(240, 320); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *
   *         SkPaint paint;
   *         paint.setColor(SK_ColorWHITE);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setAntiAlias(true);
   *
   *         constexpr SkRect rect = { 0, 0, 10, 10 };
   *         constexpr SkRect rect2 = { 0, 0, 20, 20 };
   *
   *         constexpr SkScalar gStrokeWidths[] = {
   *             4, 2, 1, 0.5f, 0.25f, 0.125f, 0
   *         };
   *
   *         canvas->translate(5, 5);
   *         for (int i = 0; i < 8; ++i) {
   *             canvas->save();
   *             canvas->translate(i*0.125f, i*30.0f);
   *             for (size_t j = 0; j < std::size(gStrokeWidths); ++j) {
   *                 paint.setStrokeWidth(gStrokeWidths[j]);
   *                 canvas->drawRect(rect, paint);
   *                 canvas->translate(15, 0);
   *             }
   *             canvas->restore();
   *         }
   *
   *         // Draw a second time in red with a scale
   *         paint.setColor(SK_ColorRED);
   *         canvas->translate(0, 15);
   *         for (int i = 0; i < 8; ++i) {
   *             canvas->save();
   *             canvas->translate(i*0.125f, i*30.0f);
   *             canvas->scale(0.5f, 0.5f);
   *             for (size_t j = 0; j < std::size(gStrokeWidths); ++j) {
   *                 paint.setStrokeWidth(2.0f * gStrokeWidths[j]);
   *                 canvas->drawRect(rect2, paint);
   *                 canvas->translate(30, 0);
   *             }
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
