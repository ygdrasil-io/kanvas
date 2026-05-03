package org.skia.tests

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct GraphiteReadPixelTestRules {
 *     // Test unpremul sources? We could omit this and detect that creating the source of the read
 *     // failed but having it lets us skip generating reference color data.
 *     bool fAllowUnpremulSrc = true;
 *     // Are reads that are overlapping but not contained by the src bounds expected to succeed?
 *     bool fUncontainedRectSucceeds = true;
 * }
 * ```
 */
public data class GraphiteReadPixelTestRules public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fAllowUnpremulSrc = true
   * ```
   */
  public var fAllowUnpremulSrc: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fUncontainedRectSucceeds = true
   * ```
   */
  public var fUncontainedRectSucceeds: Boolean,
)
