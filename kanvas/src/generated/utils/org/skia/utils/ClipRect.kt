package org.skia.utils

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ClipRect {
 *     int32_t left, top, right, bottom;
 * }
 * ```
 */
public data class ClipRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t left
   * ```
   */
  public var left: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t left, top
   * ```
   */
  public var top: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t left, top, right
   * ```
   */
  public var right: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t left, top, right, bottom
   * ```
   */
  public var bottom: Int,
)
