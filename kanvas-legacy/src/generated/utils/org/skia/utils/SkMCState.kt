package org.skia.utils

import kotlin.FloatArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkMCState {
 *     float matrix[9];
 *     // NOTE: this only works for non-antialiased clips
 *     int32_t clipRectCount;
 *     ClipRect* clipRects;
 * }
 * ```
 */
public data class SkMCState public constructor(
  /**
   * C++ original:
   * ```cpp
   * float matrix[9]
   * ```
   */
  public var matrix: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * int32_t clipRectCount
   * ```
   */
  public var clipRectCount: Int,
  /**
   * C++ original:
   * ```cpp
   * ClipRect* clipRects
   * ```
   */
  public var clipRects: ClipRect?,
)
