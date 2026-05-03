package org.skia.core

import kotlin.Int
import org.skia.sksl.TraceHook

/**
 * C++ original:
 * ```cpp
 * struct TraceVarCtx {
 *     const int* traceMask;
 *     SkSL::TraceHook* traceHook;
 *     int slotIdx, numSlots;
 *     const int* data;
 *     const uint32_t *indirectOffset;  // can be null; if set, an offset applied to `data`
 *     uint32_t indirectLimit;          // the indirect offset is clamped to this upper bound
 * }
 * ```
 */
public data class TraceVarCtx public constructor(
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
   * int slotIdx
   * ```
   */
  public var slotIdx: Int,
  /**
   * C++ original:
   * ```cpp
   * int slotIdx, numSlots
   * ```
   */
  public var numSlots: Int,
  /**
   * C++ original:
   * ```cpp
   * const int* data
   * ```
   */
  public val `data`: Int?,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t *indirectOffset
   * ```
   */
  public val indirectOffset: Int?,
  /**
   * C++ original:
   * ```cpp
   * uint32_t indirectLimit
   * ```
   */
  public var indirectLimit: Int,
)
