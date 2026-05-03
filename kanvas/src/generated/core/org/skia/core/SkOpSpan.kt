package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkOpSpan : public SkOpSpanBase {
 * public:
 *     bool alreadyAdded() const {
 *         if (fAlreadyAdded) {
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     bool clearCoincident() {
 *         SkASSERT(!final());
 *         if (fCoincident == this) {
 *             return false;
 *         }
 *         fCoincident = this;
 *         return true;
 *     }
 *
 *     int computeWindSum();
 *     bool containsCoincidence(const SkOpSegment* ) const;
 *
 *     bool containsCoincidence(const SkOpSpan* coin) const {
 *         SkASSERT(this != coin);
 *         const SkOpSpan* next = this;
 *         while ((next = next->fCoincident) != this) {
 *             if (next == coin) {
 *                 return true;
 *             }
 *         }
 *         return false;
 *     }
 *
 *     bool debugCoinLoopCheck() const;
 * #if DEBUG_COIN
 *     void debugInsertCoincidence(SkPathOpsDebug::GlitchLog* , const SkOpSpan* ) const;
 *     void debugInsertCoincidence(SkPathOpsDebug::GlitchLog* ,
 *                                 const SkOpSegment* , bool flipped, bool ordered) const;
 * #endif
 *     void dumpCoin() const;
 *     bool dumpSpan() const;
 *
 *     bool done() const {
 *         SkASSERT(!final());
 *         return fDone;
 *     }
 *
 *     void init(SkOpSegment* parent, SkOpSpan* prev, double t, const SkPoint& pt);
 *     bool insertCoincidence(const SkOpSegment* , bool flipped, bool ordered);
 *
 *     // Please keep this in sync with debugInsertCoincidence()
 *     void insertCoincidence(SkOpSpan* coin) {
 *         if (containsCoincidence(coin)) {
 *             SkASSERT(coin->containsCoincidence(this));
 *             return;
 *         }
 *         debugValidate();
 *         SkASSERT(this != coin);
 *         SkOpSpan* coinNext = coin->fCoincident;
 *         coin->fCoincident = this->fCoincident;
 *         this->fCoincident = coinNext;
 *         debugValidate();
 *     }
 *
 *     bool isCanceled() const {
 *         SkASSERT(!final());
 *         return fWindValue == 0 && fOppValue == 0;
 *     }
 *
 *     bool isCoincident() const {
 *         SkASSERT(!final());
 *         return fCoincident != this;
 *     }
 *
 *     void markAdded() {
 *         fAlreadyAdded = true;
 *     }
 *
 *     SkOpSpanBase* next() const {
 *         SkASSERT(!final());
 *         return fNext;
 *     }
 *
 *     int oppSum() const {
 *         SkASSERT(!final());
 *         return fOppSum;
 *     }
 *
 *     int oppValue() const {
 *         SkASSERT(!final());
 *         return fOppValue;
 *     }
 *
 *     void release(const SkOpPtT* );
 *
 *     SkOpPtT* setCoinStart(SkOpSpan* oldCoinStart, SkOpSegment* oppSegment);
 *
 *     void setDone(bool done) {
 *         SkASSERT(!final());
 *         fDone = done;
 *     }
 *
 *     void setNext(SkOpSpanBase* nextT) {
 *         SkASSERT(!final());
 *         fNext = nextT;
 *     }
 *
 *     void setOppSum(int oppSum);
 *
 *     void setOppValue(int oppValue) {
 *         SkASSERT(!final());
 *         SkASSERT(fOppSum == SK_MinS32);
 *         SkOPASSERT(!oppValue || !fDone);
 *         fOppValue = oppValue;
 *     }
 *
 *     void setToAngle(SkOpAngle* angle) {
 *         SkASSERT(!final());
 *         fToAngle = angle;
 *     }
 *
 *     void setWindSum(int windSum);
 *
 *     void setWindValue(int windValue) {
 *         SkASSERT(!final());
 *         SkASSERT(windValue >= 0);
 *         SkASSERT(fWindSum == SK_MinS32);
 *         SkOPASSERT(!windValue || !fDone);
 *         fWindValue = windValue;
 *     }
 *
 *     bool sortableTop(SkOpContour* );
 *
 *     SkOpAngle* toAngle() const {
 *         SkASSERT(!final());
 *         return fToAngle;
 *     }
 *
 *     int windSum() const {
 *         SkASSERT(!final());
 *         return fWindSum;
 *     }
 *
 *     int windValue() const {
 *         SkOPASSERT(!final());
 *         return fWindValue;
 *     }
 *
 * private:  // no direct access to internals to avoid treating a span base as a span
 *     SkOpSpan* fCoincident;  // linked list of spans coincident with this one (may point to itself)
 *     SkOpAngle* fToAngle;  // points to next angle from span start to end
 *     SkOpSpanBase* fNext;  // next intersection point
 *     int fWindSum;  // accumulated from contours surrounding this one.
 *     int fOppSum;  // for binary operators: the opposite winding sum
 *     int fWindValue;  // 0 == canceled; 1 == normal; >1 == coincident
 *     int fOppValue;  // normally 0 -- when binary coincident edges combine, opp value goes here
 *     int fTopTTry; // specifies direction and t value to try next
 *     bool fDone;  // if set, this span to next higher T has been processed
 *     bool fAlreadyAdded;
 * }
 * ```
 */
public open class SkOpSpan : SkOpSpanBase() {
  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* fCoincident
   * ```
   */
  private var fCoincident: SkOpSpan? = TODO("Initialize fCoincident")

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* fToAngle
   * ```
   */
  private var fToAngle: SkOpAngle? = TODO("Initialize fToAngle")

  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase* fNext
   * ```
   */
  private var fNext: SkOpSpanBase? = TODO("Initialize fNext")

  /**
   * C++ original:
   * ```cpp
   * int fWindSum
   * ```
   */
  private var fWindSum: Int = TODO("Initialize fWindSum")

  /**
   * C++ original:
   * ```cpp
   * int fOppSum
   * ```
   */
  private var fOppSum: Int = TODO("Initialize fOppSum")

  /**
   * C++ original:
   * ```cpp
   * int fWindValue
   * ```
   */
  private var fWindValue: Int = TODO("Initialize fWindValue")

  /**
   * C++ original:
   * ```cpp
   * int fOppValue
   * ```
   */
  private var fOppValue: Int = TODO("Initialize fOppValue")

  /**
   * C++ original:
   * ```cpp
   * int fTopTTry
   * ```
   */
  private var fTopTTry: Int = TODO("Initialize fTopTTry")

  /**
   * C++ original:
   * ```cpp
   * bool fDone
   * ```
   */
  private var fDone: Boolean = TODO("Initialize fDone")

  /**
   * C++ original:
   * ```cpp
   * bool fAlreadyAdded
   * ```
   */
  private var fAlreadyAdded: Boolean = TODO("Initialize fAlreadyAdded")

  /**
   * C++ original:
   * ```cpp
   * bool alreadyAdded() const {
   *         if (fAlreadyAdded) {
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun alreadyAdded(): Boolean {
    TODO("Implement alreadyAdded")
  }

  /**
   * C++ original:
   * ```cpp
   * bool clearCoincident() {
   *         SkASSERT(!final());
   *         if (fCoincident == this) {
   *             return false;
   *         }
   *         fCoincident = this;
   *         return true;
   *     }
   * ```
   */
  public fun clearCoincident(): Boolean {
    TODO("Implement clearCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSpan::computeWindSum() {
   *     SkOpGlobalState* globals = this->globalState();
   *     SkOpContour* contourHead = globals->contourHead();
   *     int windTry = 0;
   *     while (!this->sortableTop(contourHead) && ++windTry < SkOpGlobalState::kMaxWindingTries) {
   *     }
   *     return this->windSum();
   * }
   * ```
   */
  public fun computeWindSum(): Int {
    TODO("Implement computeWindSum")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpan::containsCoincidence(const SkOpSegment* segment) const {
   *     SkASSERT(this->segment() != segment);
   *     const SkOpSpan* next = fCoincident;
   *     do {
   *         if (next->segment() == segment) {
   *             return true;
   *         }
   *     } while ((next = next->fCoincident) != this);
   *     return false;
   * }
   * ```
   */
  public fun containsCoincidence(segment: SkOpSegment?): Boolean {
    TODO("Implement containsCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool containsCoincidence(const SkOpSpan* coin) const {
   *         SkASSERT(this != coin);
   *         const SkOpSpan* next = this;
   *         while ((next = next->fCoincident) != this) {
   *             if (next == coin) {
   *                 return true;
   *             }
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun containsCoincidence(coin: SkOpSpan?): Boolean {
    TODO("Implement containsCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpan::debugCoinLoopCheck() const {
   *     int loop = 0;
   *     const SkOpSpan* next = this;
   *     SkOpSpan* nextCoin;
   *     do {
   *         nextCoin = next->fCoincident;
   *         SkASSERT(nextCoin == this || nextCoin->fCoincident != nextCoin);
   *         for (int check = 1; check < loop - 1; ++check) {
   *             const SkOpSpan* checkCoin = this->fCoincident;
   *             const SkOpSpan* innerCoin = checkCoin;
   *             for (int inner = check + 1; inner < loop; ++inner) {
   *                 innerCoin = innerCoin->fCoincident;
   *                 if (checkCoin == innerCoin) {
   *                     SkDebugf("*** bad coincident loop ***\n");
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
  public fun debugCoinLoopCheck(): Boolean {
    TODO("Implement debugCoinLoopCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpan::dumpCoin() const {
   *     const SkOpSpan* coincident = fCoincident;
   *     bool ok = debugCoinLoopCheck();
   *     this->dump();
   *     int loop = 0;
   *     do {
   *         coincident->dump();
   *         if (!ok && ++loop > 10) {
   *             SkDebugf("*** abort loop ***\n");
   *             break;
   *         }
   *     } while ((coincident = coincident->fCoincident) != this);
   * }
   * ```
   */
  public override fun dumpCoin() {
    TODO("Implement dumpCoin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpan::dumpSpan() const {
   *     SkOpSpan* coin = fCoincident;
   *     if (this != coin) {
   *         SkDebugf(" coinStart seg/span=%d/%d", coin->segment()->debugID(), coin->debugID());
   *     }
   *     SkDebugf(" windVal=%d", this->windValue());
   *     SkDebugf(" windSum=");
   *     SkPathOpsDebug::WindingPrintf(this->windSum());
   *     if (this->oppValue() != 0 || this->oppSum() != SK_MinS32) {
   *         SkDebugf(" oppVal=%d", this->oppValue());
   *         SkDebugf(" oppSum=");
   *         SkPathOpsDebug::WindingPrintf(this->oppSum());
   *     }
   *     if (this->done()) {
   *         SkDebugf(" done");
   *     }
   *     return this != coin;
   * }
   * ```
   */
  public fun dumpSpan(): Boolean {
    TODO("Implement dumpSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool done() const {
   *         SkASSERT(!final());
   *         return fDone;
   *     }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpan::init(SkOpSegment* segment, SkOpSpan* prev, double t, const SkPoint& pt) {
   *     SkASSERT(t != 1);
   *     initBase(segment, prev, t, pt);
   *     fCoincident = this;
   *     fToAngle = nullptr;
   *     fWindSum = fOppSum = SK_MinS32;
   *     fWindValue = 1;
   *     fOppValue = 0;
   *     fTopTTry = 0;
   *     fChased = fDone = false;
   *     segment->bumpCount();
   *     fAlreadyAdded = false;
   * }
   * ```
   */
  public fun `init`(
    parent: SkOpSegment?,
    prev: SkOpSpan?,
    t: Double,
    pt: SkPoint,
  ) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpan::insertCoincidence(const SkOpSegment* segment, bool flipped, bool ordered) {
   *     if (this->containsCoincidence(segment)) {
   *         return true;
   *     }
   *     SkOpPtT* next = &fPtT;
   *     while ((next = next->next()) != &fPtT) {
   *         if (next->segment() == segment) {
   *             SkOpSpan* span;
   *             SkOpSpanBase* base = next->span();
   *             if (!ordered) {
   *                 const SkOpPtT* spanEndPtT = fNext->contains(segment);
   *                 FAIL_IF(!spanEndPtT);
   *                 const SkOpSpanBase* spanEnd = spanEndPtT->span();
   *                 const SkOpPtT* start = base->ptT()->starter(spanEnd->ptT());
   *                 FAIL_IF(!start->span()->upCastable());
   *                 span = const_cast<SkOpSpan*>(start->span()->upCast());
   *             } else if (flipped) {
   *                 span = base->prev();
   *                 FAIL_IF(!span);
   *             } else {
   *                 FAIL_IF(!base->upCastable());
   *                 span = base->upCast();
   *             }
   *             this->insertCoincidence(span);
   *             return true;
   *         }
   *     }
   * #if DEBUG_COINCIDENCE
   *     SkASSERT(0); // FIXME? if we get here, the span is missing its opposite segment...
   * #endif
   *     return true;
   * }
   * ```
   */
  public fun insertCoincidence(
    segment: SkOpSegment?,
    flipped: Boolean,
    ordered: Boolean,
  ): Boolean {
    TODO("Implement insertCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * void insertCoincidence(SkOpSpan* coin) {
   *         if (containsCoincidence(coin)) {
   *             SkASSERT(coin->containsCoincidence(this));
   *             return;
   *         }
   *         debugValidate();
   *         SkASSERT(this != coin);
   *         SkOpSpan* coinNext = coin->fCoincident;
   *         coin->fCoincident = this->fCoincident;
   *         this->fCoincident = coinNext;
   *         debugValidate();
   *     }
   * ```
   */
  public fun insertCoincidence(coin: SkOpSpan?) {
    TODO("Implement insertCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isCanceled() const {
   *         SkASSERT(!final());
   *         return fWindValue == 0 && fOppValue == 0;
   *     }
   * ```
   */
  public fun isCanceled(): Boolean {
    TODO("Implement isCanceled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isCoincident() const {
   *         SkASSERT(!final());
   *         return fCoincident != this;
   *     }
   * ```
   */
  public fun isCoincident(): Boolean {
    TODO("Implement isCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * void markAdded() {
   *         fAlreadyAdded = true;
   *     }
   * ```
   */
  public fun markAdded() {
    TODO("Implement markAdded")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase* next() const {
   *         SkASSERT(!final());
   *         return fNext;
   *     }
   * ```
   */
  public fun next(): SkOpSpanBase {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * int oppSum() const {
   *         SkASSERT(!final());
   *         return fOppSum;
   *     }
   * ```
   */
  public fun oppSum(): Int {
    TODO("Implement oppSum")
  }

  /**
   * C++ original:
   * ```cpp
   * int oppValue() const {
   *         SkASSERT(!final());
   *         return fOppValue;
   *     }
   * ```
   */
  public fun oppValue(): Int {
    TODO("Implement oppValue")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpan::release(const SkOpPtT* kept) {
   *     SkDEBUGCODE(fDebugDeleted = true);
   *     SkOPASSERT(kept->span() != this);
   *     SkASSERT(!final());
   *     SkOpSpan* prev = this->prev();
   *     SkASSERT(prev);
   *     SkOpSpanBase* next = this->next();
   *     SkASSERT(next);
   *     prev->setNext(next);
   *     next->setPrev(prev);
   *     this->segment()->release(this);
   *     SkOpCoincidence* coincidence = this->globalState()->coincidence();
   *     if (coincidence) {
   *         coincidence->fixUp(this->ptT(), kept);
   *     }
   *     this->ptT()->setDeleted();
   *     SkOpPtT* stopPtT = this->ptT();
   *     SkOpPtT* testPtT = stopPtT;
   *     const SkOpSpanBase* keptSpan = kept->span();
   *     do {
   *         if (this == testPtT->span()) {
   *             testPtT->setSpan(keptSpan);
   *         }
   *     } while ((testPtT = testPtT->next()) != stopPtT);
   * }
   * ```
   */
  public fun release(kept: SkOpPtT?) {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* setCoinStart(SkOpSpan* oldCoinStart, SkOpSegment* oppSegment)
   * ```
   */
  public fun setCoinStart(oldCoinStart: SkOpSpan?, oppSegment: SkOpSegment?): SkOpPtT {
    TODO("Implement setCoinStart")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDone(bool done) {
   *         SkASSERT(!final());
   *         fDone = done;
   *     }
   * ```
   */
  public fun setDone(done: Boolean) {
    TODO("Implement setDone")
  }

  /**
   * C++ original:
   * ```cpp
   * void setNext(SkOpSpanBase* nextT) {
   *         SkASSERT(!final());
   *         fNext = nextT;
   *     }
   * ```
   */
  public fun setNext(nextT: SkOpSpanBase?) {
    TODO("Implement setNext")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpan::setOppSum(int oppSum) {
   *     SkASSERT(!final());
   *     if (fOppSum != SK_MinS32 && fOppSum != oppSum) {
   *         this->globalState()->setWindingFailed();
   *         return;
   *     }
   *     SkASSERT(!DEBUG_LIMIT_WIND_SUM || SkTAbs(oppSum) <= DEBUG_LIMIT_WIND_SUM);
   *     fOppSum = oppSum;
   * }
   * ```
   */
  public fun setOppSum(oppSum: Int) {
    TODO("Implement setOppSum")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOppValue(int oppValue) {
   *         SkASSERT(!final());
   *         SkASSERT(fOppSum == SK_MinS32);
   *         SkOPASSERT(!oppValue || !fDone);
   *         fOppValue = oppValue;
   *     }
   * ```
   */
  public fun setOppValue(oppValue: Int) {
    TODO("Implement setOppValue")
  }

  /**
   * C++ original:
   * ```cpp
   * void setToAngle(SkOpAngle* angle) {
   *         SkASSERT(!final());
   *         fToAngle = angle;
   *     }
   * ```
   */
  public fun setToAngle(angle: SkOpAngle?) {
    TODO("Implement setToAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSpan::setWindSum(int windSum) {
   *     SkASSERT(!final());
   *     if (fWindSum != SK_MinS32 && fWindSum != windSum) {
   *         this->globalState()->setWindingFailed();
   *         return;
   *     }
   *     SkASSERT(!DEBUG_LIMIT_WIND_SUM || SkTAbs(windSum) <= DEBUG_LIMIT_WIND_SUM);
   *     fWindSum = windSum;
   * }
   * ```
   */
  public fun setWindSum(windSum: Int) {
    TODO("Implement setWindSum")
  }

  /**
   * C++ original:
   * ```cpp
   * void setWindValue(int windValue) {
   *         SkASSERT(!final());
   *         SkASSERT(windValue >= 0);
   *         SkASSERT(fWindSum == SK_MinS32);
   *         SkOPASSERT(!windValue || !fDone);
   *         fWindValue = windValue;
   *     }
   * ```
   */
  public fun setWindValue(windValue: Int) {
    TODO("Implement setWindValue")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSpan::sortableTop(SkOpContour* contourHead) {
   *     SkSTArenaAlloc<1024> allocator;
   *     int dirOffset;
   *     double t = get_t_guess(fTopTTry++, &dirOffset);
   *     SkOpRayHit hitBase;
   *     SkOpRayDir dir = hitBase.makeTestBase(this, t);
   *     if (hitBase.fSlope.fX == 0 && hitBase.fSlope.fY == 0) {
   *         return false;
   *     }
   *     SkOpRayHit* hitHead = &hitBase;
   *     dir = static_cast<SkOpRayDir>(static_cast<int>(dir) + dirOffset);
   *     if (hitBase.fSpan && hitBase.fSpan->segment()->verb() > SkPath::kLine_Verb
   *             && !pt_dydx(hitBase.fSlope, dir)) {
   *         return false;
   *     }
   *     SkOpContour* contour = contourHead;
   *     do {
   *         if (!contour->count()) {
   *             continue;
   *         }
   *         contour->rayCheck(hitBase, dir, &hitHead, &allocator);
   *     } while ((contour = contour->next()));
   *     // sort hits
   *     STArray<1, SkOpRayHit*> sorted;
   *     SkOpRayHit* hit = hitHead;
   *     while (hit) {
   *         sorted.push_back(hit);
   *         hit = hit->fNext;
   *     }
   *     int count = sorted.size();
   *     SkTQSort(sorted.begin(), sorted.end(),
   *              xy_index(dir) ? less_than(dir) ? hit_compare_y : reverse_hit_compare_y
   *                            : less_than(dir) ? hit_compare_x : reverse_hit_compare_x);
   *     // verify windings
   * #if DEBUG_WINDING
   *     SkDebugf("%s dir=%s seg=%d t=%1.9g pt=(%1.9g,%1.9g)\n", __FUNCTION__,
   *             gDebugRayDirName[static_cast<int>(dir)], hitBase.fSpan->segment()->debugID(),
   *             hitBase.fT, hitBase.fPt.fX, hitBase.fPt.fY);
   *     for (int index = 0; index < count; ++index) {
   *         hit = sorted[index];
   *         SkOpSpan* span = hit->fSpan;
   *         SkOpSegment* hitSegment = span ? span->segment() : nullptr;
   *         bool operand = span ? hitSegment->operand() : false;
   *         bool ccw = ccw_dxdy(hit->fSlope, dir);
   *         SkDebugf("%s [%d] valid=%d operand=%d span=%d ccw=%d ", __FUNCTION__, index,
   *                 hit->fValid, operand, span ? span->debugID() : -1, ccw);
   *         if (span) {
   *             hitSegment->dumpPtsInner();
   *         }
   *         SkDebugf(" t=%1.9g pt=(%1.9g,%1.9g) slope=(%1.9g,%1.9g)\n", hit->fT,
   *                 hit->fPt.fX, hit->fPt.fY, hit->fSlope.fX, hit->fSlope.fY);
   *     }
   * #endif
   *     const SkPoint* last = nullptr;
   *     int wind = 0;
   *     int oppWind = 0;
   *     for (int index = 0; index < count; ++index) {
   *         hit = sorted[index];
   *         if (!hit->fValid) {
   *             return false;
   *         }
   *         bool ccw = ccw_dxdy(hit->fSlope, dir);
   * //        SkASSERT(!approximately_zero(hit->fT) || !hit->fValid);
   *         SkOpSpan* span = hit->fSpan;
   *         if (!span) {
   *             return false;
   *         }
   *         SkOpSegment* hitSegment = span->segment();
   *         if (span->windValue() == 0 && span->oppValue() == 0) {
   *             continue;
   *         }
   *         if (last && SkDPoint::ApproximatelyEqual(*last, hit->fPt)) {
   *             return false;
   *         }
   *         if (index < count - 1) {
   *             const SkPoint& next = sorted[index + 1]->fPt;
   *             if (SkDPoint::ApproximatelyEqual(next, hit->fPt)) {
   *                 return false;
   *             }
   *         }
   *         bool operand = hitSegment->operand();
   *         if (operand) {
   *             using std::swap;
   *             swap(wind, oppWind);
   *         }
   *         int lastWind = wind;
   *         int lastOpp = oppWind;
   *         int windValue = ccw ? -span->windValue() : span->windValue();
   *         int oppValue = ccw ? -span->oppValue() : span->oppValue();
   *         wind += windValue;
   *         oppWind += oppValue;
   *         bool sumSet = false;
   *         int spanSum = span->windSum();
   *         int windSum = SkOpSegment::UseInnerWinding(lastWind, wind) ? wind : lastWind;
   *         if (spanSum == SK_MinS32) {
   *             span->setWindSum(windSum);
   *             sumSet = true;
   *         } else {
   *             // the need for this condition suggests that UseInnerWinding is flawed
   *             // happened when last = 1 wind = -1
   * #if 0
   *             SkASSERT((hitSegment->isXor() ? (windSum & 1) == (spanSum & 1) : windSum == spanSum)
   *                     || (abs(wind) == abs(lastWind)
   *                     && (windSum ^ wind ^ lastWind) == spanSum));
   * #endif
   *         }
   *         int oSpanSum = span->oppSum();
   *         int oppSum = SkOpSegment::UseInnerWinding(lastOpp, oppWind) ? oppWind : lastOpp;
   *         if (oSpanSum == SK_MinS32) {
   *             span->setOppSum(oppSum);
   *         } else {
   * #if 0
   *             SkASSERT(hitSegment->oppXor() ? (oppSum & 1) == (oSpanSum & 1) : oppSum == oSpanSum
   *                     || (abs(oppWind) == abs(lastOpp)
   *                     && (oppSum ^ oppWind ^ lastOpp) == oSpanSum));
   * #endif
   *         }
   *         if (sumSet) {
   *             if (this->globalState()->phase() == SkOpPhase::kFixWinding) {
   *                 hitSegment->contour()->setCcw(ccw);
   *             } else {
   *                 (void) hitSegment->markAndChaseWinding(span, span->next(), windSum, oppSum, nullptr);
   *                 (void) hitSegment->markAndChaseWinding(span->next(), span, windSum, oppSum, nullptr);
   *             }
   *         }
   *         if (operand) {
   *             using std::swap;
   *             swap(wind, oppWind);
   *         }
   *         last = &hit->fPt;
   *         this->globalState()->bumpNested();
   *     }
   *     return true;
   * }
   * ```
   */
  public fun sortableTop(contourHead: SkOpContour?): Boolean {
    TODO("Implement sortableTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* toAngle() const {
   *         SkASSERT(!final());
   *         return fToAngle;
   *     }
   * ```
   */
  public fun toAngle(): SkOpAngle {
    TODO("Implement toAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * int windSum() const {
   *         SkASSERT(!final());
   *         return fWindSum;
   *     }
   * ```
   */
  public fun windSum(): Int {
    TODO("Implement windSum")
  }

  /**
   * C++ original:
   * ```cpp
   * int windValue() const {
   *         SkOPASSERT(!final());
   *         return fWindValue;
   *     }
   * ```
   */
  public fun windValue(): Int {
    TODO("Implement windValue")
  }
}
