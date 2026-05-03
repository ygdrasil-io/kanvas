package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * template <typename Matcher>
 * struct Not {
 *     template <typename T>
 *     bool operator()(T* ptr) { return !Matcher()(ptr); }
 * }
 * ```
 */
public open class Not<Matcher> {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool operator()(T* ptr) { return !Matcher()(ptr); }
   * ```
   */
  private operator fun <T> invoke(ptr: T?): Boolean {
    TODO("Implement invoke")
  }
}
