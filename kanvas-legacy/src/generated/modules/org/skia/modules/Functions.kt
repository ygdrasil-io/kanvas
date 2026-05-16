package org.skia.modules

import SkUniqueCFRef
import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Double
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.Long
import kotlin.Pair
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import kotlin.collections.List
import kotlin.u16string
import org.skia.`external`.HbBlobT
import org.skia.`external`.HbBoolT
import org.skia.`external`.HbCodepointT
import org.skia.`external`.HbFaceT
import org.skia.`external`.HbFontFuncsT
import org.skia.`external`.HbFontT
import org.skia.`external`.HbGlyphExtentsT
import org.skia.`external`.HbPositionT
import org.skia.`external`.HbTagT
import org.skia.`external`.UBreakIterator
import org.skia.`external`.UBreakIteratorType
import org.skia.`external`.UErrorCode
import org.skia.`external`.UText
import org.skia.core.SkFourByteTag
import org.skia.core.SkPathOp
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathEffect
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar
import org.skia.foundation.SkWStream
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize
import org.skia.math.SkV2
import org.skia.math.SkV3
import org.skia.math.SkVector
import org.skia.memory.SkArenaAlloc
import org.skia.svg.SkDOM
import org.skia.svg.SkDOMNode
import org.skia.tests.Reporter
import org.skia.utils.SkTextUtils
import undefined.CFMutableDictionaryRef
import undefined.CTFontRef
import undefined.CTRunRef
import undefined.ColorPropertyHandle
import undefined.ColorPropertyValue
import undefined.HBFace
import undefined.HBFont
import undefined.SkSVGIDMapper

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isControl(SkUnichar utf8) {
 *     return (utf8 < ' ') || (utf8 >= 0x7f && utf8 <= 0x9f) ||
 *            (utf8 >= 0x200D && utf8 <= 0x200F) ||
 *            (utf8 >= 0x202A && utf8 <= 0x202E);
 * }
 * ```
 */
public fun isControl(utf8: SkUnichar) {
  TODO("Implement isControl")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isSpace(SkUnichar unichar) {
 *     static constexpr std::array<SkUnichar, 25> spaces {
 *             0x0009, // character tabulation
 *             0x000A, // line feed
 *             0x000B, // line tabulation
 *             0x000C, // form feed
 *             0x000D, // carriage return
 *             0x0020, // space
 *             0x0085, // next line
 *             0x00A0, // no-break space
 *             0x1680, // ogham space mark
 *             0x2000, // en quad
 *             0x2001, // em quad
 *             0x2002, // en space
 *             0x2003, // em space
 *             0x2004, // three-per-em space
 *             0x2005, // four-per-em space
 *             0x2006, // six-per-em space
 *             0x2007, // figure space
 *             0x2008, // punctuation space
 *             0x2009, // thin space
 *             0x200A, // hair space
 *             0x2028, // line separator
 *             0x2029, // paragraph separator
 *             0x202F, // narrow no-break space
 *             0x205F, // medium mathematical space
 *             0x3000}; // ideographic space
 *     return std::find(spaces.begin(), spaces.end(), unichar) != spaces.end();
 * }
 * ```
 */
