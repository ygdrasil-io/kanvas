package org.skia.tests

import kotlin.UInt
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct BC1Block {
 *     uint16_t fColor0;
 *     uint16_t fColor1;
 *     uint32_t fIndices;
 * }
 * ```
 */
public data class BC1Block public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint16_t fColor0
   * ```
   */
  public var fColor0: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fColor1
   * ```
   */
  public var fColor1: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fIndices
   * ```
   */
  public var fIndices: UInt,
)
