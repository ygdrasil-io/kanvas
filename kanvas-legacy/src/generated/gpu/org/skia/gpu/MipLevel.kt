package org.skia.gpu

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct MipLevel {
 *     const void* fPixels = nullptr;
 *     size_t fRowBytes = 0;
 * }
 * ```
 */
public data class MipLevel public constructor(
  /**
   * C++ original:
   * ```cpp
   * const void* fPixels = nullptr
   * ```
   */
  public val fPixels: Unit?,
  /**
   * C++ original:
   * ```cpp
   * size_t fRowBytes
   * ```
   */
  public var fRowBytes: Int,
)
