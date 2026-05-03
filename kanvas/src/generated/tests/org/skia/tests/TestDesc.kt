package org.skia.tests

import kotlin.String
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct TestDesc {
 *     void (*fun)(skiatest::Reporter*, const char* filename);
 *     const char* str;
 * }
 * ```
 */
public data class TestDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * void (*fun)(skiatest::Reporter*, const char* filename)
   * ```
   */
  public val `fun`: (Reporter?, String?) -> Unit,
  /**
   * C++ original:
   * ```cpp
   * const char* str
   * ```
   */
  public val str: String?,
)
