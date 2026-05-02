package org.skia.math

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class SkRandom {
 * public:
 *     SkRandom() { init(0); }
 *     explicit SkRandom(uint32_t seed) { init(seed); }
 *     SkRandom(const SkRandom& rand) : fK(rand.fK), fJ(rand.fJ) {}
 *
 *     SkRandom& operator=(const SkRandom& rand) {
 *         fK = rand.fK;
 *         fJ = rand.fJ;
 *
 *         return *this;
 *     }
 *
 *     /** Return the next pseudo random number as an unsigned 32bit value.
 *      */
 *     uint32_t nextU() {
 *         fK = kKMul*(fK & 0xffff) + (fK >> 16);
 *         fJ = kJMul*(fJ & 0xffff) + (fJ >> 16);
 *         return (((fK << 16) | (fK >> 16)) + fJ);
 *     }
 *
 *     /** Return the next pseudo random number as a signed 32bit value.
 *      */
 *     int32_t nextS() { return (int32_t)this->nextU(); }
 *
 *     /**
 *      *  Returns value [0...1) as an IEEE float
 *      */
 *     float nextF() {
 *         uint32_t floatint = 0x3f800000 | (this->nextU() >> 9);
 *         float f = SkBits2Float(floatint) - 1.0f;
 *         return f;
 *     }
 *
 *     /**
 *      *  Returns value [min...max) as a float
 *      */
 *     float nextRangeF(float min, float max) {
 *         return min + this->nextF() * (max - min);
 *     }
 *
 *     /** Return the next pseudo random number, as an unsigned value of
 *      at most bitCount bits.
 *      @param bitCount The maximum number of bits to be returned
 *      */
 *     uint32_t nextBits(unsigned bitCount) {
 *         SkASSERT(bitCount > 0 && bitCount <= 32);
 *         return this->nextU() >> (32 - bitCount);
 *     }
 *
 *     /** Return the next pseudo random unsigned number, mapped to lie within
 *      [min, max] inclusive.
 *      */
 *     uint32_t nextRangeU(uint32_t min, uint32_t max) {
 *         SkASSERT(min <= max);
 *         uint32_t range = max - min + 1;
 *         if (0 == range) {
 *             return this->nextU();
 *         } else {
 *             return min + this->nextU() % range;
 *         }
 *     }
 *
 *     /** Return the next pseudo random unsigned number, mapped to lie within
 *      [0, count).
 *      */
 *     uint32_t nextULessThan(uint32_t count) {
 *         SkASSERT(count > 0);
 *         return this->nextRangeU(0, count - 1);
 *     }
 *
 *     /** Return the next pseudo random number expressed as a SkScalar
 *      in the range [0..SK_Scalar1).
 *      */
 *     SkScalar nextUScalar1() { return SkFixedToScalar(this->nextUFixed1()); }
 *
 *     /** Return the next pseudo random number expressed as a SkScalar
 *      in the range [min..max).
 *      */
 *     SkScalar nextRangeScalar(SkScalar min, SkScalar max) {
 *         return this->nextUScalar1() * (max - min) + min;
 *     }
 *
 *     /** Return the next pseudo random number expressed as a SkScalar
 *      in the range [-SK_Scalar1..SK_Scalar1).
 *      */
 *     SkScalar nextSScalar1() { return SkFixedToScalar(this->nextSFixed1()); }
 *
 *     /** Return the next pseudo random number as a bool.
 *      */
 *     bool nextBool() { return this->nextU() >= 0x80000000; }
 *
 *     /** A biased version of nextBool().
 *      */
 *     bool nextBiasedBool(SkScalar fractionTrue) {
 *         SkASSERT(fractionTrue >= 0 && fractionTrue <= 1);
 *         return this->nextUScalar1() <= fractionTrue;
 *     }
 *
 *     /** Reset the random object.
 *      */
 *     void setSeed(uint32_t seed) { init(seed); }
 *
 * private:
 *     // Initialize state variables with LCG.
 *     // We must ensure that both J and K are non-zero, otherwise the
 *     // multiply-with-carry step will forevermore return zero.
 *     void init(uint32_t seed) {
 *         fK = NextLCG(seed);
 *         if (0 == fK) {
 *             fK = NextLCG(fK);
 *         }
 *         fJ = NextLCG(fK);
 *         if (0 == fJ) {
 *             fJ = NextLCG(fJ);
 *         }
 *         SkASSERT(0 != fK && 0 != fJ);
 *     }
 *     static uint32_t NextLCG(uint32_t seed) { return kMul*seed + kAdd; }
 *
 *     /** Return the next pseudo random number expressed as an unsigned SkFixed
 *      in the range [0..SK_Fixed1).
 *      */
 *     SkFixed nextUFixed1() { return this->nextU() >> 16; }
 *
 *     /** Return the next pseudo random number expressed as a signed SkFixed
 *      in the range [-SK_Fixed1..SK_Fixed1).
 *      */
 *     SkFixed nextSFixed1() { return this->nextS() >> 15; }
 *
 *     //  See "Numerical Recipes in C", 1992 page 284 for these constants
 *     //  For the LCG that sets the initial state from a seed
 *     enum {
 *         kMul = 1664525,
 *         kAdd = 1013904223
 *     };
 *     // Constants for the multiply-with-carry steps
 *     enum {
 *         kKMul = 30345,
 *         kJMul = 18000,
 *     };
 *
 *     uint32_t fK;
 *     uint32_t fJ;
 * }
 * ```
 */
public data class SkRandom public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fK
   * ```
   */
  private var fK: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fJ
   * ```
   */
  private var fJ: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkRandom& operator=(const SkRandom& rand) {
   *         fK = rand.fK;
   *         fJ = rand.fJ;
   *
   *         return *this;
   *     }
   * ```
   */
  public fun assign(rand: SkRandom) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t nextU() {
   *         fK = kKMul*(fK & 0xffff) + (fK >> 16);
   *         fJ = kJMul*(fJ & 0xffff) + (fJ >> 16);
   *         return (((fK << 16) | (fK >> 16)) + fJ);
   *     }
   * ```
   */
  public fun nextU(): Int {
    TODO("Implement nextU")
  }

  /**
   * C++ original:
   * ```cpp
   * int32_t nextS() { return (int32_t)this->nextU(); }
   * ```
   */
  public fun nextS(): Int {
    TODO("Implement nextS")
  }

  /**
   * C++ original:
   * ```cpp
   * float nextF() {
   *         uint32_t floatint = 0x3f800000 | (this->nextU() >> 9);
   *         float f = SkBits2Float(floatint) - 1.0f;
   *         return f;
   *     }
   * ```
   */
  public fun nextF(): Float {
    TODO("Implement nextF")
  }

  /**
   * C++ original:
   * ```cpp
   * float nextRangeF(float min, float max) {
   *         return min + this->nextF() * (max - min);
   *     }
   * ```
   */
  public fun nextRangeF(min: Float, max: Float): Float {
    TODO("Implement nextRangeF")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t nextBits(unsigned bitCount) {
   *         SkASSERT(bitCount > 0 && bitCount <= 32);
   *         return this->nextU() >> (32 - bitCount);
   *     }
   * ```
   */
  public fun nextBits(bitCount: UInt): Int {
    TODO("Implement nextBits")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t nextRangeU(uint32_t min, uint32_t max) {
   *         SkASSERT(min <= max);
   *         uint32_t range = max - min + 1;
   *         if (0 == range) {
   *             return this->nextU();
   *         } else {
   *             return min + this->nextU() % range;
   *         }
   *     }
   * ```
   */
  public fun nextRangeU(min: UInt, max: UInt): Int {
    TODO("Implement nextRangeU")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t nextULessThan(uint32_t count) {
   *         SkASSERT(count > 0);
   *         return this->nextRangeU(0, count - 1);
   *     }
   * ```
   */
  public fun nextULessThan(count: UInt): Int {
    TODO("Implement nextULessThan")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar nextUScalar1() { return SkFixedToScalar(this->nextUFixed1()); }
   * ```
   */
  public fun nextUScalar1(): SkScalar {
    TODO("Implement nextUScalar1")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar nextRangeScalar(SkScalar min, SkScalar max) {
   *         return this->nextUScalar1() * (max - min) + min;
   *     }
   * ```
   */
  public fun nextRangeScalar(min: SkScalar, max: SkScalar): SkScalar {
    TODO("Implement nextRangeScalar")
  }

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
   * bool nextBool() { return this->nextU() >= 0x80000000; }
   * ```
   */
  public fun nextBool(): Boolean {
    TODO("Implement nextBool")
  }

  /**
   * C++ original:
   * ```cpp
   * bool nextBiasedBool(SkScalar fractionTrue) {
   *         SkASSERT(fractionTrue >= 0 && fractionTrue <= 1);
   *         return this->nextUScalar1() <= fractionTrue;
   *     }
   * ```
   */
  public fun nextBiasedBool(fractionTrue: SkScalar): Boolean {
    TODO("Implement nextBiasedBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSeed(uint32_t seed) { init(seed); }
   * ```
   */
  public fun setSeed(seed: UInt) {
    TODO("Implement setSeed")
  }

  /**
   * C++ original:
   * ```cpp
   * void init(uint32_t seed) {
   *         fK = NextLCG(seed);
   *         if (0 == fK) {
   *             fK = NextLCG(fK);
   *         }
   *         fJ = NextLCG(fK);
   *         if (0 == fJ) {
   *             fJ = NextLCG(fJ);
   *         }
   *         SkASSERT(0 != fK && 0 != fJ);
   *     }
   * ```
   */
  private fun `init`(seed: UInt) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed nextUFixed1() { return this->nextU() >> 16; }
   * ```
   */
  private fun nextUFixed1(): SkFixed {
    TODO("Implement nextUFixed1")
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

    public val kKMul: Int = TODO("Initialize kKMul")

    public val kJMul: Int = TODO("Initialize kJMul")

    /**
     * C++ original:
     * ```cpp
     * static uint32_t NextLCG(uint32_t seed) { return kMul*seed + kAdd; }
     * ```
     */
    private fun nextLCG(seed: UInt): Int {
      TODO("Implement nextLCG")
    }
  }
}
