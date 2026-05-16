package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class RRectGM : public skiagm::GM {
 * public:
 *     RRectGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("rrect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(820, 710); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr InsetProc insetProcs[] = {
 *             inset0, inset1, inset2, inset3
 *         };
 *
 *         SkRRect rrect[4];
 *         SkRect r = { 0, 0, 120, 100 };
 *         SkVector radii[4] = {
 *             { 0, 0 }, { 30, 1 }, { 10, 40 }, { 40, 40 }
 *         };
 *
 *         rrect[0].setRect(r);
 *         rrect[1].setOval(r);
 *         rrect[2].setRectXY(r, 20, 20);
 *         rrect[3].setRectRadii(r, radii);
 *
 *         canvas->translate(50.5f, 50.5f);
 *         for (size_t j = 0; j < std::size(insetProcs); ++j) {
 *             canvas->save();
 *             for (size_t i = 0; i < std::size(rrect); ++i) {
 *                 drawrr(canvas, rrect[i], insetProcs[j]);
 *                 canvas->translate(200, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, 170);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class RRectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("rrect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(820, 710); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         constexpr InsetProc insetProcs[] = {
   *             inset0, inset1, inset2, inset3
   *         };
   *
   *         SkRRect rrect[4];
   *         SkRect r = { 0, 0, 120, 100 };
   *         SkVector radii[4] = {
   *             { 0, 0 }, { 30, 1 }, { 10, 40 }, { 40, 40 }
   *         };
   *
   *         rrect[0].setRect(r);
   *         rrect[1].setOval(r);
   *         rrect[2].setRectXY(r, 20, 20);
   *         rrect[3].setRectRadii(r, radii);
   *
   *         canvas->translate(50.5f, 50.5f);
   *         for (size_t j = 0; j < std::size(insetProcs); ++j) {
   *             canvas->save();
   *             for (size_t i = 0; i < std::size(rrect); ++i) {
   *                 drawrr(canvas, rrect[i], insetProcs[j]);
   *                 canvas->translate(200, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, 170);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
