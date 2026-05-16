package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class HairModesGM : public GM {
 *         SkPaint fBGPaint;
 *
 *     protected:
 *         SkString getName() const override { return SkString("hairmodes"); }
 *
 *         SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *         void onOnceBeforeDraw() override {
 *             fBGPaint.setShader(make_bg_shader());
 *         }
 *
 *         void onDraw(SkCanvas* canvas) override {
 *             const SkRect bounds = SkRect::MakeWH(W, H);
 *             constexpr SkAlpha gAlphaValue[] = { 0xFF, 0x88, 0x88 };
 *
 *             canvas->translate(SkIntToScalar(4), SkIntToScalar(4));
 *
 *             for (int alpha = 0; alpha < 4; ++alpha) {
 *                 canvas->save();
 *                 canvas->save();
 *                 for (size_t i = 0; i < std::size(gModes); ++i) {
 *                     if (6 == i) {
 *                         canvas->restore();
 *                         canvas->translate(W * 5, 0);
 *                         canvas->save();
 *                     }
 *
 *                     canvas->drawRect(bounds, fBGPaint);
 *                     canvas->saveLayer(&bounds, nullptr);
 *                     SkScalar dy = drawCell(canvas, gModes[i],
 *                                            gAlphaValue[alpha & 1],
 *                                            gAlphaValue[alpha & 2]);
 *                     canvas->restore();
 *
 *                     canvas->translate(0, dy * 5 / 4);
 *                 }
 *                 canvas->restore();
 *                 canvas->restore();
 *                 canvas->translate(W * 5 / 4, 0);
 *             }
 *         }
 *
 *     private:
 *         using INHERITED = GM;
 *     }
 * ```
 */
public open class HairModesGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPaint fBGPaint
   * ```
   */
  private var fBGPaint: SkPaint = TODO("Initialize fBGPaint")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("hairmodes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(640, 480); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *             fBGPaint.setShader(make_bg_shader());
   *         }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *             const SkRect bounds = SkRect::MakeWH(W, H);
   *             constexpr SkAlpha gAlphaValue[] = { 0xFF, 0x88, 0x88 };
   *
   *             canvas->translate(SkIntToScalar(4), SkIntToScalar(4));
   *
   *             for (int alpha = 0; alpha < 4; ++alpha) {
   *                 canvas->save();
   *                 canvas->save();
   *                 for (size_t i = 0; i < std::size(gModes); ++i) {
   *                     if (6 == i) {
   *                         canvas->restore();
   *                         canvas->translate(W * 5, 0);
   *                         canvas->save();
   *                     }
   *
   *                     canvas->drawRect(bounds, fBGPaint);
   *                     canvas->saveLayer(&bounds, nullptr);
   *                     SkScalar dy = drawCell(canvas, gModes[i],
   *                                            gAlphaValue[alpha & 1],
   *                                            gAlphaValue[alpha & 2]);
   *                     canvas->restore();
   *
   *                     canvas->translate(0, dy * 5 / 4);
   *                 }
   *                 canvas->restore();
   *                 canvas->restore();
   *                 canvas->translate(W * 5 / 4, 0);
   *             }
   *         }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
