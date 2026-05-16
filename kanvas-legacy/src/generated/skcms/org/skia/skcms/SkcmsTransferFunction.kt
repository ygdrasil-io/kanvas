package org.skia.skcms

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * struct skcms_TransferFunction {
 *     float g, a,b,c,d,e,f;
 * }
 * ```
 */
public data class SkcmsTransferFunction public constructor(
  /**
   * C++ original:
   * ```cpp
   * float g
   * ```
   */
  public var g: Float,
  /**
   * C++ original:
   * ```cpp
   * float g, a
   * ```
   */
  public var a: Float,
  /**
   * C++ original:
   * ```cpp
   * float g, a,b
   * ```
   */
  public var b: Float,
  /**
   * C++ original:
   * ```cpp
   * float g, a,b,c
   * ```
   */
  public var c: Float,
  /**
   * C++ original:
   * ```cpp
   * float g, a,b,c,d
   * ```
   */
  public var d: Float,
  /**
   * C++ original:
   * ```cpp
   * float g, a,b,c,d,e
   * ```
   */
  public var e: Float,
  /**
   * C++ original:
   * ```cpp
   * float g, a,b,c,d,e,f
   * ```
   */
  public var f: Float,
)

public typealias SkcmsTransferFunction = SkcmsTransferFunction
