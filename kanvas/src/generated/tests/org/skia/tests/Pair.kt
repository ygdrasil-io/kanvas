package org.skia.tests

import kotlin.Pair
import kotlin.String
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.gpu.PaintOptions

/**
 * C++ original:
 * ```cpp
 * struct Pair {
 *     SkColorType fColorType;
 *     const char* fValid;
 * }
 * ```
 */
public data class Pair public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColorType fColorType
   * ```
   */
  public var fColorType: SkColorType,
  /**
   * C++ original:
   * ```cpp
   * const char* fValid
   * ```
   */
  public val fValid: String?,
)

public typealias GradientCreationFunc = Pair<SkPaint, PaintOptions>
