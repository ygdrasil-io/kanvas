package org.skia.core

import kotlin.Int
import kotlin.UByte
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * union PositioningAndExtended {
 *     int32_t intValue;
 *     struct {
 *         uint8_t  positioning;
 *         uint8_t  extended;
 *         uint16_t padding;
 *     };
 * }
 * ```
 */
public data class PositioningAndExtended public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t intValue
   * ```
   */
  private var intValue: Int,
  public var positioning: UByte,
  public var extended: UByte,
  public var padding: UShort,
)
