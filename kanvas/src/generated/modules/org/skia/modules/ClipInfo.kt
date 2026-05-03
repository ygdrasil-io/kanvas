package org.skia.modules

import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * struct ClipInfo {
 *     ScalarValue fClipBlack = 1, // 1: clip, 2/3: don't clip
 *                 fClipWhite = 1; // ^
 * }
 * ```
 */
public data class ClipInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fClipBlack = 1
   * ```
   */
  public var fClipBlack: ScalarValue,
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fClipBlack = 1, // 1: clip, 2/3: don't clip
   *                 fClipWhite = 1
   * ```
   */
  public var fClipWhite: ScalarValue,
)
