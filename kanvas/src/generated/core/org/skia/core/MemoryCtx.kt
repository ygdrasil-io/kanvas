package org.skia.core

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct MemoryCtx {
 *     void* pixels;
 *     int   stride;
 * }
 * ```
 */
public data class MemoryCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * void* pixels
   * ```
   */
  public var pixels: Unit?,
  /**
   * C++ original:
   * ```cpp
   * int   stride
   * ```
   */
  public var stride: Int,
)
