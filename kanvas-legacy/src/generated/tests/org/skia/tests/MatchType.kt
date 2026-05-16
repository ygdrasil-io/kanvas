package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct MatchType {
 *     template <typename T> int operator()(const T&) { return 0; }
 *     int operator()(const DrawT&) { return 1; }
 * }
 * ```
 */
public open class MatchType<DrawT> {
  /**
   * C++ original:
   * ```cpp
   * int operator()(const T&) { return 0; }
   * ```
   */
  private operator fun invoke(param0: T): Int {
    TODO("Implement invoke")
  }
}
