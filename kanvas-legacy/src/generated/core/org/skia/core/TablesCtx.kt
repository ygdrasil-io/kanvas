package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct TablesCtx {
 *     const uint8_t *r, *g, *b, *a;
 * }
 * ```
 */
public data class TablesCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * const uint8_t *r
   * ```
   */
  public val r: Int?,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t *r, *g
   * ```
   */
  public val g: Int?,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t *r, *g, *b
   * ```
   */
  public val b: Int?,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t *r, *g, *b, *a
   * ```
   */
  public val a: Int?,
)
