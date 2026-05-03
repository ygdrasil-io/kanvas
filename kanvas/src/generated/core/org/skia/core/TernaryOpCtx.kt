package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TernaryOpCtx {
 *     SkRPOffset dst;
 *     SkRPOffset delta;
 * }
 * ```
 */
public data class TernaryOpCtx public constructor(
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
   * SkRPOffset delta
   * ```
   */
  public var delta: Int,
)
