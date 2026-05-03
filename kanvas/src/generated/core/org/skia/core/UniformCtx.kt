package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct UniformCtx {
 *     int32_t* dst;
 *     const int32_t* src;
 * }
 * ```
 */
public data class UniformCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t* dst
   * ```
   */
  public var dst: Int?,
  /**
   * C++ original:
   * ```cpp
   * const int32_t* src
   * ```
   */
  public val src: Int?,
)
