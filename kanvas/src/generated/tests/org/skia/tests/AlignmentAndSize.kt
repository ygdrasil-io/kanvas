package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct AlignmentAndSize {
 *     int alignment;
 *     int size;
 * }
 * ```
 */
public data class AlignmentAndSize public constructor(
  /**
   * C++ original:
   * ```cpp
   * int alignment
   * ```
   */
  public var alignment: Int,
  /**
   * C++ original:
   * ```cpp
   * int size
   * ```
   */
  public var size: Int,
)
