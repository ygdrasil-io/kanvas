package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkOpSpanBase {
 * public:
 *     enum class Collapsed {
 *         kNo,
 *         kYes,
 *         kError,
 *     };
 *
 *     bool addOpp(SkOpSpanBase* opp);
 *
 *     void bumpSpanAdds() {
 *         ++fSpanAdds;
 *     }
 *
 *     bool chased() const {
 *         return fChased;
 *     }
 *
 *     void checkForCollapsedCoincidence();
 *
 *     const SkOpSpanBase* coinEnd() const {
 *         return fCoinEnd;
 *     }
 *
 *     Collapsed collapsed(double s, double e) const;
 *     bool contains(const SkOpSpanBase* ) const;
 *     const SkOpPtT* contains(const SkOpSegment* ) const;
 *
 *     bool containsCoinEnd(const SkOpSpanBase* coin) const {
 *         SkASSERT(this != coin);
 *         const SkOpSpanBase* next = this;
 *         while ((next = next->fCoinEnd) != this) {
 *             if (next == coin) {
 *                 return true;
 *             }
 *         }
 *         return false;
 *     }
 *
 *     bool containsCoinEnd(const SkOpSegment* ) const;
 *     SkOpContour* contour() const;
 *
 *     int debugBumpCount() {
 *         return SkDEBUGRELEASE(++fCount, -1);
 *     }
 *
 *     int debugID() const {
 *         return SkDEBUGRELEASE(fID, -1);
 *     }
 *
 * #if DEBUG_COIN
 *     void debugAddOpp(SkPathOpsDebug::GlitchLog* , const SkOpSpanBase* opp) const;
 * #endif
 *     bool debugAlignedEnd(double t, const SkPoint& pt) const;
 *     bool debugAlignedInner() const;
 *     const SkOpAngle* debugAngle(int id) const;
 * #if DEBUG_COIN
 *     void debugCheckForCollapsedCoincidence(SkPathOpsDebug::GlitchLog* ) const;
 * #endif
 *     const SkOpCoincidence* debugCoincidence() const;
 *     bool debugCoinEndLoopCheck() const;
 *     SkOpContour* debugContour(int id) const;
 * #ifdef SK_DEBUG
 *     bool debugDeleted() const { return fDebugDeleted; }
 * #endif
 * #if DEBUG_COIN
 *     void debugInsertCoinEnd(SkPathOpsDebug::GlitchLog* ,
 *                             const SkOpSpanBase* ) const;
 *     void debugMergeMatches(SkPathOpsDebug::GlitchLog* log,
 *                            const SkOpSpanBase* opp) const;
 * #endif
 *     const SkOpPtT* debugPtT(int id) const;
 *     void debugResetCoinT() const;
 *     const SkOpSegment* debugSegment(int id) const;
 *     void debugSetCoinT(int ) const;
 * #ifdef SK_DEBUG
 *     void debugSetDeleted() { fDebugDeleted = true; }
 * #endif
 *     const SkOpSpanBase* debugSpan(int id) const;
 *     const SkOpSpan* debugStarter(SkOpSpanBase const** endPtr) const;
 *     SkOpGlobalState* globalState() const;
 *     void debugValidate() const;
 *
 *     bool deleted() const {
 *         return fPtT.deleted();
 *     }
 *
 *     void dump() const;  // available to testing only
 *     void dumpCoin() const;
 *     void dumpAll() const;
 *     void dumpBase() const;
 *     void dumpHead() const;
 *
 *     bool final() const {
 *         return fPtT.fT == 1;
 *     }
 *
 *     SkOpAngle* fromAngle() const {
 *         return fFromAngle;
 *     }
 *
 *     void initBase(SkOpSegment* parent, SkOpSpan* prev, double t, const SkPoint& pt);
 *
 *     // Please keep this in sync with debugInsertCoinEnd()
 *     void insertCoinEnd(SkOpSpanBase* coin) {
 *         if (containsCoinEnd(coin)) {
 *             SkASSERT(coin->containsCoinEnd(this));
 *             return;
 *         }
 *         debugValidate();
 *         SkASSERT(this != coin);
 *         SkOpSpanBase* coinNext = coin->fCoinEnd;
 *         coin->fCoinEnd = this->fCoinEnd;
 *         this->fCoinEnd = coinNext;
 *         debugValidate();
 *     }
 *
 *     void merge(SkOpSpan* span);
 *     bool mergeMatches(SkOpSpanBase* opp);
 *
 *     const SkOpSpan* prev() const {
 *         return fPrev;
 *     }
 *
 *     SkOpSpan* prev() {
 *         return fPrev;
 *     }
 *
 *     const SkPoint& pt() const {
 *         return fPtT.fPt;
 *     }
 *
 *     const SkOpPtT* ptT() const {
 *         return &fPtT;
 *     }
 *
 *     SkOpPtT* ptT() {
 *         return &fPtT;
 *     }
 *
 *     SkOpSegment* segment() const {
 *         return fSegment;
 *     }
 *
 *     void setAligned() {
 *         fAligned = true;
 *     }
 *
 *     void setChased(bool chased) {
 *         fChased = chased;
 *     }
 *
 *     void setFromAngle(SkOpAngle* angle) {
 *         fFromAngle = angle;
 *     }
 *
 *     void setPrev(SkOpSpan* prev) {
 *         fPrev = prev;
 *     }
 *
 *     bool simple() const {
 *         fPtT.debugValidate();
 *         return fPtT.next()->next() == &fPtT;
 *     }
 *
 *     int spanAddsCount() const {
 *         return fSpanAdds;
 *     }
 *
 *     const SkOpSpan* starter(const SkOpSpanBase* end) const {
 *         const SkOpSpanBase* result = t() < end->t() ? this : end;
 *         return result->upCast();
 *     }
 *
 *     SkOpSpan* starter(SkOpSpanBase* end) {
 *         SkASSERT(this->segment() == end->segment());
 *         SkOpSpanBase* result = t() < end->t() ? this : end;
 *         return result->upCast();
 *     }
 *
 *     SkOpSpan* starter(SkOpSpanBase** endPtr) {
 *         SkOpSpanBase* end = *endPtr;
 *         SkASSERT(this->segment() == end->segment());
 *         SkOpSpanBase* result;
 *         if (t() < end->t()) {
 *             result = this;
 *         } else {
 *             result = end;
 *             *endPtr = this;
 *         }
 *         return result->upCast();
 *     }
 *
 *     int step(const SkOpSpanBase* end) const {
 *         return t() < end->t() ? 1 : -1;
 *     }
 *
 *     double t() const {
 *         return fPtT.fT;
 *     }
 *
 *     void unaligned() {
 *         fAligned = false;
 *     }
 *
 *     SkOpSpan* upCast() {
 *         SkASSERT(!final());
 *         return (SkOpSpan*) this;
 *     }
 *
 *     const SkOpSpan* upCast() const {
 *         SkOPASSERT(!final());
 *         return (const SkOpSpan*) this;
 *     }
 *
 *     SkOpSpan* upCastable() {
 *         return final() ? nullptr : upCast();
 *     }
 *
 *     const SkOpSpan* upCastable() const {
 *         return final() ? nullptr : upCast();
 *     }
 *
 * private:
 *     void alignInner();
 *
 * protected:  // no direct access to internals to avoid treating a span base as a span
 *     SkOpPtT fPtT;  // list of points and t values associated with the start of this span
 *     SkOpSegment* fSegment;  // segment that contains this span
 *     SkOpSpanBase* fCoinEnd;  // linked list of coincident spans that end here (may point to itself)
 *     SkOpAngle* fFromAngle;  // points to next angle from span start to end
 *     SkOpSpan* fPrev;  // previous intersection point
 *     int fSpanAdds;  // number of times intersections have been added to span
 *     bool fAligned;
 *     bool fChased;  // set after span has been added to chase array
 *     SkDEBUGCODE(int fCount;)  // number of pt/t pairs added
 *     SkDEBUGCODE(int fID;)
 *     SkDEBUGCODE(bool fDebugDeleted;)  // set when span was merged with another span
 * }
 * ```
 */
public open class SkOpSpanBase {
  /**
   * C++ original:
   * ```cpp
   * SkOpPtT fPtT
   * ```
   */
  protected var fPtT: SkOpPtT = TODO("Initialize fPtT")

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* fSegment
   * ```
   */
  protected var fSegment: SkOpSegment? = TODO("Initialize fSegment")

  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase* fCoinEnd
   * ```
   */
  protected var fCoinEnd: SkOpSpanBase? = TODO("Initialize fCoinEnd")

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* fFromAngle
   * ```
   */
  protected var fFromAngle: SkOpAngle? = TODO("Initialize fFromAngle")

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* fPrev
   * ```
   */
  protected var fPrev: SkOpSpan? = TODO("Initialize fPrev")

  /**
   * C++ original:
   * ```cpp
   * int fSpanAdds
   * ```
   */
  protected var fSpanAdds: Int = TODO("Initialize fSpanAdds")

  /**
   * C++ original:
   * ```cpp
   * bool fAligned
   * ```
   */
  protected var fAligned: Boolean = TODO("Initialize fAligned")

  /**
   * C++ original:
   * ```cpp
   * bool fChased
   * ```
   */
  protected var fChased: Boolean = TODO("Initialize fChased")

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpanBase::addOpp(SkOpSpanBase* opp) {
   *     SkOpPtT* oppPrev = this->ptT()->oppPrev(opp->ptT());
   *     if (!oppPrev) {
   *         return true;
   *     }
   *     FAIL_IF(!this->mergeMatches(opp));
   *     this->ptT()->addOpp(opp->ptT(), oppPrev);
   *     this->checkForCollapsedCoincidence();
   *     return true;
   * }
   * ```
   */
  public fun addOpp(opp: SkOpSpanBase?): Boolean {
    TODO("Implement addOpp")
  }

  /**
   * C++ original:
   * ```cpp
   * void bumpSpanAdds() {
   *         ++fSpanAdds;
   *     }
   * ```
   */
  public fun bumpSpanAdds() {
    TODO("Implement bumpSpanAdds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool chased() const {
   *         return fChased;
   *     }
   * ```
   */
  public fun chased(): Boolean {
    TODO("Implement chased")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::checkForCollapsedCoincidence() {
   *     SkOpCoincidence* coins = this->globalState()->coincidence();
   *     if (coins->isEmpty()) {
   *         return;
   *     }
   * // the insert above may have put both ends of a coincident run in the same span
   * // for each coincident ptT in loop; see if its opposite in is also in the loop
   * // this implementation is the motivation for marking that a ptT is referenced by a coincident span
   *     SkOpPtT* head = this->ptT();
   *     SkOpPtT* test = head;
   *     do {
   *         if (!test->coincident()) {
   *             continue;
   *         }
   *         coins->markCollapsed(test);
   *     } while ((test = test->next()) != head);
   *     coins->releaseDeleted();
   * }
   * ```
   */
  public fun checkForCollapsedCoincidence() {
    TODO("Implement checkForCollapsedCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* coinEnd() const {
   *         return fCoinEnd;
   *     }
   * ```
   */
  public fun coinEnd(): SkOpSpanBase {
    TODO("Implement coinEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase::Collapsed SkOpSpanBase::collapsed(double s, double e) const {
   *     const SkOpPtT* start = &fPtT;
   *     const SkOpPtT* startNext = nullptr;
   *     const SkOpPtT* walk = start;
   *     double min = walk->fT;
   *     double max = min;
   *     const SkOpSegment* segment = this->segment();
   *     int safetyNet = 100000;
   *     while ((walk = walk->next()) != start) {
   *         if (!--safetyNet) {
   *             return Collapsed::kError;
   *         }
   *         if (walk == startNext) {
   *             return Collapsed::kError;
   *         }
   *         if (walk->segment() != segment) {
   *             continue;
   *         }
   *         min = std::min(min, walk->fT);
   *         max = std::max(max, walk->fT);
   *         if (between(min, s, max) && between(min, e, max)) {
   *             return Collapsed::kYes;
   *         }
   *         startNext = start->next();
   *     }
   *     return Collapsed::kNo;
   * }
   * ```
   */
  public fun collapsed(s: Double, e: Double): Collapsed {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpanBase::contains(const SkOpSpanBase* span) const {
   *     const SkOpPtT* start = &fPtT;
   *     const SkOpPtT* check = &span->fPtT;
   *     SkOPASSERT(start != check);
   *     const SkOpPtT* walk = start;
   *     while ((walk = walk->next()) != start) {
   *         if (walk == check) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun contains(span: SkOpSpanBase?): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpSpanBase::contains(const SkOpSegment* segment) const {
   *     const SkOpPtT* start = &fPtT;
   *     const SkOpPtT* walk = start;
   *     while ((walk = walk->next()) != start) {
   *         if (walk->deleted()) {
   *             continue;
   *         }
   *         if (walk->segment() == segment && walk->span()->ptT() == walk) {
   *             return walk;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun contains(segment: SkOpSegment?): SkOpPtT {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool containsCoinEnd(const SkOpSpanBase* coin) const {
   *         SkASSERT(this != coin);
   *         const SkOpSpanBase* next = this;
   *         while ((next = next->fCoinEnd) != this) {
   *             if (next == coin) {
   *                 return true;
   *             }
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun containsCoinEnd(coin: SkOpSpanBase?): Boolean {
    TODO("Implement containsCoinEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpanBase::containsCoinEnd(const SkOpSegment* segment) const {
   *     SkASSERT(this->segment() != segment);
   *     const SkOpSpanBase* next = this;
   *     while ((next = next->fCoinEnd) != this) {
   *         if (next->segment() == segment) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun containsCoinEnd(segment: SkOpSegment?): Boolean {
    TODO("Implement containsCoinEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* SkOpSpanBase::contour() const {
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
   * int debugBumpCount() {
   *         return SkDEBUGRELEASE(++fCount, -1);
   *     }
   * ```
   */
  public fun debugBumpCount(): Int {
    TODO("Implement debugBumpCount")
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
   * bool debugAlignedEnd(double t, const SkPoint& pt) const
   * ```
   */
  public fun debugAlignedEnd(t: Double, pt: SkPoint): Boolean {
    TODO("Implement debugAlignedEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool debugAlignedInner() const
   * ```
   */
  public fun debugAlignedInner(): Boolean {
    TODO("Implement debugAlignedInner")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpAngle* SkOpSpanBase::debugAngle(int id) const {
   *     return this->segment()->debugAngle(id);
   * }
   * ```
   */
  public fun debugAngle(id: Int): SkOpAngle {
    TODO("Implement debugAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpCoincidence* SkOpSpanBase::debugCoincidence() const {
   *     return this->segment()->debugCoincidence();
   * }
   * ```
   */
  public fun debugCoincidence(): SkOpCoincidence {
    TODO("Implement debugCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpanBase::debugCoinEndLoopCheck() const {
   *     int loop = 0;
   *     const SkOpSpanBase* next = this;
   *     SkOpSpanBase* nextCoin;
   *     do {
   *         nextCoin = next->fCoinEnd;
   *         SkASSERT(nextCoin == this || nextCoin->fCoinEnd != nextCoin);
   *         for (int check = 1; check < loop - 1; ++check) {
   *             const SkOpSpanBase* checkCoin = this->fCoinEnd;
   *             const SkOpSpanBase* innerCoin = checkCoin;
   *             for (int inner = check + 1; inner < loop; ++inner) {
   *                 innerCoin = innerCoin->fCoinEnd;
   *                 if (checkCoin == innerCoin) {
   *                     SkDebugf("*** bad coincident end loop ***\n");
   *                     return false;
   *                 }
   *             }
   *         }
   *         ++loop;
   *     } while ((next = nextCoin) && next != this);
   *     return true;
   * }
   * ```
   */
  public fun debugCoinEndLoopCheck(): Boolean {
    TODO("Implement debugCoinEndLoopCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* SkOpSpanBase::debugContour(int id) const {
   *     return this->segment()->debugContour(id);
   * }
   * ```
   */
  public fun debugContour(id: Int): SkOpContour {
    TODO("Implement debugContour")
  }

  /**
   * C++ original:
   * ```cpp
   * bool debugDeleted() const { return fDebugDeleted; }
   * ```
   */
  public fun debugDeleted(): Boolean {
    TODO("Implement debugDeleted")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpSpanBase::debugPtT(int id) const {
   *     return this->segment()->debugPtT(id);
   * }
   * ```
   */
  public fun debugPtT(id: Int): SkOpPtT {
    TODO("Implement debugPtT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::debugResetCoinT() const {
   * #if DEBUG_COINCIDENCE_ORDER
   *     const SkOpPtT* ptT = &fPtT;
   *     do {
   *         ptT->debugResetCoinT();
   *         ptT = ptT->next();
   *     } while (ptT != &fPtT);
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
   * const SkOpSegment* SkOpSpanBase::debugSegment(int id) const {
   *     return this->segment()->debugSegment(id);
   * }
   * ```
   */
  public fun debugSegment(id: Int): SkOpSegment {
    TODO("Implement debugSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::debugSetCoinT(int index) const {
   * #if DEBUG_COINCIDENCE_ORDER
   *     const SkOpPtT* ptT = &fPtT;
   *     do {
   *         if (!ptT->deleted()) {
   *             ptT->debugSetCoinT(index);
   *         }
   *         ptT = ptT->next();
   *     } while (ptT != &fPtT);
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
   * void debugSetDeleted() { fDebugDeleted = true; }
   * ```
   */
  public fun debugSetDeleted() {
    TODO("Implement debugSetDeleted")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* SkOpSpanBase::debugSpan(int id) const {
   *     return this->segment()->debugSpan(id);
   * }
   * ```
   */
  public fun debugSpan(id: Int): SkOpSpanBase {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpan* SkOpSpanBase::debugStarter(SkOpSpanBase const** endPtr) const {
   *     const SkOpSpanBase* end = *endPtr;
   *     SkASSERT(this->segment() == end->segment());
   *     const SkOpSpanBase* result;
   *     if (t() < end->t()) {
   *         result = this;
   *     } else {
   *         result = end;
   *         *endPtr = this;
   *     }
   *     return result->upCast();
   * }
   * ```
   */
  public fun debugStarter(endPtr: Int?): SkOpSpan {
    TODO("Implement debugStarter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* SkOpSpanBase::globalState() const {
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
   * void SkOpSpanBase::debugValidate() const {
   * #if DEBUG_COINCIDENCE
   *     if (this->globalState()->debugCheckHealth()) {
   *         return;
   *     }
   * #endif
   * #if DEBUG_VALIDATE
   *     const SkOpPtT* ptT = &fPtT;
   *     SkASSERT(ptT->span() == this);
   *     do {
   * //        SkASSERT(SkDPoint::RoughlyEqual(fPtT.fPt, ptT->fPt));
   *         ptT->debugValidate();
   *         ptT = ptT->next();
   *     } while (ptT != &fPtT);
   *     SkASSERT(this->debugCoinEndLoopCheck());
   *     if (!this->final()) {
   *         SkASSERT(this->upCast()->debugCoinLoopCheck());
   *     }
   *     if (fFromAngle) {
   *         fFromAngle->debugValidate();
   *     }
   *     if (!this->final() && this->upCast()->toAngle()) {
   *         this->upCast()->toAngle()->debugValidate();
   *     }
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
   *         return fPtT.deleted();
   *     }
   * ```
   */
  public fun deleted(): Boolean {
    TODO("Implement deleted")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::dump() const {
   *     this->dumpHead();
   *     this->fPtT.dump();
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::dumpCoin() const {
   *     const SkOpSpan* span = this->upCastable();
   *     if (!span) {
   *         return;
   *     }
   *     if (!span->isCoincident()) {
   *         return;
   *     }
   *     span->dumpCoin();
   * }
   * ```
   */
  public fun dumpCoin() {
    TODO("Implement dumpCoin")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::dumpAll() const {
   *     this->dumpHead();
   *     this->fPtT.dumpAll();
   * }
   * ```
   */
  public fun dumpAll() {
    TODO("Implement dumpAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::dumpBase() const {
   *     if (this->fAligned) {
   *         SkDebugf(" aligned");
   *     }
   *     if (this->fChased) {
   *         SkDebugf(" chased");
   *     }
   * #ifdef SK_DEBUG
   *     if (this->fDebugDeleted) {
   *         SkDebugf(" deleted");
   *     }
   * #endif
   *     if (!this->final()) {
   *         this->upCast()->dumpSpan();
   *     }
   *     const SkOpSpanBase* coin = this->coinEnd();
   *     if (this != coin) {
   *         SkDebugf(" coinEnd seg/span=%d/%d", coin->segment()->debugID(), coin->debugID());
   *     } else if (this->final() || !this->upCast()->isCoincident()) {
   *         const SkOpPtT* oPt = this->ptT()->next();
   *         SkDebugf(" seg/span=%d/%d", oPt->segment()->debugID(), oPt->span()->debugID());
   *     }
   *     SkDebugf(" adds=%d", fSpanAdds);
   * }
   * ```
   */
  public fun dumpBase() {
    TODO("Implement dumpBase")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::dumpHead() const {
   *     SkDebugf("%.*s", contour()->debugIndent(), "        ");
   *     SkDebugf("seg=%d span=%d", this->segment()->debugID(), this->debugID());
   *     this->dumpBase();
   *     SkDebugf("\n");
   * }
   * ```
   */
  public fun dumpHead() {
    TODO("Implement dumpHead")
  }

  /**
   * C++ original:
   * ```cpp
   * bool final() const {
   *         return fPtT.fT == 1;
   *     }
   * ```
   */
  public fun `final`(): Boolean {
    TODO("Implement final")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* fromAngle() const {
   *         return fFromAngle;
   *     }
   * ```
   */
  public fun fromAngle(): SkOpAngle {
    TODO("Implement fromAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::initBase(SkOpSegment* segment, SkOpSpan* prev, double t, const SkPoint& pt) {
   *     fSegment = segment;
   *     fPtT.init(this, t, pt, false);
   *     fCoinEnd = this;
   *     fFromAngle = nullptr;
   *     fPrev = prev;
   *     fSpanAdds = 0;
   *     fAligned = true;
   *     fChased = false;
   *     SkDEBUGCODE(fCount = 1);
   *     SkDEBUGCODE(fID = globalState()->nextSpanID());
   *     SkDEBUGCODE(fDebugDeleted = false);
   * }
   * ```
   */
  public fun initBase(
    parent: SkOpSegment?,
    prev: SkOpSpan?,
    t: Double,
    pt: SkPoint,
  ) {
    TODO("Implement initBase")
  }

  /**
   * C++ original:
   * ```cpp
   * void insertCoinEnd(SkOpSpanBase* coin) {
   *         if (containsCoinEnd(coin)) {
   *             SkASSERT(coin->containsCoinEnd(this));
   *             return;
   *         }
   *         debugValidate();
   *         SkASSERT(this != coin);
   *         SkOpSpanBase* coinNext = coin->fCoinEnd;
   *         coin->fCoinEnd = this->fCoinEnd;
   *         this->fCoinEnd = coinNext;
   *         debugValidate();
   *     }
   * ```
   */
  public fun insertCoinEnd(coin: SkOpSpanBase?) {
    TODO("Implement insertCoinEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpanBase::merge(SkOpSpan* span) {
   *     SkOpPtT* spanPtT = span->ptT();
   *     SkASSERT(this->t() != spanPtT->fT);
   *     SkASSERT(!zero_or_one(spanPtT->fT));
   *     span->release(this->ptT());
   *     if (this->contains(span)) {
   *         SkOPASSERT(0);  // check to see if this ever happens -- should have been found earlier
   *         return;  // merge is already in the ptT loop
   *     }
   *     SkOpPtT* remainder = spanPtT->next();
   *     this->ptT()->insert(spanPtT);
   *     while (remainder != spanPtT) {
   *         SkOpPtT* next = remainder->next();
   *         SkOpPtT* compare = spanPtT->next();
   *         while (compare != spanPtT) {
   *             SkOpPtT* nextC = compare->next();
   *             if (nextC->span() == remainder->span() && nextC->fT == remainder->fT) {
   *                 goto tryNextRemainder;
   *             }
   *             compare = nextC;
   *         }
   *         spanPtT->insert(remainder);
   * tryNextRemainder:
   *         remainder = next;
   *     }
   *     fSpanAdds += span->fSpanAdds;
   * }
   * ```
   */
  public fun merge(span: SkOpSpan?) {
    TODO("Implement merge")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpanBase::mergeMatches(SkOpSpanBase* opp) {
   *     SkOpPtT* test = &fPtT;
   *     SkOpPtT* testNext;
   *     const SkOpPtT* stop = test;
   *     int safetyHatch = 1000000;
   *     do {
   *         if (!--safetyHatch) {
   *             return false;
   *         }
   *         testNext = test->next();
   *         if (test->deleted()) {
   *             continue;
   *         }
   *         SkOpSpanBase* testBase = test->span();
   *         SkASSERT(testBase->ptT() == test);
   *         SkOpSegment* segment = test->segment();
   *         if (segment->done()) {
   *             continue;
   *         }
   *         SkOpPtT* inner = opp->ptT();
   *         const SkOpPtT* innerStop = inner;
   *         do {
   *             if (inner->segment() != segment) {
   *                 continue;
   *             }
   *             if (inner->deleted()) {
   *                 continue;
   *             }
   *             SkOpSpanBase* innerBase = inner->span();
   *             SkASSERT(innerBase->ptT() == inner);
   *             // when the intersection is first detected, the span base is marked if there are
   *             // more than one point in the intersection.
   *             if (!zero_or_one(inner->fT)) {
   *                 innerBase->upCast()->release(test);
   *             } else {
   *                 SkOPASSERT(inner->fT != test->fT);
   *                 if (!zero_or_one(test->fT)) {
   *                     testBase->upCast()->release(inner);
   *                 } else {
   *                     segment->markAllDone();  // mark segment as collapsed
   *                     SkDEBUGCODE(testBase->debugSetDeleted());
   *                     test->setDeleted();
   *                     SkDEBUGCODE(innerBase->debugSetDeleted());
   *                     inner->setDeleted();
   *                 }
   *             }
   * #ifdef SK_DEBUG   // assert if another undeleted entry points to segment
   *             const SkOpPtT* debugInner = inner;
   *             while ((debugInner = debugInner->next()) != innerStop) {
   *                 if (debugInner->segment() != segment) {
   *                     continue;
   *                 }
   *                 if (debugInner->deleted()) {
   *                     continue;
   *                 }
   *                 SkOPASSERT(0);
   *             }
   * #endif
   *             break;
   *         } while ((inner = inner->next()) != innerStop);
   *     } while ((test = testNext) != stop);
   *     this->checkForCollapsedCoincidence();
   *     return true;
   * }
   * ```
   */
  public fun mergeMatches(opp: SkOpSpanBase?): Boolean {
    TODO("Implement mergeMatches")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpan* prev() const {
   *         return fPrev;
   *     }
   * ```
   */
  public fun prev(): SkOpSpan {
    TODO("Implement prev")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* prev() {
   *         return fPrev;
   *     }
   * ```
   */
  public fun pt(): SkPoint {
    TODO("Implement pt")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& pt() const {
   *         return fPtT.fPt;
   *     }
   * ```
   */
  public fun ptT(): SkOpPtT {
    TODO("Implement ptT")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* ptT() const {
   *         return &fPtT;
   *     }
   * ```
   */
  public fun segment(): SkOpSegment {
    TODO("Implement segment")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* ptT() {
   *         return &fPtT;
   *     }
   * ```
   */
  public fun setAligned() {
    TODO("Implement setAligned")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* segment() const {
   *         return fSegment;
   *     }
   * ```
   */
  public fun setChased(chased: Boolean) {
    TODO("Implement setChased")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAligned() {
   *         fAligned = true;
   *     }
   * ```
   */
  public fun setFromAngle(angle: SkOpAngle?) {
    TODO("Implement setFromAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setChased(bool chased) {
   *         fChased = chased;
   *     }
   * ```
   */
  public fun setPrev(prev: SkOpSpan?) {
    TODO("Implement setPrev")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFromAngle(SkOpAngle* angle) {
   *         fFromAngle = angle;
   *     }
   * ```
   */
  public fun simple(): Boolean {
    TODO("Implement simple")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPrev(SkOpSpan* prev) {
   *         fPrev = prev;
   *     }
   * ```
   */
  public fun spanAddsCount(): Int {
    TODO("Implement spanAddsCount")
  }

  /**
   * C++ original:
   * ```cpp
   * bool simple() const {
   *         fPtT.debugValidate();
   *         return fPtT.next()->next() == &fPtT;
   *     }
   * ```
   */
  public fun starter(end: SkOpSpanBase?): SkOpSpan {
    TODO("Implement starter")
  }

  /**
   * C++ original:
   * ```cpp
   * int spanAddsCount() const {
   *         return fSpanAdds;
   *     }
   * ```
   */
  public fun starter(endPtr: Int?): SkOpSpan {
    TODO("Implement starter")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpan* starter(const SkOpSpanBase* end) const {
   *         const SkOpSpanBase* result = t() < end->t() ? this : end;
   *         return result->upCast();
   *     }
   * ```
   */
  public fun step(end: SkOpSpanBase?): Int {
    TODO("Implement step")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* starter(SkOpSpanBase* end) {
   *         SkASSERT(this->segment() == end->segment());
   *         SkOpSpanBase* result = t() < end->t() ? this : end;
   *         return result->upCast();
   *     }
   * ```
   */
  public fun t(): Double {
    TODO("Implement t")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* starter(SkOpSpanBase** endPtr) {
   *         SkOpSpanBase* end = *endPtr;
   *         SkASSERT(this->segment() == end->segment());
   *         SkOpSpanBase* result;
   *         if (t() < end->t()) {
   *             result = this;
   *         } else {
   *             result = end;
   *             *endPtr = this;
   *         }
   *         return result->upCast();
   *     }
   * ```
   */
  public fun unaligned() {
    TODO("Implement unaligned")
  }

  /**
   * C++ original:
   * ```cpp
   * int step(const SkOpSpanBase* end) const {
   *         return t() < end->t() ? 1 : -1;
   *     }
   * ```
   */
  public fun upCast(): SkOpSpan {
    TODO("Implement upCast")
  }

  /**
   * C++ original:
   * ```cpp
   * double t() const {
   *         return fPtT.fT;
   *     }
   * ```
   */
  public fun upCastable(): SkOpSpan {
    TODO("Implement upCastable")
  }

  /**
   * C++ original:
   * ```cpp
   * void unaligned() {
   *         fAligned = false;
   *     }
   * ```
   */
  private fun alignInner() {
    TODO("Implement alignInner")
  }

  public enum class Collapsed {
    kNo,
    kYes,
    kError,
  }
}
