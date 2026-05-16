package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TextBox {
 *     SkRect rect;
 *     TextDirection direction;
 *
 *     TextBox(SkRect r, TextDirection d) : rect(r), direction(d) {}
 * }
 * ```
 */
public data class TextBox public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect rect
   * ```
   */
  public var rect: Int,
  /**
   * C++ original:
   * ```cpp
   * TextDirection direction
   * ```
   */
  public var direction: TextDirection,
)
