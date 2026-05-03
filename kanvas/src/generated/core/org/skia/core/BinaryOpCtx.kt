package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct BinaryOpCtx {
 *     SkRPOffset dst;
 *     SkRPOffset src;
 * }
 * ```
 */
public data class BinaryOpCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRPOffset dst
   * ```
   */
  public var dst: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRPOffset src
   * ```
   */
  public var src: Int,
)
