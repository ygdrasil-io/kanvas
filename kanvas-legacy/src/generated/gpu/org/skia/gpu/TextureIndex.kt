package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TextureIndex { uint32_t fValue; }
 * ```
 */
public data class TextureIndex public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fValue
   * ```
   */
  public var fValue: Int,
)
