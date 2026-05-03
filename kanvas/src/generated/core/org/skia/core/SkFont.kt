package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFont {
 * public:
 *     /** Whether edge pixels draw opaque or with partial transparency.
 *     */
 *     enum class Edging {
 *         kAlias,              //!< no transparent pixels on glyph edges
 *         kAntiAlias,          //!< may have transparent pixels on glyph edges
 *         kSubpixelAntiAlias,  //!< glyph positioned in pixel using transparency
 *     };
 *
 *     /** Constructs SkFont with default values.
 *
 *         @return  default initialized SkFont
 *     */
 *     SkFont();
 *
 *     /** Constructs SkFont with default values with SkTypeface and size.
 *
 *         @param typeface  font and style used to draw and measure text
 *         @param size      EM size in local coordinate units
 *         @return          initialized SkFont
 *     */
 *     SkFont(sk_sp<SkTypeface> typeface, SkScalar size);
 *
 *     /** Constructs SkFont with default values with SkTypeface.
 *
 *         @param typeface  font and style used to draw and measure text
 *         @return          initialized SkFont
 *     */
 *     explicit SkFont(sk_sp<SkTypeface> typeface);
 *
 *
 *     /** Constructs SkFont with default values with SkTypeface and size in points,
 *         horizontal scale, and horizontal skew. Horizontal scale emulates condensed
 *         and expanded fonts. Horizontal skew emulates oblique fonts.
 *
 *         @param typeface  font and style used to draw and measure text
 *         @param size      EM size in local coordinate units
 *         @param scaleX    text horizontal scale
 *         @param skewX     additional shear on x-axis relative to y-axis
 *         @return          initialized SkFont
 *     */
 *     SkFont(sk_sp<SkTypeface> typeface, SkScalar size, SkScalar scaleX, SkScalar skewX);
 *
 *
 *     /** Compares SkFont and font, and returns true if they are equivalent.
 *         May return false if SkTypeface has identical contents but different pointers.
 *
 *         @param font  font to compare
 *         @return      true if SkFont pair are equivalent
 *     */
 *     bool operator==(const SkFont& font) const;
 *
 *     /** Compares SkFont and font, and returns true if they are not equivalent.
 *         May return true if SkTypeface has identical contents but different pointers.
 *
 *         @param font  font to compare
 *         @return      true if SkFont pair are not equivalent
 *     */
 *     bool operator!=(const SkFont& font) const { return !(*this == font); }
 *
 *     /** If true, instructs the font manager to always hint glyphs.
 *         Returned value is only meaningful if platform uses FreeType as the font manager.
 *
 *         @return  true if all glyphs are hinted
 *     */
 *     bool isForceAutoHinting() const { return SkToBool(fFlags & kForceAutoHinting_PrivFlag); }
 *
 *     /** Returns true if font engine may return glyphs from font bitmaps instead of from outlines.
 *
 *         @return  true if glyphs may be font bitmaps
 *     */
 *     bool isEmbeddedBitmaps() const { return SkToBool(fFlags & kEmbeddedBitmaps_PrivFlag); }
 *
 *     /** Returns true if glyphs may be drawn at sub-pixel offsets.
 *
 *         @return  true if glyphs may be drawn at sub-pixel offsets.
 *     */
 *     bool isSubpixel() const { return SkToBool(fFlags & kSubpixel_PrivFlag); }
 *
 *     /** Returns true if font and glyph metrics are requested to be linearly scalable.
 *
 *         @return  true if font and glyph metrics are requested to be linearly scalable.
 *     */
 *     bool isLinearMetrics() const { return SkToBool(fFlags & kLinearMetrics_PrivFlag); }
 *
 *     /** Returns true if bold is approximated by increasing the stroke width when creating glyph
 *         bitmaps from outlines.
 *
 *         @return  bold is approximated through stroke width
 *     */
 *     bool isEmbolden() const { return SkToBool(fFlags & kEmbolden_PrivFlag); }
 *
 *     /** Returns true if baselines will be snapped to pixel positions when the current transformation
 *         matrix is axis aligned.
 *
 *         @return  baselines may be snapped to pixels
 *      */
 *     bool isBaselineSnap() const { return SkToBool(fFlags & kBaselineSnap_PrivFlag); }
 *
 *     /** Sets whether to always hint glyphs.
 *         If forceAutoHinting is set, instructs the font manager to always hint glyphs.
 *
 *         Only affects platforms that use FreeType as the font manager.
 *
 *         @param forceAutoHinting  setting to always hint glyphs
 *     */
 *     void setForceAutoHinting(bool forceAutoHinting);
 *
 *     /** Requests, but does not require, to use bitmaps in fonts instead of outlines.
 *
 *         @param embeddedBitmaps  setting to use bitmaps in fonts
 *     */
 *     void setEmbeddedBitmaps(bool embeddedBitmaps);
 *
 *     /** Requests, but does not require, that glyphs respect sub-pixel positioning.
 *
 *         @param subpixel  setting for sub-pixel positioning
 *     */
 *     void setSubpixel(bool subpixel);
 *
 *     /** Requests, but does not require, linearly scalable font and glyph metrics.
 *
 *         For outline fonts 'true' means font and glyph metrics should ignore hinting and rounding.
 *         Note that some bitmap formats may not be able to scale linearly and will ignore this flag.
 *
 *         @param linearMetrics  setting for linearly scalable font and glyph metrics.
 *     */
 *     void setLinearMetrics(bool linearMetrics);
 *
 *     /** Increases stroke width when creating glyph bitmaps to approximate a bold typeface.
 *
 *         @param embolden  setting for bold approximation
 *     */
 *     void setEmbolden(bool embolden);
 *
 *     /** Requests that baselines be snapped to pixels when the current transformation matrix is axis
 *         aligned.
 *
 *         @param baselineSnap  setting for baseline snapping to pixels
 *     */
 *     void setBaselineSnap(bool baselineSnap);
 *
 *     /** Whether edge pixels draw opaque or with partial transparency.
 *     */
 *     Edging getEdging() const { return (Edging)fEdging; }
 *
 *     /** Requests, but does not require, that edge pixels draw opaque or with
 *         partial transparency.
 *     */
 *     void setEdging(Edging edging);
 *
 *     /** Sets level of glyph outline adjustment.
 *         Does not check for valid values of hintingLevel.
 *     */
 *     void setHinting(SkFontHinting hintingLevel);
 *
 *     /** Returns level of glyph outline adjustment.
 *      */
 *     SkFontHinting getHinting() const { return (SkFontHinting)fHinting; }
 *
 *     /** Returns a font with the same attributes of this font, but with the specified size.
 *         Returns nullptr if size is less than zero, infinite, or NaN.
 *
 *         @param size  EM size in local coordinate units
 *         @return      initialized SkFont
 *      */
 *     SkFont makeWithSize(SkScalar size) const;
 *
 *     /** Does not alter SkTypeface SkRefCnt.
 *
 *         @return  non-null SkTypeface
 *     */
 *     SkTypeface* getTypeface() const {
 *         SkASSERT(fTypeface);
 *         return fTypeface.get();
 *     }
 *
 *     /** Return EM size in local coordinate units.
 *         See https://skia.org/docs/user/coordinates/#local-coordinates .
 *
 *         @return  EM size in local coordinate units
 *     */
 *     SkScalar    getSize() const { return fSize; }
 *
 *     /** Returns text scale on x-axis.
 *         Default value is 1.
 *
 *         @return  text horizontal scale
 *     */
 *     SkScalar    getScaleX() const { return fScaleX; }
 *
 *     /** Returns text skew on x-axis.
 *         Default value is zero.
 *
 *         @return  additional shear on x-axis relative to y-axis
 *     */
 *     SkScalar    getSkewX() const { return fSkewX; }
 *
 *     /** Increases SkTypeface SkRefCnt by one.
 *
 *         @return  A non-null SkTypeface.
 *     */
 *     sk_sp<SkTypeface> refTypeface() const {
 *         SkASSERT(fTypeface);
 *         return fTypeface;
 *     }
 *
 *     /** Sets SkTypeface to typeface, decreasing SkRefCnt of the previous SkTypeface.
 *         Pass nullptr to clear SkTypeface and use an empty typeface (which draws nothing).
 *         Increments tf SkRefCnt by one.
 *
 *         @param tf  font and style used to draw text
 *     */
 *     void setTypeface(sk_sp<SkTypeface> tf);
 *
 *     /** Sets the EM size in local coordinate units.
 *         See https://skia.org/docs/user/coordinates/#local-coordinates .
 *         Has no effect if textSize is not greater than or equal to zero.
 *
 *         @param textSize  EM size in local coordinate units
 *     */
 *     void setSize(SkScalar textSize);
 *
 *     /** Sets text scale on x-axis.
 *         Default value is 1.
 *
 *         @param scaleX  text horizontal scale
 *     */
 *     void setScaleX(SkScalar scaleX);
 *
 *     /** Sets text skew on x-axis.
 *         Default value is zero.
 *
 *         @param skewX  additional shear on x-axis relative to y-axis
 *     */
 *     void setSkewX(SkScalar skewX);
 *
 *     /** Converts text into glyph indices.
 *         Returns the number of glyph indices represented by text.
 *         SkTextEncoding specifies how text represents characters or glyphs.
 *         glyphs may be empty, to compute the glyph count.
 *
 *         Does not check text for valid character codes or valid glyph indices.
 *
 *         If byteLength equals zero, returns zero.
 *         If byteLength includes a partial character, the partial character is ignored.
 *
 *         If encoding is SkTextEncoding::kUTF8 and text contains an invalid UTF-8 sequence,
 *         zero is returned.
 *
 *         When encoding is SkTextEncoding::kUTF8, SkTextEncoding::kUTF16, or
 *         SkTextEncoding::kUTF32; then each Unicode codepoint is mapped to a
 *         single glyph.  This function uses the default character-to-glyph
 *         mapping from the SkTypeface and maps characters not found in the
 *         SkTypeface to zero.
 *
 *         If glyphs.size() is not sufficient to store all the glyphs, no glyphs are copied.
 *         The total glyph count is returned for subsequent buffer reallocation.
 *
 *         @param text          character storage encoded with SkTextEncoding
 *         @param byteLength    length of character storage in bytes
 *         @param glyphs        storage for glyph indices; may be empty
 *         @return number of glyphs represented by text of length byteLength
 *     */
 *     size_t textToGlyphs(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                         SkSpan<SkGlyphID> glyphs) const;
 *
 *     /** Returns glyph index for Unicode character.
 *
 *         If the character is not supported by the SkTypeface, returns 0.
 *
 *         @param uni  Unicode character
 *         @return     glyph index
 *     */
 *     SkGlyphID unicharToGlyph(SkUnichar uni) const;
 *
 *     void unicharsToGlyphs(SkSpan<const SkUnichar> src, SkSpan<SkGlyphID> dst) const;
 *
 *     /** Returns number of glyphs represented by text.
 *
 *         If encoding is SkTextEncoding::kUTF8, SkTextEncoding::kUTF16, or
 *         SkTextEncoding::kUTF32; then each Unicode codepoint is mapped to a
 *         single glyph.
 *
 *         @param text          character storage encoded with SkTextEncoding
 *         @param byteLength    length of character storage in bytes
 *         @return              number of glyphs represented by text of length byteLength
 *     */
 *     size_t countText(const void* text, size_t byteLength, SkTextEncoding encoding) const {
 *         return this->textToGlyphs(text, byteLength, encoding, {});
 *     }
 *
 *     /** Returns the advance width of text.
 *         The advance is the normal distance to move before drawing additional text.
 *         Returns the bounding box of text if bounds is not nullptr.
 *
 *         @param text        character storage encoded with SkTextEncoding
 *         @param byteLength  length of character storage in bytes
 *         @param bounds      returns bounding box relative to (0, 0) if not nullptr
 *         @return            the sum of the default advance widths
 *     */
 *     SkScalar measureText(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                          SkRect* bounds = nullptr) const {
 *         return this->measureText(text, byteLength, encoding, bounds, nullptr);
 *     }
 *
 *     /** Returns the advance width of text.
 *         The advance is the normal distance to move before drawing additional text.
 *         Returns the bounding box of text if bounds is not nullptr. The paint
 *         stroke settings, mask filter, or path effect may modify the bounds.
 *
 *         @param text        character storage encoded with SkTextEncoding
 *         @param byteLength  length of character storage in bytes
 *         @param bounds      returns bounding box relative to (0, 0) if not nullptr
 *         @param paint       optional; may be nullptr
 *         @return            the sum of the default advance widths
 *     */
 *     SkScalar measureText(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                          SkRect* bounds, const SkPaint* paint) const;
 *
 *     /** Retrieves the advance and bounds for each glyph in glyphs.
 *         widths receives min(widths.size(), glyphs.size()) values.
 *         bounds receives min(bounds.size(), glyphs.size()) values.
 *
 *         @param glyphs      array of glyph indices to be measured
 *         @param widths      returns text advances for each glyph
 *         @param bounds      returns bounds for each glyph relative to (0, 0)
 *         @param paint       optional, specifies stroking, SkPathEffect and SkMaskFilter
 *      */
 *     void getWidthsBounds(SkSpan<const SkGlyphID> glyphs, SkSpan<SkScalar> widths, SkSpan<SkRect> bounds,
 *                          const SkPaint* paint) const;
 *
 *     /** Retrieves the advance and bounds for each glyph in glyphs.
 *         widths receives min(widths.size(), glyphs.size()) values.
 *
 *         @param glyphs      array of glyph indices to be measured
 *         @param widths      returns text advances for each glyph
 *      */
 *     void getWidths(SkSpan<const SkGlyphID> glyphs, SkSpan<SkScalar> widths) const {
 *         this->getWidthsBounds(glyphs, widths, {}, nullptr);
 *     }
 *     SkScalar getWidth(SkGlyphID glyph) const {
 *         SkScalar width;
 *         this->getWidthsBounds({&glyph, 1}, {&width, 1}, {}, nullptr);
 *         return width;
 *     }
 *
 *     /** Retrieves the bounds for each glyph in glyphs.
 *         bounds receives min(bounds.size(), glyphs.size()) values.
 *         If paint is not nullptr, its stroking, SkPathEffect, and SkMaskFilter fields are respected.
 *
 *         @param glyphs      array of glyph indices to be measured
 *         @param bounds      returns bounds for each glyph relative to (0, 0); may be nullptr
 *         @param paint       optional, specifies stroking, SkPathEffect, and SkMaskFilter
 *      */
 *     void getBounds(SkSpan<const SkGlyphID> glyphs, SkSpan<SkRect> bounds,
 *                    const SkPaint* paint) const {
 *         this->getWidthsBounds(glyphs, {}, bounds, paint);
 *     }
 *     SkRect getBounds(SkGlyphID glyph, const SkPaint* paint) const {
 *         SkRect bounds;
 *         this->getBounds({&glyph, 1}, {&bounds, 1}, paint);
 *         return bounds;
 *     }
 *
 *     /** Retrieves the positions for each glyph, beginning at the specified origin.
 *         pos receives min(pos.size(), glyphs.size()) values.
 *
 *         @param glyphs   array of glyph indices to be positioned
 *         @param pos      returns glyphs positions
 *         @param origin   location of the first glyph. Defaults to {0, 0}.
 *      */
 *     void getPos(SkSpan<const SkGlyphID> glyphs, SkSpan<SkPoint> pos, SkPoint origin = {0, 0}) const;
 *
 *     /** Retrieves the x-positions for each glyph, beginning at the specified origin.
 *         xpos receives min(xpos.size(), glyphs.size()) values.
 *
 *         @param glyphs   array of glyph indices to be positioned
 *         @param xpos     returns glyphs x-positions
 *         @param origin   x-position of the first glyph. Defaults to 0.
 *      */
 *     void getXPos(SkSpan<const SkGlyphID> glyphs, SkSpan<SkScalar> xpos, SkScalar origin = 0) const;
 *
 *     /** Returns intervals [start, end] describing lines parallel to the advance that intersect
 *      *  with the glyphs.
 *      *
 *      *  @param glyphs   the glyphs to intersect
 *      *  @param pos      the position of each glyph
 *      *  @param top      the top of the line intersecting
 *      *  @param bottom   the bottom of the line intersecting
 *         @return         array of pairs of x values [start, end]. May be empty.
 *      */
 *     std::vector<SkScalar> getIntercepts(SkSpan<const SkGlyphID> glyphs,
 *                                         SkSpan<const SkPoint> pos,
 *                                         SkScalar top, SkScalar bottom,
 *                                         const SkPaint* = nullptr) const;
 *
 *     /*
 *      * If the specified glyph can be represented as a path, return its path.
 *      * If it is not (e.g. it is represented with a bitmap) return {}.
 *      *
 *      * Note: an 'empty' glyph (e.g. what a space " " character might map to) can return
 *      * a path, but that path may have zero contours.
 *      */
 *     std::optional<SkPath> getPath(SkGlyphID glyphID) const;
 *
 *     /** Returns path corresponding to glyph array.
 *
 *         @param glyphIDs      array of glyph indices
 *         @param glyphPathProc function returning one glyph description as path
 *         @param ctx           function context
 *    */
 *     void getPaths(SkSpan<const SkGlyphID> glyphIDs,
 *                   void (*glyphPathProc)(const SkPath* pathOrNull, const SkMatrix& mx, void* ctx),
 *                   void* ctx) const;
 *
 *     /** Returns SkFontMetrics associated with SkTypeface.
 *         The return value is the recommended spacing between lines: the sum of metrics
 *         descent, ascent, and leading.
 *         If metrics is not nullptr, SkFontMetrics is copied to metrics.
 *         Results are scaled by text size but does not take into account
 *         dimensions required by text scale, text skew, fake bold,
 *         style stroke, and SkPathEffect.
 *
 *         @param metrics  storage for SkFontMetrics; may be nullptr
 *         @return         recommended spacing between lines
 *     */
 *     SkScalar getMetrics(SkFontMetrics* metrics) const;
 *
 *     /** Returns the recommended spacing between lines: the sum of metrics
 *         descent, ascent, and leading.
 *         Result is scaled by text size but does not take into account
 *         dimensions required by stroking and SkPathEffect.
 *         Returns the same result as getMetrics().
 *
 *         @return  recommended spacing between lines
 *     */
 *     SkScalar getSpacing() const { return this->getMetrics(nullptr); }
 *
 *     /** Dumps fields of the font to SkDebugf. May change its output over time, so clients should
 *      *  not rely on this for anything specific. Used to aid in debugging.
 *      */
 *     void dump() const;
 *
 *     using sk_is_trivially_relocatable = std::true_type;
 *
 * #ifdef SK_SUPPORT_UNSPANNED_APIS
 *     int textToGlyphs(const void* text, size_t byteLength, SkTextEncoding encoding,
 *                      SkGlyphID glyphs[], int maxGlyphCount) const {
 *         return (int)this->textToGlyphs(text, byteLength, encoding, {glyphs, maxGlyphCount});
 *     }
 *     void unicharsToGlyphs(const SkUnichar uni[], int count, SkGlyphID glyphs[]) const {
 *         this->unicharsToGlyphs({uni, count}, {glyphs, count});
 *     }
 *
 *     void getPos(const SkGlyphID glyphs[], int count, SkPoint pos[], SkPoint origin = {0, 0}) const {
 *         this->getPos({glyphs, count}, {pos, count}, origin);
 *     }
 *     void getXPos(const SkGlyphID glyphs[], int count, SkScalar xpos[], SkScalar origin = 0) const {
 *         this->getXPos({glyphs, count}, {xpos, count}, origin);
 *     }
 *     void getPaths(const SkGlyphID glyphIDs[], int count,
 *                   void (*glyphPathProc)(const SkPath* pathOrNull, const SkMatrix& mx, void* ctx),
 *                   void* ctx) const {
 *         this->getPaths({glyphIDs, count}, glyphPathProc, ctx);
 *     }
 *     void getWidthsBounds(const SkGlyphID glyphs[], int count, SkScalar widths[], SkRect bounds[],
 *                          const SkPaint* paint) const {
 *         const auto nw = widths ? count : 0;
 *         const auto nb = bounds ? count : 0;
 *         this->getWidthsBounds({glyphs, count}, {widths, nw}, {bounds, nb}, paint);
 *     }
 *     void getWidths(const SkGlyphID glyphs[], int count, SkScalar widths[], SkRect bounds[]) const {
 *         const auto nw = widths ? count : 0;
 *         const auto nb = bounds ? count : 0;
 *         this->getWidthsBounds({glyphs, count}, {widths, nw}, {bounds, nb}, nullptr);
 *     }
 *     void getWidths(const SkGlyphID glyphs[], int count, SkScalar widths[], std::nullptr_t) const {
 *         this->getWidthsBounds({glyphs, count}, {widths, count}, {}, nullptr);
 *     }
 *     void getWidths(const SkGlyphID glyphs[], int count, SkScalar widths[]) const {
 *         this->getWidthsBounds({glyphs, count}, {widths, count}, {}, nullptr);
 *     }
 *     void getBounds(const SkGlyphID glyphs[], int count, SkRect bounds[],
 *                    const SkPaint* paint) const {
 *         this->getWidthsBounds({glyphs, count}, {}, {bounds, count}, paint);
 *     }
 *
 *     std::vector<SkScalar> getIntercepts(const SkGlyphID glyphs[], int count, const SkPoint pos[],
 *                                         SkScalar top, SkScalar bottom,
 *                                         const SkPaint* paint = nullptr) const {
 *         return this->getIntercepts({glyphs, count}, {pos, count}, top, bottom, paint);
 *     }
 * #endif
 *
 *
 * private:
 *     enum PrivFlags {
 *         kForceAutoHinting_PrivFlag      = 1 << 0,
 *         kEmbeddedBitmaps_PrivFlag       = 1 << 1,
 *         kSubpixel_PrivFlag              = 1 << 2,
 *         kLinearMetrics_PrivFlag         = 1 << 3,
 *         kEmbolden_PrivFlag              = 1 << 4,
 *         kBaselineSnap_PrivFlag          = 1 << 5,
 *     };
 *
 *     static constexpr unsigned kAllFlags = kForceAutoHinting_PrivFlag
 *                                         | kEmbeddedBitmaps_PrivFlag
 *                                         | kSubpixel_PrivFlag
 *                                         | kLinearMetrics_PrivFlag
 *                                         | kEmbolden_PrivFlag
 *                                         | kBaselineSnap_PrivFlag;
 *
 *     sk_sp<SkTypeface> fTypeface;
 *     SkScalar    fSize;
 *     SkScalar    fScaleX;
 *     SkScalar    fSkewX;
 *     uint8_t     fFlags;
 *     uint8_t     fEdging;
 *     uint8_t     fHinting;
 *
 *     static_assert(::sk_is_trivially_relocatable<decltype(fTypeface)>::value);
 *
 *     SkScalar setupForAsPaths(SkPaint*);
 *     bool hasSomeAntiAliasing() const;
 *
 *     friend class SkFontPriv;
 *     friend class skcpu::GlyphRunListPainter;
 *     friend class SkStrikeSpec;
 *     friend class SkRemoteGlyphCacheTest;
 * }
 * ```
 */
public abstract class SkFont public constructor() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr unsigned kAllFlags = kForceAutoHinting_PrivFlag
   *                                         | kEmbeddedBitmaps_PrivFlag
   *                                         | kSubpixel_PrivFlag
   *                                         | kLinearMetrics_PrivFlag
   *                                         | kEmbolden_PrivFlag
   *                                         | kBaselineSnap_PrivFlag
   * ```
   */
  private var fTypeface: Int = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypeface
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * SkScalar    fSize
   * ```
   */
  private var fScaleX: Int = TODO("Initialize fScaleX")

  /**
   * C++ original:
   * ```cpp
   * SkScalar    fScaleX
   * ```
   */
  private var fSkewX: Int = TODO("Initialize fSkewX")

  /**
   * C++ original:
   * ```cpp
   * SkScalar    fSkewX
   * ```
   */
  private var fFlags: UByte = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * uint8_t     fFlags
   * ```
   */
  private var fEdging: UByte = TODO("Initialize fEdging")

  /**
   * C++ original:
   * ```cpp
   * uint8_t     fEdging
   * ```
   */
  private var fHinting: UByte = TODO("Initialize fHinting")

  /**
   * C++ original:
   * ```cpp
   * SkFont::SkFont() : SkFont(nullptr, kDefault_Size) {}
   * ```
   */
  public constructor(
    face: SkSp<SkTypeface>,
    size: SkScalar,
    scaleX: SkScalar,
    skewX: SkScalar,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont::SkFont(sk_sp<SkTypeface> face, SkScalar size, SkScalar scaleX, SkScalar skewX)
   *     : fTypeface(std::move(face))
   *     , fSize(valid_size(size))
   *     , fScaleX(scaleX)
   *     , fSkewX(skewX)
   *     , fFlags(kDefault_Flags)
   *     , fEdging(static_cast<unsigned>(kDefault_Edging))
   *     , fHinting(static_cast<unsigned>(kDefault_Hinting)) {
   *     if (!fTypeface) {
   *         fTypeface = SkTypeface::MakeEmpty();
   *     }
   * }
   * ```
   */
  public constructor(face: SkSp<SkTypeface>, size: SkScalar) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont::SkFont(sk_sp<SkTypeface> face, SkScalar size) : SkFont(std::move(face), size, 1, 0) {}
   * ```
   */
  public constructor(face: SkSp<SkTypeface>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkFont::operator==(const SkFont& b) const {
   *     return  fTypeface.get() == b.fTypeface.get() &&
   *             fSize           == b.fSize &&
   *             fScaleX         == b.fScaleX &&
   *             fSkewX          == b.fSkewX &&
   *             fFlags          == b.fFlags &&
   *             fEdging         == b.fEdging &&
   *             fHinting        == b.fHinting;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkFont& font) const { return !(*this == font); }
   * ```
   */
  public fun isForceAutoHinting(): Boolean {
    TODO("Implement isForceAutoHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isForceAutoHinting() const { return SkToBool(fFlags & kForceAutoHinting_PrivFlag); }
   * ```
   */
  public fun isEmbeddedBitmaps(): Boolean {
    TODO("Implement isEmbeddedBitmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmbeddedBitmaps() const { return SkToBool(fFlags & kEmbeddedBitmaps_PrivFlag); }
   * ```
   */
  public fun isSubpixel(): Boolean {
    TODO("Implement isSubpixel")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSubpixel() const { return SkToBool(fFlags & kSubpixel_PrivFlag); }
   * ```
   */
  public fun isLinearMetrics(): Boolean {
    TODO("Implement isLinearMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isLinearMetrics() const { return SkToBool(fFlags & kLinearMetrics_PrivFlag); }
   * ```
   */
  public fun isEmbolden(): Boolean {
    TODO("Implement isEmbolden")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmbolden() const { return SkToBool(fFlags & kEmbolden_PrivFlag); }
   * ```
   */
  public fun isBaselineSnap(): Boolean {
    TODO("Implement isBaselineSnap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isBaselineSnap() const { return SkToBool(fFlags & kBaselineSnap_PrivFlag); }
   * ```
   */
  public fun setForceAutoHinting(forceAutoHinting: Boolean) {
    TODO("Implement setForceAutoHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setForceAutoHinting(bool predicate) {
   *     fFlags = set_clear_mask(fFlags, predicate, kForceAutoHinting_PrivFlag);
   * }
   * ```
   */
  public fun setEmbeddedBitmaps(embeddedBitmaps: Boolean) {
    TODO("Implement setEmbeddedBitmaps")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setEmbeddedBitmaps(bool predicate) {
   *     fFlags = set_clear_mask(fFlags, predicate, kEmbeddedBitmaps_PrivFlag);
   * }
   * ```
   */
  public fun setSubpixel(subpixel: Boolean) {
    TODO("Implement setSubpixel")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setSubpixel(bool predicate) {
   *     fFlags = set_clear_mask(fFlags, predicate, kSubpixel_PrivFlag);
   * }
   * ```
   */
  public fun setLinearMetrics(linearMetrics: Boolean) {
    TODO("Implement setLinearMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setLinearMetrics(bool predicate) {
   *     fFlags = set_clear_mask(fFlags, predicate, kLinearMetrics_PrivFlag);
   * }
   * ```
   */
  public fun setEmbolden(embolden: Boolean) {
    TODO("Implement setEmbolden")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setEmbolden(bool predicate) {
   *     fFlags = set_clear_mask(fFlags, predicate, kEmbolden_PrivFlag);
   * }
   * ```
   */
  public fun setBaselineSnap(baselineSnap: Boolean) {
    TODO("Implement setBaselineSnap")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setBaselineSnap(bool predicate) {
   *     fFlags = set_clear_mask(fFlags, predicate, kBaselineSnap_PrivFlag);
   * }
   * ```
   */
  public fun getEdging(): Edging {
    TODO("Implement getEdging")
  }

  /**
   * C++ original:
   * ```cpp
   * Edging getEdging() const { return (Edging)fEdging; }
   * ```
   */
  public fun setEdging(edging: Edging) {
    TODO("Implement setEdging")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setEdging(Edging e) {
   *     fEdging = SkToU8(e);
   * }
   * ```
   */
  public fun setHinting(hintingLevel: SkFontHinting) {
    TODO("Implement setHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setHinting(SkFontHinting h) {
   *     fHinting = SkToU8(h);
   * }
   * ```
   */
  public fun getHinting(): SkFontHinting {
    TODO("Implement getHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontHinting getHinting() const { return (SkFontHinting)fHinting; }
   * ```
   */
  public fun makeWithSize(size: SkScalar): SkFont {
    TODO("Implement makeWithSize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFont SkFont::makeWithSize(SkScalar newSize) const {
   *     SkFont font = *this;
   *     font.setSize(newSize);
   *     return font;
   * }
   * ```
   */
  public fun getTypeface(): Int {
    TODO("Implement getTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface* getTypeface() const {
   *         SkASSERT(fTypeface);
   *         return fTypeface.get();
   *     }
   * ```
   */
  public fun getSize(): Int {
    TODO("Implement getSize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar    getSize() const { return fSize; }
   * ```
   */
  public fun getScaleX(): Int {
    TODO("Implement getScaleX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar    getScaleX() const { return fScaleX; }
   * ```
   */
  public fun getSkewX(): Int {
    TODO("Implement getSkewX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar    getSkewX() const { return fSkewX; }
   * ```
   */
  public fun refTypeface(): Int {
    TODO("Implement refTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> refTypeface() const {
   *         SkASSERT(fTypeface);
   *         return fTypeface;
   *     }
   * ```
   */
  public fun setTypeface(tf: SkSp<SkTypeface>) {
    TODO("Implement setTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setTypeface(sk_sp<SkTypeface> tf) {
   *     fTypeface = std::move(tf);
   *     if (!fTypeface) {
   *         fTypeface = SkTypeface::MakeEmpty();
   *     }
   * }
   * ```
   */
  public fun setSize(textSize: SkScalar) {
    TODO("Implement setSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setSize(SkScalar size) {
   *     fSize = valid_size(size);
   * }
   * ```
   */
  public fun setScaleX(scaleX: SkScalar) {
    TODO("Implement setScaleX")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setScaleX(SkScalar scale) {
   *     fScaleX = scale;
   * }
   * ```
   */
  public fun setSkewX(skewX: SkScalar) {
    TODO("Implement setSkewX")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::setSkewX(SkScalar skew) {
   *     fSkewX = skew;
   * }
   * ```
   */
  public fun textToGlyphs(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
    glyphs: SkSpan<SkGlyphID>,
  ): ULong {
    TODO("Implement textToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkFont::textToGlyphs(const void* text, size_t byteLength, SkTextEncoding encoding,
   *                          SkSpan<SkGlyphID> glyphs) const {
   *     return this->getTypeface()->textToGlyphs(text, byteLength, encoding, glyphs);
   * }
   * ```
   */
  public fun unicharToGlyph(uni: SkUnichar): Int {
    TODO("Implement unicharToGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphID SkFont::unicharToGlyph(SkUnichar uni) const {
   *     return this->getTypeface()->unicharToGlyph(uni);
   * }
   * ```
   */
  public fun unicharsToGlyphs(src: SkSpan<SkUnichar>, dst: SkSpan<SkGlyphID>) {
    TODO("Implement unicharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::unicharsToGlyphs(SkSpan<const SkUnichar> unis, SkSpan<SkGlyphID> glyphs) const {
   *     this->getTypeface()->unicharsToGlyphs(unis, glyphs);
   * }
   * ```
   */
  public fun countText(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
  ): ULong {
    TODO("Implement countText")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t countText(const void* text, size_t byteLength, SkTextEncoding encoding) const {
   *         return this->textToGlyphs(text, byteLength, encoding, {});
   *     }
   * ```
   */
  public fun measureText(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
    bounds: SkRect? = null,
  ): Int {
    TODO("Implement measureText")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar measureText(const void* text, size_t byteLength, SkTextEncoding encoding,
   *                          SkRect* bounds = nullptr) const {
   *         return this->measureText(text, byteLength, encoding, bounds, nullptr);
   *     }
   * ```
   */
  public fun measureText(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
    bounds: SkRect?,
    paint: SkPaint?,
  ): Int {
    TODO("Implement measureText")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkFont::measureText(const void* text, size_t length, SkTextEncoding encoding,
   *                              SkRect* bounds, const SkPaint* paint) const {
   *
   *     SkAutoToGlyphs atg(*this, text, length, encoding);
   *     const SkSpan<const SkGlyphID> glyphIDs = atg.glyphs();
   *
   *     if (glyphIDs.size() == 0) {
   *         if (bounds) {
   *             bounds->setEmpty();
   *         }
   *         return 0;
   *     }
   *
   *     auto [strikeSpec, strikeToSourceScale] = SkStrikeSpec::MakeCanonicalized(*this, paint);
   *     SkBulkGlyphMetrics metrics{strikeSpec};
   *     SkSpan<const SkGlyph*> glyphs = metrics.glyphs(glyphIDs);
   *
   *     SkScalar width = 0;
   *     if (bounds) {
   *         *bounds = glyphs[0]->rect();
   *         width = glyphs[0]->advanceX();
   *         for (size_t i = 1; i < glyphIDs.size(); ++i) {
   *             SkRect r = glyphs[i]->rect();
   *             r.offset(width, 0);
   *             bounds->join(r);
   *             width += glyphs[i]->advanceX();
   *         }
   *     } else {
   *         for (auto glyph : glyphs) {
   *             width += glyph->advanceX();
   *         }
   *     }
   *
   *     if (strikeToSourceScale != 1) {
   *         width *= strikeToSourceScale;
   *         if (bounds) {
   *             bounds->fLeft   *= strikeToSourceScale;
   *             bounds->fTop    *= strikeToSourceScale;
   *             bounds->fRight  *= strikeToSourceScale;
   *             bounds->fBottom *= strikeToSourceScale;
   *         }
   *     }
   *
   *     return width;
   * }
   * ```
   */
  public fun getWidthsBounds(
    glyphs: SkSpan<SkGlyphID>,
    widths: SkSpan<SkScalar>,
    bounds: SkSpan<SkRect>,
    paint: SkPaint?,
  ) {
    TODO("Implement getWidthsBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::getWidthsBounds(SkSpan<const SkGlyphID> glyphIDs,
   *                              SkSpan<SkScalar> widths,
   *                              SkSpan<SkRect> bounds,
   *                              const SkPaint* paint) const {
   *     auto [strikeSpec, strikeToSourceScale] = SkStrikeSpec::MakeCanonicalized(*this, paint);
   *     SkBulkGlyphMetrics metrics{strikeSpec};
   *     SkSpan<const SkGlyph*> glyphs = metrics.glyphs(glyphIDs);
   *
   *     if (bounds.size()) {
   *         const auto n = std::min(bounds.size(), glyphs.size());
   *         for (auto [bound, glyph] : SkMakeZip(bounds.first(n), glyphs.first(n))) {
   *             bound = scale_pos(glyph->rect(), strikeToSourceScale);
   *         }
   *     }
   *
   *     if (widths.size()) {
   *         const auto n = std::min(widths.size(), glyphs.size());
   *         for (auto [width, glyph] : SkMakeZip(widths.first(n), glyphs.first(n))) {
   *             width = glyph->advanceX() * strikeToSourceScale;
   *         }
   *     }
   * }
   * ```
   */
  public fun getWidths(glyphs: SkSpan<SkGlyphID>, widths: SkSpan<SkScalar>) {
    TODO("Implement getWidths")
  }

  /**
   * C++ original:
   * ```cpp
   * void getWidths(SkSpan<const SkGlyphID> glyphs, SkSpan<SkScalar> widths) const {
   *         this->getWidthsBounds(glyphs, widths, {}, nullptr);
   *     }
   * ```
   */
  public fun getWidth(glyph: SkGlyphID): Int {
    TODO("Implement getWidth")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getWidth(SkGlyphID glyph) const {
   *         SkScalar width;
   *         this->getWidthsBounds({&glyph, 1}, {&width, 1}, {}, nullptr);
   *         return width;
   *     }
   * ```
   */
  public fun getBounds(
    glyphs: SkSpan<SkGlyphID>,
    bounds: SkSpan<SkRect>,
    paint: SkPaint?,
  ) {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void getBounds(SkSpan<const SkGlyphID> glyphs, SkSpan<SkRect> bounds,
   *                    const SkPaint* paint) const {
   *         this->getWidthsBounds(glyphs, {}, bounds, paint);
   *     }
   * ```
   */
  public fun getBounds(glyph: SkGlyphID, paint: SkPaint?): Int {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect getBounds(SkGlyphID glyph, const SkPaint* paint) const {
   *         SkRect bounds;
   *         this->getBounds({&glyph, 1}, {&bounds, 1}, paint);
   *         return bounds;
   *     }
   * ```
   */
  public fun getPos(
    glyphIDs: Int,
    pos: Int,
    origin: Int,
  ) {
    TODO("Implement getPos")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::getPos(SkSpan<const SkGlyphID> glyphIDs, SkSpan<SkPoint> pos, SkPoint origin) const {
   *     auto [strikeSpec, strikeToSourceScale] = SkStrikeSpec::MakeCanonicalized(*this);
   *     SkBulkGlyphMetrics metrics{strikeSpec};
   *     SkSpan<const SkGlyph*> glyphs = metrics.glyphs(glyphIDs);
   *
   *     SkPoint sum = origin;
   *     const auto n = std::min(pos.size(), glyphs.size());
   *     for (auto [position, glyph] : SkMakeZip(pos.first(n), glyphs.first(n))) {
   *         position = sum;
   *         sum += glyph->advanceVector() * strikeToSourceScale;
   *     }
   * }
   * ```
   */
  public abstract fun getXPos(
    glyphs: SkSpan<SkGlyphID>,
    xpos: SkSpan<SkScalar>,
    origin: SkScalar = 0,
  )

  /**
   * C++ original:
   * ```cpp
   * void SkFont::getXPos(SkSpan<const SkGlyphID> gIDs, SkSpan<SkScalar> xpos, SkScalar origin) const {
   *     auto [strikeSpec, strikeToSourceScale] = SkStrikeSpec::MakeCanonicalized(*this);
   *     SkBulkGlyphMetrics metrics{strikeSpec};
   *     SkSpan<const SkGlyph*> glyphs = metrics.glyphs(gIDs);
   *
   *     SkScalar loc = origin;
   *     const auto n = std::min(xpos.size(), glyphs.size());
   *     for (auto [xposition, glyph] : SkMakeZip(xpos.first(n), glyphs.first(n))) {
   *         xposition = loc;
   *         loc += glyph->advanceX() * strikeToSourceScale;
   *     }
   * }
   * ```
   */
  public fun getIntercepts(
    glyphs: SkSpan<SkGlyphID>,
    pos: SkSpan<SkPoint>,
    top: SkScalar,
    bottom: SkScalar,
    paintPtr: SkPaint? = null,
  ): Int {
    TODO("Implement getIntercepts")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkScalar> SkFont::getIntercepts(SkSpan<const SkGlyphID> glyphs,
   *                                             SkSpan<const SkPoint> positions,
   *                                             SkScalar top, SkScalar bottom,
   *                                             const SkPaint* paintPtr) const {
   *     const auto count = std::min(glyphs.size(), positions.size());
   *     if (count == 0) {
   *         return std::vector<SkScalar>();
   *     }
   *
   *     const SkPaint paint(paintPtr ? *paintPtr : SkPaint());
   *     const SkScalar bounds[] = {top, bottom};
   *     const sktext::GlyphRun run(*this, positions, glyphs, {}, {}, {});
   *
   *     std::vector<SkScalar> result;
   *     result.resize(count * 2);   // worst case allocation
   *     int intervalCount = 0;
   *     intervalCount = get_glyph_run_intercepts(run, paint, bounds, result.data(), &intervalCount);
   *     result.resize(intervalCount);
   *     return result;
   * }
   * ```
   */
  public fun getPath(glyphID: SkGlyphID): Int {
    TODO("Implement getPath")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPath> SkFont::getPath(SkGlyphID glyphID) const {
   *     std::optional<SkPath> result;
   *
   *     this->getPaths({&glyphID, 1}, [](const SkPath* path, const SkMatrix& mx, void* ctx) {
   *         if (path) {
   *             auto* result = static_cast<std::optional<SkPath>*>(ctx);
   *             *result = path->tryMakeTransform(mx);
   *         }
   *     }, &result);
   *
   *     return result;
   * }
   * ```
   */
  public fun getPaths(
    glyphIDs: SkSpan<SkGlyphID>,
    param1: (
      Any,
      Any,
      Int,
    ) -> Unit,
    ctx: Unit?,
  ) {
    TODO("Implement getPaths")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::getPaths(SkSpan<const SkGlyphID> glyphIDs,
   *                       void (*proc)(const SkPath*, const SkMatrix&, void*), void* ctx) const {
   *     SkFont font(*this);
   *     SkScalar scale = font.setupForAsPaths(nullptr);
   *     const SkMatrix mx = SkMatrix::Scale(scale, scale);
   *
   *     SkStrikeSpec strikeSpec = SkStrikeSpec::MakeWithNoDevice(font);
   *     SkBulkGlyphMetricsAndPaths paths{strikeSpec};
   *     SkSpan<const SkGlyph*> glyphs = paths.glyphs(glyphIDs);
   *
   *     for (auto glyph : glyphs) {
   *         proc(glyph->path(), mx, ctx);
   *     }
   * }
   * ```
   */
  public fun getMetrics(metrics: SkFontMetrics?): Int {
    TODO("Implement getMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkFont::getMetrics(SkFontMetrics* metrics) const {
   *
   *     auto [strikeSpec, strikeToSourceScale] = SkStrikeSpec::MakeCanonicalized(*this, nullptr);
   *
   *     SkFontMetrics storage;
   *     if (nullptr == metrics) {
   *         metrics = &storage;
   *     }
   *
   *     auto cache = strikeSpec.findOrCreateStrike();
   *     *metrics = cache->getFontMetrics();
   *
   *     if (strikeToSourceScale != 1) {
   *         SkFontPriv::ScaleFontMetrics(metrics, strikeToSourceScale);
   *     }
   *     return metrics->fDescent - metrics->fAscent + metrics->fLeading;
   * }
   * ```
   */
  public fun getSpacing(): Int {
    TODO("Implement getSpacing")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getSpacing() const { return this->getMetrics(nullptr); }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFont::dump() const {
   *     SkDebugf("typeface %p\n", fTypeface.get());
   *     SkDebugf("size %g\n", fSize);
   *     SkDebugf("skewx %g\n", fSkewX);
   *     SkDebugf("scalex %g\n", fScaleX);
   *     SkDebugf("flags 0x%X\n", fFlags);
   *     SkDebugf("edging %u\n", (unsigned)fEdging);
   *     SkDebugf("hinting %u\n", (unsigned)fHinting);
   * }
   * ```
   */
  private fun setupForAsPaths(paint: SkPaint?): Int {
    TODO("Implement setupForAsPaths")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkFont::setupForAsPaths(SkPaint* paint) {
   *     constexpr uint32_t flagsToIgnore = kEmbeddedBitmaps_PrivFlag |
   *                                        kForceAutoHinting_PrivFlag;
   *
   *     fFlags = (fFlags & ~flagsToIgnore) | kSubpixel_PrivFlag;
   *     this->setHinting(SkFontHinting::kNone);
   *
   *     if (this->getEdging() == Edging::kSubpixelAntiAlias) {
   *         this->setEdging(Edging::kAntiAlias);
   *     }
   *
   *     if (paint) {
   *         paint->setStyle(SkPaint::kFill_Style);
   *         paint->setPathEffect(nullptr);
   *     }
   *     SkScalar textSize = fSize;
   *     this->setSize(SkIntToScalar(SkFontPriv::kCanonicalTextSizeForPaths));
   *     return textSize / SkFontPriv::kCanonicalTextSizeForPaths;
   * }
   * ```
   */
  private fun hasSomeAntiAliasing(): Boolean {
    TODO("Implement hasSomeAntiAliasing")
  }

  public enum class Edging {
    kAlias,
    kAntiAlias,
    kSubpixelAntiAlias,
  }

  public enum class PrivFlags {
    kForceAutoHinting_PrivFlag,
    kEmbeddedBitmaps_PrivFlag,
    kSubpixel_PrivFlag,
    kLinearMetrics_PrivFlag,
    kEmbolden_PrivFlag,
    kBaselineSnap_PrivFlag,
  }

  public companion object {
    private val kAllFlags: UInt = TODO("Initialize kAllFlags")
  }
}
