package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct SkTSpanBounded {
 *     SkTSpan* fBounded;
 *     SkTSpanBounded* fNext;
 * }
 * ```
 */
public data class SkTSpanBounded public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTSpan* fBounded
   * ```
   */
  public var fBounded: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * SkTSpanBounded* fNext
   * ```
   */
  public var fNext: SkTSpanBounded?,
)
