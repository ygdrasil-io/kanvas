package org.skia.core

import kotlin.Array

/**
 * C++ original:
 * ```cpp
 * struct SkDQuadPair {
 *     const SkDQuad& first() const { return (const SkDQuad&) pts[0]; }
 *     const SkDQuad& second() const { return (const SkDQuad&) pts[2]; }
 *     SkDPoint pts[5];
 * }
 * ```
 */
public data class SkDQuadPair public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDPoint pts[5]
   * ```
   */
  public var pts: Array<SkDPoint>,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkDQuad& first() const { return (const SkDQuad&) pts[0]; }
   * ```
   */
  public fun first(): SkDQuad {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDQuad& second() const { return (const SkDQuad&) pts[2]; }
   * ```
   */
  public fun second(): SkDQuad {
    TODO("Implement second")
  }
}
