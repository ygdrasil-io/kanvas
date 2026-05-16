package org.skia.core

import kotlin.FloatArray

/**
 * C++ original:
 * ```cpp
 * struct SamplerCtx {
 *     float      x[kMaxStride_highp];
 *     float      y[kMaxStride_highp];
 *     float     fx[kMaxStride_highp];
 *     float     fy[kMaxStride_highp];
 *     float scalex[kMaxStride_highp];
 *     float scaley[kMaxStride_highp];
 *
 *     // for bicubic_[np][13][xy]
 *     float weights[16];
 *     float wx[4][kMaxStride_highp];
 *     float wy[4][kMaxStride_highp];
 * }
 * ```
 */
public data class SamplerCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float      x[kMaxStride_highp]
   * ```
   */
  public var x: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float      y[kMaxStride_highp]
   * ```
   */
  public var y: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float     fx[kMaxStride_highp]
   * ```
   */
  public var fx: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float     fy[kMaxStride_highp]
   * ```
   */
  public var fy: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float scalex[kMaxStride_highp]
   * ```
   */
  public var scalex: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float scaley[kMaxStride_highp]
   * ```
   */
  public var scaley: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float weights[16]
   * ```
   */
  public var weights: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float wx[4][kMaxStride_highp]
   * ```
   */
  public var wx: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float wy[4][kMaxStride_highp]
   * ```
   */
  public var wy: FloatArray,
)
