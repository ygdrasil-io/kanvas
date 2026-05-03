package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.u16string
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct ParagraphStyle {
 *     ParagraphStyle();
 *
 *     bool operator==(const ParagraphStyle& rhs) const {
 *         return this->fHeight == rhs.fHeight &&
 *                this->fEllipsis == rhs.fEllipsis &&
 *                this->fEllipsisUtf16 == rhs.fEllipsisUtf16 &&
 *                this->fTextDirection == rhs.fTextDirection && this->fTextAlign == rhs.fTextAlign &&
 *                this->fDefaultTextStyle == rhs.fDefaultTextStyle &&
 *                this->fReplaceTabCharacters == rhs.fReplaceTabCharacters &&
 *                this->fFakeMissingFontStyles == rhs.fFakeMissingFontStyles;
 *
 *     }
 *
 *     const StrutStyle& getStrutStyle() const { return fStrutStyle; }
 *     void setStrutStyle(StrutStyle strutStyle) { fStrutStyle = std::move(strutStyle); }
 *
 *     const TextStyle& getTextStyle() const { return fDefaultTextStyle; }
 *     void setTextStyle(const TextStyle& textStyle) { fDefaultTextStyle = textStyle; }
 *
 *     TextDirection getTextDirection() const { return fTextDirection; }
 *     void setTextDirection(TextDirection direction) { fTextDirection = direction; }
 *
 *     TextAlign getTextAlign() const { return fTextAlign; }
 *     void setTextAlign(TextAlign align) { fTextAlign = align; }
 *
 *     size_t getMaxLines() const { return fLinesLimit; }
 *     void setMaxLines(size_t maxLines) { fLinesLimit = maxLines; }
 *
 *     SkString getEllipsis() const { return fEllipsis; }
 *     std::u16string getEllipsisUtf16() const { return fEllipsisUtf16; }
 *     void setEllipsis(const std::u16string& ellipsis) {  fEllipsisUtf16 = ellipsis; }
 *     void setEllipsis(const SkString& ellipsis) { fEllipsis = ellipsis; }
 *
 *     SkScalar getHeight() const { return fHeight; }
 *     void setHeight(SkScalar height) { fHeight = height; }
 *
 *     TextHeightBehavior getTextHeightBehavior() const { return fTextHeightBehavior; }
 *     void setTextHeightBehavior(TextHeightBehavior v) { fTextHeightBehavior = v; }
 *
 *     bool unlimited_lines() const {
 *         return fLinesLimit == std::numeric_limits<size_t>::max();
 *     }
 *     bool ellipsized() const { return !fEllipsis.isEmpty() || !fEllipsisUtf16.empty(); }
 *     TextAlign effective_align() const;
 *     bool hintingIsOn() const { return fHintingIsOn; }
 *     void turnHintingOff() { fHintingIsOn = false; }
 *
 *     bool fakeMissingFontStyles() const { return fFakeMissingFontStyles; }
 *     void setFakeMissingFontStyles(bool value) { fFakeMissingFontStyles = value; }
 *
 *     bool getReplaceTabCharacters() const { return fReplaceTabCharacters; }
 *     void setReplaceTabCharacters(bool value) { fReplaceTabCharacters = value; }
 *
 *     bool getApplyRoundingHack() const { return fApplyRoundingHack; }
 *     void setApplyRoundingHack(bool value) { fApplyRoundingHack = value; }
 *
 * private:
 *     StrutStyle fStrutStyle;
 *     TextStyle fDefaultTextStyle;
 *     TextAlign fTextAlign;
 *     TextDirection fTextDirection;
 *     size_t fLinesLimit;
 *     std::u16string fEllipsisUtf16;
 *     SkString fEllipsis;
 *     SkScalar fHeight;
 *     TextHeightBehavior fTextHeightBehavior;
 *     bool fHintingIsOn;
 *     bool fReplaceTabCharacters;
 *     bool fFakeMissingFontStyles;
 *     bool fApplyRoundingHack = true;
 * }
 * ```
 */
public data class ParagraphStyle public constructor(
  /**
   * C++ original:
   * ```cpp
   * StrutStyle fStrutStyle
   * ```
   */
  private var fStrutStyle: StrutStyle,
  /**
   * C++ original:
   * ```cpp
   * TextStyle fDefaultTextStyle
   * ```
   */
  private var fDefaultTextStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * TextAlign fTextAlign
   * ```
   */
  private var fTextAlign: Int,
  /**
   * C++ original:
   * ```cpp
   * TextDirection fTextDirection
   * ```
   */
  private var fTextDirection: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fLinesLimit
   * ```
   */
  private var fLinesLimit: ULong,
  /**
   * C++ original:
   * ```cpp
   * std::u16string fEllipsisUtf16
   * ```
   */
  private var fEllipsisUtf16: Int,
  /**
   * C++ original:
   * ```cpp
   * SkString fEllipsis
   * ```
   */
  private var fEllipsis: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHeight
   * ```
   */
  private var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * TextHeightBehavior fTextHeightBehavior
   * ```
   */
  private var fTextHeightBehavior: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fHintingIsOn
   * ```
   */
  private var fHintingIsOn: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fReplaceTabCharacters
   * ```
   */
  private var fReplaceTabCharacters: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fFakeMissingFontStyles
   * ```
   */
  private var fFakeMissingFontStyles: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fApplyRoundingHack = true
   * ```
   */
  private var fApplyRoundingHack: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const ParagraphStyle& rhs) const {
   *         return this->fHeight == rhs.fHeight &&
   *                this->fEllipsis == rhs.fEllipsis &&
   *                this->fEllipsisUtf16 == rhs.fEllipsisUtf16 &&
   *                this->fTextDirection == rhs.fTextDirection && this->fTextAlign == rhs.fTextAlign &&
   *                this->fDefaultTextStyle == rhs.fDefaultTextStyle &&
   *                this->fReplaceTabCharacters == rhs.fReplaceTabCharacters &&
   *                this->fFakeMissingFontStyles == rhs.fFakeMissingFontStyles;
   *
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * const StrutStyle& getStrutStyle() const { return fStrutStyle; }
   * ```
   */
  public fun getStrutStyle(): StrutStyle {
    TODO("Implement getStrutStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStrutStyle(StrutStyle strutStyle) { fStrutStyle = std::move(strutStyle); }
   * ```
   */
  public fun setStrutStyle(strutStyle: StrutStyle) {
    TODO("Implement setStrutStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextStyle& getTextStyle() const { return fDefaultTextStyle; }
   * ```
   */
  public fun getTextStyle(): Int {
    TODO("Implement getTextStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTextStyle(const TextStyle& textStyle) { fDefaultTextStyle = textStyle; }
   * ```
   */
  public fun setTextStyle(textStyle: TextStyle) {
    TODO("Implement setTextStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * TextDirection getTextDirection() const { return fTextDirection; }
   * ```
   */
  public fun getTextDirection(): Int {
    TODO("Implement getTextDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTextDirection(TextDirection direction) { fTextDirection = direction; }
   * ```
   */
  public fun setTextDirection(direction: TextDirection) {
    TODO("Implement setTextDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * TextAlign getTextAlign() const { return fTextAlign; }
   * ```
   */
  public fun getTextAlign(): Int {
    TODO("Implement getTextAlign")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTextAlign(TextAlign align) { fTextAlign = align; }
   * ```
   */
  public fun setTextAlign(align: TextAlign) {
    TODO("Implement setTextAlign")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getMaxLines() const { return fLinesLimit; }
   * ```
   */
  public fun getMaxLines(): ULong {
    TODO("Implement getMaxLines")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMaxLines(size_t maxLines) { fLinesLimit = maxLines; }
   * ```
   */
  public fun setMaxLines(maxLines: ULong) {
    TODO("Implement setMaxLines")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString getEllipsis() const { return fEllipsis; }
   * ```
   */
  public fun getEllipsis(): Int {
    TODO("Implement getEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * std::u16string getEllipsisUtf16() const { return fEllipsisUtf16; }
   * ```
   */
  public fun getEllipsisUtf16(): Int {
    TODO("Implement getEllipsisUtf16")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEllipsis(const std::u16string& ellipsis) {  fEllipsisUtf16 = ellipsis; }
   * ```
   */
  public fun setEllipsis(ellipsis: u16string) {
    TODO("Implement setEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEllipsis(const SkString& ellipsis) { fEllipsis = ellipsis; }
   * ```
   */
  public fun setEllipsis(ellipsis: String) {
    TODO("Implement setEllipsis")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getHeight() const { return fHeight; }
   * ```
   */
  public fun getHeight(): Int {
    TODO("Implement getHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHeight(SkScalar height) { fHeight = height; }
   * ```
   */
  public fun setHeight(height: SkScalar) {
    TODO("Implement setHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * TextHeightBehavior getTextHeightBehavior() const { return fTextHeightBehavior; }
   * ```
   */
  public fun getTextHeightBehavior(): Int {
    TODO("Implement getTextHeightBehavior")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTextHeightBehavior(TextHeightBehavior v) { fTextHeightBehavior = v; }
   * ```
   */
  public fun setTextHeightBehavior(v: TextHeightBehavior) {
    TODO("Implement setTextHeightBehavior")
  }

  /**
   * C++ original:
   * ```cpp
   * bool unlimited_lines() const {
   *         return fLinesLimit == std::numeric_limits<size_t>::max();
   *     }
   * ```
   */
  public fun unlimitedLines(): Boolean {
    TODO("Implement unlimitedLines")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ellipsized() const { return !fEllipsis.isEmpty() || !fEllipsisUtf16.empty(); }
   * ```
   */
  public fun ellipsized(): Boolean {
    TODO("Implement ellipsized")
  }

  /**
   * C++ original:
   * ```cpp
   * TextAlign ParagraphStyle::effective_align() const {
   *     if (fTextAlign == TextAlign::kStart) {
   *         return (fTextDirection == TextDirection::kLtr) ? TextAlign::kLeft : TextAlign::kRight;
   *     } else if (fTextAlign == TextAlign::kEnd) {
   *         return (fTextDirection == TextDirection::kLtr) ? TextAlign::kRight : TextAlign::kLeft;
   *     } else {
   *         return fTextAlign;
   *     }
   * }
   * ```
   */
  public fun effectiveAlign(): Int {
    TODO("Implement effectiveAlign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hintingIsOn() const { return fHintingIsOn; }
   * ```
   */
  public fun hintingIsOn(): Boolean {
    TODO("Implement hintingIsOn")
  }

  /**
   * C++ original:
   * ```cpp
   * void turnHintingOff() { fHintingIsOn = false; }
   * ```
   */
  public fun turnHintingOff() {
    TODO("Implement turnHintingOff")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fakeMissingFontStyles() const { return fFakeMissingFontStyles; }
   * ```
   */
  public fun fakeMissingFontStyles(): Boolean {
    TODO("Implement fakeMissingFontStyles")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFakeMissingFontStyles(bool value) { fFakeMissingFontStyles = value; }
   * ```
   */
  public fun setFakeMissingFontStyles(`value`: Boolean) {
    TODO("Implement setFakeMissingFontStyles")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getReplaceTabCharacters() const { return fReplaceTabCharacters; }
   * ```
   */
  public fun getReplaceTabCharacters(): Boolean {
    TODO("Implement getReplaceTabCharacters")
  }

  /**
   * C++ original:
   * ```cpp
   * void setReplaceTabCharacters(bool value) { fReplaceTabCharacters = value; }
   * ```
   */
  public fun setReplaceTabCharacters(`value`: Boolean) {
    TODO("Implement setReplaceTabCharacters")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getApplyRoundingHack() const { return fApplyRoundingHack; }
   * ```
   */
  public fun getApplyRoundingHack(): Boolean {
    TODO("Implement getApplyRoundingHack")
  }

  /**
   * C++ original:
   * ```cpp
   * void setApplyRoundingHack(bool value) { fApplyRoundingHack = value; }
   * ```
   */
  public fun setApplyRoundingHack(`value`: Boolean) {
    TODO("Implement setApplyRoundingHack")
  }
}
