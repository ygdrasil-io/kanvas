package org.skia.modules

import BidiRegion
import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.UShort
import kotlin.collections.List
import org.skia.foundation.SkSp
import org.skia.foundation.SkUnichar
import org.skia.sksl.Position
import skia_private.TArraytrue
import undefined.BidiLevel
import undefined.BreakType

/**
 * C++ original:
 * ```cpp
 * class StrictLinebreakUnicode final : public SkUnicode {
 * public:
 *     explicit StrictLinebreakUnicode(sk_sp<SkUnicode> uc) : fUnicode(std::move(uc))
 *     {
 *         SkASSERT(fUnicode);
 *     }
 *
 *     std::unique_ptr<SkBreakIterator> makeBreakIterator(const char locale[],
 *                                                        BreakType breakType) override {
 *         std::unique_ptr<SkBreakIterator> brk = fUnicode->makeBreakIterator(locale, breakType);
 *         if (!brk || breakType != BreakType::kLines) {
 *             return brk;
 *         }
 *
 *         // When requested a line break iterator, return a composite iterator which prevents
 *         // breaking mid-word.
 *         std::unique_ptr<SkBreakIterator> wbrk =
 *             fUnicode->makeBreakIterator(locale, BreakType::kWords);
 *
 *         return wbrk
 *             ? std::make_unique<IntersectingBreakIterator>(std::move(brk), std::move(wbrk))
 *             : std::move(brk);
 *     }
 *
 *     // Proxy everything else.
 *     SkString toUpper(const SkString& str) override { return fUnicode->toUpper(str); }
 *     SkString toUpper(const SkString& str, const char* locale) override {
 *         return fUnicode->toUpper(str, locale);
 *     }
 *     bool isControl(SkUnichar utf8) override { return fUnicode->isControl(utf8); }
 *     bool isWhitespace(SkUnichar utf8) override { return fUnicode->isWhitespace(utf8); }
 *     bool isSpace(SkUnichar utf8) override { return fUnicode->isSpace(utf8); }
 *     bool isTabulation(SkUnichar utf8) override { return fUnicode->isTabulation(utf8); }
 *     bool isHardBreak(SkUnichar utf8) override { return fUnicode->isHardBreak(utf8); }
 *     bool isEmoji(SkUnichar utf8) override { return fUnicode->isEmoji(utf8); }
 *     bool isEmojiComponent(SkUnichar utf8) override { return fUnicode->isEmojiComponent(utf8); }
 *     bool isEmojiModifierBase(SkUnichar utf8) override {
 *         return fUnicode->isEmojiModifierBase(utf8);
 *     }
 *     bool isEmojiModifier(SkUnichar utf8) override { return fUnicode->isEmojiModifier(utf8); }
 *     bool isRegionalIndicator(SkUnichar utf8) override {
 *         return fUnicode->isRegionalIndicator(utf8);
 *     }
 *     bool isIdeographic(SkUnichar utf8) override { return fUnicode->isIdeographic(utf8); }
 *     std::unique_ptr<SkBidiIterator> makeBidiIterator(const uint16_t text[],
 *                                                      int count,
 *                                                      SkBidiIterator::Direction dir) override {
 *         return fUnicode->makeBidiIterator(text, count, dir);
 *     }
 *     std::unique_ptr<SkBidiIterator> makeBidiIterator(const char text[],
 *                                                      int count,
 *                                                      SkBidiIterator::Direction dir) override {
 *         return fUnicode->makeBidiIterator(text, count, dir);
 *     }
 *     std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType breakType) override {
 *         return fUnicode->makeBreakIterator(breakType);
 *     }
 *     bool getBidiRegions(const char utf8[],
 *                         int utf8Units,
 *                         TextDirection dir,
 *                         std::vector<BidiRegion>* results) override {
 *         return fUnicode->getBidiRegions(utf8, utf8Units, dir, results);
 *     }
 *     bool getWords(const char utf8[], int utf8Units, const char* locale,
 *                   std::vector<Position>* results) override {
 *         return fUnicode->getWords(utf8, utf8Units, locale, results);
 *     }
 *     bool getUtf8Words(const char utf8[],
 *                       int utf8Units,
 *                       const char* locale,
 *                       std::vector<Position>* results) override {
 *         return fUnicode->getUtf8Words(utf8, utf8Units, locale, results);
 *     }
 *     bool getSentences(const char utf8[],
 *                       int utf8Units,
 *                       const char* locale,
 *                       std::vector<Position>* results) override {
 *         return fUnicode->getSentences(utf8, utf8Units, locale, results);
 *     }
 *     bool computeCodeUnitFlags(char utf8[], int utf8Units, bool replaceTabs,
 *             skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) override {
 *         return fUnicode->computeCodeUnitFlags(utf8, utf8Units, replaceTabs, results);
 *     }
 *     bool computeCodeUnitFlags(char16_t utf16[], int utf16Units, bool replaceTabs,
 *             skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) override {
 *         return fUnicode->computeCodeUnitFlags(utf16, utf16Units, replaceTabs, results);
 *     }
 *
 *     void reorderVisual(const BidiLevel runLevels[],
 *                        int levelsCount,
 *                        int32_t logicalFromVisual[]) override {
 *         return fUnicode->reorderVisual(runLevels, levelsCount, logicalFromVisual);
 *     }
 *
 * private:
 *     const sk_sp<SkUnicode> fUnicode;
 * }
 * ```
 */
