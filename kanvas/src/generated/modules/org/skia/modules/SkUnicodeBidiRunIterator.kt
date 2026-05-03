package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import undefined.SkUnicodeBidi

/**
 * C++ original:
 * ```cpp
 * class SkUnicodeBidiRunIterator final : public SkShaper::BiDiRunIterator {
 * public:
 *     SkUnicodeBidiRunIterator(const char* utf8, const char* end, SkUnicodeBidi bidi)
 *         : fBidi(std::move(bidi))
 *         , fEndOfCurrentRun(utf8)
 *         , fBegin(utf8)
 *         , fEnd(end)
 *         , fUTF16LogicalPosition(0)
 *         , fLevel(SkBidiIterator::kLTR)
 *     {}
 *
 *     void consume() override {
 *         SkASSERT(fUTF16LogicalPosition < fBidi->getLength());
 *         int32_t endPosition = fBidi->getLength();
 *         fLevel = fBidi->getLevelAt(fUTF16LogicalPosition);
 *         SkUnichar u = utf8_next(&fEndOfCurrentRun, fEnd);
 *         fUTF16LogicalPosition += SkUTF::ToUTF16(u);
 *         SkBidiIterator::Level level;
 *         while (fUTF16LogicalPosition < endPosition) {
 *             level = fBidi->getLevelAt(fUTF16LogicalPosition);
 *             if (level != fLevel) {
 *                 break;
 *             }
 *             u = utf8_next(&fEndOfCurrentRun, fEnd);
 *
 *             fUTF16LogicalPosition += SkUTF::ToUTF16(u);
 *         }
 *     }
 *     size_t endOfCurrentRun() const override {
 *         return fEndOfCurrentRun - fBegin;
 *     }
 *     bool atEnd() const override {
 *         return fUTF16LogicalPosition == fBidi->getLength();
 *     }
 *     SkBidiIterator::Level currentLevel() const override {
 *         return fLevel;
 *     }
 * private:
 *     SkUnicodeBidi fBidi;
 *     char const * fEndOfCurrentRun;
 *     char const * const fBegin;
 *     char const * const fEnd;
 *     int32_t fUTF16LogicalPosition;
 *     SkBidiIterator::Level fLevel;
 * }
 * ```
 */
public class SkUnicodeBidiRunIterator public constructor(
  utf8: String?,
  end: String?,
  bidi: SkUnicodeBidi,
) : SkShaper.BiDiRunIterator() {
  /**
   * C++ original:
   * ```cpp
   * SkUnicodeBidi fBidi
   * ```
   */
  private var fBidi: Int = TODO("Initialize fBidi")

  /**
   * C++ original:
   * ```cpp
   * char const * fEndOfCurrentRun
   * ```
   */
  private val fEndOfCurrentRun: String? = TODO("Initialize fEndOfCurrentRun")

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
   * int32_t fUTF16LogicalPosition
   * ```
   */
  private var fUTF16LogicalPosition: Int = TODO("Initialize fUTF16LogicalPosition")

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator::Level fLevel
   * ```
   */
  private var fLevel: SkBidiIteratorLevel = TODO("Initialize fLevel")

  /**
   * C++ original:
   * ```cpp
   * void consume() override {
   *         SkASSERT(fUTF16LogicalPosition < fBidi->getLength());
   *         int32_t endPosition = fBidi->getLength();
   *         fLevel = fBidi->getLevelAt(fUTF16LogicalPosition);
   *         SkUnichar u = utf8_next(&fEndOfCurrentRun, fEnd);
   *         fUTF16LogicalPosition += SkUTF::ToUTF16(u);
   *         SkBidiIterator::Level level;
   *         while (fUTF16LogicalPosition < endPosition) {
   *             level = fBidi->getLevelAt(fUTF16LogicalPosition);
   *             if (level != fLevel) {
   *                 break;
   *             }
   *             u = utf8_next(&fEndOfCurrentRun, fEnd);
   *
   *             fUTF16LogicalPosition += SkUTF::ToUTF16(u);
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
   *         return fEndOfCurrentRun - fBegin;
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
   *         return fUTF16LogicalPosition == fBidi->getLength();
   *     }
   * ```
   */
  public override fun atEnd(): Boolean {
    TODO("Implement atEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBidiIterator::Level currentLevel() const override {
   *         return fLevel;
   *     }
   * ```
   */
  public override fun currentLevel(): SkBidiIteratorLevel {
    TODO("Implement currentLevel")
  }
}
