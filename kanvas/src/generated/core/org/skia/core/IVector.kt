package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct IVector {
 *     int32_t fX;
 *     int32_t fY;
 *
 *     IVector() = default;
 *     IVector(int32_t x, int32_t y) : fX(x), fY(y) {}
 *     explicit IVector(const SkIVector& v) : fX(v.fX), fY(v.fY) {}
 * }
 * ```
 */
public data class IVector public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t fX
   * ```
   */
  public var fX: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fY
   * ```
   */
  public var fY: Int,
)
