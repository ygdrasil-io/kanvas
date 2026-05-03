package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class CircularClipsGM : public skiagm::GM {
 *     SkScalar fX1, fX2, fY, fR;
 *     SkPath   fCircle1, fCircle2;
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         fX1 = 80;
 *         fX2 = 120;
 *         fY = 50;
 *         fR = 40;
 *
 *         fCircle1 = SkPath::Circle(fX1, fY, fR, SkPathDirection::kCW);
 *         fCircle2 = SkPath::Circle(fX2, fY, fR, SkPathDirection::kCW);
 *     }
 *
 *
 *     bool runAsBench() const override { return true; }
 *
 *     SkString getName() const override { return SkString("circular-clips"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 200); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkClipOp ops[] = {
 *             SkClipOp::kDifference,
 *             SkClipOp::kIntersect
 *         };
 *
 *         SkRect rect = SkRect::MakeLTRB(fX1 - fR, fY - fR, fX2 + fR, fY + fR);
 *
 *         SkPaint fillPaint;
 *
 *         // Giant background circular clips (AA, non-inverted, replace/isect)
 *         fillPaint.setColor(0x80808080);
 *         canvas->save();
 *         canvas->scale(10, 10);
 *         canvas->translate(-((fX1 + fX2)/2 - fR), -(fY - 2*fR/3));
 *         canvas->clipPath(fCircle1, true);
 *         canvas->clipPath(fCircle2, true);
 *
 *         canvas->drawRect(rect, fillPaint);
 *
 *         canvas->restore();
 *
 *         fillPaint.setColor(0xFF000000);
 *
 *         for (size_t i = 0; i < 4; i++) {
 *             fCircle1.toggleInverseFillType();
 *             if (i % 2 == 0) {
 *                 fCircle2.toggleInverseFillType();
 *             }
 *
 *             canvas->save();
 *             for (size_t op = 0; op < std::size(ops); op++) {
 *                 canvas->save();
 *
 *                 canvas->clipPath(fCircle1);
 *                 canvas->clipPath(fCircle2, ops[op]);
 *
 *                 canvas->drawRect(rect, fillPaint);
 *
 *                 canvas->restore();
 *                 canvas->translate(0, 2 * fY);
 *             }
 *             canvas->restore();
 *             canvas->translate(fX1 + fX2, 0);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class CircularClipsGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fX1
   * ```
   */
  private var fX1: SkScalar = TODO("Initialize fX1")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fX1, fX2
   * ```
   */
  private var fX2: SkScalar = TODO("Initialize fX2")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fX1, fX2, fY
   * ```
   */
  private var fY: SkScalar = TODO("Initialize fY")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fX1, fX2, fY, fR
   * ```
   */
  private var fR: SkScalar = TODO("Initialize fR")

  /**
   * C++ original:
   * ```cpp
   * SkPath   fCircle1
   * ```
   */
  private var fCircle1: SkPath = TODO("Initialize fCircle1")

  /**
   * C++ original:
   * ```cpp
   * SkPath   fCircle1, fCircle2
   * ```
   */
  private var fCircle2: SkPath = TODO("Initialize fCircle2")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fX1 = 80;
   *         fX2 = 120;
   *         fY = 50;
   *         fR = 40;
   *
   *         fCircle1 = SkPath::Circle(fX1, fY, fR, SkPathDirection::kCW);
   *         fCircle2 = SkPath::Circle(fX2, fY, fR, SkPathDirection::kCW);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("circular-clips"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkClipOp ops[] = {
   *             SkClipOp::kDifference,
   *             SkClipOp::kIntersect
   *         };
   *
   *         SkRect rect = SkRect::MakeLTRB(fX1 - fR, fY - fR, fX2 + fR, fY + fR);
   *
   *         SkPaint fillPaint;
   *
   *         // Giant background circular clips (AA, non-inverted, replace/isect)
   *         fillPaint.setColor(0x80808080);
   *         canvas->save();
   *         canvas->scale(10, 10);
   *         canvas->translate(-((fX1 + fX2)/2 - fR), -(fY - 2*fR/3));
   *         canvas->clipPath(fCircle1, true);
   *         canvas->clipPath(fCircle2, true);
   *
   *         canvas->drawRect(rect, fillPaint);
   *
   *         canvas->restore();
   *
   *         fillPaint.setColor(0xFF000000);
   *
   *         for (size_t i = 0; i < 4; i++) {
   *             fCircle1.toggleInverseFillType();
   *             if (i % 2 == 0) {
   *                 fCircle2.toggleInverseFillType();
   *             }
   *
   *             canvas->save();
   *             for (size_t op = 0; op < std::size(ops); op++) {
   *                 canvas->save();
   *
   *                 canvas->clipPath(fCircle1);
   *                 canvas->clipPath(fCircle2, ops[op]);
   *
   *                 canvas->drawRect(rect, fillPaint);
   *
   *                 canvas->restore();
   *                 canvas->translate(0, 2 * fY);
   *             }
   *             canvas->restore();
   *             canvas->translate(fX1 + fX2, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
