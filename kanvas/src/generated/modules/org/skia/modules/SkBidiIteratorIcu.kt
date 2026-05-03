package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.sksl.Position
import undefined.Level
import undefined.SkUnicodeBidi

/**
 * C++ original:
 * ```cpp
 * class SkBidiIterator_icu : public SkBidiIterator {
 * public:
 *     SkBidiIterator_icu(SkUnicodeBidi bidi, sk_sp<SkBidiFactory> fact)
 *             : fBidi(std::move(bidi)), fBidiFact(std::move(fact)) {}
 *
 *     Position getLength() override { return fBidiFact->bidi_getLength(fBidi.get()); }
 *
 *     Level getLevelAt(Position pos) override { return fBidiFact->bidi_getLevelAt(fBidi.get(), pos); }
 *
 * private:
 *     SkUnicodeBidi fBidi;
 *     sk_sp<SkBidiFactory> fBidiFact;
 * }
 * ```
 */
public open class SkBidiIteratorIcu public constructor(
  bidi: SkUnicodeBidi,
  fact: SkSp<SkBidiFactory>,
) : SkBidiIterator() {
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
   * sk_sp<SkBidiFactory> fBidiFact
   * ```
   */
  private var fBidiFact: SkSp<SkBidiFactory> = TODO("Initialize fBidiFact")

  /**
   * C++ original:
   * ```cpp
   * Position getLength() override { return fBidiFact->bidi_getLength(fBidi.get()); }
   * ```
   */
  public override fun getLength(): Position {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * Level getLevelAt(Position pos) override { return fBidiFact->bidi_getLevelAt(fBidi.get(), pos); }
   * ```
   */
  public override fun getLevelAt(pos: Position): Level {
    TODO("Implement getLevelAt")
  }
}
