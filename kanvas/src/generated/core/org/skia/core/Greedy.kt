package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * template <typename Matcher>
 * struct Greedy {
 *     template <typename T>
 *     bool operator()(T* ptr) { return Matcher()(ptr); }
 * }
 * ```
 */
public open class Greedy<Matcher> {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool operator()(T* ptr) { return Matcher()(ptr); }
   * ```
   */
  private operator fun <T> invoke(ptr: T?): Boolean {
    TODO("Implement invoke")
  }
}
