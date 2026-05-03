package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct HashCopyCounter {
 *     uint32_t operator()(const CopyCounter&) const {
 *         return 0; // let them collide, what do we care?
 *     }
 * }
 * ```
 */
public open class HashCopyCounter {
  /**
   * C++ original:
   * ```cpp
   * uint32_t operator()(const CopyCounter&) const {
   *         return 0; // let them collide, what do we care?
   *     }
   * ```
   */
  public operator fun invoke(param0: CopyCounter): Int {
    TODO("Implement invoke")
  }
}
