package org.skia.skcms

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * struct TF_HLGish { float R,G,a,b,c,K_minus_1; }
 * ```
 */
public data class TFHLGish public constructor(
  /**
   * C++ original:
   * ```cpp
   * float R
   * ```
   */
  public var r: Float,
  /**
   * C++ original:
   * ```cpp
   * float R,G
   * ```
   */
  public var g: Float,
  /**
   * C++ original:
   * ```cpp
   * float R,G,a
   * ```
   */
  public var a: Float,
  /**
   * C++ original:
   * ```cpp
   * float R,G,a,b
   * ```
   */
  public var b: Float,
  /**
   * C++ original:
   * ```cpp
   * float R,G,a,b,c
   * ```
   */
  public var c: Float,
  /**
   * C++ original:
   * ```cpp
   * float R,G,a,b,c,K_minus_1
   * ```
   */
  public var kMinus1: Float,
)
