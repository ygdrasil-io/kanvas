package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkPathFillType
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class EmptyPathGM : public GM {
 *     SkString getName() const override { return SkString("emptypath"); }
 *
 *     SkISize getISize() override { return {600, 280}; }
 *
 *     void drawEmpty(SkCanvas* canvas,
 *                     SkColor color,
 *                     const SkRect& clip,
 *                     SkPaint::Style style,
 *                     SkPathFillType fill) {
 *         SkPath path;
 *         path.setFillType(fill);
 *         SkPaint paint;
 *         paint.setColor(color);
 *         paint.setStyle(style);
 *         canvas->save();
 *         canvas->clipRect(clip);
 *         canvas->drawPath(path, paint);
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         struct FillAndName {
 *             SkPathFillType fFill;
 *             const char*      fName;
 *         };
 *         constexpr FillAndName gFills[] = {
 *             {SkPathFillType::kWinding, "Winding"},
 *             {SkPathFillType::kEvenOdd, "Even / Odd"},
 *             {SkPathFillType::kInverseWinding, "Inverse Winding"},
 *             {SkPathFillType::kInverseEvenOdd, "Inverse Even / Odd"},
 *         };
 *         struct StyleAndName {
 *             SkPaint::Style fStyle;
 *             const char*    fName;
 *         };
 *         constexpr StyleAndName gStyles[] = {
 *             {SkPaint::kFill_Style, "Fill"},
 *             {SkPaint::kStroke_Style, "Stroke"},
 *             {SkPaint::kStrokeAndFill_Style, "Stroke And Fill"},
 *         };
 *
 *         SkFont     font(ToolUtils::DefaultPortableTypeface(), 15);
 *         const char title[] = "Empty Paths Drawn Into Rectangle Clips With "
 *                              "Indicated Style and Fill";
 *         canvas->drawString(title, 20.0f, 20.0f, font, SkPaint());
 *
 *         SkRandom rand;
 *         SkRect rect = SkRect::MakeWH(100*SK_Scalar1, 30*SK_Scalar1);
 *         int i = 0;
 *         canvas->save();
 *         canvas->translate(10 * SK_Scalar1, 0);
 *         canvas->save();
 *         for (size_t style = 0; style < std::size(gStyles); ++style) {
 *             for (size_t fill = 0; fill < std::size(gFills); ++fill) {
 *                 if (0 == i % 4) {
 *                     canvas->restore();
 *                     canvas->translate(0, rect.height() + 40 * SK_Scalar1);
 *                     canvas->save();
 *                 } else {
 *                     canvas->translate(rect.width() + 40 * SK_Scalar1, 0);
 *                 }
 *                 ++i;
 *
 *
 *                 SkColor color = rand.nextU();
 *                 color = 0xff000000 | color; // force solid
 *                 color         = ToolUtils::color_to_565(color);
 *                 this->drawEmpty(canvas, color, rect,
 *                                 gStyles[style].fStyle, gFills[fill].fFill);
 *
 *                 SkPaint rectPaint;
 *                 rectPaint.setColor(SK_ColorBLACK);
 *                 rectPaint.setStyle(SkPaint::kStroke_Style);
 *                 rectPaint.setStrokeWidth(-1);
 *                 rectPaint.setAntiAlias(true);
 *                 canvas->drawRect(rect, rectPaint);
 *
 *                 SkPaint labelPaint;
 *                 labelPaint.setColor(color);
 *                 SkFont labelFont(ToolUtils::DefaultPortableTypeface(), 12);
 *                 canvas->drawString(gStyles[style].fName, 0, rect.height() + 15.0f,
 *                                    labelFont, labelPaint);
 *                 canvas->drawString(gFills[fill].fName, 0, rect.height() + 28.0f,
 *                                    labelFont, labelPaint);
 *             }
 *         }
 *         canvas->restore();
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class EmptyPathGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("emptypath"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {600, 280}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawEmpty(SkCanvas* canvas,
   *                     SkColor color,
   *                     const SkRect& clip,
   *                     SkPaint::Style style,
   *                     SkPathFillType fill) {
   *         SkPath path;
   *         path.setFillType(fill);
   *         SkPaint paint;
   *         paint.setColor(color);
   *         paint.setStyle(style);
   *         canvas->save();
   *         canvas->clipRect(clip);
   *         canvas->drawPath(path, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawEmpty(
    canvas: SkCanvas?,
    color: SkColor,
    clip: SkRect,
    style: SkPaint.Style,
    fill: SkPathFillType,
  ) {
    TODO("Implement drawEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         struct FillAndName {
   *             SkPathFillType fFill;
   *             const char*      fName;
   *         };
   *         constexpr FillAndName gFills[] = {
   *             {SkPathFillType::kWinding, "Winding"},
   *             {SkPathFillType::kEvenOdd, "Even / Odd"},
   *             {SkPathFillType::kInverseWinding, "Inverse Winding"},
   *             {SkPathFillType::kInverseEvenOdd, "Inverse Even / Odd"},
   *         };
   *         struct StyleAndName {
   *             SkPaint::Style fStyle;
   *             const char*    fName;
   *         };
   *         constexpr StyleAndName gStyles[] = {
   *             {SkPaint::kFill_Style, "Fill"},
   *             {SkPaint::kStroke_Style, "Stroke"},
   *             {SkPaint::kStrokeAndFill_Style, "Stroke And Fill"},
   *         };
   *
   *         SkFont     font(ToolUtils::DefaultPortableTypeface(), 15);
   *         const char title[] = "Empty Paths Drawn Into Rectangle Clips With "
   *                              "Indicated Style and Fill";
   *         canvas->drawString(title, 20.0f, 20.0f, font, SkPaint());
   *
   *         SkRandom rand;
   *         SkRect rect = SkRect::MakeWH(100*SK_Scalar1, 30*SK_Scalar1);
   *         int i = 0;
   *         canvas->save();
   *         canvas->translate(10 * SK_Scalar1, 0);
   *         canvas->save();
   *         for (size_t style = 0; style < std::size(gStyles); ++style) {
   *             for (size_t fill = 0; fill < std::size(gFills); ++fill) {
   *                 if (0 == i % 4) {
   *                     canvas->restore();
   *                     canvas->translate(0, rect.height() + 40 * SK_Scalar1);
   *                     canvas->save();
   *                 } else {
   *                     canvas->translate(rect.width() + 40 * SK_Scalar1, 0);
   *                 }
   *                 ++i;
   *
   *
   *                 SkColor color = rand.nextU();
   *                 color = 0xff000000 | color; // force solid
   *                 color         = ToolUtils::color_to_565(color);
   *                 this->drawEmpty(canvas, color, rect,
   *                                 gStyles[style].fStyle, gFills[fill].fFill);
   *
   *                 SkPaint rectPaint;
   *                 rectPaint.setColor(SK_ColorBLACK);
   *                 rectPaint.setStyle(SkPaint::kStroke_Style);
   *                 rectPaint.setStrokeWidth(-1);
   *                 rectPaint.setAntiAlias(true);
   *                 canvas->drawRect(rect, rectPaint);
   *
   *                 SkPaint labelPaint;
   *                 labelPaint.setColor(color);
   *                 SkFont labelFont(ToolUtils::DefaultPortableTypeface(), 12);
   *                 canvas->drawString(gStyles[style].fName, 0, rect.height() + 15.0f,
   *                                    labelFont, labelPaint);
   *                 canvas->drawString(gFills[fill].fName, 0, rect.height() + 28.0f,
   *                                    labelFont, labelPaint);
   *             }
   *         }
   *         canvas->restore();
   *         canvas->restore();
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
