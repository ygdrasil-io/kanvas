package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct IndirectDispatchArgs {
 *     uint32_t global_size_x;
 *     uint32_t global_size_y;
 *     uint32_t global_size_z;
 * }
 * ```
 */
public data class IndirectDispatchArgs public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t global_size_x
   * ```
   */
  public var globalSizeX: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t global_size_y
   * ```
   */
  public var globalSizeY: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t global_size_z
   * ```
   */
  public var globalSizeZ: Int,
)
