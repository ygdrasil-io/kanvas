package org.skia.core

import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * struct ColorSpaceHeader {
 *     uint8_t fVersion = kCurrent_Version;
 *
 *     // Other fields were only used by k0_Version. Could be re-purposed in future versions.
 *     uint8_t fReserved0 = 0;
 *     uint8_t fReserved1 = 0;
 *     uint8_t fReserved2 = 0;
 * }
 * ```
 */
public data class ColorSpaceHeader public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t fVersion = kCurrent_Version
   * ```
   */
  public var fVersion: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fReserved0 = 0
   * ```
   */
  public var fReserved0: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fReserved1 = 0
   * ```
   */
  public var fReserved1: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fReserved2 = 0
   * ```
   */
  public var fReserved2: UByte,
)
