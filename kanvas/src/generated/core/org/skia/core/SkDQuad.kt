package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkDQuad {
 *     static const int kPointCount = 3;
 *     static const int kPointLast = kPointCount - 1;
 *     static const int kMaxIntersections = 4;
 *
 *     SkDPoint fPts[kPointCount];
 *
 *     bool collapsed() const {
 *         return fPts[0].approximatelyEqual(fPts[1]) && fPts[0].approximatelyEqual(fPts[2]);
 *     }
 *
 *     bool controlsInside() const {
 *         SkDVector v01 = fPts[0] - fPts[1];
 *         SkDVector v02 = fPts[0] - fPts[2];
 *         SkDVector v12 = fPts[1] - fPts[2];
 *         return v02.dot(v01) > 0 && v02.dot(v12) > 0;
 *     }
 *
 *     void debugInit() {
 *         sk_bzero(fPts, sizeof(fPts));
 *     }
 *
 *     void debugSet(const SkDPoint* pts);
 *
 *     SkDQuad flip() const {
 *         SkDQuad result = {{fPts[2], fPts[1], fPts[0]}  SkDEBUGPARAMS(fDebugGlobalState) };
 *         return result;
 *     }
 *
 *     static bool IsConic() { return false; }
 *
 *     const SkDQuad& set(const SkPoint pts[kPointCount]
 *             SkDEBUGPARAMS(SkOpGlobalState* state = nullptr)) {
 *         fPts[0] = pts[0];
 *         fPts[1] = pts[1];
 *         fPts[2] = pts[2];
 *         SkDEBUGCODE(fDebugGlobalState = state);
 *         return *this;
 *     }
 *
 *     const SkDPoint& operator[](int n) const { SkASSERT(n >= 0 && n < kPointCount); return fPts[n]; }
 *     SkDPoint& operator[](int n) { SkASSERT(n >= 0 && n < kPointCount); return fPts[n]; }
 *
 *     static int AddValidTs(double s[], int realRoots, double* t);
 *     void align(int endIndex, SkDPoint* dstPt) const;
 *     SkDQuadPair chopAt(double t) const;
 *     SkDVector dxdyAtT(double t) const;
 *     static int FindExtrema(const double src[], double tValue[1]);
 *
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const { return fDebugGlobalState; }
 * #endif
 *
 *     /**
 *      *  Return the number of valid roots (0 < root < 1) for this cubic intersecting the
 *      *  specified horizontal line.
 *      */
 *     int horizontalIntersect(double yIntercept, double roots[2]) const;
 *
 *     bool hullIntersects(const SkDQuad& , bool* isLinear) const;
 *     bool hullIntersects(const SkDConic& , bool* isLinear) const;
 *     bool hullIntersects(const SkDCubic& , bool* isLinear) const;
 *     bool isLinear(int startIndex, int endIndex) const;
 *     static int maxIntersections() { return kMaxIntersections; }
 *     bool monotonicInX() const;
 *     bool monotonicInY() const;
 *     void otherPts(int oddMan, const SkDPoint* endPt[2]) const;
 *     static int pointCount() { return kPointCount; }
 *     static int pointLast() { return kPointLast; }
 *     SkDPoint ptAtT(double t) const;
 *     static int RootsReal(double A, double B, double C, double t[2]);
 *     static int RootsValidT(const double A, const double B, const double C, double s[2]);
 *     static void SetABC(const double* quad, double* a, double* b, double* c);
 *     SkDQuad subDivide(double t1, double t2) const;
 *     void subDivide(double t1, double t2, SkDQuad* quad) const { *quad = this->subDivide(t1, t2); }
 *
 *     static SkDQuad SubDivide(const SkPoint a[kPointCount], double t1, double t2) {
 *         SkDQuad quad;
 *         quad.set(a);
 *         return quad.subDivide(t1, t2);
 *     }
 *     SkDPoint subDivide(const SkDPoint& a, const SkDPoint& c, double t1, double t2) const;
 *     static SkDPoint SubDivide(const SkPoint pts[kPointCount], const SkDPoint& a, const SkDPoint& c,
 *                               double t1, double t2) {
 *         SkDQuad quad;
 *         quad.set(pts);
 *         return quad.subDivide(a, c, t1, t2);
 *     }
 *
 *     /**
 *      *  Return the number of valid roots (0 < root < 1) for this cubic intersecting the
 *      *  specified vertical line.
 *      */
 *     int verticalIntersect(double xIntercept, double roots[2]) const;
 *
 *     SkDCubic debugToCubic() const;
 *     // utilities callable by the user from the debugger when the implementation code is linked in
 *     void dump() const;
 *     void dumpID(int id) const;
 *     void dumpInner() const;
 *
 *     SkDEBUGCODE(SkOpGlobalState* fDebugGlobalState;)
 * }
 * ```
 */
public data class SkDQuad public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kPointCount = 3
   * ```
   */
  public var fPts: Array<SkDPoint>,
) {
  /**
   * C++ original:
   * ```cpp
   * bool collapsed() const {
   *         return fPts[0].approximatelyEqual(fPts[1]) && fPts[0].approximatelyEqual(fPts[2]);
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
   *         SkDVector v12 = fPts[1] - fPts[2];
   *         return v02.dot(v01) > 0 && v02.dot(v12) > 0;
   *     }
   * ```
   */
  public fun controlsInside(): Boolean {
    TODO("Implement controlsInside")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugInit() {
   *         sk_bzero(fPts, sizeof(fPts));
   *     }
   * ```
   */
  public fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDQuad::debugSet(const SkDPoint* pts) {
   *     memcpy(fPts, pts, sizeof(fPts));
   *     SkDEBUGCODE(fDebugGlobalState = nullptr);
   * }
   * ```
   */
  public fun debugSet(pts: SkDPoint?) {
    TODO("Implement debugSet")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDQuad flip() const {
   *         SkDQuad result = {{fPts[2], fPts[1], fPts[0]}  SkDEBUGPARAMS(fDebugGlobalState) };
   *         return result;
   *     }
   * ```
   */
  public fun flip(): SkDQuad {
    TODO("Implement flip")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDQuad& set(const SkPoint pts[kPointCount]
   *             SkDEBUGPARAMS(SkOpGlobalState* state = nullptr)) {
   *         fPts[0] = pts[0];
   *         fPts[1] = pts[1];
   *         fPts[2] = pts[2];
   *         SkDEBUGCODE(fDebugGlobalState = state);
   *         return *this;
   *     }
   * ```
   */
  public fun `set`(param0: SkPoint?, param1: SkOpGlobalState?): SkDQuad {
    TODO("Implement set")
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
  public fun align(endIndex: Int, dstPt: SkDPoint?) {
    TODO("Implement align")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDQuad::align(int endIndex, SkDPoint* dstPt) const {
   *     if (fPts[endIndex].fX == fPts[1].fX) {
   *         dstPt->fX = fPts[endIndex].fX;
   *     }
   *     if (fPts[endIndex].fY == fPts[1].fY) {
   *         dstPt->fY = fPts[endIndex].fY;
   *     }
   * }
   * ```
   */
  public fun chopAt(t: Double): SkDQuadPair {
    TODO("Implement chopAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDQuadPair SkDQuad::chopAt(double t) const
   * {
   *     SkDQuadPair dst;
   *     interp_quad_coords(&fPts[0].fX, &dst.pts[0].fX, t);
   *     interp_quad_coords(&fPts[0].fY, &dst.pts[0].fY, t);
   *     return dst;
   * }
   * ```
   */
  public fun dxdyAtT(t: Double): SkDVector {
    TODO("Implement dxdyAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector SkDQuad::dxdyAtT(double t) const {
   *     double a = t - 1;
   *     double b = 1 - 2 * t;
   *     double c = t;
   *     SkDVector result = { a * fPts[0].fX + b * fPts[1].fX + c * fPts[2].fX,
   *             a * fPts[0].fY + b * fPts[1].fY + c * fPts[2].fY };
   *     if (result.fX == 0 && result.fY == 0) {
   *         if (zero_or_one(t)) {
   *             result = fPts[2] - fPts[0];
   *         } else {
   *             // incomplete
   *             SkDebugf("!q");
   *         }
   *     }
   *     return result;
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
  public fun horizontalIntersect(yIntercept: Double, roots: DoubleArray): Int {
    TODO("Implement horizontalIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDQuad::horizontalIntersect(double yIntercept, double roots[2]) const {
   *     return SkIntersections::HorizontalIntercept(*this, yIntercept, roots);
   * }
   * ```
   */
  public fun hullIntersects(q2: SkDQuad, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDQuad::hullIntersects(const SkDQuad& q2, bool* isLinear) const {
   *     bool linear = true;
   *     for (int oddMan = 0; oddMan < kPointCount; ++oddMan) {
   *         const SkDPoint* endPt[2];
   *         this->otherPts(oddMan, endPt);
   *         double origX = endPt[0]->fX;
   *         double origY = endPt[0]->fY;
   *         double adj = endPt[1]->fX - origX;
   *         double opp = endPt[1]->fY - origY;
   *         double sign = (fPts[oddMan].fY - origY) * adj - (fPts[oddMan].fX - origX) * opp;
   *         if (approximately_zero(sign)) {
   *             continue;
   *         }
   *         linear = false;
   *         bool foundOutlier = false;
   *         for (int n = 0; n < kPointCount; ++n) {
   *             double test = (q2[n].fY - origY) * adj - (q2[n].fX - origX) * opp;
   *             if (test * sign > 0 && !precisely_zero(test)) {
   *                 foundOutlier = true;
   *                 break;
   *             }
   *         }
   *         if (!foundOutlier) {
   *             return false;
   *         }
   *     }
   *     if (linear && !matchesEnd(fPts, q2.fPts[0]) && !matchesEnd(fPts, q2.fPts[2])) {
   *         // if the end point of the opposite quad is inside the hull that is nearly a line,
   *         // then representing the quad as a line may cause the intersection to be missed.
   *         // Check to see if the endpoint is in the triangle.
   *         if (pointInTriangle(fPts, q2.fPts[0]) || pointInTriangle(fPts, q2.fPts[2])) {
   *             linear = false;
   *         }
   *     }
   *     *isLinear = linear;
   *     return true;
   * }
   * ```
   */
  public fun hullIntersects(conic: SkDConic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDQuad::hullIntersects(const SkDConic& conic, bool* isLinear) const {
   *     return conic.hullIntersects(*this, isLinear);
   * }
   * ```
   */
  public fun hullIntersects(cubic: SkDCubic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDQuad::hullIntersects(const SkDCubic& cubic, bool* isLinear) const {
   *     return cubic.hullIntersects(*this, isLinear);
   * }
   * ```
   */
  public fun isLinear(startIndex: Int, endIndex: Int): Boolean {
    TODO("Implement isLinear")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDQuad::isLinear(int startIndex, int endIndex) const {
   *     SkLineParameters lineParameters;
   *     lineParameters.quadEndPoints(*this, startIndex, endIndex);
   *     // FIXME: maybe it's possible to avoid this and compare non-normalized
   *     lineParameters.normalize();
   *     double distance = lineParameters.controlPtDistance(*this);
   *     double tiniest = std::min(std::min(std::min(std::min(std::min(fPts[0].fX, fPts[0].fY),
   *             fPts[1].fX), fPts[1].fY), fPts[2].fX), fPts[2].fY);
   *     double largest = std::max(std::max(std::max(std::max(std::max(fPts[0].fX, fPts[0].fY),
   *             fPts[1].fX), fPts[1].fY), fPts[2].fX), fPts[2].fY);
   *     largest = std::max(largest, -tiniest);
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
   * bool SkDQuad::monotonicInX() const {
   *     return between(fPts[0].fX, fPts[1].fX, fPts[2].fX);
   * }
   * ```
   */
  public fun monotonicInY(): Boolean {
    TODO("Implement monotonicInY")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDQuad::monotonicInY() const {
   *     return between(fPts[0].fY, fPts[1].fY, fPts[2].fY);
   * }
   * ```
   */
  public fun otherPts(oddMan: Int, endPt: Int) {
    TODO("Implement otherPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDQuad::otherPts(int oddMan, const SkDPoint* endPt[2]) const {
   *     for (int opp = 1; opp < kPointCount; ++opp) {
   *         int end = (oddMan ^ opp) - oddMan;  // choose a value not equal to oddMan
   *         end &= ~(end >> 2);  // if the value went negative, set it to zero
   *         endPt[opp - 1] = &fPts[end];
   *     }
   * }
   * ```
   */
  public fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint SkDQuad::ptAtT(double t) const {
   *     if (0 == t) {
   *         return fPts[0];
   *     }
   *     if (1 == t) {
   *         return fPts[2];
   *     }
   *     double one_t = 1 - t;
   *     double a = one_t * one_t;
   *     double b = 2 * one_t * t;
   *     double c = t * t;
   *     SkDPoint result = { a * fPts[0].fX + b * fPts[1].fX + c * fPts[2].fX,
   *             a * fPts[0].fY + b * fPts[1].fY + c * fPts[2].fY };
   *     return result;
   * }
   * ```
   */
  public fun subDivide(t1: Double, t2: Double): SkDQuad {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDQuad SkDQuad::subDivide(double t1, double t2) const {
   *     if (0 == t1 && 1 == t2) {
   *         return *this;
   *     }
   *     SkDQuad dst;
   *     double ax = dst[0].fX = interp_quad_coords(&fPts[0].fX, t1);
   *     double ay = dst[0].fY = interp_quad_coords(&fPts[0].fY, t1);
   *     double dx = interp_quad_coords(&fPts[0].fX, (t1 + t2) / 2);
   *     double dy = interp_quad_coords(&fPts[0].fY, (t1 + t2) / 2);
   *     double cx = dst[2].fX = interp_quad_coords(&fPts[0].fX, t2);
   *     double cy = dst[2].fY = interp_quad_coords(&fPts[0].fY, t2);
   *     /* bx = */ dst[1].fX = 2 * dx - (ax + cx) / 2;
   *     /* by = */ dst[1].fY = 2 * dy - (ay + cy) / 2;
   *     return dst;
   * }
   * ```
   */
  public fun subDivide(
    t1: Double,
    t2: Double,
    quad: SkDQuad?,
  ) {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * void subDivide(double t1, double t2, SkDQuad* quad) const { *quad = this->subDivide(t1, t2); }
   * ```
   */
  public fun subDivide(
    a: SkDPoint,
    c: SkDPoint,
    t1: Double,
    t2: Double,
  ): SkDPoint {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint SkDQuad::subDivide(const SkDPoint& a, const SkDPoint& c, double t1, double t2) const {
   *     SkASSERT(t1 != t2);
   *     SkDPoint b;
   *     SkDQuad sub = subDivide(t1, t2);
   *     SkDLine b0 = {{a, sub[1] + (a - sub[0])}};
   *     SkDLine b1 = {{c, sub[1] + (c - sub[2])}};
   *     SkIntersections i;
   *     i.intersectRay(b0, b1);
   *     if (i.used() == 1 && i[0][0] >= 0 && i[1][0] >= 0) {
   *         b = i.pt(0);
   *     } else {
   *         SkASSERT(i.used() <= 2);
   *         return SkDPoint::Mid(b0[1], b1[1]);
   *     }
   *     if (t1 == 0 || t2 == 0) {
   *         align(0, &b);
   *     }
   *     if (t1 == 1 || t2 == 1) {
   *         align(2, &b);
   *     }
   *     if (AlmostBequalUlps(b.fX, a.fX)) {
   *         b.fX = a.fX;
   *     } else if (AlmostBequalUlps(b.fX, c.fX)) {
   *         b.fX = c.fX;
   *     }
   *     if (AlmostBequalUlps(b.fY, a.fY)) {
   *         b.fY = a.fY;
   *     } else if (AlmostBequalUlps(b.fY, c.fY)) {
   *         b.fY = c.fY;
   *     }
   *     return b;
   * }
   * ```
   */
  public fun verticalIntersect(xIntercept: Double, roots: DoubleArray): Int {
    TODO("Implement verticalIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkDQuad::verticalIntersect(double xIntercept, double roots[2]) const {
   *     return SkIntersections::VerticalIntercept(*this, xIntercept, roots);
   * }
   * ```
   */
  public fun debugToCubic(): SkDCubic {
    TODO("Implement debugToCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDCubic SkDQuad::debugToCubic() const {
   *     SkDCubic cubic;
   *     cubic[0] = fPts[0];
   *     cubic[2] = fPts[1];
   *     cubic[3] = fPts[2];
   *     cubic[1].fX = (cubic[0].fX + cubic[2].fX * 2) / 3;
   *     cubic[1].fY = (cubic[0].fY + cubic[2].fY * 2) / 3;
   *     cubic[2].fX = (cubic[3].fX + cubic[2].fX * 2) / 3;
   *     cubic[2].fY = (cubic[3].fY + cubic[2].fY * 2) / 3;
   *     return cubic;
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDQuad::dump() const {
   *     dumpInner();
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
   * void SkDQuad::dumpID(int id) const {
   *     dumpInner();
   *     SkDebugf("}");
   *     DumpID(id);
   * }
   * ```
   */
  public fun dumpInner() {
    TODO("Implement dumpInner")
  }

  public companion object {
    public val kPointCount: Int = TODO("Initialize kPointCount")

    public val kPointLast: Int = TODO("Initialize kPointLast")

    public val kMaxIntersections: Int = TODO("Initialize kMaxIntersections")

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
     * int SkDQuad::AddValidTs(double s[], int realRoots, double* t) {
     *     int foundRoots = 0;
     *     for (int index = 0; index < realRoots; ++index) {
     *         double tValue = s[index];
     *         if (approximately_zero_or_more(tValue) && approximately_one_or_less(tValue)) {
     *             if (approximately_less_than_zero(tValue)) {
     *                 tValue = 0;
     *             } else if (approximately_greater_than_one(tValue)) {
     *                 tValue = 1;
     *             }
     *             for (int idx2 = 0; idx2 < foundRoots; ++idx2) {
     *                 if (approximately_equal(t[idx2], tValue)) {
     *                     goto nextRoot;
     *                 }
     *             }
     *             t[foundRoots++] = tValue;
     *         }
     * nextRoot:
     *         {}
     *     }
     *     return foundRoots;
     * }
     * ```
     */
    public fun addValidTs(
      s: DoubleArray,
      realRoots: Int,
      t: Double?,
    ): Int {
      TODO("Implement addValidTs")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkDQuad::FindExtrema(const double src[], double tValue[1]) {
     *     /*  At + B == 0
     *         t = -B / A
     *     */
     *     double a = src[0];
     *     double b = src[2];
     *     double c = src[4];
     *     return valid_unit_divide(a - b, a - b - b + c, tValue);
     * }
     * ```
     */
    public fun findExtrema(src: DoubleArray, tValue: DoubleArray): Int {
      TODO("Implement findExtrema")
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
     * int SkDQuad::RootsReal(const double A, const double B, const double C, double s[2]) {
     *     if (!A) {
     *         return handle_zero(B, C, s);
     *     }
     *     const double p = B / (2 * A);
     *     const double q = C / A;
     *     if (approximately_zero(A) && (approximately_zero_inverse(p) || approximately_zero_inverse(q))) {
     *         return handle_zero(B, C, s);
     *     }
     *     /* normal form: x^2 + px + q = 0 */
     *     const double p2 = p * p;
     *     if (!AlmostDequalUlps(p2, q) && p2 < q) {
     *         return 0;
     *     }
     *     double sqrt_D = 0;
     *     if (p2 > q) {
     *         sqrt_D = sqrt(p2 - q);
     *     }
     *     s[0] = sqrt_D - p;
     *     s[1] = -sqrt_D - p;
     *     return 1 + !AlmostDequalUlps(s[0], s[1]);
     * }
     * ```
     */
    public fun rootsReal(
      a: Double,
      b: Double,
      c: Double,
      t: DoubleArray,
    ): Int {
      TODO("Implement rootsReal")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkDQuad::RootsValidT(double A, double B, double C, double t[2]) {
     *     double s[2];
     *     int realRoots = RootsReal(A, B, C, s);
     *     int foundRoots = AddValidTs(s, realRoots, t);
     *     return foundRoots;
     * }
     * ```
     */
    public fun rootsValidT(
      a: Double,
      b: Double,
      c: Double,
      s: DoubleArray,
    ): Int {
      TODO("Implement rootsValidT")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkDQuad::SetABC(const double* quad, double* a, double* b, double* c) {
     *     *a = quad[0];      // a = A
     *     *b = 2 * quad[2];  // b =     2*B
     *     *c = quad[4];      // c =             C
     *     *b -= *c;          // b =     2*B -   C
     *     *a -= *b;          // a = A - 2*B +   C
     *     *b -= *c;          // b =     2*B - 2*C
     * }
     * ```
     */
    public fun setABC(
      quad: Double?,
      a: Double?,
      b: Double?,
      c: Double?,
    ) {
      TODO("Implement setABC")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkDQuad SubDivide(const SkPoint a[kPointCount], double t1, double t2) {
     *         SkDQuad quad;
     *         quad.set(a);
     *         return quad.subDivide(t1, t2);
     *     }
     * ```
     */
    public fun subDivide(
      a: Array<SkPoint>,
      t1: Double,
      t2: Double,
    ): SkDQuad {
      TODO("Implement subDivide")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkDPoint SubDivide(const SkPoint pts[kPointCount], const SkDPoint& a, const SkDPoint& c,
     *                               double t1, double t2) {
     *         SkDQuad quad;
     *         quad.set(pts);
     *         return quad.subDivide(a, c, t1, t2);
     *     }
     * ```
     */
    public fun subDivide(
      pts: Array<SkPoint>,
      a: SkDPoint,
      c: SkDPoint,
      t1: Double,
      t2: Double,
    ): SkDPoint {
      TODO("Implement subDivide")
    }
  }
}
