package org.skia.core

import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct ETC1Block {
 *     uint32_t fHigh;
 *     uint32_t fLow;
 * }
 * ```
 */
public data class ETC1Block public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fHigh
   * ```
   */
  public var fHigh: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fLow
   * ```
   */
  public var fLow: UInt,
)
