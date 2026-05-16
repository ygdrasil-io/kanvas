package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class FontMgrRunIterator final : public SkShaper::FontRunIterator {
 * public:
 *     FontMgrRunIterator(const char* utf8, size_t utf8Bytes,
 *                        const SkFont& font, sk_sp<SkFontMgr> fallbackMgr,
 *                        const char* requestName, SkFontStyle requestStyle,
 *                        const SkShaper::LanguageRunIterator* lang)
 *         : fCurrent(utf8), fBegin(utf8), fEnd(fCurrent + utf8Bytes)
 *         , fFallbackMgr(std::move(fallbackMgr))
 *         , fFont(font)
 *         , fFallbackFont(fFont)
 *         , fCurrentFont(nullptr)
 *         , fRequestName(requestName)
 *         , fRequestStyle(requestStyle)
 *         , fLanguage(lang)
 *     {
 *         // If fallback is not wanted, clients should use TrivialFontRunIterator.
 *         SkASSERT(fFallbackMgr);
 *         fFont.setTypeface(font.refTypeface());
 *         fFallbackFont.setTypeface(nullptr);
 *     }
 *     FontMgrRunIterator(const char* utf8, size_t utf8Bytes,
 *                        const SkFont& font, sk_sp<SkFontMgr> fallbackMgr)
 *         : FontMgrRunIterator(utf8, utf8Bytes, font, std::move(fallbackMgr),
 *                              nullptr, font.getTypeface()->fontStyle(), nullptr)
 *     {}
 *
 *     void consume() override {
 *         SkASSERT(fCurrent < fEnd);
 *         SkASSERT(!fLanguage || this->endOfCurrentRun() <= fLanguage->endOfCurrentRun());
 *         SkUnichar u = utf8_next(&fCurrent, fEnd);
 *         // If the starting typeface can handle this character, use it.
 *         if (fFont.unicharToGlyph(u)) {
 *             fCurrentFont = &fFont;
 *         // If the current fallback can handle this character, use it.
 *         } else if (fFallbackFont.getTypeface() && fFallbackFont.unicharToGlyph(u)) {
 *             fCurrentFont = &fFallbackFont;
 *         // If not, try to find a fallback typeface
 *         } else {
 *             const char* language = fLanguage ? fLanguage->currentLanguage() : nullptr;
 *             int languageCount = fLanguage ? 1 : 0;
 *             sk_sp<SkTypeface> candidate(fFallbackMgr->matchFamilyStyleCharacter(
 *                 fRequestName, fRequestStyle, &language, languageCount, u));
 *             if (candidate) {
 *                 fFallbackFont.setTypeface(std::move(candidate));
 *                 fCurrentFont = &fFallbackFont;
 *             } else {
 *                 fCurrentFont = &fFont;
 *             }
 *         }
 *
 *         while (fCurrent < fEnd) {
 *             const char* prev = fCurrent;
 *             u = utf8_next(&fCurrent, fEnd);
 *
 *             // End run if not using initial typeface and initial typeface has this character.
 *             if (fCurrentFont->getTypeface() != fFont.getTypeface() && fFont.unicharToGlyph(u)) {
 *                 fCurrent = prev;
 *                 return;
 *             }
 *
 *             // End run if current typeface does not have this character and some other font does.
 *             if (!fCurrentFont->unicharToGlyph(u)) {
 *                 const char* language = fLanguage ? fLanguage->currentLanguage() : nullptr;
 *                 int languageCount = fLanguage ? 1 : 0;
 *                 sk_sp<SkTypeface> candidate(fFallbackMgr->matchFamilyStyleCharacter(
 *                     fRequestName, fRequestStyle, &language, languageCount, u));
 *                 if (candidate) {
 *                     fCurrent = prev;
 *                     return;
 *                 }
 *             }
 *         }
 *     }
 *     size_t endOfCurrentRun() const override {
 *         return fCurrent - fBegin;
 *     }
 *     bool atEnd() const override {
 *         return fCurrent == fEnd;
 *     }
 *
 *     const SkFont& currentFont() const override {
 *         return *fCurrentFont;
 *     }
 *
 * private:
 *     char const * fCurrent;
 *     char const * const fBegin;
 *     char const * const fEnd;
 *     sk_sp<SkFontMgr> const fFallbackMgr;
 *     SkFont fFont;
 *     SkFont fFallbackFont;
 *     SkFont* fCurrentFont;
 *     char const * const fRequestName;
 *     SkFontStyle const fRequestStyle;
 *     SkShaper::LanguageRunIterator const * const fLanguage;
 * }
 * ```
 */
public class FontMgrRunIterator public constructor(
  utf8: String?,
  utf8Bytes: ULong,
  font: SkFont,
  fallbackMgr: SkSp<SkFontMgr>,
  requestName: String?,
  requestStyle: SkFontStyle,
  lang: SkShaper.LanguageRunIterator?,
) : SkShaper.FontRunIterator() {
  /**
   * C++ original:
   * ```cpp
   * char const * fCurrent
   * ```
   */
  private val fCurrent: String? = TODO("Initialize fCurrent")

  /**
   * C++ original:
   * ```cpp
   * char const * const fBegin
   * ```
   */
  private val fBegin: String? = TODO("Initialize fBegin")

  /**
   * C++ original:
   * ```cpp
   * char const * const fEnd
   * ```
   */
  private val fEnd: String? = TODO("Initialize fEnd")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> const fFallbackMgr
   * ```
   */
  private val fFallbackMgr: SkSp<SkFontMgr> = TODO("Initialize fFallbackMgr")

  /**
   * C++ original:
   * ```cpp
   * SkFont fFont
   * ```
   */
  private var fFont: SkFont = TODO("Initialize fFont")

  /**
   * C++ original:
   * ```cpp
   * SkFont fFallbackFont
   * ```
   */
  private var fFallbackFont: SkFont = TODO("Initialize fFallbackFont")

  /**
   * C++ original:
   * ```cpp
   * SkFont* fCurrentFont
   * ```
   */
  private var fCurrentFont: SkFont? = TODO("Initialize fCurrentFont")

  /**
   * C++ original:
   * ```cpp
   * char const * const fRequestName
   * ```
   */
  private val fRequestName: String? = TODO("Initialize fRequestName")

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle const fRequestStyle
   * ```
   */
  private val fRequestStyle: SkFontStyle = TODO("Initialize fRequestStyle")

  /**
   * C++ original:
   * ```cpp
   * SkShaper::LanguageRunIterator const * const fLanguage
   * ```
   */
  private val fLanguage: SkShaper.LanguageRunIterator? = TODO("Initialize fLanguage")

  /**
   * C++ original:
   * ```cpp
   * FontMgrRunIterator(const char* utf8, size_t utf8Bytes,
   *                        const SkFont& font, sk_sp<SkFontMgr> fallbackMgr,
   *                        const char* requestName, SkFontStyle requestStyle,
   *                        const SkShaper::LanguageRunIterator* lang)
   *         : fCurrent(utf8), fBegin(utf8), fEnd(fCurrent + utf8Bytes)
   *         , fFallbackMgr(std::move(fallbackMgr))
   *         , fFont(font)
   *         , fFallbackFont(fFont)
   *         , fCurrentFont(nullptr)
   *         , fRequestName(requestName)
   *         , fRequestStyle(requestStyle)
   *         , fLanguage(lang)
   *     {
   *         // If fallback is not wanted, clients should use TrivialFontRunIterator.
   *         SkASSERT(fFallbackMgr);
   *         fFont.setTypeface(font.refTypeface());
   *         fFallbackFont.setTypeface(nullptr);
   *     }
   * ```
   */
  public constructor(
    utf8: String?,
    utf8Bytes: ULong,
    font: SkFont,
    fallbackMgr: SkSp<SkFontMgr>,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void consume() override {
   *         SkASSERT(fCurrent < fEnd);
   *         SkASSERT(!fLanguage || this->endOfCurrentRun() <= fLanguage->endOfCurrentRun());
   *         SkUnichar u = utf8_next(&fCurrent, fEnd);
   *         // If the starting typeface can handle this character, use it.
   *         if (fFont.unicharToGlyph(u)) {
   *             fCurrentFont = &fFont;
   *         // If the current fallback can handle this character, use it.
   *         } else if (fFallbackFont.getTypeface() && fFallbackFont.unicharToGlyph(u)) {
   *             fCurrentFont = &fFallbackFont;
   *         // If not, try to find a fallback typeface
   *         } else {
   *             const char* language = fLanguage ? fLanguage->currentLanguage() : nullptr;
   *             int languageCount = fLanguage ? 1 : 0;
   *             sk_sp<SkTypeface> candidate(fFallbackMgr->matchFamilyStyleCharacter(
   *                 fRequestName, fRequestStyle, &language, languageCount, u));
   *             if (candidate) {
   *                 fFallbackFont.setTypeface(std::move(candidate));
   *                 fCurrentFont = &fFallbackFont;
   *             } else {
   *                 fCurrentFont = &fFont;
   *             }
   *         }
   *
   *         while (fCurrent < fEnd) {
   *             const char* prev = fCurrent;
   *             u = utf8_next(&fCurrent, fEnd);
   *
   *             // End run if not using initial typeface and initial typeface has this character.
   *             if (fCurrentFont->getTypeface() != fFont.getTypeface() && fFont.unicharToGlyph(u)) {
   *                 fCurrent = prev;
   *                 return;
   *             }
   *
   *             // End run if current typeface does not have this character and some other font does.
   *             if (!fCurrentFont->unicharToGlyph(u)) {
   *                 const char* language = fLanguage ? fLanguage->currentLanguage() : nullptr;
   *                 int languageCount = fLanguage ? 1 : 0;
   *                 sk_sp<SkTypeface> candidate(fFallbackMgr->matchFamilyStyleCharacter(
   *                     fRequestName, fRequestStyle, &language, languageCount, u));
   *                 if (candidate) {
   *                     fCurrent = prev;
   *                     return;
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  public override fun consume() {
    TODO("Implement consume")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t endOfCurrentRun() const override {
   *         return fCurrent - fBegin;
   *     }
   * ```
   */
  public override fun endOfCurrentRun(): Int {
    TODO("Implement endOfCurrentRun")
  }

  /**
   * C++ original:
   * ```cpp
   * bool atEnd() const override {
   *         return fCurrent == fEnd;
   *     }
   * ```
   */
  public override fun atEnd(): Boolean {
    TODO("Implement atEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFont& currentFont() const override {
   *         return *fCurrentFont;
   *     }
   * ```
   */
  public override fun currentFont(): SkFont {
    TODO("Implement currentFont")
  }
}
