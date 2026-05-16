package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class PathInteriorGM : public skiagm::GM {
 * public:
 *     PathInteriorGM() {
 *         this->setBGColor(0xFFDDDDDD);
 *     }
 *
 * protected:
 *     SkISize getISize() override { return SkISize::Make(770, 770); }
 *
 *     SkString getName() const override { return SkString("pathinterior"); }
 *
 *     void show(SkCanvas* canvas, const SkPath& path) {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         SkRect rect;
 * #if 0
 *         bool hasInterior = path.hasRectangularInterior(&rect);
 * #else
 *         bool hasInterior = false;
 * #endif
 *
 *         paint.setColor(hasInterior ? ToolUtils::color_to_565(0xFF8888FF) : SK_ColorGRAY);
 *         canvas->drawPath(path, paint);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setColor(SK_ColorRED);
 *         canvas->drawPath(path, paint);
 *
 *         if (hasInterior) {
 *             paint.setStyle(SkPaint::kFill_Style);
 *             paint.setColor(0x8800FF00);
 *             canvas->drawRect(rect, paint);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->translate(8.5f, 8.5f);
 *
 *         const SkRect rect = { 0, 0, 80, 80 };
 *         const SkScalar RAD = rect.width()/8;
 *
 *         int i = 0;
 *         for (int insetFirst = 0; insetFirst <= 1; ++insetFirst) {
 *             for (int doEvenOdd = 0; doEvenOdd <= 1; ++doEvenOdd) {
 *                 for (int outerRR = 0; outerRR <= 1; ++outerRR) {
 *                     for (int innerRR = 0; innerRR <= 1; ++innerRR) {
 *                         for (int outerCW = 0; outerCW <= 1; ++outerCW) {
 *                             for (int innerCW = 0; innerCW <= 1; ++innerCW) {
 *                                 SkPathBuilder builder(doEvenOdd ? SkPathFillType::kEvenOdd
 *                                                                 : SkPathFillType::kWinding);
 *                                 SkPathDirection outerDir = outerCW ? SkPathDirection::kCW : SkPathDirection::kCCW;
 *                                 SkPathDirection innerDir = innerCW ? SkPathDirection::kCW : SkPathDirection::kCCW;
 *
 *                                 SkRect r = insetFirst ? inset(rect) : rect;
 *                                 if (outerRR) {
 *                                     builder.addRRect(SkRRect::MakeRectXY(r, RAD, RAD), outerDir);
 *                                 } else {
 *                                     builder.addRect(r, outerDir);
 *                                 }
 *                                 r = insetFirst ? rect : inset(rect);
 *                                 if (innerRR) {
 *                                     builder.addRRect(SkRRect::MakeRectXY(r, RAD, RAD), innerDir);
 *                                 } else {
 *                                     builder.addRect(r, innerDir);
 *                                 }
 *
 *                                 SkScalar dx = (i / 8) * rect.width() * 6 / 5;
 *                                 SkScalar dy = (i % 8) * rect.height() * 6 / 5;
 *                                 i++;
 *                                 builder.offset(dx, dy);
 *
 *                                 this->show(canvas, builder.detach());
 *                             }
 *                         }
 *                     }
 *                 }
 *             }
 *         }
 *     }
 *
 * private:
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class PathInteriorGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(770, 770); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("pathinterior"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void show(SkCanvas* canvas, const SkPath& path) {
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         SkRect rect;
   * #if 0
   *         bool hasInterior = path.hasRectangularInterior(&rect);
   * #else
   *         bool hasInterior = false;
   * #endif
   *
   *         paint.setColor(hasInterior ? ToolUtils::color_to_565(0xFF8888FF) : SK_ColorGRAY);
   *         canvas->drawPath(path, paint);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setColor(SK_ColorRED);
   *         canvas->drawPath(path, paint);
   *
   *         if (hasInterior) {
   *             paint.setStyle(SkPaint::kFill_Style);
   *             paint.setColor(0x8800FF00);
   *             canvas->drawRect(rect, paint);
   *         }
   *     }
   * ```
   */
  protected fun show(canvas: SkCanvas?, path: SkPath) {
    TODO("Implement show")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->translate(8.5f, 8.5f);
   *
   *         const SkRect rect = { 0, 0, 80, 80 };
   *         const SkScalar RAD = rect.width()/8;
   *
   *         int i = 0;
   *         for (int insetFirst = 0; insetFirst <= 1; ++insetFirst) {
   *             for (int doEvenOdd = 0; doEvenOdd <= 1; ++doEvenOdd) {
   *                 for (int outerRR = 0; outerRR <= 1; ++outerRR) {
   *                     for (int innerRR = 0; innerRR <= 1; ++innerRR) {
   *                         for (int outerCW = 0; outerCW <= 1; ++outerCW) {
   *                             for (int innerCW = 0; innerCW <= 1; ++innerCW) {
   *                                 SkPathBuilder builder(doEvenOdd ? SkPathFillType::kEvenOdd
   *                                                                 : SkPathFillType::kWinding);
   *                                 SkPathDirection outerDir = outerCW ? SkPathDirection::kCW : SkPathDirection::kCCW;
   *                                 SkPathDirection innerDir = innerCW ? SkPathDirection::kCW : SkPathDirection::kCCW;
   *
   *                                 SkRect r = insetFirst ? inset(rect) : rect;
   *                                 if (outerRR) {
   *                                     builder.addRRect(SkRRect::MakeRectXY(r, RAD, RAD), outerDir);
   *                                 } else {
   *                                     builder.addRect(r, outerDir);
   *                                 }
   *                                 r = insetFirst ? rect : inset(rect);
   *                                 if (innerRR) {
   *                                     builder.addRRect(SkRRect::MakeRectXY(r, RAD, RAD), innerDir);
   *                                 } else {
   *                                     builder.addRect(r, innerDir);
   *                                 }
   *
   *                                 SkScalar dx = (i / 8) * rect.width() * 6 / 5;
   *                                 SkScalar dy = (i % 8) * rect.height() * 6 / 5;
   *                                 i++;
   *                                 builder.offset(dx, dy);
   *
   *                                 this->show(canvas, builder.detach());
   *                             }
   *                         }
   *                     }
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
