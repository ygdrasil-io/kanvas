package org.skia.core

import kotlin.Int
import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * struct SkPathIsAData {
 *     uint8_t         fStartIndex;
 *     SkPathDirection fDirection;
 * }
 * ```
 */
public data class SkPathIsAData public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t         fStartIndex
   * ```
   */
  public var fStartIndex: UByte,
  /**
   * C++ original:
   * ```cpp
   * SkPathDirection fDirection
   * ```
   */
  public var fDirection: Int,
)
