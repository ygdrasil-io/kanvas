package org.skia.core

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFontStyleSet : public SkRefCnt {
 * public:
 *     virtual int count() = 0;
 *     virtual void getStyle(int index, SkFontStyle*, SkString* style) = 0;
 *     virtual sk_sp<SkTypeface> createTypeface(int index) = 0;
 *     virtual sk_sp<SkTypeface> matchStyle(const SkFontStyle& pattern) = 0;
 *
 *     static sk_sp<SkFontStyleSet> CreateEmpty();
 *
 * protected:
 *     sk_sp<SkTypeface> matchStyleCSS3(const SkFontStyle& pattern);
 * }
 * ```
 */
public abstract class SkFontStyleSet : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual int count() = 0
   * ```
   */
  public abstract fun count(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void getStyle(int index, SkFontStyle*, SkString* style) = 0
   * ```
   */
  public abstract fun getStyle(
    index: Int,
    param1: SkFontStyle?,
    style: String?,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> createTypeface(int index) = 0
   * ```
   */
  public abstract fun createTypeface(index: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> matchStyle(const SkFontStyle& pattern) = 0
   * ```
   */
  public abstract fun matchStyle(pattern: SkFontStyle): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkFontStyleSet::matchStyleCSS3(const SkFontStyle& pattern) {
   *     int count = this->count();
   *     if (0 == count) {
   *         return nullptr;
   *     }
   *
   *     struct Score {
   *         int score;
   *         int index;
   *         Score& operator +=(int rhs) { this->score += rhs; return *this; }
   *         Score& operator <<=(int rhs) { this->score <<= rhs; return *this; }
   *         bool operator <(const Score& that) { return this->score < that.score; }
   *     };
   *
   *     Score maxScore = { 0, 0 };
   *     for (int i = 0; i < count; ++i) {
   *         SkFontStyle current;
   *         this->getStyle(i, &current, nullptr);
   *         Score currentScore = { 0, i };
   *
   *         // CSS stretch / SkFontStyle::Width
   *         // Takes priority over everything else.
   *         if (pattern.width() <= SkFontStyle::kNormal_Width) {
   *             if (current.width() <= pattern.width()) {
   *                 currentScore += 10 - pattern.width() + current.width();
   *             } else {
   *                 currentScore += 10 - current.width();
   *             }
   *         } else {
   *             if (current.width() > pattern.width()) {
   *                 currentScore += 10 + pattern.width() - current.width();
   *             } else {
   *                 currentScore += current.width();
   *             }
   *         }
   *         currentScore <<= 8;
   *
   *         // CSS style (normal, italic, oblique) / SkFontStyle::Slant (upright, italic, oblique)
   *         // Takes priority over all valid weights.
   *         static_assert(SkFontStyle::kUpright_Slant == 0 &&
   *                       SkFontStyle::kItalic_Slant  == 1 &&
   *                       SkFontStyle::kOblique_Slant == 2,
   *                       "SkFontStyle::Slant values not as required.");
   *         SkASSERT(0 <= pattern.slant() && pattern.slant() <= 2 &&
   *                  0 <= current.slant() && current.slant() <= 2);
   *         static const int score[3][3] = {
   *             /*               Upright Italic Oblique  [current]*/
   *             /*   Upright */ {   3   ,  1   ,   2   },
   *             /*   Italic  */ {   1   ,  3   ,   2   },
   *             /*   Oblique */ {   1   ,  2   ,   3   },
   *             /* [pattern] */
   *         };
   *         currentScore += score[pattern.slant()][current.slant()];
   *         currentScore <<= 8;
   *
   *         // Synthetics (weight, style) [no stretch synthetic?]
   *
   *         // CSS weight / SkFontStyle::Weight
   *         // The 'closer' to the target weight, the higher the score.
   *         // 1000 is the 'heaviest' recognized weight
   *         if (pattern.weight() == current.weight()) {
   *             currentScore += 1000;
   *         // less than 400 prefer lighter weights
   *         } else if (pattern.weight() < 400) {
   *             if (current.weight() <= pattern.weight()) {
   *                 currentScore += 1000 - pattern.weight() + current.weight();
   *             } else {
   *                 currentScore += 1000 - current.weight();
   *             }
   *         // between 400 and 500 prefer heavier up to 500, then lighter weights
   *         } else if (pattern.weight() <= 500) {
   *             if (current.weight() >= pattern.weight() && current.weight() <= 500) {
   *                 currentScore += 1000 + pattern.weight() - current.weight();
   *             } else if (current.weight() <= pattern.weight()) {
   *                 currentScore += 500 + current.weight();
   *             } else {
   *                 currentScore += 1000 - current.weight();
   *             }
   *         // greater than 500 prefer heavier weights
   *         } else if (pattern.weight() > 500) {
   *             if (current.weight() > pattern.weight()) {
   *                 currentScore += 1000 + pattern.weight() - current.weight();
   *             } else {
   *                 currentScore += current.weight();
   *             }
   *         }
   *
   *         if (maxScore < currentScore) {
   *             maxScore = currentScore;
   *         }
   *     }
   *
   *     return this->createTypeface(maxScore.index);
   * }
   * ```
   */
  protected fun matchStyleCSS3(pattern: SkFontStyle): Int {
    TODO("Implement matchStyleCSS3")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFontStyleSet> SkFontStyleSet::CreateEmpty() {
     *     return sk_sp<SkFontStyleSet>(new SkEmptyFontStyleSet);
     * }
     * ```
     */
    public fun createEmpty(): Int {
      TODO("Implement createEmpty")
    }
  }
}
