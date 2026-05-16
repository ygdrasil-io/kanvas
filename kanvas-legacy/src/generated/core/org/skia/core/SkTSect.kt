package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.memory.SkSTArenaAlloc1024

/**
 * C++ original:
 * ```cpp
 * class SkTSect {
 * public:
 *     SkTSect(const SkTCurve& c
 *                              SkDEBUGPARAMS(SkOpGlobalState* ) PATH_OPS_DEBUG_T_SECT_PARAMS(int id));
 *     static void BinarySearch(SkTSect* sect1, SkTSect* sect2,
 *             SkIntersections* intersections);
 *
 *     SkDEBUGCODE(SkOpGlobalState* globalState() { return fDebugGlobalState; })
 *     bool hasBounded(const SkTSpan* ) const;
 *
 *     const SkTSect* debugOpp() const {
 *         return SkDEBUGRELEASE(fOppSect, nullptr);
 *     }
 *
 * #ifdef SK_DEBUG
 *     const SkTSpan* debugSpan(int id) const;
 *     const SkTSpan* debugT(double t) const;
 * #endif
 *     void dump() const;
 *     void dumpBoth(SkTSect* ) const;
 *     void dumpBounded(int id) const;
 *     void dumpBounds() const;
 *     void dumpCoin() const;
 *     void dumpCoinCurves() const;
 *     void dumpCurves() const;
 *
 * private:
 *     enum {
 *         kZeroS1Set = 1,
 *         kOneS1Set = 2,
 *         kZeroS2Set = 4,
 *         kOneS2Set = 8
 *     };
 *
 *     SkTSpan* addFollowing(SkTSpan* prior);
 *     void addForPerp(SkTSpan* span, double t);
 *     SkTSpan* addOne();
 *
 *     SkTSpan* addSplitAt(SkTSpan* span, double t) {
 *         SkTSpan* result = this->addOne();
 *         SkDEBUGCODE(result->debugSetGlobalState(this->globalState()));
 *         result->splitAt(span, t, &fHeap);
 *         result->initBounds(fCurve);
 *         span->initBounds(fCurve);
 *         return result;
 *     }
 *
 *     bool binarySearchCoin(SkTSect* , double tStart, double tStep, double* t,
 *                           double* oppT, SkTSpan** oppFirst);
 *     SkTSpan* boundsMax();
 *     bool coincidentCheck(SkTSect* sect2);
 *     void coincidentForce(SkTSect* sect2, double start1s, double start1e);
 *     bool coincidentHasT(double t);
 *     int collapsed() const;
 *     void computePerpendiculars(SkTSect* sect2, SkTSpan* first,
 *                                SkTSpan* last);
 *     int countConsecutiveSpans(SkTSpan* first,
 *                               SkTSpan** last) const;
 *
 *     int debugID() const {
 *         return PATH_OPS_DEBUG_T_SECT_RELEASE(fID, -1);
 *     }
 *
 *     bool deleteEmptySpans();
 *     void dumpCommon(const SkTSpan* ) const;
 *     void dumpCommonCurves(const SkTSpan* ) const;
 *     static int EndsEqual(const SkTSect* sect1, const SkTSect* sect2,
 *                          SkIntersections* );
 *     bool extractCoincident(SkTSect* sect2, SkTSpan* first,
 *                            SkTSpan* last, SkTSpan** result);
 *     SkTSpan* findCoincidentRun(SkTSpan* first, SkTSpan** lastPtr);
 *     int intersects(SkTSpan* span, SkTSect* opp,
 *                    SkTSpan* oppSpan, int* oppResult);
 *     bool isParallel(const SkDLine& thisLine, const SkTSect* opp) const;
 *     int linesIntersect(SkTSpan* span, SkTSect* opp,
 *                        SkTSpan* oppSpan, SkIntersections* );
 *     bool markSpanGone(SkTSpan* span);
 *     bool matchedDirection(double t, const SkTSect* sect2, double t2) const;
 *     void matchedDirCheck(double t, const SkTSect* sect2, double t2,
 *                          bool* calcMatched, bool* oppMatched) const;
 *     void mergeCoincidence(SkTSect* sect2);
 *
 *     const SkDPoint& pointLast() const {
 *         return fCurve[fCurve.pointLast()];
 *     }
 *
 *     SkTSpan* prev(SkTSpan* ) const;
 *     bool removeByPerpendicular(SkTSect* opp);
 *     void recoverCollapsed();
 *     bool removeCoincident(SkTSpan* span, bool isBetween);
 *     void removeAllBut(const SkTSpan* keep, SkTSpan* span,
 *                       SkTSect* opp);
 *     bool removeSpan(SkTSpan* span);
 *     void removeSpanRange(SkTSpan* first, SkTSpan* last);
 *     bool removeSpans(SkTSpan* span, SkTSect* opp);
 *     void removedEndCheck(SkTSpan* span);
 *
 *     void resetRemovedEnds() {
 *         fRemovedStartT = fRemovedEndT = false;
 *     }
 *
 *     SkTSpan* spanAtT(double t, SkTSpan** priorSpan);
 *     SkTSpan* tail();
 *     bool trim(SkTSpan* span, SkTSect* opp);
 *     bool unlinkSpan(SkTSpan* span);
 *     bool updateBounded(SkTSpan* first, SkTSpan* last,
 *                        SkTSpan* oppFirst);
 *     void validate() const;
 *     void validateBounded() const;
 *
 *     const SkTCurve& fCurve;
 *     SkSTArenaAlloc<1024> fHeap;
 *     SkTSpan* fHead;
 *     SkTSpan* fCoincident;
 *     SkTSpan* fDeleted;
 *     int fActiveCount;
 *     bool fRemovedStartT;
 *     bool fRemovedEndT;
 *     bool fHung;
 *     SkDEBUGCODE(SkOpGlobalState* fDebugGlobalState;)
 *     SkDEBUGCODE(SkTSect* fOppSect;)
 *     PATH_OPS_DEBUG_T_SECT_CODE(int fID;)
 *     PATH_OPS_DEBUG_T_SECT_CODE(int fDebugCount;)
 * #if DEBUG_T_SECT
 *     int fDebugAllocatedCount;
 * #endif
 *     friend class SkTSpan;
 * }
 * ```
 */
