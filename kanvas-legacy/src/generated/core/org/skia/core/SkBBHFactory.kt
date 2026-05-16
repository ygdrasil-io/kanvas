package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkBBHFactory {
 * public:
 *     /**
 *      *  Allocate a new SkBBoxHierarchy. Return NULL on failure.
 *      */
 *     virtual sk_sp<SkBBoxHierarchy> operator()() const = 0;
 *     virtual ~SkBBHFactory() {}
 *
 * protected:
 *     SkBBHFactory() = default;
 *     SkBBHFactory(const SkBBHFactory&) = delete;
 *     SkBBHFactory& operator=(const SkBBHFactory&) = delete;
 * }
 * ```
 */
public abstract class SkBBHFactory public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkBBHFactory() = default
   * ```
   */
  public constructor(param0: SkBBHFactory) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkBBoxHierarchy> operator()() const = 0
   * ```
   */
  public abstract operator fun invoke(): Int

  /**
   * C++ original:
   * ```cpp
   * SkBBHFactory& operator=(const SkBBHFactory&) = delete
   * ```
   */
  protected fun assign(param0: SkBBHFactory) {
    TODO("Implement assign")
  }
}
