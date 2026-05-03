package org.skia.core

import org.skia.foundation.SkCachedData

/**
 * C++ original:
 * ```cpp
 * struct YUVValue {
 *     SkYUVAPixmaps fPixmaps;
 *     SkCachedData* fData;
 * }
 * ```
 */
public data class YUVValue public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps fPixmaps
   * ```
   */
  public var fPixmaps: SkYUVAPixmaps,
  /**
   * C++ original:
   * ```cpp
   * SkCachedData* fData
   * ```
   */
  public var fData: SkCachedData?,
)
