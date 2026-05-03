package org.skia.effects

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * struct ZValue {
 *     ZValue() : fZ(0.f) {}
 *     ZValue(float z) : fZ(z) {}
 *     operator float() const { return fZ; }
 *
 *     float fZ;
 * }
 * ```
 */
public data class ZValue public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fZ
   * ```
   */
  public var fZ: Float,
)
