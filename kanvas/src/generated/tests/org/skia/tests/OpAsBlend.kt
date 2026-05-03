package org.skia.tests

import kotlin.Boolean
import org.skia.foundation.SkBlendMode

/**
 * C++ original:
 * ```cpp
 * struct OpAsBlend {
 *     SkBlendMode fMode;
 *     bool fInverse = false;
 * }
 * ```
 */
public data class OpAsBlend public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fMode
   * ```
   */
  public var fMode: SkBlendMode,
  /**
   * C++ original:
   * ```cpp
   * bool fInverse = false
   * ```
   */
  public var fInverse: Boolean,
)
