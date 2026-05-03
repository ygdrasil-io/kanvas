package org.skia.core

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TileCtx {
 *     float scale;
 *     float invScale; // cache of 1/scale
 *     // When in the reflection portion of mirror tiling we need to snap the opposite direction
 *     // at integer sample points than when in the forward direction. This controls which way we bias
 *     // in the reflection. It should be 1 if GatherCtx::roundDownAtInteger is true
 *     // and otherwise -1.
 *     int   mirrorBiasDir = -1;
 * }
 * ```
 */
public data class TileCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float scale
   * ```
   */
  public var scale: Float,
  /**
   * C++ original:
   * ```cpp
   * float invScale
   * ```
   */
  public var invScale: Float,
  /**
   * C++ original:
   * ```cpp
   * int   mirrorBiasDir = -1
   * ```
   */
  public var mirrorBiasDir: Int,
)
