package org.skia.modules

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class DirText {
 *     DirText(bool dir, size_t s, size_t e) : start(s), end(e) { }
 *     bool isLeftToRight() const { return start <= end; }
 *     size_t start;
 *     size_t end;
 * }
 * ```
 */
public data class DirText public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t start
   * ```
   */
  private var start: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t end
   * ```
   */
  private var end: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isLeftToRight() const { return start <= end; }
   * ```
   */
  private fun isLeftToRight(): Boolean {
    TODO("Implement isLeftToRight")
  }
}
