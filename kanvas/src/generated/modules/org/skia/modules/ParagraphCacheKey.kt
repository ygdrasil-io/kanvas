package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.skia.core.TArraytrue

/**
 * C++ original:
 * ```cpp
 * class ParagraphCacheKey {
 * public:
 *     ParagraphCacheKey(const ParagraphImpl* paragraph)
 *         : fText(paragraph->fText.c_str(), paragraph->fText.size())
 *         , fPlaceholders(paragraph->fPlaceholders)
 *         , fTextStyles(paragraph->fTextStyles)
 *         , fParagraphStyle(paragraph->paragraphStyle()) {
 *         fHash = computeHash();
 *     }
 *
 *     ParagraphCacheKey(const ParagraphCacheKey& other) = default;
 *
 *     ParagraphCacheKey(ParagraphCacheKey&& other)
 *         : fText(std::move(other.fText))
 *         , fPlaceholders(std::move(other.fPlaceholders))
 *         , fTextStyles(std::move(other.fTextStyles))
 *         , fParagraphStyle(std::move(other.fParagraphStyle))
 *         , fHash(other.fHash) {
 *         other.fHash = 0;
 *     }
 *
 *     bool operator==(const ParagraphCacheKey& other) const;
 *
 *     uint32_t hash() const { return fHash; }
 *
 *     const SkString& text() const { return fText; }
 *
 * private:
 *     static uint32_t mix(uint32_t hash, uint32_t data);
 *     uint32_t computeHash() const;
 *
 *     SkString fText;
 *     TArray<Placeholder, true> fPlaceholders;
 *     TArray<Block, true> fTextStyles;
 *     ParagraphStyle fParagraphStyle;
 *     uint32_t fHash;
 * }
 * ```
 */
