package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename K>
 * struct SkForceDirectHash {
 *     uint32_t operator()(const K& k) const {
 *         return SkChecksum::Hash32(&k, sizeof(K));
 *     }
 * }
 * ```
 */
public open class SkForceDirectHash<K> {
  /**
   * C++ original:
   * ```cpp
   * uint32_t operator()(const K& k) const {
   *         return SkChecksum::Hash32(&k, sizeof(K));
   *     }
   * ```
   */
  private operator fun invoke(k: K): Int {
    TODO("Implement invoke")
  }
}
