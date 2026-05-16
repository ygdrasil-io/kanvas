package org.skia.tests

import kotlin.String
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * struct LabeledMatrix {
 *     SkMatrix    fMatrix;
 *     const char* fLabel;
 * }
 * ```
 */
public data class LabeledMatrix public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkMatrix    fMatrix
   * ```
   */
  public var fMatrix: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * const char* fLabel
   * ```
   */
  public val fLabel: String?,
)
