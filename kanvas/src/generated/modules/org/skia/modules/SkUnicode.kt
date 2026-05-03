package org.skia.modules

import Appender16
import Appender8
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.UShort
import kotlin.collections.List
import kotlin.u16string
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSpan
import org.skia.foundation.SkUnichar
import org.skia.sksl.Position
import skia_private.TArraytrue
import undefined.BidiLevel
import undefined.Callback

/**
 * C++ original:
 * ```cpp
 * class SKUNICODE_API SkUnicode : public SkRefCnt {
 *     public:
 *         enum CodeUnitFlags {
 *             kNoCodeUnitFlag = 0x00,
 *             kPartOfWhiteSpaceBreak = 0x01,
 *             kGraphemeStart = 0x02,
 *             kSoftLineBreakBefore = 0x04,
 *             kHardLineBreakBefore = 0x08,
 *             kPartOfIntraWordBreak = 0x10,
 *             kControl = 0x20,
 *             kTabulation = 0x40,
 *             kGlyphClusterStart = 0x80,
 *             kIdeographic = 0x100,
 *             kEmoji = 0x200,
 *             kWordBreak = 0x400,
 *             kSentenceBreak = 0x800,
 *         };
 *         enum class TextDirection {
 *             kLTR,
 *             kRTL,
 *         };
 *         typedef size_t Position;
 *         typedef uint8_t BidiLevel;
 *         struct BidiRegion {
 *             BidiRegion(Position start, Position end, BidiLevel level)
 *               : start(start), end(end), level(level) { }
 *             Position start;
 *             Position end;
 *             BidiLevel level;
 *         };
 *         enum class LineBreakType {
 *             kSoftLineBreak = 0,
 *             kHardLineBreak = 100,
 *         };
 *
 *         enum class BreakType { kWords, kGraphemes, kLines, kSentences };
 *         struct LineBreakBefore {
 *             LineBreakBefore(Position pos, LineBreakType breakType)
 *               : pos(pos), breakType(breakType) { }
 *             Position pos;
 *             LineBreakType breakType;
 *         };
 *
 *         ~SkUnicode() override = default;
 *
 *         // deprecated
 *         virtual SkString toUpper(const SkString&) = 0;
 *         virtual SkString toUpper(const SkString&, const char* locale) = 0;
 *
 *         virtual bool isControl(SkUnichar utf8) = 0;
 *         virtual bool isWhitespace(SkUnichar utf8) = 0;
 *         virtual bool isSpace(SkUnichar utf8) = 0;
 *         virtual bool isTabulation(SkUnichar utf8) = 0;
 *         virtual bool isHardBreak(SkUnichar utf8) = 0;
 *         /**
 *          * Returns if a code point may start an emoji sequence.
 *          * Returns true for '#', '*', and '0'-'9' since they may start an emoji sequence.
 *          * To determine if a list of code points begins with an emoji sequence, use
 *          * getEmojiSequence.
 *          **/
 *         virtual bool isEmoji(SkUnichar utf8) = 0;
 *         virtual bool isEmojiComponent(SkUnichar utf8) = 0;
 *         virtual bool isEmojiModifierBase(SkUnichar utf8) = 0;
 *         virtual bool isEmojiModifier(SkUnichar utf8) = 0;
 *         virtual bool isRegionalIndicator(SkUnichar utf8) = 0;
 *         virtual bool isIdeographic(SkUnichar utf8) = 0;
 *
 *         // Methods used in SkShaper and SkText
 *         virtual std::unique_ptr<SkBidiIterator> makeBidiIterator
 *             (const uint16_t text[], int count, SkBidiIterator::Direction) = 0;
 *         virtual std::unique_ptr<SkBidiIterator> makeBidiIterator
 *             (const char text[], int count, SkBidiIterator::Direction) = 0;
 *         virtual std::unique_ptr<SkBreakIterator> makeBreakIterator
 *             (const char locale[], BreakType breakType) = 0;
 *         virtual std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType type) = 0;
 *
 *         // Methods used in SkParagraph
 *         static bool hasTabulationFlag(SkUnicode::CodeUnitFlags flags);
 *         static bool hasHardLineBreakFlag(SkUnicode::CodeUnitFlags flags);
 *         static bool hasSoftLineBreakFlag(SkUnicode::CodeUnitFlags flags);
 *         static bool hasGraphemeStartFlag(SkUnicode::CodeUnitFlags flags);
 *         static bool hasControlFlag(SkUnicode::CodeUnitFlags flags);
 *         static bool hasPartOfWhiteSpaceBreakFlag(SkUnicode::CodeUnitFlags flags);
 *
 *         static bool extractBidi(const char utf8[],
 *                                 int utf8Units,
 *                                 TextDirection dir,
 *                                 std::vector<BidiRegion>* bidiRegions);
 *         virtual bool getBidiRegions(const char utf8[],
 *                                     int utf8Units,
 *                                     TextDirection dir,
 *                                     std::vector<BidiRegion>* results) = 0;
 *         // Returns results in utf16
 *         virtual bool getWords(const char utf8[], int utf8Units, const char* locale,
 *                               std::vector<Position>* results) = 0;
 *         virtual bool getUtf8Words(const char utf8[],
 *                                   int utf8Units,
 *                                   const char* locale,
 *                                   std::vector<Position>* results) = 0;
 *         virtual bool getSentences(const char utf8[],
 *                                   int utf8Units,
 *                                   const char* locale,
 *                                   std::vector<Position>* results) = 0;
 *         virtual bool computeCodeUnitFlags(
 *                 char utf8[], int utf8Units, bool replaceTabs,
 *                 skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) = 0;
 *         virtual bool computeCodeUnitFlags(
 *                 char16_t utf16[], int utf16Units, bool replaceTabs,
 *                 skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) = 0;
 *
 *         static SkString convertUtf16ToUtf8(const char16_t * utf16, int utf16Units);
 *         static SkString convertUtf16ToUtf8(const std::u16string& utf16);
 *         static std::u16string convertUtf8ToUtf16(const char* utf8, int utf8Units);
 *         static std::u16string convertUtf8ToUtf16(const SkString& utf8);
 *
 *         template <typename Appender8, typename Appender16>
 *         static bool extractUtfConversionMapping(SkSpan<const char> utf8, Appender8&& appender8, Appender16&& appender16) {
 *             size_t size8 = 0;
 *             size_t size16 = 0;
 *             auto ptr = utf8.data();
 *             auto end = ptr + utf8.size();
 *             while (ptr < end) {
 *
 *                 size_t index = SkToSizeT(ptr - utf8.data());
 *                 SkUnichar u = SkUTF::NextUTF8(&ptr, end);
 *
 *                 // All UTF8 code units refer to the same codepoint
 *                 size_t next = SkToSizeT(ptr - utf8.data());
 *                 for (auto i = index; i < next; ++i) {
 *                     //fUTF16IndexForUTF8Index.emplace_back(fUTF8IndexForUTF16Index.size());
 *                     appender16(size8);
 *                     ++size16;
 *                 }
 *                 //SkASSERT(fUTF16IndexForUTF8Index.size() == next);
 *                 SkASSERT(size16 == next);
 *                 if (size16 != next) {
 *                     return false;
 *                 }
 *
 *                 // One or two UTF16 code units refer to the same codepoint
 *                 uint16_t buffer[2];
 *                 size_t count = SkUTF::ToUTF16(u, buffer);
 *                 //fUTF8IndexForUTF16Index.emplace_back(index);
 *                 appender8(index);
 *                 ++size8;
 *                 if (count > 1) {
 *                     //fUTF8IndexForUTF16Index.emplace_back(index);
 *                     appender8(index);
 *                     ++size8;
 *                 }
 *             }
 *             //fUTF16IndexForUTF8Index.emplace_back(fUTF8IndexForUTF16Index.size());
 *             appender16(size8);
 *             ++size16;
 *             //fUTF8IndexForUTF16Index.emplace_back(fText.size());
 *             appender8(utf8.size());
 *             ++size8;
 *
 *             return true;
 *         }
 *
 *         template <typename Callback>
 *         void forEachCodepoint(const char* utf8, int32_t utf8Units, Callback&& callback) {
 *             const char* current = utf8;
 *             const char* end = utf8 + utf8Units;
 *             while (current < end) {
 *                 auto before = current - utf8;
 *                 SkUnichar unichar = SkUTF::NextUTF8(&current, end);
 *                 if (unichar < 0) unichar = 0xFFFD;
 *                 auto after = current - utf8;
 *                 uint16_t buffer[2];
 *                 size_t count = SkUTF::ToUTF16(unichar, buffer);
 *                 callback(unichar, before, after, count);
 *             }
 *         }
 *
 *         template <typename Callback>
 *         void forEachCodepoint(const char16_t* utf16, int32_t utf16Units, Callback&& callback) {
 *             const char16_t* current = utf16;
 *             const char16_t* end = utf16 + utf16Units;
 *             while (current < end) {
 *                 auto before = current - utf16;
 *                 SkUnichar unichar = SkUTF::NextUTF16((const uint16_t**)&current, (const uint16_t*)end);
 *                 auto after = current - utf16;
 *                 callback(unichar, before, after);
 *             }
 *         }
 *
 *         template <typename Callback>
 *         void forEachBidiRegion(const uint16_t utf16[], int utf16Units, SkBidiIterator::Direction dir, Callback&& callback) {
 *             auto iter = makeBidiIterator(utf16, utf16Units, dir);
 *             const uint16_t* start16 = utf16;
 *             const uint16_t* end16 = utf16 + utf16Units;
 *             SkBidiIterator::Level currentLevel = 0;
 *
 *             SkBidiIterator::Position pos16 = 0;
 *             while (pos16 <= iter->getLength()) {
 *                 const auto nextPos16 = SkTo<SkBidiIterator::Position>(start16 - utf16);
 *                 // The pointer difference is bound by utf16Units, and cannot overflow nextPos16.
 *                 static_assert(std::numeric_limits<decltype(utf16Units)>::max() <=
 *                               std::numeric_limits<decltype(nextPos16)>::max());
 *                 auto level = iter->getLevelAt(nextPos16);
 *                 if (nextPos16 == 0) {
 *                     currentLevel = level;
 *                 } else if (level != currentLevel) {
 *                     callback(pos16, nextPos16, currentLevel);
 *                     currentLevel = level;
 *                     pos16 = nextPos16;
 *                 }
 *                 if (start16 == end16) {
 *                     if (pos16 != nextPos16) {
 *                         callback(pos16, nextPos16, currentLevel);
 *                     }
 *                     return;
 *                 }
 *                 SkUTF::NextUTF16(&start16, end16);
 *             }
 *         }
 *
 *         template <typename Callback>
 *         void forEachBreak(const char16_t utf16[], int utf16Units, SkUnicode::BreakType type, Callback&& callback) {
 *             auto iter = makeBreakIterator(type);
 *             iter->setText(utf16, utf16Units);
 *             auto pos = iter->first();
 *             do {
 *                 callback(pos, iter->status());
 *                 pos = iter->next();
 *             } while (!iter->isDone());
 *         }
 *
 *         virtual void reorderVisual(const BidiLevel runLevels[], int levelsCount, int32_t logicalFromVisual[]) = 0;
 * }
 * ```
 */
public abstract class SkUnicode : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual SkString toUpper(const SkString&) = 0
   * ```
   */
  public abstract fun toUpper(param0: String): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkString toUpper(const SkString&, const char* locale) = 0
   * ```
   */
  public abstract fun toUpper(param0: String, locale: String?): Int

  /**
   * C++ original:
   * ```cpp
   * virtual bool isControl(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isControl(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isWhitespace(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isWhitespace(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isSpace(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isSpace(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isTabulation(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isTabulation(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isHardBreak(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isHardBreak(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isEmoji(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isEmoji(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isEmojiComponent(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isEmojiComponent(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isEmojiModifierBase(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isEmojiModifierBase(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isEmojiModifier(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isEmojiModifier(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isRegionalIndicator(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isRegionalIndicator(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool isIdeographic(SkUnichar utf8) = 0
   * ```
   */
  public abstract fun isIdeographic(utf8: SkUnichar): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkBidiIterator> makeBidiIterator
   *             (const uint16_t text[], int count, SkBidiIterator::Direction) = 0
   * ```
   */
  public abstract fun makeBidiIterator(
    text: Array<UShort>,
    count: Int,
    param2: SkBidiIterator.Direction,
  ): SkBidiIterator?

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkBidiIterator> makeBidiIterator
   *             (const char text[], int count, SkBidiIterator::Direction) = 0
   * ```
   */
  public abstract fun makeBidiIterator(
    text: CharArray,
    count: Int,
    param2: SkBidiIterator.Direction,
  ): SkBidiIterator?

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkBreakIterator> makeBreakIterator
   *             (const char locale[], BreakType breakType) = 0
   * ```
   */
  public abstract fun makeBreakIterator(locale: CharArray, breakType: BreakType): SkBreakIterator?

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType type) = 0
   * ```
   */
  public abstract fun makeBreakIterator(type: BreakType): SkBreakIterator?

  /**
   * C++ original:
   * ```cpp
   * virtual bool getBidiRegions(const char utf8[],
   *                                     int utf8Units,
   *                                     TextDirection dir,
   *                                     std::vector<BidiRegion>* results) = 0
   * ```
   */
  public abstract fun getBidiRegions(
    utf8: CharArray,
    utf8Units: Int,
    dir: TextDirection,
    results: List<BidiRegion>?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool getWords(const char utf8[], int utf8Units, const char* locale,
   *                               std::vector<Position>* results) = 0
   * ```
   */
  public abstract fun getWords(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<SkUnicodePosition>?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool getUtf8Words(const char utf8[],
   *                                   int utf8Units,
   *                                   const char* locale,
   *                                   std::vector<Position>* results) = 0
   * ```
   */
  public abstract fun getUtf8Words(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<SkUnicodePosition>?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool getSentences(const char utf8[],
   *                                   int utf8Units,
   *                                   const char* locale,
   *                                   std::vector<Position>* results) = 0
   * ```
   */
  public abstract fun getSentences(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<SkUnicodePosition>?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool computeCodeUnitFlags(
   *                 char utf8[], int utf8Units, bool replaceTabs,
   *                 skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) = 0
   * ```
   */
  public abstract fun computeCodeUnitFlags(
    utf8: CharArray,
    utf8Units: Int,
    replaceTabs: Boolean,
    results: TArraytrue<CodeUnitFlags>,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool computeCodeUnitFlags(
   *                 char16_t utf16[], int utf16Units, bool replaceTabs,
   *                 skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) = 0
   * ```
   */
  public fun <Callback> forEachCodepoint(
    utf8: String?,
    utf8Units: Int,
    callback: Callback,
  ) {
    TODO("Implement forEachCodepoint")
  }

  /**
   * C++ original:
   * ```cpp
   *         template <typename Callback>
   *         void forEachCodepoint(const char* utf8, int32_t utf8Units, Callback&& callback) {
   *             const char* current = utf8;
   *             const char* end = utf8 + utf8Units;
   *             while (current < end) {
   *                 auto before = current - utf8;
   *                 SkUnichar unichar = SkUTF::NextUTF8(&current, end);
   *                 if (unichar < 0) unichar = 0xFFFD;
   *                 auto after = current - utf8;
   *                 uint16_t buffer[2];
   *                 size_t count = SkUTF::ToUTF16(unichar, buffer);
   *                 callback(unichar, before, after, count);
   *             }
   *         }
   * ```
   */
  public fun <Callback> forEachCodepoint(
    utf16: Char?,
    utf16Units: Int,
    callback: Callback,
  ) {
    TODO("Implement forEachCodepoint")
  }

  /**
   * C++ original:
   * ```cpp
   *         template <typename Callback>
   *         void forEachCodepoint(const char16_t* utf16, int32_t utf16Units, Callback&& callback) {
   *             const char16_t* current = utf16;
   *             const char16_t* end = utf16 + utf16Units;
   *             while (current < end) {
   *                 auto before = current - utf16;
   *                 SkUnichar unichar = SkUTF::NextUTF16((const uint16_t**)&current, (const uint16_t*)end);
   *                 auto after = current - utf16;
   *                 callback(unichar, before, after);
   *             }
   *         }
   * ```
   */
  public fun <Callback> forEachBidiRegion(
    utf16: Array<UShort>,
    utf16Units: Int,
    dir: SkBidiIterator.Direction,
    callback: Callback,
  ) {
    TODO("Implement forEachBidiRegion")
  }

  /**
   * C++ original:
   * ```cpp
   *         template <typename Callback>
   *         void forEachBidiRegion(const uint16_t utf16[], int utf16Units, SkBidiIterator::Direction dir, Callback&& callback) {
   *             auto iter = makeBidiIterator(utf16, utf16Units, dir);
   *             const uint16_t* start16 = utf16;
   *             const uint16_t* end16 = utf16 + utf16Units;
   *             SkBidiIterator::Level currentLevel = 0;
   *
   *             SkBidiIterator::Position pos16 = 0;
   *             while (pos16 <= iter->getLength()) {
   *                 const auto nextPos16 = SkTo<SkBidiIterator::Position>(start16 - utf16);
   *                 // The pointer difference is bound by utf16Units, and cannot overflow nextPos16.
   *                 static_assert(std::numeric_limits<decltype(utf16Units)>::max() <=
   *                               std::numeric_limits<decltype(nextPos16)>::max());
   *                 auto level = iter->getLevelAt(nextPos16);
   *                 if (nextPos16 == 0) {
   *                     currentLevel = level;
   *                 } else if (level != currentLevel) {
   *                     callback(pos16, nextPos16, currentLevel);
   *                     currentLevel = level;
   *                     pos16 = nextPos16;
   *                 }
   *                 if (start16 == end16) {
   *                     if (pos16 != nextPos16) {
   *                         callback(pos16, nextPos16, currentLevel);
   *                     }
   *                     return;
   *                 }
   *                 SkUTF::NextUTF16(&start16, end16);
   *             }
   *         }
   * ```
   */
  public fun <Callback> forEachBreak(
    utf16: CharArray,
    utf16Units: Int,
    type: BreakType,
    callback: Callback,
  ) {
    TODO("Implement forEachBreak")
  }

  /**
   * C++ original:
   * ```cpp
   *         template <typename Callback>
   *         void forEachBreak(const char16_t utf16[], int utf16Units, SkUnicode::BreakType type, Callback&& callback) {
   *             auto iter = makeBreakIterator(type);
   *             iter->setText(utf16, utf16Units);
   *             auto pos = iter->first();
   *             do {
   *                 callback(pos, iter->status());
   *                 pos = iter->next();
   *             } while (!iter->isDone());
   *         }
   * ```
   */
  public abstract fun reorderVisual(
    runLevels: Array<SkUnicodeBidiLevel>,
    levelsCount: Int,
    logicalFromVisual: IntArray,
  )

  public data class BidiRegion public constructor(
    public var start: Position,
    public var end: Position,
    public var level: BidiLevel,
  )

  public data class LineBreakBefore public constructor(
    public var pos: Position,
    public var breakType: undefined.LineBreakType,
  )

  public enum class CodeUnitFlags {
    kNoCodeUnitFlag,
    kPartOfWhiteSpaceBreak,
    kGraphemeStart,
    kSoftLineBreakBefore,
    kHardLineBreakBefore,
    kPartOfIntraWordBreak,
    kControl,
    kTabulation,
    kGlyphClusterStart,
    kIdeographic,
    kEmoji,
    kWordBreak,
    kSentenceBreak,
  }

  public enum class TextDirection {
    kLTR,
    kRTL,
  }

  public enum class LineBreakType {
    kSoftLineBreak,
    kHardLineBreak,
  }

  public enum class BreakType {
    kWords,
    kGraphemes,
    kLines,
    kSentences,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkUnicode::hasTabulationFlag(SkUnicode::CodeUnitFlags flags) {
     *     return (flags & SkUnicode::kTabulation) == SkUnicode::kTabulation;
     * }
     * ```
     */
    public fun hasTabulationFlag(flags: CodeUnitFlags): Boolean {
      TODO("Implement hasTabulationFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkUnicode::hasHardLineBreakFlag(SkUnicode::CodeUnitFlags flags) {
     *     return (flags & SkUnicode::kHardLineBreakBefore) == SkUnicode::kHardLineBreakBefore;
     * }
     * ```
     */
    public fun hasHardLineBreakFlag(flags: CodeUnitFlags): Boolean {
      TODO("Implement hasHardLineBreakFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkUnicode::hasSoftLineBreakFlag(SkUnicode::CodeUnitFlags flags) {
     *     return (flags & SkUnicode::kSoftLineBreakBefore) == SkUnicode::kSoftLineBreakBefore;
     * }
     * ```
     */
    public fun hasSoftLineBreakFlag(flags: CodeUnitFlags): Boolean {
      TODO("Implement hasSoftLineBreakFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkUnicode::hasGraphemeStartFlag(SkUnicode::CodeUnitFlags flags) {
     *     return (flags & SkUnicode::kGraphemeStart) == SkUnicode::kGraphemeStart;
     * }
     * ```
     */
    public fun hasGraphemeStartFlag(flags: CodeUnitFlags): Boolean {
      TODO("Implement hasGraphemeStartFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkUnicode::hasControlFlag(SkUnicode::CodeUnitFlags flags) {
     *     return (flags & SkUnicode::kControl) == SkUnicode::kControl;
     * }
     * ```
     */
    public fun hasControlFlag(flags: CodeUnitFlags): Boolean {
      TODO("Implement hasControlFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkUnicode::hasPartOfWhiteSpaceBreakFlag(SkUnicode::CodeUnitFlags flags) {
     *     return (flags & SkUnicode::kPartOfWhiteSpaceBreak) == SkUnicode::kPartOfWhiteSpaceBreak;
     * }
     * ```
     */
    public fun hasPartOfWhiteSpaceBreakFlag(flags: CodeUnitFlags): Boolean {
      TODO("Implement hasPartOfWhiteSpaceBreakFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool extractBidi(const char utf8[],
     *                                 int utf8Units,
     *                                 TextDirection dir,
     *                                 std::vector<BidiRegion>* bidiRegions)
     * ```
     */
    public fun extractBidi(
      utf8: CharArray,
      utf8Units: Int,
      dir: TextDirection,
      bidiRegions: List<BidiRegion>?,
    ): Boolean {
      TODO("Implement extractBidi")
    }

    /**
     * C++ original:
     * ```cpp
     * SkString SkUnicode::convertUtf16ToUtf8(const char16_t* utf16Char, int utf16Units) {
     *     const uint16_t* utf16 = reinterpret_cast<const uint16_t*>(utf16Char);
     *     int utf8Units = SkUTF::UTF16ToUTF8(nullptr, 0, utf16, utf16Units);
     *     if (utf8Units < 0) {
     *         SkDEBUGF("Convert error: Invalid utf16 input");
     *         return SkString();
     *     }
     *     SkString s(utf8Units);
     *     SkDEBUGCODE(int dstLen =) SkUTF::UTF16ToUTF8(s.data(), utf8Units, utf16, utf16Units);
     *     SkASSERT(dstLen == utf8Units);
     *     return s;
     * }
     * ```
     */
    public fun convertUtf16ToUtf8(utf16: Char?, utf16Units: Int): Int {
      TODO("Implement convertUtf16ToUtf8")
    }

    /**
     * C++ original:
     * ```cpp
     * SkString SkUnicode::convertUtf16ToUtf8(const std::u16string& utf16) {
     *     return convertUtf16ToUtf8(utf16.c_str(), utf16.size());
     * }
     * ```
     */
    public fun convertUtf16ToUtf8(utf16: u16string): Int {
      TODO("Implement convertUtf16ToUtf8")
    }

    /**
     * C++ original:
     * ```cpp
     * std::u16string SkUnicode::convertUtf8ToUtf16(const char* utf8, int utf8Units) {
     *     int utf16Units = SkUTF::UTF8ToUTF16(nullptr, 0, utf8, utf8Units);
     *     if (utf16Units < 0) {
     *         SkDEBUGF("Convert error: Invalid utf8 input");
     *         return std::u16string();
     *     }
     *     std::u16string utf16Char(utf16Units, '\0');
     *     uint16_t* utf16 = reinterpret_cast<uint16_t*>(utf16Char.data());
     *     SkDEBUGCODE(int dstLen =) SkUTF::UTF8ToUTF16(utf16, utf16Units, utf8, utf8Units);
     *     SkASSERT(dstLen == utf16Units);
     *     return utf16Char;
     * }
     * ```
     */
    public fun convertUtf8ToUtf16(utf8: String?, utf8Units: Int): Int {
      TODO("Implement convertUtf8ToUtf16")
    }

    /**
     * C++ original:
     * ```cpp
     * std::u16string SkUnicode::convertUtf8ToUtf16(const SkString& utf8) {
     *     return convertUtf8ToUtf16(utf8.c_str(), utf8.size());
     * }
     * ```
     */
    public fun convertUtf8ToUtf16(utf8: String): Int {
      TODO("Implement convertUtf8ToUtf16")
    }

    /**
     * C++ original:
     * ```cpp
     *         template <typename Appender8, typename Appender16>
     *         static bool extractUtfConversionMapping(SkSpan<const char> utf8, Appender8&& appender8, Appender16&& appender16) {
     *             size_t size8 = 0;
     *             size_t size16 = 0;
     *             auto ptr = utf8.data();
     *             auto end = ptr + utf8.size();
     *             while (ptr < end) {
     *
     *                 size_t index = SkToSizeT(ptr - utf8.data());
     *                 SkUnichar u = SkUTF::NextUTF8(&ptr, end);
     *
     *                 // All UTF8 code units refer to the same codepoint
     *                 size_t next = SkToSizeT(ptr - utf8.data());
     *                 for (auto i = index; i < next; ++i) {
     *                     //fUTF16IndexForUTF8Index.emplace_back(fUTF8IndexForUTF16Index.size());
     *                     appender16(size8);
     *                     ++size16;
     *                 }
     *                 //SkASSERT(fUTF16IndexForUTF8Index.size() == next);
     *                 SkASSERT(size16 == next);
     *                 if (size16 != next) {
     *                     return false;
     *                 }
     *
     *                 // One or two UTF16 code units refer to the same codepoint
     *                 uint16_t buffer[2];
     *                 size_t count = SkUTF::ToUTF16(u, buffer);
     *                 //fUTF8IndexForUTF16Index.emplace_back(index);
     *                 appender8(index);
     *                 ++size8;
     *                 if (count > 1) {
     *                     //fUTF8IndexForUTF16Index.emplace_back(index);
     *                     appender8(index);
     *                     ++size8;
     *                 }
     *             }
     *             //fUTF16IndexForUTF8Index.emplace_back(fUTF8IndexForUTF16Index.size());
     *             appender16(size8);
     *             ++size16;
     *             //fUTF8IndexForUTF16Index.emplace_back(fText.size());
     *             appender8(utf8.size());
     *             ++size8;
     *
     *             return true;
     *         }
     * ```
     */
    public fun <Appender8, Appender16> extractUtfConversionMapping(
      utf8: SkSpan<Char>,
      appender8: Appender8,
      appender16: Appender16,
    ): Boolean {
      TODO("Implement extractUtfConversionMapping")
    }
  }
}
