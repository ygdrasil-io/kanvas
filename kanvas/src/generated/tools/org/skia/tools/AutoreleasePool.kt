package org.skia.tools

/**
 * C++ original:
 * ```cpp
 * class AutoreleasePool {
 * public:
 *     AutoreleasePool() {}
 *     ~AutoreleasePool() = default;
 *
 *     void drain() {}
 * }
 * ```
 */
public open class AutoreleasePool public constructor() {
  /**
   * C++ original:
   * ```cpp
   * void AutoreleasePool::drain() {
   *     [(NSAutoreleasePool*)fPool drain];
   *     fPool = (void*)[[NSAutoreleasePool alloc] init];
   * }
   * ```
   */
  public fun drain() {
    TODO("Implement drain")
  }
}
