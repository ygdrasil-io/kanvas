package org.skia.core

import kotlin.Int
import kotlin.String
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * struct SkAdvancedTypefaceMetrics {
 *     // The PostScript name of the font. See `FontName` and `BaseFont` in PDF standard.
 *     SkString fPostScriptName;
 *
 *     // These enum values match the values used in the PDF file format.
 *     enum StyleFlags : uint32_t {
 *         kFixedPitch_Style  = 0x00000001,
 *         kSerif_Style       = 0x00000002,
 *         kScript_Style      = 0x00000008,
 *         kItalic_Style      = 0x00000040,
 *         kAllCaps_Style     = 0x00010000,
 *         kSmallCaps_Style   = 0x00020000,
 *         kForceBold_Style   = 0x00040000
 *     };
 *     StyleFlags fStyle = (StyleFlags)0;        // Font style characteristics.
 *
 *     enum FontType : uint8_t {
 *         kType1_Font,
 *         kType1CID_Font,
 *         kCFF_Font,
 *         kTrueType_Font,
 *         kOther_Font,
 *     };
 *     // The type of the underlying font program.  This field determines which
 *     // of the following fields are valid.  If it is kOther_Font the per glyph
 *     // information will never be populated.
 *     FontType fType = kOther_Font;
 *
 *     enum FontFlags : uint8_t {
 *         kVariable_FontFlag       = 1 << 0,  //!<May be true for Type1, CFF, or TrueType fonts.
 *         kNotEmbeddable_FontFlag  = 1 << 1,  //!<May not be embedded.
 *         kNotSubsettable_FontFlag = 1 << 2,  //!<May not be subset.
 *         kAltDataFormat_FontFlag  = 1 << 3,  //!<Data compressed. Table access may still work.
 *     };
 *     FontFlags fFlags = (FontFlags)0;  // Global font flags.
 *
 *     int16_t fItalicAngle = 0;  // Counterclockwise degrees from vertical of the
 *                                // dominant vertical stroke for an Italic face.
 *     // The following fields are all in font units.
 *     int16_t fAscent = 0;       // Max height above baseline, not including accents.
 *     int16_t fDescent = 0;      // Max depth below baseline (negative).
 *     int16_t fStemV = 0;        // Thickness of dominant vertical stem.
 *     int16_t fCapHeight = 0;    // Height (from baseline) of top of flat capitals.
 *
 *     SkIRect fBBox = {0, 0, 0, 0};  // The bounding box of all glyphs (in font units).
 * }
 * ```
 */
public data class SkAdvancedTypefaceMetrics public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString fPostScriptName
   * ```
   */
  public var fPostScriptName: String,
  /**
   * C++ original:
   * ```cpp
   * StyleFlags fStyle = (StyleFlags)0
   * ```
   */
  public var fStyle: StyleFlags,
  /**
   * C++ original:
   * ```cpp
   * FontType fType = kOther_Font
   * ```
   */
  public var fType: FontType,
  /**
   * C++ original:
   * ```cpp
   * FontFlags fFlags = (FontFlags)0
   * ```
   */
  public var fFlags: FontFlags,
  /**
   * C++ original:
   * ```cpp
   * int16_t fItalicAngle
   * ```
   */
  public var fItalicAngle: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fAscent
   * ```
   */
  public var fAscent: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fDescent
   * ```
   */
  public var fDescent: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fStemV
   * ```
   */
  public var fStemV: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fCapHeight
   * ```
   */
  public var fCapHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fBBox
   * ```
   */
  public var fBBox: SkIRect,
) {
  public enum class StyleFlags {
    kFixedPitch_Style,
    kSerif_Style,
    kScript_Style,
    kItalic_Style,
    kAllCaps_Style,
    kSmallCaps_Style,
    kForceBold_Style,
  }

  public enum class FontType {
    kType1_Font,
    kType1CID_Font,
    kCFF_Font,
    kTrueType_Font,
    kOther_Font,
  }

  public enum class FontFlags {
    kVariable_FontFlag,
    kNotEmbeddable_FontFlag,
    kNotSubsettable_FontFlag,
    kAltDataFormat_FontFlag,
  }
}
