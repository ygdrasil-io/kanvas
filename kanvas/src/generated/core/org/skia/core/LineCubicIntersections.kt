package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class LineCubicIntersections {
 * public:
 *     enum PinTPoint {
 *         kPointUninitialized,
 *         kPointInitialized
 *     };
 *
 *     LineCubicIntersections(const SkDCubic& c, const SkDLine& l, SkIntersections* i)
 *         : fCubic(c)
 *         , fLine(l)
 *         , fIntersections(i)
 *         , fAllowNear(true) {
 *         i->setMax(4);
 *     }
 *
 *     void allowNear(bool allow) {
 *         fAllowNear = allow;
 *     }
 *
 *     void checkCoincident() {
 *         int last = fIntersections->used() - 1;
 *         for (int index = 0; index < last; ) {
 *             double cubicMidT = ((*fIntersections)[0][index] + (*fIntersections)[0][index + 1]) / 2;
 *             SkDPoint cubicMidPt = fCubic.ptAtT(cubicMidT);
 *             double t = fLine.nearPoint(cubicMidPt, nullptr);
 *             if (t < 0) {
 *                 ++index;
 *                 continue;
 *             }
 *             if (fIntersections->isCoincident(index)) {
 *                 fIntersections->removeOne(index);
 *                 --last;
 *             } else if (fIntersections->isCoincident(index + 1)) {
 *                 fIntersections->removeOne(index + 1);
 *                 --last;
 *             } else {
 *                 fIntersections->setCoincident(index++);
 *             }
 *             fIntersections->setCoincident(index);
 *         }
 *     }
 *
 *     // see parallel routine in line quadratic intersections
 *     int intersectRay(double roots[3]) {
 *         double adj = fLine[1].fX - fLine[0].fX;
 *         double opp = fLine[1].fY - fLine[0].fY;
 *         SkDCubic c;
 *         SkDEBUGCODE(c.fDebugGlobalState = fIntersections->globalState());
 *         for (int n = 0; n < 4; ++n) {
 *             c[n].fX = (fCubic[n].fY - fLine[0].fY) * adj - (fCubic[n].fX - fLine[0].fX) * opp;
 *         }
 *         double A, B, C, D;
 *         SkDCubic::Coefficients(&c[0].fX, &A, &B, &C, &D);
 *         int count = SkDCubic::RootsValidT(A, B, C, D, roots);
 *         for (int index = 0; index < count; ++index) {
 *             SkDPoint calcPt = c.ptAtT(roots[index]);
 *             if (!approximately_zero(calcPt.fX)) {
 *                 for (int n = 0; n < 4; ++n) {
 *                     c[n].fY = (fCubic[n].fY - fLine[0].fY) * opp
 *                             + (fCubic[n].fX - fLine[0].fX) * adj;
 *                 }
 *                 double extremeTs[6];
 *                 int extrema = SkDCubic::FindExtrema(&c[0].fX, extremeTs);
 *                 count = c.searchRoots(extremeTs, extrema, 0, SkDCubic::kXAxis, roots);
 *                 break;
 *             }
 *         }
 *         return count;
 *     }
 *
 *     int intersect() {
 *         addExactEndPoints();
 *         if (fAllowNear) {
 *             addNearEndPoints();
 *         }
 *         double rootVals[3];
 *         int roots = intersectRay(rootVals);
 *         for (int index = 0; index < roots; ++index) {
 *             double cubicT = rootVals[index];
 *             double lineT = findLineT(cubicT);
 *             SkDPoint pt;
 *             if (pinTs(&cubicT, &lineT, &pt, kPointUninitialized) && uniqueAnswer(cubicT, pt)) {
 *                 fIntersections->insert(cubicT, lineT, pt);
 *             }
 *         }
 *         checkCoincident();
 *         return fIntersections->used();
 *     }
 *
 *     static int HorizontalIntersect(const SkDCubic& c, double axisIntercept, double roots[3]) {
 *         double A, B, C, D;
 *         SkDCubic::Coefficients(&c[0].fY, &A, &B, &C, &D);
 *         D -= axisIntercept;
 *         int count = SkDCubic::RootsValidT(A, B, C, D, roots);
 *         for (int index = 0; index < count; ++index) {
 *             SkDPoint calcPt = c.ptAtT(roots[index]);
 *             if (!approximately_equal(calcPt.fY, axisIntercept)) {
 *                 double extremeTs[6];
 *                 int extrema = SkDCubic::FindExtrema(&c[0].fY, extremeTs);
 *                 count = c.searchRoots(extremeTs, extrema, axisIntercept, SkDCubic::kYAxis, roots);
 *                 break;
 *             }
 *         }
 *         return count;
 *     }
 *
 *     int horizontalIntersect(double axisIntercept, double left, double right, bool flipped) {
 *         addExactHorizontalEndPoints(left, right, axisIntercept);
 *         if (fAllowNear) {
 *             addNearHorizontalEndPoints(left, right, axisIntercept);
 *         }
 *         double roots[3];
 *         int count = HorizontalIntersect(fCubic, axisIntercept, roots);
 *         for (int index = 0; index < count; ++index) {
 *             double cubicT = roots[index];
 *             SkDPoint pt = { fCubic.ptAtT(cubicT).fX,  axisIntercept };
 *             double lineT = (pt.fX - left) / (right - left);
 *             if (pinTs(&cubicT, &lineT, &pt, kPointInitialized) && uniqueAnswer(cubicT, pt)) {
 *                 fIntersections->insert(cubicT, lineT, pt);
 *             }
 *         }
 *         if (flipped) {
 *             fIntersections->flip();
 *         }
 *         checkCoincident();
 *         return fIntersections->used();
 *     }
 *
 *         bool uniqueAnswer(double cubicT, const SkDPoint& pt) {
 *             for (int inner = 0; inner < fIntersections->used(); ++inner) {
 *                 if (fIntersections->pt(inner) != pt) {
 *                     continue;
 *                 }
 *                 double existingCubicT = (*fIntersections)[0][inner];
 *                 if (cubicT == existingCubicT) {
 *                     return false;
 *                 }
 *                 // check if midway on cubic is also same point. If so, discard this
 *                 double cubicMidT = (existingCubicT + cubicT) / 2;
 *                 SkDPoint cubicMidPt = fCubic.ptAtT(cubicMidT);
 *                 if (cubicMidPt.approximatelyEqual(pt)) {
 *                     return false;
 *                 }
 *             }
 * #if ONE_OFF_DEBUG
 *             SkDPoint cPt = fCubic.ptAtT(cubicT);
 *             SkDebugf("%s pt=(%1.9g,%1.9g) cPt=(%1.9g,%1.9g)\n", __FUNCTION__, pt.fX, pt.fY,
 *                     cPt.fX, cPt.fY);
 * #endif
 *             return true;
 *         }
 *
 *     static int VerticalIntersect(const SkDCubic& c, double axisIntercept, double roots[3]) {
 *         double A, B, C, D;
 *         SkDCubic::Coefficients(&c[0].fX, &A, &B, &C, &D);
 *         D -= axisIntercept;
 *         int count = SkDCubic::RootsValidT(A, B, C, D, roots);
 *         for (int index = 0; index < count; ++index) {
 *             SkDPoint calcPt = c.ptAtT(roots[index]);
 *             if (!approximately_equal(calcPt.fX, axisIntercept)) {
 *                 double extremeTs[6];
 *                 int extrema = SkDCubic::FindExtrema(&c[0].fX, extremeTs);
 *                 count = c.searchRoots(extremeTs, extrema, axisIntercept, SkDCubic::kXAxis, roots);
 *                 break;
 *             }
 *         }
 *         return count;
 *     }
 *
 *     int verticalIntersect(double axisIntercept, double top, double bottom, bool flipped) {
 *         addExactVerticalEndPoints(top, bottom, axisIntercept);
 *         if (fAllowNear) {
 *             addNearVerticalEndPoints(top, bottom, axisIntercept);
 *         }
 *         double roots[3];
 *         int count = VerticalIntersect(fCubic, axisIntercept, roots);
 *         for (int index = 0; index < count; ++index) {
 *             double cubicT = roots[index];
 *             SkDPoint pt = { axisIntercept, fCubic.ptAtT(cubicT).fY };
 *             double lineT = (pt.fY - top) / (bottom - top);
 *             if (pinTs(&cubicT, &lineT, &pt, kPointInitialized) && uniqueAnswer(cubicT, pt)) {
 *                 fIntersections->insert(cubicT, lineT, pt);
 *             }
 *         }
 *         if (flipped) {
 *             fIntersections->flip();
 *         }
 *         checkCoincident();
 *         return fIntersections->used();
 *     }
 *
 *     protected:
 *
 *     void addExactEndPoints() {
 *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
 *             double lineT = fLine.exactPoint(fCubic[cIndex]);
 *             if (lineT < 0) {
 *                 continue;
 *             }
 *             double cubicT = (double) (cIndex >> 1);
 *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
 *         }
 *     }
 *
 *     /* Note that this does not look for endpoints of the line that are near the cubic.
 *        These points are found later when check ends looks for missing points */
 *     void addNearEndPoints() {
 *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
 *             double cubicT = (double) (cIndex >> 1);
 *             if (fIntersections->hasT(cubicT)) {
 *                 continue;
 *             }
 *             double lineT = fLine.nearPoint(fCubic[cIndex], nullptr);
 *             if (lineT < 0) {
 *                 continue;
 *             }
 *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
 *         }
 *         this->addLineNearEndPoints();
 *     }
 *
 *     void addLineNearEndPoints() {
 *         for (int lIndex = 0; lIndex < 2; ++lIndex) {
 *             double lineT = (double) lIndex;
 *             if (fIntersections->hasOppT(lineT)) {
 *                 continue;
 *             }
 *             double cubicT = ((const SkDCurve*)&fCubic)
 *                                     ->nearPoint(SkPath::kCubic_Verb, fLine[lIndex], fLine[!lIndex]);
 *             if (cubicT < 0) {
 *                 continue;
 *             }
 *             fIntersections->insert(cubicT, lineT, fLine[lIndex]);
 *         }
 *     }
 *
 *     void addExactHorizontalEndPoints(double left, double right, double y) {
 *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
 *             double lineT = SkDLine::ExactPointH(fCubic[cIndex], left, right, y);
 *             if (lineT < 0) {
 *                 continue;
 *             }
 *             double cubicT = (double) (cIndex >> 1);
 *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
 *         }
 *     }
 *
 *     void addNearHorizontalEndPoints(double left, double right, double y) {
 *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
 *             double cubicT = (double) (cIndex >> 1);
 *             if (fIntersections->hasT(cubicT)) {
 *                 continue;
 *             }
 *             double lineT = SkDLine::NearPointH(fCubic[cIndex], left, right, y);
 *             if (lineT < 0) {
 *                 continue;
 *             }
 *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
 *         }
 *         this->addLineNearEndPoints();
 *     }
 *
 *     void addExactVerticalEndPoints(double top, double bottom, double x) {
 *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
 *             double lineT = SkDLine::ExactPointV(fCubic[cIndex], top, bottom, x);
 *             if (lineT < 0) {
 *                 continue;
 *             }
 *             double cubicT = (double) (cIndex >> 1);
 *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
 *         }
 *     }
 *
 *     void addNearVerticalEndPoints(double top, double bottom, double x) {
 *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
 *             double cubicT = (double) (cIndex >> 1);
 *             if (fIntersections->hasT(cubicT)) {
 *                 continue;
 *             }
 *             double lineT = SkDLine::NearPointV(fCubic[cIndex], top, bottom, x);
 *             if (lineT < 0) {
 *                 continue;
 *             }
 *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
 *         }
 *         this->addLineNearEndPoints();
 *     }
 *
 *     double findLineT(double t) {
 *         SkDPoint xy = fCubic.ptAtT(t);
 *         double dx = fLine[1].fX - fLine[0].fX;
 *         double dy = fLine[1].fY - fLine[0].fY;
 *         if (fabs(dx) > fabs(dy)) {
 *             return (xy.fX - fLine[0].fX) / dx;
 *         }
 *         return (xy.fY - fLine[0].fY) / dy;
 *     }
 *
 *     bool pinTs(double* cubicT, double* lineT, SkDPoint* pt, PinTPoint ptSet) {
 *         if (!approximately_one_or_less(*lineT)) {
 *             return false;
 *         }
 *         if (!approximately_zero_or_more(*lineT)) {
 *             return false;
 *         }
 *         double cT = *cubicT = SkPinT(*cubicT);
 *         double lT = *lineT = SkPinT(*lineT);
 *         SkDPoint lPt = fLine.ptAtT(lT);
 *         SkDPoint cPt = fCubic.ptAtT(cT);
 *         if (!lPt.roughlyEqual(cPt)) {
 *             return false;
 *         }
 *         // FIXME: if points are roughly equal but not approximately equal, need to do
 *         // a binary search like quad/quad intersection to find more precise t values
 *         if (lT == 0 || lT == 1 || (ptSet == kPointUninitialized && cT != 0 && cT != 1)) {
 *             *pt = lPt;
 *         } else if (ptSet == kPointUninitialized) {
 *             *pt = cPt;
 *         }
 *         SkPoint gridPt = pt->asSkPoint();
 *         if (gridPt == fLine[0].asSkPoint()) {
 *             *lineT = 0;
 *         } else if (gridPt == fLine[1].asSkPoint()) {
 *             *lineT = 1;
 *         }
 *         if (gridPt == fCubic[0].asSkPoint() && approximately_equal(*cubicT, 0)) {
 *             *cubicT = 0;
 *         } else if (gridPt == fCubic[3].asSkPoint() && approximately_equal(*cubicT, 1)) {
 *             *cubicT = 1;
 *         }
 *         return true;
 *     }
 *
 * private:
 *     const SkDCubic& fCubic;
 *     const SkDLine& fLine;
 *     SkIntersections* fIntersections;
 *     bool fAllowNear;
 * }
 * ```
 */
public data class LineCubicIntersections public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkDCubic& fCubic
   * ```
   */
  private val fCubic: SkDCubic,
  /**
   * C++ original:
   * ```cpp
   * const SkDLine& fLine
   * ```
   */
  private val fLine: SkDLine,
  /**
   * C++ original:
   * ```cpp
   * SkIntersections* fIntersections
   * ```
   */
  private var fIntersections: SkIntersections?,
  /**
   * C++ original:
   * ```cpp
   * bool fAllowNear
   * ```
   */
  private var fAllowNear: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void allowNear(bool allow) {
   *         fAllowNear = allow;
   *     }
   * ```
   */
  public fun allowNear(allow: Boolean) {
    TODO("Implement allowNear")
  }

  /**
   * C++ original:
   * ```cpp
   * void checkCoincident() {
   *         int last = fIntersections->used() - 1;
   *         for (int index = 0; index < last; ) {
   *             double cubicMidT = ((*fIntersections)[0][index] + (*fIntersections)[0][index + 1]) / 2;
   *             SkDPoint cubicMidPt = fCubic.ptAtT(cubicMidT);
   *             double t = fLine.nearPoint(cubicMidPt, nullptr);
   *             if (t < 0) {
   *                 ++index;
   *                 continue;
   *             }
   *             if (fIntersections->isCoincident(index)) {
   *                 fIntersections->removeOne(index);
   *                 --last;
   *             } else if (fIntersections->isCoincident(index + 1)) {
   *                 fIntersections->removeOne(index + 1);
   *                 --last;
   *             } else {
   *                 fIntersections->setCoincident(index++);
   *             }
   *             fIntersections->setCoincident(index);
   *         }
   *     }
   * ```
   */
  public fun checkCoincident() {
    TODO("Implement checkCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * int intersectRay(double roots[3]) {
   *         double adj = fLine[1].fX - fLine[0].fX;
   *         double opp = fLine[1].fY - fLine[0].fY;
   *         SkDCubic c;
   *         SkDEBUGCODE(c.fDebugGlobalState = fIntersections->globalState());
   *         for (int n = 0; n < 4; ++n) {
   *             c[n].fX = (fCubic[n].fY - fLine[0].fY) * adj - (fCubic[n].fX - fLine[0].fX) * opp;
   *         }
   *         double A, B, C, D;
   *         SkDCubic::Coefficients(&c[0].fX, &A, &B, &C, &D);
   *         int count = SkDCubic::RootsValidT(A, B, C, D, roots);
   *         for (int index = 0; index < count; ++index) {
   *             SkDPoint calcPt = c.ptAtT(roots[index]);
   *             if (!approximately_zero(calcPt.fX)) {
   *                 for (int n = 0; n < 4; ++n) {
   *                     c[n].fY = (fCubic[n].fY - fLine[0].fY) * opp
   *                             + (fCubic[n].fX - fLine[0].fX) * adj;
   *                 }
   *                 double extremeTs[6];
   *                 int extrema = SkDCubic::FindExtrema(&c[0].fX, extremeTs);
   *                 count = c.searchRoots(extremeTs, extrema, 0, SkDCubic::kXAxis, roots);
   *                 break;
   *             }
   *         }
   *         return count;
   *     }
   * ```
   */
  public fun intersectRay(roots: DoubleArray): Int {
    TODO("Implement intersectRay")
  }

  /**
   * C++ original:
   * ```cpp
   * int intersect() {
   *         addExactEndPoints();
   *         if (fAllowNear) {
   *             addNearEndPoints();
   *         }
   *         double rootVals[3];
   *         int roots = intersectRay(rootVals);
   *         for (int index = 0; index < roots; ++index) {
   *             double cubicT = rootVals[index];
   *             double lineT = findLineT(cubicT);
   *             SkDPoint pt;
   *             if (pinTs(&cubicT, &lineT, &pt, kPointUninitialized) && uniqueAnswer(cubicT, pt)) {
   *                 fIntersections->insert(cubicT, lineT, pt);
   *             }
   *         }
   *         checkCoincident();
   *         return fIntersections->used();
   *     }
   * ```
   */
  public fun intersect(): Int {
    TODO("Implement intersect")
  }

  /**
   * C++ original:
   * ```cpp
   * int horizontalIntersect(double axisIntercept, double left, double right, bool flipped) {
   *         addExactHorizontalEndPoints(left, right, axisIntercept);
   *         if (fAllowNear) {
   *             addNearHorizontalEndPoints(left, right, axisIntercept);
   *         }
   *         double roots[3];
   *         int count = HorizontalIntersect(fCubic, axisIntercept, roots);
   *         for (int index = 0; index < count; ++index) {
   *             double cubicT = roots[index];
   *             SkDPoint pt = { fCubic.ptAtT(cubicT).fX,  axisIntercept };
   *             double lineT = (pt.fX - left) / (right - left);
   *             if (pinTs(&cubicT, &lineT, &pt, kPointInitialized) && uniqueAnswer(cubicT, pt)) {
   *                 fIntersections->insert(cubicT, lineT, pt);
   *             }
   *         }
   *         if (flipped) {
   *             fIntersections->flip();
   *         }
   *         checkCoincident();
   *         return fIntersections->used();
   *     }
   * ```
   */
  public fun horizontalIntersect(
    axisIntercept: Double,
    left: Double,
    right: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement horizontalIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool uniqueAnswer(double cubicT, const SkDPoint& pt) {
   *             for (int inner = 0; inner < fIntersections->used(); ++inner) {
   *                 if (fIntersections->pt(inner) != pt) {
   *                     continue;
   *                 }
   *                 double existingCubicT = (*fIntersections)[0][inner];
   *                 if (cubicT == existingCubicT) {
   *                     return false;
   *                 }
   *                 // check if midway on cubic is also same point. If so, discard this
   *                 double cubicMidT = (existingCubicT + cubicT) / 2;
   *                 SkDPoint cubicMidPt = fCubic.ptAtT(cubicMidT);
   *                 if (cubicMidPt.approximatelyEqual(pt)) {
   *                     return false;
   *                 }
   *             }
   * #if ONE_OFF_DEBUG
   *             SkDPoint cPt = fCubic.ptAtT(cubicT);
   *             SkDebugf("%s pt=(%1.9g,%1.9g) cPt=(%1.9g,%1.9g)\n", __FUNCTION__, pt.fX, pt.fY,
   *                     cPt.fX, cPt.fY);
   * #endif
   *             return true;
   *         }
   * ```
   */
  public fun uniqueAnswer(cubicT: Double, pt: SkDPoint): Boolean {
    TODO("Implement uniqueAnswer")
  }

  /**
   * C++ original:
   * ```cpp
   * int verticalIntersect(double axisIntercept, double top, double bottom, bool flipped) {
   *         addExactVerticalEndPoints(top, bottom, axisIntercept);
   *         if (fAllowNear) {
   *             addNearVerticalEndPoints(top, bottom, axisIntercept);
   *         }
   *         double roots[3];
   *         int count = VerticalIntersect(fCubic, axisIntercept, roots);
   *         for (int index = 0; index < count; ++index) {
   *             double cubicT = roots[index];
   *             SkDPoint pt = { axisIntercept, fCubic.ptAtT(cubicT).fY };
   *             double lineT = (pt.fY - top) / (bottom - top);
   *             if (pinTs(&cubicT, &lineT, &pt, kPointInitialized) && uniqueAnswer(cubicT, pt)) {
   *                 fIntersections->insert(cubicT, lineT, pt);
   *             }
   *         }
   *         if (flipped) {
   *             fIntersections->flip();
   *         }
   *         checkCoincident();
   *         return fIntersections->used();
   *     }
   * ```
   */
  public fun verticalIntersect(
    axisIntercept: Double,
    top: Double,
    bottom: Double,
    flipped: Boolean,
  ): Int {
    TODO("Implement verticalIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * void addExactEndPoints() {
   *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
   *             double lineT = fLine.exactPoint(fCubic[cIndex]);
   *             if (lineT < 0) {
   *                 continue;
   *             }
   *             double cubicT = (double) (cIndex >> 1);
   *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
   *         }
   *     }
   * ```
   */
  protected fun addExactEndPoints() {
    TODO("Implement addExactEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void addNearEndPoints() {
   *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
   *             double cubicT = (double) (cIndex >> 1);
   *             if (fIntersections->hasT(cubicT)) {
   *                 continue;
   *             }
   *             double lineT = fLine.nearPoint(fCubic[cIndex], nullptr);
   *             if (lineT < 0) {
   *                 continue;
   *             }
   *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
   *         }
   *         this->addLineNearEndPoints();
   *     }
   * ```
   */
  protected fun addNearEndPoints() {
    TODO("Implement addNearEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void addLineNearEndPoints() {
   *         for (int lIndex = 0; lIndex < 2; ++lIndex) {
   *             double lineT = (double) lIndex;
   *             if (fIntersections->hasOppT(lineT)) {
   *                 continue;
   *             }
   *             double cubicT = ((const SkDCurve*)&fCubic)
   *                                     ->nearPoint(SkPath::kCubic_Verb, fLine[lIndex], fLine[!lIndex]);
   *             if (cubicT < 0) {
   *                 continue;
   *             }
   *             fIntersections->insert(cubicT, lineT, fLine[lIndex]);
   *         }
   *     }
   * ```
   */
  protected fun addLineNearEndPoints() {
    TODO("Implement addLineNearEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void addExactHorizontalEndPoints(double left, double right, double y) {
   *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
   *             double lineT = SkDLine::ExactPointH(fCubic[cIndex], left, right, y);
   *             if (lineT < 0) {
   *                 continue;
   *             }
   *             double cubicT = (double) (cIndex >> 1);
   *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
   *         }
   *     }
   * ```
   */
  protected fun addExactHorizontalEndPoints(
    left: Double,
    right: Double,
    y: Double,
  ) {
    TODO("Implement addExactHorizontalEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void addNearHorizontalEndPoints(double left, double right, double y) {
   *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
   *             double cubicT = (double) (cIndex >> 1);
   *             if (fIntersections->hasT(cubicT)) {
   *                 continue;
   *             }
   *             double lineT = SkDLine::NearPointH(fCubic[cIndex], left, right, y);
   *             if (lineT < 0) {
   *                 continue;
   *             }
   *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
   *         }
   *         this->addLineNearEndPoints();
   *     }
   * ```
   */
  protected fun addNearHorizontalEndPoints(
    left: Double,
    right: Double,
    y: Double,
  ) {
    TODO("Implement addNearHorizontalEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void addExactVerticalEndPoints(double top, double bottom, double x) {
   *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
   *             double lineT = SkDLine::ExactPointV(fCubic[cIndex], top, bottom, x);
   *             if (lineT < 0) {
   *                 continue;
   *             }
   *             double cubicT = (double) (cIndex >> 1);
   *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
   *         }
   *     }
   * ```
   */
  protected fun addExactVerticalEndPoints(
    top: Double,
    bottom: Double,
    x: Double,
  ) {
    TODO("Implement addExactVerticalEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * void addNearVerticalEndPoints(double top, double bottom, double x) {
   *         for (int cIndex = 0; cIndex < 4; cIndex += 3) {
   *             double cubicT = (double) (cIndex >> 1);
   *             if (fIntersections->hasT(cubicT)) {
   *                 continue;
   *             }
   *             double lineT = SkDLine::NearPointV(fCubic[cIndex], top, bottom, x);
   *             if (lineT < 0) {
   *                 continue;
   *             }
   *             fIntersections->insert(cubicT, lineT, fCubic[cIndex]);
   *         }
   *         this->addLineNearEndPoints();
   *     }
   * ```
   */
  protected fun addNearVerticalEndPoints(
    top: Double,
    bottom: Double,
    x: Double,
  ) {
    TODO("Implement addNearVerticalEndPoints")
  }

  /**
   * C++ original:
   * ```cpp
   * double findLineT(double t) {
   *         SkDPoint xy = fCubic.ptAtT(t);
   *         double dx = fLine[1].fX - fLine[0].fX;
   *         double dy = fLine[1].fY - fLine[0].fY;
   *         if (fabs(dx) > fabs(dy)) {
   *             return (xy.fX - fLine[0].fX) / dx;
   *         }
   *         return (xy.fY - fLine[0].fY) / dy;
   *     }
   * ```
   */
  protected fun findLineT(t: Double): Double {
    TODO("Implement findLineT")
  }

  /**
   * C++ original:
   * ```cpp
   * bool pinTs(double* cubicT, double* lineT, SkDPoint* pt, PinTPoint ptSet) {
   *         if (!approximately_one_or_less(*lineT)) {
   *             return false;
   *         }
   *         if (!approximately_zero_or_more(*lineT)) {
   *             return false;
   *         }
   *         double cT = *cubicT = SkPinT(*cubicT);
   *         double lT = *lineT = SkPinT(*lineT);
   *         SkDPoint lPt = fLine.ptAtT(lT);
   *         SkDPoint cPt = fCubic.ptAtT(cT);
   *         if (!lPt.roughlyEqual(cPt)) {
   *             return false;
   *         }
   *         // FIXME: if points are roughly equal but not approximately equal, need to do
   *         // a binary search like quad/quad intersection to find more precise t values
   *         if (lT == 0 || lT == 1 || (ptSet == kPointUninitialized && cT != 0 && cT != 1)) {
   *             *pt = lPt;
   *         } else if (ptSet == kPointUninitialized) {
   *             *pt = cPt;
   *         }
   *         SkPoint gridPt = pt->asSkPoint();
   *         if (gridPt == fLine[0].asSkPoint()) {
   *             *lineT = 0;
   *         } else if (gridPt == fLine[1].asSkPoint()) {
   *             *lineT = 1;
   *         }
   *         if (gridPt == fCubic[0].asSkPoint() && approximately_equal(*cubicT, 0)) {
   *             *cubicT = 0;
   *         } else if (gridPt == fCubic[3].asSkPoint() && approximately_equal(*cubicT, 1)) {
   *             *cubicT = 1;
   *         }
   *         return true;
   *     }
   * ```
   */
  protected fun pinTs(
    cubicT: Double?,
    lineT: Double?,
    pt: SkDPoint?,
    ptSet: PinTPoint,
  ): Boolean {
    TODO("Implement pinTs")
  }

  public enum class PinTPoint {
    kPointUninitialized,
    kPointInitialized,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static int HorizontalIntersect(const SkDCubic& c, double axisIntercept, double roots[3]) {
     *         double A, B, C, D;
     *         SkDCubic::Coefficients(&c[0].fY, &A, &B, &C, &D);
     *         D -= axisIntercept;
     *         int count = SkDCubic::RootsValidT(A, B, C, D, roots);
     *         for (int index = 0; index < count; ++index) {
     *             SkDPoint calcPt = c.ptAtT(roots[index]);
     *             if (!approximately_equal(calcPt.fY, axisIntercept)) {
     *                 double extremeTs[6];
     *                 int extrema = SkDCubic::FindExtrema(&c[0].fY, extremeTs);
     *                 count = c.searchRoots(extremeTs, extrema, axisIntercept, SkDCubic::kYAxis, roots);
     *                 break;
     *             }
     *         }
     *         return count;
     *     }
     * ```
     */
    public fun horizontalIntersect(
      c: SkDCubic,
      axisIntercept: Double,
      roots: DoubleArray,
    ): Int {
      TODO("Implement horizontalIntersect")
    }

    /**
     * C++ original:
     * ```cpp
     * static int VerticalIntersect(const SkDCubic& c, double axisIntercept, double roots[3]) {
     *         double A, B, C, D;
     *         SkDCubic::Coefficients(&c[0].fX, &A, &B, &C, &D);
     *         D -= axisIntercept;
     *         int count = SkDCubic::RootsValidT(A, B, C, D, roots);
     *         for (int index = 0; index < count; ++index) {
     *             SkDPoint calcPt = c.ptAtT(roots[index]);
     *             if (!approximately_equal(calcPt.fX, axisIntercept)) {
     *                 double extremeTs[6];
     *                 int extrema = SkDCubic::FindExtrema(&c[0].fX, extremeTs);
     *                 count = c.searchRoots(extremeTs, extrema, axisIntercept, SkDCubic::kXAxis, roots);
     *                 break;
     *             }
     *         }
     *         return count;
     *     }
     * ```
     */
    public fun verticalIntersect(
      c: SkDCubic,
      axisIntercept: Double,
      roots: DoubleArray,
    ): Int {
      TODO("Implement verticalIntersect")
    }
  }
}
