package org.skia.tests

import kotlin.Int
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct OvalSet {
 *     SkRect fBounds;
 *     int fColumns;
 *     int fRows;
 *     int fRotations;
 *     SkScalar fXSpacing;
 *     SkScalar fYSpacing;
 * }
 * ```
 */
public data class OvalSet public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect fBounds
   * ```
   */
  public var fBounds: SkRect,
  /**
   * C++ original:
   * ```cpp
   * int fColumns
   * ```
   */
  public var fColumns: Int,
  /**
   * C++ original:
   * ```cpp
   * int fRows
   * ```
   */
  public var fRows: Int,
  /**
   * C++ original:
   * ```cpp
   * int fRotations
   * ```
   */
  public var fRotations: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fXSpacing
   * ```
   */
  public var fXSpacing: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fYSpacing
   * ```
   */
  public var fYSpacing: SkScalar,
)
