package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkPathFillType
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class CubicClosePathGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("cubicclosepath"); }
 *
 *     SkISize getISize() override { return {1240, 390}; }
 *
 *     void drawPath(SkPath& path,SkCanvas* canvas,SkColor color,
 *                   const SkRect& clip,SkPaint::Cap cap, SkPaint::Join join,
 *                   SkPaint::Style style, SkPathFillType fill,
 *                   SkScalar strokeWidth) {
 *         path.setFillType(fill);
 *         SkPaint paint;
 *         paint.setStrokeCap(cap);
 *         paint.setStrokeWidth(strokeWidth);
 *         paint.setStrokeJoin(join);
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
 *         struct CapAndName {
 *             SkPaint::Cap  fCap;
 *             SkPaint::Join fJoin;
 *             const char*   fName;
 *         };
 *         constexpr CapAndName gCaps[] = {
 *             {SkPaint::kButt_Cap, SkPaint::kBevel_Join, "Butt"},
 *             {SkPaint::kRound_Cap, SkPaint::kRound_Join, "Round"},
 *             {SkPaint::kSquare_Cap, SkPaint::kBevel_Join, "Square"}
 *         };
 *         struct PathAndName {
 *             SkPath      fPath;
 *             const char* fName;
 *         };
 *         PathAndName path;
 *         path.fPath = SkPathBuilder()
 *                      .moveTo(25*SK_Scalar1, 10*SK_Scalar1)
 *                      .cubicTo(40*SK_Scalar1, 20*SK_Scalar1,
 *                            60*SK_Scalar1, 20*SK_Scalar1,
 *                            75*SK_Scalar1, 10*SK_Scalar1)
 *                      .close()
 *                      .detach();
 *         path.fName = "moveTo-cubic-close";
 *
 *         SkPaint titlePaint;
 *         titlePaint.setColor(SK_ColorBLACK);
 *         titlePaint.setAntiAlias(true);
 *         SkFont     font(ToolUtils::DefaultPortableTypeface(), 15);
 *         const char title[] = "Cubic Closed Drawn Into Rectangle Clips With "
 *                              "Indicated Style, Fill and Linecaps, with stroke width 10";
 *         canvas->drawString(title, 20, 20, font, titlePaint);
 *
 *         SkRandom rand;
 *         SkRect rect = SkRect::MakeWH(100*SK_Scalar1, 30*SK_Scalar1);
 *         canvas->save();
 *         canvas->translate(10 * SK_Scalar1, 30 * SK_Scalar1);
 *         canvas->save();
 *         for (size_t cap = 0; cap < std::size(gCaps); ++cap) {
 *             if (0 < cap) {
 *                 canvas->translate((rect.width() + 40 * SK_Scalar1) * std::size(gStyles), 0);
 *             }
 *             canvas->save();
 *             for (size_t fill = 0; fill < std::size(gFills); ++fill) {
 *                 if (0 < fill) {
 *                     canvas->translate(0, rect.height() + 40 * SK_Scalar1);
 *                 }
 *                 canvas->save();
 *                 for (size_t style = 0; style < std::size(gStyles); ++style) {
 *                     if (0 < style) {
 *                         canvas->translate(rect.width() + 40 * SK_Scalar1, 0);
 *                     }
 *
 *                     SkColor color = 0xff007000;
 *                     this->drawPath(path.fPath, canvas, color, rect,
 *                                     gCaps[cap].fCap, gCaps[cap].fJoin, gStyles[style].fStyle,
 *                                     gFills[fill].fFill, SK_Scalar1*10);
 *
 *                     SkPaint rectPaint;
 *                     rectPaint.setColor(SK_ColorBLACK);
 *                     rectPaint.setStyle(SkPaint::kStroke_Style);
 *                     rectPaint.setStrokeWidth(-1);
 *                     rectPaint.setAntiAlias(true);
 *                     canvas->drawRect(rect, rectPaint);
 *
 *                     SkPaint labelPaint;
 *                     labelPaint.setColor(color);
 *                     labelPaint.setAntiAlias(true);
 *                     font.setSize(10);
 *                     canvas->drawString(gStyles[style].fName, 0, rect.height() + 12, font, labelPaint);
 *                     canvas->drawString(gFills[fill].fName, 0, rect.height() + 24, font, labelPaint);
 *                     canvas->drawString(gCaps[cap].fName, 0, rect.height() + 36, font, labelPaint);
 *                 }
 *                 canvas->restore();
 *             }
 *             canvas->restore();
 *         }
 *         canvas->restore();
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public open class CubicClosePathGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("cubicclosepath"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1240, 390}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawPath(SkPath& path,SkCanvas* canvas,SkColor color,
   *                   const SkRect& clip,SkPaint::Cap cap, SkPaint::Join join,
   *                   SkPaint::Style style, SkPathFillType fill,
   *                   SkScalar strokeWidth) {
   *         path.setFillType(fill);
   *         SkPaint paint;
   *         paint.setStrokeCap(cap);
   *         paint.setStrokeWidth(strokeWidth);
   *         paint.setStrokeJoin(join);
   *         paint.setColor(color);
   *         paint.setStyle(style);
   *         canvas->save();
   *         canvas->clipRect(clip);
   *         canvas->drawPath(path, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawPath(
    path: SkPath,
    canvas: SkCanvas?,
    color: SkColor,
    clip: SkRect,
    cap: SkPaint.Cap,
    join: SkPaint.Join,
    style: SkPaint.Style,
    fill: SkPathFillType,
    strokeWidth: SkScalar,
  ) {
    TODO("Implement drawPath")
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
   *         struct CapAndName {
   *             SkPaint::Cap  fCap;
   *             SkPaint::Join fJoin;
   *             const char*   fName;
   *         };
   *         constexpr CapAndName gCaps[] = {
   *             {SkPaint::kButt_Cap, SkPaint::kBevel_Join, "Butt"},
   *             {SkPaint::kRound_Cap, SkPaint::kRound_Join, "Round"},
   *             {SkPaint::kSquare_Cap, SkPaint::kBevel_Join, "Square"}
   *         };
   *         struct PathAndName {
   *             SkPath      fPath;
   *             const char* fName;
   *         };
   *         PathAndName path;
   *         path.fPath = SkPathBuilder()
   *                      .moveTo(25*SK_Scalar1, 10*SK_Scalar1)
   *                      .cubicTo(40*SK_Scalar1, 20*SK_Scalar1,
   *                            60*SK_Scalar1, 20*SK_Scalar1,
   *                            75*SK_Scalar1, 10*SK_Scalar1)
   *                      .close()
   *                      .detach();
   *         path.fName = "moveTo-cubic-close";
   *
   *         SkPaint titlePaint;
   *         titlePaint.setColor(SK_ColorBLACK);
   *         titlePaint.setAntiAlias(true);
   *         SkFont     font(ToolUtils::DefaultPortableTypeface(), 15);
   *         const char title[] = "Cubic Closed Drawn Into Rectangle Clips With "
   *                              "Indicated Style, Fill and Linecaps, with stroke width 10";
   *         canvas->drawString(title, 20, 20, font, titlePaint);
   *
   *         SkRandom rand;
   *         SkRect rect = SkRect::MakeWH(100*SK_Scalar1, 30*SK_Scalar1);
   *         canvas->save();
   *         canvas->translate(10 * SK_Scalar1, 30 * SK_Scalar1);
   *         canvas->save();
   *         for (size_t cap = 0; cap < std::size(gCaps); ++cap) {
   *             if (0 < cap) {
   *                 canvas->translate((rect.width() + 40 * SK_Scalar1) * std::size(gStyles), 0);
   *             }
   *             canvas->save();
   *             for (size_t fill = 0; fill < std::size(gFills); ++fill) {
   *                 if (0 < fill) {
   *                     canvas->translate(0, rect.height() + 40 * SK_Scalar1);
   *                 }
   *                 canvas->save();
   *                 for (size_t style = 0; style < std::size(gStyles); ++style) {
   *                     if (0 < style) {
   *                         canvas->translate(rect.width() + 40 * SK_Scalar1, 0);
   *                     }
   *
   *                     SkColor color = 0xff007000;
   *                     this->drawPath(path.fPath, canvas, color, rect,
   *                                     gCaps[cap].fCap, gCaps[cap].fJoin, gStyles[style].fStyle,
   *                                     gFills[fill].fFill, SK_Scalar1*10);
   *
   *                     SkPaint rectPaint;
   *                     rectPaint.setColor(SK_ColorBLACK);
   *                     rectPaint.setStyle(SkPaint::kStroke_Style);
   *                     rectPaint.setStrokeWidth(-1);
   *                     rectPaint.setAntiAlias(true);
   *                     canvas->drawRect(rect, rectPaint);
   *
   *                     SkPaint labelPaint;
   *                     labelPaint.setColor(color);
   *                     labelPaint.setAntiAlias(true);
   *                     font.setSize(10);
   *                     canvas->drawString(gStyles[style].fName, 0, rect.height() + 12, font, labelPaint);
   *                     canvas->drawString(gFills[fill].fName, 0, rect.height() + 24, font, labelPaint);
   *                     canvas->drawString(gCaps[cap].fName, 0, rect.height() + 36, font, labelPaint);
   *                 }
   *                 canvas->restore();
   *             }
   *             canvas->restore();
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