public data class ParagraphCacheKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString fText
   * ```
   */
  private var fText: String,
  /**
   * C++ original:
   * ```cpp
   * TArray<Placeholder, true> fPlaceholders
   * ```
   */
  private var fPlaceholders: TArraytrue<Placeholder>,
  /**
   * C++ original:
   * ```cpp
   * TArray<Block, true> fTextStyles
   * ```
   */
  private var fTextStyles: TArraytrue<Block>,
  /**
   * C++ original:
   * ```cpp
   * ParagraphStyle fParagraphStyle
   * ```
   */
  private var fParagraphStyle: ParagraphStyle,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fHash
   * ```
   */
  private var fHash: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * bool ParagraphCacheKey::operator==(const ParagraphCacheKey& other) const {
   *     if (fText.size() != other.fText.size()) {
   *         return false;
   *     }
   *     if (fPlaceholders.size() != other.fPlaceholders.size()) {
   *         return false;
   *     }
   *     if (fText != other.fText) {
   *         return false;
   *     }
   *     if (fTextStyles.size() != other.fTextStyles.size()) {
   *         return false;
   *     }
   *
   *     // There is no need to compare default paragraph styles - they are included into fTextStyles
   *     if (!exactlyEqual(fParagraphStyle.getHeight(), other.fParagraphStyle.getHeight())) {
   *         return false;
   *     }
   *     if (fParagraphStyle.getTextDirection() != other.fParagraphStyle.getTextDirection()) {
   *         return false;
   *     }
   *
   *     if (!(fParagraphStyle.getStrutStyle() == other.fParagraphStyle.getStrutStyle())) {
   *         return false;
   *     }
   *
   *     if (!(fParagraphStyle.getReplaceTabCharacters() == other.fParagraphStyle.getReplaceTabCharacters())) {
   *         return false;
   *     }
   *
   *     for (int i = 0; i < fTextStyles.size(); ++i) {
   *         auto& tsa = fTextStyles[i];
   *         auto& tsb = other.fTextStyles[i];
   *         if (tsa.fStyle.isPlaceholder()) {
   *             continue;
   *         }
   *         if (!(tsa.fStyle.equalsByFonts(tsb.fStyle))) {
   *             return false;
   *         }
   *         if (tsa.fRange.width() != tsb.fRange.width()) {
   *             return false;
   *         }
   *         if (tsa.fRange.start != tsb.fRange.start) {
   *             return false;
   *         }
   *     }
   *     for (int i = 0; i < fPlaceholders.size(); ++i) {
   *         auto& tsa = fPlaceholders[i];
   *         auto& tsb = other.fPlaceholders[i];
   *         if (tsa.fRange.width() == 0 && tsb.fRange.width() == 0) {
   *             continue;
   *         }
   *         if (!(tsa.fStyle.equals(tsb.fStyle))) {
   *             return false;
   *         }
   *         if (tsa.fRange.width() != tsb.fRange.width()) {
   *             return false;
   *         }
   *         if (tsa.fRange.start != tsb.fRange.start) {
   *             return false;
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t hash() const { return fHash; }
   * ```
   */
  public fun hash(): UInt {
    TODO("Implement hash")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& text() const { return fText; }
   * ```
   */
  public fun text(): String {
    TODO("Implement text")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t ParagraphCacheKey::computeHash() const {
   *     uint32_t hash = 0;
   *     for (auto& ph : fPlaceholders) {
   *         if (ph.fRange.width() == 0) {
   *             continue;
   *         }
   *         hash = mix(hash, SkGoodHash()(ph.fRange));
   *         hash = mix(hash, SkGoodHash()(relax(ph.fStyle.fHeight)));
   *         hash = mix(hash, SkGoodHash()(relax(ph.fStyle.fWidth)));
   *         hash = mix(hash, SkGoodHash()(ph.fStyle.fAlignment));
   *         hash = mix(hash, SkGoodHash()(ph.fStyle.fBaseline));
   *         if (ph.fStyle.fAlignment == PlaceholderAlignment::kBaseline) {
   *             hash = mix(hash, SkGoodHash()(relax(ph.fStyle.fBaselineOffset)));
   *         }
   *     }
   *
   *     for (auto& ts : fTextStyles) {
   *         if (ts.fStyle.isPlaceholder()) {
   *             continue;
   *         }
   *         hash = mix(hash, SkGoodHash()(relax(ts.fStyle.getLetterSpacing())));
   *         hash = mix(hash, SkGoodHash()(relax(ts.fStyle.getWordSpacing())));
   *         hash = mix(hash, SkGoodHash()(ts.fStyle.getLocale()));
   *         hash = mix(hash, SkGoodHash()(relax(ts.fStyle.getHeight())));
   *         hash = mix(hash, SkGoodHash()(relax(ts.fStyle.getBaselineShift())));
   *         for (auto& ff : ts.fStyle.getFontFamilies()) {
   *             hash = mix(hash, SkGoodHash()(ff));
   *         }
   *         for (auto& ff : ts.fStyle.getFontFeatures()) {
   *             hash = mix(hash, SkGoodHash()(ff.fValue));
   *             hash = mix(hash, SkGoodHash()(ff.fName));
   *         }
   *         hash = mix(hash, std::hash<std::optional<FontArguments>>()(ts.fStyle.getFontArguments()));
   *         hash = mix(hash, SkGoodHash()(ts.fStyle.getFontStyle()));
   *         hash = mix(hash, SkGoodHash()(relax(ts.fStyle.getFontSize())));
   *         hash = mix(hash, SkGoodHash()(ts.fRange));
   *     }
   *
   *     hash = mix(hash, SkGoodHash()(relax(fParagraphStyle.getHeight())));
   *     hash = mix(hash, SkGoodHash()(fParagraphStyle.getTextDirection()));
   *     hash = mix(hash, SkGoodHash()(fParagraphStyle.getReplaceTabCharacters() ? 1 : 0));
   *
   *     auto& strutStyle = fParagraphStyle.getStrutStyle();
   *     if (strutStyle.getStrutEnabled()) {
   *         hash = mix(hash, SkGoodHash()(relax(strutStyle.getHeight())));
   *         hash = mix(hash, SkGoodHash()(relax(strutStyle.getLeading())));
   *         hash = mix(hash, SkGoodHash()(relax(strutStyle.getFontSize())));
   *         hash = mix(hash, SkGoodHash()(strutStyle.getHeightOverride()));
   *         hash = mix(hash, SkGoodHash()(strutStyle.getFontStyle()));
   *         hash = mix(hash, SkGoodHash()(strutStyle.getForceStrutHeight()));
   *         for (auto& ff : strutStyle.getFontFamilies()) {
   *             hash = mix(hash, SkGoodHash()(ff));
   *         }
   *     }
   *
   *     hash = mix(hash, SkGoodHash()(fText));
   *     return hash;
   * }
   * ```
   */
  private fun computeHash(): UInt {
    TODO("Implement computeHash")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * uint32_t ParagraphCacheKey::mix(uint32_t hash, uint32_t data) {
     *     hash += data;
     *     hash += (hash << 10);
     *     hash ^= (hash >> 6);
     *     return hash;
     * }
     * ```
     */
    private fun mix(hash: UInt, `data`: UInt): UInt {
      TODO("Implement mix")
    }
  }
}
