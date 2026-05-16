package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkTSpan {
 * public:
 *     SkTSpan(const SkTCurve& curve, SkArenaAlloc& heap) {
 *         fPart = curve.make(heap);
 *     }
 *
 *     void addBounded(SkTSpan* , SkArenaAlloc* );
 *     double closestBoundedT(const SkDPoint& pt) const;
 *     bool contains(double t) const;
 *
 *     void debugInit(const SkTCurve& curve, SkArenaAlloc& heap) {
 * #ifdef SK_DEBUG
 *         SkTCurve* fake = curve.make(heap);
 *         fake->debugInit();
 *         init(*fake);
 *         initBounds(*fake);
 *         fCoinStart.init();
 *         fCoinEnd.init();
 * #endif
 *     }
 *
 *     const SkTSect* debugOpp() const;
 *
 * #ifdef SK_DEBUG
 *     void debugSetGlobalState(SkOpGlobalState* state) {
 *         fDebugGlobalState = state;
 *     }
 *
 *     const SkTSpan* debugSpan(int ) const;
 *     const SkTSpan* debugT(double t) const;
 *     bool debugIsBefore(const SkTSpan* span) const;
 * #endif
 *     void dump() const;
 *     void dumpAll() const;
 *     void dumpBounded(int id) const;
 *     void dumpBounds() const;
 *     void dumpCoin() const;
 *
 *     double endT() const {
 *         return fEndT;
 *     }
 *
 *     SkTSpan* findOppSpan(const SkTSpan* opp) const;
 *
 *     SkTSpan* findOppT(double t) const {
 *         SkTSpan* result = oppT(t);
 *         SkOPASSERT(result);
 *         return result;
 *     }
 *
 *     SkDEBUGCODE(SkOpGlobalState* globalState() const { return fDebugGlobalState; })
 *
 *     bool hasOppT(double t) const {
 *         return SkToBool(oppT(t));
 *     }
 *
 *     int hullsIntersect(SkTSpan* span, bool* start, bool* oppStart);
 *     void init(const SkTCurve& );
 *     bool initBounds(const SkTCurve& );
 *
 *     bool isBounded() const {
 *         return fBounded != nullptr;
 *     }
 *
 *     bool linearsIntersect(SkTSpan* span);
 *     double linearT(const SkDPoint& ) const;
 *
 *     void markCoincident() {
 *         fCoinStart.markCoincident();
 *         fCoinEnd.markCoincident();
 *     }
 *
 *     const SkTSpan* next() const {
 *         return fNext;
 *     }
 *
 *     bool onlyEndPointsInCommon(const SkTSpan* opp, bool* start,
 *             bool* oppStart, bool* ptsInCommon);
 *
 *     const SkTCurve& part() const {
 *         return *fPart;
 *     }
 *
 *     int pointCount() const {
 *         return fPart->pointCount();
 *     }
 *
 *     const SkDPoint& pointFirst() const {
 *         return (*fPart)[0];
 *     }
 *
 *     const SkDPoint& pointLast() const {
 *         return (*fPart)[fPart->pointLast()];
 *     }
 *
 *     bool removeAllBounded();
 *     bool removeBounded(const SkTSpan* opp);
 *
 *     void reset() {
 *         fBounded = nullptr;
 *     }
 *
 *     void resetBounds(const SkTCurve& curve) {
 *         fIsLinear = fIsLine = false;
 *         initBounds(curve);
 *     }
 *
 *     bool split(SkTSpan* work, SkArenaAlloc* heap) {
 *         return splitAt(work, (work->fStartT + work->fEndT) * 0.5, heap);
 *     }
 *
 *     bool splitAt(SkTSpan* work, double t, SkArenaAlloc* heap);
 *
 *     double startT() const {
 *         return fStartT;
 *     }
 *
 * private:
 *
 *     // implementation is for testing only
 *     int debugID() const {
 *         return PATH_OPS_DEBUG_T_SECT_RELEASE(fID, -1);
 *     }
 *
 *     void dumpID() const;
 *
 *     int hullCheck(const SkTSpan* opp, bool* start, bool* oppStart);
 *     int linearIntersects(const SkTCurve& ) const;
 *     SkTSpan* oppT(double t) const;
 *
 *     void validate() const;
 *     void validateBounded() const;
 *     void validatePerpT(double oppT) const;
 *     void validatePerpPt(double t, const SkDPoint& ) const;
 *
 *     SkTCurve* fPart;
 *     SkTCoincident fCoinStart;
 *     SkTCoincident fCoinEnd;
 *     SkTSpanBounded* fBounded;
 *     SkTSpan* fPrev;
 *     SkTSpan* fNext;
 *     SkDRect fBounds;
 *     double fStartT;
 *     double fEndT;
 *     double fBoundsMax;
 *     SkOpDebugBool fCollapsed;
 *     SkOpDebugBool fHasPerp;
 *     SkOpDebugBool fIsLinear;
 *     SkOpDebugBool fIsLine;
 *     SkOpDebugBool fDeleted;
 *     SkDEBUGCODE(SkOpGlobalState* fDebugGlobalState;)
 *     SkDEBUGCODE(SkTSect* fDebugSect;)
 *     PATH_OPS_DEBUG_T_SECT_CODE(int fID;)
 *     friend class SkTSect;
 * }
 * ```
 */
public data class SkTSpan public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTCurve* fPart
   * ```
   */
  private var fPart: SkTCurve?,
  /**
   * C++ original:
   * ```cpp
   * SkTCoincident fCoinStart
   * ```
   */
  private var fCoinStart: SkTCoincident,
  /**
   * C++ original:
   * ```cpp
   * SkTCoincident fCoinEnd
   * ```
   */
  private var fCoinEnd: SkTCoincident,
  /**
   * C++ original:
   * ```cpp
   * SkTSpanBounded* fBounded
   * ```
   */
  private var fBounded: SkTSpanBounded?,
  /**
   * C++ original:
   * ```cpp
   * SkTSpan* fPrev
   * ```
   */
  private var fPrev: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * SkTSpan* fNext
   * ```
   */
  private var fNext: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * SkDRect fBounds
   * ```
   */
  private var fBounds: SkDRect,
  /**
   * C++ original:
   * ```cpp
   * double fStartT
   * ```
   */
  private var fStartT: Double,
  /**
   * C++ original:
   * ```cpp
   * double fEndT
   * ```
   */
  private var fEndT: Double,
  /**
   * C++ original:
   * ```cpp
   * double fBoundsMax
   * ```
   */
  private var fBoundsMax: Double,
  /**
   * C++ original:
   * ```cpp
   * SkOpDebugBool fCollapsed
   * ```
   */
  private var fCollapsed: SkOpDebugBool,
  /**
   * C++ original:
   * ```cpp
   * SkOpDebugBool fHasPerp
   * ```
   */
  private var fHasPerp: SkOpDebugBool,
  /**
   * C++ original:
   * ```cpp
   * SkOpDebugBool fIsLinear
   * ```
   */
  private var fIsLinear: SkOpDebugBool,
  /**
   * C++ original:
   * ```cpp
   * SkOpDebugBool fIsLine
   * ```
   */
  private var fIsLine: SkOpDebugBool,
  /**
   * C++ original:
   * ```cpp
   * SkOpDebugBool fDeleted
   * ```
   */
  private var fDeleted: SkOpDebugBool,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::addBounded(SkTSpan* span, SkArenaAlloc* heap) {
   *     SkTSpanBounded* bounded = heap->make<SkTSpanBounded>();
   *     bounded->fBounded = span;
   *     bounded->fNext = fBounded;
   *     fBounded = bounded;
   * }
   * ```
   */
  public fun addBounded(span: SkTSpan?, heap: SkArenaAlloc?) {
    TODO("Implement addBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkTSpan::closestBoundedT(const SkDPoint& pt) const {
   *     double result = -1;
   *     double closest = DBL_MAX;
   *     const SkTSpanBounded* testBounded = fBounded;
   *     while (testBounded) {
   *         const SkTSpan* test = testBounded->fBounded;
   *         double startDist = test->pointFirst().distanceSquared(pt);
   *         if (closest > startDist) {
   *             closest = startDist;
   *             result = test->fStartT;
   *         }
   *         double endDist = test->pointLast().distanceSquared(pt);
   *         if (closest > endDist) {
   *             closest = endDist;
   *             result = test->fEndT;
   *         }
   *         testBounded = testBounded->fNext;
   *     }
   *     SkASSERT(between(0, result, 1));
   *     return result;
   * }
   * ```
   */
  public fun closestBoundedT(pt: SkDPoint): Double {
    TODO("Implement closestBoundedT")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::contains(double t) const {
   *     const SkTSpan* work = this;
   *     do {
   *         if (between(work->fStartT, t, work->fEndT)) {
   *             return true;
   *         }
   *     } while ((work = work->fNext));
   *     return false;
   * }
   * ```
   */
  public fun contains(t: Double): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugInit(const SkTCurve& curve, SkArenaAlloc& heap) {
   * #ifdef SK_DEBUG
   *         SkTCurve* fake = curve.make(heap);
   *         fake->debugInit();
   *         init(*fake);
   *         initBounds(*fake);
   *         fCoinStart.init();
   *         fCoinEnd.init();
   * #endif
   *     }
   * ```
   */
  public fun debugInit(curve: SkTCurve, heap: SkArenaAlloc) {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSect* SkTSpan::debugOpp() const {
   *     return SkDEBUGRELEASE(fDebugSect->debugOpp(), nullptr);
   * }
   * ```
   */
  public fun debugOpp(): SkTSect {
    TODO("Implement debugOpp")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugSetGlobalState(SkOpGlobalState* state) {
   *         fDebugGlobalState = state;
   *     }
   * ```
   */
  public fun debugSetGlobalState(state: SkOpGlobalState?) {
    TODO("Implement debugSetGlobalState")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* SkTSpan::debugSpan(int id) const {
   *     return fDebugSect->debugSpan(id);
   * }
   * ```
   */
  public fun debugSpan(id: Int): SkTSpan {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* SkTSpan::debugT(double t) const {
   *     return fDebugSect->debugT(t);
   * }
   * ```
   */
  public fun debugT(t: Double): SkTSpan {
    TODO("Implement debugT")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::debugIsBefore(const SkTSpan* span) const {
   *     const SkTSpan* work = this;
   *     do {
   *         if (span == work) {
   *             return true;
   *         }
   *     } while ((work = work->fNext));
   *     return false;
   * }
   * ```
   */
  public fun debugIsBefore(span: SkTSpan?): Boolean {
    TODO("Implement debugIsBefore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::dump() const {
   *     dumpID();
   *     SkDebugf("=(%g,%g) [", fStartT, fEndT);
   *     const SkTSpanBounded* testBounded = fBounded;
   *     while (testBounded) {
   *         const SkTSpan* span = testBounded->fBounded;
   *         const SkTSpanBounded* next = testBounded->fNext;
   *         span->dumpID();
   *         if (next) {
   *             SkDebugf(",");
   *         }
   *         testBounded = next;
   *     }
   *     SkDebugf("]");
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::dumpAll() const {
   *     dumpID();
   *     SkDebugf("=(%g,%g) [", fStartT, fEndT);
   *     const SkTSpanBounded* testBounded = fBounded;
   *     while (testBounded) {
   *         const SkTSpan* span = testBounded->fBounded;
   *         const SkTSpanBounded* next = testBounded->fNext;
   *         span->dumpID();
   *         SkDebugf("=(%g,%g)", span->fStartT, span->fEndT);
   *         if (next) {
   *             SkDebugf(" ");
   *         }
   *         testBounded = next;
   *     }
   *     SkDebugf("]\n");
   * }
   * ```
   */
  public fun dumpAll() {
    TODO("Implement dumpAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::dumpBounded(int id) const {
   *     SkDEBUGCODE(fDebugSect->dumpBounded(id));
   * }
   * ```
   */
  public fun dumpBounded(id: Int) {
    TODO("Implement dumpBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::dumpBounds() const {
   *     dumpID();
   *     SkDebugf(" bounds=(%1.9g,%1.9g, %1.9g,%1.9g) boundsMax=%1.9g%s\n",
   *             fBounds.fLeft, fBounds.fTop, fBounds.fRight, fBounds.fBottom, fBoundsMax,
   *             fCollapsed ? " collapsed" : "");
   * }
   * ```
   */
  public fun dumpBounds() {
    TODO("Implement dumpBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::dumpCoin() const {
   *     dumpID();
   *     SkDebugf(" coinStart ");
   *     fCoinStart.dump();
   *     SkDebugf(" coinEnd ");
   *     fCoinEnd.dump();
   * }
   * ```
   */
  public fun dumpCoin() {
    TODO("Implement dumpCoin")
  }

  /**
   * C++ original:
   * ```cpp
   * double endT() const {
   *         return fEndT;
   *     }
   * ```
   */
  public fun endT(): Double {
    TODO("Implement endT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSpan::findOppSpan(
   *         const SkTSpan* opp) const {
   *     SkTSpanBounded* bounded = fBounded;
   *     while (bounded) {
   *         SkTSpan* test = bounded->fBounded;
   *         if (opp == test) {
   *             return test;
   *         }
   *         bounded = bounded->fNext;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun findOppSpan(opp: SkTSpan?): SkTSpan {
    TODO("Implement findOppSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* findOppT(double t) const {
   *         SkTSpan* result = oppT(t);
   *         SkOPASSERT(result);
   *         return result;
   *     }
   * ```
   */
  public fun findOppT(t: Double): SkTSpan {
    TODO("Implement findOppT")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasOppT(double t) const {
   *         return SkToBool(oppT(t));
   *     }
   * ```
   */
  public fun hasOppT(t: Double): Boolean {
    TODO("Implement hasOppT")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSpan::hullsIntersect(SkTSpan* opp,
   *         bool* start, bool* oppStart) {
   *     if (!fBounds.intersects(opp->fBounds)) {
   *         return 0;
   *     }
   *     int hullSect = this->hullCheck(opp, start, oppStart);
   *     if (hullSect >= 0) {
   *         return hullSect;
   *     }
   *     hullSect = opp->hullCheck(this, oppStart, start);
   *     if (hullSect >= 0) {
   *         return hullSect;
   *     }
   *     return -1;
   * }
   * ```
   */
  public fun hullsIntersect(
    span: SkTSpan?,
    start: Boolean?,
    oppStart: Boolean?,
  ): Int {
    TODO("Implement hullsIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::init(const SkTCurve& c) {
   *     fPrev = fNext = nullptr;
   *     fStartT = 0;
   *     fEndT = 1;
   *     fBounded = nullptr;
   *     resetBounds(c);
   * }
   * ```
   */
  public fun `init`(c: SkTCurve) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::initBounds(const SkTCurve& c) {
   *     if (SkIsNaN(fStartT) || SkIsNaN(fEndT)) {
   *         return false;
   *     }
   *     c.subDivide(fStartT, fEndT, fPart);
   *     fBounds.setBounds(*fPart);
   *     fCoinStart.init();
   *     fCoinEnd.init();
   *     fBoundsMax = std::max(fBounds.width(), fBounds.height());
   *     fCollapsed = fPart->collapsed();
   *     fHasPerp = false;
   *     fDeleted = false;
   * #if DEBUG_T_SECT
   *     if (fCollapsed) {
   *         SkDebugf("%s", "");  // for convenient breakpoints
   *     }
   * #endif
   *     return fBounds.valid();
   * }
   * ```
   */
  public fun initBounds(c: SkTCurve): Boolean {
    TODO("Implement initBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isBounded() const {
   *         return fBounded != nullptr;
   *     }
   * ```
   */
  public fun isBounded(): Boolean {
    TODO("Implement isBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::linearsIntersect(SkTSpan* span) {
   *     int result = this->linearIntersects(*span->fPart);
   *     if (result <= 1) {
   *         return SkToBool(result);
   *     }
   *     SkASSERT(span->fIsLinear);
   *     result = span->linearIntersects(*fPart);
   * //    SkASSERT(result <= 1);
   *     return SkToBool(result);
   * }
   * ```
   */
  public fun linearsIntersect(span: SkTSpan?): Boolean {
    TODO("Implement linearsIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkTSpan::linearT(const SkDPoint& pt) const {
   *     SkDVector len = this->pointLast() - this->pointFirst();
   *     return fabs(len.fX) > fabs(len.fY)
   *             ? (pt.fX - this->pointFirst().fX) / len.fX
   *             : (pt.fY - this->pointFirst().fY) / len.fY;
   * }
   * ```
   */
  public fun linearT(pt: SkDPoint): Double {
    TODO("Implement linearT")
  }

  /**
   * C++ original:
   * ```cpp
   * void markCoincident() {
   *         fCoinStart.markCoincident();
   *         fCoinEnd.markCoincident();
   *     }
   * ```
   */
  public fun markCoincident() {
    TODO("Implement markCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* next() const {
   *         return fNext;
   *     }
   * ```
   */
  public fun next(): SkTSpan {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::onlyEndPointsInCommon(const SkTSpan* opp,
   *         bool* start, bool* oppStart, bool* ptsInCommon) {
   *     if (opp->pointFirst() == this->pointFirst()) {
   *         *start = *oppStart = true;
   *     } else if (opp->pointFirst() == this->pointLast()) {
   *         *start = false;
   *         *oppStart = true;
   *     } else if (opp->pointLast() == this->pointFirst()) {
   *         *start = true;
   *         *oppStart = false;
   *     } else if (opp->pointLast() == this->pointLast()) {
   *         *start = *oppStart = false;
   *     } else {
   *         *ptsInCommon = false;
   *         return false;
   *     }
   *     *ptsInCommon = true;
   *     const SkDPoint* otherPts[4], * oppOtherPts[4];
   * //  const SkDPoint* otherPts[this->pointCount() - 1], * oppOtherPts[opp->pointCount() - 1];
   *     int baseIndex = *start ? 0 : fPart->pointLast();
   *     fPart->otherPts(baseIndex, otherPts);
   *     opp->fPart->otherPts(*oppStart ? 0 : opp->fPart->pointLast(), oppOtherPts);
   *     const SkDPoint& base = (*fPart)[baseIndex];
   *     for (int o1 = 0; o1 < this->pointCount() - 1; ++o1) {
   *         SkDVector v1 = *otherPts[o1] - base;
   *         for (int o2 = 0; o2 < opp->pointCount() - 1; ++o2) {
   *             SkDVector v2 = *oppOtherPts[o2] - base;
   *             if (v2.dot(v1) >= 0) {
   *                 return false;
   *             }
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  public fun onlyEndPointsInCommon(
    opp: SkTSpan?,
    start: Boolean?,
    oppStart: Boolean?,
    ptsInCommon: Boolean?,
  ): Boolean {
    TODO("Implement onlyEndPointsInCommon")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTCurve& part() const {
   *         return *fPart;
   *     }
   * ```
   */
  public fun part(): SkTCurve {
    TODO("Implement part")
  }

  /**
   * C++ original:
   * ```cpp
   * int pointCount() const {
   *         return fPart->pointCount();
   *     }
   * ```
   */
  public fun pointCount(): Int {
    TODO("Implement pointCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& pointFirst() const {
   *         return (*fPart)[0];
   *     }
   * ```
   */
  public fun pointFirst(): SkDPoint {
    TODO("Implement pointFirst")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& pointLast() const {
   *         return (*fPart)[fPart->pointLast()];
   *     }
   * ```
   */
  public fun pointLast(): SkDPoint {
    TODO("Implement pointLast")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::removeAllBounded() {
   *     bool deleteSpan = false;
   *     SkTSpanBounded* bounded = fBounded;
   *     while (bounded) {
   *         SkTSpan* opp = bounded->fBounded;
   *         deleteSpan |= opp->removeBounded(this);
   *         bounded = bounded->fNext;
   *     }
   *     return deleteSpan;
   * }
   * ```
   */
  public fun removeAllBounded(): Boolean {
    TODO("Implement removeAllBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::removeBounded(const SkTSpan* opp) {
   *     if (fHasPerp) {
   *         bool foundStart = false;
   *         bool foundEnd = false;
   *         SkTSpanBounded* bounded = fBounded;
   *         while (bounded) {
   *             SkTSpan* test = bounded->fBounded;
   *             if (opp != test) {
   *                 foundStart |= between(test->fStartT, fCoinStart.perpT(), test->fEndT);
   *                 foundEnd |= between(test->fStartT, fCoinEnd.perpT(), test->fEndT);
   *             }
   *             bounded = bounded->fNext;
   *         }
   *         if (!foundStart || !foundEnd) {
   *             fHasPerp = false;
   *             fCoinStart.init();
   *             fCoinEnd.init();
   *         }
   *     }
   *     SkTSpanBounded* bounded = fBounded;
   *     SkTSpanBounded* prev = nullptr;
   *     while (bounded) {
   *         SkTSpanBounded* boundedNext = bounded->fNext;
   *         if (opp == bounded->fBounded) {
   *             if (prev) {
   *                 prev->fNext = boundedNext;
   *                 return false;
   *             } else {
   *                 fBounded = boundedNext;
   *                 return fBounded == nullptr;
   *             }
   *         }
   *         prev = bounded;
   *         bounded = boundedNext;
   *     }
   *     SkOPASSERT(0);
   *     return false;
   * }
   * ```
   */
  public fun removeBounded(opp: SkTSpan?): Boolean {
    TODO("Implement removeBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fBounded = nullptr;
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetBounds(const SkTCurve& curve) {
   *         fIsLinear = fIsLine = false;
   *         initBounds(curve);
   *     }
   * ```
   */
  public fun resetBounds(curve: SkTCurve) {
    TODO("Implement resetBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool split(SkTSpan* work, SkArenaAlloc* heap) {
   *         return splitAt(work, (work->fStartT + work->fEndT) * 0.5, heap);
   *     }
   * ```
   */
  public fun split(work: SkTSpan?, heap: SkArenaAlloc?): Boolean {
    TODO("Implement split")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSpan::splitAt(SkTSpan* work, double t, SkArenaAlloc* heap) {
   *     fStartT = t;
   *     fEndT = work->fEndT;
   *     if (fStartT == fEndT) {
   *         fCollapsed = true;
   *         return false;
   *     }
   *     work->fEndT = t;
   *     if (work->fStartT == work->fEndT) {
   *         work->fCollapsed = true;
   *         return false;
   *     }
   *     fPrev = work;
   *     fNext = work->fNext;
   *     fIsLinear = work->fIsLinear;
   *     fIsLine = work->fIsLine;
   *
   *     work->fNext = this;
   *     if (fNext) {
   *         fNext->fPrev = this;
   *     }
   *     this->validate();
   *     SkTSpanBounded* bounded = work->fBounded;
   *     fBounded = nullptr;
   *     while (bounded) {
   *         this->addBounded(bounded->fBounded, heap);
   *         bounded = bounded->fNext;
   *     }
   *     bounded = fBounded;
   *     while (bounded) {
   *         bounded->fBounded->addBounded(this, heap);
   *         bounded = bounded->fNext;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun splitAt(
    work: SkTSpan?,
    t: Double,
    heap: SkArenaAlloc?,
  ): Boolean {
    TODO("Implement splitAt")
  }

  /**
   * C++ original:
   * ```cpp
   * double startT() const {
   *         return fStartT;
   *     }
   * ```
   */
  public fun startT(): Double {
    TODO("Implement startT")
  }

  /**
   * C++ original:
   * ```cpp
   * int debugID() const {
   *         return PATH_OPS_DEBUG_T_SECT_RELEASE(fID, -1);
   *     }
   * ```
   */
  private fun debugID(): Int {
    TODO("Implement debugID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::dumpID() const {
   *     char cS = fCoinStart.dumpIsCoincidentStr();
   *     if (cS) {
   *         SkDebugf("%c", cS);
   *     }
   *     SkDebugf("%d", debugID());
   *     char cE = fCoinEnd.dumpIsCoincidentStr();
   *     if (cE) {
   *         SkDebugf("%c", cE);
   *     }
   * }
   * ```
   */
  private fun dumpID() {
    TODO("Implement dumpID")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSpan::hullCheck(const SkTSpan* opp,
   *         bool* start, bool* oppStart) {
   *     if (fIsLinear) {
   *         return -1;
   *     }
   *     bool ptsInCommon;
   *     if (onlyEndPointsInCommon(opp, start, oppStart, &ptsInCommon)) {
   *         SkASSERT(ptsInCommon);
   *         return 2;
   *     }
   *     bool linear;
   *     if (fPart->hullIntersects(*opp->fPart, &linear)) {
   *         if (!linear) {  // check set true if linear
   *             return 1;
   *         }
   *         fIsLinear = true;
   *         fIsLine = fPart->controlsInside();
   *         return ptsInCommon ? 1 : -1;
   *     }
   *     // hull is not linear; check set true if intersected at the end points
   *     return ((int) ptsInCommon) << 1;  // 0 or 2
   * }
   * ```
   */
  private fun hullCheck(
    opp: SkTSpan?,
    start: Boolean?,
    oppStart: Boolean?,
  ): Int {
    TODO("Implement hullCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSpan::linearIntersects(const SkTCurve& q2) const {
   *     // looks like q1 is near-linear
   *     int start = 0, end = fPart->pointLast();  // the outside points are usually the extremes
   *     if (!fPart->controlsInside()) {
   *         double dist = 0;  // if there's any question, compute distance to find best outsiders
   *         for (int outer = 0; outer < this->pointCount() - 1; ++outer) {
   *             for (int inner = outer + 1; inner < this->pointCount(); ++inner) {
   *                 double test = ((*fPart)[outer] - (*fPart)[inner]).lengthSquared();
   *                 if (dist > test) {
   *                     continue;
   *                 }
   *                 dist = test;
   *                 start = outer;
   *                 end = inner;
   *             }
   *         }
   *     }
   *     // see if q2 is on one side of the line formed by the extreme points
   *     double origX = (*fPart)[start].fX;
   *     double origY = (*fPart)[start].fY;
   *     double adj = (*fPart)[end].fX - origX;
   *     double opp = (*fPart)[end].fY - origY;
   *     double maxPart = std::max(fabs(adj), fabs(opp));
   *     double sign = 0;  // initialization to shut up warning in release build
   *     for (int n = 0; n < q2.pointCount(); ++n) {
   *         double dx = q2[n].fY - origY;
   *         double dy = q2[n].fX - origX;
   *         double maxVal = std::max(maxPart, std::max(fabs(dx), fabs(dy)));
   *         double test = (q2[n].fY - origY) * adj - (q2[n].fX - origX) * opp;
   *         if (precisely_zero_when_compared_to(test, maxVal)) {
   *             return 1;
   *         }
   *         if (approximately_zero_when_compared_to(test, maxVal)) {
   *             return 3;
   *         }
   *         if (n == 0) {
   *             sign = test;
   *             continue;
   *         }
   *         if (test * sign < 0) {
   *             return 1;
   *         }
   *     }
   *     return 0;
   * }
   * ```
   */
  private fun linearIntersects(q2: SkTCurve): Int {
    TODO("Implement linearIntersects")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSpan::oppT(double t) const {
   *     SkTSpanBounded* bounded = fBounded;
   *     while (bounded) {
   *         SkTSpan* test = bounded->fBounded;
   *         if (between(test->fStartT, t, test->fEndT)) {
   *             return test;
   *         }
   *         bounded = bounded->fNext;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  private fun oppT(t: Double): SkTSpan {
    TODO("Implement oppT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::validate() const {
   * #if DEBUG_VALIDATE
   *     SkASSERT(this != fPrev);
   *     SkASSERT(this != fNext);
   *     SkASSERT(fNext == nullptr || fNext != fPrev);
   *     SkASSERT(fNext == nullptr || this == fNext->fPrev);
   *     SkASSERT(fPrev == nullptr || this == fPrev->fNext);
   *     this->validateBounded();
   * #endif
   * #if DEBUG_T_SECT
   *     SkASSERT(fBounds.width() || fBounds.height() || fCollapsed);
   *     SkASSERT(fBoundsMax == std::max(fBounds.width(), fBounds.height()) || fCollapsed == 0xFF);
   *     SkASSERT(0 <= fStartT);
   *     SkASSERT(fEndT <= 1);
   *     SkASSERT(fStartT <= fEndT);
   *     SkASSERT(fBounded || fCollapsed == 0xFF);
   *     if (fHasPerp) {
   *         if (fCoinStart.isMatch()) {
   *             validatePerpT(fCoinStart.perpT());
   *             validatePerpPt(fCoinStart.perpT(), fCoinStart.perpPt());
   *         }
   *         if (fCoinEnd.isMatch()) {
   *             validatePerpT(fCoinEnd.perpT());
   *             validatePerpPt(fCoinEnd.perpT(), fCoinEnd.perpPt());
   *         }
   *     }
   * #endif
   * }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::validateBounded() const {
   * #if DEBUG_VALIDATE
   *     const SkTSpanBounded* testBounded = fBounded;
   *     while (testBounded) {
   *         SkDEBUGCODE(const SkTSpan* overlap = testBounded->fBounded);
   *         SkASSERT(!overlap->fDeleted);
   * #if DEBUG_T_SECT
   *         SkASSERT(((this->debugID() ^ overlap->debugID()) & 1) == 1);
   *         SkASSERT(overlap->findOppSpan(this));
   * #endif
   *         testBounded = testBounded->fNext;
   *     }
   * #endif
   * }
   * ```
   */
  private fun validateBounded() {
    TODO("Implement validateBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::validatePerpT(double oppT) const {
   *     const SkTSpanBounded* testBounded = fBounded;
   *     while (testBounded) {
   *         const SkTSpan* overlap = testBounded->fBounded;
   *         if (precisely_between(overlap->fStartT, oppT, overlap->fEndT)) {
   *             return;
   *         }
   *         testBounded = testBounded->fNext;
   *     }
   *     SkASSERT(0);
   * }
   * ```
   */
  private fun validatePerpT(oppT: Double) {
    TODO("Implement validatePerpT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSpan::validatePerpPt(double t, const SkDPoint& pt) const {
   *     SkASSERT(fDebugSect->fOppSect->fCurve.ptAtT(t) == pt);
   * }
   * ```
   */
  private fun validatePerpPt(t: Double, pt: SkDPoint) {
    TODO("Implement validatePerpPt")
  }
}
