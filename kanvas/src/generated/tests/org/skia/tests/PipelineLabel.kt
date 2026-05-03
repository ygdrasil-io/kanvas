package org.skia.tests

import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct PipelineLabel {
 *     int fNumHits;         // the number of uses in 9 of the 14 most visited web sites
 *     const char* fString;
 * }
 * ```
 */
public data class PipelineLabel public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fNumHits
   * ```
   */
  public var fNumHits: Int,
  /**
   * C++ original:
   * ```cpp
   * const char* fString
   * ```
   */
  public val fString: String?,
)
