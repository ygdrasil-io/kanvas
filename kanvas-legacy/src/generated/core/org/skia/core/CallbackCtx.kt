package org.skia.core

import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct CallbackCtx {
 *     void (*fn)(CallbackCtx* self, int active_pixels /*<= kMaxStride_highp*/);
 *
 *     // When called, fn() will have our active pixels available in rgba.
 *     // When fn() returns, the pipeline will read back those active pixels from read_from.
 *     float rgba[4 * kMaxStride_highp];
 *     float* read_from = rgba;
 * }
 * ```
 */
public open class CallbackCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * void (*fn)(CallbackCtx* self, int active_pixels /*<= kMaxStride_highp*/)
   * ```
   */
  public var fn: (CallbackCtx?, Int) -> Unit,
  /**
   * C++ original:
   * ```cpp
   * float rgba[4 * kMaxStride_highp]
   * ```
   */
  public var rgba: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float* read_from = rgba
   * ```
   */
  public var readFrom: Float?,
)
