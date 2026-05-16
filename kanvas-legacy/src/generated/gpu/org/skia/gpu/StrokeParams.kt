package org.skia.gpu

import kotlin.Float
import org.skia.core.SkStrokeRec

/**
 * C++ original:
 * ```cpp
 * struct StrokeParams {
 *     StrokeParams() = default;
 *     StrokeParams(float radius, float joinType) : fRadius(radius), fJoinType(joinType) {}
 *     StrokeParams(const SkStrokeRec& stroke) {
 *         this->set(stroke);
 *     }
 *     void set(const SkStrokeRec& stroke) {
 *         fRadius = stroke.getWidth() * .5f;
 *         fJoinType = GetJoinType(stroke);
 *     }
 *
 *     float fRadius;
 *     float fJoinType;  // See GetJoinType().
 * }
 * ```
 */
public data class StrokeParams public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fRadius
   * ```
   */
  public var fRadius: Float,
  /**
   * C++ original:
   * ```cpp
   * float fJoinType
   * ```
   */
  public var fJoinType: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(const SkStrokeRec& stroke) {
   *         fRadius = stroke.getWidth() * .5f;
   *         fJoinType = GetJoinType(stroke);
   *     }
   * ```
   */
  public fun `set`(stroke: SkStrokeRec) {
    TODO("Implement set")
  }
}
