package org.skia.effects

import kotlin.Int
import kotlin.UInt
import org.skia.math.SkFixed
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class LCGRandom {
 * public:
 *     LCGRandom(uint32_t seed) : fSeed(seed) {}
 *
 *     /** Return the next pseudo random number expressed as a SkScalar
 *         in the range [-SK_Scalar1..SK_Scalar1).
 *     */
 *     SkScalar nextSScalar1() { return SkFixedToScalar(this->nextSFixed1()); }
 *
 * private:
 *     /** Return the next pseudo random number as an unsigned 32bit value.
 *     */
 *     uint32_t nextU() { uint32_t r = fSeed * kMul + kAdd; fSeed = r; return r; }
 *
 *     /** Return the next pseudo random number as a signed 32bit value.
 *      */
 *     int32_t nextS() { return (int32_t)this->nextU(); }
 *
 *     /** Return the next pseudo random number expressed as a signed SkFixed
 *      in the range [-SK_Fixed1..SK_Fixed1).
 *      */
 *     SkFixed nextSFixed1() { return this->nextS() >> 15; }
 *
 *     //  See "Numerical Recipes in C", 1992 page 284 for these constants
 *     enum {
 *         kMul = 1664525,
 *         kAdd = 1013904223
 *     };
 *     uint32_t fSeed;
 * }
 * ```
 */
public data class LCGRandom public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fSeed
   * ```
   */
  private var fSeed: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar nextSScalar1() { return SkFixedToScalar(this->nextSFixed1()); }
   * ```
   */
  public fun nextSScalar1(): SkScalar {
    TODO("Implement nextSScalar1")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t nextU() { uint32_t r = fSeed * kMul + kAdd; fSeed = r; return r; }
   * ```
   */
  private fun nextU(): UInt {
    TODO("Implement nextU")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t nextS() { return (int32_t)this->nextU(); }
   * ```
   */
  private fun nextS(): Int {
    TODO("Implement nextS")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed nextSFixed1() { return this->nextS() >> 15; }
   * ```
   */
  private fun nextSFixed1(): SkFixed {
    TODO("Implement nextSFixed1")
  }

  public companion object {
    public val kMul: Int = TODO("Initialize kMul")

    public val kAdd: Int = TODO("Initialize kAdd")
  }
}
