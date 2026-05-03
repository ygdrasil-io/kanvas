package org.skia.tests

import kotlin.Int
import org.skia.math.SkIPoint

/**
 * C++ original:
 * ```cpp
 * struct Coordinates {
 *
 *     const int length;
 *     SkIPoint* const data;
 *
 *     explicit Coordinates(int _length): length(_length)
 *                                      , data(new SkIPoint[length]) { }
 *
 *     ~Coordinates(){
 *         delete [] data;
 *     }
 *
 *     SkIPoint* operator[](int i) const {
 *         // Use with care, no bounds checking.
 *         return data + i;
 *     }
 * }
 * ```
 */
public data class Coordinates public constructor(
  /**
   * C++ original:
   * ```cpp
   * const int length
   * ```
   */
  public val length: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIPoint* const data
   * ```
   */
  public val `data`: SkIPoint?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkIPoint* operator[](int i) const {
   *         // Use with care, no bounds checking.
   *         return data + i;
   *     }
   * ```
   */
  public operator fun `get`(i: Int): SkIPoint {
    TODO("Implement get")
  }
}
