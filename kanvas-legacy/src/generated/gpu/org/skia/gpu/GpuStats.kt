package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct GpuStats {
 *     uint64_t elapsedTime = 0;
 * }
 * ```
 */
public data class GpuStats public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint64_t elapsedTime
   * ```
   */
  public var elapsedTime: Int,
)
