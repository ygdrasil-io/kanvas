package org.skia.core

import kotlin.Int
import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * struct SkPathOvalInfo {
 *     SkRect          fBounds;
 *     SkPathDirection fDirection;
 *     uint8_t         fStartIndex;
 * }
 * ```
 */
public data class SkPathOvalInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect          fBounds
   * ```
   */
  public var fBounds: Int,
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
