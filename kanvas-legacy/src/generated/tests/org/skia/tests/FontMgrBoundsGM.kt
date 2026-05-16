package org.skia.tests

import kotlin.Boolean
import kotlin.Float
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.tools.SkMetaData

/**
 * C++ original:
 * ```cpp
 * class FontMgrBoundsGM : public skiagm::GM {
 * public:
 *     FontMgrBoundsGM(float scale, float skew) : fScaleX(scale) , fSkewX(skew) {}
 *
 * private:
 *     SkString getName() const override {
 *         if (fScaleX != 1 || fSkewX != 0) {
 *             return SkStringPrintf("fontmgr_bounds_%g_%g", fScaleX, fSkewX);
 *         }
 *         return SkString("fontmgr_bounds");
 *     }
 *
 *     void onOnceBeforeDraw() override { fFM = ToolUtils::TestFontMgr(); }
 *
 *     bool onGetControls(SkMetaData* controls) override {
 *         controls->setBool("Label Bounds", fLabelBounds);
 *         return true;
 *     }
 *
 *     void onSetControls(const SkMetaData& controls) override {
 *         controls.findBool("Label Bounds", &fLabelBounds);
 *     }
 *
 *     static SkRect show_bounds(SkCanvas* canvas, const SkFont& font, SkScalar x, SkScalar y,
 *                               SkColor boundsColor, bool labelBounds)
 *     {
 *         SkGlyphID left = 0, right = 0, top = 0, bottom = 0;
 *         SkRect min = SkRect::MakeLTRB(SK_ScalarInfinity, SK_ScalarInfinity,
 *                                       SK_ScalarNegativeInfinity, SK_ScalarNegativeInfinity);
 *         {
 *             int numGlyphs = font.getTypeface()->countGlyphs();
 *             for (int i = 0; i < numGlyphs; ++i) {
 *                 SkGlyphID glyphId = i;
 *                 SkRect cur = font.getBounds(glyphId, nullptr);
 *                 if (cur.fLeft   < min.fLeft  ) { min.fLeft   = cur.fLeft;   left   = i; }
 *                 if (cur.fTop    < min.fTop   ) { min.fTop    = cur.fTop ;   top    = i; }
 *                 if (min.fRight  < cur.fRight ) { min.fRight  = cur.fRight;  right  = i; }
 *                 if (min.fBottom < cur.fBottom) { min.fBottom = cur.fBottom; bottom = i; }
 *             }
 *         }
 *
 *         SkRect fontBounds = SkFontPriv::GetFontBounds(font);
 *
 *         SkRect drawBounds = min;
 *         drawBounds.join(fontBounds);
 *
 *         SkAutoCanvasRestore acr(canvas, true);
 *         canvas->translate(x - drawBounds.left(), y);
 *
 *         SkPaint boundsPaint;
 *         boundsPaint.setAntiAlias(true);
 *         boundsPaint.setColor(boundsColor);
 *         boundsPaint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawRect(fontBounds, boundsPaint);
 *
 *         const SkScalar intervals[] = { 10.f, 10.f };
 *         boundsPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0.f));
 *         canvas->drawRect(min, boundsPaint);
 *
 *         SkFontMetrics fm;
 *         font.getMetrics(&fm);
 *         SkPaint metricsPaint(boundsPaint);
 *         metricsPaint.setStyle(SkPaint::kFill_Style);
 *         metricsPaint.setAlphaf(0.25f);
 *         if ((fm.fFlags & SkFontMetrics::kUnderlinePositionIsValid_Flag) &&
 *             (fm.fFlags & SkFontMetrics::kUnderlineThicknessIsValid_Flag))
 *         {
 *             SkRect underline{ min.fLeft,  fm.fUnderlinePosition,
 *                               min.fRight, fm.fUnderlinePosition + fm.fUnderlineThickness };
 *             canvas->drawRect(underline, metricsPaint);
 *         }
 *
 *         if ((fm.fFlags & SkFontMetrics::kStrikeoutPositionIsValid_Flag) &&
 *             (fm.fFlags & SkFontMetrics::kStrikeoutThicknessIsValid_Flag))
 *         {
 *             SkRect strikeout{ min.fLeft,  fm.fStrikeoutPosition - fm.fStrikeoutThickness,
 *                               min.fRight, fm.fStrikeoutPosition };
 *             canvas->drawRect(strikeout, metricsPaint);
 *         }
 *
 *         struct GlyphToDraw {
 *             SkGlyphID id;
 *             SkPoint location;
 *             SkScalar rotation;
 *         } glyphsToDraw [] = {
 *             {left,   {min.left(),    min.centerY()}, 270},
 *             {right,  {min.right(),   min.centerY()},  90},
 *             {top,    {min.centerX(), min.top()    },   0},
 *             {bottom, {min.centerX(), min.bottom() }, 180},
 *         };
 *
 *         SkFont labelFont;
 *         labelFont.setEdging(SkFont::Edging::kAntiAlias);
 *         labelFont.setTypeface(ToolUtils::DefaultPortableTypeface());
 *
 *         if (labelBounds) {
 *             SkString name;
 *             font.getTypeface()->getFamilyName(&name);
 *             canvas->drawString(name, min.fLeft, min.fBottom, labelFont, SkPaint());
 *         }
 *         for (const GlyphToDraw& glyphToDraw : glyphsToDraw) {
 *             SkPath path = font.getPath(glyphToDraw.id).value_or(SkPath());
 *             SkPaint::Style style = path.isEmpty() ? SkPaint::kFill_Style : SkPaint::kStroke_Style;
 *             SkPaint glyphPaint;
 *             glyphPaint.setStyle(style);
 *             canvas->drawSimpleText(&glyphToDraw.id, sizeof(glyphToDraw.id),
 *                                    SkTextEncoding::kGlyphID, 0, 0, font, glyphPaint);
 *
 *             if (labelBounds) {
 *                 SkAutoCanvasRestore acr2(canvas, true);
 *                 canvas->translate(glyphToDraw.location.fX, glyphToDraw.location.fY);
 *                 canvas->rotate(glyphToDraw.rotation);
 *                 SkString glyphStr;
 *                 glyphStr.appendS32(glyphToDraw.id);
 *                 canvas->drawString(glyphStr, 0, 0, labelFont, SkPaint());
 *             }
 *         }
 *
 *         return drawBounds;
 *     }
 *
 *     SkISize getISize() override { return {1024, 850}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkFont font = ToolUtils::DefaultFont();
 *         font.setEdging(SkFont::Edging::kAntiAlias);
 *         font.setSubpixel(true);
 *         font.setSize(100);
 *         font.setScaleX(fScaleX);
 *         font.setSkewX(fSkewX);
 *
 *         const SkColor boundsColors[2] = { SK_ColorRED, SK_ColorBLUE };
 *
 *         SkFontMgr* fm = fFM.get();
 *         int count = fm->countFamilies();
 *         if (count == 0) {
 *             *errorMsg = "No families in SkFontMgr under test.";
 *             return DrawResult::kSkip;
 *         }
 *
 *         int index = 0;
 *         SkScalar x = 0, y = 0;
 *
 *         canvas->translate(10, 120);
 *
 *         int typefacesVisited = 0;
 *         for (int i = 0; i < count && typefacesVisited < 32; ++i) {
 *             sk_sp<SkFontStyleSet> set(fm->createStyleSet(i));
 *             int stylesVisited = 0;
 *             for (int j = 0; j < set->count() && typefacesVisited < 32 && stylesVisited < 3; ++j) {
 *                 font.setTypeface(sk_sp<SkTypeface>(set->createTypeface(j)));
 *                 // Fonts with lots of glyphs are interesting, but can take a long time to find
 *                 // the glyphs which make up the maximum extent.
 *                 SkTypeface* typeface = font.getTypeface();
 *                 if (typeface && 0 < typeface->countGlyphs() && typeface->countGlyphs() < 1000) {
 *                     ++typefacesVisited;
 *                     ++stylesVisited;
 *
 *                     SkColor color = boundsColors[index & 1];
 *                     SkRect drawBounds = show_bounds(canvas, font, x, y, color, fLabelBounds);
 *                     x += drawBounds.width() + 20;
 *                     index += 1;
 *                     if (x > 900) {
 *                         x = 0;
 *                         y += 160;
 *                     }
 *                     if (y >= 700) {
 *                         return DrawResult::kOk;
 *                     }
 *                 }
 *             }
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 *     sk_sp<SkFontMgr> fFM;
 *     const SkScalar fScaleX;
 *     const SkScalar fSkewX;
 *     bool fLabelBounds = false;
 * }
 * ```
 */
