package org.skia.core

import kotlin.Int
import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * struct SkPathRectInfo {
 *     SkRect          fRect;
 *     SkPathDirection fDirection;
 *     uint8_t         fStartIndex;
 * }
 * ```
 */
public data class SkPathRectInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect          fRect
   * ```
   */
  public var fRect: Int,
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
