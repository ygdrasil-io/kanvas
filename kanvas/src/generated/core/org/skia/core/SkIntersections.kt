package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.BooleanArray
import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int
import kotlin.IntArray
import kotlin.UByte
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkIntersections {
 * public:
 *     SkIntersections(SkDEBUGCODE(SkOpGlobalState* globalState = nullptr))
 *         : fSwap(0)
 * #ifdef SK_DEBUG
 *         SkDEBUGPARAMS(fDebugGlobalState(globalState))
 *         , fDepth(0)
 * #endif
 *     {
 *         sk_bzero(fPt, sizeof(fPt));
 *         sk_bzero(fPt2, sizeof(fPt2));
 *         sk_bzero(fT, sizeof(fT));
 *         sk_bzero(fNearlySame, sizeof(fNearlySame));
 * #if DEBUG_T_SECT_LOOP_COUNT
 *         sk_bzero(fDebugLoopCount, sizeof(fDebugLoopCount));
 * #endif
 *         reset();
 *         fMax = 0;  // require that the caller set the max
 *     }
 *
 *     class TArray {
 *     public:
 *         explicit TArray(const double ts[10]) : fTArray(ts) {}
 *         double operator[](int n) const {
 *             return fTArray[n];
 *         }
 *         const double* fTArray;
 *     };
 *     TArray operator[](int n) const { return TArray(fT[n]); }
 *
 *     void allowNear(bool nearAllowed) {
 *         fAllowNear = nearAllowed;
 *     }
 *
 *     void clearCoincidence(int index) {
 *         SkASSERT(index >= 0);
 *         int bit = 1 << index;
 *         fIsCoincident[0] &= ~bit;
 *         fIsCoincident[1] &= ~bit;
 *     }
 *
 *     int conicHorizontal(const SkPoint a[3], SkScalar weight, SkScalar left, SkScalar right,
 *                 SkScalar y, bool flipped) {
 *         SkDConic conic;
 *         conic.set(a, weight);
 *         fMax = 2;
 *         return horizontal(conic, left, right, y, flipped);
 *     }
 *
 *     int conicVertical(const SkPoint a[3], SkScalar weight, SkScalar top, SkScalar bottom,
 *             SkScalar x, bool flipped) {
 *         SkDConic conic;
 *         conic.set(a, weight);
 *         fMax = 2;
 *         return vertical(conic, top, bottom, x, flipped);
 *     }
 *
 *     int conicLine(const SkPoint a[3], SkScalar weight, const SkPoint b[2]) {
 *         SkDConic conic;
 *         conic.set(a, weight);
 *         SkDLine line;
 *         line.set(b);
 *         fMax = 3; // 2;  permit small coincident segment + non-coincident intersection
 *         return intersect(conic, line);
 *     }
 *
 *     int cubicHorizontal(const SkPoint a[4], SkScalar left, SkScalar right, SkScalar y,
 *                         bool flipped) {
 *         SkDCubic cubic;
 *         cubic.set(a);
 *         fMax = 3;
 *         return horizontal(cubic, left, right, y, flipped);
 *     }
 *
 *     int cubicVertical(const SkPoint a[4], SkScalar top, SkScalar bottom, SkScalar x, bool flipped) {
 *         SkDCubic cubic;
 *         cubic.set(a);
 *         fMax = 3;
 *         return vertical(cubic, top, bottom, x, flipped);
 *     }
 *
 *     int cubicLine(const SkPoint a[4], const SkPoint b[2]) {
 *         SkDCubic cubic;
 *         cubic.set(a);
 *         SkDLine line;
 *         line.set(b);
 *         fMax = 3;
 *         return intersect(cubic, line);
 *     }
 *
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* globalState() const { return fDebugGlobalState; }
 * #endif
 *
 *     bool hasT(double t) const {
 *         SkASSERT(t == 0 || t == 1);
 *         return fUsed > 0 && (t == 0 ? fT[0][0] == 0 : fT[0][fUsed - 1] == 1);
 *     }
 *
 *     bool hasOppT(double t) const {
 *         SkASSERT(t == 0 || t == 1);
 *         return fUsed > 0 && (fT[1][0] == t || fT[1][fUsed - 1] == t);
 *     }
 *
 *     int insertSwap(double one, double two, const SkDPoint& pt) {
 *         if (fSwap) {
 *             return insert(two, one, pt);
 *         } else {
 *             return insert(one, two, pt);
 *         }
 *     }
 *
 *     bool isCoincident(int index) {
 *         return (fIsCoincident[0] & 1 << index) != 0;
 *     }
 *
 *     int lineHorizontal(const SkPoint a[2], SkScalar left, SkScalar right, SkScalar y,
 *                        bool flipped) {
 *         SkDLine line;
 *         line.set(a);
 *         fMax = 2;
 *         return horizontal(line, left, right, y, flipped);
 *     }
 *
 *     int lineVertical(const SkPoint a[2], SkScalar top, SkScalar bottom, SkScalar x, bool flipped) {
 *         SkDLine line;
 *         line.set(a);
 *         fMax = 2;
 *         return vertical(line, top, bottom, x, flipped);
 *     }
 *
 *     int lineLine(const SkPoint a[2], const SkPoint b[2]) {
 *         SkDLine aLine, bLine;
 *         aLine.set(a);
 *         bLine.set(b);
 *         fMax = 2;
 *         return intersect(aLine, bLine);
 *     }
 *
 *     bool nearlySame(int index) const {
 *         SkASSERT(index == 0 || index == 1);
 *         return fNearlySame[index];
 *     }
 *
 *     const SkDPoint& pt(int index) const {
 *         return fPt[index];
 *     }
 *
 *     const SkDPoint& pt2(int index) const {
 *         return fPt2[index];
 *     }
 *
 *     int quadHorizontal(const SkPoint a[3], SkScalar left, SkScalar right, SkScalar y,
 *                        bool flipped) {
 *         SkDQuad quad;
 *         quad.set(a);
 *         fMax = 2;
 *         return horizontal(quad, left, right, y, flipped);
 *     }
 *
 *     int quadVertical(const SkPoint a[3], SkScalar top, SkScalar bottom, SkScalar x, bool flipped) {
 *         SkDQuad quad;
 *         quad.set(a);
 *         fMax = 2;
 *         return vertical(quad, top, bottom, x, flipped);
 *     }
 *
 *     int quadLine(const SkPoint a[3], const SkPoint b[2]) {
 *         SkDQuad quad;
 *         quad.set(a);
 *         SkDLine line;
 *         line.set(b);
 *         return intersect(quad, line);
 *     }
 *
 *     // leaves swap, max alone
 *     void reset() {
 *         fAllowNear = true;
 *         fUsed = 0;
 *         sk_bzero(fIsCoincident, sizeof(fIsCoincident));
 *     }
 *
 *     void set(bool swap, int tIndex, double t) {
 *         fT[(int) swap][tIndex] = t;
 *     }
 *
 *     void setMax(int max) {
 *         SkASSERT(max <= (int) std::size(fPt));
 *         fMax = max;
 *     }
 *
 *     void swap() {
 *         fSwap ^= true;
 *     }
 *
 *     bool swapped() const {
 *         return fSwap;
 *     }
 *
 *     int used() const {
 *         return fUsed;
 *     }
 *
 *     void downDepth() {
 *         SkASSERT(--fDepth >= 0);
 *     }
 *
 *     bool unBumpT(int index) {
 *         SkASSERT(fUsed == 1);
 *         fT[0][index] = fT[0][index] * (1 + BUMP_EPSILON * 2) - BUMP_EPSILON;
 *         if (!between(0, fT[0][index], 1)) {
 *             fUsed = 0;
 *             return false;
 *         }
 *         return true;
 *     }
 *
 *     void upDepth() {
 *         SkASSERT(++fDepth < 16);
 *     }
 *
 *     void alignQuadPts(const SkPoint a[3], const SkPoint b[3]);
 *     int cleanUpCoincidence();
 *     int closestTo(double rangeStart, double rangeEnd, const SkDPoint& testPt, double* dist) const;
 *     void cubicInsert(double one, double two, const SkDPoint& pt, const SkDCubic& c1,
 *                      const SkDCubic& c2);
 *     void flip();
 *     int horizontal(const SkDLine&, double left, double right, double y, bool flipped);
 *     int horizontal(const SkDQuad&, double left, double right, double y, bool flipped);
 *     int horizontal(const SkDQuad&, double left, double right, double y, double tRange[2]);
 *     int horizontal(const SkDCubic&, double y, double tRange[3]);
 *     int horizontal(const SkDConic&, double left, double right, double y, bool flipped);
 *     int horizontal(const SkDCubic&, double left, double right, double y, bool flipped);
 *     int horizontal(const SkDCubic&, double left, double right, double y, double tRange[3]);
 *     static double HorizontalIntercept(const SkDLine& line, double y);
 *     static int HorizontalIntercept(const SkDQuad& quad, SkScalar y, double* roots);
 *     static int HorizontalIntercept(const SkDConic& conic, SkScalar y, double* roots);
 *     // FIXME : does not respect swap
 *     int insert(double one, double two, const SkDPoint& pt);
 *     void insertNear(double one, double two, const SkDPoint& pt1, const SkDPoint& pt2);
 *     // start if index == 0 : end if index == 1
 *     int insertCoincident(double one, double two, const SkDPoint& pt);
 *     int intersect(const SkDLine&, const SkDLine&);
 *     int intersect(const SkDQuad&, const SkDLine&);
 *     int intersect(const SkDQuad&, const SkDQuad&);
 *     int intersect(const SkDConic&, const SkDLine&);
 *     int intersect(const SkDConic&, const SkDQuad&);
 *     int intersect(const SkDConic&, const SkDConic&);
 *     int intersect(const SkDCubic&, const SkDLine&);
 *     int intersect(const SkDCubic&, const SkDQuad&);
 *     int intersect(const SkDCubic&, const SkDConic&);
 *     int intersect(const SkDCubic&, const SkDCubic&);
 *     int intersectRay(const SkDLine&, const SkDLine&);
 *     int intersectRay(const SkDQuad&, const SkDLine&);
 *     int intersectRay(const SkDConic&, const SkDLine&);
 *     int intersectRay(const SkDCubic&, const SkDLine&);
 *     int intersectRay(const SkTCurve& tCurve, const SkDLine& line) {
 *         return tCurve.intersectRay(this, line);
 *     }
 *
 *     void merge(const SkIntersections& , int , const SkIntersections& , int );
 *     int mostOutside(double rangeStart, double rangeEnd, const SkDPoint& origin) const;
 *     void removeOne(int index);
 *     void setCoincident(int index);
 *     int vertical(const SkDLine&, double top, double bottom, double x, bool flipped);
 *     int vertical(const SkDQuad&, double top, double bottom, double x, bool flipped);
 *     int vertical(const SkDConic&, double top, double bottom, double x, bool flipped);
 *     int vertical(const SkDCubic&, double top, double bottom, double x, bool flipped);
 *     static double VerticalIntercept(const SkDLine& line, double x);
 *     static int VerticalIntercept(const SkDQuad& quad, SkScalar x, double* roots);
 *     static int VerticalIntercept(const SkDConic& conic, SkScalar x, double* roots);
 *
 *     int depth() const {
 * #ifdef SK_DEBUG
 *         return fDepth;
 * #else
 *         return 0;
 * #endif
 *     }
 *
 *     enum DebugLoop {
 *         kIterations_DebugLoop,
 *         kCoinCheck_DebugLoop,
 *         kComputePerp_DebugLoop,
 *     };
 *
 *     void debugBumpLoopCount(DebugLoop );
 *     int debugCoincidentUsed() const;
 *     int debugLoopCount(DebugLoop ) const;
 *     void debugResetLoopCount();
 *     void dump() const;  // implemented for testing only
 *
 * private:
 *     bool cubicCheckCoincidence(const SkDCubic& c1, const SkDCubic& c2);
 *     bool cubicExactEnd(const SkDCubic& cubic1, bool start, const SkDCubic& cubic2);
 *     void cubicNearEnd(const SkDCubic& cubic1, bool start, const SkDCubic& cubic2, const SkDRect& );
 *     void cleanUpParallelLines(bool parallel);
 *     void computePoints(const SkDLine& line, int used);
 *
 *     SkDPoint fPt[13];  // FIXME: since scans store points as SkPoint, this should also
 *     SkDPoint fPt2[2];  // used by nearly same to store alternate intersection point
 *     double fT[2][13];
 *     uint16_t fIsCoincident[2];  // bit set for each curve's coincident T
 *     bool fNearlySame[2];  // true if end points nearly match
 *     unsigned char fUsed;
 *     unsigned char fMax;
 *     bool fAllowNear;
 *     bool fSwap;
 * #ifdef SK_DEBUG
 *     SkOpGlobalState* fDebugGlobalState;
 *     int fDepth;
 * #endif
 * #if DEBUG_T_SECT_LOOP_COUNT
 *     int fDebugLoopCount[3];
 * #endif
 * }
 * ```
 */
public data class SkIntersections public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDPoint fPt[13]
   * ```
   */
  private var fPt: Array<SkDPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkDPoint fPt2[2]
   * ```
   */
  private var fPt2: Array<SkDPoint>,
  /**
   * C++ original:
   * ```cpp
   * double fT[2][13]
   * ```
   */
  private var fT: DoubleArray,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fIsCoincident[2]
   * ```
   */
  private var fIsCoincident: IntArray,
  /**
   * C++ original:
   * ```cpp
   * bool fNearlySame[2]
   * ```
   */
  private var fNearlySame: BooleanArray,
  /**
   * C++ original:
   * ```cpp
   * unsigned char fUsed
   * ```
   */
  private var fUsed: UByte,
  /**
   * C++ original:
   * ```cpp
   * unsigned char fMax
   * ```
   */
  private var fMax: UByte,
  /**
   * C++ original:
   * ```cpp
   * bool fAllowNear
   * ```
   */
  private var fAllowNear: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fSwap
   * ```
   */
  private var fSwap: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* fDebugGlobalState
   * ```
   */
  private var fDebugGlobalState: SkOpGlobalState?,
  /**
   * C++ original:
   * ```cpp
   * int fDepth
   * ```
   */
  private var fDepth: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * TArray operator[](int n) const { return TArray(fT[n]); }
   * ```
   */
  public operator fun `get`(n: Int): TArray {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void allowNear(bool nearAllowed) {
   *         fAllowNear = nearAllowed;
   *     }
   * ```
   */
  public fun allowNear(nearAllowed: Boolean) {
    TODO("Implement allowNear")
  }

  /**
   * C++ original:
   * ```cpp
   * void clearCoincidence(int index) {
   *         SkASSERT(index >= 0);
   *         int bit = 1 << index;
   *         fIsCoincident[0] &= ~bit;
   *         fIsCoincident[1] &= ~bit;
   *     }
   * ```
   */
  public fun clearCoincidence(index: Int) {
    TODO("Implement clearCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * int conicHorizontal(const SkPoint a[3], SkScalar weight, SkScalar left, SkScalar right,
   *                 SkScalar y, bool flipped) {
   *         SkDConic conic;
   *         conic.set(a, weight);
   *         fMax = 2;
   *         return horizontal(conic, left, right, y, flipped);
   *     }
   * ```
   */
  public fun conicHorizontal(
    a: Array<SkPoint>,
    weight: SkScalar,
    left: SkScalar,
    right: SkScalar,
    y: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement conicHorizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int conicVertical(const SkPoint a[3], SkScalar weight, SkScalar top, SkScalar bottom,
   *             SkScalar x, bool flipped) {
   *         SkDConic conic;
   *         conic.set(a, weight);
   *         fMax = 2;
   *         return vertical(conic, top, bottom, x, flipped);
   *     }
   * ```
   */
  public fun conicVertical(
    a: Array<SkPoint>,
    weight: SkScalar,
    top: SkScalar,
    bottom: SkScalar,
    x: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement conicVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int conicLine(const SkPoint a[3], SkScalar weight, const SkPoint b[2]) {
   *         SkDConic conic;
   *         conic.set(a, weight);
   *         SkDLine line;
   *         line.set(b);
   *         fMax = 3; // 2;  permit small coincident segment + non-coincident intersection
   *         return intersect(conic, line);
   *     }
   * ```
   */
  public fun conicLine(
    a: Array<SkPoint>,
    weight: SkScalar,
    b: Array<SkPoint>,
  ): Int {
    TODO("Implement conicLine")
  }

  /**
   * C++ original:
   * ```cpp
   * int cubicHorizontal(const SkPoint a[4], SkScalar left, SkScalar right, SkScalar y,
   *                         bool flipped) {
   *         SkDCubic cubic;
   *         cubic.set(a);
   *         fMax = 3;
   *         return horizontal(cubic, left, right, y, flipped);
   *     }
   * ```
   */
  public fun cubicHorizontal(
    a: Array<SkPoint>,
    left: SkScalar,
    right: SkScalar,
    y: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement cubicHorizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int cubicVertical(const SkPoint a[4], SkScalar top, SkScalar bottom, SkScalar x, bool flipped) {
   *         SkDCubic cubic;
   *         cubic.set(a);
   *         fMax = 3;
   *         return vertical(cubic, top, bottom, x, flipped);
   *     }
   * ```
   */
  public fun cubicVertical(
    a: Array<SkPoint>,
    top: SkScalar,
    bottom: SkScalar,
    x: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement cubicVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int cubicLine(const SkPoint a[4], const SkPoint b[2]) {
   *         SkDCubic cubic;
   *         cubic.set(a);
   *         SkDLine line;
   *         line.set(b);
   *         fMax = 3;
   *         return intersect(cubic, line);
   *     }
   * ```
   */
  public fun cubicLine(a: Array<SkPoint>, b: Array<SkPoint>): Int {
    TODO("Implement cubicLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const { return fDebugGlobalState; }
   * ```
   */
  public fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasT(double t) const {
   *         SkASSERT(t == 0 || t == 1);
   *         return fUsed > 0 && (t == 0 ? fT[0][0] == 0 : fT[0][fUsed - 1] == 1);
   *     }
   * ```
   */
  public fun hasT(t: Double): Boolean {
    TODO("Implement hasT")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasOppT(double t) const {
   *         SkASSERT(t == 0 || t == 1);
   *         return fUsed > 0 && (fT[1][0] == t || fT[1][fUsed - 1] == t);
   *     }
   * ```
   */
  public fun hasOppT(t: Double): Boolean {
    TODO("Implement hasOppT")
  }

  /**
   * C++ original:
   * ```cpp
   * int insertSwap(double one, double two, const SkDPoint& pt) {
   *         if (fSwap) {
   *             return insert(two, one, pt);
   *         } else {
   *             return insert(one, two, pt);
   *         }
   *     }
   * ```
   */
  public fun insertSwap(
    one: Double,
    two: Double,
    pt: SkDPoint,
  ): Int {
    TODO("Implement insertSwap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isCoincident(int index) {
   *         return (fIsCoincident[0] & 1 << index) != 0;
   *     }
   * ```
   */
  public fun isCoincident(index: Int): Boolean {
    TODO("Implement isCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * int lineHorizontal(const SkPoint a[2], SkScalar left, SkScalar right, SkScalar y,
   *                        bool flipped) {
   *         SkDLine line;
   *         line.set(a);
   *         fMax = 2;
   *         return horizontal(line, left, right, y, flipped);
   *     }
   * ```
   */
  public fun lineHorizontal(
    a: Array<SkPoint>,
    left: SkScalar,
    right: SkScalar,
    y: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement lineHorizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int lineVertical(const SkPoint a[2], SkScalar top, SkScalar bottom, SkScalar x, bool flipped) {
   *         SkDLine line;
   *         line.set(a);
   *         fMax = 2;
   *         return vertical(line, top, bottom, x, flipped);
   *     }
   * ```
   */
  public fun lineVertical(
    a: Array<SkPoint>,
    top: SkScalar,
    bottom: SkScalar,
    x: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement lineVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int lineLine(const SkPoint a[2], const SkPoint b[2]) {
   *         SkDLine aLine, bLine;
   *         aLine.set(a);
   *         bLine.set(b);
   *         fMax = 2;
   *         return intersect(aLine, bLine);
   *     }
   * ```
   */
  public fun lineLine(a: Array<SkPoint>, b: Array<SkPoint>): Int {
    TODO("Implement lineLine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool nearlySame(int index) const {
   *         SkASSERT(index == 0 || index == 1);
   *         return fNearlySame[index];
   *     }
   * ```
   */
  public fun nearlySame(index: Int): Boolean {
    TODO("Implement nearlySame")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& pt(int index) const {
   *         return fPt[index];
   *     }
   * ```
   */
  public fun pt(index: Int): SkDPoint {
    TODO("Implement pt")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& pt2(int index) const {
   *         return fPt2[index];
   *     }
   * ```
   */
  public fun pt2(index: Int): SkDPoint {
    TODO("Implement pt2")
  }

  /**
   * C++ original:
   * ```cpp
   * int quadHorizontal(const SkPoint a[3], SkScalar left, SkScalar right, SkScalar y,
   *                        bool flipped) {
   *         SkDQuad quad;
   *         quad.set(a);
   *         fMax = 2;
   *         return horizontal(quad, left, right, y, flipped);
   *     }
   * ```
   */
  public fun quadHorizontal(
    a: Array<SkPoint>,
    left: SkScalar,
    right: SkScalar,
    y: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement quadHorizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int quadVertical(const SkPoint a[3], SkScalar top, SkScalar bottom, SkScalar x, bool flipped) {
   *         SkDQuad quad;
   *         quad.set(a);
   *         fMax = 2;
   *         return vertical(quad, top, bottom, x, flipped);
   *     }
   * ```
   */
  public fun quadVertical(
    a: Array<SkPoint>,
    top: SkScalar,
    bottom: SkScalar,
    x: SkScalar,
    flipped: Boolean,
  ): Int {
    TODO("Implement quadVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int quadLine(const SkPoint a[3], const SkPoint b[2]) {
   *         SkDQuad quad;
   *         quad.set(a);
   *         SkDLine line;
   *         line.set(b);
   *         return intersect(quad, line);
   *     }
   * ```
   */
  public fun quadLine(a: Array<SkPoint>, b: Array<SkPoint>): Int {
    TODO("Implement quadLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fAllowNear = true;
   *         fUsed = 0;
   *         sk_bzero(fIsCoincident, sizeof(fIsCoincident));
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(bool swap, int tIndex, double t) {
   *         fT[(int) swap][tIndex] = t;
   *     }
   * ```
   */
  public fun `set`(
    swap: Boolean,
    tIndex: Int,
    t: Double,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void setMax(int max) {
   *         SkASSERT(max <= (int) std::size(fPt));
   *         fMax = max;
   *     }
   * ```
   */
  public fun setMax(max: Int) {
    TODO("Implement setMax")
  }

  /**
   * C++ original:
   * ```cpp
   * void swap() {
   *         fSwap ^= true;
   *     }
   * ```
   */
  public fun swap() {
    TODO("Implement swap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool swapped() const {
   *         return fSwap;
   *     }
   * ```
   */
  public fun swapped(): Boolean {
    TODO("Implement swapped")
  }

  /**
   * C++ original:
   * ```cpp
   * int used() const {
   *         return fUsed;
   *     }
   * ```
   */
  public fun used(): Int {
    TODO("Implement used")
  }

  /**
   * C++ original:
   * ```cpp
   * void downDepth() {
   *         SkASSERT(--fDepth >= 0);
   *     }
   * ```
   */
  public fun downDepth() {
    TODO("Implement downDepth")
  }

  /**
   * C++ original:
   * ```cpp
   * bool unBumpT(int index) {
   *         SkASSERT(fUsed == 1);
   *         fT[0][index] = fT[0][index] * (1 + BUMP_EPSILON * 2) - BUMP_EPSILON;
   *         if (!between(0, fT[0][index], 1)) {
   *             fUsed = 0;
   *             return false;
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun unBumpT(index: Int): Boolean {
    TODO("Implement unBumpT")
  }

  /**
   * C++ original:
   * ```cpp
   * void upDepth() {
   *         SkASSERT(++fDepth < 16);
   *     }
   * ```
   */
  public fun upDepth() {
    TODO("Implement upDepth")
  }

  /**
   * C++ original:
   * ```cpp
   * void alignQuadPts(const SkPoint a[3], const SkPoint b[3])
   * ```
   */
  public fun alignQuadPts(a: Array<SkPoint>, b: Array<SkPoint>) {
    TODO("Implement alignQuadPts")
  }

  /**
   * C++ original:
   * ```cpp
   * int cleanUpCoincidence()
   * ```
   */
  public fun cleanUpCoincidence(): Int {
    TODO("Implement cleanUpCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::closestTo(double rangeStart, double rangeEnd, const SkDPoint& testPt,
   *         double* closestDist) const {
   *     int closest = -1;
   *     *closestDist = SK_ScalarMax;
   *     for (int index = 0; index < fUsed; ++index) {
   *         if (!between(rangeStart, fT[0][index], rangeEnd)) {
   *             continue;
   *         }
   *         const SkDPoint& iPt = fPt[index];
   *         double dist = testPt.distanceSquared(iPt);
   *         if (*closestDist > dist) {
   *             *closestDist = dist;
   *             closest = index;
   *         }
   *     }
   *     return closest;
   * }
   * ```
   */
  public fun closestTo(
    rangeStart: Double,
    rangeEnd: Double,
    testPt: SkDPoint,
    dist: Double?,
  ): Int {
    TODO("Implement closestTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void cubicInsert(double one, double two, const SkDPoint& pt, const SkDCubic& c1,
   *                      const SkDCubic& c2)
   * ```
   */
  public fun cubicInsert(
    one: Double,
    two: Double,
    pt: SkDPoint,
    c1: SkDCubic,
    c2: SkDCubic,
  ) {
    TODO("Implement cubicInsert")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::flip() {
   *     for (int index = 0; index < fUsed; ++index) {
   *         fT[1][index] = 1 - fT[1][index];
   *     }
   * }
   * ```
   */
  public fun flip() {
    TODO("Implement flip")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::horizontal(const SkDLine& line, double left, double right,
   *                                 double y, bool flipped) {
   *     fMax = 3;  // clean up parallel at the end will limit the result to 2 at the most
   *     // see if end points intersect the opposite line
   *     double t;
   *     const SkDPoint leftPt = { left, y };
   *     if ((t = line.exactPoint(leftPt)) >= 0) {
   *         insert(t, (double) flipped, leftPt);
   *     }
   *     if (left != right) {
   *         const SkDPoint rightPt = { right, y };
   *         if ((t = line.exactPoint(rightPt)) >= 0) {
   *             insert(t, (double) !flipped, rightPt);
   *         }
   *         for (int index = 0; index < 2; ++index) {
   *             if ((t = SkDLine::ExactPointH(line[index], left, right, y)) >= 0) {
   *                 insert((double) index, flipped ? 1 - t : t, line[index]);
   *             }
   *         }
   *     }
   *     int result = horizontal_coincident(line, y);
   *     if (result == 1 && fUsed == 0) {
   *         fT[0][0] = HorizontalIntercept(line, y);
   *         double xIntercept = line[0].fX + fT[0][0] * (line[1].fX - line[0].fX);
   *         if (between(left, xIntercept, right)) {
   *             fT[1][0] = (xIntercept - left) / (right - left);
   *             if (flipped) {
   *                 // OPTIMIZATION: ? instead of swapping, pass original line, use [1].fX - [0].fX
   *                 for (int index = 0; index < result; ++index) {
   *                     fT[1][index] = 1 - fT[1][index];
   *                 }
   *             }
   *             fPt[0].fX = xIntercept;
   *             fPt[0].fY = y;
   *             fUsed = 1;
   *         }
   *     }
   *     if (fAllowNear || result == 2) {
   *         if ((t = line.nearPoint(leftPt, nullptr)) >= 0) {
   *             insert(t, (double) flipped, leftPt);
   *         }
   *         if (left != right) {
   *             const SkDPoint rightPt = { right, y };
   *             if ((t = line.nearPoint(rightPt, nullptr)) >= 0) {
   *                 insert(t, (double) !flipped, rightPt);
   *             }
   *             for (int index = 0; index < 2; ++index) {
   *                 if ((t = SkDLine::NearPointH(line[index], left, right, y)) >= 0) {
   *                     insert((double) index, flipped ? 1 - t : t, line[index]);
   *                 }
   *             }
   *         }
   *     }
   *     cleanUpParallelLines(result == 2);
   *     return fUsed;
   * }
   * ```
   */
  public fun horizontal(
    line: SkDLine,
    left: Double,
    right: Double,
    y: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::horizontal(const SkDQuad& quad, double left, double right, double y,
   *                                 bool flipped) {
   *     SkDLine line = {{{ left, y }, { right, y }}};
   *     LineQuadraticIntersections q(quad, line, this);
   *     return q.horizontalIntersect(y, left, right, flipped);
   * }
   * ```
   */
  public fun horizontal(
    quad: SkDQuad,
    left: Double,
    right: Double,
    y: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int horizontal(const SkDQuad&, double left, double right, double y, double tRange[2])
   * ```
   */
  public fun horizontal(
    param0: SkDQuad,
    left: Double,
    right: Double,
    y: Double,
    tRange: DoubleArray,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int horizontal(const SkDCubic&, double y, double tRange[3])
   * ```
   */
  public fun horizontal(
    param0: SkDCubic,
    y: Double,
    tRange: DoubleArray,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::horizontal(const SkDConic& conic, double left, double right, double y,
   *                                 bool flipped) {
   *     SkDLine line = {{{ left, y }, { right, y }}};
   *     LineConicIntersections c(conic, line, this);
   *     return c.horizontalIntersect(y, left, right, flipped);
   * }
   * ```
   */
  public fun horizontal(
    conic: SkDConic,
    left: Double,
    right: Double,
    y: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::horizontal(const SkDCubic& cubic, double left, double right, double y,
   *         bool flipped) {
   *     SkDLine line = {{{ left, y }, { right, y }}};
   *     LineCubicIntersections c(cubic, line, this);
   *     return c.horizontalIntersect(y, left, right, flipped);
   * }
   * ```
   */
  public fun horizontal(
    cubic: SkDCubic,
    left: Double,
    right: Double,
    y: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int horizontal(const SkDCubic&, double left, double right, double y, double tRange[3])
   * ```
   */
  public fun horizontal(
    param0: SkDCubic,
    left: Double,
    right: Double,
    y: Double,
    tRange: DoubleArray,
  ): Int {
    TODO("Implement horizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::insert(double one, double two, const SkDPoint& pt) {
   *     if (fIsCoincident[0] == 3 && between(fT[0][0], one, fT[0][1])) {
   *         // For now, don't allow a mix of coincident and non-coincident intersections
   *         return -1;
   *     }
   *     SkASSERT(fUsed <= 1 || fT[0][0] <= fT[0][1]);
   *     int index;
   *     for (index = 0; index < fUsed; ++index) {
   *         double oldOne = fT[0][index];
   *         double oldTwo = fT[1][index];
   *         if (one == oldOne && two == oldTwo) {
   *             return -1;
   *         }
   *         if (more_roughly_equal(oldOne, one) && more_roughly_equal(oldTwo, two)) {
   *             if ((!precisely_zero(one) || precisely_zero(oldOne))
   *                     && (!precisely_equal(one, 1) || precisely_equal(oldOne, 1))
   *                     && (!precisely_zero(two) || precisely_zero(oldTwo))
   *                     && (!precisely_equal(two, 1) || precisely_equal(oldTwo, 1))) {
   *                 return -1;
   *             }
   *             SkASSERT(one >= 0 && one <= 1);
   *             SkASSERT(two >= 0 && two <= 1);
   *             // remove this and reinsert below in case replacing would make list unsorted
   *             int remaining = fUsed - index - 1;
   *             memmove(&fPt[index], &fPt[index + 1], sizeof(fPt[0]) * remaining);
   *             memmove(&fT[0][index], &fT[0][index + 1], sizeof(fT[0][0]) * remaining);
   *             memmove(&fT[1][index], &fT[1][index + 1], sizeof(fT[1][0]) * remaining);
   *             int clearMask = ~((1 << index) - 1);
   *             fIsCoincident[0] -= (fIsCoincident[0] >> 1) & clearMask;
   *             fIsCoincident[1] -= (fIsCoincident[1] >> 1) & clearMask;
   *             --fUsed;
   *             break;
   *         }
   *     #if ONE_OFF_DEBUG
   *         if (pt.roughlyEqual(fPt[index])) {
   *             SkDebugf("%s t=%1.9g pts roughly equal\n", __FUNCTION__, one);
   *         }
   *     #endif
   *     }
   *     for (index = 0; index < fUsed; ++index) {
   *         if (fT[0][index] > one) {
   *             break;
   *         }
   *     }
   *     if (fUsed >= fMax) {
   *         SkOPASSERT(0);  // FIXME : this error, if it is to be handled at runtime in release, must
   *                       // be propagated all the way back down to the caller, and return failure.
   *         fUsed = 0;
   *         return 0;
   *     }
   *     int remaining = fUsed - index;
   *     if (remaining > 0) {
   *         memmove(&fPt[index + 1], &fPt[index], sizeof(fPt[0]) * remaining);
   *         memmove(&fT[0][index + 1], &fT[0][index], sizeof(fT[0][0]) * remaining);
   *         memmove(&fT[1][index + 1], &fT[1][index], sizeof(fT[1][0]) * remaining);
   *         int clearMask = ~((1 << index) - 1);
   *         fIsCoincident[0] += fIsCoincident[0] & clearMask;
   *         fIsCoincident[1] += fIsCoincident[1] & clearMask;
   *     }
   *     fPt[index] = pt;
   *     if (one < 0 || one > 1) {
   *         return -1;
   *     }
   *     if (two < 0 || two > 1) {
   *         return -1;
   *     }
   *     fT[0][index] = one;
   *     fT[1][index] = two;
   *     ++fUsed;
   *     SkASSERT(fUsed <= std::size(fPt));
   *     return index;
   * }
   * ```
   */
  public fun insert(
    one: Double,
    two: Double,
    pt: SkDPoint,
  ): Int {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::insertNear(double one, double two, const SkDPoint& pt1, const SkDPoint& pt2) {
   *     SkASSERT(one == 0 || one == 1);
   *     SkASSERT(two == 0 || two == 1);
   *     SkASSERT(pt1 != pt2);
   *     fNearlySame[one ? 1 : 0] = true;
   *     (void) insert(one, two, pt1);
   *     fPt2[one ? 1 : 0] = pt2;
   * }
   * ```
   */
  public fun insertNear(
    one: Double,
    two: Double,
    pt1: SkDPoint,
    pt2: SkDPoint,
  ) {
    TODO("Implement insertNear")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::insertCoincident(double one, double two, const SkDPoint& pt) {
   *     int index = insertSwap(one, two, pt);
   *     if (index >= 0) {
   *         setCoincident(index);
   *     }
   *     return index;
   * }
   * ```
   */
  public fun insertCoincident(
    one: Double,
    two: Double,
    pt: SkDPoint,
  ): Int {
    TODO("Implement insertCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDLine& a, const SkDLine& b) {
   *     fMax = 3;  // note that we clean up so that there is no more than two in the end
   *     // see if end points intersect the opposite line
   *     double t;
   *     for (int iA = 0; iA < 2; ++iA) {
   *         if ((t = b.exactPoint(a[iA])) >= 0) {
   *             insert(iA, t, a[iA]);
   *         }
   *     }
   *     for (int iB = 0; iB < 2; ++iB) {
   *         if ((t = a.exactPoint(b[iB])) >= 0) {
   *             insert(t, iB, b[iB]);
   *         }
   *     }
   *     /* Determine the intersection point of two line segments
   *        Return FALSE if the lines don't intersect
   *        from: http://paulbourke.net/geometry/lineline2d/ */
   *     double axLen = a[1].fX - a[0].fX;
   *     double ayLen = a[1].fY - a[0].fY;
   *     double bxLen = b[1].fX - b[0].fX;
   *     double byLen = b[1].fY - b[0].fY;
   *     /* Slopes match when denom goes to zero:
   *                       axLen / ayLen ==                   bxLen / byLen
   *     (ayLen * byLen) * axLen / ayLen == (ayLen * byLen) * bxLen / byLen
   *              byLen  * axLen         ==  ayLen          * bxLen
   *              byLen  * axLen         -   ayLen          * bxLen == 0 ( == denom )
   *      */
   *     double axByLen = axLen * byLen;
   *     double ayBxLen = ayLen * bxLen;
   *     // detect parallel lines the same way here and in SkOpAngle operator <
   *     // so that non-parallel means they are also sortable
   *     bool unparallel = fAllowNear ? NotAlmostEqualUlps_Pin(axByLen, ayBxLen)
   *             : NotAlmostDequalUlps(axByLen, ayBxLen);
   *     if (unparallel && fUsed == 0) {
   *         double ab0y = a[0].fY - b[0].fY;
   *         double ab0x = a[0].fX - b[0].fX;
   *         double numerA = ab0y * bxLen - byLen * ab0x;
   *         double numerB = ab0y * axLen - ayLen * ab0x;
   *         double denom = axByLen - ayBxLen;
   *         if (between(0, numerA, denom) && between(0, numerB, denom)) {
   *             fT[0][0] = numerA / denom;
   *             fT[1][0] = numerB / denom;
   *             computePoints(a, 1);
   *         }
   *     }
   * /* Allow tracking that both sets of end points are near each other -- the lines are entirely
   *    coincident -- even when the end points are not exactly the same.
   *    Mark this as a 'wild card' for the end points, so that either point is considered totally
   *    coincident. Then, avoid folding the lines over each other, but allow either end to mate
   *    to the next set of lines.
   *  */
   *     if (fAllowNear || !unparallel) {
   *         double aNearB[2];
   *         double bNearA[2];
   *         bool aNotB[2] = {false, false};
   *         bool bNotA[2] = {false, false};
   *         int nearCount = 0;
   *         for (int index = 0; index < 2; ++index) {
   *             aNearB[index] = t = b.nearPoint(a[index], &aNotB[index]);
   *             nearCount += t >= 0;
   *             bNearA[index] = t = a.nearPoint(b[index], &bNotA[index]);
   *             nearCount += t >= 0;
   *         }
   *         if (nearCount > 0) {
   *             // Skip if each segment contributes to one end point.
   *             if (nearCount != 2 || aNotB[0] == aNotB[1]) {
   *                 for (int iA = 0; iA < 2; ++iA) {
   *                     if (!aNotB[iA]) {
   *                         continue;
   *                     }
   *                     int nearer = aNearB[iA] > 0.5;
   *                     if (!bNotA[nearer]) {
   *                         continue;
   *                     }
   *                     SkASSERT(a[iA] != b[nearer]);
   *                     SkOPASSERT(iA == (bNearA[nearer] > 0.5));
   *                     insertNear(iA, nearer, a[iA], b[nearer]);
   *                     aNearB[iA] = -1;
   *                     bNearA[nearer] = -1;
   *                     nearCount -= 2;
   *                 }
   *             }
   *             if (nearCount > 0) {
   *                 for (int iA = 0; iA < 2; ++iA) {
   *                     if (aNearB[iA] >= 0) {
   *                         insert(iA, aNearB[iA], a[iA]);
   *                     }
   *                 }
   *                 for (int iB = 0; iB < 2; ++iB) {
   *                     if (bNearA[iB] >= 0) {
   *                         insert(bNearA[iB], iB, b[iB]);
   *                     }
   *                 }
   *             }
   *         }
   *     }
   *     cleanUpParallelLines(!unparallel);
   *     SkASSERT(fUsed <= 2);
   *     return fUsed;
   * }
   * ```
   */
  public fun intersect(a: SkDLine, b: SkDLine): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDQuad& quad, const SkDLine& line) {
   *     LineQuadraticIntersections q(quad, line, this);
   *     q.allowNear(fAllowNear);
   *     return q.intersect();
   * }
   * ```
   */
  public fun intersect(quad: SkDQuad, line: SkDLine): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDQuad& q1, const SkDQuad& q2) {
   *     SkTQuad quad1(q1);
   *     SkTQuad quad2(q2);
   *     SkTSect sect1(quad1  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(1));
   *     SkTSect sect2(quad2  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(2));
   *     SkTSect::BinarySearch(&sect1, &sect2, this);
   *     return used();
   * }
   * ```
   */
  public fun intersect(q1: SkDQuad, q2: SkDQuad): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDConic& conic, const SkDLine& line) {
   *     LineConicIntersections c(conic, line, this);
   *     c.allowNear(fAllowNear);
   *     return c.intersect();
   * }
   * ```
   */
  public fun intersect(conic: SkDConic, line: SkDLine): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDConic& c, const SkDQuad& q) {
   *     SkTConic conic(c);
   *     SkTQuad quad(q);
   *     SkTSect sect1(conic  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(1));
   *     SkTSect sect2(quad  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(2));
   *     SkTSect::BinarySearch(&sect1, &sect2, this);
   *     return used();
   * }
   * ```
   */
  public fun intersect(c: SkDConic, q: SkDQuad): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDConic& c1, const SkDConic& c2) {
   *     SkTConic conic1(c1);
   *     SkTConic conic2(c2);
   *     SkTSect sect1(conic1  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(1));
   *     SkTSect sect2(conic2  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(2));
   *     SkTSect::BinarySearch(&sect1, &sect2, this);
   *     return used();
   * }
   * ```
   */
  public fun intersect(c1: SkDConic, c2: SkDConic): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDCubic& cubic, const SkDLine& line) {
   *     LineCubicIntersections c(cubic, line, this);
   *     c.allowNear(fAllowNear);
   *     return c.intersect();
   * }
   * ```
   */
  public fun intersect(cubic: SkDCubic, line: SkDLine): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDCubic& c, const SkDQuad& q) {
   *     SkTCubic cubic(c);
   *     SkTQuad quad(q);
   *     SkTSect sect1(cubic  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(1));
   *     SkTSect sect2(quad  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(2));
   *     SkTSect::BinarySearch(&sect1, &sect2, this);
   *     return used();
   * }
   * ```
   */
  public fun intersect(c: SkDCubic, q: SkDQuad): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDCubic& cu, const SkDConic& co) {
   *     SkTCubic cubic(cu);
   *     SkTConic conic(co);
   *     SkTSect sect1(cubic  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(1));
   *     SkTSect sect2(conic  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(2));
   *     SkTSect::BinarySearch(&sect1, &sect2, this);
   *     return used();
   *
   * }
   * ```
   */
  public fun intersect(cu: SkDCubic, co: SkDConic): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersect(const SkDCubic& c1, const SkDCubic& c2) {
   *     SkTCubic cubic1(c1);
   *     SkTCubic cubic2(c2);
   *     SkTSect sect1(cubic1  SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(1));
   *     SkTSect sect2(cubic2   SkDEBUGPARAMS(globalState())  PATH_OPS_DEBUG_T_SECT_PARAMS(2));
   *     SkTSect::BinarySearch(&sect1, &sect2, this);
   *     return used();
   * }
   * ```
   */
  public fun intersect(c1: SkDCubic, c2: SkDCubic): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersectRay(const SkDLine& a, const SkDLine& b) {
   *     fMax = 2;
   *     SkDVector aLen = a[1] - a[0];
   *     SkDVector bLen = b[1] - b[0];
   *     /* Slopes match when denom goes to zero:
   *                       axLen / ayLen ==                   bxLen / byLen
   *     (ayLen * byLen) * axLen / ayLen == (ayLen * byLen) * bxLen / byLen
   *              byLen  * axLen         ==  ayLen          * bxLen
   *              byLen  * axLen         -   ayLen          * bxLen == 0 ( == denom )
   *      */
   *     double denom = bLen.fY * aLen.fX - aLen.fY * bLen.fX;
   *     int used;
   *     if (!approximately_zero(denom)) {
   *         SkDVector ab0 = a[0] - b[0];
   *         double numerA = ab0.fY * bLen.fX - bLen.fY * ab0.fX;
   *         double numerB = ab0.fY * aLen.fX - aLen.fY * ab0.fX;
   *         numerA /= denom;
   *         numerB /= denom;
   *         fT[0][0] = numerA;
   *         fT[1][0] = numerB;
   *         used = 1;
   *     } else {
   *        /* See if the axis intercepts match:
   *                   ay - ax * ayLen / axLen  ==          by - bx * ayLen / axLen
   *          axLen * (ay - ax * ayLen / axLen) == axLen * (by - bx * ayLen / axLen)
   *          axLen *  ay - ax * ayLen          == axLen *  by - bx * ayLen
   *         */
   *         if (!AlmostEqualUlps(aLen.fX * a[0].fY - aLen.fY * a[0].fX,
   *                 aLen.fX * b[0].fY - aLen.fY * b[0].fX)) {
   *             return fUsed = 0;
   *         }
   *         // there's no great answer for intersection points for coincident rays, but return something
   *         fT[0][0] = fT[1][0] = 0;
   *         fT[1][0] = fT[1][1] = 1;
   *         used = 2;
   *     }
   *     computePoints(a, used);
   *     return fUsed;
   * }
   * ```
   */
  public fun intersectRay(a: SkDLine, b: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersectRay(const SkDQuad& quad, const SkDLine& line) {
   *     LineQuadraticIntersections q(quad, line, this);
   *     fUsed = q.intersectRay(fT[0]);
   *     for (int index = 0; index < fUsed; ++index) {
   *         fPt[index] = quad.ptAtT(fT[0][index]);
   *     }
   *     return fUsed;
   * }
   * ```
   */
  public fun intersectRay(quad: SkDQuad, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersectRay(const SkDConic& conic, const SkDLine& line) {
   *     LineConicIntersections c(conic, line, this);
   *     fUsed = c.intersectRay(fT[0]);
   *     for (int index = 0; index < fUsed; ++index) {
   *         fPt[index] = conic.ptAtT(fT[0][index]);
   *     }
   *     return fUsed;
   * }
   * ```
   */
  public fun intersectRay(conic: SkDConic, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::intersectRay(const SkDCubic& cubic, const SkDLine& line) {
   *     LineCubicIntersections c(cubic, line, this);
   *     fUsed = c.intersectRay(fT[0]);
   *     for (int index = 0; index < fUsed; ++index) {
   *         fPt[index] = cubic.ptAtT(fT[0][index]);
   *     }
   *     return fUsed;
   * }
   * ```
   */
  public fun intersectRay(cubic: SkDCubic, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int intersectRay(const SkTCurve& tCurve, const SkDLine& line) {
   *         return tCurve.intersectRay(this, line);
   *     }
   * ```
   */
  public fun intersectRay(tCurve: SkTCurve, line: SkDLine): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::merge(const SkIntersections& a, int aIndex, const SkIntersections& b,
   *         int bIndex) {
   *     this->reset();
   *     fT[0][0] = a.fT[0][aIndex];
   *     fT[1][0] = b.fT[0][bIndex];
   *     fPt[0] = a.fPt[aIndex];
   *     fPt2[0] = b.fPt[bIndex];
   *     fUsed = 1;
   * }
   * ```
   */
  public fun merge(
    a: SkIntersections,
    aIndex: Int,
    b: SkIntersections,
    bIndex: Int,
  ) {
    TODO("Implement merge")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::mostOutside(double rangeStart, double rangeEnd, const SkDPoint& origin) const {
   *     int result = -1;
   *     for (int index = 0; index < fUsed; ++index) {
   *         if (!between(rangeStart, fT[0][index], rangeEnd)) {
   *             continue;
   *         }
   *         if (result < 0) {
   *             result = index;
   *             continue;
   *         }
   *         SkDVector best = fPt[result] - origin;
   *         SkDVector test = fPt[index] - origin;
   *         if (test.crossCheck(best) < 0) {
   *             result = index;
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  public fun mostOutside(
    rangeStart: Double,
    rangeEnd: Double,
    origin: SkDPoint,
  ): Int {
    TODO("Implement mostOutside")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::removeOne(int index) {
   *     int remaining = --fUsed - index;
   *     if (remaining <= 0) {
   *         return;
   *     }
   *     memmove(&fPt[index], &fPt[index + 1], sizeof(fPt[0]) * remaining);
   *     memmove(&fT[0][index], &fT[0][index + 1], sizeof(fT[0][0]) * remaining);
   *     memmove(&fT[1][index], &fT[1][index + 1], sizeof(fT[1][0]) * remaining);
   * //    SkASSERT(fIsCoincident[0] == 0);
   *     int coBit = fIsCoincident[0] & (1 << index);
   *     fIsCoincident[0] -= ((fIsCoincident[0] >> 1) & ~((1 << index) - 1)) + coBit;
   *     SkASSERT(!(coBit ^ (fIsCoincident[1] & (1 << index))));
   *     fIsCoincident[1] -= ((fIsCoincident[1] >> 1) & ~((1 << index) - 1)) + coBit;
   * }
   * ```
   */
  public fun removeOne(index: Int) {
    TODO("Implement removeOne")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::setCoincident(int index) {
   *     SkASSERT(index >= 0);
   *     int bit = 1 << index;
   *     fIsCoincident[0] |= bit;
   *     fIsCoincident[1] |= bit;
   * }
   * ```
   */
  public fun setCoincident(index: Int) {
    TODO("Implement setCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::vertical(const SkDLine& line, double top, double bottom,
   *                               double x, bool flipped) {
   *     fMax = 3;  // cleanup parallel lines will bring this back line
   *     // see if end points intersect the opposite line
   *     double t;
   *     SkDPoint topPt = { x, top };
   *     if ((t = line.exactPoint(topPt)) >= 0) {
   *         insert(t, (double) flipped, topPt);
   *     }
   *     if (top != bottom) {
   *         SkDPoint bottomPt = { x, bottom };
   *         if ((t = line.exactPoint(bottomPt)) >= 0) {
   *             insert(t, (double) !flipped, bottomPt);
   *         }
   *         for (int index = 0; index < 2; ++index) {
   *             if ((t = SkDLine::ExactPointV(line[index], top, bottom, x)) >= 0) {
   *                 insert((double) index, flipped ? 1 - t : t, line[index]);
   *             }
   *         }
   *     }
   *     int result = vertical_coincident(line, x);
   *     if (result == 1 && fUsed == 0) {
   *         fT[0][0] = VerticalIntercept(line, x);
   *         double yIntercept = line[0].fY + fT[0][0] * (line[1].fY - line[0].fY);
   *         if (between(top, yIntercept, bottom)) {
   *             fT[1][0] = (yIntercept - top) / (bottom - top);
   *             if (flipped) {
   *                 // OPTIMIZATION: instead of swapping, pass original line, use [1].fY - [0].fY
   *                 for (int index = 0; index < result; ++index) {
   *                     fT[1][index] = 1 - fT[1][index];
   *                 }
   *             }
   *             fPt[0].fX = x;
   *             fPt[0].fY = yIntercept;
   *             fUsed = 1;
   *         }
   *     }
   *     if (fAllowNear || result == 2) {
   *         if ((t = line.nearPoint(topPt, nullptr)) >= 0) {
   *             insert(t, (double) flipped, topPt);
   *         }
   *         if (top != bottom) {
   *             SkDPoint bottomPt = { x, bottom };
   *             if ((t = line.nearPoint(bottomPt, nullptr)) >= 0) {
   *                 insert(t, (double) !flipped, bottomPt);
   *             }
   *             for (int index = 0; index < 2; ++index) {
   *                 if ((t = SkDLine::NearPointV(line[index], top, bottom, x)) >= 0) {
   *                     insert((double) index, flipped ? 1 - t : t, line[index]);
   *                 }
   *             }
   *         }
   *     }
   *     cleanUpParallelLines(result == 2);
   *     SkASSERT(fUsed <= 2);
   *     return fUsed;
   * }
   * ```
   */
  public fun vertical(
    line: SkDLine,
    top: Double,
    bottom: Double,
    x: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement vertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::vertical(const SkDQuad& quad, double top, double bottom, double x,
   *                               bool flipped) {
   *     SkDLine line = {{{ x, top }, { x, bottom }}};
   *     LineQuadraticIntersections q(quad, line, this);
   *     return q.verticalIntersect(x, top, bottom, flipped);
   * }
   * ```
   */
  public fun vertical(
    quad: SkDQuad,
    top: Double,
    bottom: Double,
    x: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement vertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::vertical(const SkDConic& conic, double top, double bottom, double x,
   *                               bool flipped) {
   *     SkDLine line = {{{ x, top }, { x, bottom }}};
   *     LineConicIntersections c(conic, line, this);
   *     return c.verticalIntersect(x, top, bottom, flipped);
   * }
   * ```
   */
  public fun vertical(
    conic: SkDConic,
    top: Double,
    bottom: Double,
    x: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement vertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::vertical(const SkDCubic& cubic, double top, double bottom, double x,
   *         bool flipped) {
   *     SkDLine line = {{{ x, top }, { x, bottom }}};
   *     LineCubicIntersections c(cubic, line, this);
   *     return c.verticalIntersect(x, top, bottom, flipped);
   * }
   * ```
   */
  public fun vertical(
    cubic: SkDCubic,
    top: Double,
    bottom: Double,
    x: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement vertical")
  }

  /**
   * C++ original:
   * ```cpp
   * int depth() const {
   * #ifdef SK_DEBUG
   *         return fDepth;
   * #else
   *         return 0;
   * #endif
   *     }
   * ```
   */
  public fun depth(): Int {
    TODO("Implement depth")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugBumpLoopCount(DebugLoop )
   * ```
   */
  public fun debugBumpLoopCount(param0: DebugLoop) {
    TODO("Implement debugBumpLoopCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkIntersections::debugCoincidentUsed() const {
   *     if (!fIsCoincident[0]) {
   *         SkASSERT(!fIsCoincident[1]);
   *         return 0;
   *     }
   *     int count = 0;
   *     SkDEBUGCODE(int count2 = 0;)
   *     for (int index = 0; index < fUsed; ++index) {
   *         if (fIsCoincident[0] & (1 << index)) {
   *             ++count;
   *         }
   * #ifdef SK_DEBUG
   *         if (fIsCoincident[1] & (1 << index)) {
   *             ++count2;
   *         }
   * #endif
   *     }
   *     SkASSERT(count == count2);
   *     return count;
   * }
   * ```
   */
  public fun debugCoincidentUsed(): Int {
    TODO("Implement debugCoincidentUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * int debugLoopCount(DebugLoop ) const
   * ```
   */
  public fun debugLoopCount(param0: DebugLoop): Int {
    TODO("Implement debugLoopCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugResetLoopCount()
   * ```
   */
  public fun debugResetLoopCount() {
    TODO("Implement debugResetLoopCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::dump() const {
   *     SkDebugf("used=%d of %d", fUsed, fMax);
   *     for (int index = 0; index < fUsed; ++index) {
   *         SkDebugf(" t=(%s%1.9g,%s%1.9g) pt=(%1.9g,%1.9g)",
   *                 fIsCoincident[0] & (1 << index) ? "*" : "", fT[0][index],
   *                 fIsCoincident[1] & (1 << index) ? "*" : "", fT[1][index],
   *                 fPt[index].fX, fPt[index].fY);
   *         if (index < 2 && fNearlySame[index]) {
   *             SkDebugf(" pt2=(%1.9g,%1.9g)",fPt2[index].fX, fPt2[index].fY);
   *         }
   *     }
   *     SkDebugf("\n");
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * bool cubicCheckCoincidence(const SkDCubic& c1, const SkDCubic& c2)
   * ```
   */
  private fun cubicCheckCoincidence(c1: SkDCubic, c2: SkDCubic): Boolean {
    TODO("Implement cubicCheckCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool cubicExactEnd(const SkDCubic& cubic1, bool start, const SkDCubic& cubic2)
   * ```
   */
  private fun cubicExactEnd(
    cubic1: SkDCubic,
    start: Boolean,
    cubic2: SkDCubic,
  ): Boolean {
    TODO("Implement cubicExactEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * void cubicNearEnd(const SkDCubic& cubic1, bool start, const SkDCubic& cubic2, const SkDRect& )
   * ```
   */
  private fun cubicNearEnd(
    cubic1: SkDCubic,
    start: Boolean,
    cubic2: SkDCubic,
    param3: SkDRect,
  ) {
    TODO("Implement cubicNearEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::cleanUpParallelLines(bool parallel) {
   *     while (fUsed > 2) {
   *         removeOne(1);
   *     }
   *     if (fUsed == 2 && !parallel) {
   *         bool startMatch = fT[0][0] == 0 || zero_or_one(fT[1][0]);
   *         bool endMatch = fT[0][1] == 1 || zero_or_one(fT[1][1]);
   *         if ((!startMatch && !endMatch) || approximately_equal(fT[0][0], fT[0][1])) {
   *             SkASSERT(startMatch || endMatch);
   *             if (startMatch && endMatch && (fT[0][0] != 0 || !zero_or_one(fT[1][0]))
   *                     && fT[0][1] == 1 && zero_or_one(fT[1][1])) {
   *                 removeOne(0);
   *             } else {
   *                 removeOne(endMatch);
   *             }
   *         }
   *     }
   *     if (fUsed == 2) {
   *         fIsCoincident[0] = fIsCoincident[1] = 0x03;
   *     }
   * }
   * ```
   */
  private fun cleanUpParallelLines(parallel: Boolean) {
    TODO("Implement cleanUpParallelLines")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkIntersections::computePoints(const SkDLine& line, int used) {
   *     fPt[0] = line.ptAtT(fT[0][0]);
   *     if ((fUsed = used) == 2) {
   *         fPt[1] = line.ptAtT(fT[0][1]);
   *     }
   * }
   * ```
   */
  private fun computePoints(line: SkDLine, used: Int) {
    TODO("Implement computePoints")
  }

  public data class TArray public constructor(
    public val fTArray: Double?,
  ) {
    public operator fun `get`(n: Int): Double {
      TODO("Implement get")
    }
  }

  public enum class DebugLoop {
    kIterations_DebugLoop,
    kCoinCheck_DebugLoop,
    kComputePerp_DebugLoop,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * double SkIntersections::HorizontalIntercept(const SkDLine& line, double y) {
     *     SkASSERT(line[1].fY != line[0].fY);
     *     return SkPinT((y - line[0].fY) / (line[1].fY - line[0].fY));
     * }
     * ```
     */
    public fun horizontalIntercept(line: SkDLine, y: Double): Double {
      TODO("Implement horizontalIntercept")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkIntersections::HorizontalIntercept(const SkDQuad& quad, SkScalar y, double* roots) {
     *     LineQuadraticIntersections q(quad);
     *     return q.horizontalIntersect(y, roots);
     * }
     * ```
     */
    public fun horizontalIntercept(
      quad: SkDQuad,
      y: SkScalar,
      roots: Double?,
    ): Int {
      TODO("Implement horizontalIntercept")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkIntersections::HorizontalIntercept(const SkDConic& conic, SkScalar y, double* roots) {
     *     LineConicIntersections c(conic);
     *     return c.horizontalIntersect(y, roots);
     * }
     * ```
     */
    public fun horizontalIntercept(
      conic: SkDConic,
      y: SkScalar,
      roots: Double?,
    ): Int {
      TODO("Implement horizontalIntercept")
    }

    /**
     * C++ original:
     * ```cpp
     * double SkIntersections::VerticalIntercept(const SkDLine& line, double x) {
     *     SkASSERT(line[1].fX != line[0].fX);
     *     return SkPinT((x - line[0].fX) / (line[1].fX - line[0].fX));
     * }
     * ```
     */
    public fun verticalIntercept(line: SkDLine, x: Double): Double {
      TODO("Implement verticalIntercept")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkIntersections::VerticalIntercept(const SkDQuad& quad, SkScalar x, double* roots) {
     *     LineQuadraticIntersections q(quad);
     *     return q.verticalIntersect(x, roots);
     * }
     * ```
     */
    public fun verticalIntercept(
      quad: SkDQuad,
      x: SkScalar,
      roots: Double?,
    ): Int {
      TODO("Implement verticalIntercept")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkIntersections::VerticalIntercept(const SkDConic& conic, SkScalar x, double* roots) {
     *     LineConicIntersections c(conic);
     *     return c.verticalIntersect(x, roots);
     * }
     * ```
     */
    public fun verticalIntercept(
      conic: SkDConic,
      x: SkScalar,
      roots: Double?,
    ): Int {
      TODO("Implement verticalIntercept")
    }
  }
}
