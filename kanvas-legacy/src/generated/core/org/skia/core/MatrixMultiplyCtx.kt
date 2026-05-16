package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct MatrixMultiplyCtx {
 *     SkRPOffset dst;
 *     uint8_t leftColumns, leftRows, rightColumns, rightRows;
 * }
 * ```
 */
public data class MatrixMultiplyCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRPOffset dst
   * ```
   */
  public var dst: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t leftColumns
   * ```
   */
  public var leftColumns: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t leftColumns, leftRows
   * ```
   */
  public var leftRows: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t leftColumns, leftRows, rightColumns
   * ```
   */
  public var rightColumns: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t leftColumns, leftRows, rightColumns, rightRows
   * ```
   */
  public var rightRows: Int,
)
