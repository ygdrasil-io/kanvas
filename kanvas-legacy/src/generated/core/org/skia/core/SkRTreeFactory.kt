package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRTreeFactory : public SkBBHFactory {
 * public:
 *     sk_sp<SkBBoxHierarchy> operator()() const override;
 * }
 * ```
 */
public open class SkRTreeFactory : SkBBHFactory() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBBoxHierarchy> SkRTreeFactory::operator()() const {
   *     return sk_make_sp<SkRTree>();
   * }
   * ```
   */
  public override operator fun invoke(): Int {
    TODO("Implement invoke")
  }
}
