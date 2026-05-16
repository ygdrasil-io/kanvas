package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SweepLineTestingPeer {
 *     SweepLineTestingPeer(SweepLine* sl) : fSL{sl} {}
 *     void verifySweepLine(int32_t y) const {
 *         fSL->verify(y);
 *     }
 *     void insertSegment(int i, const Segment& s) {
 *         auto& v = fSL->fSweepLine;
 *         v.insert(v.begin() + i, s);
 *     }
 *     size_t size() const {
 *         return fSL->fSweepLine.size();
 *     }
 *
 *     const std::vector<Segment>& sweepLine() const {
 *         return fSL->fSweepLine;
 *     }
 *
 *     SweepLine* const fSL;
 * }
 * ```
 */
public open class SweepLineTestingPeer public constructor(
  /**
   * C++ original:
   * ```cpp
   * SweepLine* const fSL
   * ```
   */
  public val fSL: SweepLine?,
) {
  /**
   * C++ original:
   * ```cpp
   * SweepLineTestingPeer(SweepLine* sl) : fSL{sl} {}
   * ```
   */
  public constructor(sl: SweepLine?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void verifySweepLine(int32_t y) const {
   *         fSL->verify(y);
   *     }
   * ```
   */
  public fun verifySweepLine(y: Int) {
    TODO("Implement verifySweepLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void insertSegment(int i, const Segment& s) {
   *         auto& v = fSL->fSweepLine;
   *         v.insert(v.begin() + i, s);
   *     }
   * ```
   */
  public fun insertSegment(i: Int, s: Segment) {
    TODO("Implement insertSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const {
   *         return fSL->fSweepLine.size();
   *     }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<Segment>& sweepLine() const {
   *         return fSL->fSweepLine;
   *     }
   * ```
   */
  public fun sweepLine(): Int {
    TODO("Implement sweepLine")
  }
}

public typealias TP = SweepLineTestingPeer
