package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkPathVerb

/**
 * C++ original:
 * ```cpp
 * class SkDCurveSweep {
 * public:
 *     bool isCurve() const { return fIsCurve; }
 *     bool isOrdered() const { return fOrdered; }
 *     void setCurveHullSweep(SkPath::Verb verb);
 *
 *     SkDCurve fCurve;
 *     SkDVector fSweep[2];
 * private:
 *     bool fIsCurve;
 *     bool fOrdered;  // cleared when a cubic's control point isn't between the sweep vectors
 *
 * }
 * ```
 */
public data class SkDCurveSweep public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDCurve fCurve
   * ```
   */
  public var fCurve: SkDCurve,
  /**
   * C++ original:
   * ```cpp
   * SkDVector fSweep[2]
   * ```
   */
  public var fSweep: Array<SkDVector>,
  /**
   * C++ original:
   * ```cpp
   * bool fIsCurve
   * ```
   */
  private var fIsCurve: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fOrdered
   * ```
   */
  private var fOrdered: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isCurve() const { return fIsCurve; }
   * ```
   */
  public fun isCurve(): Boolean {
    TODO("Implement isCurve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOrdered() const { return fOrdered; }
   * ```
   */
  public fun isOrdered(): Boolean {
    TODO("Implement isOrdered")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCurveSweep::setCurveHullSweep(SkPath::Verb verb) {
   *     fOrdered = true;
   *     fSweep[0] = fCurve[1] - fCurve[0];
   *     if (SkPath::kLine_Verb == verb) {
   *         fSweep[1] = fSweep[0];
   *         fIsCurve = false;
   *         return;
   *     }
   *     fSweep[1] = fCurve[2] - fCurve[0];
   *     // OPTIMIZE: I do the following float check a lot -- probably need a
   *     // central place for this val-is-small-compared-to-curve check
   *     double maxVal = 0;
   *     for (int index = 0; index <= SkPathOpsVerbToPoints(verb); ++index) {
   *         maxVal = std::max(maxVal, std::max(SkTAbs(fCurve[index].fX),
   *                 SkTAbs(fCurve[index].fY)));
   *     }
   *     {
   *         if (SkPath::kCubic_Verb != verb) {
   *             if (roughly_zero_when_compared_to(fSweep[0].fX, maxVal)
   *                     && roughly_zero_when_compared_to(fSweep[0].fY, maxVal)) {
   *                 fSweep[0] = fSweep[1];
   *             }
   *             goto setIsCurve;
   *         }
   *         SkDVector thirdSweep = fCurve[3] - fCurve[0];
   *         if (fSweep[0].fX == 0 && fSweep[0].fY == 0) {
   *             fSweep[0] = fSweep[1];
   *             fSweep[1] = thirdSweep;
   *             if (roughly_zero_when_compared_to(fSweep[0].fX, maxVal)
   *                     && roughly_zero_when_compared_to(fSweep[0].fY, maxVal)) {
   *                 fSweep[0] = fSweep[1];
   *                 fCurve[1] = fCurve[3];
   *             }
   *             goto setIsCurve;
   *         }
   *         double s1x3 = fSweep[0].crossCheck(thirdSweep);
   *         double s3x2 = thirdSweep.crossCheck(fSweep[1]);
   *         if (s1x3 * s3x2 >= 0) {  // if third vector is on or between first two vectors
   *             goto setIsCurve;
   *         }
   *         double s2x1 = fSweep[1].crossCheck(fSweep[0]);
   *         // FIXME: If the sweep of the cubic is greater than 180 degrees, we're in trouble
   *         // probably such wide sweeps should be artificially subdivided earlier so that never happens
   *         SkASSERT(s1x3 * s2x1 < 0 || s1x3 * s3x2 < 0);
   *         if (s3x2 * s2x1 < 0) {
   *             SkASSERT(s2x1 * s1x3 > 0);
   *             fSweep[0] = fSweep[1];
   *             fOrdered = false;
   *         }
   *         fSweep[1] = thirdSweep;
   *     }
   * setIsCurve:
   *     fIsCurve = fSweep[0].crossCheck(fSweep[1]) != 0;
   * }
   * ```
   */
  public fun setCurveHullSweep(verb: SkPathVerb) {
    TODO("Implement setCurveHullSweep")
  }
}
