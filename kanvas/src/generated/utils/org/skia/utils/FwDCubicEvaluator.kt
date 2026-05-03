package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCubicCoeff
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class FwDCubicEvaluator {
 *
 * public:
 *
 *     /**
 *      * Receives the 4 control points of the cubic bezier.
 *      */
 *
 *     explicit FwDCubicEvaluator(const SkPoint points[4])
 *             : fCoefs(points) {
 *         memcpy(fPoints, points, 4 * sizeof(SkPoint));
 *
 *         this->restart(1);
 *     }
 *
 *     /**
 *      * Restarts the forward differences evaluator to the first value of t = 0.
 *      */
 *     void restart(int divisions)  {
 *         fDivisions = divisions;
 *         fCurrent    = 0;
 *         fMax        = fDivisions + 1;
 *         skvx::float2 h = 1.f / fDivisions;
 *         skvx::float2 h2 = h * h;
 *         skvx::float2 h3 = h2 * h;
 *         skvx::float2 fwDiff3 = 6 * fCoefs.fA * h3;
 *         fFwDiff[3] = to_point(fwDiff3);
 *         fFwDiff[2] = to_point(fwDiff3 + times_2(fCoefs.fB) * h2);
 *         fFwDiff[1] = to_point(fCoefs.fA * h3 + fCoefs.fB * h2 + fCoefs.fC * h);
 *         fFwDiff[0] = to_point(fCoefs.fD);
 *     }
 *
 *     /**
 *      * Check if the evaluator is still within the range of 0<=t<=1
 *      */
 *     bool done() const {
 *         return fCurrent > fMax;
 *     }
 *
 *     /**
 *      * Call next to obtain the SkPoint sampled and move to the next one.
 *      */
 *     SkPoint next() {
 *         SkPoint point = fFwDiff[0];
 *         fFwDiff[0]    += fFwDiff[1];
 *         fFwDiff[1]    += fFwDiff[2];
 *         fFwDiff[2]    += fFwDiff[3];
 *         fCurrent++;
 *         return point;
 *     }
 *
 *     const SkPoint* getCtrlPoints() const {
 *         return fPoints;
 *     }
 *
 * private:
 *     SkCubicCoeff fCoefs;
 *     int fMax, fCurrent, fDivisions;
 *     SkPoint fFwDiff[4], fPoints[4];
 * }
 * ```
 */
public data class FwDCubicEvaluator public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkCubicCoeff fCoefs
   * ```
   */
  private var fCoefs: SkCubicCoeff,
  /**
   * C++ original:
   * ```cpp
   * int fMax
   * ```
   */
  private var fMax: Int,
  /**
   * C++ original:
   * ```cpp
   * int fMax, fCurrent
   * ```
   */
  private var fCurrent: Int,
  /**
   * C++ original:
   * ```cpp
   * int fMax, fCurrent, fDivisions
   * ```
   */
  private var fDivisions: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fFwDiff[4]
   * ```
   */
  private var fFwDiff: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fFwDiff[4], fPoints[4]
   * ```
   */
  private var fPoints: Array<SkPoint>,
) {
  /**
   * C++ original:
   * ```cpp
   * void restart(int divisions)  {
   *         fDivisions = divisions;
   *         fCurrent    = 0;
   *         fMax        = fDivisions + 1;
   *         skvx::float2 h = 1.f / fDivisions;
   *         skvx::float2 h2 = h * h;
   *         skvx::float2 h3 = h2 * h;
   *         skvx::float2 fwDiff3 = 6 * fCoefs.fA * h3;
   *         fFwDiff[3] = to_point(fwDiff3);
   *         fFwDiff[2] = to_point(fwDiff3 + times_2(fCoefs.fB) * h2);
   *         fFwDiff[1] = to_point(fCoefs.fA * h3 + fCoefs.fB * h2 + fCoefs.fC * h);
   *         fFwDiff[0] = to_point(fCoefs.fD);
   *     }
   * ```
   */
  public fun restart(divisions: Int) {
    TODO("Implement restart")
  }

  /**
   * C++ original:
   * ```cpp
   * bool done() const {
   *         return fCurrent > fMax;
   *     }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint next() {
   *         SkPoint point = fFwDiff[0];
   *         fFwDiff[0]    += fFwDiff[1];
   *         fFwDiff[1]    += fFwDiff[2];
   *         fFwDiff[2]    += fFwDiff[3];
   *         fCurrent++;
   *         return point;
   *     }
   * ```
   */
  public fun next(): SkPoint {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* getCtrlPoints() const {
   *         return fPoints;
   *     }
   * ```
   */
  public fun getCtrlPoints(): SkPoint {
    TODO("Implement getCtrlPoints")
  }
}
