package org.skia.core

import kotlin.Int
import org.skia.sksl.TraceHook

/**
 * C++ original:
 * ```cpp
 * struct TraceLineCtx {
 *     const int* traceMask;
 *     SkSL::TraceHook* traceHook;
 *     int lineNumber;
 * }
 * ```
 */
public data class TraceLineCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * const int* traceMask
   * ```
   */
  public val traceMask: Int?,
  /**
   * C++ original:
   * ```cpp
   * SkSL::TraceHook* traceHook
   * ```
   */
  public var traceHook: TraceHook?,
  /**
   * C++ original:
   * ```cpp
   * int lineNumber
   * ```
   */
  public var lineNumber: Int,
)