public data class SkTSect public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkTCurve& fCurve
   * ```
   */
  private val fCurve: SkTCurve,
  /**
   * C++ original:
   * ```cpp
   * SkSTArenaAlloc<1024> fHeap
   * ```
   */
  private var fHeap: SkSTArenaAlloc1024,
  /**
   * C++ original:
   * ```cpp
   * SkTSpan* fHead
   * ```
   */
  private var fHead: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * SkTSpan* fCoincident
   * ```
   */
  private var fCoincident: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * SkTSpan* fDeleted
   * ```
   */
  private var fDeleted: SkTSpan?,
  /**
   * C++ original:
   * ```cpp
   * int fActiveCount
   * ```
   */
  private var fActiveCount: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fRemovedStartT
   * ```
   */
  private var fRemovedStartT: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fRemovedEndT
   * ```
   */
  private var fRemovedEndT: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHung
   * ```
   */
  private var fHung: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::hasBounded(const SkTSpan* span) const {
   *     const SkTSpan* test = fHead;
   *     if (!test) {
   *         return false;
   *     }
   *     do {
   *         if (test->findOppSpan(span)) {
   *             return true;
   *         }
   *     } while ((test = test->next()));
   *     return false;
   * }
   * ```
   */
  public fun hasBounded(span: SkTSpan?): Boolean {
    TODO("Implement hasBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSect* debugOpp() const {
   *         return SkDEBUGRELEASE(fOppSect, nullptr);
   *     }
   * ```
   */
  public fun debugOpp(): SkTSect {
    TODO("Implement debugOpp")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* SkTSect::debugSpan(int id) const {
   *     const SkTSpan* test = fHead;
   *     do {
   *         if (test->debugID() == id) {
   *             return test;
   *         }
   *     } while ((test = test->next()));
   *     return nullptr;
   * }
   * ```
   */
  public fun debugSpan(id: Int): SkTSpan {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTSpan* SkTSect::debugT(double t) const {
   *     const SkTSpan* test = fHead;
   *     const SkTSpan* closest = nullptr;
   *     double bestDist = DBL_MAX;
   *     do {
   *         if (between(test->fStartT, t, test->fEndT)) {
   *             return test;
   *         }
   *         double testDist = std::min(fabs(test->fStartT - t), fabs(test->fEndT - t));
   *         if (bestDist > testDist) {
   *             bestDist = testDist;
   *             closest = test;
   *         }
   *     } while ((test = test->next()));
   *     SkASSERT(closest);
   *     return closest;
   * }
   * ```
   */
  public fun debugT(t: Double): SkTSpan {
    TODO("Implement debugT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dump() const {
   *     dumpCommon(fHead);
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpBoth(SkTSect* opp) const {
   * #if DEBUG_T_SECT_DUMP <= 2
   * #if DEBUG_T_SECT_DUMP == 2
   *     SkDebugf("%d ", ++gDumpTSectNum);
   * #endif
   *     this->dump();
   *     SkDebugf("\n");
   *     opp->dump();
   *     SkDebugf("\n");
   * #elif DEBUG_T_SECT_DUMP == 3
   *     SkDebugf("<div id=\"sect%d\">\n", ++gDumpTSectNum);
   *     if (this->fHead) {
   *         this->dumpCurves();
   *     }
   *     if (opp->fHead) {
   *         opp->dumpCurves();
   *     }
   *     SkDebugf("</div>\n\n");
   * #endif
   * }
   * ```
   */
  public fun dumpBoth(opp: SkTSect?) {
    TODO("Implement dumpBoth")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpBounded(int id) const {
   * #ifdef SK_DEBUG
   *     const SkTSpan* bounded = debugSpan(id);
   *     if (!bounded) {
   *         SkDebugf("no span matches %d\n", id);
   *         return;
   *     }
   *     const SkTSpan* test = bounded->debugOpp()->fHead;
   *     do {
   *         if (test->findOppSpan(bounded)) {
   *             test->dump();
   *             SkDebugf(" ");
   *         }
   *     } while ((test = test->next()));
   *     SkDebugf("\n");
   * #endif
   * }
   * ```
   */
  public fun dumpBounded(id: Int) {
    TODO("Implement dumpBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpBounds() const {
   *     const SkTSpan* test = fHead;
   *     do {
   *         test->dumpBounds();
   *     } while ((test = test->next()));
   * }
   * ```
   */
  public fun dumpBounds() {
    TODO("Implement dumpBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpCoin() const {
   *     dumpCommon(fCoincident);
   * }
   * ```
   */
  public fun dumpCoin() {
    TODO("Implement dumpCoin")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpCoinCurves() const {
   *     dumpCommonCurves(fCoincident);
   * }
   * ```
   */
  public fun dumpCoinCurves() {
    TODO("Implement dumpCoinCurves")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpCurves() const {
   *     dumpCommonCurves(fHead);
   * }
   * ```
   */
  public fun dumpCurves() {
    TODO("Implement dumpCurves")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::addFollowing(
   *         SkTSpan* prior) {
   *     SkTSpan* result = this->addOne();
   *     SkDEBUGCODE(result->debugSetGlobalState(this->globalState()));
   *     result->fStartT = prior ? prior->fEndT : 0;
   *     SkTSpan* next = prior ? prior->fNext : fHead;
   *     result->fEndT = next ? next->fStartT : 1;
   *     result->fPrev = prior;
   *     result->fNext = next;
   *     if (prior) {
   *         prior->fNext = result;
   *     } else {
   *         fHead = result;
   *     }
   *     if (next) {
   *         next->fPrev = result;
   *     }
   *     result->resetBounds(fCurve);
   *     // world may not be consistent to call validate here
   *     result->validate();
   *     return result;
   * }
   * ```
   */
  private fun addFollowing(prior: SkTSpan?): SkTSpan {
    TODO("Implement addFollowing")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::addForPerp(SkTSpan* span, double t) {
   *     if (!span->hasOppT(t)) {
   *         SkTSpan* priorSpan;
   *         SkTSpan* opp = this->spanAtT(t, &priorSpan);
   *         if (!opp) {
   *             opp = this->addFollowing(priorSpan);
   * #if DEBUG_PERP
   *             SkDebugf("%s priorSpan=%d t=%1.9g opp=%d\n", __FUNCTION__, priorSpan ?
   *                     priorSpan->debugID() : -1, t, opp->debugID());
   * #endif
   *         }
   * #if DEBUG_PERP
   *         opp->dump(); SkDebugf("\n");
   *         SkDebugf("%s addBounded span=%d opp=%d\n", __FUNCTION__, priorSpan ?
   *                 priorSpan->debugID() : -1, opp->debugID());
   * #endif
   *         opp->addBounded(span, &fHeap);
   *         span->addBounded(opp, &fHeap);
   *     }
   *     this->validate();
   * #if DEBUG_T_SECT
   *     span->validatePerpT(t);
   * #endif
   * }
   * ```
   */
  private fun addForPerp(span: SkTSpan?, t: Double) {
    TODO("Implement addForPerp")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::addOne() {
   *     SkTSpan* result;
   *     if (fDeleted) {
   *         result = fDeleted;
   *         fDeleted = result->fNext;
   *     } else {
   *         result = fHeap.make<SkTSpan>(fCurve, fHeap);
   * #if DEBUG_T_SECT
   *         ++fDebugAllocatedCount;
   * #endif
   *     }
   *     result->reset();
   *     result->fHasPerp = false;
   *     result->fDeleted = false;
   *     ++fActiveCount;
   *     PATH_OPS_DEBUG_T_SECT_CODE(result->fID = fDebugCount++ * 2 + fID);
   *     SkDEBUGCODE(result->fDebugSect = this);
   * #ifdef SK_DEBUG
   *     result->debugInit(fCurve, fHeap);
   *     result->fCoinStart.debugInit();
   *     result->fCoinEnd.debugInit();
   *     result->fPrev = result->fNext = nullptr;
   *     result->fBounds.debugInit();
   *     result->fStartT = result->fEndT = result->fBoundsMax = SK_ScalarNaN;
   *     result->fCollapsed = result->fIsLinear = result->fIsLine = 0xFF;
   * #endif
   *     return result;
   * }
   * ```
   */
  private fun addOne(): SkTSpan {
    TODO("Implement addOne")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* addSplitAt(SkTSpan* span, double t) {
   *         SkTSpan* result = this->addOne();
   *         SkDEBUGCODE(result->debugSetGlobalState(this->globalState()));
   *         result->splitAt(span, t, &fHeap);
   *         result->initBounds(fCurve);
   *         span->initBounds(fCurve);
   *         return result;
   *     }
   * ```
   */
  private fun addSplitAt(span: SkTSpan?, t: Double): SkTSpan {
    TODO("Implement addSplitAt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::binarySearchCoin(SkTSect* sect2, double tStart,
   *         double tStep, double* resultT, double* oppT, SkTSpan** oppFirst) {
   *     SkTSpan work(fCurve, fHeap);
   *     double result = work.fStartT = work.fEndT = tStart;
   *     SkDEBUGCODE(work.fDebugSect = this);
   *     SkDPoint last = fCurve.ptAtT(tStart);
   *     SkDPoint oppPt;
   *     bool flip = false;
   *     bool contained = false;
   *     bool down = tStep < 0;
   *     const SkTCurve& opp = sect2->fCurve;
   *     do {
   *         tStep *= 0.5;
   *         work.fStartT += tStep;
   *         if (flip) {
   *             tStep = -tStep;
   *             flip = false;
   *         }
   *         work.initBounds(fCurve);
   *         if (work.fCollapsed) {
   *             return false;
   *         }
   *         if (last.approximatelyEqual(work.pointFirst())) {
   *             break;
   *         }
   *         last = work.pointFirst();
   *         work.fCoinStart.setPerp(fCurve, work.fStartT, last, opp);
   *         if (work.fCoinStart.isMatch()) {
   * #if DEBUG_T_SECT
   *             work.validatePerpPt(work.fCoinStart.perpT(), work.fCoinStart.perpPt());
   * #endif
   *             double oppTTest = work.fCoinStart.perpT();
   *             if (sect2->fHead->contains(oppTTest)) {
   *                 *oppT = oppTTest;
   *                 oppPt = work.fCoinStart.perpPt();
   *                 contained = true;
   *                 if (down ? result <= work.fStartT : result >= work.fStartT) {
   *                     *oppFirst = nullptr;    // signal caller to fail
   *                     return false;
   *                 }
   *                 result = work.fStartT;
   *                 continue;
   *             }
   *         }
   *         tStep = -tStep;
   *         flip = true;
   *     } while (true);
   *     if (!contained) {
   *         return false;
   *     }
   *     if (last.approximatelyEqual(fCurve[0])) {
   *         result = 0;
   *     } else if (last.approximatelyEqual(this->pointLast())) {
   *         result = 1;
   *     }
   *     if (oppPt.approximatelyEqual(opp[0])) {
   *         *oppT = 0;
   *     } else if (oppPt.approximatelyEqual(sect2->pointLast())) {
   *         *oppT = 1;
   *     }
   *     *resultT = result;
   *     return true;
   * }
   * ```
   */
  private fun binarySearchCoin(
    sect2: SkTSect?,
    tStart: Double,
    tStep: Double,
    t: Double?,
    oppT: Double?,
    oppFirst: Int?,
  ): Boolean {
    TODO("Implement binarySearchCoin")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::boundsMax() {
   *     SkTSpan* test = fHead;
   *     SkTSpan* largest = fHead;
   *     bool lCollapsed = largest->fCollapsed;
   *     int safetyNet = 10000;
   *     while ((test = test->fNext)) {
   *         if (!--safetyNet) {
   *             fHung = true;
   *             return nullptr;
   *         }
   *         bool tCollapsed = test->fCollapsed;
   *         if ((lCollapsed && !tCollapsed) || (lCollapsed == tCollapsed &&
   *                 largest->fBoundsMax < test->fBoundsMax)) {
   *             largest = test;
   *             lCollapsed = test->fCollapsed;
   *         }
   *     }
   *     return largest;
   * }
   * ```
   */
  private fun boundsMax(): SkTSpan {
    TODO("Implement boundsMax")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::coincidentCheck(SkTSect* sect2) {
   *     SkTSpan* first = fHead;
   *     if (!first) {
   *         return false;
   *     }
   *     SkTSpan* last, * next;
   *     do {
   *         int consecutive = this->countConsecutiveSpans(first, &last);
   *         next = last->fNext;
   *         if (consecutive < COINCIDENT_SPAN_COUNT) {
   *             continue;
   *         }
   *         this->validate();
   *         sect2->validate();
   *         this->computePerpendiculars(sect2, first, last);
   *         this->validate();
   *         sect2->validate();
   *         // check to see if a range of points are on the curve
   *         SkTSpan* coinStart = first;
   *         do {
   *             bool success = this->extractCoincident(sect2, coinStart, last, &coinStart);
   *             if (!success) {
   *                 return false;
   *             }
   *         } while (coinStart && !last->fDeleted);
   *         if (!fHead || !sect2->fHead) {
   *             break;
   *         }
   *         if (!next || next->fDeleted) {
   *             break;
   *         }
   *     } while ((first = next));
   *     return true;
   * }
   * ```
   */
  private fun coincidentCheck(sect2: SkTSect?): Boolean {
    TODO("Implement coincidentCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::coincidentForce(SkTSect* sect2,
   *         double start1s, double start1e) {
   *     SkTSpan* first = fHead;
   *     SkTSpan* last = this->tail();
   *     SkTSpan* oppFirst = sect2->fHead;
   *     SkTSpan* oppLast = sect2->tail();
   *     if (!last || !oppLast) {
   *         return;
   *     }
   *     bool deleteEmptySpans = this->updateBounded(first, last, oppFirst);
   *     deleteEmptySpans |= sect2->updateBounded(oppFirst, oppLast, first);
   *     this->removeSpanRange(first, last);
   *     sect2->removeSpanRange(oppFirst, oppLast);
   *     first->fStartT = start1s;
   *     first->fEndT = start1e;
   *     first->resetBounds(fCurve);
   *     first->fCoinStart.setPerp(fCurve, start1s, fCurve[0], sect2->fCurve);
   *     first->fCoinEnd.setPerp(fCurve, start1e, this->pointLast(), sect2->fCurve);
   *     bool oppMatched = first->fCoinStart.perpT() < first->fCoinEnd.perpT();
   *     double oppStartT = first->fCoinStart.perpT() == -1 ? 0 : std::max(0., first->fCoinStart.perpT());
   *     double oppEndT = first->fCoinEnd.perpT() == -1 ? 1 : std::min(1., first->fCoinEnd.perpT());
   *     if (!oppMatched) {
   *         using std::swap;
   *         swap(oppStartT, oppEndT);
   *     }
   *     oppFirst->fStartT = oppStartT;
   *     oppFirst->fEndT = oppEndT;
   *     oppFirst->resetBounds(sect2->fCurve);
   *     this->removeCoincident(first, false);
   *     sect2->removeCoincident(oppFirst, true);
   *     if (deleteEmptySpans) {
   *         this->deleteEmptySpans();
   *         sect2->deleteEmptySpans();
   *     }
   * }
   * ```
   */
  private fun coincidentForce(
    sect2: SkTSect?,
    start1s: Double,
    start1e: Double,
  ) {
    TODO("Implement coincidentForce")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::coincidentHasT(double t) {
   *     SkTSpan* test = fCoincident;
   *     while (test) {
   *         if (between(test->fStartT, t, test->fEndT)) {
   *             return true;
   *         }
   *         test = test->fNext;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun coincidentHasT(t: Double): Boolean {
    TODO("Implement coincidentHasT")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSect::collapsed() const {
   *     int result = 0;
   *     const SkTSpan* test = fHead;
   *     while (test) {
   *         if (test->fCollapsed) {
   *             ++result;
   *         }
   *         test = test->next();
   *     }
   *     return result;
   * }
   * ```
   */
  private fun collapsed(): Int {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::computePerpendiculars(SkTSect* sect2,
   *         SkTSpan* first, SkTSpan* last) {
   *     if (!last) {
   *         return;
   *     }
   *     const SkTCurve& opp = sect2->fCurve;
   *     SkTSpan* work = first;
   *     SkTSpan* prior = nullptr;
   *     do {
   *         if (!work->fHasPerp && !work->fCollapsed) {
   *             if (prior) {
   *                 work->fCoinStart = prior->fCoinEnd;
   *             } else {
   *                 work->fCoinStart.setPerp(fCurve, work->fStartT, work->pointFirst(), opp);
   *             }
   *             if (work->fCoinStart.isMatch()) {
   *                 double perpT = work->fCoinStart.perpT();
   *                 if (sect2->coincidentHasT(perpT)) {
   *                     work->fCoinStart.init();
   *                 } else {
   *                     sect2->addForPerp(work, perpT);
   *                 }
   *             }
   *             work->fCoinEnd.setPerp(fCurve, work->fEndT, work->pointLast(), opp);
   *             if (work->fCoinEnd.isMatch()) {
   *                 double perpT = work->fCoinEnd.perpT();
   *                 if (sect2->coincidentHasT(perpT)) {
   *                     work->fCoinEnd.init();
   *                 } else {
   *                     sect2->addForPerp(work, perpT);
   *                 }
   *             }
   *             work->fHasPerp = true;
   *         }
   *         if (work == last) {
   *             break;
   *         }
   *         prior = work;
   *         work = work->fNext;
   *         SkASSERT(work);
   *     } while (true);
   * }
   * ```
   */
  private fun computePerpendiculars(
    sect2: SkTSect?,
    first: SkTSpan?,
    last: SkTSpan?,
  ) {
    TODO("Implement computePerpendiculars")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSect::countConsecutiveSpans(SkTSpan* first,
   *         SkTSpan** lastPtr) const {
   *     int consecutive = 1;
   *     SkTSpan* last = first;
   *     do {
   *         SkTSpan* next = last->fNext;
   *         if (!next) {
   *             break;
   *         }
   *         if (next->fStartT > last->fEndT) {
   *             break;
   *         }
   *         ++consecutive;
   *         last = next;
   *     } while (true);
   *     *lastPtr = last;
   *     return consecutive;
   * }
   * ```
   */
  private fun countConsecutiveSpans(first: SkTSpan?, last: Int?): Int {
    TODO("Implement countConsecutiveSpans")
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
   * bool SkTSect::deleteEmptySpans() {
   *     SkTSpan* test;
   *     SkTSpan* next = fHead;
   *     int safetyHatch = 1000;
   *     while ((test = next)) {
   *         next = test->fNext;
   *         if (!test->fBounded) {
   *             if (!this->removeSpan(test)) {
   *                 return false;
   *             }
   *         }
   *         if (--safetyHatch < 0) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  private fun deleteEmptySpans(): Boolean {
    TODO("Implement deleteEmptySpans")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpCommon(const SkTSpan* test) const {
   *     SkDebugf("id=%d", debugID());
   *     if (!test) {
   *         SkDebugf(" (empty)");
   *         return;
   *     }
   *     do {
   *         SkDebugf(" ");
   *         test->dump();
   *     } while ((test = test->next()));
   * }
   * ```
   */
  private fun dumpCommon(test: SkTSpan?) {
    TODO("Implement dumpCommon")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::dumpCommonCurves(const SkTSpan* test) const {
   * #if DEBUG_T_SECT
   *     do {
   *         test->fPart->dumpID(test->debugID());
   *     } while ((test = test->next()));
   * #endif
   * }
   * ```
   */
  private fun dumpCommonCurves(test: SkTSpan?) {
    TODO("Implement dumpCommonCurves")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::extractCoincident(
   *         SkTSect* sect2,
   *         SkTSpan* first, SkTSpan* last,
   *         SkTSpan** result) {
   *     first = findCoincidentRun(first, &last);
   *     if (!first || !last) {
   *         *result = nullptr;
   *         return true;
   *     }
   *     // march outwards to find limit of coincidence from here to previous and next spans
   *     double startT = first->fStartT;
   *     double oppStartT SK_INIT_TO_AVOID_WARNING;
   *     double oppEndT SK_INIT_TO_AVOID_WARNING;
   *     SkTSpan* prev = first->fPrev;
   *     SkASSERT(first->fCoinStart.isMatch());
   *     SkTSpan* oppFirst = first->findOppT(first->fCoinStart.perpT());
   *     SkOPASSERT(last->fCoinEnd.isMatch());
   *     bool oppMatched = first->fCoinStart.perpT() < first->fCoinEnd.perpT();
   *     double coinStart;
   *     SkDEBUGCODE(double coinEnd);
   *     SkTSpan* cutFirst;
   *     if (prev && prev->fEndT == startT
   *             && this->binarySearchCoin(sect2, startT, prev->fStartT - startT, &coinStart,
   *                                       &oppStartT, &oppFirst)
   *             && prev->fStartT < coinStart && coinStart < startT
   *             && (cutFirst = prev->oppT(oppStartT))) {
   *         oppFirst = cutFirst;
   *         first = this->addSplitAt(prev, coinStart);
   *         first->markCoincident();
   *         prev->fCoinEnd.markCoincident();
   *         if (oppFirst->fStartT < oppStartT && oppStartT < oppFirst->fEndT) {
   *             SkTSpan* oppHalf = sect2->addSplitAt(oppFirst, oppStartT);
   *             if (oppMatched) {
   *                 oppFirst->fCoinEnd.markCoincident();
   *                 oppHalf->markCoincident();
   *                 oppFirst = oppHalf;
   *             } else {
   *                 oppFirst->markCoincident();
   *                 oppHalf->fCoinStart.markCoincident();
   *             }
   *         }
   *     } else {
   *         if (!oppFirst) {
   *             return false;
   *         }
   *         SkDEBUGCODE(coinStart = first->fStartT);
   *         SkDEBUGCODE(oppStartT = oppMatched ? oppFirst->fStartT : oppFirst->fEndT);
   *     }
   *     // FIXME: incomplete : if we're not at the end, find end of coin
   *     SkTSpan* oppLast;
   *     SkOPASSERT(last->fCoinEnd.isMatch());
   *     oppLast = last->findOppT(last->fCoinEnd.perpT());
   *     SkDEBUGCODE(coinEnd = last->fEndT);
   * #ifdef SK_DEBUG
   *     if (!this->globalState() || !this->globalState()->debugSkipAssert()) {
   *         oppEndT = oppMatched ? oppLast->fEndT : oppLast->fStartT;
   *     }
   * #endif
   *     if (!oppMatched) {
   *         using std::swap;
   *         swap(oppFirst, oppLast);
   *         swap(oppStartT, oppEndT);
   *     }
   *     SkOPASSERT(oppStartT < oppEndT);
   *     SkASSERT(coinStart == first->fStartT);
   *     SkASSERT(coinEnd == last->fEndT);
   *     if (!oppFirst) {
   *         *result = nullptr;
   *         return true;
   *     }
   *     SkOPASSERT(oppStartT == oppFirst->fStartT);
   *     if (!oppLast) {
   *         *result = nullptr;
   *         return true;
   *     }
   *     SkOPASSERT(oppEndT == oppLast->fEndT);
   *     // reduce coincident runs to single entries
   *     this->validate();
   *     sect2->validate();
   *     bool deleteEmptySpans = this->updateBounded(first, last, oppFirst);
   *     deleteEmptySpans |= sect2->updateBounded(oppFirst, oppLast, first);
   *     this->removeSpanRange(first, last);
   *     sect2->removeSpanRange(oppFirst, oppLast);
   *     first->fEndT = last->fEndT;
   *     first->resetBounds(this->fCurve);
   *     first->fCoinStart.setPerp(fCurve, first->fStartT, first->pointFirst(), sect2->fCurve);
   *     first->fCoinEnd.setPerp(fCurve, first->fEndT, first->pointLast(), sect2->fCurve);
   *     oppStartT = first->fCoinStart.perpT();
   *     oppEndT = first->fCoinEnd.perpT();
   *     if (between(0, oppStartT, 1) && between(0, oppEndT, 1)) {
   *         if (!oppMatched) {
   *             using std::swap;
   *             swap(oppStartT, oppEndT);
   *         }
   *         oppFirst->fStartT = oppStartT;
   *         oppFirst->fEndT = oppEndT;
   *         oppFirst->resetBounds(sect2->fCurve);
   *     }
   *     this->validateBounded();
   *     sect2->validateBounded();
   *     last = first->fNext;
   *     if (!this->removeCoincident(first, false)) {
   *         return false;
   *     }
   *     if (!sect2->removeCoincident(oppFirst, true)) {
   *         return false;
   *     }
   *     if (deleteEmptySpans) {
   *         if (!this->deleteEmptySpans() || !sect2->deleteEmptySpans()) {
   *             *result = nullptr;
   *             return false;
   *         }
   *     }
   *     this->validate();
   *     sect2->validate();
   *     *result = last && !last->fDeleted && fHead && sect2->fHead ? last : nullptr;
   *     return true;
   * }
   * ```
   */
  private fun extractCoincident(
    sect2: SkTSect?,
    first: SkTSpan?,
    last: SkTSpan?,
    result: Int?,
  ): Boolean {
    TODO("Implement extractCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::findCoincidentRun(
   *         SkTSpan* first, SkTSpan** lastPtr) {
   *     SkTSpan* work = first;
   *     SkTSpan* lastCandidate = nullptr;
   *     first = nullptr;
   *     // find the first fully coincident span
   *     do {
   *         if (work->fCoinStart.isMatch()) {
   * #if DEBUG_T_SECT
   *             work->validatePerpT(work->fCoinStart.perpT());
   *             work->validatePerpPt(work->fCoinStart.perpT(), work->fCoinStart.perpPt());
   * #endif
   *             SkOPASSERT(work->hasOppT(work->fCoinStart.perpT()));
   *             if (!work->fCoinEnd.isMatch()) {
   *                 break;
   *             }
   *             lastCandidate = work;
   *             if (!first) {
   *                 first = work;
   *             }
   *         } else if (first && work->fCollapsed) {
   *             *lastPtr = lastCandidate;
   *             return first;
   *         } else {
   *             lastCandidate = nullptr;
   *             SkOPASSERT(!first);
   *         }
   *         if (work == *lastPtr) {
   *             return first;
   *         }
   *         work = work->fNext;
   *         if (!work) {
   *             return nullptr;
   *         }
   *     } while (true);
   *     if (lastCandidate) {
   *         *lastPtr = lastCandidate;
   *     }
   *     return first;
   * }
   * ```
   */
  private fun findCoincidentRun(first: SkTSpan?, lastPtr: Int?): SkTSpan {
    TODO("Implement findCoincidentRun")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSect::intersects(SkTSpan* span,
   *         SkTSect* opp,
   *         SkTSpan* oppSpan, int* oppResult) {
   *     bool spanStart, oppStart;
   *     int hullResult = span->hullsIntersect(oppSpan, &spanStart, &oppStart);
   *     if (hullResult >= 0) {
   *         if (hullResult == 2) {  // hulls have one point in common
   *             if (!span->fBounded || !span->fBounded->fNext) {
   *                 SkASSERT(!span->fBounded || span->fBounded->fBounded == oppSpan);
   *                 if (spanStart) {
   *                     span->fEndT = span->fStartT;
   *                 } else {
   *                     span->fStartT = span->fEndT;
   *                 }
   *             } else {
   *                 hullResult = 1;
   *             }
   *             if (!oppSpan->fBounded || !oppSpan->fBounded->fNext) {
   *                 if (oppSpan->fBounded && oppSpan->fBounded->fBounded != span) {
   *                     return 0;
   *                 }
   *                 if (oppStart) {
   *                     oppSpan->fEndT = oppSpan->fStartT;
   *                 } else {
   *                     oppSpan->fStartT = oppSpan->fEndT;
   *                 }
   *                 *oppResult = 2;
   *             } else {
   *                 *oppResult = 1;
   *             }
   *         } else {
   *             *oppResult = 1;
   *         }
   *         return hullResult;
   *     }
   *     if (span->fIsLine && oppSpan->fIsLine) {
   *         SkIntersections i;
   *         int sects = this->linesIntersect(span, opp, oppSpan, &i);
   *         if (sects == 2) {
   *             return *oppResult = 1;
   *         }
   *         if (!sects) {
   *             return -1;
   *         }
   *         this->removedEndCheck(span);
   *         span->fStartT = span->fEndT = i[0][0];
   *         opp->removedEndCheck(oppSpan);
   *         oppSpan->fStartT = oppSpan->fEndT = i[1][0];
   *         return *oppResult = 2;
   *     }
   *     if (span->fIsLinear || oppSpan->fIsLinear) {
   *         return *oppResult = (int) span->linearsIntersect(oppSpan);
   *     }
   *     return *oppResult = 1;
   * }
   * ```
   */
  private fun intersects(
    span: SkTSpan?,
    opp: SkTSect?,
    oppSpan: SkTSpan?,
    oppResult: Int?,
  ): Int {
    TODO("Implement intersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isParallel(const SkDLine& thisLine, const SkTSect* opp) const
   * ```
   */
  private fun isParallel(thisLine: SkDLine, opp: SkTSect?): Boolean {
    TODO("Implement isParallel")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkTSect::linesIntersect(SkTSpan* span,
   *         SkTSect* opp,
   *         SkTSpan* oppSpan, SkIntersections* i) {
   *     SkIntersections thisRayI  SkDEBUGCODE((span->fDebugGlobalState));
   *     SkIntersections oppRayI  SkDEBUGCODE((span->fDebugGlobalState));
   *     SkDLine thisLine = {{ span->pointFirst(), span->pointLast() }};
   *     SkDLine oppLine = {{ oppSpan->pointFirst(), oppSpan->pointLast() }};
   *     int loopCount = 0;
   *     double bestDistSq = DBL_MAX;
   *     if (!thisRayI.intersectRay(opp->fCurve, thisLine)) {
   *         return 0;
   *     }
   *     if (!oppRayI.intersectRay(this->fCurve, oppLine)) {
   *         return 0;
   *     }
   *     // if the ends of each line intersect the opposite curve, the lines are coincident
   *     if (thisRayI.used() > 1) {
   *         int ptMatches = 0;
   *         for (int tIndex = 0; tIndex < thisRayI.used(); ++tIndex) {
   *             for (int lIndex = 0; lIndex < (int) std::size(thisLine.fPts); ++lIndex) {
   *                 ptMatches += thisRayI.pt(tIndex).approximatelyEqual(thisLine.fPts[lIndex]);
   *             }
   *         }
   *         if (ptMatches == 2 || is_parallel(thisLine, opp->fCurve)) {
   *             return 2;
   *         }
   *     }
   *     if (oppRayI.used() > 1) {
   *         int ptMatches = 0;
   *         for (int oIndex = 0; oIndex < oppRayI.used(); ++oIndex) {
   *             for (int lIndex = 0; lIndex < (int) std::size(oppLine.fPts); ++lIndex) {
   *                 ptMatches += oppRayI.pt(oIndex).approximatelyEqual(oppLine.fPts[lIndex]);
   *             }
   *         }
   *         if (ptMatches == 2|| is_parallel(oppLine, this->fCurve)) {
   *             return 2;
   *         }
   *     }
   *     do {
   *         // pick the closest pair of points
   *         double closest = DBL_MAX;
   *         int closeIndex SK_INIT_TO_AVOID_WARNING;
   *         int oppCloseIndex SK_INIT_TO_AVOID_WARNING;
   *         for (int index = 0; index < oppRayI.used(); ++index) {
   *             if (!roughly_between(span->fStartT, oppRayI[0][index], span->fEndT)) {
   *                 continue;
   *             }
   *             for (int oIndex = 0; oIndex < thisRayI.used(); ++oIndex) {
   *                 if (!roughly_between(oppSpan->fStartT, thisRayI[0][oIndex], oppSpan->fEndT)) {
   *                     continue;
   *                 }
   *                 double distSq = thisRayI.pt(index).distanceSquared(oppRayI.pt(oIndex));
   *                 if (closest > distSq) {
   *                     closest = distSq;
   *                     closeIndex = index;
   *                     oppCloseIndex = oIndex;
   *                 }
   *             }
   *         }
   *         if (closest == DBL_MAX) {
   *             break;
   *         }
   *         const SkDPoint& oppIPt = thisRayI.pt(oppCloseIndex);
   *         const SkDPoint& iPt = oppRayI.pt(closeIndex);
   *         if (between(span->fStartT, oppRayI[0][closeIndex], span->fEndT)
   *                 && between(oppSpan->fStartT, thisRayI[0][oppCloseIndex], oppSpan->fEndT)
   *                 && oppIPt.approximatelyEqual(iPt)) {
   *             i->merge(oppRayI, closeIndex, thisRayI, oppCloseIndex);
   *             return i->used();
   *         }
   *         double distSq = oppIPt.distanceSquared(iPt);
   *         if (bestDistSq < distSq || ++loopCount > 5) {
   *             return 0;
   *         }
   *         bestDistSq = distSq;
   *         double oppStart = oppRayI[0][closeIndex];
   *         thisLine[0] = fCurve.ptAtT(oppStart);
   *         thisLine[1] = thisLine[0] + fCurve.dxdyAtT(oppStart);
   *         if (!thisRayI.intersectRay(opp->fCurve, thisLine)) {
   *             break;
   *         }
   *         double start = thisRayI[0][oppCloseIndex];
   *         oppLine[0] = opp->fCurve.ptAtT(start);
   *         oppLine[1] = oppLine[0] + opp->fCurve.dxdyAtT(start);
   *         if (!oppRayI.intersectRay(this->fCurve, oppLine)) {
   *             break;
   *         }
   *     } while (true);
   *     // convergence may fail if the curves are nearly coincident
   *     SkTCoincident oCoinS, oCoinE;
   *     oCoinS.setPerp(opp->fCurve, oppSpan->fStartT, oppSpan->pointFirst(), fCurve);
   *     oCoinE.setPerp(opp->fCurve, oppSpan->fEndT, oppSpan->pointLast(), fCurve);
   *     double tStart = oCoinS.perpT();
   *     double tEnd = oCoinE.perpT();
   *     bool swap = tStart > tEnd;
   *     if (swap) {
   *         using std::swap;
   *         swap(tStart, tEnd);
   *     }
   *     tStart = std::max(tStart, span->fStartT);
   *     tEnd = std::min(tEnd, span->fEndT);
   *     if (tStart > tEnd) {
   *         return 0;
   *     }
   *     SkDVector perpS, perpE;
   *     if (tStart == span->fStartT) {
   *         SkTCoincident coinS;
   *         coinS.setPerp(fCurve, span->fStartT, span->pointFirst(), opp->fCurve);
   *         perpS = span->pointFirst() - coinS.perpPt();
   *     } else if (swap) {
   *         perpS = oCoinE.perpPt() - oppSpan->pointLast();
   *     } else {
   *         perpS = oCoinS.perpPt() - oppSpan->pointFirst();
   *     }
   *     if (tEnd == span->fEndT) {
   *         SkTCoincident coinE;
   *         coinE.setPerp(fCurve, span->fEndT, span->pointLast(), opp->fCurve);
   *         perpE = span->pointLast() - coinE.perpPt();
   *     } else if (swap) {
   *         perpE = oCoinS.perpPt() - oppSpan->pointFirst();
   *     } else {
   *         perpE = oCoinE.perpPt() - oppSpan->pointLast();
   *     }
   *     if (perpS.dot(perpE) >= 0) {
   *         return 0;
   *     }
   *     SkTCoincident coinW;
   *     double workT = tStart;
   *     double tStep = tEnd - tStart;
   *     SkDPoint workPt;
   *     do {
   *         tStep *= 0.5;
   *         if (precisely_zero(tStep)) {
   *             return 0;
   *         }
   *         workT += tStep;
   *         workPt = fCurve.ptAtT(workT);
   *         coinW.setPerp(fCurve, workT, workPt, opp->fCurve);
   *         double perpT = coinW.perpT();
   *         if (coinW.isMatch() ? !between(oppSpan->fStartT, perpT, oppSpan->fEndT) : perpT < 0) {
   *             continue;
   *         }
   *         SkDVector perpW = workPt - coinW.perpPt();
   *         if ((perpS.dot(perpW) >= 0) == (tStep < 0)) {
   *             tStep = -tStep;
   *         }
   *         if (workPt.approximatelyEqual(coinW.perpPt())) {
   *             break;
   *         }
   *     } while (true);
   *     double oppTTest = coinW.perpT();
   *     if (!opp->fHead->contains(oppTTest)) {
   *         return 0;
   *     }
   *     i->setMax(1);
   *     i->insert(workT, oppTTest, workPt);
   *     return 1;
   * }
   * ```
   */
  private fun linesIntersect(
    span: SkTSpan?,
    opp: SkTSect?,
    oppSpan: SkTSpan?,
    i: SkIntersections?,
  ): Int {
    TODO("Implement linesIntersect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::markSpanGone(SkTSpan* span) {
   *     if (--fActiveCount < 0) {
   *         return false;
   *     }
   *     span->fNext = fDeleted;
   *     fDeleted = span;
   *     SkOPASSERT(!span->fDeleted);
   *     span->fDeleted = true;
   *     return true;
   * }
   * ```
   */
  private fun markSpanGone(span: SkTSpan?): Boolean {
    TODO("Implement markSpanGone")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::matchedDirection(double t, const SkTSect* sect2,
   *         double t2) const {
   *     SkDVector dxdy = this->fCurve.dxdyAtT(t);
   *     SkDVector dxdy2 = sect2->fCurve.dxdyAtT(t2);
   *     return dxdy.dot(dxdy2) >= 0;
   * }
   * ```
   */
  private fun matchedDirection(
    t: Double,
    sect2: SkTSect?,
    t2: Double,
  ): Boolean {
    TODO("Implement matchedDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::matchedDirCheck(double t, const SkTSect* sect2,
   *         double t2, bool* calcMatched, bool* oppMatched) const {
   *     if (*calcMatched) {
   *         SkASSERT(*oppMatched == this->matchedDirection(t, sect2, t2));
   *     } else {
   *         *oppMatched = this->matchedDirection(t, sect2, t2);
   *         *calcMatched = true;
   *     }
   * }
   * ```
   */
  private fun matchedDirCheck(
    t: Double,
    sect2: SkTSect?,
    t2: Double,
    calcMatched: Boolean?,
    oppMatched: Boolean?,
  ) {
    TODO("Implement matchedDirCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::mergeCoincidence(SkTSect* sect2) {
   *     double smallLimit = 0;
   *     do {
   *         // find the smallest unprocessed span
   *         SkTSpan* smaller = nullptr;
   *         SkTSpan* test = fCoincident;
   *         do {
   *             if (!test) {
   *                 return;
   *             }
   *             if (test->fStartT < smallLimit) {
   *                 continue;
   *             }
   *             if (smaller && smaller->fEndT < test->fStartT) {
   *                 continue;
   *             }
   *             smaller = test;
   *         } while ((test = test->fNext));
   *         if (!smaller) {
   *             return;
   *         }
   *         smallLimit = smaller->fEndT;
   *         // find next larger span
   *         SkTSpan* prior = nullptr;
   *         SkTSpan* larger = nullptr;
   *         SkTSpan* largerPrior = nullptr;
   *         test = fCoincident;
   *         do {
   *             if (test->fStartT < smaller->fEndT) {
   *                 continue;
   *             }
   *             SkOPASSERT(test->fStartT != smaller->fEndT);
   *             if (larger && larger->fStartT < test->fStartT) {
   *                 continue;
   *             }
   *             largerPrior = prior;
   *             larger = test;
   *         } while ((void) (prior = test), (test = test->fNext));
   *         if (!larger) {
   *             continue;
   *         }
   *         // check middle t value to see if it is coincident as well
   *         double midT = (smaller->fEndT + larger->fStartT) / 2;
   *         SkDPoint midPt = fCurve.ptAtT(midT);
   *         SkTCoincident coin;
   *         coin.setPerp(fCurve, midT, midPt, sect2->fCurve);
   *         if (coin.isMatch()) {
   *             smaller->fEndT = larger->fEndT;
   *             smaller->fCoinEnd = larger->fCoinEnd;
   *             if (largerPrior) {
   *                 largerPrior->fNext = larger->fNext;
   *                 largerPrior->validate();
   *             } else {
   *                 fCoincident = larger->fNext;
   *             }
   *         }
   *     } while (true);
   * }
   * ```
   */
  private fun mergeCoincidence(sect2: SkTSect?) {
    TODO("Implement mergeCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& pointLast() const {
   *         return fCurve[fCurve.pointLast()];
   *     }
   * ```
   */
  private fun pointLast(): SkDPoint {
    TODO("Implement pointLast")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::prev(
   *         SkTSpan* span) const {
   *     SkTSpan* result = nullptr;
   *     SkTSpan* test = fHead;
   *     while (span != test) {
   *         result = test;
   *         test = test->fNext;
   *         SkASSERT(test);
   *     }
   *     return result;
   * }
   * ```
   */
  private fun prev(span: SkTSpan?): SkTSpan {
    TODO("Implement prev")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::removeByPerpendicular(SkTSect* opp) {
   *     SkTSpan* test = fHead;
   *     SkTSpan* next;
   *     do {
   *         next = test->fNext;
   *         if (test->fCoinStart.perpT() < 0 || test->fCoinEnd.perpT() < 0) {
   *             continue;
   *         }
   *         SkDVector startV = test->fCoinStart.perpPt() - test->pointFirst();
   *         SkDVector endV = test->fCoinEnd.perpPt() - test->pointLast();
   * #if DEBUG_T_SECT
   *         SkDebugf("%s startV=(%1.9g,%1.9g) endV=(%1.9g,%1.9g) dot=%1.9g\n", __FUNCTION__,
   *                 startV.fX, startV.fY, endV.fX, endV.fY, startV.dot(endV));
   * #endif
   *         if (startV.dot(endV) <= 0) {
   *             continue;
   *         }
   *         if (!this->removeSpans(test, opp)) {
   *             return false;
   *         }
   *     } while ((test = next));
   *     return true;
   * }
   * ```
   */
  private fun removeByPerpendicular(opp: SkTSect?): Boolean {
    TODO("Implement removeByPerpendicular")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::recoverCollapsed() {
   *     SkTSpan* deleted = fDeleted;
   *     while (deleted) {
   *         SkTSpan* delNext = deleted->fNext;
   *         if (deleted->fCollapsed) {
   *             SkTSpan** spanPtr = &fHead;
   *             while (*spanPtr && (*spanPtr)->fEndT <= deleted->fStartT) {
   *                 spanPtr = &(*spanPtr)->fNext;
   *             }
   *             deleted->fNext = *spanPtr;
   *             *spanPtr = deleted;
   *         }
   *         deleted = delNext;
   *     }
   * }
   * ```
   */
  private fun recoverCollapsed() {
    TODO("Implement recoverCollapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::removeCoincident(SkTSpan* span, bool isBetween) {
   *     if (!this->unlinkSpan(span)) {
   *         return false;
   *     }
   *     if (isBetween || between(0, span->fCoinStart.perpT(), 1)) {
   *         --fActiveCount;
   *         span->fNext = fCoincident;
   *         fCoincident = span;
   *     } else {
   *         this->markSpanGone(span);
   *     }
   *     return true;
   * }
   * ```
   */
  private fun removeCoincident(span: SkTSpan?, isBetween: Boolean): Boolean {
    TODO("Implement removeCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::removeAllBut(const SkTSpan* keep,
   *         SkTSpan* span, SkTSect* opp) {
   *     const SkTSpanBounded* testBounded = span->fBounded;
   *     while (testBounded) {
   *         SkTSpan* bounded = testBounded->fBounded;
   *         const SkTSpanBounded* next = testBounded->fNext;
   *         // may have been deleted when opp did 'remove all but'
   *         if (bounded != keep && !bounded->fDeleted) {
   *             SkAssertResult(SkDEBUGCODE(!) span->removeBounded(bounded));
   *             if (bounded->removeBounded(span)) {
   *                 opp->removeSpan(bounded);
   *             }
   *         }
   *         testBounded = next;
   *     }
   *     SkASSERT(!span->fDeleted);
   *     SkASSERT(span->findOppSpan(keep));
   *     SkASSERT(keep->findOppSpan(span));
   * }
   * ```
   */
  private fun removeAllBut(
    keep: SkTSpan?,
    span: SkTSpan?,
    opp: SkTSect?,
  ) {
    TODO("Implement removeAllBut")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::removeSpan(SkTSpan* span) {\
   *     this->removedEndCheck(span);
   *     if (!this->unlinkSpan(span)) {
   *         return false;
   *     }
   *     return this->markSpanGone(span);
   * }
   * ```
   */
  private fun removeSpan(span: SkTSpan?): Boolean {
    TODO("Implement removeSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::removeSpanRange(SkTSpan* first,
   *         SkTSpan* last) {
   *     if (first == last) {
   *         return;
   *     }
   *     SkTSpan* span = first;
   *     SkASSERT(span);
   *     SkTSpan* final = last->fNext;
   *     SkTSpan* next = span->fNext;
   *     while ((span = next) && span != final) {
   *         next = span->fNext;
   *         this->markSpanGone(span);
   *     }
   *     if (final) {
   *         final->fPrev = first;
   *     }
   *     first->fNext = final;
   *     // world may not be ready for validation here
   *     first->validate();
   * }
   * ```
   */
  private fun removeSpanRange(first: SkTSpan?, last: SkTSpan?) {
    TODO("Implement removeSpanRange")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::removeSpans(SkTSpan* span,
   *         SkTSect* opp) {
   *     SkTSpanBounded* bounded = span->fBounded;
   *     while (bounded) {
   *         SkTSpan* spanBounded = bounded->fBounded;
   *         SkTSpanBounded* next = bounded->fNext;
   *         if (span->removeBounded(spanBounded)) {  // shuffles last into position 0
   *             this->removeSpan(span);
   *         }
   *         if (spanBounded->removeBounded(span)) {
   *             opp->removeSpan(spanBounded);
   *         }
   *         if (span->fDeleted && opp->hasBounded(span)) {
   *             return false;
   *         }
   *         bounded = next;
   *     }
   *     return true;
   * }
   * ```
   */
  private fun removeSpans(span: SkTSpan?, opp: SkTSect?): Boolean {
    TODO("Implement removeSpans")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::removedEndCheck(SkTSpan* span) {
   *     if (!span->fStartT) {
   *         fRemovedStartT = true;
   *     }
   *     if (1 == span->fEndT) {
   *         fRemovedEndT = true;
   *     }
   * }
   * ```
   */
  private fun removedEndCheck(span: SkTSpan?) {
    TODO("Implement removedEndCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetRemovedEnds() {
   *         fRemovedStartT = fRemovedEndT = false;
   *     }
   * ```
   */
  private fun resetRemovedEnds() {
    TODO("Implement resetRemovedEnds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::spanAtT(double t,
   *         SkTSpan** priorSpan) {
   *     SkTSpan* test = fHead;
   *     SkTSpan* prev = nullptr;
   *     while (test && test->fEndT < t) {
   *         prev = test;
   *         test = test->fNext;
   *     }
   *     *priorSpan = prev;
   *     return test && test->fStartT <= t ? test : nullptr;
   * }
   * ```
   */
  private fun spanAtT(t: Double, priorSpan: Int?): SkTSpan {
    TODO("Implement spanAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTSpan* SkTSect::tail() {
   *     SkTSpan* result = fHead;
   *     SkTSpan* next = fHead;
   *     int safetyNet = 100000;
   *     while ((next = next->fNext)) {
   *         if (!--safetyNet) {
   *             return nullptr;
   *         }
   *         if (next->fEndT > result->fEndT) {
   *             result = next;
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  private fun tail(): SkTSpan {
    TODO("Implement tail")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::trim(SkTSpan* span,
   *         SkTSect* opp) {
   *     FAIL_IF(!span->initBounds(fCurve));
   *     const SkTSpanBounded* testBounded = span->fBounded;
   *     while (testBounded) {
   *         SkTSpan* test = testBounded->fBounded;
   *         const SkTSpanBounded* next = testBounded->fNext;
   *         int oppSects, sects = this->intersects(span, opp, test, &oppSects);
   *         if (sects >= 1) {
   *             if (oppSects == 2) {
   *                 test->initBounds(opp->fCurve);
   *                 opp->removeAllBut(span, test, this);
   *             }
   *             if (sects == 2) {
   *                 span->initBounds(fCurve);
   *                 this->removeAllBut(test, span, opp);
   *                 return true;
   *             }
   *         } else {
   *             if (span->removeBounded(test)) {
   *                 this->removeSpan(span);
   *             }
   *             if (test->removeBounded(span)) {
   *                 opp->removeSpan(test);
   *             }
   *         }
   *         testBounded = next;
   *     }
   *     return true;
   * }
   * ```
   */
  private fun trim(span: SkTSpan?, opp: SkTSect?): Boolean {
    TODO("Implement trim")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::unlinkSpan(SkTSpan* span) {
   *     SkTSpan* prev = span->fPrev;
   *     SkTSpan* next = span->fNext;
   *     if (prev) {
   *         prev->fNext = next;
   *         if (next) {
   *             next->fPrev = prev;
   *             if (next->fStartT > next->fEndT) {
   *                 return false;
   *             }
   *             // world may not be ready for validate here
   *             next->validate();
   *         }
   *     } else {
   *         fHead = next;
   *         if (next) {
   *             next->fPrev = nullptr;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  private fun unlinkSpan(span: SkTSpan?): Boolean {
    TODO("Implement unlinkSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTSect::updateBounded(SkTSpan* first,
   *         SkTSpan* last, SkTSpan* oppFirst) {
   *     SkTSpan* test = first;
   *     const SkTSpan* final = last->next();
   *     bool deleteSpan = false;
   *     do {
   *         deleteSpan |= test->removeAllBounded();
   *     } while ((test = test->fNext) != final && test);
   *     first->fBounded = nullptr;
   *     first->addBounded(oppFirst, &fHeap);
   *     // cannot call validate until remove span range is called
   *     return deleteSpan;
   * }
   * ```
   */
  private fun updateBounded(
    first: SkTSpan?,
    last: SkTSpan?,
    oppFirst: SkTSpan?,
  ): Boolean {
    TODO("Implement updateBounded")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTSect::validate() const {
   * #if DEBUG_VALIDATE
   *     int count = 0;
   *     double last = 0;
   *     if (fHead) {
   *         const SkTSpan* span = fHead;
   *         SkASSERT(!span->fPrev);
   *         const SkTSpan* next;
   *         do {
   *             span->validate();
   *             SkASSERT(span->fStartT >= last);
   *             last = span->fEndT;
   *             ++count;
   *             next = span->fNext;
   *             SkASSERT(next != span);
   *         } while ((span = next) != nullptr);
   *     }
   *     SkASSERT(count == fActiveCount);
   * #endif
   * #if DEBUG_T_SECT
   *     SkASSERT(fActiveCount <= fDebugAllocatedCount);
   *     int deletedCount = 0;
   *     const SkTSpan* deleted = fDeleted;
   *     while (deleted) {
   *         ++deletedCount;
   *         deleted = deleted->fNext;
   *     }
   *     const SkTSpan* coincident = fCoincident;
   *     while (coincident) {
   *         ++deletedCount;
   *         coincident = coincident->fNext;
   *     }
   *     SkASSERT(fActiveCount + deletedCount == fDebugAllocatedCount);
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
   * void SkTSect::validateBounded() const {
   * #if DEBUG_VALIDATE
   *     if (!fHead) {
   *         return;
   *     }
   *     const SkTSpan* span = fHead;
   *     do {
   *         span->validateBounded();
   *     } while ((span = span->fNext) != nullptr);
   * #endif
   * }
   * ```
   */
  private fun validateBounded() {
    TODO("Implement validateBounded")
  }

  public companion object {
    public val kZeroS1Set: Int = TODO("Initialize kZeroS1Set")

    public val kOneS1Set: Int = TODO("Initialize kOneS1Set")

    public val kZeroS2Set: Int = TODO("Initialize kZeroS2Set")

    public val kOneS2Set: Int = TODO("Initialize kOneS2Set")

    /**
     * C++ original:
     * ```cpp
     * void SkTSect::BinarySearch(SkTSect* sect1,
     *         SkTSect* sect2, SkIntersections* intersections) {
     * #if DEBUG_T_SECT_DUMP > 1
     *     gDumpTSectNum = 0;
     * #endif
     *     SkDEBUGCODE(sect1->fOppSect = sect2);
     *     SkDEBUGCODE(sect2->fOppSect = sect1);
     *     intersections->reset();
     *     intersections->setMax(sect1->fCurve.maxIntersections() + 4);  // give extra for slop
     *     SkTSpan* span1 = sect1->fHead;
     *     SkTSpan* span2 = sect2->fHead;
     *     int oppSect, sect = sect1->intersects(span1, sect2, span2, &oppSect);
     * //    SkASSERT(between(0, sect, 2));
     *     if (!sect) {
     *         return;
     *     }
     *     if (sect == 2 && oppSect == 2) {
     *         (void) EndsEqual(sect1, sect2, intersections);
     *         return;
     *     }
     *     span1->addBounded(span2, &sect1->fHeap);
     *     span2->addBounded(span1, &sect2->fHeap);
     *     const int kMaxCoinLoopCount = 8;
     *     int coinLoopCount = kMaxCoinLoopCount;
     *     double start1s SK_INIT_TO_AVOID_WARNING;
     *     double start1e SK_INIT_TO_AVOID_WARNING;
     *     do {
     *         // find the largest bounds
     *         SkTSpan* largest1 = sect1->boundsMax();
     *         if (!largest1) {
     *             if (sect1->fHung) {
     *                 return;
     *             }
     *             break;
     *         }
     *         SkTSpan* largest2 = sect2->boundsMax();
     *         // split it
     *         if (!largest2 || (largest1 && (largest1->fBoundsMax > largest2->fBoundsMax
     *                 || (!largest1->fCollapsed && largest2->fCollapsed)))) {
     *             if (sect2->fHung) {
     *                 return;
     *             }
     *             if (largest1->fCollapsed) {
     *                 break;
     *             }
     *             sect1->resetRemovedEnds();
     *             sect2->resetRemovedEnds();
     *             // trim parts that don't intersect the opposite
     *             SkTSpan* half1 = sect1->addOne();
     *             SkDEBUGCODE(half1->debugSetGlobalState(sect1->globalState()));
     *             if (!half1->split(largest1, &sect1->fHeap)) {
     *                 break;
     *             }
     *             if (!sect1->trim(largest1, sect2)) {
     *                 SkOPOBJASSERT(intersections, 0);
     *                 return;
     *             }
     *             if (!sect1->trim(half1, sect2)) {
     *                 SkOPOBJASSERT(intersections, 0);
     *                 return;
     *             }
     *         } else {
     *             if (largest2->fCollapsed) {
     *                 break;
     *             }
     *             sect1->resetRemovedEnds();
     *             sect2->resetRemovedEnds();
     *             // trim parts that don't intersect the opposite
     *             SkTSpan* half2 = sect2->addOne();
     *             SkDEBUGCODE(half2->debugSetGlobalState(sect2->globalState()));
     *             if (!half2->split(largest2, &sect2->fHeap)) {
     *                 break;
     *             }
     *             if (!sect2->trim(largest2, sect1)) {
     *                 SkOPOBJASSERT(intersections, 0);
     *                 return;
     *             }
     *             if (!sect2->trim(half2, sect1)) {
     *                 SkOPOBJASSERT(intersections, 0);
     *                 return;
     *             }
     *         }
     *         sect1->validate();
     *         sect2->validate();
     * #if DEBUG_T_SECT_LOOP_COUNT
     *         intersections->debugBumpLoopCount(SkIntersections::kIterations_DebugLoop);
     * #endif
     *         // if there are 9 or more continuous spans on both sects, suspect coincidence
     *         if (sect1->fActiveCount >= COINCIDENT_SPAN_COUNT
     *                 && sect2->fActiveCount >= COINCIDENT_SPAN_COUNT) {
     *             if (coinLoopCount == kMaxCoinLoopCount) {
     *                 start1s = sect1->fHead->fStartT;
     *                 start1e = sect1->tail()->fEndT;
     *             }
     *             if (!sect1->coincidentCheck(sect2)) {
     *                 return;
     *             }
     *             sect1->validate();
     *             sect2->validate();
     * #if DEBUG_T_SECT_LOOP_COUNT
     *             intersections->debugBumpLoopCount(SkIntersections::kCoinCheck_DebugLoop);
     * #endif
     *             if (!--coinLoopCount && sect1->fHead && sect2->fHead) {
     *                 /* All known working cases resolve in two tries. Sadly, cubicConicTests[0]
     *                    gets stuck in a loop. It adds an extension to allow a coincident end
     *                    perpendicular to track its intersection in the opposite curve. However,
     *                    the bounding box of the extension does not intersect the original curve,
     *                    so the extension is discarded, only to be added again the next time around. */
     *                 sect1->coincidentForce(sect2, start1s, start1e);
     *                 sect1->validate();
     *                 sect2->validate();
     *             }
     *         }
     *         if (sect1->fActiveCount >= COINCIDENT_SPAN_COUNT
     *                 && sect2->fActiveCount >= COINCIDENT_SPAN_COUNT) {
     *             if (!sect1->fHead) {
     *                 return;
     *             }
     *             sect1->computePerpendiculars(sect2, sect1->fHead, sect1->tail());
     *             if (!sect2->fHead) {
     *                 return;
     *             }
     *             sect2->computePerpendiculars(sect1, sect2->fHead, sect2->tail());
     *             if (!sect1->removeByPerpendicular(sect2)) {
     *                 return;
     *             }
     *             sect1->validate();
     *             sect2->validate();
     * #if DEBUG_T_SECT_LOOP_COUNT
     *             intersections->debugBumpLoopCount(SkIntersections::kComputePerp_DebugLoop);
     * #endif
     *             if (sect1->collapsed() > sect1->fCurve.maxIntersections()) {
     *                 break;
     *             }
     *         }
     * #if DEBUG_T_SECT_DUMP
     *         sect1->dumpBoth(sect2);
     * #endif
     *         if (!sect1->fHead || !sect2->fHead) {
     *             break;
     *         }
     *     } while (true);
     *     SkTSpan* coincident = sect1->fCoincident;
     *     if (coincident) {
     *         // if there is more than one coincident span, check loosely to see if they should be joined
     *         if (coincident->fNext) {
     *             sect1->mergeCoincidence(sect2);
     *             coincident = sect1->fCoincident;
     *         }
     *         SkASSERT(sect2->fCoincident);  // courtesy check : coincidence only looks at sect 1
     *         do {
     *             if (!coincident) {
     *                 return;
     *             }
     *             if (!coincident->fCoinStart.isMatch()) {
     *                 continue;
     *             }
     *             if (!coincident->fCoinEnd.isMatch()) {
     *                 continue;
     *             }
     *             double perpT = coincident->fCoinStart.perpT();
     *             if (perpT < 0) {
     *                 return;
     *             }
     *             int index = intersections->insertCoincident(coincident->fStartT,
     *                     perpT, coincident->pointFirst());
     *             if ((intersections->insertCoincident(coincident->fEndT,
     *                     coincident->fCoinEnd.perpT(),
     *                     coincident->pointLast()) < 0) && index >= 0) {
     *                 intersections->clearCoincidence(index);
     *             }
     *         } while ((coincident = coincident->fNext));
     *     }
     *     int zeroOneSet = EndsEqual(sect1, sect2, intersections);
     * //    if (!sect1->fHead || !sect2->fHead) {
     *         // if the final iteration contains an end (0 or 1),
     *         if (sect1->fRemovedStartT && !(zeroOneSet & kZeroS1Set)) {
     *             SkTCoincident perp;   // intersect perpendicular with opposite curve
     *             perp.setPerp(sect1->fCurve, 0, sect1->fCurve[0], sect2->fCurve);
     *             if (perp.isMatch()) {
     *                 intersections->insert(0, perp.perpT(), perp.perpPt());
     *             }
     *         }
     *         if (sect1->fRemovedEndT && !(zeroOneSet & kOneS1Set)) {
     *             SkTCoincident perp;
     *             perp.setPerp(sect1->fCurve, 1, sect1->pointLast(), sect2->fCurve);
     *             if (perp.isMatch()) {
     *                 intersections->insert(1, perp.perpT(), perp.perpPt());
     *             }
     *         }
     *         if (sect2->fRemovedStartT && !(zeroOneSet & kZeroS2Set)) {
     *             SkTCoincident perp;
     *             perp.setPerp(sect2->fCurve, 0, sect2->fCurve[0], sect1->fCurve);
     *             if (perp.isMatch()) {
     *                 intersections->insert(perp.perpT(), 0, perp.perpPt());
     *             }
     *         }
     *         if (sect2->fRemovedEndT && !(zeroOneSet & kOneS2Set)) {
     *             SkTCoincident perp;
     *             perp.setPerp(sect2->fCurve, 1, sect2->pointLast(), sect1->fCurve);
     *             if (perp.isMatch()) {
     *                 intersections->insert(perp.perpT(), 1, perp.perpPt());
     *             }
     *         }
     * //    }
     *     if (!sect1->fHead || !sect2->fHead) {
     *         return;
     *     }
     *     sect1->recoverCollapsed();
     *     sect2->recoverCollapsed();
     *     SkTSpan* result1 = sect1->fHead;
     *     // check heads and tails for zero and ones and insert them if we haven't already done so
     *     const SkTSpan* head1 = result1;
     *     if (!(zeroOneSet & kZeroS1Set) && approximately_less_than_zero(head1->fStartT)) {
     *         const SkDPoint& start1 = sect1->fCurve[0];
     *         if (head1->isBounded()) {
     *             double t = head1->closestBoundedT(start1);
     *             if (sect2->fCurve.ptAtT(t).approximatelyEqual(start1)) {
     *                 intersections->insert(0, t, start1);
     *             }
     *         }
     *     }
     *     const SkTSpan* head2 = sect2->fHead;
     *     if (!(zeroOneSet & kZeroS2Set) && approximately_less_than_zero(head2->fStartT)) {
     *         const SkDPoint& start2 = sect2->fCurve[0];
     *         if (head2->isBounded()) {
     *             double t = head2->closestBoundedT(start2);
     *             if (sect1->fCurve.ptAtT(t).approximatelyEqual(start2)) {
     *                 intersections->insert(t, 0, start2);
     *             }
     *         }
     *     }
     *     if (!(zeroOneSet & kOneS1Set)) {
     *         const SkTSpan* tail1 = sect1->tail();
     *         if (!tail1) {
     *             return;
     *         }
     *         if (approximately_greater_than_one(tail1->fEndT)) {
     *             const SkDPoint& end1 = sect1->pointLast();
     *             if (tail1->isBounded()) {
     *                 double t = tail1->closestBoundedT(end1);
     *                 if (sect2->fCurve.ptAtT(t).approximatelyEqual(end1)) {
     *                     intersections->insert(1, t, end1);
     *                 }
     *             }
     *         }
     *     }
     *     if (!(zeroOneSet & kOneS2Set)) {
     *         const SkTSpan* tail2 = sect2->tail();
     *         if (!tail2) {
     *             return;
     *         }
     *         if (approximately_greater_than_one(tail2->fEndT)) {
     *             const SkDPoint& end2 = sect2->pointLast();
     *             if (tail2->isBounded()) {
     *                 double t = tail2->closestBoundedT(end2);
     *                 if (sect1->fCurve.ptAtT(t).approximatelyEqual(end2)) {
     *                     intersections->insert(t, 1, end2);
     *                 }
     *             }
     *         }
     *     }
     *     SkClosestSect closest;
     *     do {
     *         while (result1 && result1->fCoinStart.isMatch() && result1->fCoinEnd.isMatch()) {
     *             result1 = result1->fNext;
     *         }
     *         if (!result1) {
     *             break;
     *         }
     *         SkTSpan* result2 = sect2->fHead;
     *         while (result2) {
     *             closest.find(result1, result2  SkDEBUGPARAMS(intersections));
     *             result2 = result2->fNext;
     *         }
     *     } while ((result1 = result1->fNext));
     *     closest.finish(intersections);
     *     // if there is more than one intersection and it isn't already coincident, check
     *     int last = intersections->used() - 1;
     *     for (int index = 0; index < last; ) {
     *         if (intersections->isCoincident(index) && intersections->isCoincident(index + 1)) {
     *             ++index;
     *             continue;
     *         }
     *         double midT = ((*intersections)[0][index] + (*intersections)[0][index + 1]) / 2;
     *         SkDPoint midPt = sect1->fCurve.ptAtT(midT);
     *         // intersect perpendicular with opposite curve
     *         SkTCoincident perp;
     *         perp.setPerp(sect1->fCurve, midT, midPt, sect2->fCurve);
     *         if (!perp.isMatch()) {
     *             ++index;
     *             continue;
     *         }
     *         if (intersections->isCoincident(index)) {
     *             intersections->removeOne(index);
     *             --last;
     *         } else if (intersections->isCoincident(index + 1)) {
     *             intersections->removeOne(index + 1);
     *             --last;
     *         } else {
     *             intersections->setCoincident(index++);
     *         }
     *         intersections->setCoincident(index);
     *     }
     *     SkOPOBJASSERT(intersections, intersections->used() <= sect1->fCurve.maxIntersections());
     * }
     * ```
     */
    public fun binarySearch(
      sect1: SkTSect?,
      sect2: SkTSect?,
      intersections: SkIntersections?,
    ) {
      TODO("Implement binarySearch")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkTSect::EndsEqual(const SkTSect* sect1,
     *         const SkTSect* sect2, SkIntersections* intersections) {
     *     int zeroOneSet = 0;
     *     if (sect1->fCurve[0] == sect2->fCurve[0]) {
     *         zeroOneSet |= kZeroS1Set | kZeroS2Set;
     *         intersections->insert(0, 0, sect1->fCurve[0]);
     *     }
     *     if (sect1->fCurve[0] == sect2->pointLast()) {
     *         zeroOneSet |= kZeroS1Set | kOneS2Set;
     *         intersections->insert(0, 1, sect1->fCurve[0]);
     *     }
     *     if (sect1->pointLast() == sect2->fCurve[0]) {
     *         zeroOneSet |= kOneS1Set | kZeroS2Set;
     *         intersections->insert(1, 0, sect1->pointLast());
     *     }
     *     if (sect1->pointLast() == sect2->pointLast()) {
     *         zeroOneSet |= kOneS1Set | kOneS2Set;
     *             intersections->insert(1, 1, sect1->pointLast());
     *     }
     *     // check for zero
     *     if (!(zeroOneSet & (kZeroS1Set | kZeroS2Set))
     *             && sect1->fCurve[0].approximatelyEqual(sect2->fCurve[0])) {
     *         zeroOneSet |= kZeroS1Set | kZeroS2Set;
     *         intersections->insertNear(0, 0, sect1->fCurve[0], sect2->fCurve[0]);
     *     }
     *     if (!(zeroOneSet & (kZeroS1Set | kOneS2Set))
     *             && sect1->fCurve[0].approximatelyEqual(sect2->pointLast())) {
     *         zeroOneSet |= kZeroS1Set | kOneS2Set;
     *         intersections->insertNear(0, 1, sect1->fCurve[0], sect2->pointLast());
     *     }
     *     // check for one
     *     if (!(zeroOneSet & (kOneS1Set | kZeroS2Set))
     *             && sect1->pointLast().approximatelyEqual(sect2->fCurve[0])) {
     *         zeroOneSet |= kOneS1Set | kZeroS2Set;
     *         intersections->insertNear(1, 0, sect1->pointLast(), sect2->fCurve[0]);
     *     }
     *     if (!(zeroOneSet & (kOneS1Set | kOneS2Set))
     *             && sect1->pointLast().approximatelyEqual(sect2->pointLast())) {
     *         zeroOneSet |= kOneS1Set | kOneS2Set;
     *         intersections->insertNear(1, 1, sect1->pointLast(), sect2->pointLast());
     *     }
     *     return zeroOneSet;
     * }
     * ```
     */
    private fun endsEqual(
      sect1: SkTSect?,
      sect2: SkTSect?,
      intersections: SkIntersections?,
    ): Int {
      TODO("Implement endsEqual")
    }
  }
}
