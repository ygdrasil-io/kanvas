package org.skia.modules

import PropertyT
import SkSVGViewBoxType
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import kotlin.collections.List
import org.skia.core.TArray
import org.skia.foundation.SkColor
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkPath
import org.skia.foundation.SkUnichar
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.Func
import undefined.SkSVGColorType
import undefined.SkSVGNumberType
import undefined.SkSVGStringType
import undefined.SkSVGTransformType

/**
 * C++ original:
 * ```cpp
 * class SkSVGAttributeParser : public SkNoncopyable {
 * public:
 *     explicit SkSVGAttributeParser(const char[]);
 *
 *     bool parseInteger(SkSVGIntegerType*);
 *     bool parseViewBox(SkSVGViewBoxType*);
 *     bool parsePreserveAspectRatio(SkSVGPreserveAspectRatio*);
 *
 *     // TODO: Migrate all parse*() functions to this style (and delete the old version)
 *     //      so they can be used by parse<T>():
 *     bool parse(SkSVGIntegerType* v) { return parseInteger(v); }
 *
 *     template <typename T> using ParseResult = std::optional<T>;
 *
 *     template <typename T> static ParseResult<T> parse(const char* value) {
 *         T parsedValue;
 *         if (SkSVGAttributeParser(value).parse(&parsedValue)) {
 *             return parsedValue;
 *         }
 *         return {};
 *     }
 *
 *     template <typename T>
 *     static ParseResult<T> parse(const char* expectedName,
 *                                 const char* name,
 *                                 const char* value) {
 *         if (!strcmp(name, expectedName)) {
 *             return parse<T>(value);
 *         }
 *
 *         return ParseResult<T>();
 *     }
 *
 *     template <typename PropertyT>
 *     static ParseResult<PropertyT> parseProperty(const char* expectedName,
 *                                                 const char* name,
 *                                                 const char* value) {
 *         if (strcmp(name, expectedName) != 0) {
 *             return ParseResult<PropertyT>();
 *         }
 *
 *         if (!strcmp(value, "inherit")) {
 *             return PropertyT(SkSVGPropertyState::kInherit);
 *         }
 *
 *         auto pr = parse<typename PropertyT::ValueT>(value);
 *         if (pr.has_value()) {
 *             return PropertyT(*pr);
 *         }
 *
 *         return ParseResult<PropertyT>();
 *     }
 *
 * private:
 *     class RestoreCurPos {
 *     public:
 *         explicit RestoreCurPos(SkSVGAttributeParser* self)
 *             : fSelf(self), fCurPos(self->fCurPos) {}
 *
 *         ~RestoreCurPos() {
 *             if (fSelf) {
 *                 fSelf->fCurPos = this->fCurPos;
 *             }
 *         }
 *
 *         void clear() { fSelf = nullptr; }
 *     private:
 *         SkSVGAttributeParser* fSelf;
 *         const char* fCurPos;
 *
 *         RestoreCurPos(           const RestoreCurPos&) = delete;
 *         RestoreCurPos& operator=(const RestoreCurPos&) = delete;
 *     };
 *
 *     // Stack-only
 *     void* operator new(size_t) = delete;
 *     void* operator new(size_t, void*) = delete;
 *
 *     template <typename T>
 *     bool parse(T*);
 *
 *     template <typename F>
 *     bool advanceWhile(F func);
 *
 *     bool matchStringToken(const char* token, const char** newPos = nullptr) const;
 *     bool matchHexToken(const char** newPos) const;
 *
 *     bool parseWSToken();
 *     bool parseEOSToken();
 *     bool parseSepToken();
 *     bool parseCommaWspToken();
 *     bool parseExpectedStringToken(const char*);
 *     bool parseScalarToken(SkScalar*);
 *     bool parseInt32Token(int32_t*);
 *     bool parseEscape(SkUnichar*);
 *     bool parseIdentToken(SkString*);
 *     bool parseLengthUnitToken(SkSVGLength::Unit*);
 *     bool parseNamedColorToken(SkColor*);
 *     bool parseHexColorToken(SkColor*);
 *     bool parseColorComponentScalarToken(int32_t*);
 *     bool parseColorComponentIntegralToken(int32_t*);
 *     bool parseColorComponentFractionalToken(int32_t*);
 *     bool parseColorComponentToken(int32_t*);
 *     bool parseColorToken(SkColor*);
 *     bool parseRGBColorToken(SkColor*);
 *     bool parseRGBAColorToken(SkColor*);
 *     bool parseSVGColor(SkSVGColor*, SkSVGColor::Vars&&);
 *     bool parseSVGColorType(SkSVGColorType*);
 *     bool parseFuncIRI(SkSVGFuncIRI*);
 *
 *     // Transform helpers
 *     bool parseMatrixToken(SkMatrix*);
 *     bool parseTranslateToken(SkMatrix*);
 *     bool parseScaleToken(SkMatrix*);
 *     bool parseRotateToken(SkMatrix*);
 *     bool parseSkewXToken(SkMatrix*);
 *     bool parseSkewYToken(SkMatrix*);
 *
 *     // Parses a sequence of 'WS* <prefix> WS* (<nested>)', where the nested sequence
 *     // is handled by the passed functor.
 *     template <typename Func, typename T>
 *     bool parseParenthesized(const char* prefix, Func, T* result);
 *
 *     template <typename T>
 *     bool parseList(std::vector<T>*);
 *
 *     template <typename T, typename TArray>
 *     bool parseEnumMap(const TArray& arr, T* result) {
 *         for (size_t i = 0; i < std::size(arr); ++i) {
 *             if (this->parseExpectedStringToken(std::get<0>(arr[i]))) {
 *                 *result = std::get<1>(arr[i]);
 *                 return true;
 *             }
 *         }
 *         return false;
 *     }
 *
 *     // The current position in the input string.
 *     const char* fCurPos;
 *     const char* fEndPos;
 *
 *     using INHERITED = SkNoncopyable;
 * }
 * ```
 */
