package org.skia.core

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * struct CoordClampCtx {
 *     float min_x, min_y;
 *     float max_x, max_y;
 * }
 * ```
 */
public data class CoordClampCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float min_x
   * ```
   */
  public var minX: Float,
  /**
   * C++ original:
   * ```cpp
   * float min_x, min_y
   * ```
   */
  public var minY: Float,
  /**
   * C++ original:
   * ```cpp
   * float max_x
   * ```
   */
  public var maxX: Float,
  /**
   * C++ original:
   * ```cpp
   * float max_x, max_y
   * ```
   */
  public var maxY: Float,
)
