package org.skia.tests

import kotlin.Array
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PathOpsInverseGM : public GM {
 * public:
 *     PathOpsInverseGM() {
 *     }
 *
 * protected:
 *     void onOnceBeforeDraw() override {
 *         const unsigned oneColor   = ToolUtils::color_to_565(0xFF8080FF);
 *         const unsigned twoColor = 0x807F1f1f;
 *         SkColor blendColor = blend(oneColor, twoColor);
 *         makePaint(&fOnePaint, oneColor);
 *         makePaint(&fTwoPaint, twoColor);
 *         makePaint(&fOpPaint[kDifference_SkPathOp], oneColor);
 *         makePaint(&fOpPaint[kIntersect_SkPathOp], blendColor);
 *         makePaint(&fOpPaint[kUnion_SkPathOp], ToolUtils::color_to_565(0xFFc0FFc0));
 *         makePaint(&fOpPaint[kReverseDifference_SkPathOp], twoColor);
 *         makePaint(&fOpPaint[kXOR_SkPathOp], ToolUtils::color_to_565(0xFFa0FFe0));
 *         makePaint(&fOutlinePaint, 0xFF000000);
 *         fOutlinePaint.setStyle(SkPaint::kStroke_Style);
 *     }
 *
 *     SkColor blend(SkColor one, SkColor two) {
 *         SkBitmap temp;
 *         temp.allocN32Pixels(1, 1);
 *         SkCanvas canvas(temp);
 *         canvas.drawColor(one);
 *         canvas.drawColor(two);
 *         void* pixels = temp.getPixels();
 *         return *(SkColor*) pixels;
 *     }
 *
 *     void makePaint(SkPaint* paint, SkColor color) {
 *         paint->setAntiAlias(true);
 *         paint->setStyle(SkPaint::kFill_Style);
 *         paint->setColor(color);
 *     }
 *
 *     SkString getName() const override { return SkString("pathopsinverse"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1200, 900); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         int yPos = 0;
 *         for (int oneFill = 0; oneFill <= 1; ++oneFill) {
 *             SkPathFillType oneF = oneFill ? SkPathFillType::kInverseEvenOdd
 *                     : SkPathFillType::kEvenOdd;
 *             for (int twoFill = 0; twoFill <= 1; ++twoFill) {
 *                 SkPathFillType twoF = twoFill ? SkPathFillType::kInverseEvenOdd
 *                         : SkPathFillType::kEvenOdd;
 *                 SkPath one = SkPath::Rect({10, 10, 70, 70}).makeFillType(oneF);
 *                 SkPath two = SkPath::Rect({40, 40, 100, 100}).makeFillType(twoF);
 *                 canvas->save();
 *                 canvas->translate(0, SkIntToScalar(yPos));
 *                 canvas->clipRect(SkRect::MakeWH(110, 110), true);
 *                 canvas->drawPath(one, fOnePaint);
 *                 canvas->drawPath(one, fOutlinePaint);
 *                 canvas->drawPath(two, fTwoPaint);
 *                 canvas->drawPath(two, fOutlinePaint);
 *                 canvas->restore();
 *                 int xPos = 150;
 *                 for (int op = kDifference_SkPathOp; op <= kReverseDifference_SkPathOp; ++op) {
 *                     SkPath result = Op(one, two, (SkPathOp) op).value_or(SkPath());
 *                     canvas->save();
 *                     canvas->translate(SkIntToScalar(xPos), SkIntToScalar(yPos));
 *                     canvas->clipRect(SkRect::MakeWH(110, 110), true);
 *                     canvas->drawPath(result, fOpPaint[op]);
 *                     canvas->drawPath(result, fOutlinePaint);
 *                     canvas->restore();
 *                     xPos += 150;
 *                 }
 *                 yPos += 150;
 *             }
 *         }
 *     }
 *
 * private:
 *     SkPaint fOnePaint;
 *     SkPaint fTwoPaint;
 *     SkPaint fOutlinePaint;
 *     SkPaint fOpPaint[kReverseDifference_SkPathOp - kDifference_SkPathOp + 1];
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PathOpsInverseGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkPaint fOnePaint
   * ```
   */
  private var fOnePaint: SkPaint = TODO("Initialize fOnePaint")

  /**
   * C++ original:
   * ```cpp
   * SkPaint fTwoPaint
   * ```
   */
  private var fTwoPaint: SkPaint = TODO("Initialize fTwoPaint")

  /**
   * C++ original:
   * ```cpp
   * SkPaint fOutlinePaint
   * ```
   */
  private var fOutlinePaint: SkPaint = TODO("Initialize fOutlinePaint")

  /**
   * C++ original:
   * ```cpp
   * SkPaint fOpPaint[kReverseDifference_SkPathOp - kDifference_SkPathOp + 1]
   * ```
   */
  private var fOpPaint: Array<SkPaint> = TODO("Initialize fOpPaint")

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         const unsigned oneColor   = ToolUtils::color_to_565(0xFF8080FF);
   *         const unsigned twoColor = 0x807F1f1f;
   *         SkColor blendColor = blend(oneColor, twoColor);
   *         makePaint(&fOnePaint, oneColor);
   *         makePaint(&fTwoPaint, twoColor);
   *         makePaint(&fOpPaint[kDifference_SkPathOp], oneColor);
   *         makePaint(&fOpPaint[kIntersect_SkPathOp], blendColor);
   *         makePaint(&fOpPaint[kUnion_SkPathOp], ToolUtils::color_to_565(0xFFc0FFc0));
   *         makePaint(&fOpPaint[kReverseDifference_SkPathOp], twoColor);
   *         makePaint(&fOpPaint[kXOR_SkPathOp], ToolUtils::color_to_565(0xFFa0FFe0));
   *         makePaint(&fOutlinePaint, 0xFF000000);
   *         fOutlinePaint.setStyle(SkPaint::kStroke_Style);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor blend(SkColor one, SkColor two) {
   *         SkBitmap temp;
   *         temp.allocN32Pixels(1, 1);
   *         SkCanvas canvas(temp);
   *         canvas.drawColor(one);
   *         canvas.drawColor(two);
   *         void* pixels = temp.getPixels();
   *         return *(SkColor*) pixels;
   *     }
   * ```
   */
  protected fun blend(one: SkColor, two: SkColor): SkColor {
    TODO("Implement blend")
  }

  /**
   * C++ original:
   * ```cpp
   * void makePaint(SkPaint* paint, SkColor color) {
   *         paint->setAntiAlias(true);
   *         paint->setStyle(SkPaint::kFill_Style);
   *         paint->setColor(color);
   *     }
   * ```
   */
  protected fun makePaint(paint: SkPaint?, color: SkColor) {
    TODO("Implement makePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pathopsinverse"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1200, 900); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         int yPos = 0;
   *         for (int oneFill = 0; oneFill <= 1; ++oneFill) {
   *             SkPathFillType oneF = oneFill ? SkPathFillType::kInverseEvenOdd
   *                     : SkPathFillType::kEvenOdd;
   *             for (int twoFill = 0; twoFill <= 1; ++twoFill) {
   *                 SkPathFillType twoF = twoFill ? SkPathFillType::kInverseEvenOdd
   *                         : SkPathFillType::kEvenOdd;
   *                 SkPath one = SkPath::Rect({10, 10, 70, 70}).makeFillType(oneF);
   *                 SkPath two = SkPath::Rect({40, 40, 100, 100}).makeFillType(twoF);
   *                 canvas->save();
   *                 canvas->translate(0, SkIntToScalar(yPos));
   *                 canvas->clipRect(SkRect::MakeWH(110, 110), true);
   *                 canvas->drawPath(one, fOnePaint);
   *                 canvas->drawPath(one, fOutlinePaint);
   *                 canvas->drawPath(two, fTwoPaint);
   *                 canvas->drawPath(two, fOutlinePaint);
   *                 canvas->restore();
   *                 int xPos = 150;
   *                 for (int op = kDifference_SkPathOp; op <= kReverseDifference_SkPathOp; ++op) {
   *                     SkPath result = Op(one, two, (SkPathOp) op).value_or(SkPath());
   *                     canvas->save();
   *                     canvas->translate(SkIntToScalar(xPos), SkIntToScalar(yPos));
   *                     canvas->clipRect(SkRect::MakeWH(110, 110), true);
   *                     canvas->drawPath(result, fOpPaint[op]);
   *                     canvas->drawPath(result, fOutlinePaint);
   *                     canvas->restore();
   *                     xPos += 150;
   *                 }
   *                 yPos += 150;
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
