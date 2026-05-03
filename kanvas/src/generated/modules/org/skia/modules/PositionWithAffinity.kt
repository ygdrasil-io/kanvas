package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct PositionWithAffinity {
 *     int32_t position;
 *     Affinity affinity;
 *
 *     PositionWithAffinity() : position(0), affinity(kDownstream) {}
 *     PositionWithAffinity(int32_t p, Affinity a) : position(p), affinity(a) {}
 * }
 * ```
 */
public data class PositionWithAffinity public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t position
   * ```
   */
  public var position: Int,
  /**
   * C++ original:
   * ```cpp
   * Affinity affinity
   * ```
   */
  public var affinity: Affinity,
)
