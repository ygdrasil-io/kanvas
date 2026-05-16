package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DRRectGM : public skiagm::GM {
 * public:
 *     DRRectGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("drrect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         SkRRect outers[4];
 *         // like squares/circles, to exercise fast-cases in GPU
 *         SkRect r = { 0, 0, 100, 100 };
 *         SkVector radii[4] = {
 *             { 0, 0 }, { 30, 1 }, { 10, 40 }, { 40, 40 }
 *         };
 *
 *         const SkScalar dx = r.width() + 16;
 *         const SkScalar dy = r.height() + 16;
 *
 *         outers[0].setRect(r);
 *         outers[1].setOval(r);
 *         outers[2].setRectXY(r, 20, 20);
 *         outers[3].setRectRadii(r, radii);
 *
 *         SkRRect inners[5];
 *         r.inset(25, 25);
 *
 *         inners[0].setEmpty();
 *         inners[1].setRect(r);
 *         inners[2].setOval(r);
 *         inners[3].setRectXY(r, 20, 20);
 *         inners[4].setRectRadii(r, radii);
 *
 *         canvas->translate(16, 16);
 *         for (size_t j = 0; j < std::size(inners); ++j) {
 *             for (size_t i = 0; i < std::size(outers); ++i) {
 *                 canvas->save();
 *                 canvas->translate(dx * j, dy * i);
 *                 canvas->drawDRRect(outers[i], inners[j], paint);
 *                 canvas->restore();
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class DRRectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("drrect"); }
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
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         SkRRect outers[4];
   *         // like squares/circles, to exercise fast-cases in GPU
   *         SkRect r = { 0, 0, 100, 100 };
   *         SkVector radii[4] = {
   *             { 0, 0 }, { 30, 1 }, { 10, 40 }, { 40, 40 }
   *         };
   *
   *         const SkScalar dx = r.width() + 16;
   *         const SkScalar dy = r.height() + 16;
   *
   *         outers[0].setRect(r);
   *         outers[1].setOval(r);
   *         outers[2].setRectXY(r, 20, 20);
   *         outers[3].setRectRadii(r, radii);
   *
   *         SkRRect inners[5];
   *         r.inset(25, 25);
   *
   *         inners[0].setEmpty();
   *         inners[1].setRect(r);
   *         inners[2].setOval(r);
   *         inners[3].setRectXY(r, 20, 20);
   *         inners[4].setRectRadii(r, radii);
   *
   *         canvas->translate(16, 16);
   *         for (size_t j = 0; j < std::size(inners); ++j) {
   *             for (size_t i = 0; i < std::size(outers); ++i) {
   *                 canvas->save();
   *                 canvas->translate(dx * j, dy * i);
   *                 canvas->drawDRRect(outers[i], inners[j], paint);
   *                 canvas->restore();
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
