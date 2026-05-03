package org.skia.core

import kotlin.Float
import kotlin.FloatArray

/**
 * C++ original:
 * ```cpp
 * struct MipmapCtx {
 *     // Original coords, saved before the base level logic
 *     float x[kMaxStride_highp];
 *     float y[kMaxStride_highp];
 *
 *     // Base level color
 *     float r[kMaxStride_highp];
 *     float g[kMaxStride_highp];
 *     float b[kMaxStride_highp];
 *     float a[kMaxStride_highp];
 *
 *     // Scale factors to transform base level coords to lower level coords
 *     float scaleX;
 *     float scaleY;
 *
 *     float lowerWeight;
 * }
 * ```
 */
public data class MipmapCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float x[kMaxStride_highp]
   * ```
   */
  public var x: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float y[kMaxStride_highp]
   * ```
   */
  public var y: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float r[kMaxStride_highp]
   * ```
   */
  public var r: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float g[kMaxStride_highp]
   * ```
   */
  public var g: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float b[kMaxStride_highp]
   * ```
   */
  public var b: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float a[kMaxStride_highp]
   * ```
   */
  public var a: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float scaleX
   * ```
   */
  public var scaleX: Float,
  /**
   * C++ original:
   * ```cpp
   * float scaleY
   * ```
   */
  public var scaleY: Float,
  /**
   * C++ original:
   * ```cpp
   * float lowerWeight
   * ```
   */
  public var lowerWeight: Float,
)
