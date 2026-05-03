package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class SimpleBlurRoundRectGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("simpleblurroundrect"); }
 *
 *     SkISize getISize() override { return {1000, 500}; }
 *
 *     bool runAsBench() const override { return true; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->scale(1.5f, 1.5f);
 *         canvas->translate(50,50);
 *
 *         const float blurRadii[] = {1.f, 5.f, 10.f, 20.f};
 *         const float cornerRadii[] = {1.f, 5.f, 10.f, 20.f};
 *         const SkRect r = SkRect::MakeWH(25.f, 25.f);
 *         for (size_t row = 0; row < std::size(blurRadii); ++row) {
 *             SkAutoCanvasRestore autoRestore(canvas, true);
 *             canvas->translate(0, (r.height() + 50.f) * row);
 *             for (size_t pair = 0; pair < std::size(cornerRadii); ++pair) {
 *                 SkPaint paint;
 *                 paint.setColor(SK_ColorBLACK);
 *                 paint.setMaskFilter(SkMaskFilter::MakeBlur(
 *                         kNormal_SkBlurStyle, SkBlurMask::ConvertRadiusToSigma(blurRadii[row])));
 *                 SkRRect rrect;
 *                 rrect.setRectXY(r, cornerRadii[pair], cornerRadii[pair]);
 *
 *                 // Even-indexed columns are without a gradient
 *                 canvas->drawRRect(rrect, paint);
 *                 canvas->translate(r.width() + 50.f, 0);
 *
 *                 // Odd-indexed columns have a gradient
 *                 paint.setShader(MakeRadial());
 *                 canvas->drawRRect(rrect, paint);
 *                 canvas->translate(r.width() + 50.f, 0);
 *             }
 *         }
 *     }
 * }
 * ```
 */
public open class SimpleBlurRoundRectGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("simpleblurroundrect"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1000, 500}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  public override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->scale(1.5f, 1.5f);
   *         canvas->translate(50,50);
   *
   *         const float blurRadii[] = {1.f, 5.f, 10.f, 20.f};
   *         const float cornerRadii[] = {1.f, 5.f, 10.f, 20.f};
   *         const SkRect r = SkRect::MakeWH(25.f, 25.f);
   *         for (size_t row = 0; row < std::size(blurRadii); ++row) {
   *             SkAutoCanvasRestore autoRestore(canvas, true);
   *             canvas->translate(0, (r.height() + 50.f) * row);
   *             for (size_t pair = 0; pair < std::size(cornerRadii); ++pair) {
   *                 SkPaint paint;
   *                 paint.setColor(SK_ColorBLACK);
   *                 paint.setMaskFilter(SkMaskFilter::MakeBlur(
   *                         kNormal_SkBlurStyle, SkBlurMask::ConvertRadiusToSigma(blurRadii[row])));
   *                 SkRRect rrect;
   *                 rrect.setRectXY(r, cornerRadii[pair], cornerRadii[pair]);
   *
   *                 // Even-indexed columns are without a gradient
   *                 canvas->drawRRect(rrect, paint);
   *                 canvas->translate(r.width() + 50.f, 0);
   *
   *                 // Odd-indexed columns have a gradient
   *                 paint.setShader(MakeRadial());
   *                 canvas->drawRRect(rrect, paint);
   *                 canvas->translate(r.width() + 50.f, 0);
   *             }
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
