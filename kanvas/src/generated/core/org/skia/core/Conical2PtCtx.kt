package org.skia.core

import kotlin.Float
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct Conical2PtCtx {
 *     uint32_t fMask[kMaxStride_highp];
 *     float    fP0,
 *              fP1;
 * }
 * ```
 */
public data class Conical2PtCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fMask[kMaxStride_highp]
   * ```
   */
  public var fMask: IntArray,
  /**
   * C++ original:
   * ```cpp
   * float    fP0
   * ```
   */
  public var fP0: Float,
  /**
   * C++ original:
   * ```cpp
   * float    fP0,
   *              fP1
   * ```
   */
  public var fP1: Float,
)
