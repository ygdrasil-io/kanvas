package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkOpPtT {
 * public:
 *     enum {
 *         kIsAlias = 1,
 *         kIsDuplicate = 1
 *     };
 *
 *     const SkOpPtT* active() const;
 *
 *     // please keep in sync with debugAddOpp()
 *     void addOpp(SkOpPtT* opp, SkOpPtT* oppPrev) {
 *         SkOpPtT* oldNext = this->fNext;
 *         SkASSERT(this != opp);
 *         this->fNext = opp;
 *         SkASSERT(oppPrev != oldNext);
 *         oppPrev->fNext = oldNext;
 *     }
 *
 *     bool alias() const;
 *     bool coincident() const { return fCoincident; }
 *     bool contains(const SkOpPtT* ) const;
 *     bool contains(const SkOpSegment*, const SkPoint& ) const;
 *     bool contains(const SkOpSegment*, double t) const;
 *     const SkOpPtT* contains(const SkOpSegment* ) const;
 *     SkOpContour* contour() const;
 *
 *     int debugID() const {
 *         return SkDEBUGRELEASE(fID, -1);
 *     }
 *
 *     void debugAddOpp(const SkOpPtT* opp, const SkOpPtT* oppPrev) const;
 *     const SkOpAngle* debugAngle(int id) const;
 *     const SkOpCoincidence* debugCoincidence() const;
 *     bool debugContains(const SkOpPtT* ) const;
 *     const SkOpPtT* debugContains(const SkOpSegment* check) const;
 *     SkOpContour* debugContour(int id) const;
 *     const SkOpPtT* debugEnder(const SkOpPtT* end) const;
 *     int debugLoopLimit(bool report) const;
 *     bool debugMatchID(int id) const;
 *     const SkOpPtT* debugOppPrev(const SkOpPtT* opp) const;
 *     const SkOpPtT* debugPtT(int id) const;
 *     void debugResetCoinT() const;
 *     const SkOpSegment* debugSegment(int id) const;
 *     void debugSetCoinT(int ) const;
 *     const SkOpSpanBase* debugSpan(int id) const;
 *     void debugValidate() const;
 *
 *     bool deleted() const {
 *         return fDeleted;
 *     }
 *
 *     bool duplicate() const {
 *         return fDuplicatePt;
 *     }
 *
 *     void dump() const;  // available to testing only
 *     void dumpAll() const;
 *     void dumpBase() const;
 *
 *     const SkOpPtT* find(const SkOpSegment* ) const;
 *     SkOpGlobalState* globalState() const;
 *     void init(SkOpSpanBase* , double t, const SkPoint& , bool dup);
 *
 *     void insert(SkOpPtT* span) {
 *         SkASSERT(span != this);
 *         span->fNext = fNext;
 *         fNext = span;
 *     }
 *
 *     const SkOpPtT* next() const {
 *         return fNext;
 *     }
 *
 *     SkOpPtT* next() {
 *         return fNext;
 *     }
 *
 *     bool onEnd() const;
 *
 *     // returns nullptr if this is already in the opp ptT loop
 *     SkOpPtT* oppPrev(const SkOpPtT* opp) const {
 *         // find the fOpp ptr to opp
 *         SkOpPtT* oppPrev = opp->fNext;
 *         if (oppPrev == this) {
 *             return nullptr;
 *         }
 *         while (oppPrev->fNext != opp) {
 *             oppPrev = oppPrev->fNext;
 *             if (oppPrev == this) {
 *                 return nullptr;
 *             }
 *         }
 *         return oppPrev;
 *     }
 *
 *     static bool Overlaps(const SkOpPtT* s1, const SkOpPtT* e1, const SkOpPtT* s2,
 *             const SkOpPtT* e2, const SkOpPtT** sOut, const SkOpPtT** eOut) {
 *         const SkOpPtT* start1 = s1->fT < e1->fT ? s1 : e1;
 *         const SkOpPtT* start2 = s2->fT < e2->fT ? s2 : e2;
 *         *sOut = between(s1->fT, start2->fT, e1->fT) ? start2
 *                 : between(s2->fT, start1->fT, e2->fT) ? start1 : nullptr;
 *         const SkOpPtT* end1 = s1->fT < e1->fT ? e1 : s1;
 *         const SkOpPtT* end2 = s2->fT < e2->fT ? e2 : s2;
 *         *eOut = between(s1->fT, end2->fT, e1->fT) ? end2
 *                 : between(s2->fT, end1->fT, e2->fT) ? end1 : nullptr;
 *         if (*sOut == *eOut) {
 *             SkOPOBJASSERT(s1, start1->fT >= end2->fT || start2->fT >= end1->fT);
 *             return false;
 *         }
 *         SkASSERT(!*sOut || *sOut != *eOut);
 *         return *sOut && *eOut;
 *     }
 *
 *     bool ptAlreadySeen(const SkOpPtT* head) const;
 *     SkOpPtT* prev();
 *
 *     const SkOpSegment* segment() const;
 *     SkOpSegment* segment();
 *
 *     void setCoincident() const {
 *         SkOPASSERT(!fDeleted);
 *         fCoincident = true;
 *     }
 *
 *     void setDeleted();
 *
 *     void setSpan(const SkOpSpanBase* span) {
 *         fSpan = const_cast<SkOpSpanBase*>(span);
 *     }
 *
 *     const SkOpSpanBase* span() const {
 *         return fSpan;
 *     }
 *
 *     SkOpSpanBase* span() {
 *         return fSpan;
 *     }
 *
 *     const SkOpPtT* starter(const SkOpPtT* end) const {
 *         return fT < end->fT ? this : end;
 *     }
 *
 *     double fT;
 *     SkPoint fPt;   // cache of point value at this t
 * protected:
 *     SkOpSpanBase* fSpan;  // contains winding data
 *     SkOpPtT* fNext;  // intersection on opposite curve or alias on this curve
 *     bool fDeleted;  // set if removed from span list
 *     bool fDuplicatePt;  // set if identical pt is somewhere in the next loop
 *     // below mutable since referrer is otherwise always const
 *     mutable bool fCoincident;  // set if at some point a coincident span pointed here
 *     SkDEBUGCODE(int fID;)
 * }
 * ```
 */
public data class SkOpPtT public constructor(
  /**
   * C++ original:
   * ```cpp
   * double fT
   * ```
   */
  public var fT: Double,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fPt
   * ```
   */
  public var fPt: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase* fSpan
   * ```
   */
  protected var fSpan: SkOpSpanBase?,
  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* fNext
   * ```
   */
  protected var fNext: SkOpPtT?,
  /**
   * C++ original:
   * ```cpp
   * bool fDeleted
   * ```
   */
  protected var fDeleted: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fDuplicatePt
   * ```
   */
  protected var fDuplicatePt: Boolean,
  /**
   * C++ original:
   * ```cpp
   * mutable bool fCoincident
   * ```
   */
  protected var fCoincident: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::active() const {
   *     if (!fDeleted) {
   *         return this;
   *     }
   *     const SkOpPtT* ptT = this;
   *     const SkOpPtT* stopPtT = ptT;
   *     while ((ptT = ptT->next()) != stopPtT) {
   *         if (ptT->fSpan == fSpan && !ptT->fDeleted) {
   *             return ptT;
   *         }
   *     }
   *     return nullptr; // should never return deleted; caller must abort
   * }
   * ```
   */
  public fun active(): SkOpPtT {
    TODO("Implement active")
  }

  /**
   * C++ original:
   * ```cpp
   * void addOpp(SkOpPtT* opp, SkOpPtT* oppPrev) {
   *         SkOpPtT* oldNext = this->fNext;
   *         SkASSERT(this != opp);
   *         this->fNext = opp;
   *         SkASSERT(oppPrev != oldNext);
   *         oppPrev->fNext = oldNext;
   *     }
   * ```
   */
  public fun addOpp(opp: SkOpPtT?, oppPrev: SkOpPtT?) {
    TODO("Implement addOpp")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::alias() const {
   *     return this->span()->ptT() != this;
   * }
   * ```
   */
  public fun alias(): Boolean {
    TODO("Implement alias")
  }

  /**
   * C++ original:
   * ```cpp
   * bool coincident() const { return fCoincident; }
   * ```
   */
  public fun coincident(): Boolean {
    TODO("Implement coincident")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::contains(const SkOpPtT* check) const {
   *     SkOPASSERT(this != check);
   *     const SkOpPtT* ptT = this;
   *     const SkOpPtT* stopPtT = ptT;
   *     while ((ptT = ptT->next()) != stopPtT) {
   *         if (ptT == check) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun contains(check: SkOpPtT?): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::contains(const SkOpSegment* segment, const SkPoint& pt) const {
   *     SkASSERT(this->segment() != segment);
   *     const SkOpPtT* ptT = this;
   *     const SkOpPtT* stopPtT = ptT;
   *     while ((ptT = ptT->next()) != stopPtT) {
   *         if (ptT->fPt == pt && ptT->segment() == segment) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun contains(segment: SkOpSegment?, pt: SkPoint): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::contains(const SkOpSegment* segment, double t) const {
   *     const SkOpPtT* ptT = this;
   *     const SkOpPtT* stopPtT = ptT;
   *     while ((ptT = ptT->next()) != stopPtT) {
   *         if (ptT->fT == t && ptT->segment() == segment) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun contains(segment: SkOpSegment?, t: Double): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::contains(const SkOpSegment* check) const {
   *     SkASSERT(this->segment() != check);
   *     const SkOpPtT* ptT = this;
   *     const SkOpPtT* stopPtT = ptT;
   *     while ((ptT = ptT->next()) != stopPtT) {
   *         if (ptT->segment() == check && !ptT->deleted()) {
   *             return ptT;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun contains(check: SkOpSegment?): SkOpPtT {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* SkOpPtT::contour() const {
   *     return segment()->contour();
   * }
   * ```
   */
  public fun contour(): SkOpContour {
    TODO("Implement contour")
  }

  /**
   * C++ original:
   * ```cpp
   * int debugID() const {
   *         return SkDEBUGRELEASE(fID, -1);
   *     }
   * ```
   */
  public fun debugID(): Int {
    TODO("Implement debugID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::debugAddOpp(const SkOpPtT* opp, const SkOpPtT* oppPrev) const {
   *     SkDEBUGCODE(const SkOpPtT* oldNext = this->fNext);
   *     SkASSERT(this != opp);
   * //    this->fNext = opp;
   *     SkASSERT(oppPrev != oldNext);
   * //    oppPrev->fNext = oldNext;
   * }
   * ```
   */
  public fun debugAddOpp(opp: SkOpPtT?, oppPrev: SkOpPtT?) {
    TODO("Implement debugAddOpp")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpAngle* SkOpPtT::debugAngle(int id) const {
   *     return this->span()->debugAngle(id);
   * }
   * ```
   */
  public fun debugAngle(id: Int): SkOpAngle {
    TODO("Implement debugAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpCoincidence* SkOpPtT::debugCoincidence() const {
   *     return this->span()->debugCoincidence();
   * }
   * ```
   */
  public fun debugCoincidence(): SkOpCoincidence {
    TODO("Implement debugCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::debugContains(const SkOpPtT* check) const {
   *     SkASSERT(this != check);
   *     const SkOpPtT* ptT = this;
   *     int links = 0;
   *     do {
   *         ptT = ptT->next();
   *         if (ptT == check) {
   *             return true;
   *         }
   *         ++links;
   *         const SkOpPtT* test = this;
   *         for (int index = 0; index < links; ++index) {
   *             if (ptT == test) {
   *                 return false;
   *             }
   *             test = test->next();
   *         }
   *     } while (true);
   * }
   * ```
   */
  public fun debugContains(check: SkOpPtT?): Boolean {
    TODO("Implement debugContains")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::debugContains(const SkOpSegment* check) const {
   *     SkASSERT(this->segment() != check);
   *     const SkOpPtT* ptT = this;
   *     int links = 0;
   *     do {
   *         ptT = ptT->next();
   *         if (ptT->segment() == check) {
   *             return ptT;
   *         }
   *         ++links;
   *         const SkOpPtT* test = this;
   *         for (int index = 0; index < links; ++index) {
   *             if (ptT == test) {
   *                 return nullptr;
   *             }
   *             test = test->next();
   *         }
   *     } while (true);
   * }
   * ```
   */
  public fun debugContains(check: SkOpSegment?): SkOpPtT {
    TODO("Implement debugContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* SkOpPtT::debugContour(int id) const {
   *     return this->span()->debugContour(id);
   * }
   * ```
   */
  public fun debugContour(id: Int): SkOpContour {
    TODO("Implement debugContour")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::debugEnder(const SkOpPtT* end) const {
   *     return fT < end->fT ? end : this;
   * }
   * ```
   */
  public fun debugEnder(end: SkOpPtT?): SkOpPtT {
    TODO("Implement debugEnder")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpPtT::debugLoopLimit(bool report) const {
   *     int loop = 0;
   *     const SkOpPtT* next = this;
   *     do {
   *         for (int check = 1; check < loop - 1; ++check) {
   *             const SkOpPtT* checkPtT = this->fNext;
   *             const SkOpPtT* innerPtT = checkPtT;
   *             for (int inner = check + 1; inner < loop; ++inner) {
   *                 innerPtT = innerPtT->fNext;
   *                 if (checkPtT == innerPtT) {
   *                     if (report) {
   *                         SkDebugf("*** bad ptT loop ***\n");
   *                     }
   *                     return loop;
   *                 }
   *             }
   *         }
   *         // there's nothing wrong with extremely large loop counts -- but this may appear to hang
   *         // by taking a very long time to figure out that no loop entry is a duplicate
   *         // -- and it's likely that a large loop count is indicative of a bug somewhere
   *         if (++loop > 1000) {
   *             SkDebugf("*** loop count exceeds 1000 ***\n");
   *             return 1000;
   *         }
   *     } while ((next = next->fNext) && next != this);
   *     return 0;
   * }
   * ```
   */
  public fun debugLoopLimit(report: Boolean): Int {
    TODO("Implement debugLoopLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::debugMatchID(int id) const {
   *     int limit = this->debugLoopLimit(false);
   *     int loop = 0;
   *     const SkOpPtT* ptT = this;
   *     do {
   *         if (ptT->debugID() == id) {
   *             return true;
   *         }
   *     } while ((!limit || ++loop <= limit) && (ptT = ptT->next()) && ptT != this);
   *     return false;
   * }
   * ```
   */
  public fun debugMatchID(id: Int): Boolean {
    TODO("Implement debugMatchID")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::debugOppPrev(const SkOpPtT* opp) const {
   *     return this->oppPrev(const_cast<SkOpPtT*>(opp));
   * }
   * ```
   */
  public fun debugOppPrev(opp: SkOpPtT?): SkOpPtT {
    TODO("Implement debugOppPrev")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::debugPtT(int id) const {
   *     return this->span()->debugPtT(id);
   * }
   * ```
   */
  public fun debugPtT(id: Int): SkOpPtT {
    TODO("Implement debugPtT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::debugResetCoinT() const {
   * #if DEBUG_COINCIDENCE_ORDER
   *     this->segment()->debugResetCoinT();
   * #endif
   * }
   * ```
   */
  public fun debugResetCoinT() {
    TODO("Implement debugResetCoinT")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* SkOpPtT::debugSegment(int id) const {
   *     return this->span()->debugSegment(id);
   * }
   * ```
   */
  public fun debugSegment(id: Int): SkOpSegment {
    TODO("Implement debugSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::debugSetCoinT(int index) const {
   * #if DEBUG_COINCIDENCE_ORDER
   *     this->segment()->debugSetCoinT(index, fT);
   * #endif
   * }
   * ```
   */
  public fun debugSetCoinT(index: Int) {
    TODO("Implement debugSetCoinT")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* SkOpPtT::debugSpan(int id) const {
   *     return this->span()->debugSpan(id);
   * }
   * ```
   */
  public fun debugSpan(id: Int): SkOpSpanBase {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::debugValidate() const {
   * #if DEBUG_COINCIDENCE
   *     if (this->globalState()->debugCheckHealth()) {
   *         return;
   *     }
   * #endif
   * #if DEBUG_VALIDATE
   *     SkOpPhase phase = contour()->globalState()->phase();
   *     if (phase == SkOpPhase::kIntersecting || phase == SkOpPhase::kFixWinding) {
   *         return;
   *     }
   *     SkASSERT(fNext);
   *     SkASSERT(fNext != this);
   *     SkASSERT(fNext->fNext);
   *     SkASSERT(debugLoopLimit(false) == 0);
   * #endif
   * }
   * ```
   */
  public fun debugValidate() {
    TODO("Implement debugValidate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool deleted() const {
   *         return fDeleted;
   *     }
   * ```
   */
  public fun deleted(): Boolean {
    TODO("Implement deleted")
  }

  /**
   * C++ original:
   * ```cpp
   * bool duplicate() const {
   *         return fDuplicatePt;
   *     }
   * ```
   */
  public fun duplicate(): Boolean {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::dump() const {
   *     SkDebugf("seg=%d span=%d ptT=%d",
   *             this->segment()->debugID(), this->span()->debugID(), this->debugID());
   *     this->dumpBase();
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
   * void SkOpPtT::dumpAll() const {
   *     contour()->indentDump();
   *     const SkOpPtT* next = this;
   *     int limit = debugLoopLimit(true);
   *     int loop = 0;
   *     do {
   *         SkDebugf("%.*s", contour()->debugIndent(), "        ");
   *         SkDebugf("seg=%d span=%d ptT=%d",
   *                 next->segment()->debugID(), next->span()->debugID(), next->debugID());
   *         next->dumpBase();
   *         SkDebugf("\n");
   *         if (limit && ++loop >= limit) {
   *             SkDebugf("*** abort loop ***\n");
   *             break;
   *         }
   *     } while ((next = next->fNext) && next != this);
   *     contour()->outdentDump();
   * }
   * ```
   */
  public fun dumpAll() {
    TODO("Implement dumpAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::dumpBase() const {
   *     SkDebugf(" t=%1.9g pt=(%1.9g,%1.9g)%s%s%s", this->fT, this->fPt.fX, this->fPt.fY,
   *             this->fCoincident ? " coin" : "",
   *             this->fDuplicatePt ? " dup" : "", this->fDeleted ? " deleted" : "");
   * }
   * ```
   */
  public fun dumpBase() {
    TODO("Implement dumpBase")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpPtT::find(const SkOpSegment* segment) const {
   *     const SkOpPtT* ptT = this;
   *     const SkOpPtT* stopPtT = ptT;
   *     do {
   *         if (ptT->segment() == segment && !ptT->deleted()) {
   *             return ptT;
   *         }
   *         ptT = ptT->fNext;
   *     } while (stopPtT != ptT);
   * //    SkASSERT(0);
   *     return nullptr;
   * }
   * ```
   */
  public fun find(segment: SkOpSegment?): SkOpPtT {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* SkOpPtT::globalState() const {
   *     return contour()->globalState();
   * }
   * ```
   */
  public fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::init(SkOpSpanBase* span, double t, const SkPoint& pt, bool duplicate) {
   *     fT = t;
   *     fPt = pt;
   *     fSpan = span;
   *     fNext = this;
   *     fDuplicatePt = duplicate;
   *     fDeleted = false;
   *     fCoincident = false;
   *     SkDEBUGCODE(fID = span->globalState()->nextPtTID());
   * }
   * ```
   */
  public fun `init`(
    span: SkOpSpanBase?,
    t: Double,
    pt: SkPoint,
    dup: Boolean,
  ) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void insert(SkOpPtT* span) {
   *         SkASSERT(span != this);
   *         span->fNext = fNext;
   *         fNext = span;
   *     }
   * ```
   */
  public fun insert(span: SkOpPtT?) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* next() const {
   *         return fNext;
   *     }
   * ```
   */
  public fun next(): SkOpPtT {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* next() {
   *         return fNext;
   *     }
   * ```
   */
  public fun onEnd(): Boolean {
    TODO("Implement onEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::onEnd() const {
   *     const SkOpSpanBase* span = this->span();
   *     if (span->ptT() != this) {
   *         return false;
   *     }
   *     const SkOpSegment* segment = this->segment();
   *     return span == segment->head() || span == segment->tail();
   * }
   * ```
   */
  public fun oppPrev(opp: SkOpPtT?): SkOpPtT {
    TODO("Implement oppPrev")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* oppPrev(const SkOpPtT* opp) const {
   *         // find the fOpp ptr to opp
   *         SkOpPtT* oppPrev = opp->fNext;
   *         if (oppPrev == this) {
   *             return nullptr;
   *         }
   *         while (oppPrev->fNext != opp) {
   *             oppPrev = oppPrev->fNext;
   *             if (oppPrev == this) {
   *                 return nullptr;
   *             }
   *         }
   *         return oppPrev;
   *     }
   * ```
   */
  public fun ptAlreadySeen(head: SkOpPtT?): Boolean {
    TODO("Implement ptAlreadySeen")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpPtT::ptAlreadySeen(const SkOpPtT* check) const {
   *     while (this != check) {
   *         if (this->fPt == check->fPt) {
   *             return true;
   *         }
   *         check = check->fNext;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun prev(): SkOpPtT {
    TODO("Implement prev")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* SkOpPtT::prev() {
   *     SkOpPtT* result = this;
   *     SkOpPtT* next = this;
   *     while ((next = next->fNext) != this) {
   *         result = next;
   *     }
   *     SkASSERT(result->fNext == this);
   *     return result;
   * }
   * ```
   */
  public fun segment(): SkOpSegment {
    TODO("Implement segment")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* SkOpPtT::segment() const {
   *     return span()->segment();
   * }
   * ```
   */
  public fun setCoincident() {
    TODO("Implement setCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* SkOpPtT::segment() {
   *     return span()->segment();
   * }
   * ```
   */
  public fun setDeleted() {
    TODO("Implement setDeleted")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCoincident() const {
   *         SkOPASSERT(!fDeleted);
   *         fCoincident = true;
   *     }
   * ```
   */
  public fun setSpan(span: SkOpSpanBase?) {
    TODO("Implement setSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpPtT::setDeleted() {
   *     SkASSERT(this->span()->debugDeleted() || this->span()->ptT() != this);
   *     SkOPASSERT(!fDeleted);
   *     fDeleted = true;
   * }
   * ```
   */
  public fun span(): SkOpSpanBase {
    TODO("Implement span")
  }

  /**
   * C++ original:
   * ```cpp
   * void setSpan(const SkOpSpanBase* span) {
   *         fSpan = const_cast<SkOpSpanBase*>(span);
   *     }
   * ```
   */
  public fun starter(end: SkOpPtT?): SkOpPtT {
    TODO("Implement starter")
  }

  public companion object {
    public val kIsAlias: Int = TODO("Initialize kIsAlias")

    public val kIsDuplicate: Int = TODO("Initialize kIsDuplicate")

    /**
     * C++ original:
     * ```cpp
     * static bool Overlaps(const SkOpPtT* s1, const SkOpPtT* e1, const SkOpPtT* s2,
     *             const SkOpPtT* e2, const SkOpPtT** sOut, const SkOpPtT** eOut) {
     *         const SkOpPtT* start1 = s1->fT < e1->fT ? s1 : e1;
     *         const SkOpPtT* start2 = s2->fT < e2->fT ? s2 : e2;
     *         *sOut = between(s1->fT, start2->fT, e1->fT) ? start2
     *                 : between(s2->fT, start1->fT, e2->fT) ? start1 : nullptr;
     *         const SkOpPtT* end1 = s1->fT < e1->fT ? e1 : s1;
     *         const SkOpPtT* end2 = s2->fT < e2->fT ? e2 : s2;
     *         *eOut = between(s1->fT, end2->fT, e1->fT) ? end2
     *                 : between(s2->fT, end1->fT, e2->fT) ? end1 : nullptr;
     *         if (*sOut == *eOut) {
     *             SkOPOBJASSERT(s1, start1->fT >= end2->fT || start2->fT >= end1->fT);
     *             return false;
     *         }
     *         SkASSERT(!*sOut || *sOut != *eOut);
     *         return *sOut && *eOut;
     *     }
     * ```
     */
    public fun overlaps(
      s1: SkOpPtT?,
      e1: SkOpPtT?,
      s2: SkOpPtT?,
      e2: SkOpPtT?,
      sOut: Int?,
      eOut: Int?,
    ): Boolean {
      TODO("Implement overlaps")
    }
  }
}
