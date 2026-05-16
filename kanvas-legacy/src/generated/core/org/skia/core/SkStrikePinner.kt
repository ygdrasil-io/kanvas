package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SkStrikePinner {
 * public:
 *     virtual ~SkStrikePinner() = default;
 *     virtual bool canDelete() = 0;
 *     virtual void assertValid() {}
 * }
 * ```
 */
public abstract class SkStrikePinner {
  /**
   * C++ original:
   * ```cpp
   * virtual bool canDelete() = 0
   * ```
   */
  public abstract fun canDelete(): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual void assertValid() {}
   * ```
   */
  public open fun assertValid() {
    TODO("Implement assertValid")
  }
}
