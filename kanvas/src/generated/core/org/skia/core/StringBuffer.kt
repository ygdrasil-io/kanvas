package org.skia.core

import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct StringBuffer {
 *     char*  fText;
 *     int    fLength;
 * }
 * ```
 */
public data class StringBuffer public constructor(
  /**
   * C++ original:
   * ```cpp
   * char*  fText
   * ```
   */
  public var fText: String?,
  /**
   * C++ original:
   * ```cpp
   * int    fLength
   * ```
   */
  public var fLength: Int,
)
