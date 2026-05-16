package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkDConic {
 *     static const int kPointCount = 3;
 *     static const int kPointLast = kPointCount - 1;
 *     static const int kMaxIntersections = 4;
 *
 *     SkDQuad fPts;
 *     SkScalar fWeight;
 *
 *     bool collapsed() const {
 *         return fPts.collapsed();
 *     }
 *
 *     bool controlsInside() const {
 *         return fPts.controlsInside();
 *     }
 *
 *     void debugInit() {
 *         fPts.debugInit();
 *         fWeight = 0;
 *     }
 *
 *     void debugSet(const SkDPoint* pts, SkScalar weight);
 *
 *     SkDConic flip() const {
 *         SkDConic result = {{{fPts[2], fPts[1], fPts[0]}
 *                 SkDEBUGPARAMS(fPts.fDebugGlobalState) }, fWeight};
 *         return result;
 *     }
 *
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const { return fPts.globalState(); }
 * #endif
 *
 *     static bool IsConic() { return true; }
 *
 *     const SkDConic& set(const SkPoint pts[kPointCount], SkScalar weight
 *             SkDEBUGPARAMS(SkOpGlobalState* state = nullptr)) {
 *         fPts.set(pts  SkDEBUGPARAMS(state));
 *         fWeight = weight;
 *         return *this;
 *     }
 *
 *     const SkDPoint& operator[](int n) const { return fPts[n]; }
 *     SkDPoint& operator[](int n) { return fPts[n]; }
 *
 *     static int AddValidTs(double s[], int realRoots, double* t) {
 *         return SkDQuad::AddValidTs(s, realRoots, t);
 *     }
 *
 *     void align(int endIndex, SkDPoint* dstPt) const {
 *         fPts.align(endIndex, dstPt);
 *     }
 *
 *     SkDVector dxdyAtT(double t) const;
 *     static int FindExtrema(const double src[], SkScalar weight, double tValue[1]);
 *
 *     bool hullIntersects(const SkDQuad& quad, bool* isLinear) const {
 *         return fPts.hullIntersects(quad, isLinear);
 *     }
 *
 *     bool hullIntersects(const SkDConic& conic, bool* isLinear) const {
 *         return fPts.hullIntersects(conic.fPts, isLinear);
 *     }
 *
 *     bool hullIntersects(const SkDCubic& cubic, bool* isLinear) const;
 *
 *     bool isLinear(int startIndex, int endIndex) const {
 *         return fPts.isLinear(startIndex, endIndex);
 *     }
 *
 *     static int maxIntersections() { return kMaxIntersections; }
 *
 *     bool monotonicInX() const {
 *         return fPts.monotonicInX();
 *     }
 *
 *     bool monotonicInY() const {
 *         return fPts.monotonicInY();
 *     }
 *
 *     void otherPts(int oddMan, const SkDPoint* endPt[2]) const {
 *         fPts.otherPts(oddMan, endPt);
 *     }
 *
 *     static int pointCount() { return kPointCount; }
 *     static int pointLast() { return kPointLast; }
 *     SkDPoint ptAtT(double t) const;
 *
 *     static int RootsReal(double A, double B, double C, double t[2]) {
 *         return SkDQuad::RootsReal(A, B, C, t);
 *     }
 *
 *     static int RootsValidT(const double A, const double B, const double C, double s[2]) {
 *         return SkDQuad::RootsValidT(A, B, C, s);
 *     }
 *
 *     SkDConic subDivide(double t1, double t2) const;
 *     void subDivide(double t1, double t2, SkDConic* c) const { *c = this->subDivide(t1, t2); }
 *
 *     static SkDConic SubDivide(const SkPoint a[kPointCount], SkScalar weight, double t1, double t2) {
 *         SkDConic conic;
 *         conic.set(a, weight);
 *         return conic.subDivide(t1, t2);
 *     }
 *
 *     SkDPoint subDivide(const SkDPoint& a, const SkDPoint& c, double t1, double t2,
 *             SkScalar* weight) const;
 *
 *     static SkDPoint SubDivide(const SkPoint pts[kPointCount], SkScalar weight,
 *                               const SkDPoint& a, const SkDPoint& c,
 *                               double t1, double t2, SkScalar* newWeight) {
 *         SkDConic conic;
 *         conic.set(pts, weight);
 *         return conic.subDivide(a, c, t1, t2, newWeight);
 *     }
 *
 *     // utilities callable by the user from the debugger when the implementation code is linked in
 *     void dump() const;
 *     void dumpID(int id) const;
 *     void dumpInner() const;
 *
 * }
 * ```
 */
public data class SkDConic public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kPointCount = 3
   * ```
   */
  public var fPts: SkDQuad,
  /**
   * C++ original:
   * ```cpp
   * static const int kPointLast = kPointCount - 1
   * ```
   */
  public var fWeight: SkScalar,
) {
  /**
   * C++ original:
   * ```cpp
   * bool collapsed() const {
   *         return fPts.collapsed();
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
   *         return fPts.controlsInside();
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
   *         fPts.debugInit();
   *         fWeight = 0;
   *     }
   * ```
   */
  public fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDConic::debugSet(const SkDPoint* pts, SkScalar weight) {
   *     fPts.debugSet(pts);
   *     fWeight = weight;
   * }
   * ```
   */
  public fun debugSet(pts: SkDPoint?, weight: SkScalar) {
    TODO("Implement debugSet")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDConic flip() const {
   *         SkDConic result = {{{fPts[2], fPts[1], fPts[0]}
   *                 SkDEBUGPARAMS(fPts.fDebugGlobalState) }, fWeight};
   *         return result;
   *     }
   * ```
   */
  public fun flip(): SkDConic {
    TODO("Implement flip")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const { return fPts.globalState(); }
   * ```
   */
  public fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDConic& set(const SkPoint pts[kPointCount], SkScalar weight
   *             SkDEBUGPARAMS(SkOpGlobalState* state = nullptr)) {
   *         fPts.set(pts  SkDEBUGPARAMS(state));
   *         fWeight = weight;
   *         return *this;
   *     }
   * ```
   */
  public fun `set`(
    param0: SkPoint?,
    param1: SkScalar,
    param2: SkOpGlobalState?,
  ): SkDConic {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const { return fPts[n]; }
   * ```
   */
  public operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) { return fPts[n]; }
   * ```
   */
  public fun align(endIndex: Int, dstPt: SkDPoint?) {
    TODO("Implement align")
  }

  /**
   * C++ original:
   * ```cpp
   * void align(int endIndex, SkDPoint* dstPt) const {
   *         fPts.align(endIndex, dstPt);
   *     }
   * ```
   */
  public fun dxdyAtT(t: Double): SkDVector {
    TODO("Implement dxdyAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector SkDConic::dxdyAtT(double t) const {
   *     SkDVector result = {
   *         conic_eval_tan(&fPts[0].fX, fWeight, t),
   *         conic_eval_tan(&fPts[0].fY, fWeight, t)
   *     };
   *     if (result.fX == 0 && result.fY == 0) {
   *         if (zero_or_one(t)) {
   *             result = fPts[2] - fPts[0];
   *         } else {
   *             // incomplete
   *             SkDebugf("!k");
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  public fun hullIntersects(quad: SkDQuad, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkDQuad& quad, bool* isLinear) const {
   *         return fPts.hullIntersects(quad, isLinear);
   *     }
   * ```
   */
  public fun hullIntersects(conic: SkDConic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hullIntersects(const SkDConic& conic, bool* isLinear) const {
   *         return fPts.hullIntersects(conic.fPts, isLinear);
   *     }
   * ```
   */
  public fun hullIntersects(cubic: SkDCubic, isLinear: Boolean?): Boolean {
    TODO("Implement hullIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDConic::hullIntersects(const SkDCubic& cubic, bool* isLinear) const {
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
   * bool isLinear(int startIndex, int endIndex) const {
   *         return fPts.isLinear(startIndex, endIndex);
   *     }
   * ```
   */
  public fun monotonicInX(): Boolean {
    TODO("Implement monotonicInX")
  }

  /**
   * C++ original:
   * ```cpp
   * bool monotonicInX() const {
   *         return fPts.monotonicInX();
   *     }
   * ```
   */
  public fun monotonicInY(): Boolean {
    TODO("Implement monotonicInY")
  }

  /**
   * C++ original:
   * ```cpp
   * bool monotonicInY() const {
   *         return fPts.monotonicInY();
   *     }
   * ```
   */
  public fun otherPts(oddMan: Int, endPt: Int) {
    TODO("Implement otherPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void otherPts(int oddMan, const SkDPoint* endPt[2]) const {
   *         fPts.otherPts(oddMan, endPt);
   *     }
   * ```
   */
  public fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint SkDConic::ptAtT(double t) const {
   *     if (t == 0) {
   *         return fPts[0];
   *     }
   *     if (t == 1) {
   *         return fPts[2];
   *     }
   *     double denominator = conic_eval_denominator(fWeight, t);
   *     SkDPoint result = {
   *         sk_ieee_double_divide(conic_eval_numerator(&fPts[0].fX, fWeight, t), denominator),
   *         sk_ieee_double_divide(conic_eval_numerator(&fPts[0].fY, fWeight, t), denominator)
   *     };
   *     return result;
   * }
   * ```
   */
  public fun subDivide(t1: Double, t2: Double): SkDConic {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDConic SkDConic::subDivide(double t1, double t2) const {
   *     double ax, ay, az;
   *     if (t1 == 0) {
   *         ax = fPts[0].fX;
   *         ay = fPts[0].fY;
   *         az = 1;
   *     } else if (t1 != 1) {
   *         ax = conic_eval_numerator(&fPts[0].fX, fWeight, t1);
   *         ay = conic_eval_numerator(&fPts[0].fY, fWeight, t1);
   *         az = conic_eval_denominator(fWeight, t1);
   *     } else {
   *         ax = fPts[2].fX;
   *         ay = fPts[2].fY;
   *         az = 1;
   *     }
   *     double midT = (t1 + t2) / 2;
   *     double dx = conic_eval_numerator(&fPts[0].fX, fWeight, midT);
   *     double dy = conic_eval_numerator(&fPts[0].fY, fWeight, midT);
   *     double dz = conic_eval_denominator(fWeight, midT);
   *     double cx, cy, cz;
   *     if (t2 == 1) {
   *         cx = fPts[2].fX;
   *         cy = fPts[2].fY;
   *         cz = 1;
   *     } else if (t2 != 0) {
   *         cx = conic_eval_numerator(&fPts[0].fX, fWeight, t2);
   *         cy = conic_eval_numerator(&fPts[0].fY, fWeight, t2);
   *         cz = conic_eval_denominator(fWeight, t2);
   *     } else {
   *         cx = fPts[0].fX;
   *         cy = fPts[0].fY;
   *         cz = 1;
   *     }
   *     double bx = 2 * dx - (ax + cx) / 2;
   *     double by = 2 * dy - (ay + cy) / 2;
   *     double bz = 2 * dz - (az + cz) / 2;
   *     if (!bz) {
   *         bz = 1; // if bz is 0, weight is 0, control point has no effect: any value will do
   *     }
   *     SkDConic dst = {{{{ax / az, ay / az}, {bx / bz, by / bz}, {cx / cz, cy / cz}}
   *             SkDEBUGPARAMS(fPts.fDebugGlobalState) },
   *             SkDoubleToScalar(bz / sqrt(az * cz)) };
   *     return dst;
   * }
   * ```
   */
  public fun subDivide(
    t1: Double,
    t2: Double,
    c: SkDConic?,
  ) {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * void subDivide(double t1, double t2, SkDConic* c) const { *c = this->subDivide(t1, t2); }
   * ```
   */
  public fun subDivide(
    a: SkDPoint,
    c: SkDPoint,
    t1: Double,
    t2: Double,
    weight: SkScalar?,
  ): SkDPoint {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint SkDConic::subDivide(const SkDPoint& a, const SkDPoint& c, double t1, double t2,
   *         SkScalar* weight) const {
   *     SkDConic chopped = this->subDivide(t1, t2);
   *     *weight = chopped.fWeight;
   *     return chopped[1];
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDConic::dump() const {
   *     dumpInner();
   *     SkDebugf("},\n");
   * }
   * ```
   */
  public fun dumpID(id: Int) {
    TODO("Implement dumpID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDConic::dumpID(int id) const {
   *     dumpInner();
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
     * static bool IsConic() { return true; }
     * ```
     */
    public fun isConic(): Boolean {
      TODO("Implement isConic")
    }

    /**
     * C++ original:
     * ```cpp
     * static int AddValidTs(double s[], int realRoots, double* t) {
     *         return SkDQuad::AddValidTs(s, realRoots, t);
     *     }
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
     * int SkDConic::FindExtrema(const double src[], SkScalar w, double t[1]) {
     *     double coeff[3];
     *     conic_deriv_coeff(src, w, coeff);
     *
     *     double tValues[2];
     *     int roots = SkDQuad::RootsValidT(coeff[0], coeff[1], coeff[2], tValues);
     *     // In extreme cases, the number of roots returned can be 2. Pathops
     *     // will fail later on, so there's no advantage to plumbing in an error
     *     // return here.
     *     // SkASSERT(0 == roots || 1 == roots);
     *
     *     if (1 == roots) {
     *         t[0] = tValues[0];
     *         return 1;
     *     }
     *     return 0;
     * }
     * ```
     */
    public fun findExtrema(
      src: DoubleArray,
      weight: SkScalar,
      tValue: DoubleArray,
    ): Int {
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
     * static int RootsReal(double A, double B, double C, double t[2]) {
     *         return SkDQuad::RootsReal(A, B, C, t);
     *     }
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
     * static int RootsValidT(const double A, const double B, const double C, double s[2]) {
     *         return SkDQuad::RootsValidT(A, B, C, s);
     *     }
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
     * static SkDConic SubDivide(const SkPoint a[kPointCount], SkScalar weight, double t1, double t2) {
     *         SkDConic conic;
     *         conic.set(a, weight);
     *         return conic.subDivide(t1, t2);
     *     }
     * ```
     */
    public fun subDivide(
      a: Array<SkPoint>,
      weight: SkScalar,
      t1: Double,
      t2: Double,
    ): SkDConic {
      TODO("Implement subDivide")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkDPoint SubDivide(const SkPoint pts[kPointCount], SkScalar weight,
     *                               const SkDPoint& a, const SkDPoint& c,
     *                               double t1, double t2, SkScalar* newWeight) {
     *         SkDConic conic;
     *         conic.set(pts, weight);
     *         return conic.subDivide(a, c, t1, t2, newWeight);
     *     }
     * ```
     */
    public fun subDivide(
      pts: Array<SkPoint>,
      weight: SkScalar,
      a: SkDPoint,
      c: SkDPoint,
      t1: Double,
      t2: Double,
      newWeight: SkScalar?,
    ): SkDPoint {
      TODO("Implement subDivide")
    }
  }
}
