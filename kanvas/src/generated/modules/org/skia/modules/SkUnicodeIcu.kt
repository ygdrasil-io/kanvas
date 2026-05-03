package org.skia.modules

import BidiRegion
import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.UShort
import kotlin.Unit
import kotlin.collections.List
import org.skia.foundation.SkSp
import org.skia.foundation.SkUnichar
import org.skia.sksl.Position
import skia_private.TArraytrue
import undefined.BidiLevel
import BreakType as BreakType_
import undefined.BreakType as UndefinedBreakType

/**
 * C++ original:
 * ```cpp
 * class SkUnicode_icu : public SkUnicode {
 *
 *     static bool extractWords(uint16_t utf16[], int utf16Units, const char* locale,
 *                              std::vector<Position>* words) {
 *
 *         UErrorCode status = U_ZERO_ERROR;
 *
 *         const BreakType type = BreakType::kWords;
 *         ICUBreakIterator iterator = SkIcuBreakIteratorCache::get().makeBreakIterator(type, locale);
 *         if (!iterator) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *         SkASSERT(iterator);
 *
 *         ICUUText utf16UText(sk_utext_openUChars(nullptr, (UChar*)utf16, utf16Units, &status));
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *
 *         sk_ubrk_setUText(iterator.get(), utf16UText.get(), &status);
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *
 *         // Get the words
 *         int32_t pos = sk_ubrk_first(iterator.get());
 *         while (pos != UBRK_DONE) {
 *             words->emplace_back(pos);
 *             pos = sk_ubrk_next(iterator.get());
 *         }
 *
 *         return true;
 *     }
 *
 *     static bool extractPositions(const char utf8[], int utf8Units,
 *                                  BreakType type, const char* locale,
 *                                  const std::function<void(int, int)>& setBreak) {
 *
 *         UErrorCode status = U_ZERO_ERROR;
 *         ICUUText text(sk_utext_openUTF8(nullptr, &utf8[0], utf8Units, &status));
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *         SkASSERT(text);
 *
 *         ICUBreakIterator iterator = SkIcuBreakIteratorCache::get().makeBreakIterator(type, locale);
 *         if (!iterator) {
 *             return false;
 *         }
 *
 *         sk_ubrk_setUText(iterator.get(), text.get(), &status);
 *         if (U_FAILURE(status)) {
 *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             return false;
 *         }
 *
 *         auto iter = iterator.get();
 *         int32_t pos = sk_ubrk_first(iter);
 *         while (pos != UBRK_DONE) {
 *             int s = type == SkUnicode::BreakType::kLines
 *                         ? UBRK_LINE_SOFT
 *                         : sk_ubrk_getRuleStatus(iter);
 *             setBreak(pos, s);
 *             pos = sk_ubrk_next(iter);
 *         }
 *
 *         if (type == SkUnicode::BreakType::kLines) {
 *             // This is a workaround for https://bugs.chromium.org/p/skia/issues/detail?id=10715
 *             // (ICU line break iterator does not work correctly on Thai text with new lines)
 *             // So, we only use the iterator to collect soft line breaks and
 *             // scan the text for all hard line breaks ourselves
 *             const char* end = utf8 + utf8Units;
 *             const char* ch = utf8;
 *             while (ch < end) {
 *                 auto unichar = utf8_next(&ch, end);
 *                 if (SkUnicode_icu::isHardLineBreak(unichar)) {
 *                     setBreak(ch - utf8, UBRK_LINE_HARD);
 *                 }
 *             }
 *         }
 *         return true;
 *     }
 *
 *     bool isControl(SkUnichar utf8) override {
 *         return sk_u_iscntrl(utf8);
 *     }
 *
 *     bool isWhitespace(SkUnichar utf8) override {
 *         return sk_u_isWhitespace(utf8);
 *     }
 *
 *     bool isSpace(SkUnichar utf8) override {
 *         return sk_u_isspace(utf8);
 *     }
 *
 *     bool isHardBreak(SkUnichar utf8) override {
 *         return SkUnicode_icu::isHardLineBreak(utf8);
 *     }
 *
 *     bool isEmoji(SkUnichar unichar) override {
 *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI);
 *     }
 *
 *     bool isEmojiComponent(SkUnichar unichar) override {
 *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI_COMPONENT);
 *     }
 *
 *     bool isEmojiModifierBase(SkUnichar unichar) override {
 *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI_MODIFIER_BASE);
 *     }
 *
 *     bool isEmojiModifier(SkUnichar unichar) override {
 *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI_MODIFIER);
 *     }
 *
 *     bool isRegionalIndicator(SkUnichar unichar) override {
 *         return sk_u_hasBinaryProperty(unichar, UCHAR_REGIONAL_INDICATOR);
 *     }
 *
 *     bool isIdeographic(SkUnichar unichar) override {
 *         return sk_u_hasBinaryProperty(unichar, UCHAR_IDEOGRAPHIC);
 *     }
 *
 *     bool isTabulation(SkUnichar utf8) override {
 *         return utf8 == '\t';
 *     }
 *
 *     static bool isHardLineBreak(SkUnichar utf8) {
 *         auto property = sk_u_getIntPropertyValue(utf8, UCHAR_LINE_BREAK);
 *         return property == U_LB_LINE_FEED || property == U_LB_MANDATORY_BREAK;
 *     }
 *
 * public:
 *     ~SkUnicode_icu() override { }
 *     std::unique_ptr<SkBidiIterator> makeBidiIterator(const uint16_t text[], int count,
 *                                                      SkBidiIterator::Direction dir) override {
 *         return fBidiFact->MakeIterator(text, count, dir);
 *     }
 *     std::unique_ptr<SkBidiIterator> makeBidiIterator(const char text[],
 *                                                      int count,
 *                                                      SkBidiIterator::Direction dir) override {
 *         return fBidiFact->MakeIterator(text, count, dir);
 *     }
 *     std::unique_ptr<SkBreakIterator> makeBreakIterator(const char locale[],
 *                                                        BreakType type) override {
 *         ICUBreakIterator iterator = SkIcuBreakIteratorCache::get().makeBreakIterator(type, locale);
 *         if (!iterator) {
 *             return nullptr;
 *         }
 *         return std::unique_ptr<SkBreakIterator>(new SkBreakIterator_icu(std::move(iterator)));
 *     }
 *     std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType type) override {
 *         return makeBreakIterator(sk_uloc_getDefault(), type);
 *     }
 *
 *     SkString toUpper(const SkString& str) override {
 *         return this->toUpper(str, nullptr);
 *     }
 *
 *     SkString toUpper(const SkString& str, const char* locale) override {
 *         // Convert to UTF16 since that's what ICU wants.
 *         auto str16 = SkUnicode::convertUtf8ToUtf16(str.c_str(), str.size());
 *
 *         UErrorCode icu_err = U_ZERO_ERROR;
 *         const auto upper16len = sk_u_strToUpper(
 *             nullptr, 0,
 *             reinterpret_cast<const UChar*>(str16.c_str()), str16.size(),
 *             locale, &icu_err);
 *         if (icu_err != U_BUFFER_OVERFLOW_ERROR || upper16len <= 0) {
 *             return SkString();
 *         }
 *
 *         AutoSTArray<128, uint16_t> upper16(upper16len);
 *         icu_err = U_ZERO_ERROR;
 *         sk_u_strToUpper(reinterpret_cast<UChar*>(upper16.get()), SkToS32(upper16.size()),
 *                         reinterpret_cast<const UChar*>(str16.c_str()), str16.size(),
 *                         locale, &icu_err);
 *         SkASSERT(!U_FAILURE(icu_err));
 *
 *         // ... and back to utf8 'cause that's what we want.
 *         return convertUtf16ToUtf8((char16_t*)upper16.get(), upper16.size());
 *     }
 *
 *     bool getBidiRegions(const char utf8[],
 *                         int utf8Units,
 *                         TextDirection dir,
 *                         std::vector<BidiRegion>* results) override {
 *         return fBidiFact->ExtractBidi(utf8, utf8Units, dir, results);
 *     }
 *
 *     bool getWords(const char utf8[], int utf8Units, const char* locale,
 *                   std::vector<Position>* results) override {
 *
 *         // Convert to UTF16 since we want the results in utf16
 *         auto utf16 = convertUtf8ToUtf16(utf8, utf8Units);
 *         return SkUnicode_icu::extractWords(reinterpret_cast<uint16_t*>(utf16.data()), utf16.size(),
 *                                            locale, results);
 *     }
 *
 *     bool getUtf8Words(const char utf8[],
 *                       int utf8Units,
 *                       const char* locale,
 *                       std::vector<Position>* results) override {
 *         // Convert to UTF16 since we want the results in utf16
 *         auto utf16 = convertUtf8ToUtf16(utf8, utf8Units);
 *         std::vector<Position> utf16Results;
 *         if (!SkUnicode_icu::extractWords(reinterpret_cast<uint16_t*>(utf16.data()), utf16.size(),
 *                                          locale, &utf16Results))
 *         {
 *             return false;
 *         }
 *
 *         std::vector<Position> mapping;
 *         SkSpan<const char> text(utf8, utf8Units);
 *         SkUnicode::extractUtfConversionMapping(
 *                 text, [&](size_t index) { mapping.emplace_back(index); }, [&](size_t index) {});
 *
 *         for (auto i16 : utf16Results) {
 *             results->emplace_back(mapping[i16]);
 *         }
 *         return true;
 *     }
 *
 *     bool getSentences(const char utf8[],
 *                       int utf8Units,
 *                       const char* locale,
 *                       std::vector<SkUnicode::Position>* results) override {
 *         SkUnicode_icu::extractPositions(
 *                 utf8, utf8Units, BreakType::kSentences, nullptr,
 *                 [&](int pos, int status) {
 *                     results->emplace_back(pos);
 *                 });
 *         return true;
 *     }
 *
 *     bool computeCodeUnitFlags(char utf8[], int utf8Units, bool replaceTabs,
 *                               TArray<SkUnicode::CodeUnitFlags, true>* results) override {
 *         results->clear();
 *         results->push_back_n(utf8Units + 1, CodeUnitFlags::kNoCodeUnitFlag);
 *
 *         SkUnicode_icu::extractPositions(utf8, utf8Units, BreakType::kLines, nullptr, // TODO: locale
 *                                         [&](int pos, int status) {
 *             (*results)[pos] |= status == UBRK_LINE_HARD
 *                                        ? CodeUnitFlags::kHardLineBreakBefore
 *                                        : CodeUnitFlags::kSoftLineBreakBefore;
 *         });
 *
 *         SkUnicode_icu::extractPositions(utf8, utf8Units, BreakType::kGraphemes, nullptr, //TODO
 *                                         [&](int pos, int status) {
 *             (*results)[pos] |= CodeUnitFlags::kGraphemeStart;
 *         });
 *
 *         const char* current = utf8;
 *         const char* end = utf8 + utf8Units;
 *         while (current < end) {
 *             auto before = current - utf8;
 *             SkUnichar unichar = SkUTF::NextUTF8(&current, end);
 *             if (unichar < 0) unichar = 0xFFFD;
 *             auto after = current - utf8;
 *             if (replaceTabs && this->isTabulation(unichar)) {
 *                 results->at(before) |= SkUnicode::kTabulation;
 *                 if (replaceTabs) {
 *                     unichar = ' ';
 *                     utf8[before] = ' ';
 *                 }
 *             }
 *             for (auto i = before; i < after; ++i) {
 *                 if (this->isSpace(unichar)) {
 *                     results->at(i) |= SkUnicode::kPartOfIntraWordBreak;
 *                 }
 *                 if (this->isWhitespace(unichar)) {
 *                     results->at(i) |= SkUnicode::kPartOfWhiteSpaceBreak;
 *                 }
 *                 if (this->isControl(unichar)) {
 *                     results->at(i) |= SkUnicode::kControl;
 *                 }
 *                 if (this->isIdeographic(unichar)) {
 *                     results->at(i) |= SkUnicode::kIdeographic;
 *                 }
 *             }
 *         }
 *
 *         return true;
 *     }
 *
 *     bool computeCodeUnitFlags(char16_t utf16[], int utf16Units, bool replaceTabs,
 *                           TArray<SkUnicode::CodeUnitFlags, true>* results) override {
 *         results->clear();
 *         results->push_back_n(utf16Units + 1, CodeUnitFlags::kNoCodeUnitFlag);
 *
 *         // Get white spaces
 *         this->forEachCodepoint((char16_t*)&utf16[0], utf16Units,
 *            [this, results, replaceTabs, &utf16](SkUnichar unichar, int32_t start, int32_t end) {
 *                 for (auto i = start; i < end; ++i) {
 *                     if (replaceTabs && this->isTabulation(unichar)) {
 *                         results->at(i) |= SkUnicode::kTabulation;
 *                     if (replaceTabs) {
 *                             unichar = ' ';
 *                             utf16[start] = ' ';
 *                         }
 *                     }
 *                     if (this->isSpace(unichar)) {
 *                         results->at(i) |= SkUnicode::kPartOfIntraWordBreak;
 *                     }
 *                     if (this->isWhitespace(unichar)) {
 *                         results->at(i) |= SkUnicode::kPartOfWhiteSpaceBreak;
 *                     }
 *                     if (this->isControl(unichar)) {
 *                         results->at(i) |= SkUnicode::kControl;
 *                     }
 *                 }
 *            });
 *         // Get graphemes
 *         this->forEachBreak((char16_t*)&utf16[0],
 *                            utf16Units,
 *                            SkUnicode::BreakType::kGraphemes,
 *                            [results](SkBreakIterator::Position pos, SkBreakIterator::Status) {
 *                                (*results)[pos] |= CodeUnitFlags::kGraphemeStart;
 *                            });
 *         // Get line breaks
 *         this->forEachBreak(
 *                 (char16_t*)&utf16[0],
 *                 utf16Units,
 *                 SkUnicode::BreakType::kLines,
 *                 [results](SkBreakIterator::Position pos, SkBreakIterator::Status status) {
 *                     if (status ==
 *                         (SkBreakIterator::Status)SkUnicode::LineBreakType::kHardLineBreak) {
 *                         // Hard line breaks clears off all the other flags
 *                         // TODO: Treat \n as a formatting mark and do not pass it to SkShaper
 *                         (*results)[pos-1] = CodeUnitFlags::kHardLineBreakBefore;
 *                     } else {
 *                         (*results)[pos] |= CodeUnitFlags::kSoftLineBreakBefore;
 *                     }
 *                 });
 *
 *         return true;
 *     }
 *
 *     void reorderVisual(const BidiLevel runLevels[],
 *                        int levelsCount,
 *                        int32_t logicalFromVisual[]) override {
 *         if (levelsCount == 0) {
 *             // To avoid an assert in unicode
 *             return;
 *         }
 *         SkASSERT(runLevels != nullptr);
 *         fBidiFact->bidi_reorderVisual(runLevels, levelsCount, logicalFromVisual);
 *     }
 *
 * private:
 *     sk_sp<SkBidiFactory> fBidiFact = sk_make_sp<SkBidiICUFactory>();
 * }
 * ```
 */
