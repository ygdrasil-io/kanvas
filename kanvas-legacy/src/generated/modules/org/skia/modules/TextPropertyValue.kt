package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct TextPropertyValue {
 *     sk_sp<SkTypeface>       fTypeface;
 *     SkString                fText;
 *     float                   fTextSize       = 0,
 *                             fMinTextSize    = 0,                                 // when auto-sizing
 *                             fMaxTextSize    = std::numeric_limits<float>::max(), // when auto-sizing
 *                             fStrokeWidth    = 0,
 *                             fLineHeight     = 0,
 *                             fLineShift      = 0,
 *                             fAscent         = 0;
 *     size_t                  fMaxLines       = 0;                                 // when auto-sizing
 *     SkTextUtils::Align      fHAlign         = SkTextUtils::kLeft_Align;
 *     Shaper::VAlign          fVAlign         = Shaper::VAlign::kTop;
 *     Shaper::ResizePolicy    fResize         = Shaper::ResizePolicy::kNone;
 *     Shaper::LinebreakPolicy fLineBreak      = Shaper::LinebreakPolicy::kExplicit;
 *     Shaper::Direction       fDirection      = Shaper::Direction::kLTR;
 *     Shaper::Capitalization  fCapitalization = Shaper::Capitalization::kNone;
 *     SkRect                  fBox            = SkRect::MakeEmpty();
 *     SkColor                 fFillColor      = SK_ColorTRANSPARENT,
 *                             fStrokeColor    = SK_ColorTRANSPARENT;
 *     TextPaintOrder          fPaintOrder     = TextPaintOrder::kFillStroke;
 *     SkPaint::Join           fStrokeJoin     = SkPaint::Join::kMiter_Join;
 *     bool                    fHasFill        = false,
 *                             fHasStroke      = false;
 *     sk_sp<GlyphDecorator>   fDecorator;
 *                             // The locale to be used for text shaping, in BCP47 form.  This includes
 *                             // support for RFC6067 extensions, so one can e.g. select strict line
 *                             // breaking rules for certain scripts: ja-u-lb-strict.
 *                             // Pass an empty string to use the system locale.
 *     SkString                fLocale;
 *                             // Optional font family name, to be passed to the font manager for
 *                             // fallback.
 *     SkString                fFontFamily;
 *
 *     bool operator==(const TextPropertyValue& other) const;
 *     bool operator!=(const TextPropertyValue& other) const;
 * }
 * ```
 */
public open class TextPropertyValue public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface>       fTypeface
   * ```
   */
  public var fTypeface: Int,
  /**
   * C++ original:
   * ```cpp
   * SkString                fText
   * ```
   */
  public var fText: Int,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0
   * ```
   */
  public var fTextSize: Float,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0,
   *                             fMinTextSize    = 0
   * ```
   */
  public var fMinTextSize: Float,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0,
   *                             fMinTextSize    = 0,                                 // when auto-sizing
   *                             fMaxTextSize
   * ```
   */
  public var fMaxTextSize: Float,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0,
   *                             fMinTextSize    = 0,                                 // when auto-sizing
   *                             fMaxTextSize    = std::numeric_limits<float>::max(), // when auto-sizing
   *                             fStrokeWidth    = 0
   * ```
   */
  public var fStrokeWidth: Float,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0,
   *                             fMinTextSize    = 0,                                 // when auto-sizing
   *                             fMaxTextSize    = std::numeric_limits<float>::max(), // when auto-sizing
   *                             fStrokeWidth    = 0,
   *                             fLineHeight     = 0
   * ```
   */
  public var fLineHeight: Float,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0,
   *                             fMinTextSize    = 0,                                 // when auto-sizing
   *                             fMaxTextSize    = std::numeric_limits<float>::max(), // when auto-sizing
   *                             fStrokeWidth    = 0,
   *                             fLineHeight     = 0,
   *                             fLineShift      = 0
   * ```
   */
  public var fLineShift: Float,
  /**
   * C++ original:
   * ```cpp
   * float                   fTextSize       = 0,
   *                             fMinTextSize    = 0,                                 // when auto-sizing
   *                             fMaxTextSize    = std::numeric_limits<float>::max(), // when auto-sizing
   *                             fStrokeWidth    = 0,
   *                             fLineHeight     = 0,
   *                             fLineShift      = 0,
   *                             fAscent         = 0
   * ```
   */
  public var fAscent: Float,
  /**
   * C++ original:
   * ```cpp
   * size_t                  fMaxLines       = 0
   * ```
   */
  public var fMaxLines: ULong,
  /**
   * C++ original:
   * ```cpp
   * SkTextUtils::Align      fHAlign
   * ```
   */
  public var fHAlign: Int,
  /**
   * C++ original:
   * ```cpp
   * Shaper::VAlign          fVAlign
   * ```
   */
  public var fVAlign: Int,
  /**
   * C++ original:
   * ```cpp
   * Shaper::ResizePolicy    fResize
   * ```
   */
  public var fResize: Int,
  /**
   * C++ original:
   * ```cpp
   * Shaper::LinebreakPolicy fLineBreak
   * ```
   */
  public var fLineBreak: Int,
  /**
   * C++ original:
   * ```cpp
   * Shaper::Direction       fDirection
   * ```
   */
  public var fDirection: Int,
  /**
   * C++ original:
   * ```cpp
   * Shaper::Capitalization  fCapitalization
   * ```
   */
  public var fCapitalization: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRect                  fBox
   * ```
   */
  public var fBox: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor                 fFillColor
   * ```
   */
  public var fFillColor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor                 fFillColor      = SK_ColorTRANSPARENT,
   *                             fStrokeColor
   * ```
   */
  public var fStrokeColor: Int,
  /**
   * C++ original:
   * ```cpp
   * TextPaintOrder          fPaintOrder     = TextPaintOrder::kFillStroke
   * ```
   */
  public var fPaintOrder: TextPaintOrder,
  /**
   * C++ original:
   * ```cpp
   * SkPaint::Join           fStrokeJoin
   * ```
   */
  public var fStrokeJoin: Int,
  /**
   * C++ original:
   * ```cpp
   * bool                    fHasFill        = false
   * ```
   */
  public var fHasFill: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool                    fHasFill        = false,
   *                             fHasStroke      = false
   * ```
   */
  public var fHasStroke: Boolean,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<GlyphDecorator>   fDecorator
   * ```
   */
  public var fDecorator: Int,
  /**
   * C++ original:
   * ```cpp
   * SkString                fLocale
   * ```
   */
  public var fLocale: Int,
  /**
   * C++ original:
   * ```cpp
   * SkString                fFontFamily
   * ```
   */
  public var fFontFamily: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool TextPropertyValue::operator==(const TextPropertyValue& other) const {
   *     return fTypeface == other.fTypeface
   *         && fText == other.fText
   *         && fTextSize == other.fTextSize
   *         && fStrokeWidth == other.fStrokeWidth
   *         && fLineHeight == other.fLineHeight
   *         && fLineShift == other.fLineShift
   *         && fAscent == other.fAscent
   *         && fMaxLines == other.fMaxLines
   *         && fHAlign == other.fHAlign
   *         && fVAlign == other.fVAlign
   *         && fResize == other.fResize
   *         && fLineBreak == other.fLineBreak
   *         && fDirection == other.fDirection
   *         && fCapitalization == other.fCapitalization
   *         && fBox == other.fBox
   *         && fFillColor == other.fFillColor
   *         && fStrokeColor == other.fStrokeColor
   *         && fPaintOrder == other.fPaintOrder
   *         && fStrokeJoin == other.fStrokeJoin
   *         && fHasFill == other.fHasFill
   *         && fHasStroke == other.fHasStroke
   *         && fDecorator == other.fDecorator
   *         && fLocale == other.fLocale
   *         && fFontFamily == other.fFontFamily;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}

public typealias TextValue = TextPropertyValue
