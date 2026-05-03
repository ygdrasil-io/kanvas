package org.skia.modules

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.IntArray
import kotlin.UShort
import kotlin.collections.List
import org.skia.`external`.UBiDi
import org.skia.`external`.UBiDiLevel
import org.skia.`external`.UChar
import org.skia.`external`.UErrorCode
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SkBidiFactory : public SkRefCnt {
 * public:
 *     std::unique_ptr<SkBidiIterator> MakeIterator(const uint16_t utf16[],
 *                                                  int utf16Units,
 *                                                  SkBidiIterator::Direction dir) const;
 *     std::unique_ptr<SkBidiIterator> MakeIterator(const char utf8[],
 *                                                  int utf8Units,
 *                                                  SkBidiIterator::Direction dir) const;
 *     bool ExtractBidi(const char utf8[],
 *                      int utf8Units,
 *                      SkUnicode::TextDirection dir,
 *                      std::vector<SkUnicode::BidiRegion>* bidiRegions) const;
 *
 *     virtual const char* errorName(UErrorCode status) const = 0;
 *
 * using BidiCloseCallback = void(*)(UBiDi* bidi);
 *     virtual BidiCloseCallback bidi_close_callback() const = 0;
 *     virtual UBiDiDirection bidi_getDirection(const UBiDi* bidi) const = 0;
 *     virtual SkBidiIterator::Position bidi_getLength(const UBiDi* bidi) const = 0;
 *     virtual SkBidiIterator::Level bidi_getLevelAt(const UBiDi* bidi, int pos) const = 0;
 *     virtual UBiDi* bidi_openSized(int32_t maxLength,
 *                                   int32_t maxRunCount,
 *                                   UErrorCode* pErrorCode) const = 0;
 *     virtual void bidi_setPara(UBiDi* bidi,
 *                               const UChar* text,
 *                               int32_t length,
 *                               UBiDiLevel paraLevel,
 *                               UBiDiLevel* embeddingLevels,
 *                               UErrorCode* status) const = 0;
 *     virtual void bidi_reorderVisual(const SkUnicode::BidiLevel runLevels[],
 *                                     int levelsCount,
 *                                     int32_t logicalFromVisual[]) const = 0;
 * }
 * ```
 */
public abstract class SkBidiFactory : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBidiIterator> MakeIterator(const uint16_t utf16[],
   *                                                  int utf16Units,
   *                                                  SkBidiIterator::Direction dir) const
   * ```
   */
  public fun makeIterator(
    utf16: Array<UShort>,
    utf16Units: Int,
    dir: SkBidiIterator.Direction,
  ): Int {
    TODO("Implement makeIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkBidiIterator> MakeIterator(const char utf8[],
   *                                                  int utf8Units,
   *                                                  SkBidiIterator::Direction dir) const
   * ```
   */
  public fun makeIterator(
    utf8: CharArray,
    utf8Units: Int,
    dir: SkBidiIterator.Direction,
  ): Int {
    TODO("Implement makeIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBidiFactory::ExtractBidi(const char utf8[],
   *                                 int utf8Units,
   *                                 SkUnicode::TextDirection dir,
   *                                 std::vector<SkUnicode::BidiRegion>* bidiRegions) const {
   *     // Convert to UTF16 since for now bidi iterator only operates on utf16
   *     auto utf16 = SkUnicode::convertUtf8ToUtf16(utf8, utf8Units);
   *
   *     // Create bidi iterator
   *     UErrorCode status = U_ZERO_ERROR;
   *     SkUnicodeBidi bidi(this->bidi_openSized(utf16.size(), 0, &status), this->bidi_close_callback());
   *     if (U_FAILURE(status)) {
   *         SkDEBUGF("Bidi error: %s", this->errorName(status));
   *         return false;
   *     }
   *     SkASSERT(bidi);
   *     uint8_t bidiLevel = (dir == SkUnicode::TextDirection::kLTR) ? UBIDI_LTR : UBIDI_RTL;
   *     // The required lifetime of utf16 isn't well documented.
   *     // It appears it isn't used after ubidi_setPara except through ubidi_getText.
   *     this->bidi_setPara(
   *             bidi.get(), (const UChar*)utf16.c_str(), utf16.size(), bidiLevel, nullptr, &status);
   *     if (U_FAILURE(status)) {
   *         SkDEBUGF("Bidi error: %s", this->errorName(status));
   *         return false;
   *     }
   *
   *     // Iterate through bidi regions and the result positions into utf8
   *     const char* start8 = utf8;
   *     const char* end8 = utf8 + utf8Units;
   *     SkUnicode::BidiLevel currentLevel = 0;
   *
   *     SkUnicode::Position pos8 = 0;
   *     SkUnicode::Position pos16 = 0;
   *     SkUnicode::Position end16 = this->bidi_getLength(bidi.get());
   *
   *     if (end16 == 0) {
   *         return true;
   *     }
   *     if (this->bidi_getDirection(bidi.get()) != UBIDI_MIXED) {
   *         // The entire paragraph is unidirectional.
   *         bidiRegions->emplace_back(0, utf8Units, this->bidi_getLevelAt(bidi.get(), 0));
   *         return true;
   *     }
   *
   *     while (pos16 < end16) {
   *         auto level = this->bidi_getLevelAt(bidi.get(), pos16);
   *         if (pos16 == 0) {
   *             currentLevel = level;
   *         } else if (level != currentLevel) {
   *             SkUnicode::Position end = start8 - utf8;
   *             bidiRegions->emplace_back(pos8, end, currentLevel);
   *             currentLevel = level;
   *             pos8 = end;
   *         }
   *         SkUnichar u = utf8_next(&start8, end8);
   *         pos16 += SkUTF::ToUTF16(u);
   *     }
   *     SkUnicode::Position end = start8 - utf8;
   *     if (end != pos8) {
   *         bidiRegions->emplace_back(pos8, end, currentLevel);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun extractBidi(
    utf8: CharArray,
    utf8Units: Int,
    dir: SkUnicode.TextDirection,
    bidiRegions: List<SkUnicode.BidiRegion>?,
  ): Boolean {
    TODO("Implement extractBidi")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const char* errorName(UErrorCode status) const = 0
   * ```
   */
  public abstract fun errorName(status: UErrorCode): Char

  /**
   * C++ original:
   * ```cpp
   * virtual BidiCloseCallback bidi_close_callback() const = 0
   * ```
   */
  public abstract fun bidiCloseCallback(): SkBidiFactoryBidiCloseCallback

  /**
   * C++ original:
   * ```cpp
   * virtual UBiDiDirection bidi_getDirection(const UBiDi* bidi) const = 0
   * ```
   */
  public abstract fun bidiGetDirection(bidi: UBiDi?): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkBidiIterator::Position bidi_getLength(const UBiDi* bidi) const = 0
   * ```
   */
  public abstract fun bidiGetLength(bidi: UBiDi?): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkBidiIterator::Level bidi_getLevelAt(const UBiDi* bidi, int pos) const = 0
   * ```
   */
  public abstract fun bidiGetLevelAt(bidi: UBiDi?, pos: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual UBiDi* bidi_openSized(int32_t maxLength,
   *                                   int32_t maxRunCount,
   *                                   UErrorCode* pErrorCode) const = 0
   * ```
   */
  public abstract fun bidiOpenSized(
    maxLength: Int,
    maxRunCount: Int,
    pErrorCode: UErrorCode?,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void bidi_setPara(UBiDi* bidi,
   *                               const UChar* text,
   *                               int32_t length,
   *                               UBiDiLevel paraLevel,
   *                               UBiDiLevel* embeddingLevels,
   *                               UErrorCode* status) const = 0
   * ```
   */
  public abstract fun bidiSetPara(
    bidi: UBiDi?,
    text: UChar?,
    length: Int,
    paraLevel: UBiDiLevel,
    embeddingLevels: UBiDiLevel?,
    status: UErrorCode?,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void bidi_reorderVisual(const SkUnicode::BidiLevel runLevels[],
   *                                     int levelsCount,
   *                                     int32_t logicalFromVisual[]) const = 0
   * ```
   */
  public abstract fun bidiReorderVisual(
    runLevels: Array<SkUnicodeBidiLevel>,
    levelsCount: Int,
    logicalFromVisual: IntArray,
  )
}
