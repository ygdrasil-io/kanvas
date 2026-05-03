package org.skia.modules

import kotlin.Array
import kotlin.Char
import kotlin.Int
import kotlin.IntArray
import org.skia.`external`.UBiDi
import org.skia.`external`.UBiDiLevel
import org.skia.`external`.UChar
import org.skia.`external`.UErrorCode

/**
 * C++ original:
 * ```cpp
 * class SkBidiSubsetFactory : public SkBidiFactory {
 * public:
 *     const char* errorName(UErrorCode status) const override;
 *     SkBidiFactory::BidiCloseCallback bidi_close_callback() const override;
 *     UBiDiDirection bidi_getDirection(const UBiDi* bidi) const override;
 *     SkBidiIterator::Position bidi_getLength(const UBiDi* bidi) const override;
 *     SkBidiIterator::Level bidi_getLevelAt(const UBiDi* bidi, int pos) const override;
 *     UBiDi* bidi_openSized(int32_t maxLength,
 *                           int32_t maxRunCount,
 *                           UErrorCode* pErrorCode) const override;
 *     void bidi_setPara(UBiDi* bidi,
 *                       const UChar* text,
 *                       int32_t length,
 *                       UBiDiLevel paraLevel,
 *                       UBiDiLevel* embeddingLevels,
 *                       UErrorCode* status) const override;
 *     void bidi_reorderVisual(const SkUnicode::BidiLevel runLevels[],
 *                             int levelsCount,
 *                             int32_t logicalFromVisual[]) const override;
 * }
 * ```
 */
public open class SkBidiSubsetFactory : SkBidiFactory() {
  /**
   * C++ original:
   * ```cpp
   * const char* errorName(UErrorCode status) const override
   * ```
   */
  public override fun errorName(status: UErrorCode): Char {
    TODO("Implement errorName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiFactory::BidiCloseCallback bidi_close_callback() const override
   * ```
   */
  public override fun bidiCloseCallback(): Int {
    TODO("Implement bidiCloseCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * UBiDiDirection bidi_getDirection(const UBiDi* bidi) const override
   * ```
   */
  public override fun bidiGetDirection(bidi: UBiDi?): Int {
    TODO("Implement bidiGetDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator::Position bidi_getLength(const UBiDi* bidi) const override
   * ```
   */
  public override fun bidiGetLength(bidi: UBiDi?): Int {
    TODO("Implement bidiGetLength")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator::Level bidi_getLevelAt(const UBiDi* bidi, int pos) const override
   * ```
   */
  public override fun bidiGetLevelAt(bidi: UBiDi?, pos: Int): Int {
    TODO("Implement bidiGetLevelAt")
  }

  /**
   * C++ original:
   * ```cpp
   * UBiDi* bidi_openSized(int32_t maxLength,
   *                           int32_t maxRunCount,
   *                           UErrorCode* pErrorCode) const override
   * ```
   */
  public override fun bidiOpenSized(
    maxLength: Int,
    maxRunCount: Int,
    pErrorCode: UErrorCode?,
  ): Int {
    TODO("Implement bidiOpenSized")
  }

  /**
   * C++ original:
   * ```cpp
   * void bidi_setPara(UBiDi* bidi,
   *                       const UChar* text,
   *                       int32_t length,
   *                       UBiDiLevel paraLevel,
   *                       UBiDiLevel* embeddingLevels,
   *                       UErrorCode* status) const override
   * ```
   */
  public override fun bidiSetPara(
    bidi: UBiDi?,
    text: UChar?,
    length: Int,
    paraLevel: UBiDiLevel,
    embeddingLevels: UBiDiLevel?,
    status: UErrorCode?,
  ) {
    TODO("Implement bidiSetPara")
  }

  /**
   * C++ original:
   * ```cpp
   * void bidi_reorderVisual(const SkUnicode::BidiLevel runLevels[],
   *                             int levelsCount,
   *                             int32_t logicalFromVisual[]) const override
   * ```
   */
  public override fun bidiReorderVisual(
    runLevels: Array<SkUnicodeBidiLevel>,
    levelsCount: Int,
    logicalFromVisual: IntArray,
  ) {
    TODO("Implement bidiReorderVisual")
  }
}
