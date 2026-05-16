package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class StrokeRectAnisotropicGM : public skiagm::GM {
 * public:
 *     StrokeRectAnisotropicGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("strokerect_anisotropic"); }
 *
 *     SkISize getISize() override { return SkISize::Make(160, 160); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *
 *         SkPaint aaPaint;
 *         aaPaint.setColor(SkColorSetARGB(255, 0, 0, 0));
 *         aaPaint.setAntiAlias(true);
 *         aaPaint.setStrokeWidth(10);
 *         aaPaint.setStyle(SkPaint::kStroke_Style);
 *
 *         SkPaint bwPaint;
 *         bwPaint.setColor(SkColorSetARGB(255, 0, 0, 0));
 *         bwPaint.setStrokeWidth(10);
 *         bwPaint.setStyle(SkPaint::kStroke_Style);
 *
 *         // The two miter columns
 *         draw_sqooshed_rect(canvas, {  20.0f, 40.5f }, aaPaint);  // whole pixels
 *         draw_sqooshed_rect(canvas, {  20.0f, 110.5f }, bwPaint); // whole pixels
 *
 *         draw_sqooshed_rect(canvas, {  60.5f, 40.0f }, aaPaint);  // half pixels
 *         draw_sqooshed_rect(canvas, {  60.5f, 110.0f }, bwPaint); // half pixels
 *
 *         aaPaint.setStrokeJoin(SkPaint::kBevel_Join);
 *         bwPaint.setStrokeJoin(SkPaint::kBevel_Join);
 *
 *         // The two bevel columns
 *         draw_sqooshed_rect(canvas, { 100.0f, 40.5f }, aaPaint);  // whole pixels
 *         draw_sqooshed_rect(canvas, { 100.0f, 110.5f }, bwPaint); // whole pixels
 *
 *         draw_sqooshed_rect(canvas, { 140.5f, 40.0f }, aaPaint);  // half pixels
 *         draw_sqooshed_rect(canvas, { 140.5f, 110.0f }, bwPaint); // half pixels
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class StrokeRectAnisotropicGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokerect_anisotropic"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(160, 160); }
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
   *         SkPaint aaPaint;
   *         aaPaint.setColor(SkColorSetARGB(255, 0, 0, 0));
   *         aaPaint.setAntiAlias(true);
   *         aaPaint.setStrokeWidth(10);
   *         aaPaint.setStyle(SkPaint::kStroke_Style);
   *
   *         SkPaint bwPaint;
   *         bwPaint.setColor(SkColorSetARGB(255, 0, 0, 0));
   *         bwPaint.setStrokeWidth(10);
   *         bwPaint.setStyle(SkPaint::kStroke_Style);
   *
   *         // The two miter columns
   *         draw_sqooshed_rect(canvas, {  20.0f, 40.5f }, aaPaint);  // whole pixels
   *         draw_sqooshed_rect(canvas, {  20.0f, 110.5f }, bwPaint); // whole pixels
   *
   *         draw_sqooshed_rect(canvas, {  60.5f, 40.0f }, aaPaint);  // half pixels
   *         draw_sqooshed_rect(canvas, {  60.5f, 110.0f }, bwPaint); // half pixels
   *
   *         aaPaint.setStrokeJoin(SkPaint::kBevel_Join);
   *         bwPaint.setStrokeJoin(SkPaint::kBevel_Join);
   *
   *         // The two bevel columns
   *         draw_sqooshed_rect(canvas, { 100.0f, 40.5f }, aaPaint);  // whole pixels
   *         draw_sqooshed_rect(canvas, { 100.0f, 110.5f }, bwPaint); // whole pixels
   *
   *         draw_sqooshed_rect(canvas, { 140.5f, 40.0f }, aaPaint);  // half pixels
   *         draw_sqooshed_rect(canvas, { 140.5f, 110.0f }, bwPaint); // half pixels
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
