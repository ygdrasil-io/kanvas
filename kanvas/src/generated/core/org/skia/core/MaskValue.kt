package org.skia.core

import org.skia.foundation.SkCachedData
import org.skia.foundation.SkMask

/**
 * C++ original:
 * ```cpp
 * struct MaskValue {
 *     SkMask          fMask;
 *     SkCachedData*   fData;
 * }
 * ```
 */
public data class MaskValue public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkMask          fMask
   * ```
   */
  public var fMask: SkMask,
  /**
   * C++ original:
   * ```cpp
   * SkCachedData*   fData
   * ```
   */
  public var fData: SkCachedData?,
)
