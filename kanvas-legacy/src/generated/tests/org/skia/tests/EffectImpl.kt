package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct EffectImpl : public Effect {
 *     ~EffectImpl() override {}
 *
 *     static sk_sp<EffectImpl> Create() {
 *         return sk_sp<EffectImpl>(new EffectImpl);
 *     }
 *     int fValue;
 * }
 * ```
 */
public open class EffectImpl public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fValue
   * ```
   */
  public var fValue: Int,
) : Effect(TODO()) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<EffectImpl> Create() {
     *         return sk_sp<EffectImpl>(new EffectImpl);
     *     }
     * ```
     */
    public fun create(): SkSp<EffectImpl> {
      TODO("Implement create")
    }
  }
}
