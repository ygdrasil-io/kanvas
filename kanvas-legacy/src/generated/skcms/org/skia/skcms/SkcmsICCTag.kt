package org.skia.skcms

import kotlin.Any
import kotlin.UByte
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct skcms_ICCTag {
 *     uint32_t       signature;
 *     uint32_t       type;
 *     uint32_t       size;
 *     const uint8_t* buf;
 * }
 * ```
 */
public data class SkcmsICCTag public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t       signature
   * ```
   */
  public var signature: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t       type
   * ```
   */
  public var type: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t       size
   * ```
   */
  public var size: UInt,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t* buf
   * ```
   */
  public val buf: UByte?,
)

public typealias SkcmsICCTag = Any
