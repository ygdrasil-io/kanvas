package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ConstantCtx {
 *     int32_t value;
 *     SkRPOffset dst;
 * }
 * ```
 */
public data class ConstantCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t value
   * ```
   */
  public var `value`: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRPOffset dst
   * ```
   */
  public var dst: Int,
)
