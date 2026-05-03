package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkDCubic {
 *     static const int kPointCount = 4;
 *     static const int kPointLast = kPointCount - 1;
 *     static const int kMaxIntersections = 9;
 *
 *     enum SearchAxis {
 *         kXAxis,
 *         kYAxis
 *     };
 *
 *     bool collapsed() const {
 *         return fPts[0].approximatelyEqual(fPts[1]) && fPts[0].approximatelyEqual(fPts[2])
 *                 && fPts[0].approximatelyEqual(fPts[3]);
 *     }
 *
 *     bool controlsInside() const {
 *         SkDVector v01 = fPts[0] - fPts[1];
 *         SkDVector v02 = fPts[0] - fPts[2];
 *         SkDVector v03 = fPts[0] - fPts[3];
 *         SkDVector v13 = fPts[1] - fPts[3];
 *         SkDVector v23 = fPts[2] - fPts[3];
 *         return v03.dot(v01) > 0 && v03.dot(v02) > 0 && v03.dot(v13) > 0 && v03.dot(v23) > 0;
 *     }
 *
 *     static bool IsConic() { return false; }
 *
 *     const SkDPoint& operator[](int n) const { SkASSERT(n >= 0 && n < kPointCount); return fPts[n]; }
 *     SkDPoint& operator[](int n) { SkASSERT(n >= 0 && n < kPointCount); return fPts[n]; }
 *
 *     void align(int endIndex, int ctrlIndex, SkDPoint* dstPt) const;
 *     double binarySearch(double min, double max, double axisIntercept, SearchAxis xAxis) const;
 *     double calcPrecision() const;
 *     SkDCubicPair chopAt(double t) const;
 *     static void Coefficients(const double* cubic, double* A, double* B, double* C, double* D);
 *     static int ComplexBreak(const SkPoint pts[4], SkScalar* t);
 *     int convexHull(char order[kPointCount]) const;
 *
 *     void debugInit() {
 *         sk_bzero(fPts, sizeof(fPts));
 *     }
 *
 *     void debugSet(const SkDPoint* pts);
 *
 *     void dump() const;  // callable from the debugger when the implementation code is linked in
 *     void dumpID(int id) const;
 *     void dumpInner() const;
 *     SkDVector dxdyAtT(double t) const;
 *     bool endsAreExtremaInXOrY() const;
 *     static int FindExtrema(const double src[], double tValue[2]);
 *     int findInflections(double tValues[2]) const;
 *
 *     static int FindInflections(const SkPoint a[kPointCount], double tValues[2]) {
 *         SkDCubic cubic;
 *         return cubic.set(a).findInflections(tValues);
 *     }
 *
 *     int findMaxCurvature(double tValues[]) const;
 *
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const { return fDebugGlobalState; }
 * #endif
 *
 *     bool hullIntersects(const SkDCubic& c2, bool* isLinear) const;
 *     bool hullIntersects(const SkDConic& c, bool* isLinear) const;
 *     bool hullIntersects(const SkDQuad& c2, bool* isLinear) const;
 *     bool hullIntersects(const SkDPoint* pts, int ptCount, bool* isLinear) const;
 *     bool isLinear(int startIndex, int endIndex) const;
 *     static int maxIntersections() { return kMaxIntersections; }
 *     bool monotonicInX() const;
 *     bool monotonicInY() const;
 *     void otherPts(int index, const SkDPoint* o1Pts[kPointCount - 1]) const;
 *     static int pointCount() { return kPointCount; }
 *     static int pointLast() { return kPointLast; }
 *     SkDPoint ptAtT(double t) const;
 *     static int RootsReal(double A, double B, double C, double D, double t[3]);
 *     static int RootsValidT(const double A, const double B, const double C, double D, double s[3]);
 *
 *     int searchRoots(double extremes[6], int extrema, double axisIntercept,
 *                     SearchAxis xAxis, double* validRoots) const;
 *
 *     bool toFloatPoints(SkPoint* ) const;
 *     /**
 *      *  Return the number of valid roots (0 < root < 1) for this cubic intersecting the
 *      *  specified horizontal line.
 *      */
 *     int horizontalIntersect(double yIntercept, double roots[3]) const;
 *     /**
 *      *  Return the number of valid roots (0 < root < 1) for this cubic intersecting the
 *      *  specified vertical line.
 *      */
 *     int verticalIntersect(double xIntercept, double roots[3]) const;
 *
 * // add debug only global pointer so asserts can be skipped by fuzzers
 *     const SkDCubic& set(const SkPoint pts[kPointCount]
 *             SkDEBUGPARAMS(SkOpGlobalState* state = nullptr)) {
 *         fPts[0] = pts[0];
 *         fPts[1] = pts[1];
 *         fPts[2] = pts[2];
 *         fPts[3] = pts[3];
 *         SkDEBUGCODE(fDebugGlobalState = state);
 *         return *this;
 *     }
 *
 *     SkDCubic subDivide(double t1, double t2) const;
 *     void subDivide(double t1, double t2, SkDCubic* c) const { *c = this->subDivide(t1, t2); }
 *
 *     static SkDCubic SubDivide(const SkPoint a[kPointCount], double t1, double t2) {
 *         SkDCubic cubic;
 *         return cubic.set(a).subDivide(t1, t2);
 *     }
 *
 *     void subDivide(const SkDPoint& a, const SkDPoint& d, double t1, double t2, SkDPoint p[2]) const;
 *
 *     static void SubDivide(const SkPoint pts[kPointCount], const SkDPoint& a, const SkDPoint& d, double t1,
 *                           double t2, SkDPoint p[2]) {
 *         SkDCubic cubic;
 *         cubic.set(pts).subDivide(a, d, t1, t2, p);
 *     }
 *
 *     double top(const SkDCubic& dCurve, double startT, double endT, SkDPoint*topPt) const;
 *     SkDQuad toQuad() const;
 *
 *     static const int gPrecisionUnit;
 *     SkDPoint fPts[kPointCount];
 *     SkDEBUGCODE(SkOpGlobalState* fDebugGlobalState;)
 * }
 * ```
 */
public data class SkDCubic public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kPointCount = 4
   * ```
   */
  public var fPts: Array<SkDPoint>,
) {
  /**
   * C++ original:
   * ```cpp
   * bool collapsed() const {
   *         return fPts[0].approximatelyEqual(fPts[1]) && fPts[0].approximatelyEqual(fPts[2])
   *                 && fPts[0].approximatelyEqual(fPts[3]);
   *     }
   * ```
   */
  public fun collapsed(): Boolean {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool controlsInside() const {
   *         SkDVector v01 = fPts[0] - fPts[1];
   *         SkDVector v02 = fPts[0] - fPts[2];
   *         SkDVector v03 = fPts[0] - fPts[3];
   *         SkDVector v13 = fPts[1] - fPts[3];
   *         SkDVector v23 = fPts[2] - fPts[3];
   *         return v03.dot(v01) > 0 && v03.dot(v02) > 0 && v03.dot(v13) > 0 && v03.dot(v23) > 0;
   *     }
   * ```
   */
  public fun controlsInside(): Boolean {
    TODO("Implement controlsInside")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const { SkASSERT(n >= 0 && n < kPointCount); return fPts[n]; }
   * ```
   */
  public operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) { SkASSERT(n >= 0 && n < kPointCount); return fPts[n]; }
   * ```
   */
  public fun align(
    endIndex: Int,
    ctrlIndex: Int,
    dstPt: SkDPoint?,
  ) {
    TODO("Implement align")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::align(int endIndex, int ctrlIndex, SkDPoint* dstPt) const {
   *     if (fPts[endIndex].fX == fPts[ctrlIndex].fX) {
   *         dstPt->fX = fPts[endIndex].fX;
   *     }
   *     if (fPts[endIndex].fY == fPts[ctrlIndex].fY) {
   *         dstPt->fY = fPts[endIndex].fY;
   *     }
   * }
   * ```
   */
  public fun binarySearch(
    min: Double,
    max: Double,
    axisIntercept: Double,
    xAxis: SearchAxis,
  ): Double {
    TODO("Implement binarySearch")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkDCubic::binarySearch(double min, double max, double axisIntercept,
   *         SearchAxis xAxis) const {
   *     double t = (min + max) / 2;
   *     double step = (t - min) / 2;
   *     SkDPoint cubicAtT = ptAtT(t);
   *     double calcPos = (&cubicAtT.fX)[xAxis];
   *     double calcDist = calcPos - axisIntercept;
   *     do {
   *         double priorT = std::max(min, t - step);
   *         SkDPoint lessPt = ptAtT(priorT);
   *         if (approximately_equal_half(lessPt.fX, cubicAtT.fX)
   *                 && approximately_equal_half(lessPt.fY, cubicAtT.fY)) {
   *             return -1;  // binary search found no point at this axis intercept
   *         }
   *         double lessDist = (&lessPt.fX)[xAxis] - axisIntercept;
   * #if DEBUG_CUBIC_BINARY_SEARCH
   *         SkDebugf("t=%1.9g calc=%1.9g dist=%1.9g step=%1.9g less=%1.9g\n", t, calcPos, calcDist,
   *                 step, lessDist);
   * #endif
   *         double lastStep = step;
   *         step /= 2;
   *         if (calcDist > 0 ? calcDist > lessDist : calcDist < lessDist) {
   *             t = priorT;
   *         } else {
   *             double nextT = t + lastStep;
   *             if (nextT > max) {
   *                 return -1;
   *             }
   *             SkDPoint morePt = ptAtT(nextT);
   *             if (approximately_equal_half(morePt.fX, cubicAtT.fX)
   *                     && approximately_equal_half(morePt.fY, cubicAtT.fY)) {
   *                 return -1;  // binary search found no point at this axis intercept
   *             }
   *             double moreDist = (&morePt.fX)[xAxis] - axisIntercept;
   *             if (calcDist > 0 ? calcDist <= moreDist : calcDist >= moreDist) {
   *                 continue;
   *             }
   *             t = nextT;
   *         }
   *         SkDPoint testAtT = ptAtT(t);
   *         cubicAtT = testAtT;
   *         calcPos = (&cubicAtT.fX)[xAxis];
   *         calcDist = calcPos - axisIntercept;
   *     } while (!approximately_equal(calcPos, axisIntercept));
   *     return t;
   * }
   * ```
   */
  public fun calcPrecision(): Double {
    TODO("Implement calcPrecision")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkDCubic::calcPrecision() const {
   *     return ((fPts[1] - fPts[0]).length()
   *             + (fPts[2] - fPts[1]).length()
   *             + (fPts[3] - fPts[2]).length()) / gPrecisionUnit;
   * }
   * ```
   */
  public fun chopAt(t: Double): SkDCubicPair {
    TODO("Implement chopAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDCubicPair SkDCubic::chopAt(double t) const {
   *     SkDCubicPair dst;
   *     if (t == 0.5) {
   *         dst.pts[0] = fPts[0];
   *         dst.pts[1].fX = (fPts[0].fX + fPts[1].fX) / 2;
   *         dst.pts[1].fY = (fPts[0].fY + fPts[1].fY) / 2;
   *         dst.pts[2].fX = (fPts[0].fX + 2 * fPts[1].fX + fPts[2].fX) / 4;
   *         dst.pts[2].fY = (fPts[0].fY + 2 * fPts[1].fY + fPts[2].fY) / 4;
   *         dst.pts[3].fX = (fPts[0].fX + 3 * (fPts[1].fX + fPts[2].fX) + fPts[3].fX) / 8;
   *         dst.pts[3].fY = (fPts[0].fY + 3 * (fPts[1].fY + fPts[2].fY) + fPts[3].fY) / 8;
   *         dst.pts[4].fX = (fPts[1].fX + 2 * fPts[2].fX + fPts[3].fX) / 4;
   *         dst.pts[4].fY = (fPts[1].fY + 2 * fPts[2].fY + fPts[3].fY) / 4;
   *         dst.pts[5].fX = (fPts[2].fX + fPts[3].fX) / 2;
   *         dst.pts[5].fY = (fPts[2].fY + fPts[3].fY) / 2;
   *         dst.pts[6] = fPts[3];
   *         return dst;
   *     }
   *     interp_cubic_coords(&fPts[0].fX, &dst.pts[0].fX, t);
   *     interp_cubic_coords(&fPts[0].fY, &dst.pts[0].fY, t);
   *     return dst;
   * }
   * ```
   */
  public fun convexHull(order: CharArray): Int {
    TODO("Implement convexHull")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDCubic::convexHull(char order[4]) const {
   *     size_t index;
   *     // find top point
   *     size_t yMin = 0;
   *     for (index = 1; index < 4; ++index) {
   *         if (fPts[yMin].fY > fPts[index].fY || (fPts[yMin].fY == fPts[index].fY
   *                 && fPts[yMin].fX > fPts[index].fX)) {
   *             yMin = index;
   *         }
   *     }
   *     order[0] = yMin;
   *     int midX = -1;
   *     int backupYMin = -1;
   *     for (int pass = 0; pass < 2; ++pass) {
   *         for (index = 0; index < 4; ++index) {
   *             if (index == yMin) {
   *                 continue;
   *             }
   *             // rotate line from (yMin, index) to axis
   *             // see if remaining two points are both above or below
   *             // use this to find mid
   *             int mask = other_two(yMin, index);
   *             int side1 = yMin ^ mask;
   *             int side2 = index ^ mask;
   *             SkDCubic rotPath;
   *             if (!rotate(*this, yMin, index, rotPath)) { // ! if cbc[yMin]==cbc[idx]
   *                 order[1] = side1;
   *                 order[2] = side2;
   *                 return 3;
   *             }
   *             int sides = side(rotPath[side1].fY - rotPath[yMin].fY);
   *             sides ^= side(rotPath[side2].fY - rotPath[yMin].fY);
   *             if (sides == 2) { // '2' means one remaining point <0, one >0
   *                 if (midX >= 0) {
   *                     // one of the control points is equal to an end point
   *                     order[0] = 0;
   *                     order[1] = 3;
   *                     if (fPts[1] == fPts[0] || fPts[1] == fPts[3]) {
   *                         order[2] = 2;
   *                         return 3;
   *                     }
   *                     if (fPts[2] == fPts[0] || fPts[2] == fPts[3]) {
   *                         order[2] = 1;
   *                         return 3;
   *                     }
   *                     // one of the control points may be very nearly but not exactly equal --
   *                     double dist1_0 = fPts[1].distanceSquared(fPts[0]);
   *                     double dist1_3 = fPts[1].distanceSquared(fPts[3]);
   *                     double dist2_0 = fPts[2].distanceSquared(fPts[0]);
   *                     double dist2_3 = fPts[2].distanceSquared(fPts[3]);
   *                     double smallest1distSq = std::min(dist1_0, dist1_3);
   *                     double smallest2distSq = std::min(dist2_0, dist2_3);
   *                     if (approximately_zero(std::min(smallest1distSq, smallest2distSq))) {
   *                         order[2] = smallest1distSq < smallest2distSq ? 2 : 1;
   *                         return 3;
   *                     }
   *                 }
   *                 midX = index;
   *             } else if (sides == 0) { // '0' means both to one side or the other
   *                 backupYMin = index;
   *             }
   *         }
   *         if (midX >= 0) {
   *             break;
   *         }
   *         if (backupYMin < 0) {
   *             break;
   *         }
   *         yMin = backupYMin;
   *         backupYMin = -1;
   *     }
   *     if (midX < 0) {
   *         midX = yMin ^ 3; // choose any other point
   *     }
   *     int mask = other_two(yMin, midX);
   *     int least = yMin ^ mask;
   *     int most = midX ^ mask;
   *     order[0] = yMin;
   *     order[1] = least;
   *
   *     // see if mid value is on same side of line (least, most) as yMin
   *     SkDCubic midPath;
   *     if (!rotate(*this, least, most, midPath)) { // ! if cbc[least]==cbc[most]
   *         order[2] = midX;
   *         return 3;
   *     }
   *     int midSides = side(midPath[yMin].fY - midPath[least].fY);
   *     midSides ^= side(midPath[midX].fY - midPath[least].fY);
   *     if (midSides != 2) {  // if mid point is not between
   *         order[2] = most;
   *         return 3; // result is a triangle
   *     }
   *     order[2] = midX;
   *     order[3] = most;
   *     return 4; // result is a quadralateral
   * }
   * ```
   */
  public fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugInit() {
   *         sk_bzero(fPts, sizeof(fPts));
   *     }
   * ```
   */
  public fun debugSet(pts: SkDPoint?) {
    TODO("Implement debugSet")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::debugSet(const SkDPoint* pts) {
   *     memcpy(fPts, pts, sizeof(fPts));
   *     SkDEBUGCODE(fDebugGlobalState = nullptr);
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::dump() const {
   *     this->dumpInner();
   *     SkDebugf("}},\n");
   * }
   * ```
   */
  public fun dumpID(id: Int) {
    TODO("Implement dumpID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::dumpID(int id) const {
   *     this->dumpInner();
   *     SkDebugf("}");
   *     DumpID(id);
   * }
   * ```
   */
  public fun dumpInner() {
    TODO("Implement dumpInner")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::dumpInner() const {
   *     SkDebugf("{{");
   *     int index = 0;
   *     do {
   *         if (index != 0) {
   *             if (double_is_NaN(fPts[index].fX) && double_is_NaN(fPts[index].fY)) {
   *                 return;
   *             }
   *             SkDebugf(", ");
   *         }
   *         fPts[index].dump();
   *     } while (++index < 3);
   *     if (double_is_NaN(fPts[index].fX) && double_is_NaN(fPts[index].fY)) {
   *         return;
   *     }
   *     SkDebugf(", ");
   *     fPts[index].dump();
   * }
   * ```
   */
  public fun dxdyAtT(t: Double): SkDVector {
    TODO("Implement dxdyAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector SkDCubic::dxdyAtT(double t) const {
   *     SkDVector result = { derivative_at_t(&fPts[0].fX, t), derivative_at_t(&fPts[0].fY, t) };
   *     if (result.fX == 0 && result.fY == 0) {
   *         if (t == 0) {
   *             result = fPts[2] - fPts[0];
   *         } else if (t == 1) {
   *             result = fPts[3] - fPts[1];
   *         } else {
   *             // incomplete
   *             SkDebugf("!c");
   *         }
   *         if (result.fX == 0 && result.fY == 0 && zero_or_one(t)) {
   *             result = fPts[3] - fPts[0];
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  public fun endsAreExtremaInXOrY(): Boolean {
    TODO("Implement endsAreExtremaInXOrY")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::endsAreExtremaInXOrY() const {
   *     return (between(fPts[0].fX, fPts[1].fX, fPts[3].fX)
   *             && between(fPts[0].fX, fPts[2].fX, fPts[3].fX))
   *             || (between(fPts[0].fY, fPts[1].fY, fPts[3].fY)
   *             && between(fPts[0].fY, fPts[2].fY, fPts[3].fY));
   * }
   * ```
   */
  public fun findInflections(tValues: DoubleArray): Int {
    TODO("Implement findInflections")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDCubic::findInflections(double tValues[2]) const {
   *     double Ax = fPts[1].fX - fPts[0].fX;
   *     double Ay = fPts[1].fY - fPts[0].fY;
   *     double Bx = fPts[2].fX - 2 * fPts[1].fX + fPts[0].fX;
   *     double By = fPts[2].fY - 2 * fPts[1].fY + fPts[0].fY;
   *     double Cx = fPts[3].fX + 3 * (fPts[1].fX - fPts[2].fX) - fPts[0].fX;
   *     double Cy = fPts[3].fY + 3 * (fPts[1].fY - fPts[2].fY) - fPts[0].fY;
   *     return SkDQuad::RootsValidT(Bx * Cy - By * Cx, Ax * Cy - Ay * Cx, Ax * By - Ay * Bx, tValues);
   * }
   * ```
   */
  public fun findMaxCurvature(tValues: DoubleArray): Int {
    TODO("Implement findMaxCurvature")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDCubic::findMaxCurvature(double tValues[]) const {
   *     double coeffX[4], coeffY[4];
   *     int i;
   *     formulate_F1DotF2(&fPts[0].fX, coeffX);
   *     formulate_F1DotF2(&fPts[0].fY, coeffY);
   *     for (i = 0; i < 4; i++) {
   *         coeffX[i] = coeffX[i] + coeffY[i];
   *     }
   *     return RootsValidT(coeffX[0], coeffX[1], coeffX[2], coeffX[3], tValues);
   * }
   * ```
   */
  public fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const { return fDebugGlobalState; }
   * ```
   */
  public fun hullIntersects(c2: SkDCubic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::hullIntersects(const SkDCubic& c2, bool* isLinear) const {
   *     return hullIntersects(c2.fPts, SkDCubic::kPointCount, isLinear);
   * }
   * ```
   */
  public fun hullIntersects(c: SkDConic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::hullIntersects(const SkDConic& conic, bool* isLinear) const {
   *
   *     return hullIntersects(conic.fPts, isLinear);
   * }
   * ```
   */
  public fun hullIntersects(c2: SkDQuad, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::hullIntersects(const SkDQuad& quad, bool* isLinear) const {
   *     return hullIntersects(quad.fPts, SkDQuad::kPointCount, isLinear);
   * }
   * ```
   */
  public fun hullIntersects(
    pts: SkDPoint?,
    ptCount: Int,
    isLinear: Boolean?,
  ): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::hullIntersects(const SkDPoint* pts, int ptCount, bool* isLinear) const {
   *     bool linear = true;
   *     char hullOrder[4];
   *     int hullCount = convexHull(hullOrder);
   *     int end1 = hullOrder[0];
   *     int hullIndex = 0;
   *     const SkDPoint* endPt[2];
   *     endPt[0] = &fPts[end1];
   *     do {
   *         hullIndex = (hullIndex + 1) % hullCount;
   *         int end2 = hullOrder[hullIndex];
   *         endPt[1] = &fPts[end2];
   *         double origX = endPt[0]->fX;
   *         double origY = endPt[0]->fY;
   *         double adj = endPt[1]->fX - origX;
   *         double opp = endPt[1]->fY - origY;
   *         int oddManMask = other_two(end1, end2);
   *         int oddMan = end1 ^ oddManMask;
   *         double sign = (fPts[oddMan].fY - origY) * adj - (fPts[oddMan].fX - origX) * opp;
   *         int oddMan2 = end2 ^ oddManMask;
   *         double sign2 = (fPts[oddMan2].fY - origY) * adj - (fPts[oddMan2].fX - origX) * opp;
   *         if (sign * sign2 < 0) {
   *             continue;
   *         }
   *         if (approximately_zero(sign)) {
   *             sign = sign2;
   *             if (approximately_zero(sign)) {
   *                 continue;
   *             }
   *         }
   *         linear = false;
   *         bool foundOutlier = false;
   *         for (int n = 0; n < ptCount; ++n) {
   *             double test = (pts[n].fY - origY) * adj - (pts[n].fX - origX) * opp;
   *             if (test * sign > 0 && !precisely_zero(test)) {
   *                 foundOutlier = true;
   *                 break;
   *             }
   *         }
   *         if (!foundOutlier) {
   *             return false;
   *         }
   *         endPt[0] = endPt[1];
   *         end1 = end2;
   *     } while (hullIndex);
   *     *isLinear = linear;
   *     return true;
   * }
   * ```
   */
  public fun isLinear(startIndex: Int, endIndex: Int): Boolean {
    TODO("Implement isLinear")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::isLinear(int startIndex, int endIndex) const {
   *     if (fPts[0].approximatelyDEqual(fPts[3]))  {
   *         return ((const SkDQuad *) this)->isLinear(0, 2);
   *     }
   *     SkLineParameters lineParameters;
   *     lineParameters.cubicEndPoints(*this, startIndex, endIndex);
   *     // FIXME: maybe it's possible to avoid this and compare non-normalized
   *     lineParameters.normalize();
   *     double tiniest = std::min(std::min(std::min(std::min(std::min(std::min(std::min(fPts[0].fX, fPts[0].fY),
   *             fPts[1].fX), fPts[1].fY), fPts[2].fX), fPts[2].fY), fPts[3].fX), fPts[3].fY);
   *     double largest = std::max(std::max(std::max(std::max(std::max(std::max(std::max(fPts[0].fX, fPts[0].fY),
   *             fPts[1].fX), fPts[1].fY), fPts[2].fX), fPts[2].fY), fPts[3].fX), fPts[3].fY);
   *     largest = std::max(largest, -tiniest);
   *     double distance = lineParameters.controlPtDistance(*this, 1);
   *     if (!approximately_zero_when_compared_to(distance, largest)) {
   *         return false;
   *     }
   *     distance = lineParameters.controlPtDistance(*this, 2);
   *     return approximately_zero_when_compared_to(distance, largest);
   * }
   * ```
   */
  public fun monotonicInX(): Boolean {
    TODO("Implement monotonicInX")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::monotonicInX() const {
   *     return precisely_between(fPts[0].fX, fPts[1].fX, fPts[3].fX)
   *             && precisely_between(fPts[0].fX, fPts[2].fX, fPts[3].fX);
   * }
   * ```
   */
  public fun monotonicInY(): Boolean {
    TODO("Implement monotonicInY")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::monotonicInY() const {
   *     return precisely_between(fPts[0].fY, fPts[1].fY, fPts[3].fY)
   *             && precisely_between(fPts[0].fY, fPts[2].fY, fPts[3].fY);
   * }
   * ```
   */
  public fun otherPts(index: Int, o1Pts: Int) {
    TODO("Implement otherPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::otherPts(int index, const SkDPoint* o1Pts[kPointCount - 1]) const {
   *     int offset = (int) !SkToBool(index);
   *     o1Pts[0] = &fPts[offset];
   *     o1Pts[1] = &fPts[++offset];
   *     o1Pts[2] = &fPts[++offset];
   * }
   * ```
   */
  public fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint SkDCubic::ptAtT(double t) const {
   *     if (0 == t) {
   *         return fPts[0];
   *     }
   *     if (1 == t) {
   *         return fPts[3];
   *     }
   *     double one_t = 1 - t;
   *     double one_t2 = one_t * one_t;
   *     double a = one_t2 * one_t;
   *     double b = 3 * one_t2 * t;
   *     double t2 = t * t;
   *     double c = 3 * one_t * t2;
   *     double d = t2 * t;
   *     SkDPoint result = {a * fPts[0].fX + b * fPts[1].fX + c * fPts[2].fX + d * fPts[3].fX,
   *             a * fPts[0].fY + b * fPts[1].fY + c * fPts[2].fY + d * fPts[3].fY};
   *     return result;
   * }
   * ```
   */
  public fun searchRoots(
    extremes: DoubleArray,
    extrema: Int,
    axisIntercept: Double,
    xAxis: SearchAxis,
    validRoots: Double?,
  ): Int {
    TODO("Implement searchRoots")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDCubic::searchRoots(double extremeTs[6], int extrema, double axisIntercept,
   *         SearchAxis xAxis, double* validRoots) const {
   *     extrema += findInflections(&extremeTs[extrema]);
   *     extremeTs[extrema++] = 0;
   *     extremeTs[extrema] = 1;
   *     SkASSERT(extrema < 6);
   *     SkTQSort(extremeTs, extremeTs + extrema + 1);
   *     int validCount = 0;
   *     for (int index = 0; index < extrema; ) {
   *         double min = extremeTs[index];
   *         double max = extremeTs[++index];
   *         if (min == max) {
   *             continue;
   *         }
   *         double newT = binarySearch(min, max, axisIntercept, xAxis);
   *         if (newT >= 0) {
   *             if (validCount >= 3) {
   *                 return 0;
   *             }
   *             validRoots[validCount++] = newT;
   *         }
   *     }
   *     return validCount;
   * }
   * ```
   */
  public fun toFloatPoints(pts: SkPoint?): Boolean {
    TODO("Implement toFloatPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDCubic::toFloatPoints(SkPoint* pts) const {
   *     const double* dCubic = &fPts[0].fX;
   *     SkScalar* cubic = &pts[0].fX;
   *     for (int index = 0; index < kPointCount * 2; ++index) {
   *         cubic[index] = SkDoubleToScalar(dCubic[index]);
   *         if (SkScalarAbs(cubic[index]) < FLT_EPSILON_ORDERABLE_ERR) {
   *             cubic[index] = 0;
   *         }
   *     }
   *     return SkIsFinite(&pts->fX, kPointCount * 2);
   * }
   * ```
   */
  public fun horizontalIntersect(yIntercept: Double, roots: DoubleArray): Int {
    TODO("Implement horizontalIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDCubic::horizontalIntersect(double yIntercept, double roots[3]) const {
   *     return LineCubicIntersections::HorizontalIntersect(*this, yIntercept, roots);
   * }
   * ```
   */
  public fun verticalIntersect(xIntercept: Double, roots: DoubleArray): Int {
    TODO("Implement verticalIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDCubic::verticalIntersect(double xIntercept, double roots[3]) const {
   *     return LineCubicIntersections::VerticalIntersect(*this, xIntercept, roots);
   * }
   * ```
   */
  public fun `set`(param0: SkPoint?, param1: SkOpGlobalState?): SkDCubic {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDCubic& set(const SkPoint pts[kPointCount]
   *             SkDEBUGPARAMS(SkOpGlobalState* state = nullptr)) {
   *         fPts[0] = pts[0];
   *         fPts[1] = pts[1];
   *         fPts[2] = pts[2];
   *         fPts[3] = pts[3];
   *         SkDEBUGCODE(fDebugGlobalState = state);
   *         return *this;
   *     }
   * ```
   */
  public fun subDivide(t1: Double, t2: Double): SkDCubic {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDCubic SkDCubic::subDivide(double t1, double t2) const {
   *     if (t1 == 0 || t2 == 1) {
   *         if (t1 == 0 && t2 == 1) {
   *             return *this;
   *         }
   *         SkDCubicPair pair = chopAt(t1 == 0 ? t2 : t1);
   *         SkDCubic dst = t1 == 0 ? pair.first() : pair.second();
   *         return dst;
   *     }
   *     SkDCubic dst;
   *     double ax = dst[0].fX = interp_cubic_coords(&fPts[0].fX, t1);
   *     double ay = dst[0].fY = interp_cubic_coords(&fPts[0].fY, t1);
   *     double ex = interp_cubic_coords(&fPts[0].fX, (t1*2+t2)/3);
   *     double ey = interp_cubic_coords(&fPts[0].fY, (t1*2+t2)/3);
   *     double fx = interp_cubic_coords(&fPts[0].fX, (t1+t2*2)/3);
   *     double fy = interp_cubic_coords(&fPts[0].fY, (t1+t2*2)/3);
   *     double dx = dst[3].fX = interp_cubic_coords(&fPts[0].fX, t2);
   *     double dy = dst[3].fY = interp_cubic_coords(&fPts[0].fY, t2);
   *     double mx = ex * 27 - ax * 8 - dx;
   *     double my = ey * 27 - ay * 8 - dy;
   *     double nx = fx * 27 - ax - dx * 8;
   *     double ny = fy * 27 - ay - dy * 8;
   *     /* bx = */ dst[1].fX = (mx * 2 - nx) / 18;
   *     /* by = */ dst[1].fY = (my * 2 - ny) / 18;
   *     /* cx = */ dst[2].fX = (nx * 2 - mx) / 18;
   *     /* cy = */ dst[2].fY = (ny * 2 - my) / 18;
   *     // FIXME: call align() ?
   *     return dst;
   * }
   * ```
   */
  public fun subDivide(
    t1: Double,
    t2: Double,
    c: SkDCubic?,
  ) {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * void subDivide(double t1, double t2, SkDCubic* c) const { *c = this->subDivide(t1, t2); }
   * ```
   */
  public fun subDivide(
    a: SkDPoint,
    d: SkDPoint,
    t1: Double,
    t2: Double,
    p: Array<SkDPoint>,
  ) {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDCubic::subDivide(const SkDPoint& a, const SkDPoint& d,
   *                          double t1, double t2, SkDPoint dst[2]) const {
   *     SkASSERT(t1 != t2);
   *     // this approach assumes that the control points computed directly are accurate enough
   *     SkDCubic sub = subDivide(t1, t2);
   *     dst[0] = sub[1] + (a - sub[0]);
   *     dst[1] = sub[2] + (d - sub[3]);
   *     if (t1 == 0 || t2 == 0) {
   *         align(0, 1, t1 == 0 ? &dst[0] : &dst[1]);
   *     }
   *     if (t1 == 1 || t2 == 1) {
   *         align(3, 2, t1 == 1 ? &dst[0] : &dst[1]);
   *     }
   *     if (AlmostBequalUlps(dst[0].fX, a.fX)) {
   *         dst[0].fX = a.fX;
   *     }
   *     if (AlmostBequalUlps(dst[0].fY, a.fY)) {
   *         dst[0].fY = a.fY;
   *     }
   *     if (AlmostBequalUlps(dst[1].fX, d.fX)) {
   *         dst[1].fX = d.fX;
   *     }
   *     if (AlmostBequalUlps(dst[1].fY, d.fY)) {
   *         dst[1].fY = d.fY;
   *     }
   * }
   * ```
   */
  public fun top(
    dCurve: SkDCubic,
    startT: Double,
    endT: Double,
    param3: Int,
  ): Double {
    TODO("Implement top")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkDCubic::top(const SkDCubic& dCurve, double startT, double endT, SkDPoint*topPt) const {
   *     double extremeTs[2];
   *     double topT = -1;
   *     int roots = SkDCubic::FindExtrema(&fPts[0].fY, extremeTs);
   *     for (int index = 0; index < roots; ++index) {
   *         double t = startT + (endT - startT) * extremeTs[index];
   *         SkDPoint mid = dCurve.ptAtT(t);
   *         if (topPt->fY > mid.fY || (topPt->fY == mid.fY && topPt->fX > mid.fX)) {
   *             topT = t;
   *             *topPt = mid;
   *         }
   *     }
   *     return topT;
   * }
   * ```
   */
  public fun toQuad(): SkDQuad {
    TODO("Implement toQuad")
  }

  public enum class SearchAxis {
    kXAxis,
    kYAxis,
  }

  public companion object {
    public val kPointCount: Int = TODO("Initialize kPointCount")

    public val kPointLast: Int = TODO("Initialize kPointLast")

    public val kMaxIntersections: Int = TODO("Initialize kMaxIntersections")

    public val gPrecisionUnit: Int = TODO("Initialize gPrecisionUnit")

    /**
     * C++ original:
     * ```cpp
     * static bool IsConic() { return false; }
     * ```
     */
    public fun isConic(): Boolean {
      TODO("Implement isConic")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkDCubic::Coefficients(const double* src, double* A, double* B, double* C, double* D) {
     *     *A = src[6];  // d
     *     *B = src[4] * 3;  // 3*c
     *     *C = src[2] * 3;  // 3*b
     *     *D = src[0];  // a
     *     *A -= *D - *C + *B;     // A =   -a + 3*b - 3*c + d
     *     *B += 3 * *D - 2 * *C;  // B =  3*a - 6*b + 3*c
     *     *C -= 3 * *D;           // C = -3*a + 3*b
     * }
     * ```
     */
    public fun coefficients(
      cubic: Double?,
      a: Double?,
      b: Double?,
      c: Double?,
      d: Double?,
    ) {
      TODO("Implement coefficients")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkDCubic::ComplexBreak(const SkPoint pointsPtr[4], SkScalar* t) {
     *     SkDCubic cubic;
     *     cubic.set(pointsPtr);
     *     if (cubic.monotonicInX() && cubic.monotonicInY()) {
     *         return 0;
     *     }
     *     double tt[2], ss[2];
     *     SkCubicType cubicType = SkClassifyCubic(pointsPtr, tt, ss);
     *     switch (cubicType) {
     *         case SkCubicType::kLoop: {
     *             const double &td = tt[0], &te = tt[1], &sd = ss[0], &se = ss[1];
     *             if (roughly_between(0, td, sd) && roughly_between(0, te, se)) {
     *                 t[0] = static_cast<SkScalar>((td * se + te * sd) / (2 * sd * se));
     *                 return (int) (t[0] > 0 && t[0] < 1);
     *             }
     *         }
     *         [[fallthrough]]; // fall through if no t value found
     *         case SkCubicType::kSerpentine:
     *         case SkCubicType::kLocalCusp:
     *         case SkCubicType::kCuspAtInfinity: {
     *             double inflectionTs[2];
     *             int infTCount = cubic.findInflections(inflectionTs);
     *             double maxCurvature[3];
     *             int roots = cubic.findMaxCurvature(maxCurvature);
     *     #if DEBUG_CUBIC_SPLIT
     *             SkDebugf("%s\n", __FUNCTION__);
     *             cubic.dump();
     *             for (int index = 0; index < infTCount; ++index) {
     *                 SkDebugf("inflectionsTs[%d]=%1.9g ", index, inflectionTs[index]);
     *                 SkDPoint pt = cubic.ptAtT(inflectionTs[index]);
     *                 SkDVector dPt = cubic.dxdyAtT(inflectionTs[index]);
     *                 SkDLine perp = {{pt - dPt, pt + dPt}};
     *                 perp.dump();
     *             }
     *             for (int index = 0; index < roots; ++index) {
     *                 SkDebugf("maxCurvature[%d]=%1.9g ", index, maxCurvature[index]);
     *                 SkDPoint pt = cubic.ptAtT(maxCurvature[index]);
     *                 SkDVector dPt = cubic.dxdyAtT(maxCurvature[index]);
     *                 SkDLine perp = {{pt - dPt, pt + dPt}};
     *                 perp.dump();
     *             }
     *     #endif
     *             if (infTCount == 2) {
     *                 for (int index = 0; index < roots; ++index) {
     *                     if (between(inflectionTs[0], maxCurvature[index], inflectionTs[1])) {
     *                         t[0] = maxCurvature[index];
     *                         return (int) (t[0] > 0 && t[0] < 1);
     *                     }
     *                 }
     *             } else {
     *                 int resultCount = 0;
     *                 // FIXME: constant found through experimentation -- maybe there's a better way....
     *                 double precision = cubic.calcPrecision() * 2;
     *                 for (int index = 0; index < roots; ++index) {
     *                     double testT = maxCurvature[index];
     *                     if (0 >= testT || testT >= 1) {
     *                         continue;
     *                     }
     *                     // don't call dxdyAtT since we want (0,0) results
     *                     SkDVector dPt = { derivative_at_t(&cubic.fPts[0].fX, testT),
     *                             derivative_at_t(&cubic.fPts[0].fY, testT) };
     *                     double dPtLen = dPt.length();
     *                     if (dPtLen < precision) {
     *                         t[resultCount++] = testT;
     *                     }
     *                 }
     *                 if (!resultCount && infTCount == 1) {
     *                     t[0] = inflectionTs[0];
     *                     resultCount = (int) (t[0] > 0 && t[0] < 1);
     *                 }
     *                 return resultCount;
     *             }
     *             break;
     *         }
     *         default:
     *             break;
     *     }
     *     return 0;
     * }
     * ```
     */
    public fun complexBreak(pts: Array<SkPoint>, t: SkScalar?): Int {
      TODO("Implement complexBreak")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkDCubic::FindExtrema(const double src[], double tValues[2]) {
     *     // we divide A,B,C by 3 to simplify
     *     double a = src[0];
     *     double b = src[2];
     *     double c = src[4];
     *     double d = src[6];
     *     double A = d - a + 3 * (b - c);
     *     double B = 2 * (a - b - b + c);
     *     double C = b - a;
     *
     *     return SkDQuad::RootsValidT(A, B, C, tValues);
     * }
     * ```
     */
    public fun findExtrema(src: DoubleArray, tValue: DoubleArray): Int {
      TODO("Implement findExtrema")
    }

    /**
     * C++ original:
     * ```cpp
     * static int FindInflections(const SkPoint a[kPointCount], double tValues[2]) {
     *         SkDCubic cubic;
     *         return cubic.set(a).findInflections(tValues);
     *     }
     * ```
     */
    public fun findInflections(a: Array<SkPoint>, tValues: DoubleArray): Int {
      TODO("Implement findInflections")
    }

    /**
     * C++ original:
     * ```cpp
     * static int maxIntersections() { return kMaxIntersections; }
     * ```
     */
    public fun maxIntersections(): Int {
      TODO("Implement maxIntersections")
    }

    /**
     * C++ original:
     * ```cpp
     * static int pointCount() { return kPointCount; }
     * ```
     */
    public fun pointCount(): Int {
      TODO("Implement pointCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static int pointLast() { return kPointLast; }
     * ```
     */
    public fun pointLast(): Int {
      TODO("Implement pointLast")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkDCubic::RootsReal(double A, double B, double C, double D, double s[3]) {
     * #ifdef SK_DEBUG
     *     #if ONE_OFF_DEBUG && ONE_OFF_DEBUG_MATHEMATICA
     *     // create a string mathematica understands
     *     // GDB set print repe 15 # if repeated digits is a bother
     *     //     set print elements 400 # if line doesn't fit
     *     char str[1024];
     *     sk_bzero(str, sizeof(str));
     *     snprintf(str, sizeof(str), "Solve[%1.19g x^3 + %1.19g x^2 + %1.19g x + %1.19g == 0, x]",
     *             A, B, C, D);
     *     SkPathOpsDebug::MathematicaIze(str, sizeof(str));
     *     SkDebugf("%s\n", str);
     *     #endif
     * #endif
     *     if (approximately_zero(A)
     *             && approximately_zero_when_compared_to(A, B)
     *             && approximately_zero_when_compared_to(A, C)
     *             && approximately_zero_when_compared_to(A, D)) {  // we're just a quadratic
     *         return SkDQuad::RootsReal(B, C, D, s);
     *     }
     *     if (approximately_zero_when_compared_to(D, A)
     *             && approximately_zero_when_compared_to(D, B)
     *             && approximately_zero_when_compared_to(D, C)) {  // 0 is one root
     *         int num = SkDQuad::RootsReal(A, B, C, s);
     *         for (int i = 0; i < num; ++i) {
     *             if (approximately_zero(s[i])) {
     *                 return num;
     *             }
     *         }
     *         s[num++] = 0;
     *         return num;
     *     }
     *     if (approximately_zero(A + B + C + D)) {  // 1 is one root
     *         int num = SkDQuad::RootsReal(A, A + B, -D, s);
     *         for (int i = 0; i < num; ++i) {
     *             if (AlmostDequalUlps(s[i], 1)) {
     *                 return num;
     *             }
     *         }
     *         s[num++] = 1;
     *         return num;
     *     }
     *     double a, b, c;
     *     {
     *         double invA = 1 / A;
     *         a = B * invA;
     *         b = C * invA;
     *         c = D * invA;
     *     }
     *     double a2 = a * a;
     *     double Q = (a2 - b * 3) / 9;
     *     double R = (2 * a2 * a - 9 * a * b + 27 * c) / 54;
     *     double R2 = R * R;
     *     double Q3 = Q * Q * Q;
     *     double R2MinusQ3 = R2 - Q3;
     *     double adiv3 = a / 3;
     *     double r;
     *     double* roots = s;
     *     if (R2MinusQ3 < 0) {   // we have 3 real roots
     *         // the divide/root can, due to finite precisions, be slightly outside of -1...1
     *         double theta = acos(SkTPin(R / sqrt(Q3), -1., 1.));
     *         double neg2RootQ = -2 * sqrt(Q);
     *
     *         r = neg2RootQ * cos(theta / 3) - adiv3;
     *         *roots++ = r;
     *
     *         r = neg2RootQ * cos((theta + 2 * SK_DoublePI) / 3) - adiv3;
     *         if (!AlmostDequalUlps(s[0], r)) {
     *             *roots++ = r;
     *         }
     *         r = neg2RootQ * cos((theta - 2 * SK_DoublePI) / 3) - adiv3;
     *         if (!AlmostDequalUlps(s[0], r) && (roots - s == 1 || !AlmostDequalUlps(s[1], r))) {
     *             *roots++ = r;
     *         }
     *     } else {  // we have 1 real root
     *         double sqrtR2MinusQ3 = sqrt(R2MinusQ3);
     *         A = fabs(R) + sqrtR2MinusQ3;
     *         A = std::cbrt(A); // cube root
     *         if (R > 0) {
     *             A = -A;
     *         }
     *         if (A != 0) {
     *             A += Q / A;
     *         }
     *         r = A - adiv3;
     *         *roots++ = r;
     *         if (AlmostDequalUlps((double) R2, (double) Q3)) {
     *             r = -A / 2 - adiv3;
     *             if (!AlmostDequalUlps(s[0], r)) {
     *                 *roots++ = r;
     *             }
     *         }
     *     }
     *     return static_cast<int>(roots - s);
     * }
     * ```
     */
    public fun rootsReal(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      t: DoubleArray,
    ): Int {
      TODO("Implement rootsReal")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkDCubic::RootsValidT(double A, double B, double C, double D, double t[3]) {
     *     double s[3];
     *     int realRoots = RootsReal(A, B, C, D, s);
     *     int foundRoots = SkDQuad::AddValidTs(s, realRoots, t);
     *     for (int index = 0; index < realRoots; ++index) {
     *         double tValue = s[index];
     *         if (!approximately_one_or_less(tValue) && between(1, tValue, 1.00005)) {
     *             for (int idx2 = 0; idx2 < foundRoots; ++idx2) {
     *                 if (approximately_equal(t[idx2], 1)) {
     *                     goto nextRoot;
     *                 }
     *             }
     *             SkASSERT(foundRoots < 3);
     *             t[foundRoots++] = 1;
     *         } else if (!approximately_zero_or_more(tValue) && between(-0.00005, tValue, 0)) {
     *             for (int idx2 = 0; idx2 < foundRoots; ++idx2) {
     *                 if (approximately_equal(t[idx2], 0)) {
     *                     goto nextRoot;
     *                 }
     *             }
     *             SkASSERT(foundRoots < 3);
     *             t[foundRoots++] = 0;
     *         }
     * nextRoot:
     *         ;
     *     }
     *     return foundRoots;
     * }
     * ```
     */
    public fun rootsValidT(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      s: DoubleArray,
    ): Int {
      TODO("Implement rootsValidT")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkDCubic SubDivide(const SkPoint a[kPointCount], double t1, double t2) {
     *         SkDCubic cubic;
     *         return cubic.set(a).subDivide(t1, t2);
     *     }
     * ```
     */
    public fun subDivide(
      a: Array<SkPoint>,
      t1: Double,
      t2: Double,
    ): SkDCubic {
      TODO("Implement subDivide")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SubDivide(const SkPoint pts[kPointCount], const SkDPoint& a, const SkDPoint& d, double t1,
     *                           double t2, SkDPoint p[2]) {
     *         SkDCubic cubic;
     *         cubic.set(pts).subDivide(a, d, t1, t2, p);
     *     }
     * ```
     */
    public fun subDivide(
      pts: Array<SkPoint>,
      a: SkDPoint,
      d: SkDPoint,
      t1: Double,
      t2: Double,
      p: Array<SkDPoint>,
    ) {
      TODO("Implement subDivide")
    }
  }
}
