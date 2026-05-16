package org.skia.core

import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class TraceStringWithCopy {
 *  public:
 *   explicit TraceStringWithCopy(const char* str) : str_(str) {}
 *   operator const char* () const { return str_; }
 *  private:
 *   const char* str_;
 * }
 * ```
 */
public data class TraceStringWithCopy public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* str_
   * ```
   */
  private val str: String?,
)
