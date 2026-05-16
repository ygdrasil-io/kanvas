package org.skia.tests

import kotlin.String
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct AnnotationRec {
 *     const SkRect    fRect;
 *     const char*     fKey;
 *     sk_sp<SkData>   fValue;
 * }
 * ```
 */
public data class AnnotationRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkRect    fRect
   * ```
   */
  public val fRect: SkRect,
  /**
   * C++ original:
   * ```cpp
   * const char*     fKey
   * ```
   */
  public val fKey: String?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData>   fValue
   * ```
   */
  public var fValue: SkSp<SkData>,
)
