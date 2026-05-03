package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ResourceBinding {
 *     BindingIndex fIndex;
 *     DispatchResource fResource;
 * }
 * ```
 */
public data class ResourceBinding public constructor(
  /**
   * C++ original:
   * ```cpp
   * BindingIndex fIndex
   * ```
   */
  public var fIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * DispatchResource fResource
   * ```
   */
  public var fResource: Int,
)
