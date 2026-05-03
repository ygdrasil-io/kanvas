package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkNoOpPurge {
 *     template <typename K, typename V>
 *     void operator()(void* /* context */, const K& /* k */, const V* /* v */) const {}
 * }
 * ```
 */
public open class SkNoOpPurge {
  /**
   * C++ original:
   * ```cpp
   *     template <typename K, typename V>
   *     void operator()(void* /* context */, const K& /* k */, const V* /* v */) const {}
   * ```
   */
  public operator fun <K, V> invoke(
    param0: Int,
    param1: Int,
    param2: Int,
  ) {
    TODO("Implement invoke")
  }
}
