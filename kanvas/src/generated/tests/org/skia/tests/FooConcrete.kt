package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct FooConcrete : public FooAbstract {
 *     void f() override {}
 * }
 * ```
 */
public open class FooConcrete : FooAbstract() {
  /**
   * C++ original:
   * ```cpp
   * void f() override {}
   * ```
   */
  public override fun f() {
    TODO("Implement f")
  }
}
