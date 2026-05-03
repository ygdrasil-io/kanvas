package org.skia.core

import kotlin.Float
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct DFData {
 *     float   fAlpha;      // alpha value of source texel
 *     float   fDistSq;     // distance squared to nearest (so far) edge texel
 *     SkPoint fDistVector; // distance vector to nearest (so far) edge texel
 * }
 * ```
 */
public data class DFData public constructor(
  /**
   * C++ original:
   * ```cpp
   * float   fAlpha
   * ```
   */
  public var fAlpha: Float,
  /**
   * C++ original:
   * ```cpp
   * float   fDistSq
   * ```
   */
  public var fDistSq: Float,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fDistVector
   * ```
   */
  public var fDistVector: SkPoint,
)
