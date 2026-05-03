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
 * class SkBidiICUFactory : public SkBidiFactory {
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
public open class SkBidiICUFactory : SkBidiFactory() {
  /**
   * C++ original:
   * ```cpp
   * const char* SkBidiICUFactory::errorName(UErrorCode status) const {
   *     return SkGetICULib()->f_u_errorName(status);
   * }
   * ```
   */
  public override fun errorName(status: UErrorCode): Char {
    TODO("Implement errorName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiFactory::BidiCloseCallback SkBidiICUFactory::bidi_close_callback() const {
   *     return SkGetICULib()->f_ubidi_close;
   * }
   * ```
   */
  public override fun bidiCloseCallback(): Int {
    TODO("Implement bidiCloseCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * UBiDiDirection SkBidiICUFactory::bidi_getDirection(const UBiDi* bidi) const {
   *     return SkGetICULib()->f_ubidi_getDirection(bidi);
   * }
   * ```
   */
  public override fun bidiGetDirection(bidi: UBiDi?): Int {
    TODO("Implement bidiGetDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator::Position SkBidiICUFactory::bidi_getLength(const UBiDi* bidi) const {
   *     return SkGetICULib()->f_ubidi_getLength(bidi);
   * }
   * ```
   */
  public override fun bidiGetLength(bidi: UBiDi?): Int {
    TODO("Implement bidiGetLength")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator::Level SkBidiICUFactory::bidi_getLevelAt(const UBiDi* bidi, int pos) const {
   *     return SkGetICULib()->f_ubidi_getLevelAt(bidi, pos);
   * }
   * ```
   */
  public override fun bidiGetLevelAt(bidi: UBiDi?, pos: Int): Int {
    TODO("Implement bidiGetLevelAt")
  }

  /**
   * C++ original:
   * ```cpp
   * UBiDi* SkBidiICUFactory::bidi_openSized(int32_t maxLength,
   *                                         int32_t maxRunCount,
   *                                         UErrorCode* pErrorCode) const {
   *     return SkGetICULib()->f_ubidi_openSized(maxLength, maxRunCount, pErrorCode);
   * }
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
   * void SkBidiICUFactory::bidi_setPara(UBiDi* bidi,
   *                                     const UChar* text,
   *                                     int32_t length,
   *                                     UBiDiLevel paraLevel,
   *                                     UBiDiLevel* embeddingLevels,
   *                                     UErrorCode* status) const {
   *     return SkGetICULib()->f_ubidi_setPara(bidi, text, length, paraLevel, embeddingLevels, status);
   * }
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
   * void SkBidiICUFactory::bidi_reorderVisual(const SkUnicode::BidiLevel runLevels[],
   *                                           int levelsCount,
   *                                           int32_t logicalFromVisual[]) const {
   *     if (levelsCount == 0) {
   *         // To avoid an assert in unicode
   *         return;
   *     }
   *     SkASSERT(runLevels != nullptr);
   *     SkGetICULib()->f_ubidi_reorderVisual(runLevels, levelsCount, logicalFromVisual);
   * }
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
