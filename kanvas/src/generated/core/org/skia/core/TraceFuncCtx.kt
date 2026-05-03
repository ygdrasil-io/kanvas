package org.skia.core

import kotlin.Int
import org.skia.sksl.TraceHook

/**
 * C++ original:
 * ```cpp
 * struct TraceFuncCtx {
 *     const int* traceMask;
 *     SkSL::TraceHook* traceHook;
 *     int funcIdx;
 * }
 * ```
 */
public data class TraceFuncCtx public constructor(
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
   * int funcIdx
   * ```
   */
  public var funcIdx: Int,
)
