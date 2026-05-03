package org.skia.core

import kotlin.Array
import kotlin.Int
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkOpCurve {
 *     SkPoint fPts[4];
 *     SkScalar fWeight;
 *     SkDEBUGCODE(SkPath::Verb fVerb;)
 *
 *     const SkPoint& operator[](int n) const {
 *         SkASSERT(n >= 0 && n <= SkPathOpsVerbToPoints(fVerb));
 *         return fPts[n];
 *     }
 *
 *     void dump() const;
 *
 *     void set(const SkDQuad& quad) {
 *         for (int index = 0; index < SkDQuad::kPointCount; ++index) {
 *             fPts[index] = quad[index].asSkPoint();
 *         }
 *         SkDEBUGCODE(fWeight = 1);
 *         SkDEBUGCODE(fVerb = SkPath::kQuad_Verb);
 *     }
 *
 *     void set(const SkDCubic& cubic) {
 *         for (int index = 0; index < SkDCubic::kPointCount; ++index) {
 *             fPts[index] = cubic[index].asSkPoint();
 *         }
 *         SkDEBUGCODE(fWeight = 1);
 *         SkDEBUGCODE(fVerb = SkPath::kCubic_Verb);
 *     }
 *
 * }
 * ```
 */
public data class SkOpCurve public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fPts[4]
   * ```
   */
  public var fPts: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWeight
   * ```
   */
  public var fWeight: SkScalar,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkPoint& operator[](int n) const {
   *         SkASSERT(n >= 0 && n <= SkPathOpsVerbToPoints(fVerb));
   *         return fPts[n];
   *     }
   * ```
   */
  public operator fun `get`(n: Int): SkPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpCurve::dump() const {
   *     int count = SkPathOpsVerbToPoints(SkDEBUGRELEASE(fVerb, SkPath::kCubic_Verb));
   *     SkDebugf("{{");
   *     int index;
   *     for (index = 0; index <= count - 1; ++index) {
   *         SkDebugf("{%1.9gf,%1.9gf}, ", fPts[index].fX, fPts[index].fY);
   *     }
   *     SkDebugf("{%1.9gf,%1.9gf}}}\n", fPts[index].fX, fPts[index].fY);
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkDQuad& quad) {
   *         for (int index = 0; index < SkDQuad::kPointCount; ++index) {
   *             fPts[index] = quad[index].asSkPoint();
   *         }
   *         SkDEBUGCODE(fWeight = 1);
   *         SkDEBUGCODE(fVerb = SkPath::kQuad_Verb);
   *     }
   * ```
   */
  public fun `set`(quad: SkDQuad) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkDCubic& cubic) {
   *         for (int index = 0; index < SkDCubic::kPointCount; ++index) {
   *             fPts[index] = cubic[index].asSkPoint();
   *         }
   *         SkDEBUGCODE(fWeight = 1);
   *         SkDEBUGCODE(fVerb = SkPath::kCubic_Verb);
   *     }
   * ```
   */
  public fun `set`(cubic: SkDCubic) {
    TODO("Implement set")
  }
}