public open class FontMgrBoundsGM public constructor(
  scale: Float,
  skew: Float,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fFM
   * ```
   */
  private var fFM: SkSp<SkFontMgr> = TODO("Initialize fFM")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar fScaleX
   * ```
   */
  private val fScaleX: SkScalar = TODO("Initialize fScaleX")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar fSkewX
   * ```
   */
  private val fSkewX: SkScalar = TODO("Initialize fSkewX")

  /**
   * C++ original:
   * ```cpp
   * bool fLabelBounds = false
   * ```
   */
  private var fLabelBounds: Boolean = TODO("Initialize fLabelBounds")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         if (fScaleX != 1 || fSkewX != 0) {
   *             return SkStringPrintf("fontmgr_bounds_%g_%g", fScaleX, fSkewX);
   *         }
   *         return SkString("fontmgr_bounds");
   *     }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override { fFM = ToolUtils::TestFontMgr(); }
   * ```
   */
  public override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGetControls(SkMetaData* controls) override {
   *         controls->setBool("Label Bounds", fLabelBounds);
   *         return true;
   *     }
   * ```
   */
  public override fun onGetControls(controls: SkMetaData?): Boolean {
    TODO("Implement onGetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSetControls(const SkMetaData& controls) override {
   *         controls.findBool("Label Bounds", &fLabelBounds);
   *     }
   * ```
   */
  public override fun onSetControls(controls: SkMetaData) {
    TODO("Implement onSetControls")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {1024, 850}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         SkFont font = ToolUtils::DefaultFont();
   *         font.setEdging(SkFont::Edging::kAntiAlias);
   *         font.setSubpixel(true);
   *         font.setSize(100);
   *         font.setScaleX(fScaleX);
   *         font.setSkewX(fSkewX);
   *
   *         const SkColor boundsColors[2] = { SK_ColorRED, SK_ColorBLUE };
   *
   *         SkFontMgr* fm = fFM.get();
   *         int count = fm->countFamilies();
   *         if (count == 0) {
   *             *errorMsg = "No families in SkFontMgr under test.";
   *             return DrawResult::kSkip;
   *         }
   *
   *         int index = 0;
   *         SkScalar x = 0, y = 0;
   *
   *         canvas->translate(10, 120);
   *
   *         int typefacesVisited = 0;
   *         for (int i = 0; i < count && typefacesVisited < 32; ++i) {
   *             sk_sp<SkFontStyleSet> set(fm->createStyleSet(i));
   *             int stylesVisited = 0;
   *             for (int j = 0; j < set->count() && typefacesVisited < 32 && stylesVisited < 3; ++j) {
   *                 font.setTypeface(sk_sp<SkTypeface>(set->createTypeface(j)));
   *                 // Fonts with lots of glyphs are interesting, but can take a long time to find
   *                 // the glyphs which make up the maximum extent.
   *                 SkTypeface* typeface = font.getTypeface();
   *                 if (typeface && 0 < typeface->countGlyphs() && typeface->countGlyphs() < 1000) {
   *                     ++typefacesVisited;
   *                     ++stylesVisited;
   *
   *                     SkColor color = boundsColors[index & 1];
   *                     SkRect drawBounds = show_bounds(canvas, font, x, y, color, fLabelBounds);
   *                     x += drawBounds.width() + 20;
   *                     index += 1;
   *                     if (x > 900) {
   *                         x = 0;
   *                         y += 160;
   *                     }
   *                     if (y >= 700) {
   *                         return DrawResult::kOk;
   *                     }
   *                 }
   *             }
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkRect show_bounds(SkCanvas* canvas, const SkFont& font, SkScalar x, SkScalar y,
     *                               SkColor boundsColor, bool labelBounds)
     *     {
     *         SkGlyphID left = 0, right = 0, top = 0, bottom = 0;
     *         SkRect min = SkRect::MakeLTRB(SK_ScalarInfinity, SK_ScalarInfinity,
     *                                       SK_ScalarNegativeInfinity, SK_ScalarNegativeInfinity);
     *         {
     *             int numGlyphs = font.getTypeface()->countGlyphs();
     *             for (int i = 0; i < numGlyphs; ++i) {
     *                 SkGlyphID glyphId = i;
     *                 SkRect cur = font.getBounds(glyphId, nullptr);
     *                 if (cur.fLeft   < min.fLeft  ) { min.fLeft   = cur.fLeft;   left   = i; }
     *                 if (cur.fTop    < min.fTop   ) { min.fTop    = cur.fTop ;   top    = i; }
     *                 if (min.fRight  < cur.fRight ) { min.fRight  = cur.fRight;  right  = i; }
     *                 if (min.fBottom < cur.fBottom) { min.fBottom = cur.fBottom; bottom = i; }
     *             }
     *         }
     *
     *         SkRect fontBounds = SkFontPriv::GetFontBounds(font);
     *
     *         SkRect drawBounds = min;
     *         drawBounds.join(fontBounds);
     *
     *         SkAutoCanvasRestore acr(canvas, true);
     *         canvas->translate(x - drawBounds.left(), y);
     *
     *         SkPaint boundsPaint;
     *         boundsPaint.setAntiAlias(true);
     *         boundsPaint.setColor(boundsColor);
     *         boundsPaint.setStyle(SkPaint::kStroke_Style);
     *         canvas->drawRect(fontBounds, boundsPaint);
     *
     *         const SkScalar intervals[] = { 10.f, 10.f };
     *         boundsPaint.setPathEffect(SkDashPathEffect::Make(intervals, 0.f));
     *         canvas->drawRect(min, boundsPaint);
     *
     *         SkFontMetrics fm;
     *         font.getMetrics(&fm);
     *         SkPaint metricsPaint(boundsPaint);
     *         metricsPaint.setStyle(SkPaint::kFill_Style);
     *         metricsPaint.setAlphaf(0.25f);
     *         if ((fm.fFlags & SkFontMetrics::kUnderlinePositionIsValid_Flag) &&
     *             (fm.fFlags & SkFontMetrics::kUnderlineThicknessIsValid_Flag))
     *         {
     *             SkRect underline{ min.fLeft,  fm.fUnderlinePosition,
     *                               min.fRight, fm.fUnderlinePosition + fm.fUnderlineThickness };
     *             canvas->drawRect(underline, metricsPaint);
     *         }
     *
     *         if ((fm.fFlags & SkFontMetrics::kStrikeoutPositionIsValid_Flag) &&
     *             (fm.fFlags & SkFontMetrics::kStrikeoutThicknessIsValid_Flag))
     *         {
     *             SkRect strikeout{ min.fLeft,  fm.fStrikeoutPosition - fm.fStrikeoutThickness,
     *                               min.fRight, fm.fStrikeoutPosition };
     *             canvas->drawRect(strikeout, metricsPaint);
     *         }
     *
     *         struct GlyphToDraw {
     *             SkGlyphID id;
     *             SkPoint location;
     *             SkScalar rotation;
     *         } glyphsToDraw [] = {
     *             {left,   {min.left(),    min.centerY()}, 270},
     *             {right,  {min.right(),   min.centerY()},  90},
     *             {top,    {min.centerX(), min.top()    },   0},
     *             {bottom, {min.centerX(), min.bottom() }, 180},
     *         };
     *
     *         SkFont labelFont;
     *         labelFont.setEdging(SkFont::Edging::kAntiAlias);
     *         labelFont.setTypeface(ToolUtils::DefaultPortableTypeface());
     *
     *         if (labelBounds) {
     *             SkString name;
     *             font.getTypeface()->getFamilyName(&name);
     *             canvas->drawString(name, min.fLeft, min.fBottom, labelFont, SkPaint());
     *         }
     *         for (const GlyphToDraw& glyphToDraw : glyphsToDraw) {
     *             SkPath path = font.getPath(glyphToDraw.id).value_or(SkPath());
     *             SkPaint::Style style = path.isEmpty() ? SkPaint::kFill_Style : SkPaint::kStroke_Style;
     *             SkPaint glyphPaint;
     *             glyphPaint.setStyle(style);
     *             canvas->drawSimpleText(&glyphToDraw.id, sizeof(glyphToDraw.id),
     *                                    SkTextEncoding::kGlyphID, 0, 0, font, glyphPaint);
     *
     *             if (labelBounds) {
     *                 SkAutoCanvasRestore acr2(canvas, true);
     *                 canvas->translate(glyphToDraw.location.fX, glyphToDraw.location.fY);
     *                 canvas->rotate(glyphToDraw.rotation);
     *                 SkString glyphStr;
     *                 glyphStr.appendS32(glyphToDraw.id);
     *                 canvas->drawString(glyphStr, 0, 0, labelFont, SkPaint());
     *             }
     *         }
     *
     *         return drawBounds;
     *     }
     * ```
     */
    private fun showBounds(
      canvas: SkCanvas?,
      font: SkFont,
      x: SkScalar,
      y: SkScalar,
      boundsColor: SkColor,
      labelBounds: Boolean,
    ): SkRect {
      TODO("Implement showBounds")
    }
  }
}
