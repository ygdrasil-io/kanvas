package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename U>
 * struct ReadAs {
 *     ReadAs() : ptr(nullptr), type(SkRecords::Type(~0)) {}
 *
 *     const U* ptr;
 *     SkRecords::Type type;
 *
 *     void operator()(const U& r) { ptr = &r; type = U::kType; }
 *
 *     template <typename T>
 *     void operator()(const T&) { type = U::kType; }
 * }
 * ```
 */
public data class ReadAs<U> public constructor(
  /**
   * C++ original:
   * ```cpp
   * const U* ptr
   * ```
   */
  private val ptr: U,
  /**
   * C++ original:
   * ```cpp
   * SkRecords::Type type
   * ```
   */
  private var type: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void operator()(const U& r) { ptr = &r; type = U::kType; }
   * ```
   */
  private operator fun invoke(r: U) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     void operator()(const T&) { type = U::kType; }
   * ```
   */
  private operator fun <T> invoke(param0: T) {
    TODO("Implement invoke")
  }
}