public open class SkSVGAttributeParser public constructor(
  attributeString: CharArray,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const char* fCurPos
   * ```
   */
  private val fCurPos: String? = TODO("Initialize fCurPos")

  /**
   * C++ original:
   * ```cpp
   * const char* fEndPos
   * ```
   */
  private val fEndPos: String? = TODO("Initialize fEndPos")

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseInteger(SkSVGIntegerType* number) {
   *     // consume WS
   *     this->parseWSToken();
   *
   *     // consume optional '+'
   *     this->parseExpectedStringToken("+");
   *
   *     SkSVGIntegerType i;
   *     if (this->parseInt32Token(&i)) {
   *         *number = SkSVGNumberType(i);
   *         // consume trailing separators
   *         this->parseSepToken();
   *         return true;
   *     }
   *
   *     return false;
   * }
   * ```
   */
  public fun parseInteger(number: SkSVGIntegerType?): Boolean {
    TODO("Implement parseInteger")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseViewBox(SkSVGViewBoxType* vb) {
   *     SkScalar x, y, w, h;
   *     this->parseWSToken();
   *
   *     bool parsedValue = false;
   *     if (this->parseScalarToken(&x) && this->parseSepToken() &&
   *         this->parseScalarToken(&y) && this->parseSepToken() &&
   *         this->parseScalarToken(&w) && this->parseSepToken() &&
   *         this->parseScalarToken(&h)) {
   *
   *         *vb = SkSVGViewBoxType(SkRect::MakeXYWH(x, y, w, h));
   *         parsedValue = true;
   *         // consume trailing whitespace
   *         this->parseWSToken();
   *     }
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parseViewBox(vb: SkSVGViewBoxType?): Boolean {
    TODO("Implement parseViewBox")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parsePreserveAspectRatio(SkSVGPreserveAspectRatio* par) {
   *     static constexpr std::tuple<const char*, SkSVGPreserveAspectRatio::Align> gAlignMap[] = {
   *         { "none"    , SkSVGPreserveAspectRatio::kNone     },
   *         { "xMinYMin", SkSVGPreserveAspectRatio::kXMinYMin },
   *         { "xMidYMin", SkSVGPreserveAspectRatio::kXMidYMin },
   *         { "xMaxYMin", SkSVGPreserveAspectRatio::kXMaxYMin },
   *         { "xMinYMid", SkSVGPreserveAspectRatio::kXMinYMid },
   *         { "xMidYMid", SkSVGPreserveAspectRatio::kXMidYMid },
   *         { "xMaxYMid", SkSVGPreserveAspectRatio::kXMaxYMid },
   *         { "xMinYMax", SkSVGPreserveAspectRatio::kXMinYMax },
   *         { "xMidYMax", SkSVGPreserveAspectRatio::kXMidYMax },
   *         { "xMaxYMax", SkSVGPreserveAspectRatio::kXMaxYMax },
   *     };
   *
   *     static constexpr std::tuple<const char*, SkSVGPreserveAspectRatio::Scale> gScaleMap[] = {
   *         { "meet" , SkSVGPreserveAspectRatio::kMeet  },
   *         { "slice", SkSVGPreserveAspectRatio::kSlice },
   *     };
   *
   *     bool parsedValue = false;
   *
   *     // ignoring optional 'defer'
   *     this->parseExpectedStringToken("defer");
   *     this->parseWSToken();
   *
   *     if (this->parseEnumMap(gAlignMap, &par->fAlign)) {
   *         parsedValue = true;
   *
   *         // optional scaling selector
   *         this->parseWSToken();
   *         this->parseEnumMap(gScaleMap, &par->fScale);
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parsePreserveAspectRatio(par: SkSVGPreserveAspectRatio?): Boolean {
    TODO("Implement parsePreserveAspectRatio")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGPointsType* points) {
   *     SkSVGPointsType pts;
   *
   *     // Skip initial wsp.
   *     // list-of-points:
   *     //     wsp* coordinate-pairs? wsp*
   *     this->advanceWhile(is_ws);
   *
   *     bool parsedValue = false;
   *     for (;;) {
   *         // Adjacent coordinate-pairs separated by comma-wsp.
   *         // coordinate-pairs:
   *         //     coordinate-pair
   *         //     | coordinate-pair comma-wsp coordinate-pairs
   *         if (parsedValue && !this->parseCommaWspToken()) {
   *             break;
   *         }
   *
   *         SkScalar x, y;
   *         if (!this->parseScalarToken(&x)) {
   *             break;
   *         }
   *
   *         // Coordinate values separated by comma-wsp or '-'.
   *         // coordinate-pair:
   *         //     coordinate comma-wsp coordinate
   *         //     | coordinate negative-coordinate
   *         if (!this->parseCommaWspToken() && !this->parseEOSToken() && *fCurPos != '-') {
   *             break;
   *         }
   *
   *         if (!this->parseScalarToken(&y)) {
   *             break;
   *         }
   *
   *         pts.push_back(SkPoint::Make(x, y));
   *         parsedValue = true;
   *     }
   *
   *     if (parsedValue && this->parseEOSToken()) {
   *         *points = std::move(pts);
   *         return true;
   *     }
   *
   *     return false;
   * }
   * ```
   */
  public fun parse(v: SkSVGIntegerType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t) = delete
   * ```
   */
  private fun toNew(param0: ULong) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t, void*) = delete
   * ```
   */
  private fun toNew(param0: ULong, param1: Unit?) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool parse(T*)
   * ```
   */
  private fun <T> parse(param0: T?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename F>
   * inline bool SkSVGAttributeParser::advanceWhile(F f) {
   *     auto initial = fCurPos;
   *     while (fCurPos < fEndPos && f(*fCurPos)) {
   *         fCurPos++;
   *     }
   *     return fCurPos != initial;
   * }
   * ```
   */
  private fun <F> advanceWhile(func: F): Boolean {
    TODO("Implement advanceWhile")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::matchStringToken(const char* token, const char** newPos) const {
   *     const char* c = fCurPos;
   *
   *     while (c < fEndPos && *token && *c == *token) {
   *         c++;
   *         token++;
   *     }
   *
   *     if (*token) {
   *         return false;
   *     }
   *
   *     if (newPos) {
   *         *newPos = c;
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun matchStringToken(token: String?, newPos: Int? = TODO()): Boolean {
    TODO("Implement matchStringToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::matchHexToken(const char** newPos) const {
   *     *newPos = fCurPos;
   *     while (*newPos < fEndPos && is_hex(**newPos)) { ++*newPos; }
   *     return *newPos != fCurPos;
   * }
   * ```
   */
  private fun matchHexToken(newPos: Int?): Boolean {
    TODO("Implement matchHexToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseWSToken() {
   *     return this->advanceWhile(is_ws);
   * }
   * ```
   */
  private fun parseWSToken(): Boolean {
    TODO("Implement parseWSToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseEOSToken() {
   *     return fCurPos == fEndPos;
   * }
   * ```
   */
  private fun parseEOSToken(): Boolean {
    TODO("Implement parseEOSToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseSepToken() {
   *     return this->advanceWhile(is_sep);
   * }
   * ```
   */
  private fun parseSepToken(): Boolean {
    TODO("Implement parseSepToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseCommaWspToken() {
   *     // comma-wsp:
   *     //     (wsp+ comma? wsp*) | (comma wsp*)
   *     return this->parseWSToken() || this->parseExpectedStringToken(",");
   * }
   * ```
   */
  private fun parseCommaWspToken(): Boolean {
    TODO("Implement parseCommaWspToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseExpectedStringToken(const char* expected) {
   *     const char* newPos;
   *     if (!matchStringToken(expected, &newPos)) {
   *         return false;
   *     }
   *
   *     fCurPos = newPos;
   *     return true;
   * }
   * ```
   */
  private fun parseExpectedStringToken(expected: String?): Boolean {
    TODO("Implement parseExpectedStringToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseScalarToken(SkScalar* res) {
   *     if (const char* next = SkParse::FindScalar(fCurPos, res)) {
   *         fCurPos = next;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun parseScalarToken(res: SkScalar?): Boolean {
    TODO("Implement parseScalarToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseInt32Token(int32_t* res) {
   *     if (const char* next = SkParse::FindS32(fCurPos, res)) {
   *         fCurPos = next;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun parseInt32Token(res: Int?): Boolean {
    TODO("Implement parseInt32Token")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseEscape(SkUnichar* c) {
   *     // \(hexDigit{1,6}whitespace?|[^newline|hexDigit])
   *     RestoreCurPos restoreCurPos(this);
   *
   *     if (!this->parseExpectedStringToken("\\")) {
   *         return false;
   *     }
   *     const char* hexEnd;
   *     if (this->matchHexToken(&hexEnd)) {
   *         if (hexEnd - fCurPos > 6) {
   *             hexEnd = fCurPos + 6;
   *         }
   *         char hexString[7];
   *         size_t hexSize = hexEnd - fCurPos;
   *         memcpy(hexString, fCurPos, hexSize);
   *         hexString[hexSize] = '\0';
   *         uint32_t cp;
   *         const char* hexFound = SkParse::FindHex(hexString, &cp);
   *         if (!hexFound || cp < 1 || (0xD800 <= cp && cp <= 0xDFFF) || 0x10FFFF < cp) {
   *             cp = 0xFFFD;
   *         }
   *         *c = cp;
   *         fCurPos = hexEnd;
   *         this->parseWSToken();
   *     } else if (this->parseEOSToken() || is_nl(*fCurPos)) {
   *         *c = 0xFFFD;
   *         return false;
   *     } else {
   *         if ((*c = SkUTF::NextUTF8(&fCurPos, fEndPos)) < 0) {
   *             return false;
   *         }
   *     }
   *
   *     restoreCurPos.clear();
   *     return true;
   * }
   * ```
   */
  private fun parseEscape(c: SkUnichar?): Boolean {
    TODO("Implement parseEscape")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseIdentToken(SkString* ident) {
   *     // <ident-token>
   *     // (--|-?([a-z|A-Z|_|non-ASCII]|escape))([a-z|A-Z|0-9|_|-|non-ASCII]|escape)?
   *     RestoreCurPos restoreCurPos(this);
   *
   *     SkUnichar c;
   *     if (this->parseExpectedStringToken("--")) {
   *         ident->append("--");
   *     } else {
   *         if (this->parseExpectedStringToken("-")) {
   *             ident->append("-");
   *         }
   *         if (this->parseEscape(&c)) {
   *             ident->appendUnichar(c);
   *         } else {
   *             if ((c = SkUTF::NextUTF8(&fCurPos, fEndPos)) < 0) {
   *                 return false;
   *             }
   *             if ((c < 'a' || 'z' < c) &&
   *                 (c < 'A' || 'Z' < c) &&
   *                 (c != '_') &&
   *                 (c < 0x80 || 0x10FFFF < c))
   *             {
   *                 return false;
   *             }
   *             ident->appendUnichar(c);
   *         }
   *     }
   *     while (fCurPos < fEndPos) {
   *         if (this->parseEscape(&c)) {
   *             ident->appendUnichar(c);
   *             continue;
   *         }
   *         const char* next = fCurPos;
   *         if ((c = SkUTF::NextUTF8(&next, fEndPos)) < 0) {
   *             break;
   *         }
   *         if ((c < 'a' || 'z' < c) &&
   *             (c < 'A' || 'Z' < c) &&
   *             (c < '0' || '9' < c) &&
   *             (c != '_') &&
   *             (c != '-') &&
   *             (c < 0x80 || 0x10FFFF < c))
   *         {
   *             break;
   *         }
   *         ident->appendUnichar(c);
   *         fCurPos = next;
   *     }
   *
   *     restoreCurPos.clear();
   *     return true;
   * }
   * ```
   */
  private fun parseIdentToken(ident: String?): Boolean {
    TODO("Implement parseIdentToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseLengthUnitToken(SkSVGLength::Unit* unit) {
   *     static const struct {
   *         const char*       fUnitName;
   *         SkSVGLength::Unit fUnit;
   *     } gUnitInfo[] = {
   *         { "%" , SkSVGLength::Unit::kPercentage },
   *         { "em", SkSVGLength::Unit::kEMS        },
   *         { "ex", SkSVGLength::Unit::kEXS        },
   *         { "px", SkSVGLength::Unit::kPX         },
   *         { "cm", SkSVGLength::Unit::kCM         },
   *         { "mm", SkSVGLength::Unit::kMM         },
   *         { "in", SkSVGLength::Unit::kIN         },
   *         { "pt", SkSVGLength::Unit::kPT         },
   *         { "pc", SkSVGLength::Unit::kPC         },
   *     };
   *
   *     for (size_t i = 0; i < std::size(gUnitInfo); ++i) {
   *         if (this->parseExpectedStringToken(gUnitInfo[i].fUnitName)) {
   *             *unit = gUnitInfo[i].fUnit;
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  private fun parseLengthUnitToken(unit: SkSVGLength.Unit?): Boolean {
    TODO("Implement parseLengthUnitToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseNamedColorToken(SkColor* c) {
   *     RestoreCurPos restoreCurPos(this);
   *
   *     SkString ident;
   *     if (!this->parseIdentToken(&ident)) {
   *         return false;
   *     }
   *     if (!SkParse::FindNamedColor(ident.c_str(), ident.size(), c)) {
   *         return false;
   *     }
   *
   *     restoreCurPos.clear();
   *     return true;
   * }
   * ```
   */
  private fun parseNamedColorToken(c: SkColor?): Boolean {
    TODO("Implement parseNamedColorToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseHexColorToken(SkColor* c) {
   *     RestoreCurPos restoreCurPos(this);
   *
   *     const char* hexEnd;
   *     if (!this->parseExpectedStringToken("#") || !this->matchHexToken(&hexEnd)) {
   *         return false;
   *     }
   *
   *     uint32_t v;
   *     SkString hexString(fCurPos, hexEnd - fCurPos);
   *     SkParse::FindHex(hexString.c_str(), &v);
   *
   *     switch (hexString.size()) {
   *     case 6:
   *         // matched #xxxxxxx
   *         break;
   *     case 3:
   *         // matched '#xxx;
   *         v = ((v << 12) & 0x00f00000) |
   *             ((v <<  8) & 0x000ff000) |
   *             ((v <<  4) & 0x00000ff0) |
   *             ((v <<  0) & 0x0000000f);
   *         break;
   *     default:
   *         return false;
   *     }
   *
   *     *c = v | 0xff000000;
   *     fCurPos = hexEnd;
   *
   *     restoreCurPos.clear();
   *     return true;
   * }
   * ```
   */
  private fun parseHexColorToken(c: SkColor?): Boolean {
    TODO("Implement parseHexColorToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseColorComponentScalarToken(int32_t* c) {
   *     SkScalar s;
   *     if (const char* p = SkParse::FindScalar(fCurPos, &s)) {
   *         *c = SkScalarRoundToInt(s * 255.0f);
   *         *c = SkTPin<int32_t>(*c, 0, 255);
   *         fCurPos = p;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun parseColorComponentScalarToken(c: Int?): Boolean {
    TODO("Implement parseColorComponentScalarToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseColorComponentIntegralToken(int32_t* c) {
   *     const char* p = SkParse::FindS32(fCurPos, c);
   *     if (!p || *p == '.') {
   *         // No value parsed, or fractional value.
   *         return false;
   *     }
   *
   *     if (*p == '%') {
   *         *c = SkScalarRoundToInt(*c * 255.0f / 100);
   *         *c = SkTPin<int32_t>(*c, 0, 255);
   *         p++;
   *     }
   *
   *     fCurPos = p;
   *     return true;
   * }
   * ```
   */
  private fun parseColorComponentIntegralToken(c: Int?): Boolean {
    TODO("Implement parseColorComponentIntegralToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseColorComponentFractionalToken(int32_t* c) {
   *     SkScalar s;
   *     const char* p = SkParse::FindScalar(fCurPos, &s);
   *     if (!p || *p != '%') {
   *         // Floating point must be a percentage (CSS2 rgb-percent syntax).
   *         return false;
   *     }
   *     p++;  // Skip '%'
   *
   *     *c = SkScalarRoundToInt(s * 255.0f / 100);
   *     *c = SkTPin<int32_t>(*c, 0, 255);
   *     fCurPos = p;
   *     return true;
   * }
   * ```
   */
  private fun parseColorComponentFractionalToken(c: Int?): Boolean {
    TODO("Implement parseColorComponentFractionalToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseColorComponentToken(int32_t* c) {
   *     return parseColorComponentIntegralToken(c) ||
   *            parseColorComponentFractionalToken(c);
   * }
   * ```
   */
  private fun parseColorComponentToken(c: Int?): Boolean {
    TODO("Implement parseColorComponentToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseColorToken(SkColor* c) {
   *     return this->parseHexColorToken(c) ||
   *            this->parseNamedColorToken(c) ||
   *            this->parseRGBAColorToken(c) ||
   *            this->parseRGBColorToken(c);
   * }
   * ```
   */
  private fun parseColorToken(c: SkColor?): Boolean {
    TODO("Implement parseColorToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseRGBColorToken(SkColor* c) {
   *     return this->parseParenthesized("rgb", [this](SkColor* c) -> bool {
   *         int32_t r, g, b;
   *         if (this->parseColorComponentToken(&r) &&
   *             this->parseSepToken() &&
   *             this->parseColorComponentToken(&g) &&
   *             this->parseSepToken() &&
   *             this->parseColorComponentToken(&b)) {
   *
   *             *c = SkColorSetRGB(static_cast<uint8_t>(r),
   *                                static_cast<uint8_t>(g),
   *                                static_cast<uint8_t>(b));
   *             return true;
   *         }
   *         return false;
   *     }, c);
   * }
   * ```
   */
  private fun parseRGBColorToken(c: SkColor?): Boolean {
    TODO("Implement parseRGBColorToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseRGBAColorToken(SkColor* c) {
   *     return this->parseParenthesized("rgba", [this](SkColor* c) -> bool {
   *         int32_t r, g, b, a;
   *         if (this->parseColorComponentToken(&r) &&
   *             this->parseSepToken() &&
   *             this->parseColorComponentToken(&g) &&
   *             this->parseSepToken() &&
   *             this->parseColorComponentToken(&b) &&
   *             this->parseSepToken() &&
   *             this->parseColorComponentScalarToken(&a)) {
   *
   *             *c = SkColorSetARGB(static_cast<uint8_t>(a),
   *                                 static_cast<uint8_t>(r),
   *                                 static_cast<uint8_t>(g),
   *                                 static_cast<uint8_t>(b));
   *             return true;
   *         }
   *         return false;
   *     }, c);
   * }
   * ```
   */
  private fun parseRGBAColorToken(c: SkColor?): Boolean {
    TODO("Implement parseRGBAColorToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseSVGColor(SkSVGColor* color, SkSVGColor::Vars&& vars) {
   *     static const constexpr int kVarsLimit = 32;
   *
   *     if (SkSVGColorType c; this->parseSVGColorType(&c)) {
   *         *color = SkSVGColor(c, std::move(vars));
   *         return true;
   *     }
   *     if (this->parseExpectedStringToken("currentColor")) {
   *         *color = SkSVGColor(SkSVGColor::Type::kCurrentColor, std::move(vars));
   *         return true;
   *     }
   *     // https://drafts.csswg.org/css-variables/#using-variables
   *     if (this->parseParenthesized("var", [this, &vars](SkSVGColor* colorResult) -> bool {
   *             SkString ident;
   *             if (!this->parseIdentToken(&ident) || ident.size() < 2 || !ident.startsWith("--")) {
   *                 return false;
   *             }
   *             ident.remove(0, 2);
   *             vars.push_back(std::move(ident));
   *             this->parseWSToken();
   *             if (!this->parseExpectedStringToken(",")) {
   *                 *colorResult = SkSVGColor(SK_ColorBLACK, std::move(vars));
   *                 return true;
   *             }
   *             this->parseWSToken();
   *             if (this->matchStringToken(")")) {
   *                 *colorResult = SkSVGColor(SK_ColorBLACK, std::move(vars));
   *                 return true;
   *             }
   *             return vars.size() < kVarsLimit && this->parseSVGColor(colorResult, std::move(vars));
   *         }, color))
   *     {
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun parseSVGColor(color: SkSVGColor?, vars: SkSVGColor.Vars): Boolean {
    TODO("Implement parseSVGColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseSVGColorType(SkSVGColorType* color) {
   *     SkColor c;
   *     if (!this->parseColorToken(&c)) {
   *         return false;
   *     }
   *     *color = SkSVGColorType(c);
   *     return true;
   * }
   * ```
   */
  private fun parseSVGColorType(color: SkSVGColorType?): Boolean {
    TODO("Implement parseSVGColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseFuncIRI(SkSVGFuncIRI* iri) {
   *     return this->parseParenthesized("url", [this](SkSVGFuncIRI* iriResult) -> bool {
   *         SkSVGIRI iri;
   *         if (this->parse(&iri)) {
   *             *iriResult = SkSVGFuncIRI(std::move(iri));
   *             return true;
   *         }
   *         return false;
   *     }, iri);
   * }
   * ```
   */
  private fun parseFuncIRI(iri: SkSVGFuncIRI?): Boolean {
    TODO("Implement parseFuncIRI")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseMatrixToken(SkMatrix* matrix) {
   *     return this->parseParenthesized("matrix", [this](SkMatrix* m) -> bool {
   *         SkScalar scalars[6];
   *         for (int i = 0; i < 6; ++i) {
   *             if (!(this->parseScalarToken(scalars + i) &&
   *                   (i > 4 || this->parseSepToken()))) {
   *                 return false;
   *             }
   *         }
   *
   *         m->setAll(scalars[0], scalars[2], scalars[4], scalars[1], scalars[3], scalars[5], 0, 0, 1);
   *         return true;
   *     }, matrix);
   * }
   * ```
   */
  private fun parseMatrixToken(matrix: SkMatrix?): Boolean {
    TODO("Implement parseMatrixToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseTranslateToken(SkMatrix* matrix) {
   *     return this->parseParenthesized("translate", [this](SkMatrix* m) -> bool {
   *         SkScalar tx = 0.0, ty = 0.0;
   *         this->parseWSToken();
   *         if (!this->parseScalarToken(&tx)) {
   *             return false;
   *         }
   *
   *         if (!this->parseSepToken() || !this->parseScalarToken(&ty)) {
   *             ty = 0.0;
   *         }
   *
   *         m->setTranslate(tx, ty);
   *         return true;
   *     }, matrix);
   * }
   * ```
   */
  private fun parseTranslateToken(matrix: SkMatrix?): Boolean {
    TODO("Implement parseTranslateToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseScaleToken(SkMatrix* matrix) {
   *     return this->parseParenthesized("scale", [this](SkMatrix* m) -> bool {
   *         SkScalar sx = 0.0, sy = 0.0;
   *         if (!this->parseScalarToken(&sx)) {
   *             return false;
   *         }
   *
   *         if (!(this->parseSepToken() && this->parseScalarToken(&sy))) {
   *             sy = sx;
   *         }
   *
   *         m->setScale(sx, sy);
   *         return true;
   *     }, matrix);
   * }
   * ```
   */
  private fun parseScaleToken(matrix: SkMatrix?): Boolean {
    TODO("Implement parseScaleToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseRotateToken(SkMatrix* matrix) {
   *     return this->parseParenthesized("rotate", [this](SkMatrix* m) -> bool {
   *         SkScalar angle;
   *         if (!this->parseScalarToken(&angle)) {
   *             return false;
   *         }
   *
   *         SkScalar cx = 0;
   *         SkScalar cy = 0;
   *         // optional [<cx> <cy>]
   *         if (this->parseSepToken() && this->parseScalarToken(&cx)) {
   *             if (!(this->parseSepToken() && this->parseScalarToken(&cy))) {
   *                 return false;
   *             }
   *         }
   *
   *         m->setRotate(angle, cx, cy);
   *         return true;
   *     }, matrix);
   * }
   * ```
   */
  private fun parseRotateToken(matrix: SkMatrix?): Boolean {
    TODO("Implement parseRotateToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseSkewXToken(SkMatrix* matrix) {
   *     return this->parseParenthesized("skewX", [this](SkMatrix* m) -> bool {
   *         SkScalar angle;
   *         if (!this->parseScalarToken(&angle)) {
   *             return false;
   *         }
   *         m->setSkewX(tanf(SkDegreesToRadians(angle)));
   *         return true;
   *     }, matrix);
   * }
   * ```
   */
  private fun parseSkewXToken(matrix: SkMatrix?): Boolean {
    TODO("Implement parseSkewXToken")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGAttributeParser::parseSkewYToken(SkMatrix* matrix) {
   *     return this->parseParenthesized("skewY", [this](SkMatrix* m) -> bool {
   *         SkScalar angle;
   *         if (!this->parseScalarToken(&angle)) {
   *             return false;
   *         }
   *         m->setSkewY(tanf(SkDegreesToRadians(angle)));
   *         return true;
   *     }, matrix);
   * }
   * ```
   */
  private fun parseSkewYToken(matrix: SkMatrix?): Boolean {
    TODO("Implement parseSkewYToken")
  }

  /**
   * C++ original:
   * ```cpp
   * template <typename Func, typename T>
   * bool SkSVGAttributeParser::parseParenthesized(const char* prefix, Func f, T* result) {
   *     RestoreCurPos restoreCurPos(this);
   *
   *     this->parseWSToken();
   *     if (prefix && !this->parseExpectedStringToken(prefix)) {
   *         return false;
   *     }
   *     this->parseWSToken();
   *     if (!this->parseExpectedStringToken("(")) {
   *         return false;
   *     }
   *     this->parseWSToken();
   *
   *     if (!f(result)) {
   *         return false;
   *     }
   *
   *     this->parseWSToken();
   *     if (!this->parseExpectedStringToken(")")) {
   *         return false;
   *     }
   *
   *     restoreCurPos.clear();
   *     return true;
   * }
   * ```
   */
  private fun <Func, T> parseParenthesized(
    prefix: String?,
    f: Func,
    result: T?,
  ): Boolean {
    TODO("Implement parseParenthesized")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool parseList(std::vector<T>*)
   * ```
   */
  private fun <T> parseList(param0: List<T>?): Boolean {
    TODO("Implement parseList")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T, typename TArray>
   *     bool parseEnumMap(const TArray& arr, T* result) {
   *         for (size_t i = 0; i < std::size(arr); ++i) {
   *             if (this->parseExpectedStringToken(std::get<0>(arr[i]))) {
   *                 *result = std::get<1>(arr[i]);
   *                 return true;
   *             }
   *         }
   *         return false;
   *     }
   * ```
   */
  private fun <T, TArray> parseEnumMap(arr: TArray, result: T?): Boolean {
    TODO("Implement parseEnumMap")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGColorType* color) {
   *     this->parseWSToken();
   *     if (!this->parseSVGColorType(color)) {
   *         return false;
   *     }
   *     this->parseWSToken();
   *     return this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(color: SkSVGColorType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGColor* color) {
   *     this->parseWSToken();
   *     if (!this->parseSVGColor(color, SkSVGColor::Vars())) {
   *         return false;
   *     }
   *     this->parseWSToken();
   *     return this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(color: SkSVGColor?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGIRI* iri) {
   *     // consume preceding whitespace
   *     this->parseWSToken();
   *
   *     SkSVGIRI::Type iriType;
   *     if (this->parseExpectedStringToken("#")) {
   *         iriType = SkSVGIRI::Type::kLocal;
   *     } else if (this->matchStringToken("data:")) {
   *         iriType = SkSVGIRI::Type::kDataURI;
   *     } else {
   *         iriType = SkSVGIRI::Type::kNonlocal;
   *     }
   *
   *     const auto* start = fCurPos;
   *     if (!this->advanceWhile([](char c) -> bool { return c != ')'; })) {
   *         return false;
   *     }
   *     *iri = SkSVGIRI(iriType, SkString(start, fCurPos - start));
   *     return true;
   * }
   * ```
   */
  public fun parse(iri: SkSVGIRI?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGStringType* result) {
   *     if (this->parseEOSToken()) {
   *         return false;
   *     }
   *     *result = SkSVGStringType(fCurPos);
   *     fCurPos += result->size();
   *     return this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(result: SkSVGStringType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGNumberType* number) {
   *     // consume WS
   *     this->parseWSToken();
   *
   *     SkScalar s;
   *     if (this->parseScalarToken(&s)) {
   *         *number = SkSVGNumberType(s);
   *         // consume trailing separators
   *         this->parseSepToken();
   *         return true;
   *     }
   *
   *     return false;
   * }
   * ```
   */
  public fun parse(number: SkSVGNumberType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGLength* length) {
   *     SkScalar s;
   *     SkSVGLength::Unit u = SkSVGLength::Unit::kNumber;
   *
   *     if (this->parseScalarToken(&s) &&
   *         (this->parseLengthUnitToken(&u) || this->parseSepToken() || this->parseEOSToken())) {
   *         *length = SkSVGLength(s, u);
   *         // consume trailing separators
   *         this->parseSepToken();
   *         return true;
   *     }
   *
   *     return false;
   * }
   * ```
   */
  public fun parse(length: SkSVGLength?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGTransformType* t) {
   *     SkMatrix matrix = SkMatrix::I();
   *
   *     bool parsed = false;
   *     while (true) {
   *         SkMatrix m;
   *
   *         if (!( this->parseMatrixToken(&m)
   *             || this->parseTranslateToken(&m)
   *             || this->parseScaleToken(&m)
   *             || this->parseRotateToken(&m)
   *             || this->parseSkewXToken(&m)
   *             || this->parseSkewYToken(&m))) {
   *             break;
   *         }
   *
   *         matrix.preConcat(m);
   *         parsed = true;
   *
   *         this->parseCommaWspToken();
   *     }
   *
   *     this->parseWSToken();
   *     if (!parsed || !this->parseEOSToken()) {
   *         return false;
   *     }
   *
   *     *t = SkSVGTransformType(matrix);
   *     return true;
   * }
   * ```
   */
  public fun parse(t: SkSVGTransformType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGPaint* paint) {
   *     SkSVGColor c;
   *     SkSVGFuncIRI iri;
   *     bool parsedValue = false;
   *
   *     this->parseWSToken();
   *     if (this->parseSVGColor(&c, SkSVGColor::Vars())) {
   *         *paint = SkSVGPaint(std::move(c));
   *         parsedValue = true;
   *     } else if (this->parseExpectedStringToken("none")) {
   *         *paint = SkSVGPaint(SkSVGPaint::Type::kNone);
   *         parsedValue = true;
   *     } else if (this->parseFuncIRI(&iri)) {
   *         // optional fallback color
   *         this->parseWSToken();
   *         this->parseSVGColor(&c, SkSVGColor::Vars());
   *         *paint = SkSVGPaint(iri.iri(), std::move(c));
   *         parsedValue = true;
   *     }
   *     this->parseWSToken();
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(paint: SkSVGPaint?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFuncIRI* firi) {
   *     SkSVGStringType iri;
   *     bool parsedValue = false;
   *
   *     if (this->parseExpectedStringToken("none")) {
   *         *firi = SkSVGFuncIRI();
   *         parsedValue = true;
   *     } else if (this->parseFuncIRI(firi)) {
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(firi: SkSVGFuncIRI?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGLineCap* cap) {
   *     static const struct {
   *         SkSVGLineCap fType;
   *         const char*        fName;
   *     } gCapInfo[] = {
   *         { SkSVGLineCap::kButt   , "butt"    },
   *         { SkSVGLineCap::kRound  , "round"   },
   *         { SkSVGLineCap::kSquare , "square"  },
   *     };
   *
   *     bool parsedValue = false;
   *     for (size_t i = 0; i < std::size(gCapInfo); ++i) {
   *         if (this->parseExpectedStringToken(gCapInfo[i].fName)) {
   *             *cap = SkSVGLineCap(gCapInfo[i].fType);
   *             parsedValue = true;
   *             break;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(cap: SkSVGLineCap?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGLineJoin* join) {
   *     static const struct {
   *         SkSVGLineJoin::Type fType;
   *         const char*         fName;
   *     } gJoinInfo[] = {
   *         { SkSVGLineJoin::Type::kMiter  , "miter"   },
   *         { SkSVGLineJoin::Type::kRound  , "round"   },
   *         { SkSVGLineJoin::Type::kBevel  , "bevel"   },
   *         { SkSVGLineJoin::Type::kInherit, "inherit" },
   *     };
   *
   *     bool parsedValue = false;
   *     for (size_t i = 0; i < std::size(gJoinInfo); ++i) {
   *         if (this->parseExpectedStringToken(gJoinInfo[i].fName)) {
   *             *join = SkSVGLineJoin(gJoinInfo[i].fType);
   *             parsedValue = true;
   *             break;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(join: SkSVGLineJoin?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGObjectBoundingBoxUnits* objectBoundingBoxUnits) {
   *     bool parsedValue = false;
   *     if (this->parseExpectedStringToken("userSpaceOnUse")) {
   *         *objectBoundingBoxUnits =
   *                 SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kUserSpaceOnUse);
   *         parsedValue = true;
   *     } else if (this->parseExpectedStringToken("objectBoundingBox")) {
   *         *objectBoundingBoxUnits =
   *                 SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox);
   *         parsedValue = true;
   *     }
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(objectBoundingBoxUnits: SkSVGObjectBoundingBoxUnits?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFillRule* fillRule) {
   *     static const struct {
   *         SkSVGFillRule::Type fType;
   *         const char*         fName;
   *     } gFillRuleInfo[] = {
   *         { SkSVGFillRule::Type::kNonZero, "nonzero" },
   *         { SkSVGFillRule::Type::kEvenOdd, "evenodd" },
   *         { SkSVGFillRule::Type::kInherit, "inherit" },
   *     };
   *
   *     bool parsedValue = false;
   *     for (size_t i = 0; i < std::size(gFillRuleInfo); ++i) {
   *         if (this->parseExpectedStringToken(gFillRuleInfo[i].fName)) {
   *             *fillRule = SkSVGFillRule(gFillRuleInfo[i].fType);
   *             parsedValue = true;
   *             break;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(fillRule: SkSVGFillRule?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGVisibility* visibility) {
   *     static const struct {
   *         SkSVGVisibility::Type fType;
   *         const char*           fName;
   *     } gVisibilityInfo[] = {
   *         { SkSVGVisibility::Type::kVisible , "visible"  },
   *         { SkSVGVisibility::Type::kHidden  , "hidden"   },
   *         { SkSVGVisibility::Type::kCollapse, "collapse" },
   *         { SkSVGVisibility::Type::kInherit , "inherit"  },
   *     };
   *
   *     bool parsedValue = false;
   *     for (const auto& parseInfo : gVisibilityInfo) {
   *         if (this->parseExpectedStringToken(parseInfo.fName)) {
   *             *visibility = SkSVGVisibility(parseInfo.fType);
   *             parsedValue = true;
   *             break;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(visibility: SkSVGVisibility?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGDashArray* dashArray) {
   *     bool parsedValue = false;
   *     if (this->parseExpectedStringToken("none")) {
   *         *dashArray = SkSVGDashArray(SkSVGDashArray::Type::kNone);
   *         parsedValue = true;
   *     } else if (this->parseExpectedStringToken("inherit")) {
   *         *dashArray = SkSVGDashArray(SkSVGDashArray::Type::kInherit);
   *         parsedValue = true;
   *     } else {
   *         std::vector<SkSVGLength> dashes;
   *         for (;;) {
   *             SkSVGLength dash;
   *             // parseLength() also consumes trailing separators.
   *             if (!this->parse(&dash)) {
   *                 break;
   *             }
   *
   *             dashes.push_back(dash);
   *             parsedValue = true;
   *         }
   *
   *         if (parsedValue) {
   *             *dashArray = SkSVGDashArray(std::move(dashes));
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(dashArray: SkSVGDashArray?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFontFamily* family) {
   *     bool parsedValue = false;
   *     if (this->parseExpectedStringToken("inherit")) {
   *         *family = SkSVGFontFamily();
   *         parsedValue = true;
   *     } else {
   *         // The spec allows specifying a comma-separated list for explicit fallback order.
   *         // For now, we only use the first entry and rely on the font manager to handle fallback.
   *         const auto* comma = strchr(fCurPos, ',');
   *         auto family_name = comma ? SkString(fCurPos, comma - fCurPos)
   *                                  : SkString(fCurPos);
   *         *family = SkSVGFontFamily(family_name.c_str());
   *         fCurPos += strlen(fCurPos);
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(family: SkSVGFontFamily?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFontSize* size) {
   *     bool parsedValue = false;
   *     if (this->parseExpectedStringToken("inherit")) {
   *         *size = SkSVGFontSize();
   *         parsedValue = true;
   *     } else {
   *         SkSVGLength length;
   *         if (this->parse(&length)) {
   *             *size = SkSVGFontSize(length);
   *             parsedValue = true;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(size: SkSVGFontSize?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFontStyle* style) {
   *     static constexpr std::tuple<const char*, SkSVGFontStyle::Type> gStyleMap[] = {
   *         { "normal" , SkSVGFontStyle::Type::kNormal  },
   *         { "italic" , SkSVGFontStyle::Type::kItalic  },
   *         { "oblique", SkSVGFontStyle::Type::kOblique },
   *         { "inherit", SkSVGFontStyle::Type::kInherit },
   *     };
   *
   *     bool parsedValue = false;
   *     SkSVGFontStyle::Type type;
   *
   *     if (this->parseEnumMap(gStyleMap, &type)) {
   *         *style = SkSVGFontStyle(type);
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(style: SkSVGFontStyle?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFontWeight* weight) {
   *     static constexpr std::tuple<const char*, SkSVGFontWeight::Type> gWeightMap[] = {
   *         { "normal" , SkSVGFontWeight::Type::kNormal  },
   *         { "bold"   , SkSVGFontWeight::Type::kBold    },
   *         { "bolder" , SkSVGFontWeight::Type::kBolder  },
   *         { "lighter", SkSVGFontWeight::Type::kLighter },
   *         { "100"    , SkSVGFontWeight::Type::k100     },
   *         { "200"    , SkSVGFontWeight::Type::k200     },
   *         { "300"    , SkSVGFontWeight::Type::k300     },
   *         { "400"    , SkSVGFontWeight::Type::k400     },
   *         { "500"    , SkSVGFontWeight::Type::k500     },
   *         { "600"    , SkSVGFontWeight::Type::k600     },
   *         { "700"    , SkSVGFontWeight::Type::k700     },
   *         { "800"    , SkSVGFontWeight::Type::k800     },
   *         { "900"    , SkSVGFontWeight::Type::k900     },
   *         { "inherit", SkSVGFontWeight::Type::kInherit },
   *     };
   *
   *     bool parsedValue = false;
   *     SkSVGFontWeight::Type type;
   *
   *     if (this->parseEnumMap(gWeightMap, &type)) {
   *         *weight = SkSVGFontWeight(type);
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(weight: SkSVGFontWeight?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGTextAnchor* anchor) {
   *     static constexpr std::tuple<const char*, SkSVGTextAnchor::Type> gAnchorMap[] = {
   *         { "start"  , SkSVGTextAnchor::Type::kStart  },
   *         { "middle" , SkSVGTextAnchor::Type::kMiddle },
   *         { "end"    , SkSVGTextAnchor::Type::kEnd    },
   *         { "inherit", SkSVGTextAnchor::Type::kInherit},
   *     };
   *
   *     bool parsedValue = false;
   *     SkSVGTextAnchor::Type type;
   *
   *     if (this->parseEnumMap(gAnchorMap, &type)) {
   *         *anchor = SkSVGTextAnchor(type);
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(anchor: SkSVGTextAnchor?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGPreserveAspectRatio* par) {
   *     return this->parsePreserveAspectRatio(par);
   * }
   * ```
   */
  public fun parse(par: SkSVGPreserveAspectRatio?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGColorspace* colorspace) {
   *     static constexpr std::tuple<const char*, SkSVGColorspace> gColorspaceMap[] = {
   *         { "auto"     , SkSVGColorspace::kAuto      },
   *         { "sRGB"     , SkSVGColorspace::kSRGB      },
   *         { "linearRGB", SkSVGColorspace::kLinearRGB },
   *     };
   *
   *     return this->parseEnumMap(gColorspaceMap, colorspace) && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(colorspace: SkSVGColorspace?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGDisplay* display) {
   *     static const struct {
   *         SkSVGDisplay fType;
   *         const char*  fName;
   *     } gDisplayInfo[] = {
   *         { SkSVGDisplay::kInline, "inline" },
   *         { SkSVGDisplay::kNone  , "none"   },
   *     };
   *
   *     bool parsedValue = false;
   *     for (const auto& parseInfo : gDisplayInfo) {
   *         if (this->parseExpectedStringToken(parseInfo.fName)) {
   *             *display = SkSVGDisplay(parseInfo.fType);
   *             parsedValue = true;
   *             break;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(display: SkSVGDisplay?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <> bool SkSVGAttributeParser::parse(SkSVGFeInputType* type) {
   *     static constexpr std::tuple<const char*, SkSVGFeInputType::Type> gTypeMap[] = {
   *             {"SourceGraphic", SkSVGFeInputType::Type::kSourceGraphic},
   *             {"SourceAlpha", SkSVGFeInputType::Type::kSourceAlpha},
   *             {"BackgroundImage", SkSVGFeInputType::Type::kBackgroundImage},
   *             {"BackgroundAlpha", SkSVGFeInputType::Type::kBackgroundAlpha},
   *             {"FillPaint", SkSVGFeInputType::Type::kFillPaint},
   *             {"StrokePaint", SkSVGFeInputType::Type::kStrokePaint},
   *     };
   *
   *     SkSVGStringType resultId;
   *     SkSVGFeInputType::Type t;
   *     bool parsedValue = false;
   *     if (this->parseEnumMap(gTypeMap, &t)) {
   *         *type = SkSVGFeInputType(t);
   *         parsedValue = true;
   *     } else if (parse(&resultId)) {
   *         *type = SkSVGFeInputType(resultId);
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(type: SkSVGFeInputType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <> bool SkSVGAttributeParser::parse(SkSVGFeColorMatrixType* type) {
   *     static constexpr std::tuple<const char*, SkSVGFeColorMatrixType> gTypeMap[] = {
   *             {"matrix", SkSVGFeColorMatrixType::kMatrix},
   *             {"saturate", SkSVGFeColorMatrixType::kSaturate},
   *             {"hueRotate", SkSVGFeColorMatrixType::kHueRotate},
   *             {"luminanceToAlpha", SkSVGFeColorMatrixType::kLuminanceToAlpha},
   *     };
   *
   *     return this->parseEnumMap(gTypeMap, type) && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(type: SkSVGFeColorMatrixType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGFeFuncType* type) {
   *     static constexpr std::tuple<const char*, SkSVGFeFuncType> gTypeMap[] = {
   *             { "identity", SkSVGFeFuncType::kIdentity },
   *             { "table"   , SkSVGFeFuncType::kTable    },
   *             { "discrete", SkSVGFeFuncType::kDiscrete },
   *             { "linear"  , SkSVGFeFuncType::kLinear   },
   *             { "gamma"   , SkSVGFeFuncType::kGamma    },
   *     };
   *
   *     return this->parseEnumMap(gTypeMap, type) && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(type: SkSVGFeFuncType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <> bool SkSVGAttributeParser::parse(SkSVGFeCompositeOperator* op) {
   *     static constexpr std::tuple<const char*, SkSVGFeCompositeOperator> gOpMap[] = {
   *             {"over", SkSVGFeCompositeOperator::kOver},
   *             {"in", SkSVGFeCompositeOperator::kIn},
   *             {"out", SkSVGFeCompositeOperator::kOut},
   *             {"atop", SkSVGFeCompositeOperator::kAtop},
   *             {"xor", SkSVGFeCompositeOperator::kXor},
   *             {"arithmetic", SkSVGFeCompositeOperator::kArithmetic},
   *     };
   *
   *     return this->parseEnumMap(gOpMap, op) && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(op: SkSVGFeCompositeOperator?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse<SkSVGFeTurbulenceBaseFrequency>(
   *         SkSVGFeTurbulenceBaseFrequency* freq) {
   *     SkSVGNumberType freqX;
   *     if (!this->parse(&freqX)) {
   *         return false;
   *     }
   *
   *     SkSVGNumberType freqY;
   *     this->parseCommaWspToken();
   *     if (this->parse(&freqY)) {
   *         *freq = SkSVGFeTurbulenceBaseFrequency(freqX, freqY);
   *     } else {
   *         *freq = SkSVGFeTurbulenceBaseFrequency(freqX, freqX);
   *     }
   *
   *     return this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(freq: SkSVGFeTurbulenceBaseFrequency?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse<SkSVGFeTurbulenceType>(SkSVGFeTurbulenceType* type) {
   *     bool parsedValue = false;
   *
   *     if (this->parseExpectedStringToken("fractalNoise")) {
   *         *type = SkSVGFeTurbulenceType(SkSVGFeTurbulenceType::kFractalNoise);
   *         parsedValue = true;
   *     } else if (this->parseExpectedStringToken("turbulence")) {
   *         *type = SkSVGFeTurbulenceType(SkSVGFeTurbulenceType::kTurbulence);
   *         parsedValue = true;
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(type: SkSVGFeTurbulenceType?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGSpreadMethod* spread) {
   *     static const struct {
   *         SkSVGSpreadMethod::Type fType;
   *         const char*             fName;
   *     } gSpreadInfo[] = {
   *         { SkSVGSpreadMethod::Type::kPad    , "pad"     },
   *         { SkSVGSpreadMethod::Type::kReflect, "reflect" },
   *         { SkSVGSpreadMethod::Type::kRepeat , "repeat"  },
   *     };
   *
   *     bool parsedValue = false;
   *     for (size_t i = 0; i < std::size(gSpreadInfo); ++i) {
   *         if (this->parseExpectedStringToken(gSpreadInfo[i].fName)) {
   *             *spread = SkSVGSpreadMethod(gSpreadInfo[i].fType);
   *             parsedValue = true;
   *             break;
   *         }
   *     }
   *
   *     return parsedValue && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(spread: SkSVGSpreadMethod?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse<SkPath>(SkPath* path) {
   *     if (auto result = SkParsePath::FromSVGString(fCurPos)) {
   *         *path = *result;
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun parse(path: SkPath?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool SkSVGAttributeParser::parse(SkSVGXmlSpace* xs) {
   *     static constexpr std::tuple<const char*, SkSVGXmlSpace> gXmlSpaceMap[] = {
   *             {"default" , SkSVGXmlSpace::kDefault },
   *             {"preserve", SkSVGXmlSpace::kPreserve},
   *     };
   *
   *     return this->parseEnumMap(gXmlSpaceMap, xs) && this->parseEOSToken();
   * }
   * ```
   */
  public fun parse(xs: SkSVGXmlSpace?): Boolean {
    TODO("Implement parse")
  }

  public data class RestoreCurPos public constructor(
    private var fSelf: SkSVGAttributeParser?,
    private val fCurPos: String?,
  ) {
    public fun clear() {
      TODO("Implement clear")
    }

    private fun assign(param0: undefined.RestoreCurPos) {
      TODO("Implement assign")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static ParseResult<T> parse(const char* value) {
     *         T parsedValue;
     *         if (SkSVGAttributeParser(value).parse(&parsedValue)) {
     *             return parsedValue;
     *         }
     *         return {};
     *     }
     * ```
     */
    public fun <T> parse(`value`: String?): SkSVGAttributeParserParseResult<T> {
      TODO("Implement parse")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename T>
     *     static ParseResult<T> parse(const char* expectedName,
     *                                 const char* name,
     *                                 const char* value) {
     *         if (!strcmp(name, expectedName)) {
     *             return parse<T>(value);
     *         }
     *
     *         return ParseResult<T>();
     *     }
     * ```
     */
    public fun <T> parse(
      expectedName: String?,
      name: String?,
      `value`: String?,
    ): SkSVGAttributeParserParseResult<T> {
      TODO("Implement parse")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename PropertyT>
     *     static ParseResult<PropertyT> parseProperty(const char* expectedName,
     *                                                 const char* name,
     *                                                 const char* value) {
     *         if (strcmp(name, expectedName) != 0) {
     *             return ParseResult<PropertyT>();
     *         }
     *
     *         if (!strcmp(value, "inherit")) {
     *             return PropertyT(SkSVGPropertyState::kInherit);
     *         }
     *
     *         auto pr = parse<typename PropertyT::ValueT>(value);
     *         if (pr.has_value()) {
     *             return PropertyT(*pr);
     *         }
     *
     *         return ParseResult<PropertyT>();
     *     }
     * ```
     */
    public fun <PropertyT> parseProperty(
      expectedName: String?,
      name: String?,
      `value`: String?,
    ): SkSVGAttributeParserParseResult<PropertyT> {
      TODO("Implement parseProperty")
    }
  }
}
