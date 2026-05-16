package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkFontMetrics
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class TextStyle {
 * public:
 *     TextStyle() = default;
 *     TextStyle(const TextStyle& other) = default;
 *     TextStyle& operator=(const TextStyle& other) = default;
 *
 *     TextStyle cloneForPlaceholder();
 *
 *     bool equals(const TextStyle& other) const;
 *     bool equalsByFonts(const TextStyle& that) const;
 *     bool matchOneAttribute(StyleType styleType, const TextStyle& other) const;
 *     bool operator==(const TextStyle& rhs) const { return this->equals(rhs); }
 *
 *     // Colors
 *     SkColor getColor() const { return fColor; }
 *     void setColor(SkColor color) { fColor = color; }
 *
 *     bool hasForeground() const { return fHasForeground; }
 *     SkPaint getForeground() const {
 *         const SkPaint* paint = std::get_if<SkPaint>(&fForeground);
 *         return paint ? *paint : SkPaint();
 *     }
 *     ParagraphPainter::SkPaintOrID getForegroundPaintOrID() const {
 *         return fForeground;
 *     }
 *     void setForegroundPaint(SkPaint paint) {
 *         fHasForeground = true;
 *         fForeground = std::move(paint);
 *     }
 *     // DEPRECATED: prefer `setForegroundPaint`.
 *     void setForegroundColor(SkPaint paint) { setForegroundPaint(std::move(paint)); }
 *
 *     // Set the foreground to a paint ID.  This is intended for use by clients
 *     // that implement a custom ParagraphPainter that can not accept an SkPaint.
 *     void setForegroundPaintID(ParagraphPainter::PaintID paintID) {
 *         fHasForeground = true;
 *         fForeground = paintID;
 *     }
 *     void clearForegroundColor() { fHasForeground = false; }
 *
 *     bool hasBackground() const { return fHasBackground; }
 *     SkPaint getBackground() const {
 *         const SkPaint* paint = std::get_if<SkPaint>(&fBackground);
 *         return paint ? *paint : SkPaint();
 *     }
 *     ParagraphPainter::SkPaintOrID getBackgroundPaintOrID() const {
 *         return fBackground;
 *     }
 *     void setBackgroundPaint(SkPaint paint) {
 *         fHasBackground = true;
 *         fBackground = std::move(paint);
 *     }
 *     // DEPRECATED: prefer `setBackgroundPaint`.
 *     void setBackgroundColor(SkPaint paint) { setBackgroundPaint(std::move(paint)); }
 *     void setBackgroundPaintID(ParagraphPainter::PaintID paintID) {
 *         fHasBackground = true;
 *         fBackground = paintID;
 *     }
 *     void clearBackgroundColor() { fHasBackground = false; }
 *
 *     // Decorations
 *     Decoration getDecoration() const { return fDecoration; }
 *     TextDecoration getDecorationType() const { return fDecoration.fType; }
 *     TextDecorationMode getDecorationMode() const { return fDecoration.fMode; }
 *     SkColor getDecorationColor() const { return fDecoration.fColor; }
 *     TextDecorationStyle getDecorationStyle() const { return fDecoration.fStyle; }
 *     SkScalar getDecorationThicknessMultiplier() const {
 *         return fDecoration.fThicknessMultiplier;
 *     }
 *     void setDecoration(TextDecoration decoration) { fDecoration.fType = decoration; }
 *     void setDecorationMode(TextDecorationMode mode) { fDecoration.fMode = mode; }
 *     void setDecorationStyle(TextDecorationStyle style) { fDecoration.fStyle = style; }
 *     void setDecorationColor(SkColor color) { fDecoration.fColor = color; }
 *     void setDecorationThicknessMultiplier(SkScalar m) { fDecoration.fThicknessMultiplier = m; }
 *
 *     // Weight/Width/Slant
 *     SkFontStyle getFontStyle() const { return fFontStyle; }
 *     void setFontStyle(SkFontStyle fontStyle) { fFontStyle = fontStyle; }
 *
 *     // Shadows
 *     size_t getShadowNumber() const { return fTextShadows.size(); }
 *     std::vector<TextShadow> getShadows() const { return fTextShadows; }
 *     void addShadow(TextShadow shadow) { fTextShadows.emplace_back(shadow); }
 *     void resetShadows() { fTextShadows.clear(); }
 *
 *     // Font features
 *     size_t getFontFeatureNumber() const { return fFontFeatures.size(); }
 *     std::vector<FontFeature> getFontFeatures() const { return fFontFeatures; }
 *     void addFontFeature(const SkString& fontFeature, int value)
 *         { fFontFeatures.emplace_back(fontFeature, value); }
 *     void resetFontFeatures() { fFontFeatures.clear(); }
 *
 *     // Font arguments
 *     const std::optional<FontArguments>& getFontArguments() const { return fFontArguments; }
 *     // The contents of the SkFontArguments will be copied into the TextStyle,
 *     // and the SkFontArguments can be safely deleted after setFontArguments returns.
 *     void setFontArguments(const std::optional<SkFontArguments>& args);
 *
 *     SkScalar getFontSize() const { return fFontSize; }
 *     void setFontSize(SkScalar size) { fFontSize = size; }
 *
 *     const std::vector<SkString>& getFontFamilies() const { return fFontFamilies; }
 *     void setFontFamilies(std::vector<SkString> families) {
 *         fFontFamilies = std::move(families);
 *     }
 *
 *     SkScalar getBaselineShift() const { return fBaselineShift; }
 *     void setBaselineShift(SkScalar baselineShift) { fBaselineShift = baselineShift; }
 *
 *     void setHeight(SkScalar height) { fHeight = height; }
 *     SkScalar getHeight() const { return fHeightOverride ? fHeight : 0; }
 *
 *     void setHeightOverride(bool heightOverride) { fHeightOverride = heightOverride; }
 *     bool getHeightOverride() const { return fHeightOverride; }
 *
 *     void setHalfLeading(bool halfLeading) { fHalfLeading = halfLeading; }
 *     bool getHalfLeading() const { return fHalfLeading; }
 *
 *     void setLetterSpacing(SkScalar letterSpacing) { fLetterSpacing = letterSpacing; }
 *     SkScalar getLetterSpacing() const { return fLetterSpacing; }
 *
 *     void setWordSpacing(SkScalar wordSpacing) { fWordSpacing = wordSpacing; }
 *     SkScalar getWordSpacing() const { return fWordSpacing; }
 *
 *     SkTypeface* getTypeface() const { return fTypeface.get(); }
 *     sk_sp<SkTypeface> refTypeface() const { return fTypeface; }
 *     void setTypeface(sk_sp<SkTypeface> typeface) { fTypeface = std::move(typeface); }
 *
 *     SkString getLocale() const { return fLocale; }
 *     void setLocale(const SkString& locale) { fLocale = locale; }
 *
 *     TextBaseline getTextBaseline() const { return fTextBaseline; }
 *     void setTextBaseline(TextBaseline baseline) { fTextBaseline = baseline; }
 *
 *     void getFontMetrics(SkFontMetrics* metrics) const;
 *
 *     bool isPlaceholder() const { return fIsPlaceholder; }
 *     void setPlaceholder() { fIsPlaceholder = true; }
 *
 *     void setFontEdging(SkFont::Edging edging) { fEdging = edging; }
 *     SkFont::Edging getFontEdging() const { return fEdging; }
 *
 *     void setSubpixel(bool subpixel) { fSubpixel = subpixel; }
 *     bool getSubpixel() const { return fSubpixel; }
 *
 *     void setFontHinting(SkFontHinting hinting) { fHinting = hinting; }
 *     SkFontHinting getFontHinting() const { return fHinting; }
 *
 * private:
 *     static const std::vector<SkString>* kDefaultFontFamilies;
 *
 *     Decoration fDecoration = {
 *             TextDecoration::kNoDecoration,
 *             // TODO: switch back to kGaps when (if) switching flutter to skparagraph
 *             TextDecorationMode::kThrough,
 *             // It does not make sense to draw a transparent object, so we use this as a default
 *             // value to indicate no decoration color was set.
 *             SK_ColorTRANSPARENT, TextDecorationStyle::kSolid,
 *             // Thickness is applied as a multiplier to the default thickness of the font.
 *             1.0f};
 *
 *     SkFontStyle fFontStyle;
 *
 *     std::vector<SkString> fFontFamilies = *kDefaultFontFamilies;
 *
 *     SkScalar fFontSize = 14.0;
 *     SkFont::Edging fEdging = SkFont::Edging::kAntiAlias;
 *     bool fSubpixel = true;
 *     SkFontHinting fHinting = SkFontHinting::kSlight;
 *     SkScalar fHeight = 1.0;
 *     bool fHeightOverride = false;
 *     SkScalar fBaselineShift = 0.0f;
 *     // true: half leading.
 *     // false: scale ascent/descent with fHeight.
 *     bool fHalfLeading = false;
 *     SkString fLocale = {};
 *     SkScalar fLetterSpacing = 0.0;
 *     SkScalar fWordSpacing = 0.0;
 *
 *     TextBaseline fTextBaseline = TextBaseline::kAlphabetic;
 *
 *     SkColor fColor = SK_ColorWHITE;
 *     bool fHasBackground = false;
 *     ParagraphPainter::SkPaintOrID fBackground;
 *     bool fHasForeground = false;
 *     ParagraphPainter::SkPaintOrID fForeground;
 *
 *     std::vector<TextShadow> fTextShadows;
 *
 *     sk_sp<SkTypeface> fTypeface;
 *     bool fIsPlaceholder = false;
 *
 *     std::vector<FontFeature> fFontFeatures;
 *
 *     std::optional<FontArguments> fFontArguments;
 * }
 * ```
 */
public data class TextStyle public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const std::vector<SkString>* kDefaultFontFamilies
   * ```
   */
  private var fDecoration: Decoration,
  /**
   * C++ original:
   * ```cpp
   * Decoration fDecoration
   * ```
   */
  private var fFontStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFontStyle fFontStyle
   * ```
   */
  private var fFontFamilies: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkString> fFontFamilies
   * ```
   */
  private var fFontSize: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fFontSize
   * ```
   */
  private var fEdging: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFont::Edging fEdging
   * ```
   */
  private var fSubpixel: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fSubpixel = true
   * ```
   */
  private var fHinting: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFontHinting fHinting
   * ```
   */
  private var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHeight
   * ```
   */
  private var fHeightOverride: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHeightOverride = false
   * ```
   */
  private var fBaselineShift: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fBaselineShift
   * ```
   */
  private var fHalfLeading: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHalfLeading = false
   * ```
   */
  private var fLocale: Int,
  /**
   * C++ original:
   * ```cpp
   * SkString fLocale
   * ```
   */
  private var fLetterSpacing: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fLetterSpacing
   * ```
   */
  private var fWordSpacing: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWordSpacing
   * ```
   */
  private var fTextBaseline: Int,
  /**
   * C++ original:
   * ```cpp
   * TextBaseline fTextBaseline
   * ```
   */
  private var fColor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor fColor
   * ```
   */
  private var fHasBackground: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasBackground = false
   * ```
   */
  private var fBackground: Int,
  /**
   * C++ original:
   * ```cpp
   * ParagraphPainter::SkPaintOrID fBackground
   * ```
   */
  private var fHasForeground: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasForeground = false
   * ```
   */
  private var fForeground: Int,
  /**
   * C++ original:
   * ```cpp
   * ParagraphPainter::SkPaintOrID fForeground
   * ```
   */
  private var fTextShadows: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<TextShadow> fTextShadows
   * ```
   */
  private var fTypeface: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypeface
   * ```
   */
  private var fIsPlaceholder: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fIsPlaceholder = false
   * ```
   */
  private var fFontFeatures: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<FontFeature> fFontFeatures
   * ```
   */
  private var fFontArguments: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * TextStyle& operator=(const TextStyle& other) = default
   * ```
   */
  public fun assign(other: TextStyle) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * TextStyle TextStyle::cloneForPlaceholder() {
   *     TextStyle result;
   *     result.fColor = fColor;
   *     result.fFontSize = fFontSize;
   *     result.fFontFamilies = fFontFamilies;
   *     result.fDecoration = fDecoration;
   *     result.fHasBackground = fHasBackground;
   *     result.fHasForeground = fHasForeground;
   *     result.fBackground = fBackground;
   *     result.fForeground = fForeground;
   *     result.fHeightOverride = fHeightOverride;
   *     result.fIsPlaceholder = true;
   *     result.fFontFeatures = fFontFeatures;
   *     result.fHalfLeading = fHalfLeading;
   *     result.fBaselineShift = fBaselineShift;
   *     result.fFontArguments = fFontArguments;
   *     return result;
   * }
   * ```
   */
  public fun cloneForPlaceholder(): TextStyle {
    TODO("Implement cloneForPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextStyle::equals(const TextStyle& other) const {
   *
   *     if (fIsPlaceholder || other.fIsPlaceholder) {
   *         return false;
   *     }
   *
   *     if (fColor != other.fColor) {
   *         return false;
   *     }
   *     if (!(fDecoration == other.fDecoration)) {
   *         return false;
   *     }
   *     if (!(fFontStyle == other.fFontStyle)) {
   *         return false;
   *     }
   *     if (fFontFamilies != other.fFontFamilies) {
   *         return false;
   *     }
   *     if (fLetterSpacing != other.fLetterSpacing) {
   *         return false;
   *     }
   *     if (fWordSpacing != other.fWordSpacing) {
   *         return false;
   *     }
   *     if (fHeight != other.fHeight) {
   *         return false;
   *     }
   *     if (fHeightOverride != other.fHeightOverride) {
   *         return false;
   *     }
   *     if (fHalfLeading != other.fHalfLeading) {
   *         return false;
   *     }
   *     if (fBaselineShift != other.fBaselineShift) {
   *         return false;
   *     }
   *     if (fFontSize != other.fFontSize) {
   *         return false;
   *     }
   *     if (fLocale != other.fLocale) {
   *         return false;
   *     }
   *     if (fHasForeground != other.fHasForeground || fForeground != other.fForeground) {
   *         return false;
   *     }
   *     if (fHasBackground != other.fHasBackground || fBackground != other.fBackground) {
   *         return false;
   *     }
   *     if (fTextShadows.size() != other.fTextShadows.size()) {
   *         return false;
   *     }
   *     for (size_t i = 0; i < fTextShadows.size(); ++i) {
   *         if (fTextShadows[i] != other.fTextShadows[i]) {
   *             return false;
   *         }
   *     }
   *     if (fFontFeatures.size() != other.fFontFeatures.size()) {
   *         return false;
   *     }
   *     for (size_t i = 0; i < fFontFeatures.size(); ++i) {
   *         if (!(fFontFeatures[i] == other.fFontFeatures[i])) {
   *             return false;
   *         }
   *     }
   *     if (fFontArguments != other.fFontArguments) {
   *         return false;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextStyle::equalsByFonts(const TextStyle& that) const {
   *
   *     return !fIsPlaceholder && !that.fIsPlaceholder &&
   *            fFontStyle == that.fFontStyle &&
   *            fFontFamilies == that.fFontFamilies &&
   *            fFontFeatures == that.fFontFeatures &&
   *            fFontArguments == that.getFontArguments() &&
   *            nearlyEqual(fLetterSpacing, that.fLetterSpacing) &&
   *            nearlyEqual(fWordSpacing, that.fWordSpacing) &&
   *            nearlyEqual(fHeight, that.fHeight) &&
   *            nearlyEqual(fBaselineShift, that.fBaselineShift) &&
   *            nearlyEqual(fFontSize, that.fFontSize) &&
   *            fLocale == that.fLocale;
   * }
   * ```
   */
  public fun equalsByFonts(that: TextStyle): Boolean {
    TODO("Implement equalsByFonts")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextStyle::matchOneAttribute(StyleType styleType, const TextStyle& other) const {
   *     switch (styleType) {
   *         case kForeground:
   *             return (!fHasForeground && !other.fHasForeground && fColor == other.fColor) ||
   *                    ( fHasForeground &&  other.fHasForeground && fForeground == other.fForeground);
   *
   *         case kBackground:
   *             return (!fHasBackground && !other.fHasBackground) ||
   *                    ( fHasBackground &&  other.fHasBackground && fBackground == other.fBackground);
   *
   *         case kShadow:
   *             if (fTextShadows.size() != other.fTextShadows.size()) {
   *                 return false;
   *             }
   *
   *             for (int32_t i = 0; i < SkToInt(fTextShadows.size()); ++i) {
   *                 if (fTextShadows[i] != other.fTextShadows[i]) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *
   *         case kDecorations:
   *             return this->fDecoration == other.fDecoration;
   *
   *         case kLetterSpacing:
   *             return fLetterSpacing == other.fLetterSpacing;
   *
   *         case kWordSpacing:
   *             return fWordSpacing == other.fWordSpacing;
   *
   *         case kAllAttributes:
   *             return this->equals(other);
   *
   *         case kFont:
   *             // TODO: should not we take typefaces in account?
   *             return fFontStyle == other.fFontStyle &&
   *                    fLocale == other.fLocale &&
   *                    fFontFamilies == other.fFontFamilies &&
   *                    fFontSize == other.fFontSize &&
   *                    fHeight == other.fHeight &&
   *                    fHalfLeading == other.fHalfLeading &&
   *                    fBaselineShift == other.fBaselineShift &&
   *                    fFontArguments == other.fFontArguments;
   *         default:
   *             SkASSERT(false);
   *             return false;
   *     }
   * }
   * ```
   */
  public fun matchOneAttribute(styleType: StyleType, other: TextStyle): Boolean {
    TODO("Implement matchOneAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const TextStyle& rhs) const { return this->equals(rhs); }
   * ```
   */
  public fun getColor(): Int {
    TODO("Implement getColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor getColor() const { return fColor; }
   * ```
   */
  public fun setColor(color: SkColor) {
    TODO("Implement setColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setColor(SkColor color) { fColor = color; }
   * ```
   */
  public fun hasForeground(): Boolean {
    TODO("Implement hasForeground")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasForeground() const { return fHasForeground; }
   * ```
   */
  public fun getForeground(): Int {
    TODO("Implement getForeground")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint getForeground() const {
   *         const SkPaint* paint = std::get_if<SkPaint>(&fForeground);
   *         return paint ? *paint : SkPaint();
   *     }
   * ```
   */
  public fun getForegroundPaintOrID(): Int {
    TODO("Implement getForegroundPaintOrID")
  }

  /**
   * C++ original:
   * ```cpp
   * ParagraphPainter::SkPaintOrID getForegroundPaintOrID() const {
   *         return fForeground;
   *     }
   * ```
   */
  public fun setForegroundPaint(paint: SkPaint) {
    TODO("Implement setForegroundPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void setForegroundPaint(SkPaint paint) {
   *         fHasForeground = true;
   *         fForeground = std::move(paint);
   *     }
   * ```
   */
  public fun setForegroundColor(paint: SkPaint) {
    TODO("Implement setForegroundColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setForegroundColor(SkPaint paint) { setForegroundPaint(std::move(paint)); }
   * ```
   */
  public fun setForegroundPaintID(paintID: ParagraphPainterPaintID) {
    TODO("Implement setForegroundPaintID")
  }

  /**
   * C++ original:
   * ```cpp
   * void setForegroundPaintID(ParagraphPainter::PaintID paintID) {
   *         fHasForeground = true;
   *         fForeground = paintID;
   *     }
   * ```
   */
  public fun clearForegroundColor() {
    TODO("Implement clearForegroundColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void clearForegroundColor() { fHasForeground = false; }
   * ```
   */
  public fun hasBackground(): Boolean {
    TODO("Implement hasBackground")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasBackground() const { return fHasBackground; }
   * ```
   */
  public fun getBackground(): Int {
    TODO("Implement getBackground")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPaint getBackground() const {
   *         const SkPaint* paint = std::get_if<SkPaint>(&fBackground);
   *         return paint ? *paint : SkPaint();
   *     }
   * ```
   */
  public fun getBackgroundPaintOrID(): Int {
    TODO("Implement getBackgroundPaintOrID")
  }

  /**
   * C++ original:
   * ```cpp
   * ParagraphPainter::SkPaintOrID getBackgroundPaintOrID() const {
   *         return fBackground;
   *     }
   * ```
   */
  public fun setBackgroundPaint(paint: SkPaint) {
    TODO("Implement setBackgroundPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBackgroundPaint(SkPaint paint) {
   *         fHasBackground = true;
   *         fBackground = std::move(paint);
   *     }
   * ```
   */
  public fun setBackgroundColor(paint: SkPaint) {
    TODO("Implement setBackgroundColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBackgroundColor(SkPaint paint) { setBackgroundPaint(std::move(paint)); }
   * ```
   */
  public fun setBackgroundPaintID(paintID: ParagraphPainterPaintID) {
    TODO("Implement setBackgroundPaintID")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBackgroundPaintID(ParagraphPainter::PaintID paintID) {
   *         fHasBackground = true;
   *         fBackground = paintID;
   *     }
   * ```
   */
  public fun clearBackgroundColor() {
    TODO("Implement clearBackgroundColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void clearBackgroundColor() { fHasBackground = false; }
   * ```
   */
  public fun getDecoration(): Decoration {
    TODO("Implement getDecoration")
  }

  /**
   * C++ original:
   * ```cpp
   * Decoration getDecoration() const { return fDecoration; }
   * ```
   */
  public fun getDecorationType(): TextDecoration {
    TODO("Implement getDecorationType")
  }

  /**
   * C++ original:
   * ```cpp
   * TextDecoration getDecorationType() const { return fDecoration.fType; }
   * ```
   */
  public fun getDecorationMode(): TextDecorationMode {
    TODO("Implement getDecorationMode")
  }

  /**
   * C++ original:
   * ```cpp
   * TextDecorationMode getDecorationMode() const { return fDecoration.fMode; }
   * ```
   */
  public fun getDecorationColor(): Int {
    TODO("Implement getDecorationColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor getDecorationColor() const { return fDecoration.fColor; }
   * ```
   */
  public fun getDecorationStyle(): TextDecorationStyle {
    TODO("Implement getDecorationStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * TextDecorationStyle getDecorationStyle() const { return fDecoration.fStyle; }
   * ```
   */
  public fun getDecorationThicknessMultiplier(): Int {
    TODO("Implement getDecorationThicknessMultiplier")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getDecorationThicknessMultiplier() const {
   *         return fDecoration.fThicknessMultiplier;
   *     }
   * ```
   */
  public fun setDecoration(decoration: TextDecoration) {
    TODO("Implement setDecoration")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDecoration(TextDecoration decoration) { fDecoration.fType = decoration; }
   * ```
   */
  public fun setDecorationMode(mode: TextDecorationMode) {
    TODO("Implement setDecorationMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDecorationMode(TextDecorationMode mode) { fDecoration.fMode = mode; }
   * ```
   */
  public fun setDecorationStyle(style: TextDecorationStyle) {
    TODO("Implement setDecorationStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDecorationStyle(TextDecorationStyle style) { fDecoration.fStyle = style; }
   * ```
   */
  public fun setDecorationColor(color: SkColor) {
    TODO("Implement setDecorationColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDecorationColor(SkColor color) { fDecoration.fColor = color; }
   * ```
   */
  public fun setDecorationThicknessMultiplier(m: SkScalar) {
    TODO("Implement setDecorationThicknessMultiplier")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDecorationThicknessMultiplier(SkScalar m) { fDecoration.fThicknessMultiplier = m; }
   * ```
   */
  public fun getFontStyle(): Int {
    TODO("Implement getFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle getFontStyle() const { return fFontStyle; }
   * ```
   */
  public fun setFontStyle(fontStyle: SkFontStyle) {
    TODO("Implement setFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontStyle(SkFontStyle fontStyle) { fFontStyle = fontStyle; }
   * ```
   */
  public fun getShadowNumber(): ULong {
    TODO("Implement getShadowNumber")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getShadowNumber() const { return fTextShadows.size(); }
   * ```
   */
  public fun getShadows(): Int {
    TODO("Implement getShadows")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<TextShadow> getShadows() const { return fTextShadows; }
   * ```
   */
  public fun addShadow(shadow: TextShadow) {
    TODO("Implement addShadow")
  }

  /**
   * C++ original:
   * ```cpp
   * void addShadow(TextShadow shadow) { fTextShadows.emplace_back(shadow); }
   * ```
   */
  public fun resetShadows() {
    TODO("Implement resetShadows")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetShadows() { fTextShadows.clear(); }
   * ```
   */
  public fun getFontFeatureNumber(): ULong {
    TODO("Implement getFontFeatureNumber")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getFontFeatureNumber() const { return fFontFeatures.size(); }
   * ```
   */
  public fun getFontFeatures(): Int {
    TODO("Implement getFontFeatures")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<FontFeature> getFontFeatures() const { return fFontFeatures; }
   * ```
   */
  public fun addFontFeature(fontFeature: String, `value`: Int) {
    TODO("Implement addFontFeature")
  }

  /**
   * C++ original:
   * ```cpp
   * void addFontFeature(const SkString& fontFeature, int value)
   *         { fFontFeatures.emplace_back(fontFeature, value); }
   * ```
   */
  public fun resetFontFeatures() {
    TODO("Implement resetFontFeatures")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetFontFeatures() { fFontFeatures.clear(); }
   * ```
   */
  public fun getFontArguments(): Int {
    TODO("Implement getFontArguments")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::optional<FontArguments>& getFontArguments() const { return fFontArguments; }
   * ```
   */
  public fun setFontArguments(args: SkFontArguments?) {
    TODO("Implement setFontArguments")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextStyle::setFontArguments(const std::optional<SkFontArguments>& args) {
   *     if (!args) {
   *         fFontArguments.reset();
   *         return;
   *     }
   *
   *     fFontArguments.emplace(*args);
   * }
   * ```
   */
  public fun getFontSize(): Int {
    TODO("Implement getFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getFontSize() const { return fFontSize; }
   * ```
   */
  public fun setFontSize(size: SkScalar) {
    TODO("Implement setFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontSize(SkScalar size) { fFontSize = size; }
   * ```
   */
  public fun getFontFamilies(): Int {
    TODO("Implement getFontFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<SkString>& getFontFamilies() const { return fFontFamilies; }
   * ```
   */
  public fun setFontFamilies(families: List<String>) {
    TODO("Implement setFontFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontFamilies(std::vector<SkString> families) {
   *         fFontFamilies = std::move(families);
   *     }
   * ```
   */
  public fun getBaselineShift(): Int {
    TODO("Implement getBaselineShift")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getBaselineShift() const { return fBaselineShift; }
   * ```
   */
  public fun setBaselineShift(baselineShift: SkScalar) {
    TODO("Implement setBaselineShift")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBaselineShift(SkScalar baselineShift) { fBaselineShift = baselineShift; }
   * ```
   */
  public fun setHeight(height: SkScalar) {
    TODO("Implement setHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHeight(SkScalar height) { fHeight = height; }
   * ```
   */
  public fun getHeight(): Int {
    TODO("Implement getHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getHeight() const { return fHeightOverride ? fHeight : 0; }
   * ```
   */
  public fun setHeightOverride(heightOverride: Boolean) {
    TODO("Implement setHeightOverride")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHeightOverride(bool heightOverride) { fHeightOverride = heightOverride; }
   * ```
   */
  public fun getHeightOverride(): Boolean {
    TODO("Implement getHeightOverride")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getHeightOverride() const { return fHeightOverride; }
   * ```
   */
  public fun setHalfLeading(halfLeading: Boolean) {
    TODO("Implement setHalfLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHalfLeading(bool halfLeading) { fHalfLeading = halfLeading; }
   * ```
   */
  public fun getHalfLeading(): Boolean {
    TODO("Implement getHalfLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getHalfLeading() const { return fHalfLeading; }
   * ```
   */
  public fun setLetterSpacing(letterSpacing: SkScalar) {
    TODO("Implement setLetterSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLetterSpacing(SkScalar letterSpacing) { fLetterSpacing = letterSpacing; }
   * ```
   */
  public fun getLetterSpacing(): Int {
    TODO("Implement getLetterSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getLetterSpacing() const { return fLetterSpacing; }
   * ```
   */
  public fun setWordSpacing(wordSpacing: SkScalar) {
    TODO("Implement setWordSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * void setWordSpacing(SkScalar wordSpacing) { fWordSpacing = wordSpacing; }
   * ```
   */
  public fun getWordSpacing(): Int {
    TODO("Implement getWordSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getWordSpacing() const { return fWordSpacing; }
   * ```
   */
  public fun getTypeface(): Int {
    TODO("Implement getTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface* getTypeface() const { return fTypeface.get(); }
   * ```
   */
  public fun refTypeface(): Int {
    TODO("Implement refTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> refTypeface() const { return fTypeface; }
   * ```
   */
  public fun setTypeface(typeface: SkSp<SkTypeface>) {
    TODO("Implement setTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTypeface(sk_sp<SkTypeface> typeface) { fTypeface = std::move(typeface); }
   * ```
   */
  public fun getLocale(): Int {
    TODO("Implement getLocale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getLocale() const { return fLocale; }
   * ```
   */
  public fun setLocale(locale: String) {
    TODO("Implement setLocale")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLocale(const SkString& locale) { fLocale = locale; }
   * ```
   */
  public fun getTextBaseline(): Int {
    TODO("Implement getTextBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * TextBaseline getTextBaseline() const { return fTextBaseline; }
   * ```
   */
  public fun setTextBaseline(baseline: TextBaseline) {
    TODO("Implement setTextBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTextBaseline(TextBaseline baseline) { fTextBaseline = baseline; }
   * ```
   */
  public fun getFontMetrics(metrics: SkFontMetrics?) {
    TODO("Implement getFontMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextStyle::getFontMetrics(SkFontMetrics* metrics) const {
   *     SkFont font(fTypeface, fFontSize);
   *     font.setEdging(fEdging);
   *     font.setSubpixel(fSubpixel);
   *     font.setHinting(fHinting);
   *     font.getMetrics(metrics);
   *     if (fHeightOverride) {
   *         auto multiplier = fHeight * fFontSize;
   *         auto height = metrics->fDescent - metrics->fAscent + metrics->fLeading;
   *         metrics->fAscent = (metrics->fAscent - metrics->fLeading / 2) * multiplier / height;
   *         metrics->fDescent = (metrics->fDescent + metrics->fLeading / 2) * multiplier / height;
   *
   *     } else {
   *         metrics->fAscent = (metrics->fAscent - metrics->fLeading / 2);
   *         metrics->fDescent = (metrics->fDescent + metrics->fLeading / 2);
   *     }
   *     // If we shift the baseline we need to make sure the shifted text fits the line
   *     metrics->fAscent += fBaselineShift;
   *     metrics->fDescent += fBaselineShift;
   * }
   * ```
   */
  public fun isPlaceholder(): Boolean {
    TODO("Implement isPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isPlaceholder() const { return fIsPlaceholder; }
   * ```
   */
  public fun setPlaceholder() {
    TODO("Implement setPlaceholder")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPlaceholder() { fIsPlaceholder = true; }
   * ```
   */
  public fun setFontEdging(edging: SkFont.Edging) {
    TODO("Implement setFontEdging")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontEdging(SkFont::Edging edging) { fEdging = edging; }
   * ```
   */
  public fun getFontEdging(): Int {
    TODO("Implement getFontEdging")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont::Edging getFontEdging() const { return fEdging; }
   * ```
   */
  public fun setSubpixel(subpixel: Boolean) {
    TODO("Implement setSubpixel")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSubpixel(bool subpixel) { fSubpixel = subpixel; }
   * ```
   */
  public fun getSubpixel(): Boolean {
    TODO("Implement getSubpixel")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getSubpixel() const { return fSubpixel; }
   * ```
   */
  public fun setFontHinting(hinting: SkFontHinting) {
    TODO("Implement setFontHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontHinting(SkFontHinting hinting) { fHinting = hinting; }
   * ```
   */
  public fun getFontHinting(): Int {
    TODO("Implement getFontHinting")
  }

  public companion object {
    private val kDefaultFontFamilies: Int? = TODO("Initialize kDefaultFontFamilies")
  }
}
