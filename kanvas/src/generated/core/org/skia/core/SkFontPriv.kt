package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkUnichar
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkFontPriv {
 * public:
 *     /*  This is the size we use when we ask for a glyph's path. We then
 *      *  post-transform it as we draw to match the request.
 *      *  This is done to try to re-use cache entries for the path.
 *      *
 *      *  This value is somewhat arbitrary. In theory, it could be 1, since
 *      *  we store paths as floats. However, we get the path from the font
 *      *  scaler, and it may represent its paths as fixed-point (or 26.6),
 *      *  so we shouldn't ask for something too big (might overflow 16.16)
 *      *  or too small (underflow 26.6).
 *      *
 *      *  This value could track kMaxSizeForGlyphCache, assuming the above
 *      *  constraints, but since we ask for unhinted paths, the two values
 *      *  need not match per-se.
 *      */
 *     inline static constexpr int kCanonicalTextSizeForPaths  = 64;
 *
 *     /**
 *      *  Return a matrix that applies the paint's text values: size, scale, skew
 *      */
 *     static SkMatrix MakeTextMatrix(SkScalar size, SkScalar scaleX, SkScalar skewX) {
 *         SkMatrix m = SkMatrix::Scale(size * scaleX, size);
 *         if (skewX) {
 *             m.postSkew(skewX, 0);
 *         }
 *         return m;
 *     }
 *
 *     static SkMatrix MakeTextMatrix(const SkFont& font) {
 *         return MakeTextMatrix(font.getSize(), font.getScaleX(), font.getSkewX());
 *     }
 *
 *     static void ScaleFontMetrics(SkFontMetrics*, SkScalar);
 *
 *     /**
 *         Returns the union of bounds of all glyphs.
 *         Returned dimensions are computed by font manager from font data,
 *         ignoring SkPaint::Hinting. Includes font metrics, but not fake bold or SkPathEffect.
 *
 *         If text size is large, text scale is one, and text skew is zero,
 *         returns the bounds as:
 *         { SkFontMetrics::fXMin, SkFontMetrics::fTop, SkFontMetrics::fXMax, SkFontMetrics::fBottom }.
 *
 *         @return  union of bounds of all glyphs
 *      */
 *     static SkRect GetFontBounds(const SkFont&);
 *
 *     /** Return the approximate largest dimension of typical text when transformed by the matrix.
 *      *
 *      * @param matrix  used to transform size
 *      * @param textLocation  location of the text prior to matrix transformation. Used if the
 *      *                      matrix has perspective.
 *      * @return  typical largest dimension
 *      */
 *     static SkScalar ApproximateTransformedTextSize(const SkFont& font, const SkMatrix& matrix,
 *                                                    const SkPoint& textLocation);
 *
 *     static bool IsFinite(const SkFont& font) {
 *         return SkIsFinite(font.getSize(), font.getScaleX(), font.getSkewX());
 *     }
 *
 *     // Returns the number of elements (characters or glyphs) in the array.
 *     static size_t CountTextElements(const void* text, size_t byteLength, SkTextEncoding);
 *
 *     static void GlyphsToUnichars(const SkFont&, const SkGlyphID glyphs[], int count, SkUnichar[]);
 *
 *     static void Flatten(const SkFont&, SkWriteBuffer& buffer);
 *     static bool Unflatten(SkFont*, SkReadBuffer& buffer);
 *
 *     static inline uint8_t Flags(const SkFont& font) { return font.fFlags; }
 * }
 * ```
 */
public open class SkFontPriv {
  public companion object {
    public val kCanonicalTextSizeForPaths: Int = TODO("Initialize kCanonicalTextSizeForPaths")

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix MakeTextMatrix(SkScalar size, SkScalar scaleX, SkScalar skewX) {
     *         SkMatrix m = SkMatrix::Scale(size * scaleX, size);
     *         if (skewX) {
     *             m.postSkew(skewX, 0);
     *         }
     *         return m;
     *     }
     * ```
     */
    public fun makeTextMatrix(
      size: SkScalar,
      scaleX: SkScalar,
      skewX: SkScalar,
    ): SkMatrix {
      TODO("Implement makeTextMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkMatrix MakeTextMatrix(const SkFont& font) {
     *         return MakeTextMatrix(font.getSize(), font.getScaleX(), font.getSkewX());
     *     }
     * ```
     */
    public fun makeTextMatrix(font: SkFont): SkMatrix {
      TODO("Implement makeTextMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFontPriv::ScaleFontMetrics(SkFontMetrics* metrics, SkScalar scale) {
     *     metrics->fTop *= scale;
     *     metrics->fAscent *= scale;
     *     metrics->fDescent *= scale;
     *     metrics->fBottom *= scale;
     *     metrics->fLeading *= scale;
     *     metrics->fAvgCharWidth *= scale;
     *     metrics->fMaxCharWidth *= scale;
     *     metrics->fXMin *= scale;
     *     metrics->fXMax *= scale;
     *     metrics->fXHeight *= scale;
     *     metrics->fCapHeight *= scale;
     *     metrics->fUnderlineThickness *= scale;
     *     metrics->fUnderlinePosition *= scale;
     *     metrics->fStrikeoutThickness *= scale;
     *     metrics->fStrikeoutPosition *= scale;
     * }
     * ```
     */
    public fun scaleFontMetrics(metrics: SkFontMetrics?, scale: SkScalar) {
      TODO("Implement scaleFontMetrics")
    }

    /**
     * C++ original:
     * ```cpp
     * SkRect SkFontPriv::GetFontBounds(const SkFont& font) {
     *     SkMatrix m;
     *     m.setScale(font.getSize() * font.getScaleX(), font.getSize());
     *     m.postSkew(font.getSkewX(), 0);
     *
     *     SkTypeface* typeface = font.getTypeface();
     *
     *     SkRect bounds;
     *     m.mapRect(&bounds, typeface->getBounds());
     *     return bounds;
     * }
     * ```
     */
    public fun getFontBounds(font: SkFont): SkRect {
      TODO("Implement getFontBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkFontPriv::ApproximateTransformedTextSize(const SkFont& font, const SkMatrix& matrix,
     *                                                     const SkPoint& textLocation) {
     *     if (!matrix.hasPerspective()) {
     *         return font.getSize() * matrix.getMaxScale();
     *     } else {
     *         // approximate the scale since we can't get it directly from the matrix
     *         SkScalar maxScaleSq = SkMatrixPriv::DifferentialAreaScale(matrix, textLocation);
     *         if (SkIsFinite(maxScaleSq) && !SkScalarNearlyZero(maxScaleSq)) {
     *             return font.getSize() * SkScalarSqrt(maxScaleSq);
     *         } else {
     *             return -font.getSize();
     *         }
     *     }
     * }
     * ```
     */
    public fun approximateTransformedTextSize(
      font: SkFont,
      matrix: SkMatrix,
      textLocation: SkPoint,
    ): SkScalar {
      TODO("Implement approximateTransformedTextSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsFinite(const SkFont& font) {
     *         return SkIsFinite(font.getSize(), font.getScaleX(), font.getSkewX());
     *     }
     * ```
     */
    public fun isFinite(font: SkFont): Boolean {
      TODO("Implement isFinite")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkFontPriv::CountTextElements(const void* text, size_t byteLength, SkTextEncoding encoding) {
     *     switch (encoding) {
     *         case SkTextEncoding::kUTF8:
     *             return SkUTF::CountUTF8(reinterpret_cast<const char*>(text), byteLength);
     *         case SkTextEncoding::kUTF16:
     *             return SkUTF::CountUTF16(reinterpret_cast<const uint16_t*>(text), byteLength);
     *         case SkTextEncoding::kUTF32:
     *             return byteLength >> 2;
     *         case SkTextEncoding::kGlyphID:
     *             return byteLength >> 1;
     *     }
     *     SkASSERT(false);
     *     return 0;
     * }
     * ```
     */
    public fun countTextElements(
      text: Unit?,
      byteLength: ULong,
      encoding: SkTextEncoding,
    ): Int {
      TODO("Implement countTextElements")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFontPriv::GlyphsToUnichars(const SkFont& font, const SkGlyphID glyphs[], int count,
     *                                   SkUnichar text[]) {
     *     if (count <= 0) {
     *         return;
     *     }
     *
     *     auto typeface = font.getTypeface();
     *     const unsigned numGlyphsInTypeface = typeface->countGlyphs();
     *     AutoTArray<SkUnichar> unichars(static_cast<size_t>(numGlyphsInTypeface));
     *     typeface->getGlyphToUnicodeMap(unichars);
     *
     *     for (int i = 0; i < count; ++i) {
     *         unsigned id = glyphs[i];
     *         text[i] = (id < numGlyphsInTypeface) ? unichars[id] : 0xFFFD;
     *     }
     * }
     * ```
     */
    public fun glyphsToUnichars(
      font: SkFont,
      glyphs: Array<SkGlyphID>,
      count: Int,
      text: Array<SkUnichar>,
    ) {
      TODO("Implement glyphsToUnichars")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFontPriv::Flatten(const SkFont& font, SkWriteBuffer& buffer) {
     *     SkASSERT(font.fFlags <= SkFont::kAllFlags);
     *     SkASSERT((font.fFlags & ~kMask_For_Flags) == 0);
     *     SkASSERT((font.fEdging & ~kMask_For_Edging) == 0);
     *     SkASSERT((font.fHinting & ~kMask_For_Hinting) == 0);
     *
     *     uint32_t packed = 0;
     *     packed |= font.fFlags << kShift_For_Flags;
     *     packed |= font.fEdging << kShift_For_Edging;
     *     packed |= font.fHinting << kShift_For_Hinting;
     *
     *     if (scalar_is_byte(font.fSize)) {
     *         packed |= kSize_Is_Byte_Bit;
     *         packed |= (int)font.fSize << kShift_for_Size;
     *     }
     *     if (font.fScaleX != 1) {
     *         packed |= kHas_ScaleX_Bit;
     *     }
     *     if (font.fSkewX != 0) {
     *         packed |= kHas_SkewX_Bit;
     *     }
     *     if (font.fTypeface) {
     *         packed |= kHas_Typeface_Bit;
     *     }
     *
     *     buffer.write32(packed);
     *     if (!(packed & kSize_Is_Byte_Bit)) {
     *         buffer.writeScalar(font.fSize);
     *     }
     *     if (packed & kHas_ScaleX_Bit) {
     *         buffer.writeScalar(font.fScaleX);
     *     }
     *     if (packed & kHas_SkewX_Bit) {
     *         buffer.writeScalar(font.fSkewX);
     *     }
     *     if (packed & kHas_Typeface_Bit) {
     *         buffer.writeTypeface(font.fTypeface.get());
     *     }
     * }
     * ```
     */
    public fun flatten(font: SkFont, buffer: SkWriteBuffer) {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkFontPriv::Unflatten(SkFont* font, SkReadBuffer& buffer) {
     *     const uint32_t packed = buffer.read32();
     *
     *     if (packed & kSize_Is_Byte_Bit) {
     *         font->fSize = (packed >> kShift_for_Size) & kMask_For_Size;
     *     } else {
     *         font->fSize = buffer.readScalar();
     *     }
     *     if (packed & kHas_ScaleX_Bit) {
     *         font->fScaleX = buffer.readScalar();
     *     }
     *     if (packed & kHas_SkewX_Bit) {
     *         font->fSkewX = buffer.readScalar();
     *     }
     *     if (packed & kHas_Typeface_Bit) {
     *         font->setTypeface(buffer.readTypeface());
     *     }
     *
     *     SkASSERT(SkFont::kAllFlags <= kMask_For_Flags);
     *     // we & with kAllFlags, to clear out any unknown flag bits
     *     font->fFlags = SkToU8((packed >> kShift_For_Flags) & SkFont::kAllFlags);
     *
     *     unsigned edging = (packed >> kShift_For_Edging) & kMask_For_Edging;
     *     if (edging > (unsigned)SkFont::Edging::kSubpixelAntiAlias) {
     *         edging = 0;
     *     }
     *     font->fEdging = SkToU8(edging);
     *
     *     unsigned hinting = (packed >> kShift_For_Hinting) & kMask_For_Hinting;
     *     if (hinting > (unsigned)SkFontHinting::kFull) {
     *         hinting = 0;
     *     }
     *     font->fHinting = SkToU8(hinting);
     *
     *     return buffer.isValid();
     * }
     * ```
     */
    public fun unflatten(font: SkFont?, buffer: SkReadBuffer): Boolean {
      TODO("Implement unflatten")
    }

    /**
     * C++ original:
     * ```cpp
     * static inline uint8_t Flags(const SkFont& font) { return font.fFlags; }
     * ```
     */
    public fun flags(font: SkFont): Int {
      TODO("Implement flags")
    }
  }
}
