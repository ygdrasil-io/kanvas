package org.skia.tests

import kotlin.String
import org.skia.foundation.SkImage

/**
 * C++ original:
 * ```cpp
 * struct State {
 *     const char* fStr;
 *     SkImage*    fImg;
 * }
 * ```
 */
public data class State public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fStr
   * ```
   */
  public val fStr: String?,
  /**
   * C++ original:
   * ```cpp
   * SkImage*    fImg
   * ```
   */
  public var fImg: SkImage?,
)
