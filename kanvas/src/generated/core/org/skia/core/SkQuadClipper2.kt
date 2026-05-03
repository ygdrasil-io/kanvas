package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkQuadClipper2 {
 * public:
 *     bool clipQuad(const SkPoint pts[3], const SkRect& clip);
 *     bool clipCubic(const SkPoint pts[4], const SkRect& clip);
 *
 *     SkPath::Verb next(SkPoint pts[]);
 *
 * private:
 *     SkPoint*        fCurrPoint;
 *     SkPath::Verb*   fCurrVerb;
 *
 *     enum {
 *         kMaxVerbs = 13,
 *         kMaxPoints = 32
 *     };
 *     SkPoint         fPoints[kMaxPoints];
 *     SkPath::Verb    fVerbs[kMaxVerbs];
 *
 *     void clipMonoQuad(const SkPoint srcPts[3], const SkRect& clip);
 *     void clipMonoCubic(const SkPoint srcPts[4], const SkRect& clip);
 *     void appendVLine(SkScalar x, SkScalar y0, SkScalar y1, bool reverse);
 *     void appendQuad(const SkPoint pts[3], bool reverse);
 *     void appendCubic(const SkPoint pts[4], bool reverse);
 * }
 * ```
 */
public data class SkQuadClipper2 public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint*        fCurrPoint
   * ```
   */
  private var fCurrPoint: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * SkPath::Verb*   fCurrVerb
   * ```
   */
  private var fCurrVerb: SkPathVerb?,
  /**
   * C++ original:
   * ```cpp
   * SkPoint         fPoints[kMaxPoints]
   * ```
   */
  private var fPoints: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkPath::Verb    fVerbs[kMaxVerbs]
   * ```
   */
  private var fVerbs: Array<SkPathVerb>,
) {
  /**
   * C++ original:
   * ```cpp
   * bool clipQuad(const SkPoint pts[3], const SkRect& clip)
   * ```
   */
  public fun clipQuad(pts: Array<SkPoint>, clip: SkRect): Boolean {
    TODO("Implement clipQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * bool clipCubic(const SkPoint pts[4], const SkRect& clip)
   * ```
   */
  public fun clipCubic(pts: Array<SkPoint>, clip: SkRect): Boolean {
    TODO("Implement clipCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath::Verb next(SkPoint pts[])
   * ```
   */
  public fun next(pts: Array<SkPoint>): SkPathVerb {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipMonoQuad(const SkPoint srcPts[3], const SkRect& clip)
   * ```
   */
  private fun clipMonoQuad(srcPts: Array<SkPoint>, clip: SkRect) {
    TODO("Implement clipMonoQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipMonoCubic(const SkPoint srcPts[4], const SkRect& clip)
   * ```
   */
  private fun clipMonoCubic(srcPts: Array<SkPoint>, clip: SkRect) {
    TODO("Implement clipMonoCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendVLine(SkScalar x, SkScalar y0, SkScalar y1, bool reverse)
   * ```
   */
  private fun appendVLine(
    x: SkScalar,
    y0: SkScalar,
    y1: SkScalar,
    reverse: Boolean,
  ) {
    TODO("Implement appendVLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendQuad(const SkPoint pts[3], bool reverse)
   * ```
   */
  private fun appendQuad(pts: Array<SkPoint>, reverse: Boolean) {
    TODO("Implement appendQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * void appendCubic(const SkPoint pts[4], bool reverse)
   * ```
   */
  private fun appendCubic(pts: Array<SkPoint>, reverse: Boolean) {
    TODO("Implement appendCubic")
  }

  public companion object {
    public val kMaxVerbs: Int = TODO("Initialize kMaxVerbs")

    public val kMaxPoints: Int = TODO("Initialize kMaxPoints")
  }
}
