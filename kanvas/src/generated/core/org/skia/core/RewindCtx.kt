package org.skia.core

import kotlin.FloatArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct RewindCtx {
 *     float  r[kMaxStride_highp];
 *     float  g[kMaxStride_highp];
 *     float  b[kMaxStride_highp];
 *     float  a[kMaxStride_highp];
 *     float dr[kMaxStride_highp];
 *     float dg[kMaxStride_highp];
 *     float db[kMaxStride_highp];
 *     float da[kMaxStride_highp];
 *     std::byte* base;
 *     SkRasterPipelineStage* stage;
 * }
 * ```
 */
public data class RewindCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float  r[kMaxStride_highp]
   * ```
   */
  public var r: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float  g[kMaxStride_highp]
   * ```
   */
  public var g: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float  b[kMaxStride_highp]
   * ```
   */
  public var b: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float  a[kMaxStride_highp]
   * ```
   */
  public var a: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float dr[kMaxStride_highp]
   * ```
   */
  public var dr: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float dg[kMaxStride_highp]
   * ```
   */
  public var dg: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float db[kMaxStride_highp]
   * ```
   */
  public var db: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * float da[kMaxStride_highp]
   * ```
   */
  public var da: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * std::byte* base
   * ```
   */
  public var base: Int?,
  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineStage* stage
   * ```
   */
  public var stage: SkRasterPipelineStage?,
)
