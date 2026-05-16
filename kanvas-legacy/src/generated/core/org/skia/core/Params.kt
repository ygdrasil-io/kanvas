package org.skia.core

import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct Params {
 *         size_t dx, dy;
 *         U16 dr,dg,db,da;
 *     }
 * ```
 */
public data class Params public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t dx
   * ```
   */
  public var dx: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t dx, dy
   * ```
   */
  public var dy: ULong,
  /**
   * C++ original:
   * ```cpp
   * U16 dr
   * ```
   */
  public var dr: Int,
  /**
   * C++ original:
   * ```cpp
   * U16 dr,dg
   * ```
   */
  public var dg: Int,
  /**
   * C++ original:
   * ```cpp
   * U16 dr,dg,db
   * ```
   */
  public var db: Int,
  /**
   * C++ original:
   * ```cpp
   * U16 dr,dg,db,da
   * ```
   */
  public var da: Int,
)