public class StrictLinebreakUnicode public constructor(
  uc: SkSp<SkUnicode>,
) : SkUnicode() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkUnicode> fUnicode
   * ```
   */
  private val fUnicode: SkSp<SkUnicode> = TODO("Initialize fUnicode")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBreakIterator> makeBreakIterator(const char locale[],
   *                                                        BreakType breakType) override {
   *         std::unique_ptr<SkBreakIterator> brk = fUnicode->makeBreakIterator(locale, breakType);
   *         if (!brk || breakType != BreakType::kLines) {
   *             return brk;
   *         }
   *
   *         // When requested a line break iterator, return a composite iterator which prevents
   *         // breaking mid-word.
   *         std::unique_ptr<SkBreakIterator> wbrk =
   *             fUnicode->makeBreakIterator(locale, BreakType::kWords);
   *
   *         return wbrk
   *             ? std::make_unique<IntersectingBreakIterator>(std::move(brk), std::move(wbrk))
   *             : std::move(brk);
   *     }
   * ```
   */
  public override fun makeBreakIterator(locale: CharArray, breakType: BreakType): Int {
    TODO("Implement makeBreakIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toUpper(const SkString& str) override { return fUnicode->toUpper(str); }
   * ```
   */
  public override fun toUpper(str: String): String {
    TODO("Implement toUpper")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString toUpper(const SkString& str, const char* locale) override {
   *         return fUnicode->toUpper(str, locale);
   *     }
   * ```
   */
  public override fun toUpper(str: String, locale: String?): String {
    TODO("Implement toUpper")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isControl(SkUnichar utf8) override { return fUnicode->isControl(utf8); }
   * ```
   */
  public override fun isControl(utf8: SkUnichar): Boolean {
    TODO("Implement isControl")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isWhitespace(SkUnichar utf8) override { return fUnicode->isWhitespace(utf8); }
   * ```
   */
  public override fun isWhitespace(utf8: SkUnichar): Boolean {
    TODO("Implement isWhitespace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isSpace(SkUnichar utf8) override { return fUnicode->isSpace(utf8); }
   * ```
   */
  public override fun isSpace(utf8: SkUnichar): Boolean {
    TODO("Implement isSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTabulation(SkUnichar utf8) override { return fUnicode->isTabulation(utf8); }
   * ```
   */
  public override fun isTabulation(utf8: SkUnichar): Boolean {
    TODO("Implement isTabulation")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isHardBreak(SkUnichar utf8) override { return fUnicode->isHardBreak(utf8); }
   * ```
   */
  public override fun isHardBreak(utf8: SkUnichar): Boolean {
    TODO("Implement isHardBreak")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmoji(SkUnichar utf8) override { return fUnicode->isEmoji(utf8); }
   * ```
   */
  public override fun isEmoji(utf8: SkUnichar): Boolean {
    TODO("Implement isEmoji")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmojiComponent(SkUnichar utf8) override { return fUnicode->isEmojiComponent(utf8); }
   * ```
   */
  public override fun isEmojiComponent(utf8: SkUnichar): Boolean {
    TODO("Implement isEmojiComponent")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmojiModifierBase(SkUnichar utf8) override {
   *         return fUnicode->isEmojiModifierBase(utf8);
   *     }
   * ```
   */
  public override fun isEmojiModifierBase(utf8: SkUnichar): Boolean {
    TODO("Implement isEmojiModifierBase")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmojiModifier(SkUnichar utf8) override { return fUnicode->isEmojiModifier(utf8); }
   * ```
   */
  public override fun isEmojiModifier(utf8: SkUnichar): Boolean {
    TODO("Implement isEmojiModifier")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRegionalIndicator(SkUnichar utf8) override {
   *         return fUnicode->isRegionalIndicator(utf8);
   *     }
   * ```
   */
  public override fun isRegionalIndicator(utf8: SkUnichar): Boolean {
    TODO("Implement isRegionalIndicator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isIdeographic(SkUnichar utf8) override { return fUnicode->isIdeographic(utf8); }
   * ```
   */
  public override fun isIdeographic(utf8: SkUnichar): Boolean {
    TODO("Implement isIdeographic")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBidiIterator> makeBidiIterator(const uint16_t text[],
   *                                                      int count,
   *                                                      SkBidiIterator::Direction dir) override {
   *         return fUnicode->makeBidiIterator(text, count, dir);
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
   *         return fUnicode->makeBidiIterator(text, count, dir);
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
   * std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType breakType) override {
   *         return fUnicode->makeBreakIterator(breakType);
   *     }
   * ```
   */
  public override fun makeBreakIterator(breakType: BreakType): Int {
    TODO("Implement makeBreakIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getBidiRegions(const char utf8[],
   *                         int utf8Units,
   *                         TextDirection dir,
   *                         std::vector<BidiRegion>* results) override {
   *         return fUnicode->getBidiRegions(utf8, utf8Units, dir, results);
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
   *         return fUnicode->getWords(utf8, utf8Units, locale, results);
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
   *         return fUnicode->getUtf8Words(utf8, utf8Units, locale, results);
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
   *                       std::vector<Position>* results) override {
   *         return fUnicode->getSentences(utf8, utf8Units, locale, results);
   *     }
   * ```
   */
  public override fun getSentences(
    utf8: CharArray,
    utf8Units: Int,
    locale: String?,
    results: List<Position>?,
  ): Boolean {
    TODO("Implement getSentences")
  }

  /**
   * C++ original:
   * ```cpp
   * bool computeCodeUnitFlags(char utf8[], int utf8Units, bool replaceTabs,
   *             skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) override {
   *         return fUnicode->computeCodeUnitFlags(utf8, utf8Units, replaceTabs, results);
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
   *             skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) override {
   *         return fUnicode->computeCodeUnitFlags(utf16, utf16Units, replaceTabs, results);
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
}
