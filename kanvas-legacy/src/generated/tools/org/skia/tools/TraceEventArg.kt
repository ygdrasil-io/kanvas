package org.skia.tools

import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct TraceEventArg {
 *     uint8_t     fArgType;
 *     const char* fArgName;
 *     uint64_t    fArgValue;
 * }
 * ```
 */
public data class TraceEventArg public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t     fArgType
   * ```
   */
  public var fArgType: Int,
  /**
   * C++ original:
   * ```cpp
   * const char* fArgName
   * ```
   */
  public val fArgName: String?,
  /**
   * C++ original:
   * ```cpp
   * uint64_t    fArgValue
   * ```
   */
  public var fArgValue: Int,
)
