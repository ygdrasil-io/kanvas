package org.skia.tests

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class Paint {
 * public:
 *     sk_sp<Effect> fEffect;
 *
 *     const sk_sp<Effect>& get() const { return fEffect; }
 *
 *     void set(sk_sp<Effect> value) {
 *         fEffect = std::move(value);
 *     }
 * }
 * ```
 */
public data class Paint public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<Effect> fEffect
   * ```
   */
  public var fEffect: SkSp<Effect>,
) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<Effect>& get() const { return fEffect; }
   * ```
   */
  public fun `get`(): SkSp<Effect> {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(sk_sp<Effect> value) {
   *         fEffect = std::move(value);
   *     }
   * ```
   */
  public fun `set`(`value`: SkSp<Effect>) {
    TODO("Implement set")
  }
}
