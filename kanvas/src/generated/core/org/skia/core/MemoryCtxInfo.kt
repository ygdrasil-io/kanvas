package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct MemoryCtxInfo {
 *     MemoryCtx* context;
 *
 *     int bytesPerPixel;
 *     bool load;
 *     bool store;
 * }
 * ```
 */
public data class MemoryCtxInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * MemoryCtx* context
   * ```
   */
  public var context: MemoryCtx?,
  /**
   * C++ original:
   * ```cpp
   * int bytesPerPixel
   * ```
   */
  public var bytesPerPixel: Int,
  /**
   * C++ original:
   * ```cpp
   * bool load
   * ```
   */
  public var load: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool store
   * ```
   */
  public var store: Boolean,
)
