package org.skia.core

import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkGaussFilter {
 * public:
 *     inline static constexpr int kGaussArrayMax = 6;
 *
 *     explicit SkGaussFilter(double sigma);
 *
 *     size_t size()   const { return fN; }
 *     int radius() const { return fN - 1; }
 *     int width()  const { return 2 * this->radius() + 1; }
 *
 *     // Allow a filter to be used in a C++ ranged-for loop.
 *     const double* begin() const { return &fBasis[0];  }
 *     const double* end()   const { return &fBasis[fN]; }
 *
 * private:
 *     double fBasis[kGaussArrayMax];
 *     int    fN;
 * }
 * ```
 */
public data class SkGaussFilter public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kGaussArrayMax = 6
   * ```
   */
  private var fBasis: DoubleArray,
  /**
   * C++ original:
   * ```cpp
   * double fBasis[kGaussArrayMax]
   * ```
   */
  private var fN: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * size_t size()   const { return fN; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * int radius() const { return fN - 1; }
   * ```
   */
  public fun radius(): Int {
    TODO("Implement radius")
  }

  /**
   * C++ original:
   * ```cpp
   * int width()  const { return 2 * this->radius() + 1; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * const double* begin() const { return &fBasis[0];  }
   * ```
   */
  public fun begin(): Double {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * const double* end()   const { return &fBasis[fN]; }
   * ```
   */
  public fun end(): Double {
    TODO("Implement end")
  }

  public companion object {
    public val kGaussArrayMax: Int = TODO("Initialize kGaussArrayMax")
  }
}
