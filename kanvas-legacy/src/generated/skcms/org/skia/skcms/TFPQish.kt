package org.skia.skcms

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * struct TF_PQish  { float A,B,C,D,E,F; }
 * ```
 */
public data class TFPQish public constructor(
  /**
   * C++ original:
   * ```cpp
   * float A
   * ```
   */
  public var a: Float,
  /**
   * C++ original:
   * ```cpp
   * float A,B
   * ```
   */
  public var b: Float,
  /**
   * C++ original:
   * ```cpp
   * float A,B,C
   * ```
   */
  public var c: Float,
  /**
   * C++ original:
   * ```cpp
   * float A,B,C,D
   * ```
   */
  public var d: Float,
  /**
   * C++ original:
   * ```cpp
   * float A,B,C,D,E
   * ```
   */
  public var e: Float,
  /**
   * C++ original:
   * ```cpp
   * float A,B,C,D,E,F
   * ```
   */
  public var f: Float,
)
