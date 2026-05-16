package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct PointSet {
 *     const SkPoint* fPts;
 *     size_t         fCount;
 *     bool           fIsFinite;
 * }
 * ```
 */
public data class PointSet public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkPoint* fPts
   * ```
   */
  public val fPts: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * size_t         fCount
   * ```
   */
  public var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * bool           fIsFinite
   * ```
   */
  public var fIsFinite: Boolean,
)
