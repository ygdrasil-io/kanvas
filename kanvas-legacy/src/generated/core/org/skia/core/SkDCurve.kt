package org.skia.core

import kotlin.Array
import kotlin.Double
import kotlin.Int
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkDCurve {
 *     union {
 *         SkDLine fLine;
 *         SkDQuad fQuad;
 *         SkDConic fConic;
 *         SkDCubic fCubic;
 *     };
 *     SkDEBUGCODE(SkPath::Verb fVerb;)
 *
 *     const SkDPoint& operator[](int n) const {
 *         SkASSERT(n >= 0 && n <= SkPathOpsVerbToPoints(fVerb));
 *         return fCubic[n];
 *     }
 *
 *     SkDPoint& operator[](int n) {
 *         SkASSERT(n >= 0 && n <= SkPathOpsVerbToPoints(fVerb));
 *         return fCubic[n];
 *     }
 *
 *     SkDPoint conicTop(const SkPoint curve[3], SkScalar curveWeight,
 *                       double s, double e, double* topT);
 *     SkDPoint cubicTop(const SkPoint curve[4], SkScalar , double s, double e, double* topT);
 *     void dump() const;
 *     void dumpID(int ) const;
 *     SkDPoint lineTop(const SkPoint[2], SkScalar , double , double , double* topT);
 *     double nearPoint(SkPath::Verb verb, const SkDPoint& xy, const SkDPoint& opp) const;
 *     SkDPoint quadTop(const SkPoint curve[3], SkScalar , double s, double e, double* topT);
 *
 *     void setConicBounds(const SkPoint curve[3], SkScalar curveWeight,
 *                         double s, double e, SkPathOpsBounds* );
 *     void setCubicBounds(const SkPoint curve[4], SkScalar ,
 *                         double s, double e, SkPathOpsBounds* );
 *     void setQuadBounds(const SkPoint curve[3], SkScalar ,
 *                        double s, double e, SkPathOpsBounds*);
 * }
 * ```
 */
public data class SkDCurve public constructor(
  private var fLine: SkDLine,
  private var fQuad: SkDQuad,
  private var fConic: SkDConic,
  private var fCubic: SkDCubic,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const {
   *         SkASSERT(n >= 0 && n <= SkPathOpsVerbToPoints(fVerb));
   *         return fCubic[n];
   *     }
   * ```
   */
  public operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) {
   *         SkASSERT(n >= 0 && n <= SkPathOpsVerbToPoints(fVerb));
   *         return fCubic[n];
   *     }
   * ```
   */
  public fun conicTop(
    curve: Array<SkPoint>,
    curveWeight: SkScalar,
    s: Double,
    e: Double,
    topT: Double?,
  ): SkDPoint {
    TODO("Implement conicTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint conicTop(const SkPoint curve[3], SkScalar curveWeight,
   *                       double s, double e, double* topT)
   * ```
   */
  public fun cubicTop(
    curve: Array<SkPoint>,
    param1: SkScalar,
    s: Double,
    e: Double,
    topT: Double?,
  ): SkDPoint {
    TODO("Implement cubicTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint cubicTop(const SkPoint curve[4], SkScalar , double s, double e, double* topT)
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCurve::dump() const {
   *     dumpID(-1);
   * }
   * ```
   */
  public fun dumpID(id: Int) {
    TODO("Implement dumpID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCurve::dumpID(int id) const {
   * #ifndef SK_RELEASE
   *     switch(fVerb) {
   *         case SkPath::kLine_Verb:
   *             fLine.dumpID(id);
   *             break;
   *         case SkPath::kQuad_Verb:
   *             fQuad.dumpID(id);
   *             break;
   *         case SkPath::kConic_Verb:
   *             fConic.dumpID(id);
   *             break;
   *         case SkPath::kCubic_Verb:
   *             fCubic.dumpID(id);
   *             break;
   *         default:
   *             SkASSERT(0);
   *     }
   * #else
   *     fCubic.dumpID(id);
   * #endif
   * }
   * ```
   */
  public fun lineTop(
    param0: Array<SkPoint>,
    param1: SkScalar,
    param2: Double,
    param3: Double,
    topT: Double?,
  ): SkDPoint {
    TODO("Implement lineTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint lineTop(const SkPoint[2], SkScalar , double , double , double* topT)
   * ```
   */
  public fun nearPoint(
    verb: SkPathVerb,
    xy: SkDPoint,
    opp: SkDPoint,
  ): Double {
    TODO("Implement nearPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkDCurve::nearPoint(SkPath::Verb verb, const SkDPoint& xy, const SkDPoint& opp) const {
   *     int count = SkPathOpsVerbToPoints(verb);
   *     double minX = fCubic.fPts[0].fX;
   *     double maxX = minX;
   *     for (int index = 1; index <= count; ++index) {
   *         minX = std::min(minX, fCubic.fPts[index].fX);
   *         maxX = std::max(maxX, fCubic.fPts[index].fX);
   *     }
   *     if (!AlmostBetweenUlps(minX, xy.fX, maxX)) {
   *         return -1;
   *     }
   *     double minY = fCubic.fPts[0].fY;
   *     double maxY = minY;
   *     for (int index = 1; index <= count; ++index) {
   *         minY = std::min(minY, fCubic.fPts[index].fY);
   *         maxY = std::max(maxY, fCubic.fPts[index].fY);
   *     }
   *     if (!AlmostBetweenUlps(minY, xy.fY, maxY)) {
   *         return -1;
   *     }
   *     SkIntersections i;
   *     SkDLine perp = {{ xy, { xy.fX + opp.fY - xy.fY, xy.fY + xy.fX - opp.fX }}};
   *     (*CurveDIntersectRay[verb])(*this, perp, &i);
   *     int minIndex = -1;
   *     double minDist = FLT_MAX;
   *     for (int index = 0; index < i.used(); ++index) {
   *         double dist = xy.distance(i.pt(index));
   *         if (minDist > dist) {
   *             minDist = dist;
   *             minIndex = index;
   *         }
   *     }
   *     if (minIndex < 0) {
   *         return -1;
   *     }
   *     double largest = std::max(std::max(maxX, maxY), -std::min(minX, minY));
   *     if (!AlmostEqualUlps_Pin(largest, largest + minDist)) { // is distance within ULPS tolerance?
   *         return -1;
   *     }
   *     return SkPinT(i[0][minIndex]);
   * }
   * ```
   */
  public fun quadTop(
    curve: Array<SkPoint>,
    param1: SkScalar,
    s: Double,
    e: Double,
    topT: Double?,
  ): SkDPoint {
    TODO("Implement quadTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint quadTop(const SkPoint curve[3], SkScalar , double s, double e, double* topT)
   * ```
   */
  public fun setConicBounds(
    curve: Array<SkPoint>,
    curveWeight: SkScalar,
    s: Double,
    e: Double,
    bounds: SkPathOpsBounds?,
  ) {
    TODO("Implement setConicBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCurve::setConicBounds(const SkPoint curve[3], SkScalar curveWeight,
   *         double tStart, double tEnd, SkPathOpsBounds* bounds) {
   *     SkDConic dCurve;
   *     dCurve.set(curve, curveWeight);
   *     SkDRect dRect;
   *     dRect.setBounds(dCurve, fConic, tStart, tEnd);
   *     bounds->setLTRB(SkDoubleToScalar(dRect.fLeft), SkDoubleToScalar(dRect.fTop),
   *                     SkDoubleToScalar(dRect.fRight), SkDoubleToScalar(dRect.fBottom));
   * }
   * ```
   */
  public fun setCubicBounds(
    curve: Array<SkPoint>,
    param1: SkScalar,
    s: Double,
    e: Double,
    bounds: SkPathOpsBounds?,
  ) {
    TODO("Implement setCubicBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCurve::setCubicBounds(const SkPoint curve[4], SkScalar ,
   *         double tStart, double tEnd, SkPathOpsBounds* bounds) {
   *     SkDCubic dCurve;
   *     dCurve.set(curve);
   *     SkDRect dRect;
   *     dRect.setBounds(dCurve, fCubic, tStart, tEnd);
   *     bounds->setLTRB(SkDoubleToScalar(dRect.fLeft), SkDoubleToScalar(dRect.fTop),
   *                     SkDoubleToScalar(dRect.fRight), SkDoubleToScalar(dRect.fBottom));
   * }
   * ```
   */
  public fun setQuadBounds(
    curve: Array<SkPoint>,
    param1: SkScalar,
    s: Double,
    e: Double,
    bounds: SkPathOpsBounds?,
  ) {
    TODO("Implement setQuadBounds")
  }
}
