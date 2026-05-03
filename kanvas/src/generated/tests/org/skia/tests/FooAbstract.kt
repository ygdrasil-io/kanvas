package org.skia.tests

import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * struct FooAbstract : public SkRefCnt {
 *     virtual void f() = 0;
 * }
 * ```
 */
public abstract class FooAbstract : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void f() = 0
   * ```
   */
  public abstract fun f()
}
