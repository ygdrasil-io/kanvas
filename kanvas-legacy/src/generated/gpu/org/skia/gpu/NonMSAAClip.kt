package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct NonMSAAClip {
 *     AnalyticClip fAnalyticClip;
 *     AtlasClip    fAtlasClip;
 *
 *     bool isEmpty() const { return fAnalyticClip.isEmpty() && fAtlasClip.isEmpty(); }
 * }
 * ```
 */
public data class NonMSAAClip public constructor(
  /**
   * C++ original:
   * ```cpp
   * AnalyticClip fAnalyticClip
   * ```
   */
  public var fAnalyticClip: AnalyticClip,
  /**
   * C++ original:
   * ```cpp
   * AtlasClip    fAtlasClip
   * ```
   */
  public var fAtlasClip: AtlasClip,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fAnalyticClip.isEmpty() && fAtlasClip.isEmpty(); }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }
}
