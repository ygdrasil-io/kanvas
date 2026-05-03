package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class LangIterator final : public SkShaper::LanguageRunIterator {
 * public:
 *     LangIterator(SkSpan<const char> utf8, SkSpan<Block> styles, const TextStyle& defaultStyle)
 *             : fText(utf8)
 *             , fTextStyles(styles)
 *             , fCurrentChar(utf8.data())
 *             , fCurrentStyle(fTextStyles.data())
 *             , fCurrentLocale(defaultStyle.getLocale()) {}
 *
 *     void consume() override {
 *         const char* textEnd = fText.data() + fText.size();
 *         const Block* stylesEnd = fTextStyles.data() + fTextStyles.size();
 *
 *         SkASSERT(fCurrentChar < textEnd);
 *
 *         if (fCurrentStyle == stylesEnd) {
 *             fCurrentChar = textEnd;
 *             return;
 *         }
 *
 *         fCurrentChar = fText.data() + fCurrentStyle->fRange.end;
 *         fCurrentLocale = fCurrentStyle->fStyle.getLocale();
 *         while (++fCurrentStyle != stylesEnd && !fCurrentStyle->fStyle.isPlaceholder()) {
 *             if (fCurrentStyle->fStyle.getLocale() != fCurrentLocale) {
 *                 break;
 *             }
 *             fCurrentChar = fText.data() + fCurrentStyle->fRange.end;
 *         }
 *     }
 *
 *     size_t endOfCurrentRun() const override { return fCurrentChar - fText.data(); }
 *     bool atEnd() const override { return fCurrentChar >= fText.data() + fText.size(); }
 *     const char* currentLanguage() const override { return fCurrentLocale.c_str(); }
 *
 * private:
 *     SkSpan<const char> fText;
 *     SkSpan<Block> fTextStyles;
 *     const char* fCurrentChar;
 *     Block* fCurrentStyle;
 *     SkString fCurrentLocale;
 * }
 * ```
 */
public class LangIterator : SkShaper.LanguageRunIterator() {
  /**
   * C++ original:
   * ```cpp
   * LangIterator(SkSpan<const char> utf8, SkSpan<Block> styles, const TextStyle& defaultStyle)
   * ```
   */
  public var skSpan: LangIterator = TODO("Initialize skSpan")

  /**
   * C++ original:
   * ```cpp
   * LangIterator(SkSpan<const char> utf8, SkSpan<Block> styles, const TextStyle& defaultStyle)
   *             : fText(utf8)
   *             , fTextStyles(styles)
   * ```
   */
  public fun fTextStyles(param0: Int): LangIterator {
    TODO("Implement fTextStyles")
  }

  /**
   * C++ original:
   * ```cpp
   * LangIterator(SkSpan<const char> utf8, SkSpan<Block> styles, const TextStyle& defaultStyle)
   *             : fText(utf8)
   *             , fTextStyles(styles)
   *             , fCurrentChar(utf8.data())
   * ```
   */
  public fun fCurrentChar(param0: Int): LangIterator {
    TODO("Implement fCurrentChar")
  }

  /**
   * C++ original:
   * ```cpp
   * LangIterator(SkSpan<const char> utf8, SkSpan<Block> styles, const TextStyle& defaultStyle)
   *             : fText(utf8)
   *             , fTextStyles(styles)
   *             , fCurrentChar(utf8.data())
   *             , fCurrentStyle(fTextStyles.data())
   * ```
   */
  public fun fCurrentStyle(param0: Int): LangIterator {
    TODO("Implement fCurrentStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * LangIterator(SkSpan<const char> utf8, SkSpan<Block> styles, const TextStyle& defaultStyle)
   *             : fText(utf8)
   *             , fTextStyles(styles)
   *             , fCurrentChar(utf8.data())
   *             , fCurrentStyle(fTextStyles.data())
   *             , fCurrentLocale(defaultStyle.getLocale())
   * ```
   */
  public fun fCurrentLocale(param0: Int): LangIterator {
    TODO("Implement fCurrentLocale")
  }
}