public fun isSpace(unichar: SkUnichar) {
  TODO("Implement isSpace")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isTabulation(SkUnichar utf8) {
 *     return utf8 == '\t';
 * }
 * ```
 */
public fun isTabulation(utf8: SkUnichar) {
  TODO("Implement isTabulation")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isHardBreak(SkUnichar utf8) {
 *     return utf8 == '\n' || utf8 == u'\u2028';
 * }
 * ```
 */
public fun isHardBreak(utf8: SkUnichar) {
  TODO("Implement isHardBreak")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isEmoji(SkUnichar unichar) {
 *     SkDEBUGFAIL("isEmoji Not implemented");
 *     return false;
 * }
 * ```
 */
public fun isEmoji(unichar: SkUnichar) {
  TODO("Implement isEmoji")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isEmojiComponent(SkUnichar utf8)  {
 *     SkDEBUGFAIL("isEmojiComponent Not implemented");
 *     return false;
 * }
 * ```
 */
public fun isEmojiComponent(utf8: SkUnichar) {
  TODO("Implement isEmojiComponent")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isEmojiModifier(SkUnichar utf8)  {
 *     SkDEBUGFAIL("isEmojiModifier Not implemented");
 *     return false;
 * }
 * ```
 */
public fun isEmojiModifier(utf8: SkUnichar) {
  TODO("Implement isEmojiModifier")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isEmojiModifierBase(SkUnichar utf8) {
 *     SkDEBUGFAIL("isEmojiModifierBase Not implemented");
 *     return false;
 * }
 * ```
 */
public fun isEmojiModifierBase(utf8: SkUnichar) {
  TODO("Implement isEmojiModifierBase")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isRegionalIndicator(SkUnichar unichar) {
 *     SkDEBUGFAIL("isRegionalIndicator Not implemented");
 *     return false;
 * }
 * ```
 */
public fun isRegionalIndicator(unichar: SkUnichar) {
  TODO("Implement isRegionalIndicator")
}

/**
 * C++ original:
 * ```cpp
 * bool SkUnicodeHardCodedCharProperties::isIdeographic(SkUnichar unichar) {
 *     static constexpr std::array<std::pair<SkUnichar, SkUnichar>, 8> ranges {{
 *           {4352,   4607}, // Hangul Jamo
 *           {11904, 42191}, // CJK_Radicals
 *           {43072, 43135}, // Phags_Pa
 *           {44032, 55215}, // Hangul_Syllables
 *           {63744, 64255}, // CJK_Compatibility_Ideographs
 *           {65072, 65103}, // CJK_Compatibility_Forms
 *           {65381, 65500}, // Katakana_Hangul_Halfwidth
 *           {131072, 196607}// Supplementary_Ideographic_Plane
 *     }};
 *     for (auto range : ranges) {
 *         if (range.first <= unichar && range.second > unichar) {
 *             return true;
 *         }
 *     }
 *     return false;
 * }
 * ```
 */
public fun isIdeographic(unichar: SkUnichar) {
  TODO("Implement isIdeographic")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkUnichar utf8_next(const char** ptr, const char* end) {
 *     SkUnichar val = SkUTF::NextUTF8(ptr, end);
 *     return val < 0 ? 0xFFFD : val;
 * }
 * ```
 */
public fun utf8Next(ptr: Int?, end: String?): SkUnichar {
  TODO("Implement utf8Next")
}

/**
 * C++ original:
 * ```cpp
 * const SkICULib* SkGetICULib() {
 *     static const auto gICU = SkLoadICULib();
 *     return gICU.get();
 * }
 * ```
 */
public fun skGetICULib(): SkICULib {
  TODO("Implement skGetICULib")
}

/**
 * C++ original:
 * ```cpp
 * static inline UBreakIterator* sk_ubrk_clone(const UBreakIterator* bi, UErrorCode* status) {
 *     const auto* icu = SkGetICULib();
 *     SkASSERT(icu->f_ubrk_clone_ || icu->f_ubrk_safeClone_);
 *     return icu->f_ubrk_clone_
 *         ? icu->f_ubrk_clone_(bi, status)
 *         : icu->f_ubrk_safeClone_(bi, nullptr, nullptr, status);
 * }
 * ```
 */
public fun skUbrkClone(bi: UBreakIterator?, status: UErrorCode?): UBreakIterator {
  TODO("Implement skUbrkClone")
}

/**
 * C++ original:
 * ```cpp
 * static UText* utext_close_wrapper(UText* ut) {
 *     return sk_utext_close(ut);
 * }
 * ```
 */
public fun utextCloseWrapper(ut: UText?): UText {
  TODO("Implement utextCloseWrapper")
}

/**
 * C++ original:
 * ```cpp
 * static void ubrk_close_wrapper(UBreakIterator* bi) {
 *     sk_ubrk_close(bi);
 * }
 * ```
 */
public fun ubrkCloseWrapper(bi: UBreakIterator?) {
  TODO("Implement ubrkCloseWrapper")
}

/**
 * C++ original:
 * ```cpp
 * static UBreakIteratorType convertType(SkUnicode::BreakType type) {
 *     switch (type) {
 *         case SkUnicode::BreakType::kLines: return UBRK_LINE;
 *         case SkUnicode::BreakType::kGraphemes: return UBRK_CHARACTER;
 *         case SkUnicode::BreakType::kWords: return UBRK_WORD;
 *         case SkUnicode::BreakType::kSentences:
 *             return UBRK_SENTENCE;
 *         default:
 *             return UBRK_CHARACTER;
 *     }
 * }
 * ```
 */
public fun convertType(type: SkUnicode.BreakType): UBreakIteratorType {
  TODO("Implement convertType")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkICULib> SkLoadICULib() {
 *     return std::make_unique<SkICULib>(SkICULib{
 *         SKICU_EMIT_FUNCS
 *         &SkUbrkClone<const UBreakIterator*>::clone,
 *         nullptr,
 *         &SkUbrkGetLocaleByType<const UBreakIterator*>::getLocaleByType,
 *     });
 * }
 * ```
 */
public fun skLoadICULib(): SkICULib? {
  TODO("Implement skLoadICULib")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkData> decode_datauri(const char prefix[], const char uri[]) {
 *     // We only handle B64 encoded image dataURIs: data:image/<type>;base64,<data>
 *     // (https://en.wikipedia.org/wiki/Data_URI_scheme)
 *     static constexpr char kDataURIEncodingStr[] = ";base64,";
 *
 *     const size_t prefixLen = strlen(prefix);
 *     if (strncmp(uri, prefix, prefixLen) != 0) {
 *         return nullptr;
 *     }
 *
 *     const char* encoding = strstr(uri + prefixLen, kDataURIEncodingStr);
 *     if (!encoding) {
 *         return nullptr;
 *     }
 *
 *     const char* b64Data = encoding + std::size(kDataURIEncodingStr) - 1;
 *     size_t b64DataLen = strlen(b64Data);
 *     size_t dataLen;
 *     if (SkBase64::Decode(b64Data, b64DataLen, nullptr, &dataLen) != SkBase64::kNoError) {
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkData> data = SkData::MakeUninitialized(dataLen);
 *     void* rawData = data->writable_data();
 *     if (SkBase64::Decode(b64Data, b64DataLen, rawData, &dataLen) != SkBase64::kNoError) {
 *         return nullptr;
 *     }
 *
 *     return data;
 * }
 * ```
 */
public fun decodeDatauri(prefix: CharArray, uri: CharArray): SkSp<SkData> {
  TODO("Implement decodeDatauri")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShapers::Factory> Factory() {
 *     return sk_make_sp<PrimitiveFactory>();
 * }
 * ```
 */
public fun factory(): SkSp<SkShapers.Factory> {
  TODO("Implement factory")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool is_breaking_whitespace(SkUnichar c) {
 *     switch (c) {
 *         case 0x0020: // SPACE
 *         //case 0x00A0: // NO-BREAK SPACE
 *         case 0x1680: // OGHAM SPACE MARK
 *         case 0x180E: // MONGOLIAN VOWEL SEPARATOR
 *         case 0x2000: // EN QUAD
 *         case 0x2001: // EM QUAD
 *         case 0x2002: // EN SPACE (nut)
 *         case 0x2003: // EM SPACE (mutton)
 *         case 0x2004: // THREE-PER-EM SPACE (thick space)
 *         case 0x2005: // FOUR-PER-EM SPACE (mid space)
 *         case 0x2006: // SIX-PER-EM SPACE
 *         case 0x2007: // FIGURE SPACE
 *         case 0x2008: // PUNCTUATION SPACE
 *         case 0x2009: // THIN SPACE
 *         case 0x200A: // HAIR SPACE
 *         case 0x200B: // ZERO WIDTH SPACE
 *         case 0x202F: // NARROW NO-BREAK SPACE
 *         case 0x205F: // MEDIUM MATHEMATICAL SPACE
 *         case 0x3000: // IDEOGRAPHIC SPACE
 *         //case 0xFEFF: // ZERO WIDTH NO-BREAK SPACE
 *             return true;
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun isBreakingWhitespace(c: SkUnichar): Boolean {
  TODO("Implement isBreakingWhitespace")
}

/**
 * C++ original:
 * ```cpp
 * static size_t linebreak(const char text[], const char stop[],
 *                         const SkFont& font, SkScalar width,
 *                         SkScalar* advance,
 *                         size_t* trailing)
 * {
 *     SkScalar accumulatedWidth = 0;
 *     int glyphIndex = 0;
 *     const char* start = text;
 *     const char* wordStart = text;
 *     bool prevWS = true;
 *     *trailing = 0;
 *
 *     while (text < stop) {
 *         const char* prevText = text;
 *         SkUnichar uni = SkUTF::NextUTF8(&text, stop);
 *         accumulatedWidth += advance[glyphIndex++];
 *         bool currWS = is_breaking_whitespace(uni);
 *
 *         if (!currWS && prevWS) {
 *             wordStart = prevText;
 *         }
 *         prevWS = currWS;
 *
 *         if (width < accumulatedWidth) {
 *             bool consumeWhitespace = false;
 *             if (currWS) {
 *                 // previous fit, put this and following whitespace in trailing
 *                 if (prevText == start) {
 *                     // don't put this in trailing if it's the first thing
 *                     prevText = text;
 *                 }
 *                 consumeWhitespace = true;
 *             } else if (wordStart != start) {
 *                 // backup to the last whitespace that fit
 *                 text = wordStart;
 *             } else if (prevText > start) {
 *                 // backup to just before the glyph that didn't fit
 *                 text = prevText;
 *             } else {
 *                 // let it overflow, put any following whitespace in trailing
 *                 prevText = text;
 *                 consumeWhitespace = true;
 *             }
 *             if (consumeWhitespace) {
 *                 const char* next = text;
 *                 while (next < stop && is_breaking_whitespace(SkUTF::NextUTF8(&next, stop))) {
 *                     text = next;
 *                 }
 *                 if (trailing) {
 *                     *trailing = text - prevText;
 *                 }
 *             }
 *             break;
 *         }
 *     }
 *
 *     return text - start;
 * }
 * ```
 */
public fun linebreak(
  text: CharArray,
  stop: CharArray,
  font: SkFont,
  width: SkScalar,
  advance: SkScalar?,
  trailing: ULong?,
): ULong {
  TODO("Implement linebreak")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkShaper> PrimitiveText() { return std::make_unique<SkShaperPrimitive>(); }
 * ```
 */
public fun primitiveText(): SkShaper? {
  TODO("Implement primitiveText")
}

/**
 * C++ original:
 * ```cpp
 * static void dict_add_double(CFMutableDictionaryRef d, const void* name, double value) {
 *     SkUniqueCFRef<CFNumberRef> number(
 *             CFNumberCreate(kCFAllocatorDefault, kCFNumberDoubleType, &value));
 *     CFDictionaryAddValue(d, name, number.get());
 * }
 * ```
 */
public fun dictAddDouble(
  d: CFMutableDictionaryRef,
  name: Unit?,
  `value`: Double,
) {
  TODO("Implement dictAddDouble")
}

/**
 * C++ original:
 * ```cpp
 * static SkUniqueCFRef<CTFontRef> create_ctfont_from_font(const SkFont& font) {
 *     auto typeface = font.getTypeface();
 *     auto ctfont = SkTypeface_GetCTFontRef(typeface);
 *     if (!ctfont) {
 *         return nullptr;
 *     }
 *     return SkUniqueCFRef<CTFontRef>(
 *             CTFontCreateCopyWithAttributes(ctfont, font.getSize(), nullptr, nullptr));
 * }
 * ```
 */
public fun createCtfontFromFont(font: SkFont): SkUniqueCFRef<CTFontRef> {
  TODO("Implement createCtfontFromFont")
}

/**
 * C++ original:
 * ```cpp
 * static SkFont run_to_font(CTRunRef run, const SkFont& orig) {
 *     CFDictionaryRef attr = CTRunGetAttributes(run);
 *     CTFontRef ct = (CTFontRef)CFDictionaryGetValue(attr, kCTFontAttributeName);
 *     if (!ct) {
 *         SkDebugf("no ctfont in Run Attributes\n");
 *         CFShow(attr);
 *         return orig;
 *     }
 *     // Do I need to add a local cache, or allow the caller to manage this lookup?
 *     SkFont font(orig);
 *     font.setTypeface(SkMakeTypefaceFromCTFont(ct));
 *     return font;
 * }
 * ```
 */
public fun runToFont(run: CTRunRef, orig: SkFont): SkFont {
  TODO("Implement runToFont")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkShaper> CoreText() { return std::make_unique<SkShaper_CoreText>(); }
 * ```
 */
public fun coreText(): SkShaper? {
  TODO("Implement coreText")
}

/**
 * C++ original:
 * ```cpp
 * hb_position_t skhb_position(SkScalar value) {
 *     // Treat HarfBuzz hb_position_t as 16.16 fixed-point.
 *     constexpr int kHbPosition1 = 1 << 16;
 *     return SkScalarRoundToInt(value * kHbPosition1);
 * }
 * ```
 */
public fun skhbPosition(`value`: SkScalar): HbPositionT {
  TODO("Implement skhbPosition")
}

/**
 * C++ original:
 * ```cpp
 * hb_bool_t skhb_glyph(hb_font_t* hb_font,
 *                      void* font_data,
 *                      hb_codepoint_t unicode,
 *                      hb_codepoint_t variation_selector,
 *                      hb_codepoint_t* glyph,
 *                      void* user_data) {
 *     SkFont& font = *reinterpret_cast<SkFont*>(font_data);
 *
 *     *glyph = font.unicharToGlyph(unicode);
 *     return *glyph != 0;
 * }
 * ```
 */
public fun skhbGlyph(
  hbFont: HbFontT?,
  fontData: Unit?,
  unicode: HbCodepointT,
  variationSelector: HbCodepointT,
  glyph: HbCodepointT?,
  userData: Unit?,
): HbBoolT {
  TODO("Implement skhbGlyph")
}

/**
 * C++ original:
 * ```cpp
 * hb_bool_t skhb_nominal_glyph(hb_font_t* hb_font,
 *                              void* font_data,
 *                              hb_codepoint_t unicode,
 *                              hb_codepoint_t* glyph,
 *                              void* user_data) {
 *   return skhb_glyph(hb_font, font_data, unicode, 0, glyph, user_data);
 * }
 * ```
 */
public fun skhbNominalGlyph(
  hbFont: HbFontT?,
  fontData: Unit?,
  unicode: HbCodepointT,
  glyph: HbCodepointT?,
  userData: Unit?,
): HbBoolT {
  TODO("Implement skhbNominalGlyph")
}

/**
 * C++ original:
 * ```cpp
 * unsigned skhb_nominal_glyphs(hb_font_t *hb_font, void *font_data,
 *                              unsigned int count,
 *                              const hb_codepoint_t *unicodes,
 *                              unsigned int unicode_stride,
 *                              hb_codepoint_t *glyphs,
 *                              unsigned int glyph_stride,
 *                              void *user_data) {
 *     SkFont& font = *reinterpret_cast<SkFont*>(font_data);
 *
 *     // Batch call textToGlyphs since entry cost is not cheap.
 *     // Copy requred because textToGlyphs is dense and hb is strided.
 *     AutoSTMalloc<256, SkUnichar> unicode(count);
 *     for (unsigned i = 0; i < count; i++) {
 *         unicode[i] = *unicodes;
 *         unicodes = SkTAddOffset<const hb_codepoint_t>(unicodes, unicode_stride);
 *     }
 *     AutoSTMalloc<256, SkGlyphID> glyph(count);
 *     font.textToGlyphs(unicode.get(), count * sizeof(SkUnichar), SkTextEncoding::kUTF32,
 *                       {glyph.get(), count});
 *
 *     // Copy the results back to the sparse array.
 *     unsigned int done;
 *     for (done = 0; done < count && glyph[done] != 0; done++) {
 *         *glyphs = glyph[done];
 *         glyphs = SkTAddOffset<hb_codepoint_t>(glyphs, glyph_stride);
 *     }
 *     // return 'done' to allow HarfBuzz to synthesize with NFC and spaces, return 'count' to avoid
 *     return done;
 * }
 * ```
 */
public fun skhbNominalGlyphs(
  hbFont: HbFontT?,
  fontData: Unit?,
  count: UInt,
  unicodes: HbCodepointT?,
  unicodeStride: UInt,
  glyphs: HbCodepointT?,
  glyphStride: UInt,
  userData: Unit?,
): UInt {
  TODO("Implement skhbNominalGlyphs")
}

/**
 * C++ original:
 * ```cpp
 * hb_position_t skhb_glyph_h_advance(hb_font_t* hb_font,
 *                                    void* font_data,
 *                                    hb_codepoint_t hbGlyph,
 *                                    void* user_data) {
 *     SkFont& font = *reinterpret_cast<SkFont*>(font_data);
 *
 *     SkScalar advance = font.getWidth(SkTo<SkGlyphID>(hbGlyph));
 *
 *     if (!font.isSubpixel()) {
 *         advance = SkScalarRoundToInt(advance);
 *     }
 *     return skhb_position(advance);
 * }
 * ```
 */
public fun skhbGlyphHAdvance(
  hbFont: HbFontT?,
  fontData: Unit?,
  hbGlyph: HbCodepointT,
  userData: Unit?,
): HbPositionT {
  TODO("Implement skhbGlyphHAdvance")
}

/**
 * C++ original:
 * ```cpp
 * void skhb_glyph_h_advances(hb_font_t* hb_font,
 *                            void* font_data,
 *                            unsigned count,
 *                            const hb_codepoint_t* glyphs,
 *                            unsigned int glyph_stride,
 *                            hb_position_t* advances,
 *                            unsigned int advance_stride,
 *                            void* user_data) {
 *     SkFont& font = *reinterpret_cast<SkFont*>(font_data);
 *
 *     // Batch call getWidths since entry cost is not cheap.
 *     // Copy requred because getWidths is dense and hb is strided.
 *     AutoSTMalloc<256, SkGlyphID> glyph(count);
 *     for (unsigned i = 0; i < count; i++) {
 *         glyph[i] = *glyphs;
 *         glyphs = SkTAddOffset<const hb_codepoint_t>(glyphs, glyph_stride);
 *     }
 *     AutoSTMalloc<256, SkScalar> advance(count);
 *     font.getWidths({glyph.data(), count}, {advance.data(), count});
 *
 *     if (!font.isSubpixel()) {
 *         for (unsigned i = 0; i < count; i++) {
 *             advance[i] = SkScalarRoundToInt(advance[i]);
 *         }
 *     }
 *
 *     // Copy the results back to the sparse array.
 *     for (unsigned i = 0; i < count; i++) {
 *         *advances = skhb_position(advance[i]);
 *         advances = SkTAddOffset<hb_position_t>(advances, advance_stride);
 *     }
 * }
 * ```
 */
public fun skhbGlyphHAdvances(
  hbFont: HbFontT?,
  fontData: Unit?,
  count: UInt,
  glyphs: HbCodepointT?,
  glyphStride: UInt,
  advances: HbPositionT?,
  advanceStride: UInt,
  userData: Unit?,
) {
  TODO("Implement skhbGlyphHAdvances")
}

/**
 * C++ original:
 * ```cpp
 * hb_bool_t skhb_glyph_extents(hb_font_t* hb_font,
 *                              void* font_data,
 *                              hb_codepoint_t hbGlyph,
 *                              hb_glyph_extents_t* extents,
 *                              void* user_data) {
 *     SkFont& font = *reinterpret_cast<SkFont*>(font_data);
 *     SkASSERT(extents);
 *
 *     SkRect sk_bounds;
 *     SkGlyphID skGlyph = SkTo<SkGlyphID>(hbGlyph);
 *
 *     font.getBounds({&skGlyph, 1}, {&sk_bounds, 1}, nullptr);
 *     if (!font.isSubpixel()) {
 *         sk_bounds.set(sk_bounds.roundOut());
 *     }
 *
 *     // Skia is y-down but HarfBuzz is y-up.
 *     extents->x_bearing = skhb_position(sk_bounds.fLeft);
 *     extents->y_bearing = skhb_position(-sk_bounds.fTop);
 *     extents->width = skhb_position(sk_bounds.width());
 *     extents->height = skhb_position(-sk_bounds.height());
 *     return true;
 * }
 * ```
 */
public fun skhbGlyphExtents(
  hbFont: HbFontT?,
  fontData: Unit?,
  hbGlyph: HbCodepointT,
  extents: HbGlyphExtentsT?,
  userData: Unit?,
): HbBoolT {
  TODO("Implement skhbGlyphExtents")
}

/**
 * C++ original:
 * ```cpp
 * hb_font_funcs_t* skhb_get_font_funcs() {
 *     static hb_font_funcs_t* const funcs = []{
 *         // HarfBuzz will use the default (parent) implementation if they aren't set.
 *         hb_font_funcs_t* const funcs = hb_font_funcs_create();
 *         hb_font_funcs_set_variation_glyph_func(funcs, skhb_glyph, nullptr, nullptr);
 *         hb_font_funcs_set_nominal_glyph_func(funcs, skhb_nominal_glyph, nullptr, nullptr);
 * #if SK_HB_VERSION_CHECK(2, 0, 0)
 *         hb_font_funcs_set_nominal_glyphs_func(funcs, skhb_nominal_glyphs, nullptr, nullptr);
 * #else
 *         sk_ignore_unused_variable(skhb_nominal_glyphs);
 * #endif
 *         hb_font_funcs_set_glyph_h_advance_func(funcs, skhb_glyph_h_advance, nullptr, nullptr);
 * #if SK_HB_VERSION_CHECK(1, 8, 6)
 *         hb_font_funcs_set_glyph_h_advances_func(funcs, skhb_glyph_h_advances, nullptr, nullptr);
 * #else
 *         sk_ignore_unused_variable(skhb_glyph_h_advances);
 * #endif
 *         hb_font_funcs_set_glyph_extents_func(funcs, skhb_glyph_extents, nullptr, nullptr);
 *         hb_font_funcs_make_immutable(funcs);
 *         return funcs;
 *     }();
 *     SkASSERT(funcs);
 *     return funcs;
 * }
 * ```
 */
public fun skhbGetFontFuncs(): HbFontFuncsT {
  TODO("Implement skhbGetFontFuncs")
}

/**
 * C++ original:
 * ```cpp
 * hb_blob_t* skhb_get_table(hb_face_t* face, hb_tag_t tag, void* user_data) {
 *     SkTypeface& typeface = *reinterpret_cast<SkTypeface*>(user_data);
 *
 *     auto data = typeface.copyTableData(tag);
 *     if (!data) {
 *         return nullptr;
 *     }
 *     SkData* rawData = data.release();
 *     return hb_blob_create(reinterpret_cast<char*>(rawData->writable_data()), rawData->size(),
 *                           HB_MEMORY_MODE_READONLY, rawData, [](void* ctx) {
 *                               SkSafeUnref(((SkData*)ctx));
 *                           });
 * }
 * ```
 */
public fun skhbGetTable(
  face: HbFaceT?,
  tag: HbTagT,
  userData: Unit?,
): HbBlobT {
  TODO("Implement skhbGetTable")
}

/**
 * C++ original:
 * ```cpp
 * HBFace create_hb_face(const SkTypeface& typeface) {
 *     int index = 0;
 *     std::unique_ptr<SkStreamAsset> typefaceAsset = typeface.openExistingStream(&index);
 *     HBFace face;
 *     if (typefaceAsset && typefaceAsset->getMemoryBase()) {
 *         HBBlob blob(stream_to_blob(std::move(typefaceAsset)));
 *         // hb_face_create always succeeds. Check that the format is minimally recognized first.
 *         // hb_face_create_for_tables may still create a working hb_face.
 *         // See https://github.com/harfbuzz/harfbuzz/issues/248 .
 *         unsigned int num_hb_faces = hb_face_count(blob.get());
 *         if (0 < num_hb_faces && (unsigned)index < num_hb_faces) {
 *             face.reset(hb_face_create(blob.get(), (unsigned)index));
 *             // Check the number of glyphs as a basic sanitization step.
 *             if (face && hb_face_get_glyph_count(face.get()) == 0) {
 *                 face.reset();
 *             }
 *         }
 *     }
 *     if (!face) {
 *         face.reset(hb_face_create_for_tables(
 *             skhb_get_table,
 *             const_cast<SkTypeface*>(SkRef(&typeface)),
 *             [](void* user_data){ SkSafeUnref(reinterpret_cast<SkTypeface*>(user_data)); }));
 *         hb_face_set_index(face.get(), (unsigned)index);
 *     }
 *     SkASSERT(face);
 *     if (!face) {
 *         return nullptr;
 *     }
 *     hb_face_set_upem(face.get(), typeface.getUnitsPerEm());
 *
 *     SkDEBUGCODE(
 *         hb_face_set_user_data(face.get(), &gDataIdKey, const_cast<SkTypeface*>(&typeface),
 *                               nullptr, false);
 *     )
 *
 *     return face;
 * }
 * ```
 */
public fun createHbFace(typeface: SkTypeface): HBFace {
  TODO("Implement createHbFace")
}

/**
 * C++ original:
 * ```cpp
 * HBFont create_typeface_hb_font(const SkTypeface& typeface) {
 *     HBFace face(create_hb_face(typeface));
 *     if (!face) {
 *         return nullptr;
 *     }
 *
 *     HBFont otFont(hb_font_create(face.get()));
 *     SkASSERT(otFont);
 *     if (!otFont) {
 *         return nullptr;
 *     }
 *     hb_ot_font_set_funcs(otFont.get());
 *     int axis_count = typeface.getVariationDesignPosition({});
 *     if (axis_count > 0) {
 *         AutoSTMalloc<4, SkFontArguments::VariationPosition::Coordinate> axis_values(axis_count);
 *         if (typeface.getVariationDesignPosition({axis_values.get(),
 *                                                  (size_t)axis_count}) == axis_count) {
 *             hb_font_set_variations(otFont.get(),
 *                                    reinterpret_cast<hb_variation_t*>(axis_values.get()),
 *                                    axis_count);
 *         }
 *     }
 *
 *     return otFont;
 * }
 * ```
 */
public fun createTypefaceHbFont(typeface: SkTypeface): HBFont {
  TODO("Implement createTypefaceHbFont")
}

/**
 * C++ original:
 * ```cpp
 * HBFont create_sub_hb_font(const SkFont& font, const HBFont& typefaceFont) {
 *     SkDEBUGCODE(
 *         hb_face_t* face = hb_font_get_face(typefaceFont.get());
 *         void* dataId = hb_face_get_user_data(face, &gDataIdKey);
 *         SkASSERT(dataId == font.getTypeface());
 *     )
 *
 *     // Creating a sub font means that non-available functions
 *     // are found from the parent.
 *     HBFont skFont(hb_font_create_sub_font(typefaceFont.get()));
 *     hb_font_set_funcs(skFont.get(), skhb_get_font_funcs(),
 *                       reinterpret_cast<void *>(new SkFont(font)),
 *                       [](void* user_data){ delete reinterpret_cast<SkFont*>(user_data); });
 *     int scale = skhb_position(font.getSize());
 *     hb_font_set_scale(skFont.get(), scale, scale);
 *
 *     return skFont;
 * }
 * ```
 */
public fun createSubHbFont(font: SkFont, typefaceFont: HBFont): HBFont {
  TODO("Implement createSubHbFont")
}

/**
 * C++ original:
 * ```cpp
 * constexpr bool is_LTR(SkBidiIterator::Level level) {
 *     return (level & 1) == 0;
 * }
 * ```
 */
public fun isLTR(level: SkBidiIteratorLevel): Boolean {
  TODO("Implement isLTR")
}

/**
 * C++ original:
 * ```cpp
 * void append(SkShaper::RunHandler* handler, const SkShaper::RunHandler::RunInfo& runInfo,
 *                    const ShapedRun& run, size_t startGlyphIndex, size_t endGlyphIndex) {
 *     SkASSERT(startGlyphIndex <= endGlyphIndex);
 *     const size_t glyphLen = endGlyphIndex - startGlyphIndex;
 *
 *     const auto buffer = handler->runBuffer(runInfo);
 *     SkASSERT(buffer.glyphs);
 *     SkASSERT(buffer.positions);
 *
 *     SkVector advance = {0,0};
 *     for (size_t i = 0; i < glyphLen; i++) {
 *         // Glyphs are in logical order, but output ltr since PDF readers seem to expect that.
 *         const ShapedGlyph& glyph = run.fGlyphs[is_LTR(run.fLevel) ? startGlyphIndex + i
 *                                                                   : endGlyphIndex - 1 - i];
 *         buffer.glyphs[i] = glyph.fID;
 *         if (buffer.offsets) {
 *             buffer.positions[i] = advance + buffer.point;
 *             buffer.offsets[i] = glyph.fOffset;
 *         } else {
 *             buffer.positions[i] = advance + buffer.point + glyph.fOffset;
 *         }
 *         if (buffer.clusters) {
 *             buffer.clusters[i] = glyph.fCluster;
 *         }
 *         advance += glyph.fAdvance;
 *     }
 *     handler->commitRunBuffer(runInfo);
 * }
 * ```
 */
public fun append(
  handler: SkShaper.RunHandler?,
  runInfo: RunHandler.RunInfo,
  run: ShapedRun,
  startGlyphIndex: ULong,
  endGlyphIndex: ULong,
) {
  TODO("Implement append")
}

/**
 * C++ original:
 * ```cpp
 * void emit(SkUnicode* unicode, const ShapedLine& line, SkShaper::RunHandler* handler) {
 *     // Reorder the runs and glyphs per line and write them out.
 *     handler->beginLine();
 *
 *     int numRuns = line.runs.size();
 *     AutoSTMalloc<4, SkBidiIterator::Level> runLevels(numRuns);
 *     for (int i = 0; i < numRuns; ++i) {
 *         runLevels[i] = line.runs[i].fLevel;
 *     }
 *     AutoSTMalloc<4, int32_t> logicalFromVisual(numRuns);
 *     unicode->reorderVisual(runLevels, numRuns, logicalFromVisual);
 *
 *     for (int i = 0; i < numRuns; ++i) {
 *         int logicalIndex = logicalFromVisual[i];
 *
 *         const auto& run = line.runs[logicalIndex];
 *         const SkShaper::RunHandler::RunInfo info = {
 *             run.fFont,
 *             run.fLevel,
 *             run.fScript,
 *             run.fLanguage,
 *             run.fAdvance,
 *             run.fNumGlyphs,
 *             run.fUtf8Range
 *         };
 *         handler->runInfo(info);
 *     }
 *     handler->commitRunInfo();
 *     for (int i = 0; i < numRuns; ++i) {
 *         int logicalIndex = logicalFromVisual[i];
 *
 *         const auto& run = line.runs[logicalIndex];
 *         const SkShaper::RunHandler::RunInfo info = {
 *             run.fFont,
 *             run.fLevel,
 *             run.fScript,
 *             run.fLanguage,
 *             run.fAdvance,
 *             run.fNumGlyphs,
 *             run.fUtf8Range
 *         };
 *         append(handler, info, run, 0, run.fNumGlyphs);
 *     }
 *
 *     handler->commitLine();
 * }
 * ```
 */
public fun emit(
  unicode: SkUnicode?,
  line: ShapedLine,
  handler: SkShaper.RunHandler?,
) {
  TODO("Implement emit")
}

/**
 * C++ original:
 * ```cpp
 * static HBLockedFaceCache get_hbFace_cache() {
 *     static SkMutex gHBFaceCacheMutex;
 *     static SkLRUCache<SkTypefaceID, HBFont> gHBFaceCache(100);
 *     return HBLockedFaceCache(gHBFaceCache, gHBFaceCacheMutex);
 * }
 * ```
 */
public fun getHbFaceCache(): HBLockedFaceCache {
  TODO("Implement getHbFaceCache")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkShaper> ShaperDrivenWrapper(sk_sp<SkUnicode> unicode,
 *                                               sk_sp<SkFontMgr> fallback) {
 *     if (!unicode) {
 *         return nullptr;
 *     }
 *     HBBuffer buffer(hb_buffer_create());
 *     if (!buffer) {
 *         SkDEBUGF("Could not create hb_buffer");
 *         return nullptr;
 *     }
 *     return std::make_unique<::ShaperDrivenWrapper>(
 *             unicode, std::move(buffer), std::move(fallback));
 * }
 * ```
 */
public fun shaperDrivenWrapper(unicode: SkSp<SkUnicode>, fallback: SkSp<SkFontMgr>): SkShaper? {
  TODO("Implement shaperDrivenWrapper")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkShaper> ShapeThenWrap(sk_sp<SkUnicode> unicode,
 *                                         sk_sp<SkFontMgr> fallback) {
 *     if (!unicode) {
 *         return nullptr;
 *     }
 *     HBBuffer buffer(hb_buffer_create());
 *     if (!buffer) {
 *         SkDEBUGF("Could not create hb_buffer");
 *         return nullptr;
 *     }
 *     return std::make_unique<::ShapeThenWrap>(
 *             unicode, std::move(buffer), std::move(fallback));
 * }
 * ```
 */
public fun shapeThenWrap(unicode: SkSp<SkUnicode>, fallback: SkSp<SkFontMgr>): SkShaper? {
  TODO("Implement shapeThenWrap")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkShaper> ShapeDontWrapOrReorder(sk_sp<SkUnicode> unicode,
 *                                                  sk_sp<SkFontMgr> fallback) {
 *     if (!unicode) {
 *         return nullptr;
 *     }
 *     HBBuffer buffer(hb_buffer_create());
 *     if (!buffer) {
 *         SkDEBUGF("Could not create hb_buffer");
 *         return nullptr;
 *     }
 *     return std::make_unique<::ShapeDontWrapOrReorder>(
 *             unicode, std::move(buffer), std::move(fallback));
 * }
 * ```
 */
public fun shapeDontWrapOrReorder(unicode: SkSp<SkUnicode>, fallback: SkSp<SkFontMgr>): SkShaper? {
  TODO("Implement shapeDontWrapOrReorder")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkShaper::ScriptRunIterator> ScriptRunIterator(const char* utf8,
 *                                                                size_t utf8Bytes,
 *                                                                SkFourByteTag script) {
 *     return std::make_unique<SkUnicodeHbScriptRunIterator>(
 *             utf8, utf8Bytes, hb_script_from_iso15924_tag((hb_tag_t)script));
 * }
 * ```
 */
public fun scriptRunIterator(
  utf8: String?,
  utf8Bytes: ULong,
  script: SkFourByteTag,
): SkShaper.ScriptRunIterator? {
  TODO("Implement scriptRunIterator")
}

/**
 * C++ original:
 * ```cpp
 * void PurgeCaches() {
 *     HBLockedFaceCache cache = get_hbFace_cache();
 *     cache.reset();
 * }
 * ```
 */
public fun purgeCaches() {
  TODO("Implement purgeCaches")
}

/**
 * C++ original:
 * ```cpp
 * static int inline_strcmp(const char a[], const char b[]) {
 *     for (;;) {
 *         char c = *a++;
 *         if (c == 0) {
 *             break;
 *         }
 *         if (c != *b++) {
 *             return 1;
 *         }
 *     }
 *     return *b != 0;
 * }
 * ```
 */
public fun inlineStrcmp(a: CharArray, b: CharArray): Int {
  TODO("Implement inlineStrcmp")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool is_eostring(char c) { return g_token_flags[static_cast<uint8_t>(c)] & 0x04; }
 * ```
 */
public fun isEostring(c: Char): Boolean {
  TODO("Implement isEostring")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool is_digit(char c)    { return g_token_flags[static_cast<uint8_t>(c)] & 0x08; }
 * ```
 */
public fun isDigit(c: Char): Boolean {
  TODO("Implement isDigit")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool is_numeric(char c)  { return g_token_flags[static_cast<uint8_t>(c)] & 0x10; }
 * ```
 */
public fun isNumeric(c: Char): Boolean {
  TODO("Implement isNumeric")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool is_eoscope(char c)  { return g_token_flags[static_cast<uint8_t>(c)] & 0x20; }
 * ```
 */
public fun isEoscope(c: Char): Boolean {
  TODO("Implement isEoscope")
}

/**
 * C++ original:
 * ```cpp
 * static inline const char* skip_ws(const char* p) {
 *     while (is_ws(*p)) ++p;
 *     return p;
 * }
 * ```
 */
public fun skipWs(p: String?): Char {
  TODO("Implement skipWs")
}

/**
 * C++ original:
 * ```cpp
 * static inline float pow10(int32_t exp) {
 *     static constexpr float g_pow10_table[63] =
 *     {
 *        1.e-031f, 1.e-030f, 1.e-029f, 1.e-028f, 1.e-027f, 1.e-026f, 1.e-025f, 1.e-024f,
 *        1.e-023f, 1.e-022f, 1.e-021f, 1.e-020f, 1.e-019f, 1.e-018f, 1.e-017f, 1.e-016f,
 *        1.e-015f, 1.e-014f, 1.e-013f, 1.e-012f, 1.e-011f, 1.e-010f, 1.e-009f, 1.e-008f,
 *        1.e-007f, 1.e-006f, 1.e-005f, 1.e-004f, 1.e-003f, 1.e-002f, 1.e-001f, 1.e+000f,
 *        1.e+001f, 1.e+002f, 1.e+003f, 1.e+004f, 1.e+005f, 1.e+006f, 1.e+007f, 1.e+008f,
 *        1.e+009f, 1.e+010f, 1.e+011f, 1.e+012f, 1.e+013f, 1.e+014f, 1.e+015f, 1.e+016f,
 *        1.e+017f, 1.e+018f, 1.e+019f, 1.e+020f, 1.e+021f, 1.e+022f, 1.e+023f, 1.e+024f,
 *        1.e+025f, 1.e+026f, 1.e+027f, 1.e+028f, 1.e+029f, 1.e+030f, 1.e+031f
 *     };
 *
 *     static constexpr int32_t k_exp_offset = std::size(g_pow10_table) / 2;
 *
 *     // We only support negative exponents for now.
 *     SkASSERT(exp <= 0);
 *
 *     return (exp >= -k_exp_offset) ? g_pow10_table[exp + k_exp_offset]
 *                                   : std::pow(10.0f, static_cast<float>(exp));
 * }
 * ```
 */
public fun pow10(exp: Int): Float {
  TODO("Implement pow10")
}

/**
 * C++ original:
 * ```cpp
 * void Write(const Value& v, SkWStream* stream) {
 *     // We use the address of these as special tags in the pending list.
 *     static const NullValue kArrayCloseTag,    // ]
 *                            kObjectCloseTag,   // }
 *                            kListSeparatorTag, // ,
 *                            kKeySeparatorTag;  // :
 *
 *     std::vector<const Value*> pending{&v};
 *
 *     do {
 *         const Value* val = pending.back();
 *         pending.pop_back();
 *
 *         if (val == &kArrayCloseTag) {
 *             stream->writeText("]");
 *             continue;
 *         }
 *
 *         if (val == &kObjectCloseTag) {
 *             stream->writeText("}");
 *             continue;
 *         }
 *
 *         if (val == &kListSeparatorTag) {
 *             stream->writeText(",");
 *             continue;
 *         }
 *
 *         if (val == &kKeySeparatorTag) {
 *             stream->writeText(":");
 *             continue;
 *         }
 *
 *         switch (val->getType()) {
 *             case Value::Type::kNull:
 *                 stream->writeText("null");
 *                 break;
 *             case Value::Type::kBool:
 *                 stream->writeText(*val->as<BoolValue>() ? "true" : "false");
 *                 break;
 *             case Value::Type::kNumber:
 *                 stream->writeScalarAsText(*val->as<NumberValue>());
 *                 break;
 *             case Value::Type::kString:
 *                 stream->writeText("\"");
 *                 stream->writeText(val->as<StringValue>().begin());
 *                 stream->writeText("\"");
 *                 break;
 *             case Value::Type::kArray: {
 *                 const auto& array = val->as<ArrayValue>();
 *                 stream->writeText("[");
 *                 // "val, val, .. ]" in reverse order
 *                 pending.push_back(&kArrayCloseTag);
 *                 if (array.size() > 0) {
 *                     bool last_value = true;
 *                     for (const Value* it = array.end() - 1; it >= array.begin(); --it) {
 *                         if (!last_value) pending.push_back(&kListSeparatorTag);
 *                         pending.push_back(it);
 *                         last_value = false;
 *                     }
 *                 }
 *             } break;
 *             case Value::Type::kObject: {
 *                 const auto& object = val->as<ObjectValue>();
 *                 stream->writeText("{");
 *                 // "key: val, key: val, .. }" in reverse order
 *                 pending.push_back(&kObjectCloseTag);
 *                 if (object.size() > 0) {
 *                     bool last_member = true;
 *                     for (const Member* it = object.end() - 1; it >= object.begin(); --it) {
 *                         if (!last_member) pending.push_back(&kListSeparatorTag);
 *                         pending.push_back(&it->fValue);
 *                         pending.push_back(&kKeySeparatorTag);
 *                         pending.push_back(&it->fKey);
 *                         last_member = false;
 *                     }
 *                 }
 *             } break;
 *         }
 *     } while (!pending.empty());
 * }
 * ```
 */
public fun write(v: Value, stream: SkWStream?) {
  TODO("Implement write")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkColorFilter> Make2ColorGradient(const sk_sp<Color>& color0, const sk_sp<Color>& color1) {
 *     const auto c0 = SkColor4f::FromColor(color0->getColor()),
 *                c1 = SkColor4f::FromColor(color1->getColor());
 *
 *     const auto dR = c1.fR - c0.fR,
 *                dG = c1.fG - c0.fG,
 *                dB = c1.fB - c0.fB;
 *
 *     // A 2-color gradient can be expressed as a color matrix (and combined with the luminance
 *     // calculation).  First, the luminance:
 *     //
 *     //   L = [r,g,b] . [kR,kG,kB]
 *     //
 *     // We can compute it using a color matrix (result stored in R):
 *     //
 *     //   | kR, kG, kB,  0,  0 |    r' = L
 *     //   |  0,  0,  0,  0,  0 |    g' = 0
 *     //   |  0,  0,  0,  0,  0 |    b' = 0
 *     //   |  0,  0,  0,  1,  0 |    a' = a
 *     //
 *     // Then we want to interpolate component-wise, based on L:
 *     //
 *     //   r' = c0.r + (c1.r - c0.r) * L = c0.r + dR*L
 *     //   g' = c0.g + (c1.g - c0.g) * L = c0.g + dG*L
 *     //   b' = c0.b + (c1.b - c0.b) * L = c0.b + dB*L
 *     //   a' = a
 *     //
 *     // This can be expressed as another color matrix (when L is stored in R):
 *     //
 *     //  | dR,  0,  0,  0, c0.r |
 *     //  | dG,  0,  0,  0, c0.g |
 *     //  | dB,  0,  0,  0, c0.b |
 *     //  |  0,  0,  0,  1,    0 |
 *     //
 *     // Composing these two, we get the total tint matrix:
 *
 *     const float tint_matrix[] = {
 *         dR*SK_LUM_COEFF_R, dR*SK_LUM_COEFF_G, dR*SK_LUM_COEFF_B, 0, c0.fR,
 *         dG*SK_LUM_COEFF_R, dG*SK_LUM_COEFF_G, dG*SK_LUM_COEFF_B, 0, c0.fG,
 *         dB*SK_LUM_COEFF_R, dB*SK_LUM_COEFF_G, dB*SK_LUM_COEFF_B, 0, c0.fB,
 *         0,                 0,                 0,                 1, 0,
 *     };
 *
 *     return SkColorFilters::Matrix(tint_matrix);
 * }
 * ```
 */
public fun make2ColorGradient(color0: SkSp<Color>, color1: SkSp<Color>): SkSp<SkColorFilter> {
  TODO("Implement make2ColorGradient")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkColorFilter> MakeNColorGradient(const std::vector<sk_sp<Color>>& colors) {
 *     // For N colors, we build a gradient color table.
 *     uint8_t rTable[256], gTable[256], bTable[256];
 *
 *     SkASSERT(colors.size() > 2);
 *     const auto span_count = colors.size() - 1;
 *
 *     size_t span_start = 0;
 *     for (size_t i = 0; i < span_count; ++i) {
 *         const auto span_stop = static_cast<size_t>(std::round((i + 1) * 255.0f / span_count)),
 *                    span_size = span_stop - span_start;
 *         if (span_start > span_stop) {
 *             // Degenerate case.
 *             continue;
 *         }
 *         SkASSERT(span_stop <= 255);
 *
 *         // Fill the gradient in [span_start,span_stop] -> [c0,c1]
 *         const SkColor c0 = colors[i    ]->getColor(),
 *                       c1 = colors[i + 1]->getColor();
 *         float r = SkColorGetR(c0),
 *               g = SkColorGetG(c0),
 *               b = SkColorGetB(c0);
 *         const float dR = (SkColorGetR(c1) - r) / span_size,
 *                     dG = (SkColorGetG(c1) - g) / span_size,
 *                     dB = (SkColorGetB(c1) - b) / span_size;
 *
 *         for (size_t j = span_start; j <= span_stop; ++j) {
 *             rTable[j] = static_cast<uint8_t>(std::round(r));
 *             gTable[j] = static_cast<uint8_t>(std::round(g));
 *             bTable[j] = static_cast<uint8_t>(std::round(b));
 *             r += dR;
 *             g += dG;
 *             b += dB;
 *         }
 *
 *         // Ensure we always advance.
 *         span_start = span_stop + 1;
 *     }
 *     SkASSERT(span_start == 256);
 *
 *     const float luminance_matrix[] = {
 *         SK_LUM_COEFF_R, SK_LUM_COEFF_G, SK_LUM_COEFF_B,  0,  0,  // r' = L
 *         SK_LUM_COEFF_R, SK_LUM_COEFF_G, SK_LUM_COEFF_B,  0,  0,  // g' = L
 *         SK_LUM_COEFF_R, SK_LUM_COEFF_G, SK_LUM_COEFF_B,  0,  0,  // b' = L
 *                      0,              0,              0,  1,  0,  // a' = a
 *     };
 *
 *     return SkColorFilters::TableARGB(nullptr, rTable, gTable, bTable)
 *             ->makeComposed(SkColorFilters::Matrix(luminance_matrix));
 * }
 * ```
 */
public fun makeNColorGradient(colors: List<SkSp<Color>>): SkSp<SkColorFilter> {
  TODO("Implement makeNColorGradient")
}

/**
 * C++ original:
 * ```cpp
 * static SkPathOp mode_to_op(Merge::Mode mode) {
 *     switch (mode) {
 *     case Merge::Mode::kUnion:
 *         return kUnion_SkPathOp;
 *     case Merge::Mode::kIntersect:
 *         return kIntersect_SkPathOp;
 *     case Merge::Mode::kDifference:
 *         return kDifference_SkPathOp;
 *     case Merge::Mode::kReverseDifference:
 *         return kReverseDifference_SkPathOp;
 *     case Merge::Mode::kXOR:
 *         return kXOR_SkPathOp;
 *     default:
 *         break;
 *     }
 *
 *     return kUnion_SkPathOp;
 * }
 * ```
 */
public fun modeToOp(mode: Merge.Mode): SkPathOp {
  TODO("Implement modeToOp")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_inverted(sksg::MaskEffect::Mode mode) {
 *     return static_cast<uint32_t>(mode) & 1;
 * }
 * ```
 */
public fun isInverted(mode: MaskEffect.Mode): Boolean {
  TODO("Implement isInverted")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_luma(sksg::MaskEffect::Mode mode) {
 *     return static_cast<uint32_t>(mode) & 2;
 * }
 * ```
 */
public fun isLuma(mode: MaskEffect.Mode): Boolean {
  TODO("Implement isLuma")
}

/**
 * C++ original:
 * ```cpp
 * static SkAlpha ScaleAlpha(SkAlpha alpha, float opacity) {
 *    return SkToU8(sk_float_round2int(alpha * opacity));
 * }
 * ```
 */
public fun scaleAlpha(alpha: SkAlpha, opacity: Float): SkAlpha {
  TODO("Implement scaleAlpha")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkShader> LocalShader(const sk_sp<SkShader>& shader,
 *                                    const SkMatrix& base,
 *                                    const SkMatrix& ctm) {
 *     // Mask filters / shaders are declared to operate under a specific transform, but due to the
 *     // deferral mechanism, other transformations might have been pushed to the state.
 *     // We want to undo these transforms (T):
 *     //
 *     //   baseCTM x T = ctm
 *     //
 *     //   =>  T = Inv(baseCTM) x ctm
 *     //
 *     //   =>  Inv(T) = Inv(Inv(baseCTM) x ctm)
 *     //
 *     //   =>  Inv(T) = Inv(ctm) x baseCTM
 *
 *     SkMatrix lm;
 *     if (base != ctm && ctm.invert(&lm)) {
 *         lm.preConcat(base);
 *     } else {
 *         lm = SkMatrix::I();
 *     }
 *
 *     // Note: this doesn't play ball with existing shader local matrices (what we really want is
 *     // SkShader::makeWithPostLocalMatrix).  Probably a good signal that the whole mechanism is
 *     // contrived and should be redesigned (use SkCanvas::clipShader when available, drop shader
 *     // "effects" completely, etc).
 *     return shader->makeWithLocalMatrix(lm);
 * }
 * ```
 */
public fun localShader(
  shader: SkSp<SkShader>,
  base: SkMatrix,
  ctm: SkMatrix,
): SkSp<SkShader> {
  TODO("Implement localShader")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * SkMatrix AsSkMatrix<SkMatrix>(const SkMatrix& m) { return m; }
 * ```
 */
public fun asSkMatrixSkMatrix(m: SkMatrix): SkMatrix {
  TODO("Implement asSkMatrixSkMatrix")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * SkMatrix AsSkMatrix<SkM44>(const SkM44& m) { return m.asM33(); }
 * ```
 */
public fun asSkMatrixSkM44(m: SkM44): SkMatrix {
  TODO("Implement asSkMatrixSkM44")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * SkM44 AsSkM44<SkMatrix>(const SkMatrix& m) { return SkM44(m); }
 * ```
 */
public fun asSkM44SkMatrix(m: SkMatrix): SkM44 {
  TODO("Implement asSkM44SkMatrix")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * SkM44 AsSkM44<SkM44>(const SkM44& m) { return m; }
 * ```
 */
public fun asSkM44SkM44(m: SkM44): SkM44 {
  TODO("Implement asSkM44SkM44")
}

/**
 * C++ original:
 * ```cpp
 * inline bool is_between(char c, char min, char max) {
 *     SkASSERT(min <= max);
 *     return (unsigned)(c - min) <= (unsigned)(max - min);
 * }
 * ```
 */
public fun isBetween(
  c: Char,
  min: Char,
  max: Char,
): Boolean {
  TODO("Implement isBetween")
}

/**
 * C++ original:
 * ```cpp
 * inline bool is_ws(char c) {
 *     return is_between(c, 1, 32);
 * }
 * ```
 */
public fun isWs(c: Char): Boolean {
  TODO("Implement isWs")
}

/**
 * C++ original:
 * ```cpp
 * inline bool is_sep(char c) {
 *     return is_ws(c) || c == ',' || c == ';';
 * }
 * ```
 */
public fun isSep(c: Char): Boolean {
  TODO("Implement isSep")
}

/**
 * C++ original:
 * ```cpp
 * inline bool is_nl(char c) {
 *     return c == '\n' || c == '\r' || c == '\f';
 * }
 * ```
 */
public fun isNl(c: Char): Boolean {
  TODO("Implement isNl")
}

/**
 * C++ original:
 * ```cpp
 * inline bool is_hex(char c) {
 *     return is_between(c, 'a', 'f') ||
 *            is_between(c, 'A', 'F') ||
 *            is_between(c, '0', '9');
 * }
 * ```
 */
public fun isHex(c: Char): Boolean {
  TODO("Implement isHex")
}

/**
 * C++ original:
 * ```cpp
 * bool SetIRIAttribute(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr,
 *                       const char* stringValue) {
 *     auto parseResult = SkSVGAttributeParser::parse<SkSVGIRI>(stringValue);
 *     if (!parseResult.has_value()) {
 *         return false;
 *     }
 *
 *     node->setAttribute(attr, SkSVGStringValue(parseResult->iri()));
 *     return true;
 * }
 * ```
 */
public fun setIRIAttribute(
  node: SkSp<SkSVGNode>,
  attr: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setIRIAttribute")
}

/**
 * C++ original:
 * ```cpp
 * bool SetTransformAttribute(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr,
 *                            const char* stringValue) {
 *     auto parseResult = SkSVGAttributeParser::parse<SkSVGTransformType>(stringValue);
 *     if (!parseResult.has_value()) {
 *         return false;
 *     }
 *
 *     node->setAttribute(attr, SkSVGTransformValue(*parseResult));
 *     return true;
 * }
 * ```
 */
public fun setTransformAttribute(
  node: SkSp<SkSVGNode>,
  attr: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setTransformAttribute")
}

/**
 * C++ original:
 * ```cpp
 * bool SetLengthAttribute(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr,
 *                         const char* stringValue) {
 *     auto parseResult = SkSVGAttributeParser::parse<SkSVGLength>(stringValue);
 *     if (!parseResult.has_value()) {
 *         return false;
 *     }
 *
 *     node->setAttribute(attr, SkSVGLengthValue(*parseResult));
 *     return true;
 * }
 * ```
 */
public fun setLengthAttribute(
  node: SkSp<SkSVGNode>,
  attr: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setLengthAttribute")
}

/**
 * C++ original:
 * ```cpp
 * bool SetViewBoxAttribute(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr,
 *                          const char* stringValue) {
 *     SkSVGViewBoxType viewBox;
 *     SkSVGAttributeParser parser(stringValue);
 *     if (!parser.parseViewBox(&viewBox)) {
 *         return false;
 *     }
 *
 *     node->setAttribute(attr, SkSVGViewBoxValue(viewBox));
 *     return true;
 * }
 * ```
 */
public fun setViewBoxAttribute(
  node: SkSp<SkSVGNode>,
  attr: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setViewBoxAttribute")
}

/**
 * C++ original:
 * ```cpp
 * bool SetObjectBoundingBoxUnitsAttribute(const sk_sp<SkSVGNode>& node,
 *                                         SkSVGAttribute attr,
 *                                         const char* stringValue) {
 *     auto parseResult = SkSVGAttributeParser::parse<SkSVGObjectBoundingBoxUnits>(stringValue);
 *     if (!parseResult.has_value()) {
 *         return false;
 *     }
 *
 *     node->setAttribute(attr, SkSVGObjectBoundingBoxUnitsValue(*parseResult));
 *     return true;
 * }
 * ```
 */
public fun setObjectBoundingBoxUnitsAttribute(
  node: SkSp<SkSVGNode>,
  attr: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setObjectBoundingBoxUnitsAttribute")
}

/**
 * C++ original:
 * ```cpp
 * bool SetPreserveAspectRatioAttribute(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr,
 *                                      const char* stringValue) {
 *     SkSVGPreserveAspectRatio par;
 *     SkSVGAttributeParser parser(stringValue);
 *     if (!parser.parsePreserveAspectRatio(&par)) {
 *         return false;
 *     }
 *
 *     node->setAttribute(attr, SkSVGPreserveAspectRatioValue(par));
 *     return true;
 * }
 * ```
 */
public fun setPreserveAspectRatioAttribute(
  node: SkSp<SkSVGNode>,
  attr: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setPreserveAspectRatioAttribute")
}

/**
 * C++ original:
 * ```cpp
 * SkString TrimmedString(const char* first, const char* last) {
 *     SkASSERT(first);
 *     SkASSERT(last);
 *     SkASSERT(first <= last);
 *
 *     while (first <= last && *first <= ' ') { first++; }
 *     while (first <= last && *last  <= ' ') { last--; }
 *
 *     SkASSERT(last - first + 1 >= 0);
 *     return SkString(first, SkTo<size_t>(last - first + 1));
 * }
 * ```
 */
public fun trimmedString(first: String?, last: String?): String {
  TODO("Implement trimmedString")
}

/**
 * C++ original:
 * ```cpp
 * bool SetStyleAttributes(const sk_sp<SkSVGNode>& node, SkSVGAttribute,
 *                         const char* stringValue) {
 *
 *     SkString name, value;
 *     StyleIterator iter(stringValue);
 *     for (;;) {
 *         std::tie(name, value) = iter.next();
 *         if (name.isEmpty()) {
 *             break;
 *         }
 *         set_string_attribute(node, name.c_str(), value.c_str());
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun setStyleAttributes(
  node: SkSp<SkSVGNode>,
  param1: SkSVGAttribute,
  stringValue: String?,
): Boolean {
  TODO("Implement setStyleAttributes")
}

/**
 * C++ original:
 * ```cpp
 * bool set_string_attribute(const sk_sp<SkSVGNode>& node, const char* name, const char* value) {
 *     if (node->parseAndSetAttribute(name, value)) {
 *         // Handled by new code path
 *         return true;
 *     }
 *
 *     const int attrIndex = SkStrSearch(&gAttributeParseInfo[0].fKey,
 *                                       SkTo<int>(std::size(gAttributeParseInfo)),
 *                                       name, sizeof(gAttributeParseInfo[0]));
 *     if (attrIndex < 0) {
 * #if defined(SK_VERBOSE_SVG_PARSING)
 *         SkDebugf("unhandled attribute: %s\n", name);
 * #endif
 *         return false;
 *     }
 *
 *     SkASSERT(SkTo<size_t>(attrIndex) < std::size(gAttributeParseInfo));
 *     const auto& attrInfo = gAttributeParseInfo[attrIndex].fValue;
 *     if (!attrInfo.fSetter(node, attrInfo.fAttr, value)) {
 * #if defined(SK_VERBOSE_SVG_PARSING)
 *         SkDebugf("could not parse attribute: '%s=\"%s\"'\n", name, value);
 * #endif
 *         return false;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun setStringAttribute(
  node: SkSp<SkSVGNode>,
  name: String?,
  `value`: String?,
): Boolean {
  TODO("Implement setStringAttribute")
}

/**
 * C++ original:
 * ```cpp
 * void parse_node_attributes(const SkDOM& xmlDom, const SkDOM::Node* xmlNode,
 *                            const sk_sp<SkSVGNode>& svgNode, SkSVGIDMapper* mapper) {
 *     const char* name, *value;
 *     SkDOM::AttrIter attrIter(xmlDom, xmlNode);
 *     while ((name = attrIter.next(&value))) {
 *         // We're handling id attributes out of band for now.
 *         if (!strcmp(name, "id")) {
 *             mapper->set(SkString(value), svgNode);
 *             continue;
 *         }
 *         set_string_attribute(svgNode, name, value);
 *     }
 * }
 * ```
 */
public fun parseNodeAttributes(
  xmlDom: SkDOM,
  xmlNode: SkDOMNode?,
  svgNode: SkSp<SkSVGNode>,
  mapper: SkSVGIDMapper?,
) {
  TODO("Implement parseNodeAttributes")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSVGNode> construct_svg_node(const SkDOM& dom, const ConstructionContext& ctx,
 *                                     const SkDOM::Node* xmlNode) {
 *     const char* elem = dom.getName(xmlNode);
 *     const SkDOM::Type elemType = dom.getType(xmlNode);
 *
 *     if (elemType == SkDOM::kText_Type) {
 *         // Text literals require special handling.
 *         SkASSERT(dom.countChildren(xmlNode) == 0);
 *         auto txt = SkSVGTextLiteral::Make();
 *         txt->setText(SkString(dom.getName(xmlNode)));
 *         ctx.fParent->appendChild(std::move(txt));
 *
 *         return nullptr;
 *     }
 *
 *     SkASSERT(elemType == SkDOM::kElement_Type);
 *
 *     auto make_node = [](const ConstructionContext& ctx, const char* elem) -> sk_sp<SkSVGNode> {
 *         if (strcmp(elem, "svg") == 0) {
 *             // Outermost SVG element must be tagged as such.
 *             return SkSVGSVG::Make(ctx.fParent ? SkSVGSVG::Type::kInner
 *                                               : SkSVGSVG::Type::kRoot);
 *         }
 *
 *         const int tagIndex = SkStrSearch(&gTagFactories[0].fKey,
 *                                          SkTo<int>(std::size(gTagFactories)),
 *                                          elem, sizeof(gTagFactories[0]));
 *         if (tagIndex < 0) {
 * #if defined(SK_VERBOSE_SVG_PARSING)
 *             SkDebugf("unhandled element: <%s>\n", elem);
 * #endif
 *             return nullptr;
 *         }
 *         SkASSERT(SkTo<size_t>(tagIndex) < std::size(gTagFactories));
 *
 *         return gTagFactories[tagIndex].fValue();
 *     };
 *
 *     auto node = make_node(ctx, elem);
 *     if (!node) {
 *         return nullptr;
 *     }
 *
 *     parse_node_attributes(dom, xmlNode, node, ctx.fIDMapper);
 *
 *     ConstructionContext localCtx(ctx, node);
 *     for (auto* child = dom.getFirstChild(xmlNode, nullptr); child;
 *          child = dom.getNextSibling(child)) {
 *         sk_sp<SkSVGNode> childNode = construct_svg_node(dom, localCtx, child);
 *         if (childNode) {
 *             node->appendChild(std::move(childNode));
 *         }
 *     }
 *
 *     return node;
 * }
 * ```
 */
public fun constructSvgNode(
  dom: SkDOM,
  ctx: ConstructionContext,
  xmlNode: SkDOMNode?,
): SkSp<SkSVGNode> {
  TODO("Implement constructSvgNode")
}

/**
 * C++ original:
 * ```cpp
 * static SkBlendMode GetBlendMode(SkSVGFeBlend::Mode mode) {
 *     switch (mode) {
 *         case SkSVGFeBlend::Mode::kNormal:
 *             return SkBlendMode::kSrcOver;
 *         case SkSVGFeBlend::Mode::kMultiply:
 *             return SkBlendMode::kMultiply;
 *         case SkSVGFeBlend::Mode::kScreen:
 *             return SkBlendMode::kScreen;
 *         case SkSVGFeBlend::Mode::kDarken:
 *             return SkBlendMode::kDarken;
 *         case SkSVGFeBlend::Mode::kLighten:
 *             return SkBlendMode::kLighten;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun getBlendMode(mode: SkSVGFeBlend.Mode): SkBlendMode {
  TODO("Implement getBlendMode")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool SkSVGAttributeParser::parse<SkSVGFeBlend::Mode>(
 *         SkSVGFeBlend::Mode* mode) {
 *     static constexpr std::tuple<const char*, SkSVGFeBlend::Mode> gMap[] = {
 *         { "normal"  , SkSVGFeBlend::Mode::kNormal   },
 *         { "multiply", SkSVGFeBlend::Mode::kMultiply },
 *         { "screen"  , SkSVGFeBlend::Mode::kScreen   },
 *         { "darken"  , SkSVGFeBlend::Mode::kDarken   },
 *         { "lighten" , SkSVGFeBlend::Mode::kLighten  },
 *     };
 *
 *     return this->parseEnumMap(gMap, mode) && this->parseEOSToken();
 * }
 * ```
 */
public fun parseSkSVGFeBlendMode(mode: SkSVGFeBlend.Mode?) {
  TODO("Implement parseSkSVGFeBlendMode")
}

/**
 * C++ original:
 * ```cpp
 * static bool AnyIsStandardInput(const SkSVGFilterContext& fctx,
 *                                const std::vector<SkSVGFeInputType>& inputs) {
 *     for (const auto& in : inputs) {
 *         switch (in.type()) {
 *             case SkSVGFeInputType::Type::kFilterPrimitiveReference:
 *                 break;
 *             case SkSVGFeInputType::Type::kSourceGraphic:
 *             case SkSVGFeInputType::Type::kSourceAlpha:
 *             case SkSVGFeInputType::Type::kBackgroundImage:
 *             case SkSVGFeInputType::Type::kBackgroundAlpha:
 *             case SkSVGFeInputType::Type::kFillPaint:
 *             case SkSVGFeInputType::Type::kStrokePaint:
 *                 return true;
 *             case SkSVGFeInputType::Type::kUnspecified:
 *                 // Unspecified means previous result (which may be SourceGraphic).
 *                 if (fctx.previousResultIsSourceGraphic()) {
 *                     return true;
 *                 }
 *                 break;
 *         }
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun anyIsStandardInput(fctx: SkSVGFilterContext, inputs: List<SkSVGFeInputType>): Boolean {
  TODO("Implement anyIsStandardInput")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool SkSVGAttributeParser::parse<SkSVGFeDisplacementMap::ChannelSelector>(
 *         SkSVGFeDisplacementMap::ChannelSelector* channel) {
 *     static constexpr std::tuple<const char*, SkSVGFeDisplacementMap::ChannelSelector> gMap[] = {
 *             { "R", SkSVGFeDisplacementMap::ChannelSelector::kR },
 *             { "G", SkSVGFeDisplacementMap::ChannelSelector::kG },
 *             { "B", SkSVGFeDisplacementMap::ChannelSelector::kB },
 *             { "A", SkSVGFeDisplacementMap::ChannelSelector::kA },
 *     };
 *
 *     return this->parseEnumMap(gMap, channel) && this->parseEOSToken();
 * }
 * ```
 */
public fun parseSkSVGFeDisplacementMapChannelSelector(channel: SkSVGFeDisplacementMap.ChannelSelector?) {
  TODO("Implement parseSkSVGFeDisplacementMapChannelSelector")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool SkSVGAttributeParser::parse<SkSVGFeGaussianBlur::StdDeviation>(
 *         SkSVGFeGaussianBlur::StdDeviation* stdDeviation) {
 *     std::vector<SkSVGNumberType> values;
 *     if (!this->parse(&values)) {
 *         return false;
 *     }
 *
 *     stdDeviation->fX = values[0];
 *     stdDeviation->fY = values.size() > 1 ? values[1] : values[0];
 *     return true;
 * }
 * ```
 */
public fun parseSkSVGFeGaussianBlurStdDeviation(stdDeviation: SkSVGFeGaussianBlur.StdDeviation?) {
  TODO("Implement parseSkSVGFeGaussianBlurStdDeviation")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool SkSVGAttributeParser::parse<SkSVGFeLighting::KernelUnitLength>(
 *         SkSVGFeLighting::KernelUnitLength* kernelUnitLength) {
 *     std::vector<SkSVGNumberType> values;
 *     if (!this->parse(&values)) {
 *         return false;
 *     }
 *
 *     kernelUnitLength->fDx = values[0];
 *     kernelUnitLength->fDy = values.size() > 1 ? values[1] : values[0];
 *     return true;
 * }
 * ```
 */
public fun parseSkSVGFeLightingKernelUnitLength(kernelUnitLength: SkSVGFeLighting.KernelUnitLength?) {
  TODO("Implement parseSkSVGFeLightingKernelUnitLength")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool SkSVGAttributeParser::parse<SkSVGFeMorphology::Operator>(SkSVGFeMorphology::Operator* op) {
 *     static constexpr std::tuple<const char*, SkSVGFeMorphology::Operator> gMap[] = {
 *             { "dilate", SkSVGFeMorphology::Operator::kDilate },
 *             { "erode" , SkSVGFeMorphology::Operator::kErode  },
 *     };
 *
 *     return this->parseEnumMap(gMap, op) && this->parseEOSToken();
 * }
 * ```
 */
public fun parseSkSVGFeMorphologyOperator(op: SkSVGFeMorphology.Operator?) {
  TODO("Implement parseSkSVGFeMorphologyOperator")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool SkSVGAttributeParser::parse<SkSVGFeMorphology::Radius>(SkSVGFeMorphology::Radius* radius) {
 *     std::vector<SkSVGNumberType> values;
 *     if (!this->parse(&values)) {
 *         return false;
 *     }
 *
 *     radius->fX = values[0];
 *     radius->fY = values.size() > 1 ? values[1] : values[0];
 *     return true;
 * }
 * ```
 */
public fun parseSkSVGFeMorphologyRadius(radius: SkSVGFeMorphology.Radius?) {
  TODO("Implement parseSkSVGFeMorphologyRadius")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImageFilter> ConvertFilterColorspace(sk_sp<SkImageFilter>&& input,
 *                                              SkSVGColorspace src,
 *                                              SkSVGColorspace dst) {
 *     if (src == dst) {
 *         return std::move(input);
 *     } else if (src == SkSVGColorspace::kSRGB && dst == SkSVGColorspace::kLinearRGB) {
 *         return SkImageFilters::ColorFilter(SkColorFilters::SRGBToLinearGamma(), input);
 *     } else {
 *         SkASSERT(src == SkSVGColorspace::kLinearRGB && dst == SkSVGColorspace::kSRGB);
 *         return SkImageFilters::ColorFilter(SkColorFilters::LinearToSRGBGamma(), input);
 *     }
 * }
 * ```
 */
public fun convertFilterColorspace(
  input: SkSp<SkImageFilter>,
  src: SkSVGColorspace,
  dst: SkSVGColorspace,
): SkSp<SkImageFilter> {
  TODO("Implement convertFilterColorspace")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShader> paint_as_shader(const SkPaint& paint) {
 *     sk_sp<SkShader> shader = paint.refShader();
 *     auto color = paint.getColor4f();
 *     if (shader && color.fA < 1.f) {
 *         // Multiply by paint alpha
 *         shader = shader->makeWithColorFilter(
 *                 SkColorFilters::Blend(color, /*colorSpace=*/nullptr, SkBlendMode::kDstIn));
 *     } else if (!shader) {
 *         shader = SkShaders::Color(color, /*colorSpace=*/nullptr);
 *     }
 *     if (paint.getColorFilter()) {
 *         shader = shader->makeWithColorFilter(paint.refColorFilter());
 *     }
 *     return shader;
 * }
 * ```
 */
public fun paintAsShader(paint: SkPaint): SkSp<SkShader> {
  TODO("Implement paintAsShader")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> LoadImage(const sk_sp<skresources::ResourceProvider>& rp,
 *                                 const SkSVGIRI& href) {
 *     // TODO: It may be better to use the SVG 'id' attribute as the asset id, to allow
 *     // clients to perform asset substitution based on element id.
 *     sk_sp<skresources::ImageAsset> imageAsset;
 *     switch (href.type()) {
 *         case SkSVGIRI::Type::kDataURI:
 *             imageAsset = rp->loadImageAsset("", href.iri().c_str(), "");
 *             break;
 *         case SkSVGIRI::Type::kNonlocal: {
 *             const auto path = SkOSPath::Dirname(href.iri().c_str());
 *             const auto name = SkOSPath::Basename(href.iri().c_str());
 *             imageAsset = rp->loadImageAsset(path.c_str(), name.c_str(), /* id */ name.c_str());
 *             break;
 *         }
 *         default:
 *             SkDEBUGF("error loading image: unhandled iri type %d\n", (int)href.type());
 *             return nullptr;
 *     }
 *
 *     return imageAsset ? imageAsset->getFrameData(0).image : nullptr;
 * }
 * ```
 */
public fun loadImage(rp: SkSp<ResourceProvider>, href: SkSVGIRI): SkSp<SkImage> {
  TODO("Implement loadImage")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * int inherit_if_needed(const std::optional<T>& src, std::optional<T>& dst) {
 *     if (!dst.has_value()) {
 *         dst = src;
 *         return 1;
 *     }
 *
 *     return 0;
 * }
 * ```
 */
public fun <T> inheritIfNeeded(src: T?, dst: T?): Int {
  TODO("Implement inheritIfNeeded")
}

/**
 * C++ original:
 * ```cpp
 * std::tuple<float, float> ResolveOptionalRadii(const std::optional<SkSVGLength>& opt_rx,
 *                                               const std::optional<SkSVGLength>& opt_ry,
 *                                               const SkSVGLengthContext& lctx) {
 *     // https://www.w3.org/TR/SVG2/shapes.html#RectElement
 *     //
 *     // The used values for rx and ry are determined from the computed values by following these
 *     // steps in order:
 *     //
 *     // 1. If both rx and ry have a computed value of auto (since auto is the initial value for both
 *     //    properties, this will also occur if neither are specified by the author or if all
 *     //    author-supplied values are invalid), then the used value of both rx and ry is 0.
 *     //    (This will result in square corners.)
 *     // 2. Otherwise, convert specified values to absolute values as follows:
 *     //     1. If rx is set to a length value or a percentage, but ry is auto, calculate an absolute
 *     //        length equivalent for rx, resolving percentages against the used width of the
 *     //        rectangle; the absolute value for ry is the same.
 *     //     2. If ry is set to a length value or a percentage, but rx is auto, calculate the absolute
 *     //        length equivalent for ry, resolving percentages against the used height of the
 *     //        rectangle; the absolute value for rx is the same.
 *     //     3. If both rx and ry were set to lengths or percentages, absolute values are generated
 *     //        individually, resolving rx percentages against the used width, and resolving ry
 *     //        percentages against the used height.
 *     const float rx = opt_rx.has_value()
 *         ? lctx.resolve(*opt_rx, SkSVGLengthContext::LengthType::kHorizontal)
 *         : 0;
 *     const float ry = opt_ry.has_value()
 *         ? lctx.resolve(*opt_ry, SkSVGLengthContext::LengthType::kVertical)
 *         : 0;
 *
 *     return std::make_tuple(opt_rx.has_value() ? rx : ry,
 *                            opt_ry.has_value() ? ry : rx);
 * }
 * ```
 */
public fun resolveOptionalRadii(
  optRx: SkSVGLength?,
  optRy: SkSVGLength?,
  lctx: SkSVGLengthContext,
): Pair<Float, Float> {
  TODO("Implement resolveOptionalRadii")
}

/**
 * C++ original:
 * ```cpp
 * SkScalar length_size_for_type(const SkSize& viewport, SkSVGLengthContext::LengthType t) {
 *     switch (t) {
 *     case SkSVGLengthContext::LengthType::kHorizontal:
 *         return viewport.width();
 *     case SkSVGLengthContext::LengthType::kVertical:
 *         return viewport.height();
 *     case SkSVGLengthContext::LengthType::kOther: {
 *         // https://www.w3.org/TR/SVG11/coords.html#Units_viewport_percentage
 *         constexpr SkScalar rsqrt2 = 1.0f / SK_ScalarSqrt2;
 *         const SkScalar w = viewport.width(), h = viewport.height();
 *         return rsqrt2 * SkScalarSqrt(w * w + h * h);
 *     }
 *     }
 *
 *     SkASSERT(false);  // Not reached.
 *     return 0;
 * }
 * ```
 */
public fun lengthSizeForType(viewport: SkSize, t: SkSVGLengthContext.LengthType): SkScalar {
  TODO("Implement lengthSizeForType")
}

/**
 * C++ original:
 * ```cpp
 * SkPaint::Cap toSkCap(const SkSVGLineCap& cap) {
 *     switch (cap) {
 *     case SkSVGLineCap::kButt:
 *         return SkPaint::kButt_Cap;
 *     case SkSVGLineCap::kRound:
 *         return SkPaint::kRound_Cap;
 *     case SkSVGLineCap::kSquare:
 *         return SkPaint::kSquare_Cap;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun toSkCap(cap: SkSVGLineCap): SkPaint.Cap {
  TODO("Implement toSkCap")
}

/**
 * C++ original:
 * ```cpp
 * SkPaint::Join toSkJoin(const SkSVGLineJoin& join) {
 *     switch (join.type()) {
 *     case SkSVGLineJoin::Type::kMiter:
 *         return SkPaint::kMiter_Join;
 *     case SkSVGLineJoin::Type::kRound:
 *         return SkPaint::kRound_Join;
 *     case SkSVGLineJoin::Type::kBevel:
 *         return SkPaint::kBevel_Join;
 *     default:
 *         SkASSERT(false);
 *         return SkPaint::kMiter_Join;
 *     }
 * }
 * ```
 */
public fun toSkJoin(join: SkSVGLineJoin): SkPaint.Join {
  TODO("Implement toSkJoin")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkPathEffect> dash_effect(const SkSVGPresentationAttributes& props,
 *                                        const SkSVGLengthContext& lctx) {
 *     if (props.fStrokeDashArray->type() != SkSVGDashArray::Type::kDashArray) {
 *         return nullptr;
 *     }
 *
 *     const auto& da = *props.fStrokeDashArray;
 *     const auto count = da.dashArray().size();
 *     STArray<128, SkScalar, true> intervals(count);
 *     for (const auto& dash : da.dashArray()) {
 *         intervals.push_back(lctx.resolve(dash, SkSVGLengthContext::LengthType::kOther));
 *     }
 *
 *     if (count & 1) {
 *         // If an odd number of values is provided, then the list of values
 *         // is repeated to yield an even number of values.
 *         intervals.push_back_n(count);
 *         memcpy(intervals.begin() + count, intervals.begin(), count * sizeof(SkScalar));
 *     }
 *
 *     SkASSERT((intervals.size() & 1) == 0);
 *
 *     const auto phase = lctx.resolve(*props.fStrokeDashOffset,
 *                                     SkSVGLengthContext::LengthType::kOther);
 *
 *     return SkDashPathEffect::Make(intervals, phase);
 * }
 * ```
 */
public fun dashEffect(props: SkSVGPresentationAttributes, lctx: SkSVGLengthContext): SkSp<SkPathEffect> {
  TODO("Implement dashEffect")
}

/**
 * C++ original:
 * ```cpp
 * static SkFont ResolveFont(const SkSVGRenderContext& ctx) {
 *     auto weight = [](const SkSVGFontWeight& w) {
 *         switch (w.type()) {
 *             case SkSVGFontWeight::Type::k100:     return SkFontStyle::kThin_Weight;
 *             case SkSVGFontWeight::Type::k200:     return SkFontStyle::kExtraLight_Weight;
 *             case SkSVGFontWeight::Type::k300:     return SkFontStyle::kLight_Weight;
 *             case SkSVGFontWeight::Type::k400:     return SkFontStyle::kNormal_Weight;
 *             case SkSVGFontWeight::Type::k500:     return SkFontStyle::kMedium_Weight;
 *             case SkSVGFontWeight::Type::k600:     return SkFontStyle::kSemiBold_Weight;
 *             case SkSVGFontWeight::Type::k700:     return SkFontStyle::kBold_Weight;
 *             case SkSVGFontWeight::Type::k800:     return SkFontStyle::kExtraBold_Weight;
 *             case SkSVGFontWeight::Type::k900:     return SkFontStyle::kBlack_Weight;
 *             case SkSVGFontWeight::Type::kNormal:  return SkFontStyle::kNormal_Weight;
 *             case SkSVGFontWeight::Type::kBold:    return SkFontStyle::kBold_Weight;
 *             case SkSVGFontWeight::Type::kBolder:  return SkFontStyle::kExtraBold_Weight;
 *             case SkSVGFontWeight::Type::kLighter: return SkFontStyle::kLight_Weight;
 *             case SkSVGFontWeight::Type::kInherit: {
 *                 SkASSERT(false);
 *                 return SkFontStyle::kNormal_Weight;
 *             }
 *         }
 *         SkUNREACHABLE;
 *     };
 *
 *     auto slant = [](const SkSVGFontStyle& s) {
 *         switch (s.type()) {
 *             case SkSVGFontStyle::Type::kNormal:  return SkFontStyle::kUpright_Slant;
 *             case SkSVGFontStyle::Type::kItalic:  return SkFontStyle::kItalic_Slant;
 *             case SkSVGFontStyle::Type::kOblique: return SkFontStyle::kOblique_Slant;
 *             case SkSVGFontStyle::Type::kInherit: {
 *                 SkASSERT(false);
 *                 return SkFontStyle::kUpright_Slant;
 *             }
 *         }
 *         SkUNREACHABLE;
 *     };
 *
 *     const auto& family = ctx.presentationContext().fInherited.fFontFamily->family();
 *     const SkFontStyle style(weight(*ctx.presentationContext().fInherited.fFontWeight),
 *                             SkFontStyle::kNormal_Width,
 *                             slant(*ctx.presentationContext().fInherited.fFontStyle));
 *
 *     const auto size =
 *             ctx.lengthContext().resolve(ctx.presentationContext().fInherited.fFontSize->size(),
 *                                         SkSVGLengthContext::LengthType::kVertical);
 *
 *     // TODO: we likely want matchFamilyStyle here, but switching away from legacyMakeTypeface
 *     // changes all the results when using the default fontmgr.
 *     auto tf = ctx.fontMgr()->legacyMakeTypeface(family.c_str(), style);
 *     if (!tf) {
 *         tf = ctx.fontMgr()->legacyMakeTypeface(nullptr, style);
 *     }
 *     SkASSERT(tf);
 *     SkFont font(std::move(tf), size);
 *     font.setHinting(SkFontHinting::kNone);
 *     font.setSubpixel(true);
 *     font.setLinearMetrics(true);
 *     font.setBaselineSnap(false);
 *     font.setEdging(SkFont::Edging::kAntiAlias);
 *
 *     return font;
 * }
 * ```
 */
public fun resolveFont(ctx: SkSVGRenderContext): SkFont {
  TODO("Implement resolveFont")
}

/**
 * C++ original:
 * ```cpp
 * static std::vector<float> ResolveLengths(const SkSVGLengthContext& lctx,
 *                                          const std::vector<SkSVGLength>& lengths,
 *                                          SkSVGLengthContext::LengthType lt) {
 *     std::vector<float> resolved;
 *     resolved.reserve(lengths.size());
 *
 *     for (const auto& l : lengths) {
 *         resolved.push_back(lctx.resolve(l, lt));
 *     }
 *
 *     return resolved;
 * }
 * ```
 */
public fun resolveLengths(
  lctx: SkSVGLengthContext,
  lengths: List<SkSVGLength>,
  lt: SkSVGLengthContext.LengthType,
): List<Float> {
  TODO("Implement resolveLengths")
}

/**
 * C++ original:
 * ```cpp
 * static float ComputeAlignmentFactor(const SkSVGPresentationContext& pctx) {
 *     switch (pctx.fInherited.fTextAnchor->type()) {
 *     case SkSVGTextAnchor::Type::kStart : return  0.0f;
 *     case SkSVGTextAnchor::Type::kMiddle: return -0.5f;
 *     case SkSVGTextAnchor::Type::kEnd   : return -1.0f;
 *     case SkSVGTextAnchor::Type::kInherit:
 *         SkASSERT(false);
 *         return 0.0f;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun computeAlignmentFactor(pctx: SkSVGPresentationContext): Float {
  TODO("Implement computeAlignmentFactor")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkBlender> hardMix() {
 *     static SkRuntimeEffect* hardMixEffect = []{
 *         const char hardMix[] =
 *             "half4 main(half4 src, half4 dst) {"
 *                 "src.rgb = unpremul(src).rgb + unpremul(dst).rgb;"
 *                 "src.rgb = min(floor(src.rgb), 1) * src.a;"
 *
 *                 "return src + (1 - src.a)*dst;"
 *             "}"
 *         ;
 *         auto result = SkRuntimeEffect::MakeForBlender(SkString(hardMix));
 *         return result.effect.release();
 *     }();
 *     return hardMixEffect->makeBlender(nullptr);
 * }
 * ```
 */
public fun hardMix(): SkSp<SkBlender> {
  TODO("Implement hardMix")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkBlender> get_blender(const skjson::ObjectValue& jobject,
 *                                     const AnimationBuilder* abuilder) {
 *     static constexpr SkBlendMode kBlendModeMap[] = {
 *         SkBlendMode::kSrcOver,    // 0:'normal'
 *         SkBlendMode::kMultiply,   // 1:'multiply'
 *         SkBlendMode::kScreen,     // 2:'screen'
 *         SkBlendMode::kOverlay,    // 3:'overlay
 *         SkBlendMode::kDarken,     // 4:'darken'
 *         SkBlendMode::kLighten,    // 5:'lighten'
 *         SkBlendMode::kColorDodge, // 6:'color-dodge'
 *         SkBlendMode::kColorBurn,  // 7:'color-burn'
 *         SkBlendMode::kHardLight,  // 8:'hard-light'
 *         SkBlendMode::kSoftLight,  // 9:'soft-light'
 *         SkBlendMode::kDifference, // 10:'difference'
 *         SkBlendMode::kExclusion,  // 11:'exclusion'
 *         SkBlendMode::kHue,        // 12:'hue'
 *         SkBlendMode::kSaturation, // 13:'saturation'
 *         SkBlendMode::kColor,      // 14:'color'
 *         SkBlendMode::kLuminosity, // 15:'luminosity'
 *         SkBlendMode::kPlus,       // 16:'add'
 *     };
 *
 *     const size_t mode = ParseDefault<size_t>(jobject["bm"], 0);
 *
 *     // Special handling of src-over, so we can detect the trivial/no-fancy-blending case
 *     // (a null blender is equivalent to src-over).
 *     if (!mode) {
 *         return nullptr;
 *     }
 *
 *     // Modes that are expressible as SkBlendMode.
 *     if (mode < std::size(kBlendModeMap)) {
 *         return SkBlender::Mode(kBlendModeMap[mode]);
 *     }
 *
 *     // Modes that require custom blenders.
 *     switch (mode)
 *     {
 *     case HARDMIX:
 *         return hardMix();
 *     default:
 *         break;
 *     }
 *
 *     abuilder->log(Logger::Level::kWarning, &jobject, "Unsupported blend mode %zu\n", mode);
 *     return nullptr;
 * }
 * ```
 */
public fun getBlender(jobject: ObjectValue, abuilder: AnimationBuilder?): SkSp<SkBlender> {
  TODO("Implement getBlender")
}

/**
 * C++ original:
 * ```cpp
 * SkM44 ComputeCameraMatrix(const SkV3& position,
 *                           const SkV3& poi,
 *                           const SkV3& rotation,
 *                           const SkSize& viewport_size,
 *                           float zoom) {
 *
 *     // Initial camera vector.
 *     const auto cam_t = SkM44::Rotate({0, 0, 1}, SkDegreesToRadians(-rotation.z))
 *                      * SkM44::Rotate({0, 1, 0}, SkDegreesToRadians( rotation.y))
 *                      * SkM44::Rotate({1, 0, 0}, SkDegreesToRadians( rotation.x))
 *                      * SkM44::LookAt({ position.x, position.y, -position.z },
 *                                      {      poi.x,      poi.y,       poi.z },
 *                                      {          0,          1,           0 })
 *                      * SkM44::Scale(1, 1, -1);
 *
 *     // View parameters:
 *     //
 *     //   * size     -> composition size (TODO: AE seems to base it on width only?)
 *     //   * distance -> "zoom" camera attribute
 *     //
 *     const auto view_size     = std::max(viewport_size.width(), viewport_size.height()),
 *                view_distance = zoom,
 *                view_angle    = std::atan(sk_ieee_float_divide(view_size * 0.5f, view_distance));
 *
 *     const auto persp_t = SkM44::Scale(view_size * 0.5f, view_size * 0.5f, 1)
 *                        * SkM44::Perspective(0, view_distance, 2 * view_angle);
 *
 *     return SkM44::Translate(viewport_size.width()  * 0.5f,
 *                             viewport_size.height() * 0.5f,
 *                             0)
 *            * persp_t * cam_t;
 * }
 * ```
 */
public fun computeCameraMatrix(
  position: SkV3,
  poi: SkV3,
  rotation: SkV3,
  viewportSize: SkSize,
  zoom: Float,
): SkM44 {
  TODO("Implement computeCameraMatrix")
}

/**
 * C++ original:
 * ```cpp
 * const MaskInfo* GetMaskInfo(char mode) {
 *     static constexpr MaskInfo k_add_info =
 *         { SkBlendMode::kSrcOver   , sksg::Merge::Mode::kUnion     , false };
 *     static constexpr MaskInfo k_int_info =
 *         { SkBlendMode::kSrcIn     , sksg::Merge::Mode::kIntersect , false };
 *     static constexpr MaskInfo k_sub_info =
 *         { SkBlendMode::kDstOut    , sksg::Merge::Mode::kDifference, true  };
 *     static constexpr MaskInfo k_dif_info =
 *         { SkBlendMode::kXor       , sksg::Merge::Mode::kXOR       , false };
 *
 *     switch (mode) {
 *     case 'a': return &k_add_info;
 *     case 'f': return &k_dif_info;
 *     case 'i': return &k_int_info;
 *     case 's': return &k_sub_info;
 *     default: break;
 *     }
 *
 *     return nullptr;
 * }
 * ```
 */
public fun getMaskInfo(mode: Char): MaskInfo {
  TODO("Implement getMaskInfo")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<sksg::RenderNode> AttachMask(const skjson::ArrayValue* jmask,
 *                                    const AnimationBuilder* abuilder,
 *                                    sk_sp<sksg::RenderNode> childNode) {
 *     if (!jmask) return childNode;
 *
 *     struct MaskRecord {
 *         sk_sp<sksg::Path>  mask_path;    // for clipping and masking
 *         sk_sp<MaskAdapter> mask_adapter; // for masking
 *         sksg::Merge::Mode  merge_mode;   // for clipping
 *     };
 *
 *     STArray<4, MaskRecord, true> mask_stack;
 *     bool has_effect = false;
 *
 *     for (const skjson::ObjectValue* m : *jmask) {
 *         if (!m) continue;
 *
 *         const skjson::StringValue* jmode = (*m)["mode"];
 *         if (!jmode || jmode->size() != 1) {
 *             abuilder->log(Logger::Level::kError, &(*m)["mode"], "Invalid mask mode.");
 *             continue;
 *         }
 *
 *         const auto mode = *jmode->begin();
 *         if (mode == 'n') {
 *             // "None" masks have no effect.
 *             continue;
 *         }
 *
 *         const auto* mask_info = GetMaskInfo(mode);
 *         if (!mask_info) {
 *             abuilder->log(Logger::Level::kWarning, nullptr, "Unsupported mask mode: '%c'.", mode);
 *             continue;
 *         }
 *
 *         auto mask_path = abuilder->attachPath((*m)["pt"]);
 *         if (!mask_path) {
 *             abuilder->log(Logger::Level::kError, m, "Could not parse mask path.");
 *             continue;
 *         }
 *
 *         auto mask_blend_mode = mask_info->fBlendMode;
 *         auto mask_merge_mode = mask_info->fMergeMode;
 *         auto mask_inverted   = ParseDefault<bool>((*m)["inv"], false);
 *
 *         if (mask_stack.empty()) {
 *             // First mask adjustments:
 *             //   - always draw in source mode
 *             //   - invert geometry if needed
 *             mask_blend_mode = SkBlendMode::kSrc;
 *             mask_merge_mode = sksg::Merge::Mode::kMerge;
 *             mask_inverted   = mask_inverted != mask_info->fInvertGeometry;
 *         }
 *
 *         mask_path->setFillType(mask_inverted ? SkPathFillType::kInverseWinding
 *                                              : SkPathFillType::kWinding);
 *
 *         auto mask_adapter = sk_make_sp<MaskAdapter>(*m, *abuilder, mask_blend_mode);
 *         abuilder->attachDiscardableAdapter(mask_adapter);
 *
 *         has_effect |= mask_adapter->hasEffect();
 *
 *         mask_stack.push_back({ std::move(mask_path),
 *                                std::move(mask_adapter),
 *                                mask_merge_mode });
 *     }
 *
 *
 *     if (mask_stack.empty())
 *         return childNode;
 *
 *     // If the masks are fully opaque, we can clip.
 *     if (!has_effect) {
 *         sk_sp<sksg::GeometryNode> clip_node;
 *
 *         if (mask_stack.size() == 1) {
 *             // Single path -> just clip.
 *             clip_node = std::move(mask_stack.front().mask_path);
 *         } else {
 *             // Multiple clip paths -> merge.
 *             std::vector<sksg::Merge::Rec> merge_recs;
 *             merge_recs.reserve(SkToSizeT(mask_stack.size()));
 *
 *             for (auto& mask : mask_stack) {
 *                 merge_recs.push_back({std::move(mask.mask_path), mask.merge_mode });
 *             }
 *             clip_node = sksg::Merge::Make(std::move(merge_recs));
 *         }
 *
 *         return sksg::ClipEffect::Make(std::move(childNode), std::move(clip_node), true);
 *     }
 *
 *     // Complex masks (non-opaque or blurred) turn into a mask node stack.
 *     sk_sp<sksg::RenderNode> maskNode;
 *     if (mask_stack.size() == 1) {
 *         // no group needed for single mask
 *         const auto rec = mask_stack.front();
 *         maskNode = rec.mask_adapter->makeMask(std::move(rec.mask_path));
 *     } else {
 *         std::vector<sk_sp<sksg::RenderNode>> masks;
 *         masks.reserve(SkToSizeT(mask_stack.size()));
 *         for (auto& rec : mask_stack) {
 *             masks.push_back(rec.mask_adapter->makeMask(std::move(rec.mask_path)));
 *         }
 *
 *         maskNode = sksg::Group::Make(std::move(masks));
 *     }
 *
 *     return sksg::MaskEffect::Make(std::move(childNode), std::move(maskNode));
 * }
 * ```
 */
public fun attachMask(
  jmask: ArrayValue?,
  abuilder: AnimationBuilder?,
  childNode: SkSp<RenderNode>,
): SkSp<RenderNode> {
  TODO("Implement attachMask")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<SkScalar>(const Value& v, SkScalar* s) {
 *     // Some versions wrap values as single-element arrays.
 *     if (const skjson::ArrayValue* array = v) {
 *         if (array->size() > 0) {
 *             return Parse((*array)[0], s);
 *         }
 *     }
 *
 *     if (const skjson::NumberValue* num = v) {
 *         *s = static_cast<SkScalar>(**num);
 *         return true;
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun parseSkScalar(v: Value, s: SkScalar?): Boolean {
  TODO("Implement parseSkScalar")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<bool>(const Value& v, bool* b) {
 *     switch(v.getType()) {
 *     case Value::Type::kNumber:
 *         *b = SkToBool(*v.as<NumberValue>());
 *         return true;
 *     case Value::Type::kBool:
 *         *b = *v.as<BoolValue>();
 *         return true;
 *     default:
 *         break;
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun parsebool(v: Value, b: Boolean?): Boolean {
  TODO("Implement parsebool")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * bool ParseIntegral(const Value& v, T* result) {
 *     if (const skjson::NumberValue* num = v) {
 *         const auto dbl = **num;
 *         if (dbl > static_cast<double>(std::numeric_limits<T>::max()) ||
 *             dbl < static_cast<double>(std::numeric_limits<T>::min())) {
 *             return false;
 *         }
 *
 *         *result = static_cast<T>(dbl);
 *         return true;
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun <T> parseIntegral(v: Value, result: T): Boolean {
  TODO("Implement parseIntegral")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<int>(const Value& v, int* i) {
 *     return ParseIntegral(v, i);
 * }
 * ```
 */
public fun parseint(v: Value, i: Int?): Boolean {
  TODO("Implement parseint")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<SkString>(const Value& v, SkString* s) {
 *     if (const skjson::StringValue* sv = v) {
 *         s->set(sv->begin(), sv->size());
 *         return true;
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun parseSkString(v: Value, s: String?): Boolean {
  TODO("Implement parseSkString")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<SkV2>(const Value& v, SkV2* v2) {
 *     if (!v.is<ArrayValue>())
 *         return false;
 *     const auto& av = v.as<ArrayValue>();
 *
 *     // We need at least two scalars (BM sometimes exports a third value == 0).
 *     return av.size() >= 2
 *         && Parse<SkScalar>(av[0], &v2->x)
 *         && Parse<SkScalar>(av[1], &v2->y);
 * }
 * ```
 */
public fun parseSkV2(v: Value, v2: SkV2?): Boolean {
  TODO("Implement parseSkV2")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<SkPoint>(const Value& v, SkPoint* pt) {
 *     if (!v.is<ObjectValue>())
 *         return false;
 *     const auto& ov = v.as<ObjectValue>();
 *
 *     return Parse<SkScalar>(ov["x"], &pt->fX)
 *         && Parse<SkScalar>(ov["y"], &pt->fY);
 * }
 * ```
 */
public fun parseSkPoint(v: Value, pt: SkPoint?): Boolean {
  TODO("Implement parseSkPoint")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * bool Parse<VectorValue>(const Value& v, VectorValue* vec) {
 *     if (!v.is<ArrayValue>())
 *         return false;
 *     const auto& av = v.as<ArrayValue>();
 *
 *     vec->resize(av.size());
 *     for (size_t i = 0; i < av.size(); ++i) {
 *         if (!Parse(av[i], vec->data() + i)) {
 *             return false;
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun parseVectorValue(v: Value, vec: VectorValue?): Boolean {
  TODO("Implement parseVectorValue")
}

/**
 * C++ original:
 * ```cpp
 * const skjson::StringValue* ParseSlotID(const skjson::ObjectValue* jobj) {
 *     if (jobj) {
 *         if (const skjson::StringValue* sid = (*jobj)["sid"]) {
 *             return sid;
 *         }
 *     }
 *     return nullptr;
 * }
 * ```
 */
public fun parseSlotID(jobj: ObjectValue?): StringValue {
  TODO("Implement parseSlotID")
}

/**
 * C++ original:
 * ```cpp
 * template <> SK_API
 * ColorPropertyHandle::PropertyHandle(const ColorPropertyHandle& other)
 *     : fNode(other.fNode), fRevalidator(other.fRevalidator) {}
 * ```
 */
public fun propertyHandle(other: ColorPropertyHandle) {
  TODO("Implement propertyHandle")
}

/**
 * C++ original:
 * ```cpp
 * template <> SK_API
 * void ColorPropertyHandle::set(const ColorPropertyValue& c) {
 *     fNode->setColor(c);
 *
 *     if (fRevalidator) {
 *         fRevalidator->revalidate();
 *     }
 * }
 * ```
 */
public fun `set`(c: ColorPropertyValue) {
  TODO("Implement set")
}

/**
 * C++ original:
 * ```cpp
 * static size_t shape_encoding_len(size_t vertex_count) {
 *     return vertex_count * kFloatsPerVertex + 1;
 * }
 * ```
 */
public fun shapeEncodingLen(vertexCount: ULong): ULong {
  TODO("Implement shapeEncodingLen")
}

/**
 * C++ original:
 * ```cpp
 * static const skjson::ObjectValue* shape_root(const skjson::Value& jv) {
 *     if (const skjson::ArrayValue* av = jv) {
 *         if (av->size() == 1) {
 *             return (*av)[0];
 *         }
 *     }
 *
 *     return jv;
 * }
 * ```
 */
public fun shapeRoot(jv: Value): ObjectValue {
  TODO("Implement shapeRoot")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_encoding_len(const skjson::Value& jv, size_t* len) {
 *     if (const auto* jshape = shape_root(jv)) {
 *         if (const skjson::ArrayValue* jvs = (*jshape)["v"]) {
 *             *len = shape_encoding_len(jvs->size());
 *             return true;
 *         }
 *     }
 *     return false;
 * }
 * ```
 */
public fun parseEncodingLen(jv: Value, len: ULong?): Boolean {
  TODO("Implement parseEncodingLen")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_encoding_data(const skjson::Value& jv, size_t data_len, float data[]) {
 *     const auto* jshape = shape_root(jv);
 *     if (!jshape) {
 *         return false;
 *     }
 *
 *     // vertices are required, in/out tangents are optional
 *     const skjson::ArrayValue* jvs = (*jshape)["v"]; // vertex points
 *     const skjson::ArrayValue* jis = (*jshape)["i"]; // in-tangent points
 *     const skjson::ArrayValue* jos = (*jshape)["o"]; // out-tangent points
 *
 *     if (!jvs || data_len != shape_encoding_len(jvs->size())) {
 *         return false;
 *     }
 *
 *     auto parse_point = [](const skjson::ArrayValue* ja, size_t i, float* x, float* y) {
 *         SkASSERT(ja);
 *         const skjson::ArrayValue* jpt = (*ja)[i];
 *
 *         if (!jpt || jpt->size() != 2ul) {
 *             return false;
 *         }
 *
 *         return Parse((*jpt)[0], x) && Parse((*jpt)[1], y);
 *     };
 *
 *     auto parse_optional_point = [&parse_point](const skjson::ArrayValue* ja, size_t i,
 *                                                float* x, float* y) {
 *         if (!ja || i >= ja->size()) {
 *             // default control point
 *             *x = *y = 0;
 *             return true;
 *         }
 *
 *         return parse_point(*ja, i, x, y);
 *     };
 *
 *     for (size_t i = 0; i < jvs->size(); ++i) {
 *         float* dst = data + i * kFloatsPerVertex;
 *         SkASSERT(dst + kFloatsPerVertex <= data + data_len);
 *
 *         if (!parse_point         (jvs, i, dst +    kX_Index, dst +    kY_Index) ||
 *             !parse_optional_point(jis, i, dst +  kInX_Index, dst +  kInY_Index) ||
 *             !parse_optional_point(jos, i, dst + kOutX_Index, dst + kOutY_Index)) {
 *             return false;
 *         }
 *     }
 *
 *     // "closed" flag
 *     data[data_len - 1] = ParseDefault<bool>((*jshape)["c"], false);
 *
 *     return true;
 * }
 * ```
 */
public fun parseEncodingData(
  jv: Value,
  dataLen: ULong,
  `data`: FloatArray,
): Boolean {
  TODO("Implement parseEncodingData")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_array(const skjson::ArrayValue* ja, float* a, size_t count) {
 *     if (!ja || ja->size() != count) {
 *         return false;
 *     }
 *
 *     for (size_t i = 0; i < count; ++i) {
 *         if (!Parse((*ja)[i], a + i)) {
 *             return false;
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun parseArray(
  ja: ArrayValue?,
  a: Float?,
  count: ULong,
): Boolean {
  TODO("Implement parseArray")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkData> make_contrast_coeffs(float contrast) {
 *     struct { float a, b, c; } coeffs;
 *
 *     coeffs.b = SK_ScalarPI * contrast;
 *     coeffs.a = -2 * coeffs.b / 3;
 *     coeffs.c =  1 - coeffs.b / 3;
 *
 *     return SkData::MakeWithCopy(&coeffs, sizeof(coeffs));
 * }
 * ```
 */
public fun makeContrastCoeffs(contrast: Float): SkSp<SkData> {
  TODO("Implement makeContrastCoeffs")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkData> make_brightness_coeffs(float brightness) {
 *     const float coeff_a = std::pow(2.0f, brightness * 1.8f);
 *
 *     return SkData::MakeWithCopy(&coeff_a, sizeof(coeff_a));
 * }
 * ```
 */
public fun makeBrightnessCoeffs(brightness: Float): SkSp<SkData> {
  TODO("Implement makeBrightnessCoeffs")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkRuntimeEffect> bulge_effect() {
 *     static const SkRuntimeEffect* effect =
 *         SkRuntimeEffect::MakeForShader(SkString(gBulgeDisplacementSkSL), {}).effect.release();
 *     SkASSERT(effect);
 *
 *     return sk_ref_sp(effect);
 * }
 * ```
 */
public fun bulgeEffect(): SkSp<SkRuntimeEffect> {
  TODO("Implement bulgeEffect")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkRuntimeEffect> displacement_effect_singleton() {
 *     static const SkRuntimeEffect* effect =
 *             SkRuntimeEffect::MakeForShader(SkString(gDisplacementSkSL)).effect.release();
 *     if (0 && !effect) {
 *         auto err = SkRuntimeEffect::MakeForShader(SkString(gDisplacementSkSL)).errorText;
 *         printf("!!! %s\n", err.c_str());
 *     }
 *     SkASSERT(effect);
 *
 *     return sk_ref_sp(effect);
 * }
 * ```
 */
public fun displacementEffectSingleton(): SkSp<SkRuntimeEffect> {
  TODO("Implement displacementEffectSingleton")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkRuntimeEffect> make_noise_effect(unsigned loops, const char* filter, const char* fractal) {
 *     auto result = SkRuntimeEffect::MakeForShader(
 *             SkStringPrintf(gNoiseEffectSkSL, filter, fractal, loops), {});
 *
 *     return std::move(result.effect);
 * }
 * ```
 */
public fun makeNoiseEffect(
  loops: UInt,
  filter: String?,
  fractal: String?,
): SkSp<SkRuntimeEffect> {
  TODO("Implement makeNoiseEffect")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkRuntimeEffect> noise_effect(float octaves, NoiseFilter filter, NoiseFractal fractal) {
 *     static constexpr char const* gFilters[] = {
 *         gFilterNearestSkSL,
 *         gFilterLinearSkSL,
 *         gFilterSoftLinearSkSL
 *     };
 *
 *     static constexpr char const* gFractals[] = {
 *         gFractalBasicSkSL,
 *         gFractalTurbulentBasicSkSL,
 *         gFractalTurbulentSmoothSkSL,
 *         gFractalTurbulentSharpSkSL
 *     };
 *
 *     SkASSERT(static_cast<size_t>(filter)  < std::size(gFilters));
 *     SkASSERT(static_cast<size_t>(fractal) < std::size(gFractals));
 *
 *     // Bin the loop counter based on the number of octaves (range: [1..20]).
 *     // Low complexities are common, so we maximize resolution for the low end.
 *     struct BinInfo {
 *         float    threshold;
 *         unsigned loops;
 *     };
 *     static constexpr BinInfo kLoopBins[] = {
 *         { 8, 20 },
 *         { 4,  8 },
 *         { 3,  4 },
 *         { 2,  3 },
 *         { 1,  2 },
 *         { 0,  1 }
 *     };
 *
 *     auto bin_index = [](float octaves) {
 *         SkASSERT(octaves > kLoopBins[std::size(kLoopBins) - 1].threshold);
 *
 *         for (size_t i = 0; i < std::size(kLoopBins); ++i) {
 *             if (octaves > kLoopBins[i].threshold) {
 *                 return i;
 *             }
 *         }
 *         SkUNREACHABLE;
 *     };
 *
 *     static SkRuntimeEffect* kEffectCache[std::size(kLoopBins)]
 *                                         [std::size(gFilters)]
 *                                         [std::size(gFractals)];
 *
 *     const size_t bin = bin_index(octaves);
 *
 *     auto& effect = kEffectCache[bin]
 *                                [static_cast<size_t>(filter)]
 *                                [static_cast<size_t>(fractal)];
 *     if (!effect) {
 *         effect = make_noise_effect(kLoopBins[bin].loops,
 *                                    gFilters[static_cast<size_t>(filter)],
 *                                    gFractals[static_cast<size_t>(fractal)])
 *                  .release();
 *     }
 *
 *     SkASSERT(effect);
 *     return sk_ref_sp(effect);
 * }
 * ```
 */
public fun noiseEffect(
  octaves: Float,
  filter: NoiseFilter,
  fractal: NoiseFractal,
): SkSp<SkRuntimeEffect> {
  TODO("Implement noiseEffect")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<sksg::RenderNode> make_glow_effect(const skjson::ObjectValue& jstyle,
 *                                                 const AnimationBuilder& abuilder,
 *                                                 sk_sp<sksg::RenderNode> layer,
 *                                                 GlowAdapter::Type type) {
 *     auto filter_node = abuilder.attachDiscardableAdapter<GlowAdapter>(jstyle, abuilder, type);
 *
 *     return sksg::ImageFilterEffect::Make(std::move(layer), std::move(filter_node));
 * }
 * ```
 */
public fun makeGlowEffect(
  jstyle: ObjectValue,
  abuilder: AnimationBuilder,
  layer: SkSp<RenderNode>,
  type: GlowAdapter.Type,
): SkSp<RenderNode> {
  TODO("Implement makeGlowEffect")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkColorFilter> make_saturate(float chroma_scale) {
 *     static const auto* effect =
 *             SkRuntimeEffect::MakeForColorFilter(SkString(gSaturateSkSL), {}).effect.release();
 *     SkASSERT(effect);
 *
 *     return effect->makeColorFilter(SkData::MakeWithCopy(&chroma_scale, sizeof(chroma_scale)));
 * }
 * ```
 */
public fun makeSaturate(chromaScale: Float): SkSp<SkColorFilter> {
  TODO("Implement makeSaturate")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<sksg::RenderNode> make_shadow_effect(const skjson::ObjectValue& jstyle,
 *                                                   const AnimationBuilder& abuilder,
 *                                                   sk_sp<sksg::RenderNode> layer,
 *                                                   ShadowAdapter::Type type) {
 *     auto filter_node = abuilder.attachDiscardableAdapter<ShadowAdapter>(jstyle, abuilder, type);
 *
 *     return sksg::ImageFilterEffect::Make(std::move(layer), std::move(filter_node));
 * }
 * ```
 */
public fun makeShadowEffect(
  jstyle: ObjectValue,
  abuilder: AnimationBuilder,
  layer: SkSp<RenderNode>,
  type: ShadowAdapter.Type,
): SkSp<RenderNode> {
  TODO("Implement makeShadowEffect")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkRuntimeEffect> sphere_fancylight_effect() {
 *     static const SkRuntimeEffect* effect =
 *             SkRuntimeEffect::MakeForShader(SkStringPrintf(gSphereSkSL, gFancyLightSkSL), {})
 *                     .effect.release();
 *     if (0 && !effect) {
 *         printf("!!! %s\n",
 *                SkRuntimeEffect::MakeForShader(SkStringPrintf(gSphereSkSL, gFancyLightSkSL), {})
 *                        .errorText.c_str());
 *     }
 *     SkASSERT(effect);
 *
 *     return sk_ref_sp(effect);
 * }
 * ```
 */
public fun sphereFancylightEffect(): SkSp<SkRuntimeEffect> {
  TODO("Implement sphereFancylightEffect")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkRuntimeEffect> sphere_basiclight_effect() {
 *     static const SkRuntimeEffect* effect =
 *             SkRuntimeEffect::MakeForShader(SkStringPrintf(gSphereSkSL, gBasicLightSkSL), {})
 *                     .effect.release();
 *     SkASSERT(effect);
 *
 *     return sk_ref_sp(effect);
 * }
 * ```
 */
public fun sphereBasiclightEffect(): SkSp<SkRuntimeEffect> {
  TODO("Implement sphereBasiclightEffect")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkRuntimeEffect> threshold_effect() {
 *     static const SkRuntimeEffect* effect =
 *         SkRuntimeEffect::MakeForColorFilter(SkString(gThresholdSkSL), {}).effect.release();
 *     SkASSERT(effect);
 *
 *     return sk_ref_sp(effect);
 * }
 * ```
 */
public fun thresholdEffect(): SkSp<SkRuntimeEffect> {
  TODO("Implement thresholdEffect")
}

/**
 * C++ original:
 * ```cpp
 * SkMatrix image_matrix(const ImageAsset::FrameData& frame_data, const SkISize& dest_size) {
 *     if (!frame_data.image) {
 *         return SkMatrix::I();
 *     }
 *
 *     const auto size_fit_matrix = frame_data.scaling == ImageAsset::SizeFit::kNone
 *             ? SkMatrix::I()
 *             : SkMatrix::RectToRectOrIdentity(SkRect::Make(frame_data.image->bounds()),
 *                                              SkRect::Make(dest_size),
 *                                              static_cast<SkMatrix::ScaleToFit>(frame_data.scaling));
 *
 *     return frame_data.matrix * size_fit_matrix;
 * }
 * ```
 */
public fun imageMatrix(frameData: ImageAsset.FrameData, destSize: SkISize): SkMatrix {
  TODO("Implement imageMatrix")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename TMap>
 * const char* parse_map(const TMap& map, const char* str, T* result) {
 *     // ignore leading whitespace
 *     while (*str == ' ') ++str;
 *
 *     const char* next_tok = strchr(str, ' ');
 *
 *     if (const auto len = next_tok ? (next_tok - str) : strlen(str)) {
 *         for (const auto& e : map) {
 *             const char* key = std::get<0>(e);
 *             if (!strncmp(str, key, len) && key[len] == '\0') {
 *                 *result = std::get<1>(e);
 *                 return str + len;
 *             }
 *         }
 *     }
 *
 *     return str;
 * }
 * ```
 */
public fun <T, TMap> parseMap(
  map: TMap,
  str: String?,
  result: T,
): Char {
  TODO("Implement parseMap")
}

/**
 * C++ original:
 * ```cpp
 * SkFontStyle FontStyle(const AnimationBuilder* abuilder, const char* style) {
 *     static constexpr std::tuple<const char*, SkFontStyle::Weight> gWeightMap[] = {
 *         { "regular"   , SkFontStyle::kNormal_Weight     },
 *         { "medium"    , SkFontStyle::kMedium_Weight     },
 *         { "bold"      , SkFontStyle::kBold_Weight       },
 *         { "light"     , SkFontStyle::kLight_Weight      },
 *         { "black"     , SkFontStyle::kBlack_Weight      },
 *         { "thin"      , SkFontStyle::kThin_Weight       },
 *         { "extra"     , SkFontStyle::kExtraBold_Weight  },
 *         { "extrabold" , SkFontStyle::kExtraBold_Weight  },
 *         { "extralight", SkFontStyle::kExtraLight_Weight },
 *         { "extrablack", SkFontStyle::kExtraBlack_Weight },
 *         { "semibold"  , SkFontStyle::kSemiBold_Weight   },
 *         { "hairline"  , SkFontStyle::kThin_Weight       },
 *         { "normal"    , SkFontStyle::kNormal_Weight     },
 *         { "plain"     , SkFontStyle::kNormal_Weight     },
 *         { "standard"  , SkFontStyle::kNormal_Weight     },
 *         { "roman"     , SkFontStyle::kNormal_Weight     },
 *         { "heavy"     , SkFontStyle::kBlack_Weight      },
 *         { "demi"      , SkFontStyle::kSemiBold_Weight   },
 *         { "demibold"  , SkFontStyle::kSemiBold_Weight   },
 *         { "ultra"     , SkFontStyle::kExtraBold_Weight  },
 *         { "ultrabold" , SkFontStyle::kExtraBold_Weight  },
 *         { "ultrablack", SkFontStyle::kExtraBlack_Weight },
 *         { "ultraheavy", SkFontStyle::kExtraBlack_Weight },
 *         { "ultralight", SkFontStyle::kExtraLight_Weight },
 *     };
 *     static constexpr std::tuple<const char*, SkFontStyle::Slant> gSlantMap[] = {
 *         { "italic" , SkFontStyle::kItalic_Slant  },
 *         { "oblique", SkFontStyle::kOblique_Slant },
 *     };
 *
 *     auto weight = SkFontStyle::kNormal_Weight;
 *     auto slant  = SkFontStyle::kUpright_Slant;
 *
 *     // style is case insensitive.
 *     SkAutoAsciiToLC lc_style(style);
 *     style = lc_style.lc();
 *     style = parse_map(gWeightMap, style, &weight);
 *     style = parse_map(gSlantMap , style, &slant );
 *
 *     // ignore trailing whitespace
 *     while (*style == ' ') ++style;
 *
 *     if (*style) {
 *         abuilder->log(Logger::Level::kWarning, nullptr, "Unknown font style: %s.", style);
 *     }
 *
 *     return SkFontStyle(weight, SkFontStyle::kNormal_Width, slant);
 * }
 * ```
 */
public fun fontStyle(abuilder: AnimationBuilder?, style: String?): SkFontStyle {
  TODO("Implement fontStyle")
}

/**
 * C++ original:
 * ```cpp
 * const ShapeInfo* FindShapeInfo(const skjson::ObjectValue& jshape) {
 *     static constexpr ShapeInfo gShapeInfo[] = {
 *         { "el", ShapeType::kGeometry      , 2, kNone          }, // ellipse
 *         { "fl", ShapeType::kPaint         , 0, kNone          }, // fill
 *         { "gf", ShapeType::kPaint         , 2, kNone          }, // gfill
 *         { "gr", ShapeType::kGroup         , 0, kNone          }, // group
 *         { "gs", ShapeType::kPaint         , 3, kNone          }, // gstroke
 *         { "mm", ShapeType::kGeometryEffect, 0, kSuppressDraws }, // merge
 *         { "op", ShapeType::kGeometryEffect, 3, kNone          }, // offset
 *         { "pb", ShapeType::kGeometryEffect, 4, kNone          }, // pucker/bloat
 *         { "rc", ShapeType::kGeometry      , 1, kNone          }, // rrect
 *         { "rd", ShapeType::kGeometryEffect, 2, kNone          }, // round
 *         { "rp", ShapeType::kDrawEffect    , 0, kNone          }, // repeater
 *         { "sh", ShapeType::kGeometry      , 0, kNone          }, // shape
 *         { "sr", ShapeType::kGeometry      , 3, kNone          }, // polystar
 *         { "st", ShapeType::kPaint         , 1, kNone          }, // stroke
 *         { "tm", ShapeType::kGeometryEffect, 1, kNone          }, // trim
 *         { "tr", ShapeType::kTransform     , 0, kNone          }, // transform
 *     };
 *
 *     const skjson::StringValue* type = jshape["ty"];
 *     if (!type) {
 *         return nullptr;
 *     }
 *
 *     const auto* info = bsearch(type->begin(),
 *                                gShapeInfo,
 *                                std::size(gShapeInfo),
 *                                sizeof(ShapeInfo),
 *                                [](const void* key, const void* info) {
 *                                   return strcmp(static_cast<const char*>(key),
 *                                                 static_cast<const ShapeInfo*>(info)->fTypeString);
 *                                });
 *
 *     return static_cast<const ShapeInfo*>(info);
 * }
 * ```
 */
public fun findShapeInfo(jshape: ObjectValue): ShapeInfo {
  TODO("Implement findShapeInfo")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<sksg::GeometryNode> AdjustGeometryFillRule(sk_sp<sksg::GeometryNode> geo,
 *                                                  const skjson::ObjectValue& jpaint) {
 *     static constexpr SkPathFillType gFillTypes[] = {
 *         SkPathFillType::kWinding,  // "r": 1
 *         SkPathFillType::kEvenOdd,  // "r": 2
 *     };
 *     const SkPathFillType ft = gFillTypes[std::min(ParseDefault<size_t>(jpaint["r"], 1) - 1,
 *                                                   std::size(gFillTypes) - 1)];
 *     return ft == SkPathFillType::kDefault
 *         ? geo
 *         : sksg::FillTypeOverride::Make(std::move(geo), ft);
 * }
 * ```
 */
public fun adjustGeometryFillRule(geo: SkSp<GeometryNode>, jpaint: ObjectValue): SkSp<GeometryNode> {
  TODO("Implement adjustGeometryFillRule")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename TArray>
 * T ParseEnum(const TArray& arr, const skjson::Value& jenum,
 *             const AnimationBuilder* abuilder, const char* warn_name) {
 *
 *     const auto idx = ParseDefault<int>(jenum, 1);
 *
 *     if (idx > 0 && SkToSizeT(idx) <= std::size(arr)) {
 *         return arr[idx - 1];
 *     }
 *
 *     // For animators without selectors, BM emits placeholder selector entries with 0 (inval) props.
 *     // Supress warnings for these as they are "normal".
 *     if (idx != 0) {
 *         abuilder->log(Logger::Level::kWarning, nullptr,
 *                       "Ignoring unknown range selector %s '%d'", warn_name, idx);
 *     }
 *
 *     SkASSERT(std::size(arr) > 0);
 *     return arr[0];
 * }
 * ```
 */
public fun <T, TArray> parseEnum(
  arr: TArray,
  jenum: Value,
  abuilder: AnimationBuilder?,
  warnName: String?,
): T {
  TODO("Implement parseEnum")
}

/**
 * C++ original:
 * ```cpp
 * SkVector EaseVec(float ease) {
 *     return (ease < 0) ? SkVector{0, -ease} : SkVector{ease, 0};
 * }
 * ```
 */
public fun easeVec(ease: Float): SkVector {
  TODO("Implement easeVec")
}

/**
 * C++ original:
 * ```cpp
 * static float align_factor(SkTextUtils::Align a) {
 *     switch (a) {
 *         case SkTextUtils::kLeft_Align  : return 0.0f;
 *         case SkTextUtils::kCenter_Align: return 0.5f;
 *         case SkTextUtils::kRight_Align : return 1.0f;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun alignFactor(a: SkTextUtils.Align): Float {
  TODO("Implement alignFactor")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_whitespace(char c) {
 *     // TODO: we've been getting away with this simple heuristic,
 *     // but ideally we should use SkUicode::isWhiteSpace().
 *     return c == ' ' || c == '\t' || c == '\r' || c == '\n';
 * }
 * ```
 */
public fun isWhitespace(c: Char): Boolean {
  TODO("Implement isWhitespace")
}

/**
 * C++ original:
 * ```cpp
 * Shaper::Result ShapeImpl(const SkString& txt, const Shaper::TextDesc& desc,
 *                          const SkRect& box, const sk_sp<SkFontMgr>& fontmgr,
 *                          const sk_sp<SkShapers::Factory>& shapingFactory,
 *                          SkSize* shaped_size) {
 *     const auto& is_line_break = [](SkUnichar uch) {
 *         // TODO: other explicit breaks?
 *         return uch == '\r';
 *     };
 *
 *     const char* ptr        = txt.c_str();
 *     const char* line_start = ptr;
 *     const char* begin      = ptr;
 *     const char* end        = ptr + txt.size();
 *
 *     ResultBuilder rbuilder(desc, box, fontmgr, shapingFactory);
 *     while (ptr < end) {
 *         if (is_line_break(SkUTF::NextUTF8(&ptr, end))) {
 *             rbuilder.shapeLine(line_start, ptr - 1, SkToSizeT(line_start - begin));
 *             line_start = ptr;
 *         }
 *     }
 *     rbuilder.shapeLine(line_start, ptr, SkToSizeT(line_start - begin));
 *
 *     return rbuilder.finalize(shaped_size);
 * }
 * ```
 */
public fun shapeImpl(
  txt: String,
  desc: Shaper.TextDesc,
  box: SkRect,
  fontmgr: SkSp<SkFontMgr>,
  shapingFactory: SkSp<SkShapers.Factory>,
  shapedSize: SkSize?,
): Shaper.Result {
  TODO("Implement shapeImpl")
}

/**
 * C++ original:
 * ```cpp
 * bool result_fits(const Shaper::Result& res, const SkSize& res_size,
 *                  const SkRect& box, const Shaper::TextDesc& desc) {
 *     // optional max line count constraint
 *     if (desc.fMaxLines) {
 *         const auto line_count = res.fFragments.empty()
 *                 ? 0
 *                 : res.fFragments.back().fLineIndex + 1;
 *         if (line_count > desc.fMaxLines) {
 *             return false;
 *         }
 *     }
 *
 *     // geometric constraint
 *     return res_size.width() <= box.width() && res_size.height() <= box.height();
 * }
 * ```
 */
public fun resultFits(
  res: Shaper.Result,
  resSize: SkSize,
  box: SkRect,
  desc: Shaper.TextDesc,
): Boolean {
  TODO("Implement resultFits")
}

/**
 * C++ original:
 * ```cpp
 * Shaper::Result ShapeToFit(const SkString& txt, const Shaper::TextDesc& orig_desc,
 *                           const SkRect& box, const sk_sp<SkFontMgr>& fontmgr,
 *                           const sk_sp<SkShapers::Factory>& shapingFactory) {
 *     Shaper::Result best_result;
 *
 *     if (box.isEmpty() || orig_desc.fTextSize <= 0) {
 *         return best_result;
 *     }
 *
 *     auto desc = orig_desc;
 *
 *     const auto min_scale = std::max(desc.fMinTextSize / desc.fTextSize, 0.0f),
 *                max_scale = std::max(desc.fMaxTextSize / desc.fTextSize, min_scale);
 *
 *     float in_scale = min_scale,                          // maximum scale that fits inside
 *          out_scale = max_scale,                          // minimum scale that doesn't fit
 *          try_scale = SkTPin(1.0f, min_scale, max_scale); // current probe
 *
 *     // Perform a binary search for the best vertical fit (SkShaper already handles
 *     // horizontal fitting), starting with the specified text size.
 *     //
 *     // This hybrid loop handles both the binary search (when in/out extremes are known), and an
 *     // exponential search for the extremes.
 *     static constexpr size_t kMaxIter = 16;
 *     for (size_t i = 0; i < kMaxIter; ++i) {
 *         SkASSERT(try_scale >= in_scale && try_scale <= out_scale);
 *         desc.fTextSize   = try_scale * orig_desc.fTextSize;
 *         desc.fLineHeight = try_scale * orig_desc.fLineHeight;
 *         desc.fLineShift  = try_scale * orig_desc.fLineShift;
 *         desc.fAscent     = try_scale * orig_desc.fAscent;
 *
 *         SkSize res_size = {0, 0};
 *         auto res = ShapeImpl(txt, desc, box, fontmgr, shapingFactory, &res_size);
 *
 *         const auto prev_scale = try_scale;
 *         if (!result_fits(res, res_size, box, desc)) {
 *             out_scale = try_scale;
 *             try_scale = (in_scale == min_scale)
 *                     // initial in_scale not found yet - search exponentially
 *                     ? std::max(min_scale, try_scale * 0.5f)
 *                     // in_scale found - binary search
 *                     : (in_scale + out_scale) * 0.5f;
 *         } else {
 *             // It fits - so it's a candidate.
 *             best_result = std::move(res);
 *             best_result.fScale = try_scale;
 *
 *             in_scale = try_scale;
 *             try_scale = (out_scale == max_scale)
 *                     // initial out_scale not found yet - search exponentially
 *                     ? std::min(max_scale, try_scale * 2)
 *                     // out_scale found - binary search
 *                     : (in_scale + out_scale) * 0.5f;
 *         }
 *
 *         if (try_scale == prev_scale) {
 *             // no more progress
 *             break;
 *         }
 *     }
 *
 *     return best_result;
 * }
 * ```
 */
public fun shapeToFit(
  txt: String,
  origDesc: Shaper.TextDesc,
  box: SkRect,
  fontmgr: SkSp<SkFontMgr>,
  shapingFactory: SkSp<SkShapers.Factory>,
): Shaper.Result {
  TODO("Implement shapeToFit")
}

/**
 * C++ original:
 * ```cpp
 * bool Parse(const skjson::Value& jv, const internal::AnimationBuilder& abuilder, TextValue* v) {
 *     const skjson::ObjectValue* jtxt = jv;
 *     if (!jtxt) {
 *         return false;
 *     }
 *
 *     const skjson::StringValue* font_name   = (*jtxt)["f"];
 *     const skjson::StringValue* text        = (*jtxt)["t"];
 *     const skjson::NumberValue* text_size   = (*jtxt)["s"];
 *     const skjson::NumberValue* line_height = (*jtxt)["lh"];
 *     if (!font_name || !text || !text_size || !line_height) {
 *         return false;
 *     }
 *
 *     const auto* font = abuilder.findFont(SkString(font_name->begin(), font_name->size()));
 *     if (!font) {
 *         abuilder.log(Logger::Level::kError, nullptr, "Unknown font: \"%s\".", font_name->begin());
 *         return false;
 *     }
 *
 *     v->fText.set(text->begin(), text->size());
 *     v->fTextSize   = **text_size;
 *     v->fLineHeight = **line_height;
 *     v->fTypeface   = font->fTypeface;
 *     v->fFontFamily = font->fFamily;
 *     v->fAscent     = font->fAscentPct * -0.01f * v->fTextSize; // negative ascent per SkFontMetrics
 *     v->fLineShift  = ParseDefault((*jtxt)["ls"], 0.0f);
 *
 *     static constexpr SkTextUtils::Align gAlignMap[] = {
 *         SkTextUtils::kLeft_Align,  // 'j': 0
 *         SkTextUtils::kRight_Align, // 'j': 1
 *         SkTextUtils::kCenter_Align // 'j': 2
 *     };
 *     v->fHAlign = gAlignMap[std::min<size_t>(ParseDefault<size_t>((*jtxt)["j"], 0),
 *                                             std::size(gAlignMap) - 1)];
 *
 *     // Optional text box size.
 *     if (const skjson::ArrayValue* jsz = (*jtxt)["sz"]) {
 *         if (jsz->size() == 2) {
 *             v->fBox.setWH(ParseDefault<SkScalar>((*jsz)[0], 0),
 *                           ParseDefault<SkScalar>((*jsz)[1], 0));
 *         }
 *     }
 *
 *     // Optional text box position.
 *     if (const skjson::ArrayValue* jps = (*jtxt)["ps"]) {
 *         if (jps->size() == 2) {
 *             v->fBox.offset(ParseDefault<SkScalar>((*jps)[0], 0),
 *                            ParseDefault<SkScalar>((*jps)[1], 0));
 *         }
 *     }
 *
 *     static constexpr Shaper::Direction gDirectionMap[] = {
 *         Shaper::Direction::kLTR,  // 'd': 0
 *         Shaper::Direction::kRTL,  // 'd': 1
 *     };
 *     v->fDirection = gDirectionMap[std::min(ParseDefault<size_t>((*jtxt)["d"], 0),
 *                                            std::size(gDirectionMap) - 1)];
 *
 *     static constexpr Shaper::ResizePolicy gResizeMap[] = {
 *         Shaper::ResizePolicy::kNone,           // 'rs': 0
 *         Shaper::ResizePolicy::kScaleToFit,     // 'rs': 1
 *         Shaper::ResizePolicy::kDownscaleToFit, // 'rs': 2
 *     };
 *     // TODO: remove "sk_rs" support after migrating clients.
 *     v->fResize = gResizeMap[std::min(std::max(ParseDefault<size_t>((*jtxt)[   "rs"], 0),
 *                                               ParseDefault<size_t>((*jtxt)["sk_rs"], 0)),
 *                                      std::size(gResizeMap) - 1)];
 *
 *     // Optional min/max font size and line count (used when aute-resizing)
 *     v->fMinTextSize = ParseDefault<SkScalar>((*jtxt)["mf"], 0.0f);
 *     v->fMaxTextSize = ParseDefault<SkScalar>((*jtxt)["xf"], std::numeric_limits<float>::max());
 *     v->fMaxLines    = ParseDefault<size_t>  ((*jtxt)["xl"], 0);
 *
 *     // At the moment, BM uses the paragraph box to discriminate point mode vs. paragraph mode.
 *     v->fLineBreak = v->fBox.isEmpty()
 *             ? Shaper::LinebreakPolicy::kExplicit
 *             : Shaper::LinebreakPolicy::kParagraph;
 *
 *     // Optional explicit text mode.
 *     // N.b.: this is not being exported by BM, only used for testing.
 *     auto text_mode = ParseDefault((*jtxt)["m"], -1);
 *     if (text_mode >= 0) {
 *         // Explicit text mode.
 *         v->fLineBreak = (text_mode == 0)
 *                 ? Shaper::LinebreakPolicy::kExplicit   // 'm': 0 -> point text
 *                 : Shaper::LinebreakPolicy::kParagraph; // 'm': 1 -> paragraph text
 *     }
 *
 *     // Optional capitalization.
 *     static constexpr Shaper::Capitalization gCapMap[] = {
 *         Shaper::Capitalization::kNone,      // 'ca': 0
 *         Shaper::Capitalization::kUpperCase, // 'ca': 1
 *     };
 *     v->fCapitalization = gCapMap[std::min<size_t>(ParseDefault<size_t>((*jtxt)["ca"], 0),
 *                                                   std::size(gCapMap) - 1)];
 *
 *     // In point mode, the text is baseline-aligned.
 *     v->fVAlign = v->fBox.isEmpty() ? Shaper::VAlign::kTopBaseline
 *                                    : Shaper::VAlign::kTop;
 *
 *     static constexpr Shaper::VAlign gVAlignMap[] = {
 *         Shaper::VAlign::kHybridTop,    // 'vj': 0
 *         Shaper::VAlign::kHybridCenter, // 'vj': 1
 *         Shaper::VAlign::kHybridBottom, // 'vj': 2
 *         Shaper::VAlign::kVisualTop,    // 'vj': 3
 *         Shaper::VAlign::kVisualCenter, // 'vj': 4
 *         Shaper::VAlign::kVisualBottom, // 'vj': 5
 *     };
 *     size_t vj;
 *     if (skottie::Parse((*jtxt)["vj"], &vj)) {
 *         if (vj < std::size(gVAlignMap)) {
 *             v->fVAlign = gVAlignMap[vj];
 *         } else {
 *             abuilder.log(Logger::Level::kWarning, nullptr, "Ignoring unknown 'vj' value: %zu", vj);
 *         }
 *     } else if (skottie::Parse((*jtxt)["sk_vj"], &vj)) {
 *         // Legacy sk_vj values.
 *         // TODO: remove after clients update.
 *         switch (vj) {
 *         case 0:
 *         case 1:
 *         case 2:
 *             static_assert(std::size(gVAlignMap) > 2);
 *             v->fVAlign = gVAlignMap[vj];
 *             break;
 *         case 3:
 *             // 'sk_vj': 3 -> kHybridCenter/kScaleToFit
 *             v->fVAlign = Shaper::VAlign::kHybridCenter;
 *             v->fResize = Shaper::ResizePolicy::kScaleToFit;
 *             break;
 *         case 4:
 *             // 'sk_vj': 4 -> kHybridCenter/kDownscaleToFit
 *             v->fVAlign = Shaper::VAlign::kHybridCenter;
 *             v->fResize = Shaper::ResizePolicy::kDownscaleToFit;
 *             break;
 *         default:
 *             abuilder.log(Logger::Level::kWarning, nullptr,
 *                          "Ignoring unknown 'sk_vj' value: %zu", vj);
 *             break;
 *         }
 *     }
 *
 *     const auto& parse_color = [] (const skjson::ArrayValue* jcolor,
 *                                   SkColor* c) {
 *         if (!jcolor) {
 *             return false;
 *         }
 *
 *         ColorValue color_vec;
 *         if (!skottie::Parse(*jcolor, static_cast<VectorValue*>(&color_vec))) {
 *             return false;
 *         }
 *
 *         *c = color_vec;
 *         return true;
 *     };
 *
 *     v->fHasFill   = parse_color((*jtxt)["fc"], &v->fFillColor);
 *     v->fHasStroke = parse_color((*jtxt)["sc"], &v->fStrokeColor);
 *
 *     if (v->fHasStroke) {
 *         v->fStrokeWidth = ParseDefault((*jtxt)["sw"], 1.0f);
 *         v->fPaintOrder  = ParseDefault((*jtxt)["of"], true)
 *                 ? TextPaintOrder::kFillStroke
 *                 : TextPaintOrder::kStrokeFill;
 *
 *         static constexpr SkPaint::Join gJoins[] = {
 *             SkPaint::kMiter_Join,  // lj: 1
 *             SkPaint::kRound_Join,  // lj: 2
 *             SkPaint::kBevel_Join,  // lj: 3
 *         };
 *         v->fStrokeJoin = gJoins[std::min<size_t>(ParseDefault<size_t>((*jtxt)["lj"], 1) - 1,
 *                                                  std::size(gJoins) - 1)];
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun parse(
  jv: Value,
  abuilder: `internal`.AnimationBuilder,
  v: TextValue?,
): Boolean {
  TODO("Implement parse")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkUnicode> SK_API MakeStrictLinebreakUnicode(sk_sp<SkUnicode> uc) {
 *     return uc
 *         ? sk_make_sp<StrictLinebreakUnicode>(std::move(uc))
 *         : nullptr;
 * }
 * ```
 */
public fun makeStrictLinebreakUnicode(uc: SkSp<SkUnicode>): SkSp<SkUnicode> {
  TODO("Implement makeStrictLinebreakUnicode")
}

/**
 * C++ original:
 * ```cpp
 * static SkIRect extend_rect(SkIRect r, Point p) {
 *     int32_t left   = std::min(p.x, r.fLeft),
 *             top    = std::min(p.y, r.fTop),
 *             right  = std::max(p.x, r.fRight),
 *             bottom = std::max(p.y, r.fBottom);
 *     return {left, top, right, bottom};
 * }
 * ```
 */
public fun extendRect(r: SkIRect, p: Point): SkIRect {
  TODO("Implement extendRect")
}

/**
 * C++ original:
 * ```cpp
 * Int96 multiply(int32_t a, int64_t b) {
 *     return multiply(b, a);
 * }
 * ```
 */
public fun multiply(a: Int, b: Long): Int96 {
  TODO("Implement multiply")
}

/**
 * C++ original:
 * ```cpp
 * std::tuple<int64_t, int64_t> point_to_s64(Point p) {
 *     return std::make_tuple(SkToS64(p.x), SkToS64(p.y));
 * }
 * ```
 */
public fun pointToS64(p: Point): Pair<Long, Long> {
  TODO("Implement pointToS64")
}

/**
 * C++ original:
 * ```cpp
 * bool slope_s0_less_than_slope_s1(const Segment& s0, const Segment& s1) {
 *     return compare_slopes(s0, s1) < 0;
 * }
 * ```
 */
public fun slopeS0LessThanSlopeS1(s0: Segment, s1: Segment): Boolean {
  TODO("Implement slopeS0LessThanSlopeS1")
}

/**
 * C++ original:
 * ```cpp
 * int64_t compare_point_to_segment(Point p, const Segment& s) {
 *     const auto [u, l] = s;
 *
 *     // The segment must span p vertically.
 *     SkASSERT(u.y <= p.y && p.y <= l.y);
 *
 *     // Check horizontal extents.
 *     {
 *         const auto [left, right] = std::minmax(u.x, l.x);
 *         if (p.x < left) {
 *             return -1;
 *         }
 *
 *         if (right < p.x) {
 *             return 1;
 *         }
 *     }
 *
 *     // If s is horizontal, then p is on the interval [u.x, l.x].
 *     if (s.isHorizontal()) {
 *         return 0;
 *     }
 *
 *     // The point p < s when:
 *     //     p.x < u.x + (l.x - u.x)(p.y - u.y) / (l.y - u.y),
 *     //     p.x - u.x < (l.x - u.x)(p.y - u.y) / (l.y - u.y),
 *     //     (p.x - u.x)(l.y - u.y) < (l.x - u.x)(p.y - u.y),
 *     //     (p.x - u.x)(l.y - u.y) - (l.x - u.x)(p.y - u.y) < 0,
 *     //     (p - u) x (l - u) < 0,
 *     //     dUtoP x dS < 0.
 *     // The other relations can be implemented in a similar way.
 *     const Point dUToP = p - u;
 *     const Point dS = l - u;
 *
 *     SkASSERT(dS.y > 0);
 *     return cross(dUToP, dS);
 * }
 * ```
 */
public fun comparePointToSegment(p: Point, s: Segment): Long {
  TODO("Implement comparePointToSegment")
}

/**
 * C++ original:
 * ```cpp
 * bool segment_less_than_upper_to_insert(const Segment& segment, const Segment& to_insert) {
 *     const int64_t compare = compare_point_to_segment(to_insert.upper(), segment);
 *
 *     // compare > 0 when segment < to_insert.upper().
 *     return (compare > 0) || ((compare == 0) && slope_s0_less_than_slope_s1(segment, to_insert));
 * }
 * ```
 */
public fun segmentLessThanUpperToInsert(segment: Segment, toInsert: Segment): Boolean {
  TODO("Implement segmentLessThanUpperToInsert")
}

/**
 * C++ original:
 * ```cpp
 * bool s0_less_than_s1_at_y(const Segment& s0, const Segment& s1, int32_t y) {
 *     // Neither s0 nor s1 are horizontal because this is used during the sorting phase
 *     SkASSERT(!s0.isHorizontal() && !s1.isHorizontal());
 *
 *     const auto [u0, l0] = s0;
 *     const auto [u1, l1] = s1;
 *
 *     const auto [left0, right0] = std::minmax(u0.x, l0.x);
 *     const auto [left1, right1] = std::minmax(u1.x, l1.x);
 *
 *     if (right0 < left1) {
 *         return true;
 *     } else if (right1 < left0) {
 *         return false;
 *     }
 *
 *     const Point d0 = l0 - u0;
 *     const Point d1 = l1 - u1;
 *
 *     // Since horizontal lines are handled separately and the ordering of points for the segment,
 *     // then there should always be positive Dy.
 *     SkASSERT(d0.y > 0 && d1.y > 0);
 *
 *     namespace bo = bentleyottmann;
 *     using Int96 = bo::Int96;
 *
 *     // Defining s0(y) and s1(y),
 *     //    s0(y) = u0.x + (y - u0.y) * d0.x / d0.y
 *     //    s1(y) = u1.x + (y - u1.y) * d1.x / d1.y
 *     // Find the following
 *     //    s0(y) < s1(y)
 *     // Substituting s0(y) and s1(y)
 *     //    u0.x + (y - u0.y) * d0.x / d0.y < u1.x + (y - u1.y) * d1.x / d1.y
 *     // Factoring out the denominator.
 *     //    (u0.x * d0.y + (y - u0.y) * d0.x) / d0.y < (u1.x * d1.y + (y - u1.y) * d1.x) / d1.y
 *     // Cross-multiplying the denominators. The sign will not switch because d0.y and d1.y are
 *     // always positive.
 *     //    d1.y * (u0.x * d0.y + (y - u0.y) * d0.x) < d0.y * (u1.x * d1.y + (y - u1.y) * d1.x)
 *     // If these are equal, then we use the slope to break the tie.
 *     //    d0.x / d0.y < d1.x / d1.y
 *     // Cross multiplying leaves.
 *     //    d0.x * d1.y < d1.x * d0.y
 *     const Int96 lhs = bo::multiply(d1.y, u0.x * SkToS64(d0.y) + (y - u0.y) * SkToS64(d0.x));
 *     const Int96 rhs = bo::multiply(d0.y, u1.x * SkToS64(d1.y) + (y - u1.y) * SkToS64(d1.x));
 *
 *     return lhs < rhs || ((lhs == rhs) && slope_s0_less_than_slope_s1(s0, s1));
 * }
 * ```
 */
public fun s0LessThanS1AtY(
  s0: Segment,
  s1: Segment,
  y: Int,
): Boolean {
  TODO("Implement s0LessThanS1AtY")
}

/**
 * C++ original:
 * ```cpp
 * std::vector<Crossing> myers_find_crossings(const SkSpan<const Segment> segments) {
 *     const EventQueue eventQueue = EventQueue::Make(segments);
 *     SweepLine sweepLine;
 *
 *     for (const Event& event : eventQueue) {
 *         sweepLine.handleEvent(event);
 *     }
 *
 *     return sweepLine.finishAndReleaseCrossings();
 * }
 * ```
 */
public fun myersFindCrossings(segments: SkSpan<Segment>): List<Crossing> {
  TODO("Implement myersFindCrossings")
}

/**
 * C++ original:
 * ```cpp
 * bool s0_intersects_s1(const Segment& s0, const Segment& s1) {
 *     // Make sure that s0 upper is above s1 upper.
 *     if (s1.upper().y < s0.upper().y
 *         || ((s1.upper().y == s0.upper().y) && (s1.lower().y > s0.lower().y))) {
 *
 *         // Swap to put in the right orientation.
 *         return s0_intersects_s1(s1, s0);
 *     }
 *
 *     SkASSERT(s0.upper().y <= s1.upper().y);
 *
 *     {  // If extents don't overlap then there is no intersection.
 *         auto [left0, top0, right0, bottom0] = s0.bounds();
 *         auto [left1, top1, right1, bottom1] = s1.bounds();
 *         if (right1 < left0 || right0 < left1 || bottom1 < top0 || bottom0 < top1) {
 *             return false;
 *         }
 *     }
 *
 *     auto [u0, l0] = s0;
 *     auto [u1, l1] = s1;
 *
 *     const Point D0 = l0 - u0,
 *                 D1 = l1 - u1;
 *
 *     // If the vector from u0 to l0 (named D0) and the vector from u0 to u1 have an angle of 0
 *     // between them, then u1 is on the segment u0 to l0 (named s0).
 *     const Point U0toU1 = (u1 - u0);
 *     const int64_t D0xU0toU1 = cross(D0, U0toU1);
 *     if (D0xU0toU1 == 0) {
 *         // u1 is on s0.
 *         return true;
 *     }
 *
 *     if (l1.y <= l0.y) {
 *         // S1 is between the upper and lower points of S0.
 *         const Point U0toL1 = (l1 - u0);
 *         const int64_t D0xU0toL1 = cross(D0, U0toL1);
 *         if (D0xU0toL1 == 0) {
 *             // l1 is on s0.
 *             return true;
 *         }
 *
 *         // If U1 and L1 are on opposite sides of D0 then the segments cross.
 *         return (D0xU0toU1 ^ D0xU0toL1) < 0;
 *     } else {
 *         // S1 extends past S0. It could be that S1 crosses the line of S0 (not the bound segment)
 *         // beyond the endpoints of S0. Make sure that it crosses on the segment and not beyond.
 *         const Point U1toL0 = (l0 - u1);
 *         const int64_t D1xU1toL0 = cross(D1, U1toL0);
 *         if (D1xU1toL0 == 0) {
 *             return true;
 *         }
 *
 *         // For D1 to cross D0, then D1 must be on the same side of U1toL0 as D0. D0xU0toU1
 *         // describes the orientation of U0 compared to D0. The angle from D1 to U1toL0 must
 *         // have the same direction as the angle from U0toU1 to D0.
 *         return (D0xU0toU1 ^ D1xU1toL0) >= 0;
 *     }
 * }
 * ```
 */
public fun s0IntersectsS1(s0: Segment, s1: Segment): Boolean {
  TODO("Implement s0IntersectsS1")
}

/**
 * C++ original:
 * ```cpp
 * std::vector<Crossing> brute_force_crossings(SkSpan<Segment> segments) {
 *
 *     auto isNonZeroSegment = [](const Segment& segment) {
 *         return segment.upper() != segment.lower();
 *     };
 *     const auto zeroSegments = std::partition(segments.begin(), segments.end(), isNonZeroSegment);
 *
 *     std::sort(segments.begin(), zeroSegments);
 *
 *     const auto duplicateSegments = std::unique(segments.begin(), zeroSegments);
 *
 *     SkSpan<const Segment> cleanSegments =
 *             SkSpan{segments.data(), SkToSizeT(std::distance(segments.begin(), duplicateSegments))};
 *
 *     CrossingAccumulator crossings;
 *     if (cleanSegments.size() >= 2) {
 *         for (auto i = cleanSegments.begin(); i != std::prev(cleanSegments.end()); ++i) {
 *             for (auto j = std::next(i); j != cleanSegments.end(); ++j) {
 *                 if (s0_intersects_s1(*i, *j)) {
 *                     crossings.recordCrossing(*i, *j);
 *                 }
 *             }
 *         }
 *     }
 *     return crossings.finishAndReleaseCrossings();
 * }
 * ```
 */
public fun bruteForceCrossings(segments: SkSpan<Segment>): List<Crossing> {
  TODO("Implement bruteForceCrossings")
}

/**
 * C++ original:
 * ```cpp
 * bool no_intersection_by_bounding_box(const Segment& s0, const Segment& s1) {
 *     auto [left0, top0, right0, bottom0] = s0.bounds();
 *     auto [left1, top1, right1, bottom1] = s1.bounds();
 *     // If the sides of the box touch, then there is no new intersection.
 *     return right0 <= left1 || right1 <= left0 || bottom0 <= top1 || bottom1 <= top0;
 * }
 * ```
 */
public fun noIntersectionByBoundingBox(s0: Segment, s1: Segment): Boolean {
  TODO("Implement noIntersectionByBoundingBox")
}

/**
 * C++ original:
 * ```cpp
 * std::optional<Point> intersect(const Segment& s0, const Segment& s1) {
 *
 *     // Check if the bounds intersect.
 *     if (no_intersection_by_bounding_box(s0, s1)) {
 *         return std::nullopt;
 *     }
 *
 *     // Create the end Points for s0 and s1
 *     const Point P0 = s0.upper(),
 *                 P1 = s0.lower(),
 *                 P2 = s1.upper(),
 *                 P3 = s1.lower();
 *
 *     if (P0 == P2 || P1 == P3 || P1 == P2 || P3 == P0) {
 *         // Lines don't intersect if they share an end point.
 *         return std::nullopt;
 *     }
 *
 *     // Create the Q, R, and T.
 *     const Point Q = P1 - P0,
 *                 R = P2 - P0,
 *                 T = P3 - P2;
 *
 *     // 64-bit cross product.
 *     auto cross = [](const Point& v0, const Point& v1) {
 *         int64_t x0 = SkToS64(v0.x),
 *                 y0 = SkToS64(v0.y),
 *                 x1 = SkToS64(v1.x),
 *                 y1 = SkToS64(v1.y);
 *         return x0 * y1 - y0 * x1;
 *     };
 *
 *     // Calculate the cross products needed for calculating s and t.
 *     const int64_t QxR = cross(Q, R),
 *                   TxR = cross(T, R),
 *                   TxQ = cross(T, Q);
 *
 *     if (TxQ == 0) {
 *         // Both t and s are either < 0 or > 1 because the denominator is 0.
 *         return std::nullopt;
 *     }
 *
 *     // t = (Q x R) / (T x Q). s = (T x R) / (T x Q). Check that t & s are on [0, 1]
 *     if ((QxR ^ TxQ) < 0 || (TxR ^ TxQ) < 0) {
 *         // The division is negative and t or s < 0.
 *         return std::nullopt;
 *     }
 *
 *     if (TxQ > 0) {
 *         if (QxR > TxQ || TxR > TxQ) {
 *             // t or s is greater than 1.
 *             return std::nullopt;
 *         }
 *     } else {
 *         if (QxR < TxQ || TxR < TxQ) {
 *             // t or s is greater than 1.
 *             return std::nullopt;
 *         }
 *     }
 *
 *     // Calculate the intersection using doubles.
 *     // TODO: This is just a placeholder approximation for calculating x and y should use big math
 *     // above.
 *     const double t = static_cast<double>(QxR) / static_cast<double>(TxQ);
 *     SkASSERT(0 <= t && t <= 1);
 *     const int32_t x = std::round(t * (P3.x - P2.x) + P2.x),
 *                   y = std::round(t * (P3.y - P2.y) + P2.y);
 *
 *     return Point{x, y};
 * }
 * ```
 */
public fun intersect(s0: Segment, s1: Segment): Point? {
  TODO("Implement intersect")
}

/**
 * C++ original:
 * ```cpp
 * bool less_than_at(const Segment& s0, const Segment& s1, int32_t y) {
 *     auto [l0, t0, r0, b0] = s0.bounds();
 *     auto [l1, t1, r1, b1] = s1.bounds();
 *     SkASSERT(t0 <= y && y <= b0);
 *     SkASSERT(t1 <= y && y <= b1);
 *
 *     // Return true if the bounding box of s0 is fully to the left of s1.
 *     if (r0 < l1) {
 *         return true;
 *     }
 *
 *     // Return false if the bounding box of s0 is fully to the right of s1.
 *     if (r1 < l0) {
 *         return false;
 *     }
 *
 *     // Check the x intercepts along the horizontal line at y.
 *     // Make s0 be (x0, y0) -> (x1, y1) and s1 be (x2, y2) -> (x3, y3).
 *     auto [x0, y0] = s0.upper();
 *     auto [x1, y1] = s0.lower();
 *     auto [x2, y2] = s1.upper();
 *     auto [x3, y3] = s1.lower();
 *
 *     int64_t s0YDiff = y - y0,
 *             s1YDiff = y - y2,
 *             s0YDelta = y1 - y0,
 *             s1YDelta = y3 - y2,
 *             x0Offset = x0 * s0YDelta + s0YDiff * (x1 - x0),
 *             x2Offset = x2 * s1YDelta + s1YDiff * (x3 - x2);
 *
 *     Int96 s0Factor = multiply(x0Offset, y3 - y2),
 *           s1Factor = multiply(x2Offset, y1 - y0);
 *
 *     return s0Factor < s1Factor;
 * }
 * ```
 */
public fun lessThanAt(
  s0: Segment,
  s1: Segment,
  y: Int,
): Boolean {
  TODO("Implement lessThanAt")
}

/**
 * C++ original:
 * ```cpp
 * bool point_less_than_segment_in_x(Point p, const Segment& segment) {
 *     auto [l, t, r, b] = segment.bounds();
 *
 *     // Ensure that the segment intersects the horizontal sweep line
 *     SkASSERT(t <= p.y && p.y <= b);
 *
 *     // Fast answers using bounding boxes.
 *     if (p.x < l) {
 *         return true;
 *     } else if (p.x >= r) {
 *         return false;
 *     }
 *
 *     auto [x0, y0] = segment.upper();
 *     auto [x1, y1] = segment.lower();
 *     auto [x2, y2] = p;
 *
 *     // For a point and a segment the comparison is:
 *     //    x2 < x0 + (y2 - y0)(x1 - x0) / (y1 - y0)
 *     // becomes
 *     //    (x2 - x0)(y1 - y0) < (x1 - x0)(y2 - y0)
 *     // We don't need to worry about the signs changing in the cross multiply because (y1 - y0) is
 *     // always positive. Manipulating a little further derives predicate 2 from "Robust Plane
 *     // Sweep for Intersecting Segments" page 9.
 *     //    0 < (x1 - x0)(y2 - y0) - (x2 - x0)(y1 - y0)
 *     // becomes
 *     //        | x1-x0   x2-x0 |
 *     //   0 <  | y1-y0   y2-y0 |
 *     return SkToS64(x2 - x0) * SkToS64(y1 - y0) < SkToS64(y2 - y0) * SkToS64(x1 - x0);
 * }
 * ```
 */
public fun pointLessThanSegmentInX(p: Point, segment: Segment): Boolean {
  TODO("Implement pointLessThanSegmentInX")
}

/**
 * C++ original:
 * ```cpp
 * bool rounded_point_less_than_segment_in_x_lower(const Segment& s, Point p) {
 *     const auto [l, t, r, b] = s.bounds();
 *     const auto [x, y] = p;
 *
 *     // Ensure that the segment intersects the horizontal sweep line
 *     SkASSERT(t <= y && y <= b);
 *
 *     // In the comparisons below, x is really x - ½
 *     if (r < x) {
 *         // s is entirely < p.
 *         return true;
 *     } else if (x <= l) {
 *         // s is entirely > p. This also handles vertical lines, so we don't have to handle them
 *         // below.
 *         return false;
 *     }
 *
 *     const auto [x0, y0] = s.upper();
 *     const auto [x1, y1] = s.lower();
 *
 *     // Horizontal - from the guards above we know that p is on s.
 *     if (y0 == y1) {
 *         return false;
 *     }
 *
 *     // s is not horizontal or vertical.
 *     SkASSERT(x0 != x1 && y0 != y1);
 *
 *     // Given the segment upper = (x0, y0) and lower = (x1, y1)
 *     // x0 + (x1 - x0)(y - y0) / (y1 - y0) < x - ½
 *     // (x1 - x0)(y - y0) / (y1 - y0) < x - x0 - ½
 *     // Because (y1 - y0) is always positive we can multiply through the inequality without
 *     // worrying about sign changes.
 *     // (x1 - x0)(y - y0) < (x - x0 - ½)(y1 - y0)
 *     // (x1 - x0)(y - y0) < ½(2x - 2x0 - 1)(y1 - y0)
 *     // 2(x1 - x0)(y - y0) < (2(x - x0) - 1)(y1 - y0)
 *     return 2 * SkToS64(x1 - x0) * SkToS64(y - y0) < (2 * SkToS64(x - x0) - 1) * SkToS64(y1 - y0);
 * }
 * ```
 */
public fun roundedPointLessThanSegmentInXLower(s: Segment, p: Point): Boolean {
  TODO("Implement roundedPointLessThanSegmentInXLower")
}

/**
 * C++ original:
 * ```cpp
 * bool rounded_point_less_than_segment_in_x_upper(const Segment& s, Point p) {
 *     const auto [l, t, r, b] = s.bounds();
 *     const auto [x, y] = p;
 *
 *     // Ensure that the segment intersects the horizontal sweep line
 *     SkASSERT(t <= y && y <= b);
 *
 *     // In the comparisons below, x is really x + ½
 *     if (r <= x) {
 *         // s is entirely < p.
 *         return true;
 *     } else if (x < l) {
 *         // s is entirely > p. This also handles vertical lines, so we don't have to handle them
 *         // below.
 *         return false;
 *     }
 *
 *     const auto [x0, y0] = s.upper();
 *     const auto [x1, y1] = s.lower();
 *
 *     // Horizontal - from the guards above we know that p is on s.
 *     if (y0 == y1) {
 *         return false;
 *     }
 *
 *     // s is not horizontal or vertical.
 *     SkASSERT(x0 != x1 && y0 != y1);
 *
 *     // Given the segment upper = (x0, y0) and lower = (x1, y1)
 *     // x0 + (x1 - x0)(y - y0) / (y1 - y0) < x + ½
 *     // (x1 - x0)(y - y0) / (y1 - y0) < x - x0 + ½
 *     // Because (y1 - y0) is always positive we can multiply through the inequality without
 *     // worrying about sign changes.
 *     // (x1 - x0)(y - y0) < (x - x0 + ½)(y1 - y0)
 *     // (x1 - x0)(y - y0) < ½(2x - 2x0 + 1)(y1 - y0)
 *     // 2(x1 - x0)(y - y0) < (2(x - x0) + 1)(y1 - y0)
 *     return 2 * SkToS64(x1 - x0) * SkToS64(y - y0) < (2 * SkToS64(x - x0) + 1) * SkToS64(y1 - y0);
 * }
 * ```
 */
public fun roundedPointLessThanSegmentInXUpper(s: Segment, p: Point): Boolean {
  TODO("Implement roundedPointLessThanSegmentInXUpper")
}

/**
 * C++ original:
 * ```cpp
 * int compare_slopes(const Segment& s0, const Segment& s1) {
 *     Point s0Delta = s0.lower() - s0.upper(),
 *           s1Delta = s1.lower() - s1.upper();
 *
 *     // Handle the horizontal cases to avoid dealing with infinities.
 *     if (s0Delta.y == 0 || s1Delta.y == 0) {
 *         if (s0Delta.y != 0) {
 *             return -1;
 *         } else if (s1Delta.y != 0) {
 *             return 1;
 *         } else {
 *             return 0;
 *         }
 *     }
 *
 *     // Compare s0Delta.x / s0Delta.y ? s1Delta.x / s1Delta.y. I used the alternate slope form for
 *     // two reasons.
 *     // * no change of sign - since the delta ys are always positive, then I don't need to worry
 *     //                       about the change in sign with the cross-multiply.
 *     // * proper slope ordering - the slope monotonically increases from the smallest along the
 *     //                           negative x-axis increasing counterclockwise to the largest along
 *     //                           the positive x-axis.
 *     int64_t lhs = SkToS64(s0Delta.x) * SkToS64(s1Delta.y),
 *             rhs = SkToS64(s1Delta.x) * SkToS64(s0Delta.y);
 *
 *     if (lhs < rhs) {
 *         return -1;
 *     } else if (lhs > rhs) {
 *         return 1;
 *     } else {
 *         return 0;
 *     }
 * }
 * ```
 */
public fun compareSlopes(s0: Segment, s1: Segment): Int {
  TODO("Implement compareSlopes")
}

/**
 * C++ original:
 * ```cpp
 * SkPath make_cursor_path() {
 *     // Normalized values, relative to text/font size.
 *     constexpr float kWidth  = 0.2f,
 *                     kHeight = 0.75f;
 *
 *     SkPathBuilder p;
 *
 *     p.moveTo(0, 0);
 *     p.lineTo(kWidth  , 0);
 *     p.moveTo(kWidth/2, 0);
 *     p.lineTo(kWidth/2, kHeight);
 *     p.moveTo(0       , kHeight);
 *     p.lineTo(kWidth  , kHeight);
 *
 *     return p.detach();
 * }
 * ```
 */
public fun makeCursorPath(): SkPath {
  TODO("Implement makeCursorPath")
}

/**
 * C++ original:
 * ```cpp
 * size_t next_utf8(const SkString& str, size_t index) {
 *     SkASSERT(index < str.size());
 *
 *     const char* utf8_ptr = str.c_str() + index;
 *
 *     if (SkUTF::NextUTF8(&utf8_ptr, str.c_str() + str.size()) < 0){
 *         // Invalid UTF sequence.
 *         return index;
 *     }
 *
 *     return utf8_ptr - str.c_str();
 * }
 * ```
 */
public fun nextUtf8(str: String, index: ULong): ULong {
  TODO("Implement nextUtf8")
}

/**
 * C++ original:
 * ```cpp
 * size_t prev_utf8(const SkString& str, size_t index) {
 *     SkASSERT(index > 0);
 *
 *     // Find the previous utf8 index by probing the preceding 4 offsets.  Utf8 leading bytes are
 *     // always distinct from continuation bytes, so only one of these probes will succeed.
 *     for (unsigned i = 1; i <= SkUTF::kMaxBytesInUTF8Sequence && i <= index; ++i) {
 *         const char* utf8_ptr = str.c_str() + index - i;
 *         if (SkUTF::NextUTF8(&utf8_ptr, str.c_str() + str.size()) >= 0) {
 *             return index - i;
 *         }
 *     }
 *
 *     // Invalid UTF sequence.
 *     return index;
 * }
 * ```
 */
public fun prevUtf8(str: String, index: ULong): ULong {
  TODO("Implement prevUtf8")
}

/**
 * C++ original:
 * ```cpp
 * SkString preshapedFontName(const std::string_view& fontName) {
 *     return SkStringPrintf("%s_preshaped", fontName.data());
 * }
 * ```
 */
public fun preshapedFontName(fontName: String): String {
  TODO("Implement preshapedFontName")
}

/**
 * C++ original:
 * ```cpp
 * Value pathToLottie(const SkPath& path, SkArenaAlloc& alloc) {
 *     // Lottie paths are single-contour vectors of cubic segments, stored as
 *     // (vertex, in_tangent, out_tangent) tuples.
 *     // A usual Skia cubic segment (p0, c0, c1, p1) corresponds to Lottie's
 *     // (vertex[0], out_tan[0], in_tan[1], vertex[1]).
 *     // Tangent control points are stored in separate arrays, using relative coordinates.
 *     struct Contour {
 *         std::vector<SkPoint> verts, in_tan, out_tan;
 *         bool closed = false;
 *
 *         void add(const SkPoint& v, const SkPoint& i, const SkPoint& o) {
 *             verts.push_back(v);
 *             in_tan.push_back(i);
 *             out_tan.push_back(o);
 *         }
 *
 *         size_t size() const {
 *             SkASSERT(verts.size() == in_tan.size());
 *             SkASSERT(verts.size() == out_tan.size());
 *             return verts.size();
 *         }
 *     };
 *
 *     std::vector<Contour> contours(1);
 *
 *     for (const auto [verb, pts, weights] : SkPathPriv::Iterate(path)) {
 *         switch (verb) {
 *             case SkPathVerb::kMove:
 *                 if (!contours.back().verts.empty()) {
 *                     contours.emplace_back();
 *                 }
 *                 contours.back().add(pts[0], {0, 0}, {0, 0});
 *                 break;
 *             case SkPathVerb::kClose:
 *                 SkASSERT(contours.back().size() > 0);
 *                 contours.back().closed = true;
 *                 break;
 *             case SkPathVerb::kLine:
 *                 SkASSERT(contours.back().size() > 0);
 *                 SkASSERT(pts[0] == contours.back().verts.back());
 *                 contours.back().add(pts[1], {0, 0}, {0, 0});
 *                 break;
 *             case SkPathVerb::kQuad:
 *                 SkASSERT(contours.back().size() > 0);
 *                 SkASSERT(pts[0] == contours.back().verts.back());
 *                 SkPoint cubic[4];
 *                 SkConvertQuadToCubic(pts, cubic);
 *                 contours.back().out_tan.back() = cubic[1] - cubic[0];
 *                 contours.back().add(cubic[3], cubic[2] - cubic[3], {0, 0});
 *                 break;
 *             case SkPathVerb::kCubic:
 *                 SkASSERT(contours.back().size() > 0);
 *                 SkASSERT(pts[0] == contours.back().verts.back());
 *                 contours.back().out_tan.back() = pts[1] - pts[0];
 *                 contours.back().add(pts[3], pts[2] - pts[3], {0, 0});
 *                 break;
 *             case SkPathVerb::kConic:
 *                 SkDebugf("Unexpected conic verb!\n");
 *                 break;
 *         }
 *     }
 *
 *     auto ptsToLottie = [](const std::vector<SkPoint> v, SkArenaAlloc& alloc) {
 *         std::vector<Value> vec(v.size());
 *         for (size_t i = 0; i < v.size(); ++i) {
 *             Value fields[] = { NumberValue(v[i].fX), NumberValue(v[i].fY) };
 *             vec[i] = ArrayValue(fields, std::size(fields), alloc);
 *         }
 *
 *         return ArrayValue(vec.data(), vec.size(), alloc);
 *     };
 *
 *     std::vector<Value> jcontours(contours.size());
 *     for (size_t i = 0; i < contours.size(); ++i) {
 *         const skjson::Member fields_k[] = {
 *             { StringValue("v", alloc), ptsToLottie(contours[i].verts,   alloc) },
 *             { StringValue("i", alloc), ptsToLottie(contours[i].in_tan,  alloc) },
 *             { StringValue("o", alloc), ptsToLottie(contours[i].out_tan, alloc) },
 *             { StringValue("c", alloc), BoolValue (contours[i].closed)          },
 *         };
 *
 *         const skjson::Member fields_ks[] = {
 *             { StringValue("a", alloc), NumberValue(0)                                    },
 *             { StringValue("k", alloc), ObjectValue(fields_k, std::size(fields_k), alloc) },
 *         };
 *
 *         const skjson::Member fields[] = {
 *             { StringValue("ty" , alloc), StringValue("sh", alloc)                            },
 *             { StringValue("hd" , alloc), BoolValue(false)                                    },
 *             { StringValue("ind", alloc), NumberValue(SkToInt(i))                             },
 *             { StringValue("ks" , alloc), ObjectValue(fields_ks, std::size(fields_ks), alloc) },
 *             { StringValue("mn" , alloc), StringValue("ADBE Vector Shape - Group" , alloc)    },
 *             { StringValue("nm" , alloc), StringValue("_" , alloc)                            },
 *         };
 *
 *         jcontours[i] = ObjectValue(fields, std::size(fields), alloc);
 *     }
 *
 *     const skjson::Member fields_sh[] = {
 *         { StringValue("ty" , alloc), StringValue("gr", alloc)                              },
 *         { StringValue("hd" , alloc), BoolValue(false)                                      },
 *         { StringValue("bm" , alloc), NumberValue(0)                                        },
 *         { StringValue("it" , alloc), ArrayValue(jcontours.data(), jcontours.size(), alloc) },
 *         { StringValue("mn" , alloc), StringValue("ADBE Vector Group" , alloc)              },
 *         { StringValue("nm" , alloc), StringValue("_" , alloc)                              },
 *     };
 *
 *     const Value shape = ObjectValue(fields_sh, std::size(fields_sh), alloc);
 *     const skjson::Member fields_data[] = {
 *         { StringValue("shapes" , alloc), ArrayValue(&shape, 1, alloc) },
 *     };
 *
 *     return ObjectValue(fields_data, std::size(fields_data), alloc);
 * }
 * ```
 */
public fun pathToLottie(path: SkPath, alloc: SkArenaAlloc): Value {
  TODO("Implement pathToLottie")
}

/**
 * C++ original:
 * ```cpp
 * bool Preshape(const sk_sp<SkData>& json, SkWStream* stream,
 *               const sk_sp<SkFontMgr>& fmgr,
 *               const sk_sp<SkShapers::Factory>& sfact,
 *               const sk_sp<ResourceProvider>& rp) {
 *     return Preshape(static_cast<const char*>(json->data()), json->size(), stream, fmgr, sfact, rp);
 * }
 * ```
 */
public fun preshape(
  json: SkSp<SkData>,
  stream: SkWStream?,
  fmgr: SkSp<SkFontMgr>,
  sfact: SkSp<SkShapers.Factory>,
  rp: SkSp<ResourceProvider>,
): Boolean {
  TODO("Implement preshape")
}

/**
 * C++ original:
 * ```cpp
 * void draw_line_as_rect(ParagraphPainter* painter, SkScalar x, SkScalar y, SkScalar width,
 *                        const ParagraphPainter::DecorationStyle& decorStyle) {
 *     SkASSERT(decorStyle.skPaint().getPathEffect() == nullptr);
 *     SkASSERT(decorStyle.skPaint().getStrokeCap() == SkPaint::kButt_Cap);
 *     SkASSERT(decorStyle.skPaint().getStrokeWidth() > 0);   // this trick won't work for hairlines
 *
 *     float radius = decorStyle.getStrokeWidth() * 0.5f;
 *     painter->drawFilledRect({x, y - radius, x + width, y + radius}, decorStyle);
 * }
 * ```
 */
public fun drawLineAsRect(
  painter: ParagraphPainter?,
  x: SkScalar,
  y: SkScalar,
  width: SkScalar,
  decorStyle: ParagraphPainter.DecorationStyle,
) {
  TODO("Implement drawLineAsRect")
}

/**
 * C++ original:
 * ```cpp
 * int32_t relax(SkScalar a) {
 *         // This rounding is done to match Flutter tests. Must be removed..
 *         if (SkIsFinite(a)) {
 *           auto threshold = SkIntToScalar(1 << 12);
 *           return SkFloat2Bits(SkScalarRoundToScalar(a * threshold)/threshold);
 *         } else {
 *           return SkFloat2Bits(a);
 *         }
 *     }
 * ```
 */
public fun relax(a: SkScalar): Int {
  TODO("Implement relax")
}

/**
 * C++ original:
 * ```cpp
 * bool exactlyEqual(SkScalar x, SkScalar y) {
 *         return x == y || (x != x && y != y);
 *     }
 * ```
 */
public fun exactlyEqual(x: SkScalar, y: SkScalar): Boolean {
  TODO("Implement exactlyEqual")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_ascii_7bit_space(int c) {
 *     SkASSERT(c >= 0 && c <= 127);
 *
 *     // Extracted from https://en.wikipedia.org/wiki/Whitespace_character
 *     //
 *     enum WS {
 *         kHT    = 9,
 *         kLF    = 10,
 *         kVT    = 11,
 *         kFF    = 12,
 *         kCR    = 13,
 *         kSP    = 32,    // too big to use as shift
 *     };
 * #define M(shift)    (1 << (shift))
 *     constexpr uint32_t kSpaceMask = M(kHT) | M(kLF) | M(kVT) | M(kFF) | M(kCR);
 *     // we check for Space (32) explicitly, since it is too large to shift
 *     return (c == kSP) || (c <= 31 && (kSpaceMask & M(c)));
 * #undef M
 * }
 * ```
 */
public fun isAscii7bitSpace(c: Int): Boolean {
  TODO("Implement isAscii7bitSpace")
}

/**
 * C++ original:
 * ```cpp
 * TextRange intersected(const TextRange& a, const TextRange& b) {
 *     if (a.start == b.start && a.end == b.end) return a;
 *     auto begin = std::max(a.start, b.start);
 *     auto end = std::min(a.end, b.end);
 *     return end >= begin ? TextRange(begin, end) : EMPTY_TEXT;
 * }
 * ```
 */
public fun intersected(a: TextRange, b: TextRange): TextRange {
  TODO("Implement intersected")
}

/**
 * C++ original:
 * ```cpp
 * SkScalar littleRound(SkScalar a) {
 *     // This rounding is done to match Flutter tests. Must be removed..
 *   return SkScalarRoundToScalar(a * 100.0)/100.0;
 * }
 * ```
 */
public fun littleRound(a: SkScalar): SkScalar {
  TODO("Implement littleRound")
}

/**
 * C++ original:
 * ```cpp
 * int compareRound(SkScalar a, SkScalar b, bool applyRoundingHack) {
 *     // There is a rounding error that gets bigger when maxWidth gets bigger
 *     // VERY long zalgo text (> 100000) on a VERY long line (> 10000)
 *     // Canvas scaling affects it
 *     // Letter spacing affects it
 *     // It has to be relative to be useful
 *     auto base = std::max(SkScalarAbs(a), SkScalarAbs(b));
 *     auto diff = SkScalarAbs(a - b);
 *     if (nearlyZero(base) || diff / base < 0.001f) {
 *         return 0;
 *     }
 *
 *     auto ra = a;
 *     auto rb = b;
 *
 *     if (applyRoundingHack) {
 *         ra = littleRound(a);
 *         rb = littleRound(b);
 *     }
 *     if (ra < rb) {
 *         return -1;
 *     } else {
 *         return 1;
 *     }
 * }
 * ```
 */
public fun compareRound(
  a: SkScalar,
  b: SkScalar,
  applyRoundingHack: Boolean,
): Int {
  TODO("Implement compareRound")
}

/**
 * C++ original:
 * ```cpp
 * static Segment swap_ends(const Segment& s) {
 *     return {s.p1, s.p0};
 * }
 * ```
 */
public fun swapEnds(s: Segment): Segment {
  TODO("Implement swapEnds")
}

/**
 * C++ original:
 * ```cpp
 * TextPropertyValue make_text_prop(const char* str) {
 *     TextPropertyValue prop;
 *
 *     prop.fTypeface = ToolUtils::DefaultPortableTypeface();
 *     prop.fText     = SkString(str);
 *
 *     return prop;
 * }
 * ```
 */
public fun makeTextProp(str: String?): TextPropertyValue {
  TODO("Implement makeTextProp")
}

/**
 * C++ original:
 * ```cpp
 * std::u16string mirror(const std::string& text) {
 *     std::u16string result;
 *     result += u"\u202E";
 *     for (auto i = text.size(); i > 0; --i) {
 *         result += text[i - 1];
 *     }
 *     //result += u"\u202C";
 *     return result;
 * }
 * ```
 */
public fun mirror(text: String): u16string {
  TODO("Implement mirror")
}

/**
 * C++ original:
 * ```cpp
 * std::u16string straight(const std::string& text) {
 *     std::u16string result;
 *     result += u"\u202D";
 *     for (auto ch : text) {
 *         result += ch;
 *     }
 *     return result;
 * }
 * ```
 */
public fun straight(text: String): u16string {
  TODO("Implement straight")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkUnicode> get_unicode() {
 *     auto factory = SkShapers::BestAvailable();
 *     return sk_ref_sp<SkUnicode>(factory->getUnicode());
 * }
 * ```
 */
public fun getUnicode(): SkSp<SkUnicode> {
  TODO("Implement getUnicode")
}

/**
 * C++ original:
 * ```cpp
 * void performGetRectsForRangeConcurrently(skiatest::Reporter* reporter) {
 *     sk_sp<ResourceFontCollection> fontCollection = sk_make_sp<ResourceFontCollection>();
 *     SKIP_IF_FONTS_NOT_FOUND(reporter, fontCollection)
 *
 *     auto const text = std::u16string(42000, 'x');
 *     ParagraphStyle paragraphStyle;
 *     TextStyle textStyle;
 *     textStyle.setFontFamilies({SkString("Roboto")});
 *     textStyle.setFontSize(14);
 *     textStyle.setColor(SK_ColorBLACK);
 *     textStyle.setFontStyle(SkFontStyle(SkFontStyle::kMedium_Weight, SkFontStyle::kNormal_Width,
 *                                        SkFontStyle::kUpright_Slant));
 *
 *     ParagraphBuilderImpl builder(paragraphStyle, fontCollection, get_unicode());
 *     builder.pushStyle(textStyle);
 *     builder.addText(text);
 *     builder.pop();
 *
 *     auto paragraph = builder.Build();
 *     paragraph->layout(std::numeric_limits<float>::max());
 *
 *     RectHeightStyle heightStyle = RectHeightStyle::kMax;
 *     RectWidthStyle widthStyle = RectWidthStyle::kMax;
 *     auto t1 = std::thread([&] {
 *         auto result = paragraph->getRectsForRange(0, 2, heightStyle, widthStyle);
 *         REPORTER_ASSERT(reporter, !result.empty());
 *     });
 *     auto t2 = std::thread([&] {
 *         auto result = paragraph->getRectsForRange(5, 10, heightStyle, widthStyle);
 *         REPORTER_ASSERT(reporter, !result.empty());
 *     });
 *     t1.join();
 *     t2.join();
 * }
 * ```
 */
public fun performGetRectsForRangeConcurrently(reporter: Reporter?) {
  TODO("Implement performGetRectsForRangeConcurrently")
}

/**
 * C++ original:
 * ```cpp
 * static bool has_empty_typeface(SkFont f) {
 *     SkTypeface* face = f.getTypeface();
 *     if (!face) {
 *         return true; // Should be impossible, but just in case...
 *     }
 *     return face->countGlyphs() == 0 && face->getBounds().isEmpty();
 * }
 * ```
 */
public fun hasEmptyTypeface(f: SkFont): Boolean {
  TODO("Implement hasEmptyTypeface")
}

/**
 * C++ original:
 * ```cpp
 * static void SkParagraph_EmojiFontResolution(sk_sp<SkUnicode> icu, skiatest::Reporter* reporter) {
 *     sk_sp<ResourceFontCollection> fontCollection = sk_make_sp<ResourceFontCollection>();
 *     SKIP_IF_FONTS_NOT_FOUND(reporter, fontCollection)
 *
 *     const char* text = "♻️🏴󠁧󠁢󠁳󠁣󠁴󠁿";
 *     const char* text1 = "♻️";
 *     const size_t len = strlen(text);
 *     const size_t len1 = strlen(text1);
 *
 *     ParagraphStyle paragraph_style;
 *     ParagraphBuilderImpl builder(paragraph_style, fontCollection, get_unicode());
 *     TextStyle text_style;
 *     text_style.setFontFamilies({SkString("")});
 *     builder.pushStyle(text_style);
 *     builder.addText(text, len);
 *     auto paragraph = builder.Build();
 *     paragraph->layout(SK_ScalarMax);
 *
 *     auto impl = static_cast<ParagraphImpl*>(paragraph.get());
 *     REPORTER_ASSERT(reporter, impl->runs().size() == 1, "size: %zu", impl->runs().size());
 *
 *     ParagraphBuilderImpl builder1(paragraph_style, fontCollection, get_unicode());
 *     builder1.pushStyle(text_style);
 *     builder1.addText(text1, len1);
 *     auto paragraph1 = builder1.Build();
 *     paragraph1->layout(SK_ScalarMax);
 *
 *     auto impl1 = static_cast<ParagraphImpl*>(paragraph1.get());
 *     REPORTER_ASSERT(reporter, impl1->runs().size() == 1, "size: %zu", impl1->runs().size());
 *     if (impl1->runs().size() == 1) {
 *         SkString ff;
 *         impl->run(0).font().getTypeface()->getFamilyName(&ff);
 *         SkString ff1;
 *         impl1->run(0).font().getTypeface()->getFamilyName(&ff1);
 *         REPORTER_ASSERT(reporter, ff.equals(ff1));
 *     }
 * }
 * ```
 */
public fun skParagraphEmojiFontResolution(icu: SkSp<SkUnicode>, reporter: Reporter?) {
  TODO("Implement skParagraphEmojiFontResolution")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_one_to_one(const char utf8[], size_t utf8Begin, size_t utf8End,
 *         std::vector<uint32_t>& clusters) {
 *     size_t lastUtf8Index = utf8End;
 *
 *     auto checkCluster = [&](size_t clusterIndex) {
 *         if (clusters[clusterIndex] >= lastUtf8Index) {
 *             return false;
 *         }
 *         size_t utf8ClusterSize = lastUtf8Index - clusters[clusterIndex];
 *         if (SkUTF::CountUTF8(&utf8[clusters[clusterIndex]], utf8ClusterSize) != 1) {
 *             return false;
 *         }
 *         lastUtf8Index = clusters[clusterIndex];
 *         return true;
 *     };
 *
 *     if (clusters.front() <= clusters.back()) {
 *         // left-to-right clusters
 *         size_t clusterCursor = clusters.size();
 *         while (clusterCursor > 0) {
 *             if (!checkCluster(--clusterCursor)) { return false; }
 *         }
 *     } else {
 *         // right-to-left clusters
 *         size_t clusterCursor = 0;
 *         while (clusterCursor < clusters.size()) {
 *             if (!checkCluster(clusterCursor++)) { return false; }
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun isOneToOne(
  utf8: CharArray,
  utf8Begin: ULong,
  utf8End: ULong,
  clusters: List<UInt>,
): Boolean {
  TODO("Implement isOneToOne")
}

/**
 * C++ original:
 * ```cpp
 * static void check_inval(skiatest::Reporter* reporter, const sk_sp<sksg::Node>& root,
 *                         const SkRect& expected_bounds,
 *                         const SkRect& expected_inval_bounds,
 *                         const std::vector<SkRect>* expected_damage) {
 *     sksg::InvalidationController ic;
 *     const auto bbox = root->revalidate(&ic, SkMatrix::I());
 *
 *     if ((false)) {
 *         SkDebugf("** bbox: [%f %f %f %f], ibbox: [%f %f %f %f]\n",
 *                  bbox.fLeft, bbox.fTop, bbox.fRight, bbox.fBottom,
 *                  ic.bounds().left(), ic.bounds().top(), ic.bounds().right(), ic.bounds().bottom());
 *     }
 *
 *     REPORTER_ASSERT(reporter, bbox == expected_bounds);
 *     REPORTER_ASSERT(reporter, ic.bounds() == expected_inval_bounds);
 *
 *     if (expected_damage) {
 *         const auto damage_count = SkTo<size_t>(ic.end() - ic.begin());
 *         REPORTER_ASSERT(reporter, expected_damage->size() == damage_count);
 *         for (size_t i = 0; i < std::min(expected_damage->size(), damage_count); ++i) {
 *             const auto r1 = (*expected_damage)[i],
 *                        r2 = ic.begin()[i];
 *             if ((false)) {
 *                 SkDebugf("*** expected inval: [%f %f %f %f], actual: [%f %f %f %f]\n",
 *                          r1.left(), r1.top(), r1.right(), r1.bottom(),
 *                          r2.left(), r2.top(), r2.right(), r2.bottom());
 *             }
 *             REPORTER_ASSERT(reporter, r1 == r2);
 *         }
 *     }
 * }
 * ```
 */
public fun checkInval(
  reporter: Reporter?,
  root: SkSp<Node>,
  expectedBounds: SkRect,
  expectedInvalBounds: SkRect,
  expectedDamage: List<SkRect>?,
) {
  TODO("Implement checkInval")
}

/**
 * C++ original:
 * ```cpp
 * static void check_hittest(skiatest::Reporter* reporter, const sk_sp<sksg::RenderNode>& root,
 *                           const std::vector<HitTest>& tests) {
 *     for (const auto& tst : tests) {
 *         const auto* node = root->nodeAt(tst.pt);
 *         if (node != tst.node.get()) {
 *             SkDebugf("*** nodeAt(%f, %f) - expected %p, got %p\n",
 *                      tst.pt.x(), tst.pt.y(), tst.node.get(), node);
 *         }
 *         REPORTER_ASSERT(reporter, tst.node.get() == node);
 *     }
 * }
 * ```
 */
public fun checkHittest(
  reporter: Reporter?,
  root: SkSp<RenderNode>,
  tests: List<HitTest>,
) {
  TODO("Implement checkHittest")
}

/**
 * C++ original:
 * ```cpp
 * static void inval_test1(skiatest::Reporter* reporter) {
 *     auto color  = sksg::Color::Make(0xff000000);
 *     auto r1     = sksg::Rect::Make(SkRect::MakeWH(100, 100)),
 *          r2     = sksg::Rect::Make(SkRect::MakeWH(100, 100));
 *     auto grp    = sksg::Group::Make();
 *     auto matrix = sksg::Matrix<SkMatrix>::Make(SkMatrix::I());
 *     auto root   = sksg::TransformEffect::Make(grp, matrix);
 *     auto d1     = sksg::Draw::Make(r1, color),
 *          d2     = sksg::Draw::Make(r2, color);
 *
 *     grp->addChild(d1);
 *     grp->addChild(d2);
 *
 *     {
 *         // Initial revalidation.
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(100, 100),
 *                     SkRectPriv::MakeLargeS32(),
 *                     nullptr);
 *
 *         check_hittest(reporter, root, {
 *                           {{  -1,   0 }, nullptr },
 *                           {{   0,  -1 }, nullptr },
 *                           {{ 100,   0 }, nullptr },
 *                           {{   0, 100 }, nullptr },
 *                           {{   0,   0 },      d2 },
 *                           {{  99,  99 },      d2 },
 *                       });
 *     }
 *
 *     {
 *         // Move r2 to (200 100).
 *         r2->setL(200); r2->setT(100); r2->setR(300); r2->setB(200);
 *         std::vector<SkRect> damage = { {0, 0, 100, 100}, { 200, 100, 300, 200} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(300, 200),
 *                     SkRect::MakeWH(300, 200),
 *                     &damage);
 *
 *         check_hittest(reporter, root, {
 *                           {{  -1,   0 }, nullptr },
 *                           {{   0,  -1 }, nullptr },
 *                           {{ 100,   0 }, nullptr },
 *                           {{   0, 100 }, nullptr },
 *                           {{   0,   0 },      d1 },
 *                           {{  99,  99 },      d1 },
 *
 *                           {{ 199, 100 }, nullptr },
 *                           {{ 200,  99 }, nullptr },
 *                           {{ 300, 100 }, nullptr },
 *                           {{ 200, 200 }, nullptr },
 *                           {{ 200, 100 },      d2 },
 *                           {{ 299, 199 },      d2 },
 *                       });
 *     }
 *
 *     {
 *         // Update the common color.
 *         color->setColor(0xffff0000);
 *         std::vector<SkRect> damage = { {0, 0, 100, 100}, { 200, 100, 300, 200} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(300, 200),
 *                     SkRect::MakeWH(300, 200),
 *                     &damage);
 *     }
 *
 *     {
 *         // Shrink r1.
 *         r1->setR(50);
 *         std::vector<SkRect> damage = { {0, 0, 100, 100}, { 0, 0, 50, 100} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(300, 200),
 *                     SkRect::MakeWH(100, 100),
 *                     &damage);
 *
 *         check_hittest(reporter, root, {
 *                           {{  -1,   0 }, nullptr },
 *                           {{   0,  -1 }, nullptr },
 *                           {{  50,   0 }, nullptr },
 *                           {{   0, 100 }, nullptr },
 *                           {{   0,   0 },      d1 },
 *                           {{  49,  99 },      d1 },
 *
 *                           {{ 199, 100 }, nullptr },
 *                           {{ 200,  99 }, nullptr },
 *                           {{ 300, 100 }, nullptr },
 *                           {{ 200, 200 }, nullptr },
 *                           {{ 200, 100 },      d2 },
 *                           {{ 299, 199 },      d2 },
 *                       });
 *     }
 *
 *     {
 *         // Update transform.
 *         matrix->setMatrix(SkMatrix::Scale(2, 2));
 *         std::vector<SkRect> damage = { {0, 0, 300, 200}, { 0, 0, 600, 400} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(600, 400),
 *                     SkRect::MakeWH(600, 400),
 *                     &damage);
 *
 *         check_hittest(reporter, root, {
 *                           {{  -1,   0 }, nullptr },
 *                           {{   0,  -1 }, nullptr },
 *                           {{  25,   0 }, nullptr },
 *                           {{   0,  50 }, nullptr },
 *                           {{   0,   0 },      d1 },
 *                           {{  24,  49 },      d1 },
 *
 *                           {{  99,  50 }, nullptr },
 *                           {{ 100,  49 }, nullptr },
 *                           {{ 150,  50 }, nullptr },
 *                           {{ 100, 100 }, nullptr },
 *                           {{ 100,  50 },      d2 },
 *                           {{ 149,  99 },      d2 },
 *                       });
 *     }
 *
 *     {
 *         // Shrink r2 under transform.
 *         r2->setR(250);
 *         std::vector<SkRect> damage = { {400, 200, 600, 400}, { 400, 200, 500, 400} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(500, 400),
 *                     SkRect::MakeLTRB(400, 200, 600, 400),
 *                     &damage);
 *
 *         check_hittest(reporter, root, {
 *                           {{  -1,   0 }, nullptr },
 *                           {{   0,  -1 }, nullptr },
 *                           {{  25,   0 }, nullptr },
 *                           {{   0,  50 }, nullptr },
 *                           {{   0,   0 },      d1 },
 *                           {{  24,  49 },      d1 },
 *
 *                           {{  99,  50 }, nullptr },
 *                           {{ 100,  49 }, nullptr },
 *                           {{ 125,  50 }, nullptr },
 *                           {{ 100, 100 }, nullptr },
 *                           {{ 100,  50 },      d2 },
 *                           {{ 124,  99 },      d2 },
 *                       });
 *     }
 * }
 * ```
 */
public fun invalTest1(reporter: Reporter?) {
  TODO("Implement invalTest1")
}

/**
 * C++ original:
 * ```cpp
 * static void inval_test2(skiatest::Reporter* reporter) {
 *     auto color = sksg::Color::Make(0xff000000);
 *     auto rect  = sksg::Rect::Make(SkRect::MakeWH(100, 100));
 *     auto m1    = sksg::Matrix<SkMatrix>::Make(SkMatrix::I()),
 *          m2    = sksg::Matrix<SkMatrix>::Make(SkMatrix::I());
 *     auto t1    = sksg::TransformEffect::Make(sksg::Draw::Make(rect, color),
 *                                              sksg::Transform::MakeConcat(m1, m2)),
 *          t2    = sksg::TransformEffect::Make(sksg::Draw::Make(rect, color), m1);
 *     auto root  = sksg::Group::Make();
 *     root->addChild(t1);
 *     root->addChild(t2);
 *
 *     {
 *         // Initial revalidation.
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(100, 100),
 *                     SkRectPriv::MakeLargeS32(),
 *                     nullptr);
 *     }
 *
 *     {
 *         // Update the shared color.
 *         color->setColor(0xffff0000);
 *         std::vector<SkRect> damage = { {0, 0, 100, 100}, { 0, 0, 100, 100} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(100, 100),
 *                     SkRect::MakeWH(100, 100),
 *                     &damage);
 *     }
 *
 *     {
 *         // Update m2.
 *         m2->setMatrix(SkMatrix::Scale(2, 2));
 *         std::vector<SkRect> damage = { {0, 0, 100, 100}, { 0, 0, 200, 200} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeWH(200, 200),
 *                     SkRect::MakeWH(200, 200),
 *                     &damage);
 *     }
 *
 *     {
 *         // Update shared m1.
 *         m1->setMatrix(SkMatrix::Translate(100, 100));
 *         std::vector<SkRect> damage = { {   0,   0, 200, 200},   // draw1 prev bounds
 *                                        { 100, 100, 300, 300},   // draw1 new bounds
 *                                        {   0,   0, 100, 100},   // draw2 prev bounds
 *                                        { 100, 100, 200, 200} }; // draw2 new bounds
 *         check_inval(reporter, root,
 *                     SkRect::MakeLTRB(100, 100, 300, 300),
 *                     SkRect::MakeLTRB(  0,   0, 300, 300),
 *                     &damage);
 *     }
 *
 *     {
 *         // Update shared rect.
 *         rect->setR(50);
 *         std::vector<SkRect> damage = { { 100, 100, 300, 300},   // draw1 prev bounds
 *                                        { 100, 100, 200, 300},   // draw1 new bounds
 *                                        { 100, 100, 200, 200},   // draw2 prev bounds
 *                                        { 100, 100, 150, 200} }; // draw2 new bounds
 *         check_inval(reporter, root,
 *                     SkRect::MakeLTRB(100, 100, 200, 300),
 *                     SkRect::MakeLTRB(100, 100, 300, 300),
 *                     &damage);
 *     }
 * }
 * ```
 */
public fun invalTest2(reporter: Reporter?) {
  TODO("Implement invalTest2")
}

/**
 * C++ original:
 * ```cpp
 * static void inval_test3(skiatest::Reporter* reporter) {
 *     auto color1 = sksg::Color::Make(0xff000000),
 *          color2 = sksg::Color::Make(0xff000000);
 *     auto group  = sksg::Group::Make();
 *
 *     group->addChild(sksg::Draw::Make(sksg::Rect::Make(SkRect::MakeWH(100, 100)),
 *                                      color1));
 *     group->addChild(sksg::Draw::Make(sksg::Rect::Make(SkRect::MakeXYWH(200, 0, 100, 100)),
 *                                      color2));
 *     auto filter = sksg::DropShadowImageFilter::Make();
 *     filter->setOffset({50, 75});
 *     auto root = sksg::ImageFilterEffect::Make(group, filter);
 *
 *     {
 *         // Initial revalidation.
 *         check_inval(reporter, root,
 *                     SkRect::MakeXYWH(0, 0, 350, 175),
 *                     SkRectPriv::MakeLargeS32(),
 *                     nullptr);
 *     }
 *
 *     {
 *         // Shadow-only.
 *         filter->setMode(sksg::DropShadowImageFilter::Mode::kShadowOnly);
 *         std::vector<SkRect> damage = { {0, 0, 350, 175}, { 50, 75, 350, 175} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeLTRB(50, 75, 350, 175),
 *                     SkRect::MakeLTRB(0, 0, 350, 175),
 *                     &damage);
 *     }
 *
 *     {
 *         // Content change -> single/full filter bounds inval.
 *         color1->setColor(0xffff0000);
 *         std::vector<SkRect> damage = { { 50, 75, 350, 175} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeLTRB(50, 75, 350, 175),
 *                     SkRect::MakeLTRB(50, 75, 350, 175),
 *                     &damage);
 *     }
 *
 *     {
 *         // Visibility change -> full inval.
 *         group->setVisible(false);
 *         std::vector<SkRect> damage = { { 50, 75, 350, 175} };
 *         check_inval(reporter, root,
 *                     SkRect::MakeLTRB(50, 75, 350, 175),
 *                     SkRect::MakeLTRB(50, 75, 350, 175),
 *                     &damage);
 *     }
 * }
 * ```
 */
public fun invalTest3(reporter: Reporter?) {
  TODO("Implement invalTest3")
}

/**
 * C++ original:
 * ```cpp
 * static void inval_group_remove(skiatest::Reporter* reporter) {
 *     auto draw = sksg::Draw::Make(sksg::Rect::Make(SkRect::MakeWH(100, 100)),
 *                                  sksg::Color::Make(SK_ColorBLACK));
 *     auto grp = sksg::Group::Make();
 *
 *     // Readding the child should not trigger asserts.
 *     grp->addChild(draw);
 *     grp->removeChild(draw);
 *     grp->addChild(draw);
 * }
 * ```
 */
public fun invalGroupRemove(reporter: Reporter?) {
  TODO("Implement invalGroupRemove")
}

/**
 * C++ original:
 * ```cpp
 * static const char* verb_to_string(SkPath::Verb verb) {
 *     switch (verb) {
 *         case SkPath::kMove_Verb:
 *             return "Move";
 *         case SkPath::kLine_Verb:
 *             return "Line";
 *         case SkPath::kQuad_Verb:
 *             return "Quad";
 *         case SkPath::kConic_Verb:
 *             return "Conic";
 *         case SkPath::kCubic_Verb:
 *             return "Cubic";
 *         case SkPath::kClose_Verb:
 *             return "Close";
 *         case SkPath::kDone_Verb:
 *             return "Done";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun verbToString(verb: SkPathVerb): Char {
  TODO("Implement verbToString")
}

/**
 * C++ original:
 * ```cpp
 * static void assert_paths_equal(skiatest::Reporter* reporter, const SkPath& a, const SkPath& b) {
 *     if (a.getFillType() != b.getFillType()) {
 *         ERRORF(reporter,
 *                "Paths differ in FillType. Expected %d, got %d.",
 *                (int)a.getFillType(),
 *                (int)b.getFillType());
 *         a.dump();
 *         b.dump();
 *         return;
 *     }
 *     if (SkPathPriv::GetConvexity(a) != SkPathPriv::GetConvexity(b)) {
 *         ERRORF(reporter,
 *                "Paths differ in Convexity. Expected %d, got %d.",
 *                (int)SkPathPriv::GetConvexity(a),
 *                (int)SkPathPriv::GetConvexity(b));
 *         return;
 *     }
 *
 *     SkPath::RawIter iterA(a);
 *     SkPath::RawIter iterB(b);
 *
 *     SkPoint ptsA[4], ptsB[4];
 *     int verbIndex = 0;
 *
 *     for (;;) {
 *         SkPath::Verb verbA = iterA.next(ptsA);
 *         SkPath::Verb verbB = iterB.next(ptsB);
 *
 *         if (verbA != verbB) {
 *             ERRORF(reporter,
 *                    "Paths differ at verb index %d. Expected %s, got %s.",
 *                    verbIndex,
 *                    verb_to_string(verbA),
 *                    verb_to_string(verbB));
 *             a.dump();
 *             b.dump();
 *             return;
 *         }
 *
 *         if (verbA == SkPath::kDone_Verb) {
 *             break;
 *         }
 *
 *         const int numPts = SkPathPriv::PtsInIter(verbA);
 *         for (int i = 0; i < numPts; ++i) {
 *             if (ptsA[i] != ptsB[i]) {
 *                 ERRORF(reporter,
 *                        "Paths differ at verb index %d (%s), point %d. "
 *                        "Expected (%f, %f), got (%f, %f).",
 *                        verbIndex,
 *                        verb_to_string(verbA),
 *                        i,
 *                        ptsA[i].fX,
 *                        ptsA[i].fY,
 *                        ptsB[i].fX,
 *                        ptsB[i].fY);
 *                 a.dump();
 *                 b.dump();
 *                 return;
 *             }
 *         }
 *
 *         if (verbA == SkPath::kConic_Verb) {
 *             const float weightA = iterA.conicWeight();
 *             const float weightB = iterB.conicWeight();
 *             if (weightA != weightB) {
 *                 ERRORF(reporter,
 *                        "Paths differ at verb index %d (Conic), weight. "
 *                        "Expected %f, got %f.",
 *                        verbIndex,
 *                        weightA,
 *                        weightB);
 *                 a.dump();
 *                 b.dump();
 *                 return;
 *             }
 *         }
 *
 *         verbIndex++;
 *     }
 * }
 * ```
 */
public fun assertPathsEqual(
  reporter: Reporter?,
  a: SkPath,
  b: SkPath,
) {
  TODO("Implement assertPathsEqual")
}

/**
 * C++ original:
 * ```cpp
 * static void test_merge(skiatest::Reporter* reporter,
 *                        const char* name,
 *                        std::vector<sksg::Merge::Rec>&& recs,
 *                        const SkPath& expected) {
 *     skiatest::ReporterContext rc(reporter, name);
 *     auto merge = sksg::Merge::Make(std::move(recs));
 *     sksg::InvalidationController ic;
 *     merge->revalidate(&ic, SkMatrix::I());
 *     assert_paths_equal(reporter, merge->asPath(), expected);
 * }
 * ```
 */
public fun testMerge(
  reporter: Reporter?,
  name: String?,
  recs: List<Merge.Rec>,
  expected: SkPath,
) {
  TODO("Implement testMerge")
}

/**
 * C++ original:
 * ```cpp
 * void shaper_test(skiatest::Reporter* reporter, const char* name, SkData* data) {
 *     skiatest::ReporterContext context(reporter, name);
 *     auto unicode = get_unicode();
 *     if (!unicode) {
 *         ERRORF(reporter, "Could not create unicode.");
 *         return;
 *     }
 *
 *     auto shaper = SkShapers::HB::ShaperDrivenWrapper(unicode,
 *                                                      SkFontMgr::RefEmpty());  // no fallback
 *     if (!shaper) {
 *         ERRORF(reporter, "Could not create shaper.");
 *         return;
 *     }
 *     if (!unicode) {
 *         ERRORF(reporter, "Could not create unicode.");
 *         return;
 *     }
 *     constexpr float kWidth = 400;
 *     SkFont font = ToolUtils::DefaultFont();
 *     const char* utf8 = (const char*)data->data();
 *     size_t utf8Bytes = data->size();
 *
 *     RunHandler rh(name, reporter, utf8, utf8Bytes);
 *
 *     const SkBidiIterator::Level defaultLevel = SkBidiIterator::kLTR;
 *     std::unique_ptr<SkShaper::BiDiRunIterator> bidi =
 *             SkShapers::unicode::BidiRunIterator(unicode, utf8, utf8Bytes, defaultLevel);
 *     SkASSERT(bidi);
 *
 *     std::unique_ptr<SkShaper::LanguageRunIterator> language =
 *             SkShaper::MakeStdLanguageRunIterator(utf8, utf8Bytes);
 *     SkASSERT(language);
 *
 *     std::unique_ptr<SkShaper::ScriptRunIterator> script =
 *             SkShapers::HB::ScriptRunIterator(utf8, utf8Bytes);
 *     SkASSERT(script);
 *
 *     std::unique_ptr<SkShaper::FontRunIterator> fontRuns =
 *             SkShaper::MakeFontMgrRunIterator(utf8, utf8Bytes, font, SkFontMgr::RefEmpty());
 *     SkASSERT(fontRuns);
 *     shaper->shape(utf8, utf8Bytes, *fontRuns, *bidi, *script, *language, nullptr, 0, kWidth, &rh);
 *
 *     // Even on empty input, expect that the line is started, that the zero run infos are committed,
 *     // and the empty line is committed. This allows the user to properly handle empty runs.
 *     REPORTER_ASSERT(reporter, rh.fBeginLine);
 *     REPORTER_ASSERT(reporter, rh.fCommitRunInfo);
 *     REPORTER_ASSERT(reporter, rh.fCommitLine);
 *
 *     constexpr SkFourByteTag latn = SkSetFourByteTag('l','a','t','n');
 *     auto fontIterator = SkShaper::TrivialFontRunIterator(font, data->size());
 *     auto bidiIterator = SkShaper::TrivialBiDiRunIterator(0, data->size());
 *     auto scriptIterator = SkShaper::TrivialScriptRunIterator(latn, data->size());
 *     auto languageIterator = SkShaper::TrivialLanguageRunIterator("en-US", data->size());
 *     shaper->shape((const char*)data->data(),
 *                   data->size(),
 *                   fontIterator,
 *                   bidiIterator,
 *                   scriptIterator,
 *                   languageIterator,
 *                   nullptr,
 *                   0,
 *                   kWidth,
 *                   &rh);
 * }
 * ```
 */
public fun shaperTest(
  reporter: Reporter?,
  name: String?,
  `data`: SkData?,
) {
  TODO("Implement shaperTest")
}

/**
 * C++ original:
 * ```cpp
 * void cluster_test(skiatest::Reporter* reporter, const char* resource) {
 *     auto data = GetResourceAsData(resource);
 *     if (!data) {
 *         ERRORF(reporter, "Could not get resource %s.", resource);
 *         return;
 *     }
 *
 *     shaper_test(reporter, resource, data.get());
 * }
 * ```
 */
public fun clusterTest(reporter: Reporter?, resource: String?) {
  TODO("Implement clusterTest")
}

/**
 * C++ original:
 * ```cpp
 * bool hasWordFlag(SkUnicode::CodeUnitFlags flags) {
 *     return (flags & SkUnicode::kWordBreak) == SkUnicode::kWordBreak;
 * }
 * ```
 */
public fun hasWordFlag(flags: SkUnicode.CodeUnitFlags): Boolean {
  TODO("Implement hasWordFlag")
}

/**
 * C++ original:
 * ```cpp
 * static void SkUnicode_Emoji(SkUnicode* icu, skiatest::Reporter* reporter) {
 *     std::u32string emojis(U"😄😁😆😅😂🤣");
 *     std::u32string not_emojis(U"満毎行昼本可");
 *     for (auto e : emojis) {
 *         REPORTER_ASSERT(reporter, icu->isEmoji(e));
 *     }
 *     for (auto n: not_emojis) {
 *         REPORTER_ASSERT(reporter, !icu->isEmoji(n));
 *     }
 * }
 * ```
 */
public fun skUnicodeEmoji(icu: SkUnicode?, reporter: Reporter?) {
  TODO("Implement skUnicodeEmoji")
}

/**
 * C++ original:
 * ```cpp
 * static void SkUnicode_Ideographic(SkUnicode* icu, skiatest::Reporter* reporter) {
 *     std::u32string ideographic(U"満毎行昼本可");
 *     std::u32string not_ideographic(U"😄😁😆😅😂🤣");
 *     for (auto i : ideographic) {
 *         REPORTER_ASSERT(reporter, icu->isIdeographic(i));
 *     }
 *     for (auto n: not_ideographic) {
 *         REPORTER_ASSERT(reporter, !icu->isIdeographic(n));
 *     }
 * }
 * ```
 */
public fun skUnicodeIdeographic(icu: SkUnicode?, reporter: Reporter?) {
  TODO("Implement skUnicodeIdeographic")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * T ParseDefault(const skjson::Value& v, const T& defaultValue) {
 *     T res;
 *     if (!Parse<T>(v, &res)) {
 *         res = defaultValue;
 *     }
 *     return res;
 * }
 * ```
 */
public fun <T> parseDefault(v: Value, defaultValue: T): T {
  TODO("Implement parseDefault")
}

/**
 * C++ original:
 * ```cpp
 * inline sk_sp<Factory> BestAvailable() {
 * #if defined(SK_SHAPER_HARFBUZZ_AVAILABLE) && defined(SK_SHAPER_UNICODE_AVAILABLE)
 *     return sk_make_sp<SkShapers::HarfbuzzFactory>();
 * #elif defined(SK_SHAPER_CORETEXT_AVAILABLE)
 *     return sk_make_sp<SkShapers::CoreTextFactory>();
 * #else
 *     return SkShapers::Primitive::Factory();
 * #endif
 * }
 * ```
 */
public fun bestAvailable(): SkSp<Factory> {
  TODO("Implement bestAvailable")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * T Lerp(const T& a, const T& b, float t) { return a + (b - a) * t; }
 * ```
 */
public fun <T> lerp(
  a: T,
  b: T,
  t: Float,
): T {
  TODO("Implement lerp")
}

/**
 * C++ original:
 * ```cpp
 * template<> inline const myers::Point& get<1>(const myers::Segment& s) { return s.lower(); }
 * ```
 */
public fun `get`(s: Segment): Point {
  TODO("Implement get")
}

/**
 * C++ original:
 * ```cpp
 * bool operator==(const SkSpan<T>& a, const SkSpan<T>& b) {
 *     return a.size() == b.size() && a.begin() == b.begin();
 * }
 * ```
 */
public fun equals(other: Any?): Boolean {
  TODO("Implement equals")
}

/**
 * C++ original:
 * ```cpp
 * bool operator<=(const SkSpan<T>& a, const SkSpan<T>& b) {
 *     return a.begin() >= b.begin() && a.end() <= b.end();
 * }
 * ```
 */
public operator fun <T> compareTo(a: SkSpan<T>, b: SkSpan<T>): Int {
  TODO("Implement compareTo")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool nearlyZero(SkScalar x, SkScalar tolerance = SK_ScalarNearlyZero) {
 *     if (SkIsFinite(x)) {
 *         return SkScalarNearlyZero(x, tolerance);
 *     }
 *     return false;
 * }
 * ```
 */
public fun nearlyZero(x: SkScalar, tolerance: SkScalar = TODO()): Boolean {
  TODO("Implement nearlyZero")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool nearlyEqual(SkScalar x, SkScalar y, SkScalar tolerance = SK_ScalarNearlyZero) {
 *     if (SkIsFinite(x, y)) {
 *         return SkScalarNearlyEqual(x, y, tolerance);
 *     }
 *     // Inf == Inf, anything else is false
 *     return x == y;
 * }
 * ```
 */
public fun nearlyEqual(
  x: SkScalar,
  y: SkScalar,
  tolerance: SkScalar = TODO(),
): Boolean {
  TODO("Implement nearlyEqual")
}

/**
 * C++ original:
 * ```cpp
 * template<typename C, typename UnaryFunction>
 * UnaryFunction directional_for_each(C& c, bool forwards, UnaryFunction f) {
 *     return forwards
 *               ? std::for_each(std::begin(c), std::end(c), f)
 *               : std::for_each(std::rbegin(c), std::rend(c), f);
 * }
 * ```
 */
public fun <C, UnaryFunction> directionalForEach(
  c: C,
  forwards: Boolean,
  f: UnaryFunction,
): UnaryFunction {
  TODO("Implement directionalForEach")
}
