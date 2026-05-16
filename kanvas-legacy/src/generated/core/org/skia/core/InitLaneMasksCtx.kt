package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct InitLaneMasksCtx {
 *     uint8_t* tail;
 * }
 * ```
 */
public data class InitLaneMasksCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t* tail
   * ```
   */
  public var tail: Int?,
)
