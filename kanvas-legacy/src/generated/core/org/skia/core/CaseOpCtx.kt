package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct CaseOpCtx {
 *     int expectedValue;
 *     SkRPOffset offset;  // points to a pair of adjacent I32s: {I32 actualValue, I32 defaultMask}
 * }
 * ```
 */
public data class CaseOpCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int expectedValue
   * ```
   */
  public var expectedValue: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRPOffset offset
   * ```
   */
  public var offset: Int,
)
