package org.skia.core

import kotlin.Int
import org.skia.sksl.TraceHook

/**
 * C++ original:
 * ```cpp
 * struct TraceScopeCtx {
 *     const int* traceMask;
 *     SkSL::TraceHook* traceHook;
 *     int delta;
 * }
 * ```
 */
public data class TraceScopeCtx public constructor(
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
   * int delta
   * ```
   */
  public var delta: Int,
)
