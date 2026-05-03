package org.skia.gpu

import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * class SwizzleCtorAccessor {
 * public:
 *     static Swizzle Make(uint16_t key) { return Swizzle(key); }
 * }
 * ```
 */
public open class SwizzleCtorAccessor {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Swizzle Make(uint16_t key) { return Swizzle(key); }
     * ```
     */
    public fun make(key: UShort): Swizzle {
      TODO("Implement make")
    }
  }
}
