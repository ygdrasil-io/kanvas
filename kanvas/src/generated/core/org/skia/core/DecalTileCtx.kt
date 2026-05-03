package org.skia.core

import kotlin.Float
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct DecalTileCtx {
 *     uint32_t mask[kMaxStride];
 *     float    limit_x;
 *     float    limit_y;
 *     // These control which edge of the interval is included (i.e. closed interval at 0 or at limit).
 *     // They should be set to limit_x and limit_y if GatherCtx::roundDownAtInteger
 *     // is true and otherwise zero.
 *     float    inclusiveEdge_x = 0;
 *     float    inclusiveEdge_y = 0;
 * }
 * ```
 */
public data class DecalTileCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t mask[kMaxStride]
   * ```
   */
  public var mask: IntArray,
  /**
   * C++ original:
   * ```cpp
   * float    limit_x
   * ```
   */
  public var limitX: Float,
  /**
   * C++ original:
   * ```cpp
   * float    limit_y
   * ```
   */
  public var limitY: Float,
  /**
   * C++ original:
   * ```cpp
   * float    inclusiveEdge_x = 0
   * ```
   */
  public var inclusiveEdgeX: Float,
  /**
   * C++ original:
   * ```cpp
   * float    inclusiveEdge_y = 0
   * ```
   */
  public var inclusiveEdgeY: Float,
)
