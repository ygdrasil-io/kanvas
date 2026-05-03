package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * template <typename First, typename... Rest>
 * struct Or {
 *     template <typename T>
 *     bool operator()(T* ptr) { return First()(ptr) || Or<Rest...>()(ptr); }
 * }
 * ```
 */
public open class Or<First, Rest> {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool operator()(T* ptr) { return First()(ptr) || Or<Rest...>()(ptr); }
   * ```
   */
  private operator fun <T> invoke(ptr: T?): Boolean {
    TODO("Implement invoke")
  }
}
