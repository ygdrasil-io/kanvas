package org.skia.core

import kotlin.Int
import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * struct SkPathRRectInfo {
 *     SkRRect         fRRect;
 *     SkPathDirection fDirection;
 *     uint8_t         fStartIndex;
 * }
 * ```
 */
public data class SkPathRRectInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRRect         fRRect
   * ```
   */
  public var fRRect: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPathDirection fDirection
   * ```
   */
  public var fDirection: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t         fStartIndex
   * ```
   */
  public var fStartIndex: UByte,
)