public open class SkUnicodeIcu : SkUnicode() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBidiFactory> fBidiFact
   * ```
   */
  private var fBidiFact: SkSp<SkBidiFactory> = TODO("Initialize fBidiFact")

  /**
   * C++ original:
   * ```cpp
   * bool isControl(SkUnichar utf8) override {
   *         return sk_u_iscntrl(utf8);
   *     }
   * ```
   */
  public override fun isControl(utf8: SkUnichar): Boolean {
    TODO("Implement isControl")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isWhitespace(SkUnichar utf8) override {
   *         return sk_u_isWhitespace(utf8);
   *     }
   * ```
   */
  public override fun isWhitespace(utf8: SkUnichar): Boolean {
    TODO("Implement isWhitespace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSpace(SkUnichar utf8) override {
   *         return sk_u_isspace(utf8);
   *     }
   * ```
   */
  public override fun isSpace(utf8: SkUnichar): Boolean {
    TODO("Implement isSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isHardBreak(SkUnichar utf8) override {
   *         return SkUnicode_icu::isHardLineBreak(utf8);
   *     }
   * ```
   */
  public override fun isHardBreak(utf8: SkUnichar): Boolean {
    TODO("Implement isHardBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmoji(SkUnichar unichar) override {
   *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI);
   *     }
   * ```
   */
  public override fun isEmoji(unichar: SkUnichar): Boolean {
    TODO("Implement isEmoji")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmojiComponent(SkUnichar unichar) override {
   *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI_COMPONENT);
   *     }
   * ```
   */
  public override fun isEmojiComponent(unichar: SkUnichar): Boolean {
    TODO("Implement isEmojiComponent")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmojiModifierBase(SkUnichar unichar) override {
   *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI_MODIFIER_BASE);
   *     }
   * ```
   */
  public override fun isEmojiModifierBase(unichar: SkUnichar): Boolean {
    TODO("Implement isEmojiModifierBase")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmojiModifier(SkUnichar unichar) override {
   *         return sk_u_hasBinaryProperty(unichar, UCHAR_EMOJI_MODIFIER);
   *     }
   * ```
   */
  public override fun isEmojiModifier(unichar: SkUnichar): Boolean {
    TODO("Implement isEmojiModifier")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRegionalIndicator(SkUnichar unichar) override {
   *         return sk_u_hasBinaryProperty(unichar, UCHAR_REGIONAL_INDICATOR);
   *     }
   * ```
   */
  public override fun isRegionalIndicator(unichar: SkUnichar): Boolean {
    TODO("Implement isRegionalIndicator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isIdeographic(SkUnichar unichar) override {
   *         return sk_u_hasBinaryProperty(unichar, UCHAR_IDEOGRAPHIC);
   *     }
   * ```
   */
  public override fun isIdeographic(unichar: SkUnichar): Boolean {
    TODO("Implement isIdeographic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTabulation(SkUnichar utf8) override {
   *         return utf8 == '\t';
   *     }
   * ```
   */
  public override fun isTabulation(utf8: SkUnichar): Boolean {
    TODO("Implement isTabulation")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBidiIterator> makeBidiIterator(const uint16_t text[], int count,
   *                                                      SkBidiIterator::Direction dir) override {
   *         return fBidiFact->MakeIterator(text, count, dir);
   *     }
   * ```
   */
  public override fun makeBidiIterator(
    text: Array<UShort>,
    count: Int,
    dir: SkBidiIterator.Direction,
  ): Int {
    TODO("Implement makeBidiIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBidiIterator> makeBidiIterator(const char text[],
   *                                                      int count,
   *                                                      SkBidiIterator::Direction dir) override {
   *         return fBidiFact->MakeIterator(text, count, dir);
   *     }
   * ```
   */
  public override fun makeBidiIterator(
    text: CharArray,
    count: Int,
    dir: SkBidiIterator.Direction,
  ): Int {
    TODO("Implement makeBidiIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBreakIterator> makeBreakIterator(const char locale[],
   *                                                        BreakType type) override {
   *         ICUBreakIterator iterator = SkIcuBreakIteratorCache::get().makeBreakIterator(type, locale);
   *         if (!iterator) {
   *             return nullptr;
   *         }
   *         return std::unique_ptr<SkBreakIterator>(new SkBreakIterator_icu(std::move(iterator)));
   *     }
   * ```
   */
  public override fun makeBreakIterator(locale: CharArray, type: UndefinedBreakType): Int {
    TODO("Implement makeBreakIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType type) override {
   *         return makeBreakIterator(sk_uloc_getDefault(), type);
   *     }
   * ```
   */
  public override fun makeBreakIterator(type: UndefinedBreakType): Int {
    TODO("Implement makeBreakIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toUpper(const SkString& str) override {
   *         return this->toUpper(str, nullptr);
   *     }
   * ```
   */
  public override fun toUpper(str: String): String {
    TODO("Implement toUpper")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toUpper(const SkString& str, const char* locale) override {
   *         // Convert to UTF16 since that's what ICU wants.
   *         auto str16 = SkUnicode::convertUtf8ToUtf16(str.c_str(), str.size());
   *
   *         UErrorCode icu_err = U_ZERO_ERROR;
   *         const auto upper16len = sk_u_strToUpper(
   *             nullptr, 0,
   *             reinterpret_cast<const UChar*>(str16.c_str()), str16.size(),
   *             locale, &icu_err);
   *         if (icu_err != U_BUFFER_OVERFLOW_ERROR || upper16len <= 0) {
   *             return SkString();
   *         }
   *
   *         AutoSTArray<128, uint16_t> upper16(upper16len);
   *         icu_err = U_ZERO_ERROR;
   *         sk_u_strToUpper(reinterpret_cast<UChar*>(upper16.get()), SkToS32(upper16.size()),
   *                         reinterpret_cast<const UChar*>(str16.c_str()), str16.size(),
   *                         locale, &icu_err);
   *         SkASSERT(!U_FAILURE(icu_err));
   *
   *         // ... and back to utf8 'cause that's what we want.
   *         return convertUtf16ToUtf8((char16_t*)upper16.get(), upper16.size());
   *     }
   * ```
   */
  public override fun toUpper(str: String, locale: String?): String {
    TODO("Implement toUpper")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getBidiRegions(const char utf8[],
   *                         int utf8Units,
   *                         TextDirection dir,
   *                         std::vector<BidiRegion>* results) override {
   *         return fBidiFact->ExtractBidi(utf8, utf8Units, dir, results);
   *     }
   * ```
   */
  public override fun getBidiRegions(
    utf8: CharArray,
    utf8Units: Int,
    dir: TextDirection,
    results: List<BidiRegion>?,
  ): Boolean {
    TODO("Implement getBidiRegions")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getWords(const char utf8[], int utf8Units, const char* locale,
   *                   std::vector<Position>* results) override {
   *
   *         // Convert to UTF16 since we want the results in utf16
   *         auto utf16 = convertUtf8ToUtf16(utf8, utf8Units);
   *         return SkUnicode_icu::extractWords(reinterpret_cast<uint16_t*>(utf16.data()), utf16.size(),
   *                                            locale, results);
   *     }
   * ```
   */
  public override fun getWords(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<Position>?,
  ): Boolean {
    TODO("Implement getWords")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getUtf8Words(const char utf8[],
   *                       int utf8Units,
   *                       const char* locale,
   *                       std::vector<Position>* results) override {
   *         // Convert to UTF16 since we want the results in utf16
   *         auto utf16 = convertUtf8ToUtf16(utf8, utf8Units);
   *         std::vector<Position> utf16Results;
   *         if (!SkUnicode_icu::extractWords(reinterpret_cast<uint16_t*>(utf16.data()), utf16.size(),
   *                                          locale, &utf16Results))
   *         {
   *             return false;
   *         }
   *
   *         std::vector<Position> mapping;
   *         SkSpan<const char> text(utf8, utf8Units);
   *         SkUnicode::extractUtfConversionMapping(
   *                 text, [&](size_t index) { mapping.emplace_back(index); }, [&](size_t index) {});
   *
   *         for (auto i16 : utf16Results) {
   *             results->emplace_back(mapping[i16]);
   *         }
   *         return true;
   *     }
   * ```
   */
  public override fun getUtf8Words(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<Position>?,
  ): Boolean {
    TODO("Implement getUtf8Words")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getSentences(const char utf8[],
   *                       int utf8Units,
   *                       const char* locale,
   *                       std::vector<SkUnicode::Position>* results) override {
   *         SkUnicode_icu::extractPositions(
   *                 utf8, utf8Units, BreakType::kSentences, nullptr,
   *                 [&](int pos, int status) {
   *                     results->emplace_back(pos);
   *                 });
   *         return true;
   *     }
   * ```
   */
  public override fun getSentences(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<SkUnicodePosition>?,
  ): Boolean {
    TODO("Implement getSentences")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeCodeUnitFlags(char utf8[], int utf8Units, bool replaceTabs,
   *                               TArray<SkUnicode::CodeUnitFlags, true>* results) override {
   *         results->clear();
   *         results->push_back_n(utf8Units + 1, CodeUnitFlags::kNoCodeUnitFlag);
   *
   *         SkUnicode_icu::extractPositions(utf8, utf8Units, BreakType::kLines, nullptr, // TODO: locale
   *                                         [&](int pos, int status) {
   *             (*results)[pos] |= status == UBRK_LINE_HARD
   *                                        ? CodeUnitFlags::kHardLineBreakBefore
   *                                        : CodeUnitFlags::kSoftLineBreakBefore;
   *         });
   *
   *         SkUnicode_icu::extractPositions(utf8, utf8Units, BreakType::kGraphemes, nullptr, //TODO
   *                                         [&](int pos, int status) {
   *             (*results)[pos] |= CodeUnitFlags::kGraphemeStart;
   *         });
   *
   *         const char* current = utf8;
   *         const char* end = utf8 + utf8Units;
   *         while (current < end) {
   *             auto before = current - utf8;
   *             SkUnichar unichar = SkUTF::NextUTF8(&current, end);
   *             if (unichar < 0) unichar = 0xFFFD;
   *             auto after = current - utf8;
   *             if (replaceTabs && this->isTabulation(unichar)) {
   *                 results->at(before) |= SkUnicode::kTabulation;
   *                 if (replaceTabs) {
   *                     unichar = ' ';
   *                     utf8[before] = ' ';
   *                 }
   *             }
   *             for (auto i = before; i < after; ++i) {
   *                 if (this->isSpace(unichar)) {
   *                     results->at(i) |= SkUnicode::kPartOfIntraWordBreak;
   *                 }
   *                 if (this->isWhitespace(unichar)) {
   *                     results->at(i) |= SkUnicode::kPartOfWhiteSpaceBreak;
   *                 }
   *                 if (this->isControl(unichar)) {
   *                     results->at(i) |= SkUnicode::kControl;
   *                 }
   *                 if (this->isIdeographic(unichar)) {
   *                     results->at(i) |= SkUnicode::kIdeographic;
   *                 }
   *             }
   *         }
   *
   *         return true;
   *     }
   * ```
   */
  public override fun computeCodeUnitFlags(
    utf8: CharArray,
    utf8Units: Int,
    replaceTabs: Boolean,
    results: TArraytrue<SkUnicode.CodeUnitFlags>,
  ): Boolean {
    TODO("Implement computeCodeUnitFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeCodeUnitFlags(char16_t utf16[], int utf16Units, bool replaceTabs,
   *                           TArray<SkUnicode::CodeUnitFlags, true>* results) override {
   *         results->clear();
   *         results->push_back_n(utf16Units + 1, CodeUnitFlags::kNoCodeUnitFlag);
   *
   *         // Get white spaces
   *         this->forEachCodepoint((char16_t*)&utf16[0], utf16Units,
   *            [this, results, replaceTabs, &utf16](SkUnichar unichar, int32_t start, int32_t end) {
   *                 for (auto i = start; i < end; ++i) {
   *                     if (replaceTabs && this->isTabulation(unichar)) {
   *                         results->at(i) |= SkUnicode::kTabulation;
   *                     if (replaceTabs) {
   *                             unichar = ' ';
   *                             utf16[start] = ' ';
   *                         }
   *                     }
   *                     if (this->isSpace(unichar)) {
   *                         results->at(i) |= SkUnicode::kPartOfIntraWordBreak;
   *                     }
   *                     if (this->isWhitespace(unichar)) {
   *                         results->at(i) |= SkUnicode::kPartOfWhiteSpaceBreak;
   *                     }
   *                     if (this->isControl(unichar)) {
   *                         results->at(i) |= SkUnicode::kControl;
   *                     }
   *                 }
   *            });
   *         // Get graphemes
   *         this->forEachBreak((char16_t*)&utf16[0],
   *                            utf16Units,
   *                            SkUnicode::BreakType::kGraphemes,
   *                            [results](SkBreakIterator::Position pos, SkBreakIterator::Status) {
   *                                (*results)[pos] |= CodeUnitFlags::kGraphemeStart;
   *                            });
   *         // Get line breaks
   *         this->forEachBreak(
   *                 (char16_t*)&utf16[0],
   *                 utf16Units,
   *                 SkUnicode::BreakType::kLines,
   *                 [results](SkBreakIterator::Position pos, SkBreakIterator::Status status) {
   *                     if (status ==
   *                         (SkBreakIterator::Status)SkUnicode::LineBreakType::kHardLineBreak) {
   *                         // Hard line breaks clears off all the other flags
   *                         // TODO: Treat \n as a formatting mark and do not pass it to SkShaper
   *                         (*results)[pos-1] = CodeUnitFlags::kHardLineBreakBefore;
   *                     } else {
   *                         (*results)[pos] |= CodeUnitFlags::kSoftLineBreakBefore;
   *                     }
   *                 });
   *
   *         return true;
   *     }
   * ```
   */
  public override fun reorderVisual(
    runLevels: Array<BidiLevel>,
    levelsCount: Int,
    logicalFromVisual: IntArray,
  ) {
    TODO("Implement reorderVisual")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool extractWords(uint16_t utf16[], int utf16Units, const char* locale,
     *                              std::vector<Position>* words) {
     *
     *         UErrorCode status = U_ZERO_ERROR;
     *
     *         const BreakType type = BreakType::kWords;
     *         ICUBreakIterator iterator = SkIcuBreakIteratorCache::get().makeBreakIterator(type, locale);
     *         if (!iterator) {
     *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
     *             return false;
     *         }
     *         SkASSERT(iterator);
     *
     *         ICUUText utf16UText(sk_utext_openUChars(nullptr, (UChar*)utf16, utf16Units, &status));
     *         if (U_FAILURE(status)) {
     *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
     *             return false;
     *         }
     *
     *         sk_ubrk_setUText(iterator.get(), utf16UText.get(), &status);
     *         if (U_FAILURE(status)) {
     *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
     *             return false;
     *         }
     *
     *         // Get the words
     *         int32_t pos = sk_ubrk_first(iterator.get());
     *         while (pos != UBRK_DONE) {
     *             words->emplace_back(pos);
     *             pos = sk_ubrk_next(iterator.get());
     *         }
     *
     *         return true;
     *     }
     * ```
     */
    private fun extractWords(
      utf16: Array<UShort>,
      utf16Units: Int,
      locale: String?,
      words: List<Position>?,
    ): Boolean {
      TODO("Implement extractWords")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool extractPositions(const char utf8[], int utf8Units,
     *                                  BreakType type, const char* locale,
     *                                  const std::function<void(int, int)>& setBreak) {
     *
     *         UErrorCode status = U_ZERO_ERROR;
     *         ICUUText text(sk_utext_openUTF8(nullptr, &utf8[0], utf8Units, &status));
     *         if (U_FAILURE(status)) {
     *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
     *             return false;
     *         }
     *         SkASSERT(text);
     *
     *         ICUBreakIterator iterator = SkIcuBreakIteratorCache::get().makeBreakIterator(type, locale);
     *         if (!iterator) {
     *             return false;
     *         }
     *
     *         sk_ubrk_setUText(iterator.get(), text.get(), &status);
     *         if (U_FAILURE(status)) {
     *             SkDEBUGF("Break error: %s", sk_u_errorName(status));
     *             return false;
     *         }
     *
     *         auto iter = iterator.get();
     *         int32_t pos = sk_ubrk_first(iter);
     *         while (pos != UBRK_DONE) {
     *             int s = type == SkUnicode::BreakType::kLines
     *                         ? UBRK_LINE_SOFT
     *                         : sk_ubrk_getRuleStatus(iter);
     *             setBreak(pos, s);
     *             pos = sk_ubrk_next(iter);
     *         }
     *
     *         if (type == SkUnicode::BreakType::kLines) {
     *             // This is a workaround for https://bugs.chromium.org/p/skia/issues/detail?id=10715
     *             // (ICU line break iterator does not work correctly on Thai text with new lines)
     *             // So, we only use the iterator to collect soft line breaks and
     *             // scan the text for all hard line breaks ourselves
     *             const char* end = utf8 + utf8Units;
     *             const char* ch = utf8;
     *             while (ch < end) {
     *                 auto unichar = utf8_next(&ch, end);
     *                 if (SkUnicode_icu::isHardLineBreak(unichar)) {
     *                     setBreak(ch - utf8, UBRK_LINE_HARD);
     *                 }
     *             }
     *         }
     *         return true;
     *     }
     * ```
     */
    private fun extractPositions(
      utf8: CharArray,
      utf8Units: Int,
      type: BreakType_,
      locale: String?,
      setBreak: (Int, Int) -> Unit,
    ): Boolean {
      TODO("Implement extractPositions")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool isHardLineBreak(SkUnichar utf8) {
     *         auto property = sk_u_getIntPropertyValue(utf8, UCHAR_LINE_BREAK);
     *         return property == U_LB_LINE_FEED || property == U_LB_MANDATORY_BREAK;
     *     }
     * ```
     */
    private fun isHardLineBreak(utf8: SkUnichar): Boolean {
      TODO("Implement isHardLineBreak")
    }
  }
}
